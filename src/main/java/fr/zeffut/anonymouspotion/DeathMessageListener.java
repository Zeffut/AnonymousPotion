package fr.zeffut.anonymouspotion;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

public final class DeathMessageListener implements Listener {

    private final AnonymousPotionPlugin plugin;
    private final InvisibilityMemory invisibilityMemory = new InvisibilityMemory();

    public DeathMessageListener(AnonymousPotionPlugin plugin) {
        this.plugin = plugin;
    }

    // HIGHEST et non HIGH : c'est la dernière priorité qui peut encore modifier l'événement
    // avant MONITOR. En HIGH, un plugin tiers de messages de mort personnalisés écraserait
    // notre message brouillé et remettrait le vrai pseudo à l'écran.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Component message = event.deathMessage();
        if (message == null) {
            return;
        }

        // Tout le traitement est dans le try, sélection des cibles comprise : une exception
        // pendant ce calcul ferait sortir le handler et laisserait partir le message vanilla,
        // avec le vrai pseudo dedans — exactement la fuite que le plugin existe pour empêcher.
        try {
            Set<UUID> targets = NameObfuscator.targets(
                    message, event.getEntity().getUniqueId(), this::isInvisible);
            if (targets.isEmpty()) {
                return;
            }

            AnonymousPotionConfig config = plugin.config();
            NameObfuscator obfuscator = new NameObfuscator(
                    config.obfuscatedLength(),
                    config.fillerCharacter(),
                    config.obfuscateWeaponName());

            Component obfuscated = obfuscator.obfuscate(message, targets);

            if (config.logRealNames()) {
                plugin.getLogger().info("Mort de " + event.getEntity().getName()
                        + " — pseudo(s) brouillé(s) : " + realNames(targets));
            }

            event.deathMessage(obfuscated);
        } catch (Throwable erreur) {
            // Throwable et non RuntimeException : un StackOverflowError, que le parcours
            // récursif d'un message anormalement profond peut lever, traverserait un catch
            // plus étroit et ferait fuiter le pseudo en silence.
            //
            // Fail-closed : on supprime le message de mort et on journalise en SEVERE. Perdre
            // un message de mort se voit et se corrige ; une fuite silencieuse du pseudo, non.
            event.deathMessage(null);
            plugin.getLogger().log(Level.SEVERE,
                    "Échec du brouillage du message de mort, message supprimé par sécurité.", erreur);
        }
    }

    /** Pseudos réels des joueurs brouillés, pour les logs de modération. */
    private String realNames(Set<UUID> targets) {
        return targets.stream()
                .map(id -> {
                    Player player = Bukkit.getPlayer(id);
                    return player != null ? player.getName() : id.toString();
                })
                .collect(Collectors.joining(", "));
    }

    /**
     * Mémorise les joueurs qui quittent le serveur en étant invisibles. Ce handler vit ici, et
     * pas dans un listener à part, parce qu'il n'alimente qu'une seule décision : celle prise
     * plus haut par {@link #isInvisible(UUID)}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            invisibilityMemory.remember(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Seul point du plugin qui interroge Bukkit sur l'invisibilité. Une entité qui n'est pas
     * un joueur connecté — un mob, un joueur parti depuis longtemps — n'est pas invisible.
     *
     * <p>Le joueur hors ligne passe par la mémoire courte : un invisible qui frappe puis se
     * déconnecte reste cité dans le message de mort différé de sa cible, et Bukkit ne le
     * connaît plus. Le traiter comme visible serait un échec ouvert.
     */
    private boolean isInvisible(UUID entityId) {
        Player player = Bukkit.getPlayer(entityId);
        if (player != null) {
            return player.hasPotionEffect(PotionEffectType.INVISIBILITY);
        }
        return invisibilityMemory.wasRecentlyInvisible(entityId, System.currentTimeMillis());
    }
}
