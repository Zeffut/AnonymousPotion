package fr.zeffut.anonymouspotion;

import fr.zeffut.anonymouspotion.telemetry.PostHogClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mappe les événements métier du plugin vers PostHog. Aucune logique réseau ici.
 *
 * <p>Aucune méthode de cette classe ne transmet de pseudo, d'adresse IP, d'UUID de joueur ni
 * de contenu de message. Un plugin qui existe pour empêcher un pseudo de fuiter ne peut pas
 * expédier ces mêmes pseudos à un service tiers.
 */
public final class Telemetry {

    /**
     * Une clé de traduction vanilla ne contient que lettres, chiffres, points et tirets bas
     * (le camelCase existe réellement dans les clés vanilla, ex. {@code death.attack.onFire}) ;
     * un message rendu contient toujours des espaces, et souvent des accents, donc reste rejeté.
     */
    private static final Pattern DEATH_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._]+$");

    /**
     * Plafond de longueur : la plus longue clé {@code death.*} de 1.21.11 fait 36 caractères,
     * donc 64 laisse toute la marge utile. Sans plafond, la garde accepterait une chaîne de
     * longueur arbitraire du moment qu'elle commence par {@code death.} et ne contient que des
     * caractères de clé — ce n'est plus une clé de traduction à ce stade.
     */
    private static final int MAX_DEATH_KEY_LENGTH = 64;

    /** Repli prévu par le câblage de la tâche 3 quand le message n'est pas traduisible. */
    private static final String UNKNOWN_DEATH_KEY = "unknown";

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

    /**
     * {@code weaponObfuscated} est le fait constaté à cette mort — un nœud d'item a réellement
     * été remplacé — et non le réglage {@code obfuscate-weapon-name} du serveur, déjà émis par
     * {@code plugin_enabled}.
     *
     * <p>{@code deathKey} doit être la clé de traduction vanilla, jamais le message rendu. Garde
     * exécutable : une valeur n'est acceptée que si elle respecte le format d'une clé (lettres,
     * chiffres, points, tirets bas), qu'elle ne dépasse pas {@value #MAX_DEATH_KEY_LENGTH}
     * caractères, ET qu'elle commence par {@code death.} ou vaut exactement {@code "unknown"} ;
     * toute autre valeur — {@code null}, un pseudo brut sans espace (ex. {@code "Steve_99"}), ou
     * une chaîne au bon préfixe mais au mauvais format (ex. {@code "death.attack.player: Zeffut"})
     * — est remplacée par {@code "invalid"} avant l'envoi.
     */
    public void deathObfuscated(int obfuscatedCount, boolean weaponObfuscated,
                                boolean offlineKiller, String deathKey) {
        String safeDeathKey = isValidDeathKey(deathKey) ? deathKey : "invalid";
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("obfuscated_count", obfuscatedCount);
        p.put("weapon_obfuscated", weaponObfuscated);
        p.put("offline_killer", offlineKiller);
        p.put("death_key", safeDeathKey);
        client.capture("death_obfuscated", serverInstallId, p);
    }

    private static boolean isValidDeathKey(String deathKey) {
        if (deathKey == null || deathKey.length() > MAX_DEATH_KEY_LENGTH
                || !DEATH_KEY_PATTERN.matcher(deathKey).matches()) {
            return false;
        }
        return deathKey.startsWith("death.") || deathKey.equals(UNKNOWN_DEATH_KEY);
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
