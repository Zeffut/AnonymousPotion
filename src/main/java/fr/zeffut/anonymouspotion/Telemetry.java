package fr.zeffut.anonymouspotion;

import fr.zeffut.anonymouspotion.telemetry.PostHogClient;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mappe les événements métier du plugin vers PostHog. Aucune logique réseau ici.
 *
 * <p>Aucune méthode de cette classe ne transmet de pseudo, d'adresse IP, d'UUID de joueur ni
 * de contenu de message. Un plugin qui existe pour empêcher un pseudo de fuiter ne peut pas
 * expédier ces mêmes pseudos à un service tiers.
 */
public final class Telemetry {

    private final PostHogClient client;
    private final String serverInstallId;

    public Telemetry(PostHogClient client, String serverInstallId) {
        this.client = client;
        this.serverInstallId = serverInstallId;
    }

    public void pluginEnabled(String serverVersion, int obfuscatedLength,
                              boolean obfuscateWeaponName, boolean logRealNames,
                              boolean onlineMode) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("server_version", serverVersion);
        p.put("obfuscated_length", obfuscatedLength);
        p.put("obfuscate_weapon_name", obfuscateWeaponName);
        p.put("log_real_names", logRealNames);
        p.put("online_mode", onlineMode);
        client.capture("plugin_enabled", serverInstallId, p);
    }

    /** {@code deathKey} est la clé de traduction vanilla, jamais le message rendu. */
    public void deathObfuscated(int obfuscatedCount, boolean weaponObfuscated,
                                boolean offlineKiller, String deathKey) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("obfuscated_count", obfuscatedCount);
        p.put("weapon_obfuscated", weaponObfuscated);
        p.put("offline_killer", offlineKiller);
        p.put("death_key", deathKey);
        client.capture("death_obfuscated", serverInstallId, p);
    }

    /**
     * Seul le type de l'exception est transmis : son message pourrait contenir un pseudo.
     * Sans cet événement, une panne du plugin resterait invisible côté auteur.
     */
    public void obfuscationFailed(Throwable error) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("error_type", error.getClass().getSimpleName());
        client.capture("obfuscation_failed", serverInstallId, p);
    }

    public void commandUsed(String subcommand) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("subcommand", subcommand);
        client.capture("command_used", serverInstallId, p);
    }

    public void sessionHeartbeat(long uptimeMinutes, long deathsObfuscated) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("uptime_minutes", uptimeMinutes);
        p.put("deaths_obfuscated", deathsObfuscated);
        client.capture("session_heartbeat", serverInstallId, p);
    }
}
