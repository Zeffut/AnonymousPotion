package fr.zeffut.anonymouspotion;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class AnonymousPotionPlugin extends JavaPlugin {

    private AnonymousPotionConfig config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        getServer().getPluginManager().registerEvents(new DeathMessageListener(this), this);
        getLogger().info("AnonymousPotion activé.");
    }

    /** Configuration courante. Relue à chaque mort pour que le rechargement prenne effet. */
    public AnonymousPotionConfig config() {
        return config;
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
}
