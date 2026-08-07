package fr.zeffut.anonymouspotion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

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

    /**
     * Vrai dès qu'un nœud d'item a réellement été remplacé pendant le dernier
     * {@link #obfuscate(Component, Set)}. Le réglage {@code obfuscate-weapon-name} ne dit que
     * ce que le serveur autorise ; seul ce drapeau dit ce qui s'est produit à cette mort — la
     * plupart des morts (noyade, chute, poing nu) ne portent aucun nœud d'item.
     */
    private boolean weaponObfuscated;

    public NameObfuscator(int length, char filler, boolean obfuscateWeaponName) {
        this.replacement = String.valueOf(filler).repeat(length);
        this.obfuscateWeaponName = obfuscateWeaponName;
    }

    /**
     * Retourne le message avec les nœuds des joueurs listés dans {@code targets} remplacés
     * par un texte obfusqué. Le message d'origine est retourné tel quel si rien ne change.
     */
    public Component obfuscate(Component message, Set<UUID> targets) {
        weaponObfuscated = false;
        if (targets.isEmpty()) {
            return message;
        }
        return walk(message, targets);
    }

    /**
     * Un nom d'arme a-t-il effectivement été brouillé lors du dernier appel à
     * {@link #obfuscate(Component, Set)} ? Faux tant qu'aucun appel n'a eu lieu. L'instance
     * étant créée à chaque mort, la réponse porte bien sur cette mort-là.
     */
    public boolean weaponObfuscated() {
        return weaponObfuscated;
    }

    private Component walk(Component input, Set<UUID> targets) {
        UUID entityId = entityIdOf(input);
        if (entityId != null && targets.contains(entityId)) {
            return obfuscatedName(input);
        }

        if (obfuscateWeaponName && isItem(input)) {
            weaponObfuscated = true;
            return obfuscatedItem(input);
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

    /**
     * Les entités du message qu'il faut brouiller : celles que {@code isInvisible} retient,
     * la victime exceptée. La victime garde toujours son pseudo, même invisible — c'est la
     * seule exception de tout le design.
     *
     * <p>Le prédicat est injecté plutôt que codé en dur sur Bukkit : c'est ce qui rend cette
     * règle vérifiable en JUnit, sans démarrer de serveur. Le listener y passe un prédicat qui
     * interroge l'effet de potion ; une entité qui n'est pas un joueur connecté (mob, joueur
     * parti depuis longtemps) fait répondre faux au prédicat et sort donc de l'ensemble.
     */
    public static Set<UUID> targets(Component message, UUID victimId, Predicate<UUID> isInvisible) {
        Set<UUID> targets = new HashSet<>();
        for (UUID entityId : collectEntityIds(message)) {
            if (entityId.equals(victimId)) {
                continue;
            }
            if (isInvisible.test(entityId)) {
                targets.add(entityId);
            }
        }
        return targets;
    }

    /** Tous les UUID d'entités mentionnées dans le message, joueurs comme mobs. */
    public static Set<UUID> collectEntityIds(Component message) {
        Set<UUID> ids = new HashSet<>();
        collectInto(message, ids);
        return ids;
    }

    private static void collectInto(Component component, Set<UUID> ids) {
        UUID entityId = entityIdOf(component);
        if (entityId != null) {
            ids.add(entityId);
        }
        if (component instanceof TranslatableComponent translatable) {
            for (TranslationArgument argument : translatable.arguments()) {
                collectInto(argument.asComponent(), ids);
            }
        }
        for (Component child : component.children()) {
            collectInto(child, ids);
        }
    }

    private static boolean isItem(Component component) {
        HoverEvent<?> hover = component.hoverEvent();
        return hover != null && hover.action() == HoverEvent.Action.SHOW_ITEM;
    }

    /**
     * Les crochets restent lisibles pour garder l'allure d'un message vanilla ; seul le nom
     * de l'objet est brouillé. Le survol part avec le reste : il contient les NBT, donc le nom.
     */
    private Component obfuscatedItem(Component original) {
        Style style = original.style().toBuilder()
                .hoverEvent(null)
                .clickEvent(null)
                .insertion(null)
                .build();
        return Component.text()
                .style(style)
                .append(Component.text("["))
                .append(Component.text(replacement).decorate(TextDecoration.OBFUSCATED))
                .append(Component.text("]"))
                .build();
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
