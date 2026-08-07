# Télémétrie PostHog — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Câbler la télémétrie PostHog d'AnonymousPotion selon les conventions Minecraft de Zeffut, sans jamais transmettre de donnée personnelle.

**Architecture:** Copie du module de télémétrie de ModChecker (`PostHogClient` + `JsonWriter`), spécialisée pour ce plugin, plus une classe `Telemetry` qui mappe les événements métier. Envoi HTTP asynchrone fire-and-forget : aucune erreur réseau ne peut atteindre le serveur. Opt-out par `config.yml`.

**Tech Stack:** Java 21, `java.net.http.HttpClient`, aucune dépendance ajoutée.

**Référence à copier :** `~/Desktop/Projets/ModChecker/server/common/src/main/java/fr/zeffut/modchecker/telemetry/` (`PostHogClient.java`, `JsonWriter.java`) et `~/Desktop/Projets/ModChecker/server/paper/src/main/java/fr/zeffut/modchecker/Telemetry.java`.

## Global Constraints

- Package `fr.zeffut.anonymouspotion.telemetry` pour le client, `fr.zeffut.anonymouspotion` pour `Telemetry`.
- `APP = "anonymouspotion"` — c'est ce qui segmente le projet PostHog partagé.
- Clé d'ingestion : `phc_zdMj4p5wo8EvfVApjb2EbfUHJ76zgYGM5wAGz5YJC359`, host `https://eu.i.posthog.com`. La clé est publique par nature (clé d'ingestion PostHog) et se code en dur, comme dans ModChecker.
- **Aucune donnée personnelle ne quitte le serveur.** Pas de pseudo, pas d'adresse IP, pas d'UUID de joueur, pas de contenu de message. Le `distinct_id` est toujours l'install-id du serveur. Cette règle prime sur toute ressemblance avec ModChecker, qui lui envoie `username` et `player_ip`.
- Télémétrie **active par défaut**, désactivable par `telemetry: false` dans `config.yml`.
- Fire-and-forget : `capture()` ne doit jamais lever ni bloquer le thread appelant.
- Aucune dépendance Gradle ajoutée.
- Commentaires et messages en français.
- Un commit par tâche.

---

### Task 1: Client PostHog

**Files:**
- Create: `src/main/java/fr/zeffut/anonymouspotion/telemetry/PostHogClient.java`
- Create: `src/main/java/fr/zeffut/anonymouspotion/telemetry/JsonWriter.java`
- Test: `src/test/java/fr/zeffut/anonymouspotion/telemetry/PostHogClientTest.java`

**Interfaces:**
- Consumes: rien.
- Produces:
  - `PostHogClient(boolean enabled, String host, String source, String mcVersion, String componentVersion)` — constructeur de production
  - `PostHogClient(…, Sender sender)` — même chose avec le `Sender` injecté, pour les tests
  - `interface Sender { void send(String jsonBody); }`
  - `void capture(String event, String distinctId, Map<String, Object> properties)`
  - `String buildBody(String event, String distinctId, Map<String, Object> properties)` — visible pour les tests
  - Constantes `API_KEY`, `DEFAULT_HOST`, `APP`

- [ ] **Step 1: Copier les deux fichiers de référence**

```bash
mkdir -p src/main/java/fr/zeffut/anonymouspotion/telemetry
cp ~/Desktop/Projets/ModChecker/server/common/src/main/java/fr/zeffut/modchecker/telemetry/PostHogClient.java \
   ~/Desktop/Projets/ModChecker/server/common/src/main/java/fr/zeffut/modchecker/telemetry/JsonWriter.java \
   src/main/java/fr/zeffut/anonymouspotion/telemetry/
```

- [ ] **Step 2: Adapter les deux fichiers**

Dans les deux : `package fr.zeffut.modchecker.telemetry;` → `package fr.zeffut.anonymouspotion.telemetry;`

Dans `PostHogClient` uniquement :
- `APP` passe de `"modchecker"` à `"anonymouspotion"`
- le nom du thread de l'exécuteur passe de `"modchecker-posthog"` à `"anonymouspotion-posthog"`

Ne rien changer d'autre : la logique réseau, le format du corps et l'échappement JSON sont éprouvés en production sur ModChecker.

- [ ] **Step 3: Écrire les tests**

```java
package fr.zeffut.anonymouspotion.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PostHogClientTest {

    private final List<String> envoyes = new ArrayList<>();

    private PostHogClient client(boolean enabled) {
        return new PostHogClient(enabled, "https://example.invalid", "paper", "1.21.11", "1.0.0",
                envoyes::add);
    }

    @Test
    void envoieUnEventQuandActive() {
        client(true).capture("plugin_enabled", "install-42", Map.of());

        assertEquals(1, envoyes.size());
        assertTrue(envoyes.get(0).contains("\"event\":\"plugin_enabled\""));
        assertTrue(envoyes.get(0).contains("\"distinct_id\":\"install-42\""));
    }

    @Test
    void nEnvoieRienQuandDesactive() {
        client(false).capture("plugin_enabled", "install-42", Map.of());

        assertTrue(envoyes.isEmpty());
    }

    @Test
    void marqueChaqueEventAvecLeSlugDeLApplication() {
        String body = client(true).buildBody("plugin_enabled", "install-42", Map.of());

        assertTrue(body.contains("\"app\":\"anonymouspotion\""));
    }

    @Test
    void jointLesProprietesCommunes() {
        String body = client(true).buildBody("plugin_enabled", "install-42", Map.of());

        assertTrue(body.contains("\"source\":\"paper\""));
        assertTrue(body.contains("\"mc_version\":\"1.21.11\""));
        assertTrue(body.contains("\"component_version\":\"1.0.0\""));
    }

    @Test
    void serialiseLesProprietesMetier() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("obfuscated_count", 2);
        props.put("weapon_obfuscated", true);

        String body = client(true).buildBody("death_obfuscated", "install-42", props);

        assertTrue(body.contains("\"obfuscated_count\":2"));
        assertTrue(body.contains("\"weapon_obfuscated\":true"));
    }

    @Test
    void echappeLesGuillemetsDansLesValeurs() {
        String body = client(true).buildBody("x", "install-42", Map.of("k", "a\"b"));

        assertTrue(body.contains("a\\\"b"));
    }

    @Test
    void nePropageJamaisUneErreurDEnvoi() {
        PostHogClient quiCasse = new PostHogClient(true, "h", "paper", "1.21.11", "1.0.0",
                body -> { throw new IllegalStateException("réseau coupé"); });

        quiCasse.capture("plugin_enabled", "install-42", Map.of());
        // Aucune exception ne doit remonter : le test passe s'il arrive ici.
    }

    @Test
    void neTransmetAucuneProprieteNonFournie() {
        String body = client(true).buildBody("death_obfuscated", "install-42", Map.of());

        assertFalse(body.contains("username"));
        assertFalse(body.contains("player_ip"));
    }
}
```

- [ ] **Step 4: Lancer les tests**

```bash
./gradlew test --tests 'fr.zeffut.anonymouspotion.telemetry.PostHogClientTest'
```

Attendu : 8 tests verts.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/zeffut/anonymouspotion/telemetry src/test/java/fr/zeffut/anonymouspotion/telemetry
git commit -m "feat: client PostHog fire-and-forget"
```

---

### Task 2: Événements métier

**Files:**
- Create: `src/main/java/fr/zeffut/anonymouspotion/Telemetry.java`
- Test: `src/test/java/fr/zeffut/anonymouspotion/TelemetryTest.java`

**Interfaces:**
- Consumes: `PostHogClient`, `capture(String, String, Map<String, Object>)`, `PostHogClient.Sender`.
- Produces: `Telemetry(PostHogClient client, String serverInstallId)` et les cinq méthodes ci-dessous.

**La taxonomie.** Cinq événements, tous avec `distinct_id` = install-id du serveur. Aucun ne
porte de pseudo, d'IP, d'UUID de joueur ni de contenu de message.

| Événement | Quand | Propriétés |
|---|---|---|
| `plugin_enabled` | au démarrage | `server_version`, `obfuscated_length`, `obfuscate_weapon_name`, `log_real_names`, `online_mode` |
| `death_obfuscated` | à chaque message brouillé | `obfuscated_count`, `weapon_obfuscated`, `offline_killer`, `death_key` |
| `obfuscation_failed` | quand le fail-closed se déclenche | `error_type` |
| `command_used` | `/anonymouspotion reload` | `subcommand` |
| `session_heartbeat` | toutes les 30 min | `uptime_minutes`, `deaths_obfuscated` |

`death_key` est la clé de traduction vanilla du message (`death.attack.player`,
`death.attack.lava.player`…), jamais le texte rendu. Elle dit quel type de mort est concerné
sans rien révéler des joueurs. `obfuscation_failed` est le plus utile des cinq : la revue de
la branche précédente a relevé qu'une panne systémique du plugin serait aujourd'hui
silencieuse côté auteur — cet événement la rend visible.

- [ ] **Step 1: Écrire les tests**

```java
package fr.zeffut.anonymouspotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.zeffut.anonymouspotion.telemetry.PostHogClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelemetryTest {

    private final List<String> envoyes = new ArrayList<>();

    private Telemetry telemetry() {
        PostHogClient client = new PostHogClient(true, "https://example.invalid", "paper",
                "1.21.11", "1.0.0", envoyes::add);
        return new Telemetry(client, "install-42");
    }

    private String dernier() {
        return envoyes.get(envoyes.size() - 1);
    }

    @Test
    void emetLeDemarrageAvecLaConfiguration() {
        telemetry().pluginEnabled("1.21.11-132", 8, true, true, true);

        assertTrue(dernier().contains("\"event\":\"plugin_enabled\""));
        assertTrue(dernier().contains("\"obfuscated_length\":8"));
        assertTrue(dernier().contains("\"obfuscate_weapon_name\":true"));
    }

    @Test
    void emetUneMortBrouillee() {
        telemetry().deathObfuscated(2, true, false, "death.attack.player");

        assertTrue(dernier().contains("\"event\":\"death_obfuscated\""));
        assertTrue(dernier().contains("\"obfuscated_count\":2"));
        assertTrue(dernier().contains("\"death_key\":\"death.attack.player\""));
    }

    @Test
    void emetUnEchecDeBrouillage() {
        telemetry().obfuscationFailed(new IllegalStateException("boum"));

        assertTrue(dernier().contains("\"event\":\"obfuscation_failed\""));
        assertTrue(dernier().contains("\"error_type\":\"IllegalStateException\""));
    }

    @Test
    void nEmetPasLeMessageDeLException() {
        telemetry().obfuscationFailed(new IllegalStateException("Zeffut a été tué par Steve"));

        assertFalse(dernier().contains("Zeffut"));
        assertFalse(dernier().contains("Steve"));
    }

    @Test
    void emetLUsageDUneCommande() {
        telemetry().commandUsed("reload");

        assertTrue(dernier().contains("\"event\":\"command_used\""));
        assertTrue(dernier().contains("\"subcommand\":\"reload\""));
    }

    @Test
    void emetUnBattementDeSession() {
        telemetry().sessionHeartbeat(30, 7);

        assertTrue(dernier().contains("\"event\":\"session_heartbeat\""));
        assertTrue(dernier().contains("\"uptime_minutes\":30"));
        assertTrue(dernier().contains("\"deaths_obfuscated\":7"));
    }

    @Test
    void identifieToujoursLeServeurEtJamaisUnJoueur() {
        Telemetry t = telemetry();
        t.pluginEnabled("1.21.11-132", 8, true, true, true);
        t.deathObfuscated(1, false, false, "death.attack.player");
        t.commandUsed("reload");

        for (String body : envoyes) {
            assertTrue(body.contains("\"distinct_id\":\"install-42\""));
            assertFalse(body.contains("username"));
            assertFalse(body.contains("player_ip"));
        }
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./gradlew test --tests 'fr.zeffut.anonymouspotion.TelemetryTest'
```

Attendu : échec de compilation, `Telemetry` n'existe pas.

- [ ] **Step 3: Écrire `Telemetry.java`**

```java
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
```

- [ ] **Step 4: Lancer toute la suite**

```bash
./gradlew test
```

Attendu : 48 tests verts (33 existants + 8 de la tâche 1 + 7 ici).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/zeffut/anonymouspotion/Telemetry.java src/test/java/fr/zeffut/anonymouspotion/TelemetryTest.java
git commit -m "feat: taxonomie d'événements de télémétrie"
```

---

### Task 3: Câblage, configuration et divulgation

**Files:**
- Modify: `src/main/java/fr/zeffut/anonymouspotion/AnonymousPotionPlugin.java`
- Modify: `src/main/java/fr/zeffut/anonymouspotion/DeathMessageListener.java`
- Modify: `src/main/java/fr/zeffut/anonymouspotion/ReloadCommand.java`
- Modify: `src/main/resources/config.yml`
- Modify: `README.md`
- Modify: `docs/modrinth-description.md`

**Interfaces:**
- Consumes: `Telemetry` et ses cinq méthodes, `PostHogClient(boolean, String, String, String, String)`, `PostHogClient.DEFAULT_HOST`.
- Produces: `AnonymousPotionPlugin#telemetry()` pour le listener et la commande.

- [ ] **Step 1: Câbler le plugin**

Dans `AnonymousPotionPlugin`, ajouter un champ `Telemetry telemetry`, son accesseur
`public Telemetry telemetry()`, un compteur `deathsObfuscated` avec
`public void countObfuscatedDeath()`, et l'initialisation dans `onEnable()` après
`reloadSettings()` :

```java
        boolean telemetryEnabled = getConfig().getBoolean("telemetry", true);
        String telemetryHost = getConfig().getString("telemetry-host", PostHogClient.DEFAULT_HOST);
        PostHogClient client = new PostHogClient(telemetryEnabled, telemetryHost, "paper",
                getServer().getMinecraftVersion(), getPluginMeta().getVersion());
        telemetry = new Telemetry(client, loadOrCreateInstallId());
        telemetry.pluginEnabled(getServer().getBukkitVersion(),
                config.obfuscatedLength(), config.obfuscateWeaponName(),
                config.logRealNames(), getServer().getOnlineMode());
```

L'install-id, identique à celui de ModChecker — un UUID tiré une fois puis relu, qui
identifie l'installation sans rien dire de qui l'utilise :

```java
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
```

Le battement de session, planifié après l'initialisation de la télémétrie. 36 000 ticks
valent 30 minutes ; le premier part après 30 minutes, pas au démarrage, où
`plugin_enabled` fait déjà le travail :

```java
        long trenteMinutes = 20L * 60L * 30L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            long uptime = (System.currentTimeMillis() - demarrage) / 60_000L;
            telemetry.sessionHeartbeat(uptime, deathsObfuscated.get());
        }, trenteMinutes, trenteMinutes);
```

avec `private final java.util.concurrent.atomic.AtomicLong deathsObfuscated = new AtomicLong();`
et `private long demarrage;` initialisé à `System.currentTimeMillis()` en début d'`onEnable()`.

Le compteur est un `AtomicLong` parce qu'il est incrémenté depuis le thread principal et lu
depuis le thread du planificateur asynchrone.

- [ ] **Step 2: Émettre l'événement de mort dans le listener**

Dans `DeathMessageListener`, après `event.deathMessage(obfuscated)` — donc uniquement quand
le brouillage a réussi :

```java
            plugin.countObfuscatedDeath();
            plugin.telemetry().deathObfuscated(targets.size(), weaponObfuscated,
                    offlineKiller, deathKey(message));
```

Les deux drapeaux se calculent juste avant, dans le même `try` :

```java
            boolean weaponObfuscated = config.obfuscateWeaponName();
            boolean offlineKiller = anyOffline(targets);
```

`offlineKiller` vaut vrai si l'une des cibles n'est plus connectée — c'est le cas couvert par
`InvisibilityMemory` :

```java
    /** Vrai si l'une des cibles n'est plus en ligne : le cas couvert par la mémoire courte. */
    private boolean anyOffline(Set<UUID> targets) {
        return targets.stream().anyMatch(id -> Bukkit.getPlayer(id) == null);
    }

    /** Clé de traduction du message, jamais son texte rendu. */
    private static String deathKey(Component message) {
        return message instanceof TranslatableComponent t ? t.key() : "unknown";
    }
```

Dans le bloc `catch`, après la suppression du message et le log :

```java
            plugin.telemetry().obfuscationFailed(erreur);
```

Ces appels sont dans le `try` existant pour les deux premiers, et dans le `catch` pour le
troisième. `capture()` avale déjà toute erreur, donc la télémétrie ne peut pas déclencher le
fail-closed elle-même.

- [ ] **Step 3: Émettre l'usage de la commande**

Dans `ReloadCommand`, après le rechargement réussi :

```java
        plugin.telemetry().commandUsed("reload");
```

- [ ] **Step 4: Ajouter les clés de configuration**

À la fin de `src/main/resources/config.yml` :

```yaml

# Télémétrie anonyme (PostHog) — passer à false pour la désactiver.
# Aucune donnée personnelle n'est transmise : ni pseudo, ni adresse IP, ni UUID de joueur,
# ni contenu de message. Seuls sont envoyés un identifiant d'installation tiré au hasard,
# la version du serveur, les réglages ci-dessus et des compteurs de morts brouillées.
telemetry: true
telemetry-host: "https://eu.i.posthog.com"
```

- [ ] **Step 5: Divulguer dans les deux documentations**

Les conventions imposent de divulguer la télémétrie sur la page du projet.

Ajouter au `README.md`, après la section Configuration, en français :

```markdown
## Télémétrie

Le plugin envoie des statistiques d'usage anonymes à PostHog. `telemetry: false` dans
`config.yml` les désactive.

**Ce qui est envoyé :** un identifiant d'installation tiré au hasard au premier démarrage,
la version du serveur, les réglages du plugin, et des compteurs — nombre de pseudos brouillés
par mort, type de mort (la clé de traduction vanilla), et les erreurs éventuelles.

**Ce qui ne l'est jamais :** aucun pseudo, aucune adresse IP, aucun UUID de joueur, aucun
contenu de message. Ce plugin existe pour empêcher un pseudo de fuiter — il ne va pas
expédier ces mêmes pseudos ailleurs.
```

Ajouter à `docs/modrinth-description.md`, avant la section Requirements, la même chose en
anglais :

```markdown
## Telemetry

The plugin sends anonymous usage statistics to PostHog. Set `telemetry: false` in
`config.yml` to turn it off.

**What is sent:** a random installation id generated on first start, the server version, the
plugin settings, and counters — how many names were obfuscated per death, the kind of death
(the vanilla translation key), and any errors.

**What is never sent:** no usernames, no IP addresses, no player UUIDs, no message contents.
This plugin exists to stop a username from leaking — it is not going to ship those same
usernames somewhere else.
```

- [ ] **Step 6: Vérifier**

```bash
./gradlew build
```

Attendu : `BUILD SUCCESSFUL`, 48 tests verts, jar régénéré.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: câble la télémétrie et la divulgue dans la documentation"
```
