package fr.zeffut.anonymouspotion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Réécrit un message Adventure en remplaçant le pseudo de certains joueurs par un texte
 * obfusqué. Classe volontairement sans dépendance Bukkit : toute la logique délicate est ici,
 * et elle se teste sans démarrer de serveur.
 */
public final class NameObfuscator {

    private final String replacement;
    private final boolean obfuscateWeaponName;

    public NameObfuscator(int length, char filler, boolean obfuscateWeaponName) {
        this.replacement = String.valueOf(filler).repeat(length);
        this.obfuscateWeaponName = obfuscateWeaponName;
    }

    /**
     * Retourne le message avec les nœuds des joueurs listés dans {@code targets} remplacés
     * par un texte obfusqué. Le message d'origine est retourné tel quel si rien ne change.
     */
    public Component obfuscate(Component message, Set<UUID> targets) {
        if (targets.isEmpty()) {
            return message;
        }
        return walk(message, targets);
    }

    private Component walk(Component input, Set<UUID> targets) {
        UUID entityId = entityIdOf(input);
        if (entityId != null && targets.contains(entityId)) {
            return obfuscatedName(input);
        }

        Component current = input;

        // Les pseudos d'un message de mort vivent dans les arguments du composant
        // traduisible, pas dans ses enfants. Oublier cette branche neutralise le plugin.
        if (current instanceof TranslatableComponent translatable) {
            List<TranslationArgument> arguments = translatable.arguments();
            List<TranslationArgument> rewritten = new ArrayList<>(arguments.size());
            boolean changed = false;
            for (TranslationArgument argument : arguments) {
                Component value = argument.asComponent();
                Component walked = walk(value, targets);
                if (walked == value) {
                    rewritten.add(argument);
                } else {
                    rewritten.add(TranslationArgument.component(walked));
                    changed = true;
                }
            }
            if (changed) {
                current = translatable.arguments(rewritten);
            }
        }

        List<Component> children = current.children();
        if (!children.isEmpty()) {
            List<Component> rewritten = new ArrayList<>(children.size());
            boolean changed = false;
            for (Component child : children) {
                Component walked = walk(child, targets);
                rewritten.add(walked);
                if (walked != child) {
                    changed = true;
                }
            }
            if (changed) {
                current = current.children(rewritten);
            }
        }

        return current;
    }

    /** UUID porté par le survol d'un nœud d'entité, ou {@code null} si ce n'en est pas un. */
    static UUID entityIdOf(Component component) {
        HoverEvent<?> hover = component.hoverEvent();
        if (hover == null || hover.action() != HoverEvent.Action.SHOW_ENTITY) {
            return null;
        }
        if (!(hover.value() instanceof HoverEvent.ShowEntity showEntity)) {
            return null;
        }
        return showEntity.id();
    }

    /**
     * Le survol d'un pseudo expose le vrai nom et l'UUID, et le clic pré-remplit
     * {@code /tell <pseudo>} : les trois doivent disparaître, sinon le pseudo fuite.
     */
    private Component obfuscatedName(Component original) {
        Style style = original.style().toBuilder()
                .hoverEvent(null)
                .clickEvent(null)
                .insertion(null)
                .decorate(TextDecoration.OBFUSCATED)
                .build();
        return Component.text(replacement, style);
    }
}
