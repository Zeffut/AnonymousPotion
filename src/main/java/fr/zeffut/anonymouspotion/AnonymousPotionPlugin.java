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

        // Initialisée avant registerEvents(...) : le listener ne doit structurellement jamais
        // pouvoir observer plugin.telemetry() == null, plutôt que de dépendre du modèle
        // mono-thread de Bukkit pour l'éviter.
        boolean telemetryEnabled = getConfig().getBoolean("telemetry", true);
        String telemetryHost = getConfig().getString("telemetry-host", PostHogClient.DEFAULT_HOST);
        PostHogClient client = new PostHogClient(telemetryEnabled, telemetryHost, "paper",
                getServer().getMinecraftVersion(), getPluginMeta().getVersion());
        telemetry = new Telemetry(client, loadOrCreateInstallId());
        telemetry.pluginEnabled(getServer().getBukkitVersion(),
                config.obfuscatedLength(), config.obfuscateWeaponName(),
                config.logRealNames(), getServer().getOnlineMode());

        getServer().getPluginManager().registerEvents(new DeathMessageListener(this), this);
        java.util.Objects.requireNonNull(getCommand("anonymouspotion"))
                .setExecutor(new ReloadCommand(this));

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

    /**
     * Identifiant de repli quand le fichier n'est ni lisible ni inscriptible. Tiré une seule
     * fois par vie de JVM, et non à chaque appel : un serveur dont le dossier de données n'est
     * pas inscriptible ne peut pas persister son identifiant, mais le redessiner à chaque
     * démarrage le ferait compter comme une installation neuve à chaque fois et gonflerait les
     * statistiques d'installations.
     */
    private static final String IDENTIFIANT_DE_REPLI = "unknown-" + java.util.UUID.randomUUID();

    /**
     * Identifiant anonyme et stable de cette installation, tiré au premier démarrage. Ne
     * contient rien de personnel : c'est un UUID aléatoire, sans lien avec un joueur ni avec
     * l'adresse du serveur.
     */
    private String loadOrCreateInstallId() {
        java.io.File f = new java.io.File(getDataFolder(), ".install-id");
        try {
            if (f.exists()) {
                String existant = java.nio.file.Files.readString(f.toPath()).trim();
                // Un fichier vide ou blanc — écriture interrompue, disque plein, édition
                // manuelle — donnerait un distinct_id vide, que PostHog rattacherait à un
                // profil fourre-tout partagé par toutes les installations dans ce cas. On le
                // traite donc comme absent, ce qui en tire un neuf et réécrit le fichier.
                if (!existant.isEmpty()) return existant;
            }
            String id = java.util.UUID.randomUUID().toString();
            getDataFolder().mkdirs();
            java.nio.file.Files.writeString(f.toPath(), id);
            return id;
        } catch (Exception e) {
            return IDENTIFIANT_DE_REPLI;
        }
    }
}
