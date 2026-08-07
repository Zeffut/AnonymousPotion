package fr.zeffut.anonymouspotion;

import fr.zeffut.anonymouspotion.telemetry.PostHogClient;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class AnonymousPotionPlugin extends JavaPlugin {

    private AnonymousPotionConfig config;
    private Telemetry telemetry;
    private final java.util.concurrent.atomic.AtomicLong deathsObfuscated =
            new java.util.concurrent.atomic.AtomicLong();
    private long demarrage;

    @Override
    public void onEnable() {
        demarrage = System.currentTimeMillis();
        saveDefaultConfig();
        reloadSettings();
        getServer().getPluginManager().registerEvents(new DeathMessageListener(this), this);
        java.util.Objects.requireNonNull(getCommand("anonymouspotion"))
                .setExecutor(new ReloadCommand(this));

        boolean telemetryEnabled = getConfig().getBoolean("telemetry", true);
        String telemetryHost = getConfig().getString("telemetry-host", PostHogClient.DEFAULT_HOST);
        PostHogClient client = new PostHogClient(telemetryEnabled, telemetryHost, "paper",
                getServer().getMinecraftVersion(), getPluginMeta().getVersion());
        telemetry = new Telemetry(client, loadOrCreateInstallId());
        telemetry.pluginEnabled(getServer().getBukkitVersion(),
                config.obfuscatedLength(), config.obfuscateWeaponName(),
                config.logRealNames(), getServer().getOnlineMode());

        // 36 000 ticks valent 30 minutes ; le premier battement part après 30 minutes, pas au
        // démarrage, où plugin_enabled fait déjà le travail.
        long trenteMinutes = 20L * 60L * 30L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            long uptime = (System.currentTimeMillis() - demarrage) / 60_000L;
            telemetry.sessionHeartbeat(uptime, deathsObfuscated.get());
        }, trenteMinutes, trenteMinutes);

        getLogger().info("AnonymousPotion activé.");
    }

    /** Configuration courante. Relue à chaque mort pour que le rechargement prenne effet. */
    public AnonymousPotionConfig config() {
        return config;
    }

    /** Point d'entrée télémétrie pour le listener et la commande. */
    public Telemetry telemetry() {
        return telemetry;
    }

    /**
     * Incrémenté à chaque mort brouillée avec succès. Lu depuis le thread du planificateur
     * asynchrone du battement de session, d'où l'{@code AtomicLong}.
     */
    public void countObfuscatedDeath() {
        deathsObfuscated.incrementAndGet();
    }

    /** Relit le fichier de configuration depuis le disque. */
    public void reloadSettings() {
        reloadConfig();
        FileConfiguration file = getConfig();
        config = AnonymousPotionConfig.of(
                file.getInt("obfuscated-length", AnonymousPotionConfig.DEFAULT_LENGTH),
                file.getString("filler-character", String.valueOf(AnonymousPotionConfig.DEFAULT_FILLER)),
                file.getBoolean("obfuscate-weapon-name", true),
                file.getBoolean("log-real-names", true),
                getLogger()::warning);
    }

    /** Identifiant anonyme et stable de cette installation, tiré au premier démarrage. */
    private String loadOrCreateInstallId() {
        java.io.File f = new java.io.File(getDataFolder(), ".install-id");
        try {
            if (f.exists()) return java.nio.file.Files.readString(f.toPath()).trim();
            String id = java.util.UUID.randomUUID().toString();
            getDataFolder().mkdirs();
            java.nio.file.Files.writeString(f.toPath(), id);
            return id;
        } catch (Exception e) {
            return "unknown-" + java.util.UUID.randomUUID();
        }
    }
}
