# AnonymousPotion — Design

**Date :** 2026-08-06
**Statut :** approuvé
**Plateforme :** Paper 1.21.x (API `1.21.11-R0.1-SNAPSHOT`), Java 21

## Objectif

Un joueur sous effet Invisibilité ne doit jamais voir son pseudo apparaître dans un message
de mort. Le message vanilla est conservé, mais le pseudo du joueur invisible est remplacé
par un texte au formatage **obfuscated** d'Adventure (`§k`), que le client Minecraft anime
en faisant défiler des caractères aléatoires en continu.

Seule exception : quand le joueur invisible **meurt**, son propre pseudo s'affiche en clair.

## Règle unique

> Dans un message de mort, tout joueur mentionné qui possède l'effet Invisibilité est
> brouillé — **sauf la victime**, toujours affichée en clair.

Tous les cas en découlent sans code spécifique :

| Situation | Message affiché |
|---|---|
| Invisible tue à l'épée / à l'arc / au trident | `Zeffut a été tué par ▓╫≡┼╪▒≈╬` |
| Victime meurt en fuyant l'invisible (lave, vide, noyade) | `Zeffut a essayé de nager dans la lave pour échapper à ▓╫≡┼╪▒≈╬` |
| L'invisible meurt | `Steve a été tué par Zeffut` — pseudo de la victime en clair |
| Invisible tué par un autre invisible | victime en clair, tueur brouillé |
| Deux invisibles cités dans le même message | chacun brouillé indépendamment |
| Le loup apprivoisé d'un invisible tue | vanilla ne cite pas le propriétaire → message inchangé |

## Périmètre

**Dans le périmètre :** uniquement les messages de mort (`PlayerDeathEvent`).

**Hors périmètre, volontairement — comportement vanilla inchangé :** les messages de chat du
joueur, les messages de connexion et déconnexion, les avancements, la tab list, et le nametag
au-dessus de la tête.

Aucun joueur ne voit jamais le vrai pseudo, y compris les OP : le gameplay est identique pour
tous. Les logs serveur conservent le vrai message pour la modération.

## Approche technique

`PlayerDeathEvent#deathMessage()` expose le message en Adventure. Le message vanilla est un
arbre de composants : `translatable("death.attack.player", [victime, tueur, arme])`.

On parcourt cet arbre et on remplace le **nœud** correspondant au joueur invisible. La phrase
n'est jamais parsée, donc tous les types de mort et toutes les langues fonctionnent sans
énumération de cas.

Approches écartées :

- **Renommer temporairement le joueur** (`setDisplayName`, préfixe de team) : trivial à écrire
  mais fuite dans la tab list, le chat et les plugins tiers, et fragile en cas de kills simultanés.
- **Interception de paquets** (ProtocolLib / PacketEvents) : ne se justifierait que pour afficher
  un message différent selon le destinataire, ce que le design exclut. Dépendance externe et
  maintenance à chaque version de Minecraft.

### Identification du joueur à brouiller

Chaque nœud « pseudo » d'un message de mort porte un `hoverEvent` de type `SHOW_ENTITY`
contenant l'**UUID** du joueur. L'identification se fait par UUID, jamais par comparaison de
texte : insensible aux pseudos qui se ressemblent et aux préfixes de rang.

L'état d'invisibilité est lu au moment de l'événement via
`Player#hasPotionEffect(PotionEffectType.INVISIBILITY)`, quelle que soit l'origine de l'effet
(potion, flèche, commande, plugin tiers). Le port d'une armure, qui rend le joueur
partiellement visible en jeu, ne change rien : l'effet est présent, donc le pseudo est brouillé.

### Suppression des métadonnées du nœud

Le nœud de remplacement doit perdre son `hoverEvent`, son `clickEvent` et son `insertion`.
En vanilla, survoler un pseudo affiche une infobulle contenant le vrai nom et l'UUID, et
cliquer dessus pré-remplit `/tell <pseudo>`. Sans ce nettoyage, l'anonymat fuiterait au
premier survol de souris.

Le style hérité restant (couleur notamment) est conservé, et la décoration `OBFUSCATED` est
ajoutée.

### Texte de remplacement

`Component.text("aaaaaaaa").decorate(TextDecoration.OBFUSCATED)`

Longueur **fixe** quel que soit le joueur : sur un serveur avec peu de joueurs connectés, une
longueur variable identifierait le tueur presque à coup sûr. Le nom d'arme brouillé utilise le
même texte de remplacement et la même longueur, pour la même raison.

Minecraft remplace chaque caractère obfusqué par un glyphe aléatoire de **même largeur**. Le
caractère de remplissage détermine donc la largeur affichée ; `a` correspond à une lettre
standard.

## Architecture

Package `fr.zeffut.anonymouspotion`. Cinq classes, une responsabilité chacune.

| Classe | Rôle |
|---|---|
| `AnonymousPotionPlugin` | Bootstrap : charge la config, enregistre le listener et la commande |
| `AnonymousPotionConfig` | Lecture typée et validée du `config.yml` |
| `DeathMessageListener` | Écoute `PlayerDeathEvent`, détermine qui brouiller, log le vrai message |
| `NameObfuscator` | Logique pure : `Component` + règle de brouillage → `Component` brouillé |
| `ReloadCommand` | `/anonymouspotion reload` |

`NameObfuscator` ne dépend d'aucune API Bukkit — seulement d'Adventure. Il reçoit l'ensemble
des UUID à brouiller et les paramètres de rendu, et retourne un nouveau composant. Cette
absence de dépendance serveur le rend testable en JUnit sans démarrer de serveur, ce qui est
le point important : c'est la classe qui contient toute la complexité.

Le parcours descend récursivement dans les **enfants** (`children()`) *et* dans les
**arguments des composants traduisibles** (`TranslatableComponent#arguments()`). Oublier les
arguments laisserait passer tous les messages de mort, dont c'est justement la structure.

## Configuration

```yaml
# Nombre de caractères du texte brouillé. Identique pour tous les joueurs.
obfuscated-length: 8

# Caractère de base du brouillage. Minecraft remplace chaque caractère par un
# glyphe aléatoire de MÊME LARGEUR, donc ce choix fixe la largeur affichée.
filler-character: 'a'

# Brouiller aussi le nom des armes personnalisées, qui peuvent trahir le tueur.
# Ne s'applique qu'aux messages où un joueur invisible est déjà brouillé.
obfuscate-weapon-name: true

# Écrire le vrai message de mort dans les logs serveur, pour la modération.
log-real-death-message: true
```

**Validation :** `obfuscated-length` est ramené dans l'intervalle 1–32 ;
`filler-character` doit faire exactement un caractère, sinon retour à `a`. Toute valeur
invalide produit un avertissement dans la console et l'usage du défaut, jamais une erreur
au démarrage.

**Rechargement :** `/anonymouspotion reload`, permission `anonymouspotion.admin`, `op` par
défaut. C'est la seule commande et la seule permission du plugin.

## Journalisation

Quand `log-real-death-message` est actif et qu'un brouillage a eu lieu, le plugin écrit le
message de mort non brouillé dans les logs. Le serveur journalisant de son côté le message
tel qu'affiché, la console contient les deux lignes — celle du serveur avec le texte brouillé,
et celle du plugin avec le vrai pseudo, préfixée `[AnonymousPotion]`. Cette redondance est
volontaire et assumée : elle évite d'intercepter le mécanisme de log du serveur.

## Gestion d'erreur — fail-closed

Si le brouillage lève une exception alors qu'un joueur invisible est impliqué, le message de
mort est **supprimé** (`event.deathMessage(null)`) plutôt que laissé tel quel. Un bug ne doit
jamais faire fuiter un pseudo, ce qui est la raison d'être du plugin. L'exception est loggée
avec sa stacktrace.

Quand aucun joueur invisible n'est impliqué, l'événement n'est pas modifié.

## Tests

**JUnit 5 sur `NameObfuscator`** — la logique est pure, les composants de test sont construits
à la main :

- un nœud ciblé par UUID est remplacé, un nœud non ciblé ne l'est pas
- la victime est préservée même lorsqu'elle est invisible
- le parcours descend dans les arguments des composants traduisibles
- le parcours descend dans les enfants
- `hoverEvent`, `clickEvent` et `insertion` sont absents du nœud de remplacement
- le texte de remplacement a exactement la longueur configurée et porte `OBFUSCATED`
- le nom d'arme personnalisé est brouillé quand l'option est active, intact sinon
- un message ne contenant aucun joueur invisible ressort strictement inchangé

**Checklist de test en jeu**, documentée dans le README : boire une potion d'invisibilité,
tuer à l'épée puis à l'arc, faire mourir la cible dans la lave en la poursuivant, mourir
soi-même en étant invisible, et tuer avec une arme renommée en basculant
`obfuscate-weapon-name`.

## Build

Gradle Kotlin DSL, `group = fr.zeffut`, Java 21 via toolchain, `paper-api:1.21.11-R0.1-SNAPSHOT`
en `compileOnly`. Aucune dépendance à embarquer, donc pas de shadowJar : la tâche `jar`
standard suffit. Artefact `AnonymousPotion.jar`.

`plugin.yml` : `api-version: '1.21'`, `main: fr.zeffut.anonymouspotion.AnonymousPotionPlugin`,
`authors: [Zeffut]`.
