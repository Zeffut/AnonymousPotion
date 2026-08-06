# AnonymousPotion — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un plugin Paper qui remplace le pseudo d'un joueur sous effet Invisibilité par un texte obfusqué dans les messages de mort, sauf quand ce joueur est lui-même la victime.

**Architecture:** Le plugin écoute `PlayerDeathEvent` et réécrit l'arbre de `Component` Adventure du message de mort. Il identifie les nœuds « pseudo » par l'UUID contenu dans leur `hoverEvent` de type `SHOW_ENTITY`, et remplace ceux des joueurs invisibles par un texte de longueur fixe décoré `OBFUSCATED`, dépouillé de son hover, de son click et de son insertion. Toute la manipulation de `Component` vit dans une classe pure sans dépendance Bukkit, testable en JUnit sans serveur.

**Tech Stack:** Java 21, Paper API 1.21.11 (Adventure), Gradle Kotlin DSL, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-06-anonymous-potion-design.md`

## Global Constraints

- Package racine : `fr.zeffut.anonymouspotion`. Group Gradle : `fr.zeffut`. Version : `1.0.0`.
- Java 21 via toolchain Gradle. Gradle 9.4.1 (wrapper).
- `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` en `compileOnly` **et** `testImplementation`.
- Aucune dépendance embarquée : pas de shadowJar. Artefact `AnonymousPotion.jar`.
- `plugin.yml` : `api-version: '1.21'`, `authors: [Zeffut]`.
- Longueur du brouillage **fixe**, identique pour tous les joueurs et pour les noms d'armes.
- Aucun joueur ne voit jamais le vrai pseudo, y compris les OP. Pas de permission de révélation.
- Le nœud de remplacement ne doit conserver ni `hoverEvent`, ni `clickEvent`, ni `insertion`.
- Commentaires et messages utilisateur en français.
- Un commit par tâche.

---

### Task 1: Squelette Gradle et plugin qui démarre

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `src/main/java/fr/zeffut/anonymouspotion/AnonymousPotionPlugin.java`
- Create: `src/main/resources/plugin.yml`
- Create: `gradle/wrapper/` (généré par `gradle wrapper`)

**Interfaces:**
- Consumes: rien.
- Produces: la classe `fr.zeffut.anonymouspotion.AnonymousPotionPlugin extends JavaPlugin`, point d'entrée que les tâches suivantes enrichissent. Le build produit `build/libs/AnonymousPotion.jar`.

- [ ] **Step 1: Générer le wrapper Gradle et l'autoriser dans git**

Depuis la racine du projet :

```bash
gradle wrapper --gradle-version 9.4.1
```

Le `.gitignore` existant contient `*.jar`, ce qui exclurait `gradle-wrapper.jar` — le wrapper
serait alors inutilisable après un clone. Ajouter l'exception :

```bash
printf '!gradle/wrapper/gradle-wrapper.jar\n' >> .gitignore
git check-ignore -v gradle/wrapper/gradle-wrapper.jar
```

Attendu : la seconde commande ne retourne rien (le fichier n'est plus ignoré).

- [ ] **Step 2: Écrire `settings.gradle.kts`**

```kotlin
rootProject.name = "AnonymousPotion"
```

- [ ] **Step 3: Écrire `build.gradle.kts`**

`paper-api` est en `testImplementation` en plus de `compileOnly` parce que les tests
manipulent des `Component` Adventure, qui arrivent par cette dépendance. Utiliser la même
coordonnée garantit une version d'Adventure identique entre le test et l'exécution.

```kotlin
plugins {
    java
}

group = "fr.zeffut"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("AnonymousPotion.jar")
}
```

- [ ] **Step 4: Écrire `src/main/resources/plugin.yml`**

La commande et la permission sont déclarées dès maintenant ; leur implémentation arrive en
tâche 6. Un `plugin.yml` déclarant une commande sans exécuteur ne provoque aucune erreur au
démarrage : Bukkit répond simplement « commande inconnue ».

```yaml
name: AnonymousPotion
version: ${version}
main: fr.zeffut.anonymouspotion.AnonymousPotionPlugin
api-version: '1.21'
authors: [Zeffut]
description: Brouille le pseudo des joueurs invisibles dans les messages de mort

commands:
  anonymouspotion:
    description: Commandes d'administration d'AnonymousPotion
    usage: /anonymouspotion reload
    permission: anonymouspotion.admin

permissions:
  anonymouspotion.admin:
    description: Recharger la configuration d'AnonymousPotion
    default: op
```

- [ ] **Step 5: Écrire `AnonymousPotionPlugin.java`**

```java
package fr.zeffut.anonymouspotion;

import org.bukkit.plugin.java.JavaPlugin;

public final class AnonymousPotionPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("AnonymousPotion activé.");
    }
}
```

- [ ] **Step 6: Vérifier que le build passe et produit le jar**

```bash
./gradlew build
ls -l build/libs/AnonymousPotion.jar
```

Attendu : `BUILD SUCCESSFUL` et le fichier `AnonymousPotion.jar` présent.

Si la résolution de `paper-api:1.21.11-R0.1-SNAPSHOT` échoue, lister les versions
disponibles sur `https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/`
et prendre la plus récente en `1.21.x`, puis ajuster `build.gradle.kts` **et**
`api-version` dans `plugin.yml` si la version mineure diffère.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradlew gradlew.bat gradle src
git commit -m "feat: squelette Gradle du plugin AnonymousPotion"
```

---

### Task 2: NameObfuscator — remplacer le pseudo des joueurs ciblés

**Files:**
- Create: `src/main/java/fr/zeffut/anonymouspotion/NameObfuscator.java`
- Test: `src/test/java/fr/zeffut/anonymouspotion/NameObfuscatorTest.java`

**Interfaces:**
- Consumes: rien (classe pure, aucune dépendance Bukkit).
- Produces:
  - `NameObfuscator(int length, char filler, boolean obfuscateWeaponName)`
  - `Component obfuscate(Component message, Set<UUID> targets)` — retourne le message avec les
    nœuds des joueurs dont l'UUID est dans `targets` remplacés. Retourne l'instance d'origine
    inchangée si `targets` est vide.

**Contexte pour l'implémenteur.** Un message de mort vanilla n'est pas du texte plat, c'est un
arbre :

```
translatable("death.attack.player")
  ├─ argument[0] : text("Zeffut")  + hoverEvent(SHOW_ENTITY, uuid victime)
  └─ argument[1] : text("Steve")   + hoverEvent(SHOW_ENTITY, uuid tueur)
```

Deux pièges à connaître avant d'écrire le code :

1. Les pseudos sont dans les **arguments** du composant traduisible, pas dans ses `children()`.
   Un parcours qui ne descend que dans `children()` ne trouvera jamais rien.
2. Le `hoverEvent` d'un pseudo contient le vrai nom **et** l'UUID, et le `clickEvent` contient
   `/tell <pseudo>`. Remplacer le texte sans supprimer ces deux événements laisse le vrai
   pseudo lisible au survol de la souris. C'est le bug le plus facile à commettre ici.

- [ ] **Step 1: Écrire les tests qui échouent**

Créer `src/test/java/fr/zeffut/anonymouspotion/NameObfuscatorTest.java` :

```java
package fr.zeffut.anonymouspotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class NameObfuscatorTest {

    private static final UUID VICTIM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID KILLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final NameObfuscator obfuscator = new NameObfuscator(8, 'a', true);

    /** Reproduit un nœud « pseudo » tel que le serveur le construit. */
    private static Component playerName(String name, UUID id) {
        return Component.text(name)
                .hoverEvent(HoverEvent.showEntity(Key.key("minecraft:player"), id, Component.text(name)))
                .clickEvent(ClickEvent.suggestCommand("/tell " + name))
                .insertion(name);
    }

    /** Reproduit « Zeffut a été tué par Steve ». */
    private static Component deathMessage() {
        return Component.translatable("death.attack.player",
                playerName("Zeffut", VICTIM_ID),
                playerName("Steve", KILLER_ID));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void remplaceLePseudoCible() {
        Component result = obfuscator.obfuscate(deathMessage(), Set.of(KILLER_ID));

        assertFalse(plain(result).contains("Steve"));
        assertTrue(plain(result).contains("aaaaaaaa"));
    }

    @Test
    void laisseIntactLePseudoNonCible() {
        Component result = obfuscator.obfuscate(deathMessage(), Set.of(KILLER_ID));

        assertTrue(plain(result).contains("Zeffut"));
    }

    @Test
    void supprimeHoverClickEtInsertionDuNoeudRemplace() {
        Component result = obfuscator.obfuscate(deathMessage(), Set.of(KILLER_ID));
        Component replaced = ((TranslatableComponent) result).arguments().get(1).asComponent();

        assertNull(replaced.hoverEvent());
        assertNull(replaced.clickEvent());
        assertNull(replaced.insertion());
    }

    @Test
    void appliqueLaDecorationObfusquee() {
        Component result = obfuscator.obfuscate(deathMessage(), Set.of(KILLER_ID));
        Component replaced = ((TranslatableComponent) result).arguments().get(1).asComponent();

        assertTrue(replaced.hasDecoration(TextDecoration.OBFUSCATED));
    }

    @Test
    void conserveLaCouleurDuNoeudRemplace() {
        Component message = Component.translatable("death.attack.player",
                playerName("Zeffut", VICTIM_ID),
                playerName("Steve", KILLER_ID).color(NamedTextColor.RED));

        Component result = obfuscator.obfuscate(message, Set.of(KILLER_ID));
        Component replaced = ((TranslatableComponent) result).arguments().get(1).asComponent();

        assertEquals(NamedTextColor.RED, replaced.color());
    }

    @Test
    void utiliseExactementLaLongueurConfiguree() {
        NameObfuscator court = new NameObfuscator(3, 'x', true);

        Component result = court.obfuscate(deathMessage(), Set.of(KILLER_ID));

        assertTrue(plain(result).contains("xxx"));
        assertFalse(plain(result).contains("xxxx"));
    }

    @Test
    void descendDansLesEnfants() {
        Component message = Component.text("Le tueur : ").append(playerName("Steve", KILLER_ID));

        Component result = obfuscator.obfuscate(message, Set.of(KILLER_ID));

        assertFalse(plain(result).contains("Steve"));
        assertTrue(plain(result).startsWith("Le tueur : "));
    }

    @Test
    void descendDansLesArgumentsImbriques() {
        Component message = Component.translatable("death.attack.player",
                playerName("Zeffut", VICTIM_ID),
                Component.translatable("chat.square_brackets", playerName("Steve", KILLER_ID)));

        Component result = obfuscator.obfuscate(message, Set.of(KILLER_ID));

        assertFalse(plain(result).contains("Steve"));
    }

    @Test
    void retourneLeMessageInchangeQuandAucuneCible() {
        Component message = deathMessage();

        assertSame(message, obfuscator.obfuscate(message, Set.of()));
    }

    @Test
    void neTouchePasUnMessageSansCiblePresente() {
        Component message = deathMessage();
        UUID absent = UUID.fromString("00000000-0000-0000-0000-000000000009");

        Component result = obfuscator.obfuscate(message, Set.of(absent));

        assertEquals("Steve", plain(((TranslatableComponent) result).arguments().get(1).asComponent()));
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./gradlew test --tests 'fr.zeffut.anonymouspotion.NameObfuscatorTest'
```

Attendu : échec de compilation, `NameObfuscator` n'existe pas encore.

- [ ] **Step 3: Écrire `NameObfuscator.java`**

```java
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
```

Le champ `obfuscateWeaponName` n'est pas encore utilisé : il l'est en tâche 3. Le déclarer
dès maintenant évite de changer la signature du constructeur entre deux tâches.

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

```bash
./gradlew test --tests 'fr.zeffut.anonymouspotion.NameObfuscatorTest'
```

Attendu : `BUILD SUCCESSFUL`, 10 tests passés.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/zeffut/anonymouspotion/NameObfuscator.java src/test/java/fr/zeffut/anonymouspotion/NameObfuscatorTest.java
git commit -m "feat: brouillage du pseudo des joueurs ciblés dans un message Adventure"
```

---

### Task 3: NameObfuscator — collecte des UUID et brouillage de l'arme

**Files:**
- Modify: `src/main/java/fr/zeffut/anonymouspotion/NameObfuscator.java`
- Modify: `src/test/java/fr/zeffut/anonymouspotion/NameObfuscatorTest.java`

**Interfaces:**
- Consumes: `NameObfuscator(int, char, boolean)` et `obfuscate(Component, Set<UUID>)` de la tâche 2.
- Produces:
  - `static Set<UUID> collectEntityIds(Component message)` — tous les UUID d'entités mentionnées
    dans le message. Le listener s'en sert pour savoir qui filtrer.
  - Le brouillage des nœuds d'item quand `obfuscateWeaponName` vaut `true`, rendu
    `[XXXXXXXX]` avec les crochets conservés en clair.

**Contexte.** Quand le tueur frappe avec un objet au nom personnalisé, le serveur émet
`death.attack.player.item` avec un troisième argument : le nœud de l'item, reconnaissable à
son `hoverEvent` de type `SHOW_ITEM`. Ce survol contient les NBT de l'objet, donc son nom
complet — il faut le supprimer comme pour un pseudo.

Le nom de la méthode dit « entity » et non « player » parce que le survol `SHOW_ENTITY`
existe pour n'importe quelle entité, pas seulement les joueurs. Le tri entre joueurs et mobs
se fait dans le listener, qui a accès à Bukkit.

- [ ] **Step 1: Écrire les tests qui échouent**

Ajouter dans `NameObfuscatorTest`. Aucun nouvel import n'est nécessaire : `Key`, `HoverEvent`,
`Set`, `UUID` et les assertions utilisées sont déjà importés par la tâche 2.

```java
    /** Reproduit le nœud d'un objet nommé, tel qu'affiché dans « ... avec [Excalibur] ». */
    private static Component namedItem(String itemName) {
        return Component.text()
                .append(Component.text("["))
                .append(Component.text(itemName))
                .append(Component.text("]"))
                .hoverEvent(HoverEvent.showItem(Key.key("minecraft:diamond_sword"), 1))
                .build();
    }

    private static Component deathMessageWithItem(String itemName) {
        return Component.translatable("death.attack.player.item",
                playerName("Zeffut", VICTIM_ID),
                playerName("Steve", KILLER_ID),
                namedItem(itemName));
    }

    @Test
    void collecteLesUuidDesEntitesMentionnees() {
        Set<UUID> ids = NameObfuscator.collectEntityIds(deathMessage());

        assertEquals(Set.of(VICTIM_ID, KILLER_ID), ids);
    }

    @Test
    void collecteLesUuidDansLesArgumentsImbriques() {
        Component message = Component.translatable("death.attack.player",
                playerName("Zeffut", VICTIM_ID),
                Component.translatable("chat.square_brackets", playerName("Steve", KILLER_ID)));

        assertEquals(Set.of(VICTIM_ID, KILLER_ID), NameObfuscator.collectEntityIds(message));
    }

    @Test
    void collecteUnEnsembleVideQuandAucuneEntite() {
        assertTrue(NameObfuscator.collectEntityIds(Component.text("Zeffut est tombé de haut")).isEmpty());
    }

    @Test
    void brouilleLeNomDArmeQuandLOptionEstActive() {
        Component result = obfuscator.obfuscate(deathMessageWithItem("Excalibur"), Set.of(KILLER_ID));

        assertFalse(plain(result).contains("Excalibur"));
        assertTrue(plain(result).contains("[aaaaaaaa]"));
    }

    @Test
    void supprimeLeSurvolDuNoeudDArmeBrouille() {
        Component result = obfuscator.obfuscate(deathMessageWithItem("Excalibur"), Set.of(KILLER_ID));
        Component item = ((TranslatableComponent) result).arguments().get(2).asComponent();

        assertNull(item.hoverEvent());
    }

    @Test
    void laisseLeNomDArmeQuandLOptionEstDesactivee() {
        NameObfuscator sansArme = new NameObfuscator(8, 'a', false);

        Component result = sansArme.obfuscate(deathMessageWithItem("Excalibur"), Set.of(KILLER_ID));

        assertTrue(plain(result).contains("Excalibur"));
        assertFalse(plain(result).contains("Steve"));
    }

    @Test
    void neBrouillePasLArmeSansJoueurCible() {
        Component message = deathMessageWithItem("Excalibur");

        assertSame(message, obfuscator.obfuscate(message, Set.of()));
    }
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./gradlew test --tests 'fr.zeffut.anonymouspotion.NameObfuscatorTest'
```

Attendu : échec de compilation, `collectEntityIds` n'existe pas.

- [ ] **Step 3: Ajouter la collecte et le brouillage d'item dans `NameObfuscator`**

Ajouter l'import `java.util.HashSet`, puis les trois méthodes suivantes, et brancher le
brouillage d'item dans `walk`.

Dans `walk`, juste après le bloc qui traite le pseudo ciblé :

```java
        if (obfuscateWeaponName && isItem(input)) {
            return obfuscatedItem(input);
        }
```

Nouvelles méthodes :

```java
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
```

- [ ] **Step 4: Lancer toute la suite de tests**

```bash
./gradlew test
```

Attendu : `BUILD SUCCESSFUL`, 17 tests passés.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/zeffut/anonymouspotion/NameObfuscator.java src/test/java/fr/zeffut/anonymouspotion/NameObfuscatorTest.java
git commit -m "feat: collecte des UUID d'entités et brouillage des armes nommées"
```

---

### Task 4: Configuration validée

**Files:**
- Create: `src/main/java/fr/zeffut/anonymouspotion/AnonymousPotionConfig.java`
- Create: `src/main/resources/config.yml`
- Test: `src/test/java/fr/zeffut/anonymouspotion/AnonymousPotionConfigTest.java`

**Interfaces:**
- Consumes: rien.
- Produces:
  - `record AnonymousPotionConfig(int obfuscatedLength, char fillerCharacter, boolean obfuscateWeaponName, boolean logRealDeathMessage)`
  - `static AnonymousPotionConfig of(int obfuscatedLength, String fillerCharacter, boolean obfuscateWeaponName, boolean logRealDeathMessage, Consumer<String> warnings)`
    — valide les entrées, retombe sur les défauts en signalant chaque correction via `warnings`.
  - Constantes `DEFAULT_LENGTH = 8`, `DEFAULT_FILLER = 'a'`, `MIN_LENGTH = 1`, `MAX_LENGTH = 32`.

**Contexte.** La validation prend des types Java bruts et un `Consumer<String>` plutôt qu'un
`FileConfiguration` Bukkit. C'est ce qui rend la classe testable sans serveur : la lecture du
YAML reste dans le plugin, où elle est triviale, et toute la logique de validation vit ici.

- [ ] **Step 1: Écrire les tests qui échouent**

```java
package fr.zeffut.anonymouspotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnonymousPotionConfigTest {

    private final List<String> warnings = new ArrayList<>();

    private AnonymousPotionConfig load(int length, String filler) {
        return AnonymousPotionConfig.of(length, filler, true, true, warnings::add);
    }

    @Test
    void accepteDesValeursValides() {
        AnonymousPotionConfig config = AnonymousPotionConfig.of(12, "x", false, false, warnings::add);

        assertEquals(12, config.obfuscatedLength());
        assertEquals('x', config.fillerCharacter());
        assertFalse(config.obfuscateWeaponName());
        assertFalse(config.logRealDeathMessage());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void ramoneUneLongueurTropPetiteAuDefaut() {
        AnonymousPotionConfig config = load(0, "a");

        assertEquals(AnonymousPotionConfig.DEFAULT_LENGTH, config.obfuscatedLength());
        assertEquals(1, warnings.size());
    }

    @Test
    void ramoneUneLongueurTropGrandeAuDefaut() {
        AnonymousPotionConfig config = load(500, "a");

        assertEquals(AnonymousPotionConfig.DEFAULT_LENGTH, config.obfuscatedLength());
        assertEquals(1, warnings.size());
    }

    @Test
    void accepteLesBornesDeLongueur() {
        assertEquals(AnonymousPotionConfig.MIN_LENGTH, load(AnonymousPotionConfig.MIN_LENGTH, "a").obfuscatedLength());
        assertEquals(AnonymousPotionConfig.MAX_LENGTH, load(AnonymousPotionConfig.MAX_LENGTH, "a").obfuscatedLength());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void refuseUnRemplissageDePlusieursCaracteres() {
        AnonymousPotionConfig config = load(8, "abc");

        assertEquals(AnonymousPotionConfig.DEFAULT_FILLER, config.fillerCharacter());
        assertEquals(1, warnings.size());
    }

    @Test
    void refuseUnRemplissageVide() {
        AnonymousPotionConfig config = load(8, "");

        assertEquals(AnonymousPotionConfig.DEFAULT_FILLER, config.fillerCharacter());
        assertEquals(1, warnings.size());
    }

    @Test
    void refuseUnRemplissageAbsent() {
        AnonymousPotionConfig config = load(8, null);

        assertEquals(AnonymousPotionConfig.DEFAULT_FILLER, config.fillerCharacter());
        assertEquals(1, warnings.size());
    }

    @Test
    void signaleChaqueProblemeSeparement() {
        load(0, "abc");

        assertEquals(2, warnings.size());
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./gradlew test --tests 'fr.zeffut.anonymouspotion.AnonymousPotionConfigTest'
```

Attendu : échec de compilation, `AnonymousPotionConfig` n'existe pas.

- [ ] **Step 3: Écrire `AnonymousPotionConfig.java`**

```java
package fr.zeffut.anonymouspotion;

import java.util.function.Consumer;

/**
 * Réglages du plugin, déjà validés. Ne dépend pas de Bukkit : le plugin lit le YAML et
 * passe ici des valeurs brutes, ce qui rend la validation testable sans serveur.
 */
public record AnonymousPotionConfig(
        int obfuscatedLength,
        char fillerCharacter,
        boolean obfuscateWeaponName,
        boolean logRealDeathMessage) {

    public static final int DEFAULT_LENGTH = 8;
    public static final char DEFAULT_FILLER = 'a';
    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 32;

    /**
     * Construit une configuration en corrigeant les valeurs invalides. Chaque correction est
     * signalée à {@code warnings} : une valeur douteuse ne doit jamais empêcher le démarrage.
     */
    public static AnonymousPotionConfig of(
            int obfuscatedLength,
            String fillerCharacter,
            boolean obfuscateWeaponName,
            boolean logRealDeathMessage,
            Consumer<String> warnings) {

        int length = obfuscatedLength;
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            warnings.accept("obfuscated-length doit être entre " + MIN_LENGTH + " et " + MAX_LENGTH
                    + " (valeur lue : " + obfuscatedLength + "). Utilisation de " + DEFAULT_LENGTH + ".");
            length = DEFAULT_LENGTH;
        }

        char filler = DEFAULT_FILLER;
        if (fillerCharacter == null || fillerCharacter.length() != 1) {
            warnings.accept("filler-character doit faire exactement un caractère (valeur lue : "
                    + fillerCharacter + "). Utilisation de '" + DEFAULT_FILLER + "'.");
        } else {
            filler = fillerCharacter.charAt(0);
        }

        return new AnonymousPotionConfig(length, filler, obfuscateWeaponName, logRealDeathMessage);
    }
}
```

- [ ] **Step 4: Écrire `src/main/resources/config.yml`**

```yaml
# AnonymousPotion — brouille le pseudo des joueurs invisibles dans les messages de mort.

# Nombre de caractères du texte brouillé. Identique pour tous les joueurs : une longueur
# variable trahirait l'identité du tueur sur un serveur peu peuplé. Entre 1 et 32.
obfuscated-length: 8

# Caractère de base du brouillage. Minecraft remplace chaque caractère par un glyphe
# aléatoire de MÊME LARGEUR, donc ce choix fixe la largeur affichée à l'écran.
# 'a' correspond à la largeur d'une lettre standard.
filler-character: 'a'

# Brouiller aussi le nom des armes personnalisées, qui peuvent trahir le tueur.
# Ne s'applique qu'aux messages où un joueur invisible est déjà brouillé.
obfuscate-weapon-name: true

# Écrire le vrai message de mort dans les logs serveur, pour la modération.
log-real-death-message: true
```

- [ ] **Step 5: Lancer toute la suite de tests**

```bash
./gradlew test
```

Attendu : `BUILD SUCCESSFUL`, 25 tests passés.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/fr/zeffut/anonymouspotion/AnonymousPotionConfig.java src/main/resources/config.yml src/test/java/fr/zeffut/anonymouspotion/AnonymousPotionConfigTest.java
git commit -m "feat: configuration validée du plugin"
```

---

### Task 5: Listener de mort et câblage du plugin

**Files:**
- Create: `src/main/java/fr/zeffut/anonymouspotion/DeathMessageListener.java`
- Modify: `src/main/java/fr/zeffut/anonymouspotion/AnonymousPotionPlugin.java`

**Interfaces:**
- Consumes:
  - `NameObfuscator(int, char, boolean)`, `obfuscate(Component, Set<UUID>)`, `collectEntityIds(Component)`
  - `AnonymousPotionConfig.of(int, String, boolean, boolean, Consumer<String>)` et ses accesseurs
    `obfuscatedLength()`, `fillerCharacter()`, `obfuscateWeaponName()`, `logRealDeathMessage()`
- Produces:
  - `DeathMessageListener(AnonymousPotionPlugin plugin)` — se relit la config du plugin à chaque mort.
  - `AnonymousPotionPlugin#config()` retournant l'`AnonymousPotionConfig` courant.
  - `AnonymousPotionPlugin#reloadSettings()` — recharge le YAML et remplace la config en mémoire.
    Utilisé par la commande de la tâche 6.

**Contexte.** Le listener applique la règle unique de la spec : tout joueur invisible mentionné
est brouillé, sauf la victime. Il n'utilise volontairement pas `event.getEntity().getKiller()`,
qui est nul dans les morts indirectes (« a essayé de nager dans la lave pour échapper à… »).
Scanner le message couvre ces cas sans les énumérer.

Le listener lit la config via `plugin.config()` à chaque événement plutôt que de la capturer
dans un champ : c'est ce qui fait que `/anonymouspotion reload` prend effet immédiatement,
sans réenregistrer le listener.

- [ ] **Step 1: Écrire `DeathMessageListener.java`**

Pas de test unitaire ici : la classe n'est qu'un branchement entre Bukkit et les deux classes
déjà couvertes par 25 tests. Elle se vérifie par la checklist en jeu de la tâche 6.

```java
package fr.zeffut.anonymouspotion;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

            if (config.logRealDeathMessage()) {
                plugin.getLogger().info("Vrai message de mort : "
                        + PlainTextComponentSerializer.plainText().serialize(message));
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
```

- [ ] **Step 2: Câbler le plugin**

Remplacer `AnonymousPotionPlugin.java` par :

```java
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
                file.getBoolean("log-real-death-message", true),
                getLogger()::warning);
    }
}
```

- [ ] **Step 3: Vérifier que tout compile et que les tests passent**

```bash
./gradlew build
```

Attendu : `BUILD SUCCESSFUL`, 25 tests passés, `build/libs/AnonymousPotion.jar` régénéré.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/fr/zeffut/anonymouspotion/DeathMessageListener.java src/main/java/fr/zeffut/anonymouspotion/AnonymousPotionPlugin.java
git commit -m "feat: brouillage des messages de mort des joueurs invisibles"
```

---

### Task 6: Commande de rechargement et documentation

**Files:**
- Create: `src/main/java/fr/zeffut/anonymouspotion/ReloadCommand.java`
- Modify: `src/main/java/fr/zeffut/anonymouspotion/AnonymousPotionPlugin.java`
- Create: `README.md`

**Interfaces:**
- Consumes: `AnonymousPotionPlugin#reloadSettings()` de la tâche 5.
- Produces: `/anonymouspotion reload`, exécuteur enregistré dans `onEnable`.

- [ ] **Step 1: Écrire `ReloadCommand.java`**

```java
package fr.zeffut.anonymouspotion;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class ReloadCommand implements CommandExecutor {

    private final AnonymousPotionPlugin plugin;

    public ReloadCommand(AnonymousPotionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Component.text("Usage : /" + label + " reload", NamedTextColor.RED));
            return true;
        }

        plugin.reloadSettings();
        sender.sendMessage(Component.text("Configuration d'AnonymousPotion rechargée.", NamedTextColor.GREEN));
        return true;
    }
}
```

- [ ] **Step 2: Enregistrer la commande dans `onEnable`**

Dans `AnonymousPotionPlugin#onEnable`, après l'enregistrement du listener :

```java
        java.util.Objects.requireNonNull(getCommand("anonymouspotion"))
                .setExecutor(new ReloadCommand(this));
```

- [ ] **Step 3: Vérifier que tout compile et que les tests passent**

```bash
./gradlew build
```

Attendu : `BUILD SUCCESSFUL`, 25 tests passés.

- [ ] **Step 4: Écrire le `README.md`**

````markdown
# AnonymousPotion

Plugin Paper 1.21.x. Quand un joueur est sous effet **Invisibilité**, son pseudo n'apparaît
jamais dans les messages de mort : il est remplacé par un texte au formatage *obfuscated*,
que le client anime en faisant défiler des caractères aléatoires.

Seule exception : quand le joueur invisible **meurt**, son propre pseudo s'affiche en clair.

```
Zeffut a été tué par ▓╫≡┼╪▒≈╬
Zeffut a essayé de nager dans la lave pour échapper à ▓╫≡┼╪▒≈╬
Steve a été tué par Zeffut          ← Zeffut est invisible, mais il est la victime
```

Seuls les messages de mort sont concernés. Le chat, les connexions, les avancements, la tab
list et le nametag restent vanilla. Aucun joueur ne voit le vrai pseudo, y compris les OP ;
les logs serveur le conservent pour la modération.

## Installation

Déposer `AnonymousPotion.jar` dans le dossier `plugins/`, puis redémarrer le serveur.

## Configuration

`plugins/AnonymousPotion/config.yml`

| Clé | Défaut | Rôle |
|---|---|---|
| `obfuscated-length` | `8` | Nombre de caractères du brouillage, entre 1 et 32. Fixe pour tous les joueurs : une longueur variable trahirait le tueur. |
| `filler-character` | `a` | Caractère de base. Minecraft remplace chaque caractère par un glyphe de même largeur, ce réglage fixe donc la largeur affichée. |
| `obfuscate-weapon-name` | `true` | Brouille aussi le nom des armes personnalisées, qui peuvent trahir le tueur. |
| `log-real-death-message` | `true` | Écrit le vrai message de mort dans les logs serveur. |

`/anonymouspotion reload` recharge à chaud (permission `anonymouspotion.admin`, OP par défaut).

## Compilation

```bash
./gradlew build
```

Le jar est produit dans `build/libs/AnonymousPotion.jar`.

## Checklist de test en jeu

À dérouler sur un serveur Paper 1.21.x avec deux comptes.

1. **Kill à l'épée** — le tueur boit une potion d'invisibilité, tue la victime :
   le pseudo du tueur défile, celui de la victime est lisible.
2. **Kill à l'arc** — même chose à distance : le pseudo du tueur défile.
3. **Mort indirecte** — poursuivre la victime jusqu'à ce qu'elle meure dans la lave :
   le message « pour échapper à … » brouille le poursuivant.
4. **Mort de l'invisible** — le joueur invisible se fait tuer : **son pseudo s'affiche en clair**.
5. **Survol du pseudo brouillé** — passer la souris dessus dans le chat : aucune infobulle,
   aucun nom, et le clic ne pré-remplit pas `/tell`.
6. **Arme nommée** — tuer avec une épée renommée : le nom de l'arme défile lui aussi.
   Passer `obfuscate-weapon-name` à `false`, lancer `/anonymouspotion reload`, retuer :
   le nom de l'arme redevient lisible, le pseudo reste brouillé.
7. **Logs** — vérifier que la console contient bien la ligne
   `[AnonymousPotion] Vrai message de mort : …` avec le vrai pseudo.
8. **Sans invisibilité** — un kill normal produit un message de mort strictement vanilla.
````

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/zeffut/anonymouspotion/ReloadCommand.java src/main/java/fr/zeffut/anonymouspotion/AnonymousPotionPlugin.java README.md
git commit -m "feat: commande de rechargement et documentation"
```
