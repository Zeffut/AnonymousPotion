package fr.zeffut.anonymouspotion;

import java.util.HashSet;
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
import org.bukkit.potion.PotionEffectType;

public final class DeathMessageListener implements Listener {

    private final AnonymousPotionPlugin plugin;

    public DeathMessageListener(AnonymousPotionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Component message = event.deathMessage();
        if (message == null) {
            return;
        }

        Set<UUID> targets = invisiblePlayersIn(message, event.getEntity().getUniqueId());
        if (targets.isEmpty()) {
            return;
        }

        AnonymousPotionConfig config = plugin.config();
        try {
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
        } catch (RuntimeException exception) {
            // Fail-closed : un bug ne doit jamais laisser passer le vrai pseudo, qui est
            // la seule chose que ce plugin existe pour cacher.
            event.deathMessage(null);
            plugin.getLogger().log(Level.SEVERE,
                    "Échec du brouillage du message de mort, message supprimé par sécurité.", exception);
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
     * Les joueurs invisibles cités dans le message, hors victime. La victime garde toujours
     * son pseudo, même invisible : c'est l'exception voulue par le design.
     */
    private Set<UUID> invisiblePlayersIn(Component message, UUID victimId) {
        Set<UUID> targets = new HashSet<>();
        for (UUID entityId : NameObfuscator.collectEntityIds(message)) {
            if (entityId.equals(victimId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(entityId);
            if (player != null && player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                targets.add(entityId);
            }
        }
        return targets;
    }
}
