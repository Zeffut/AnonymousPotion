# AnonymousPotion

Plugin Paper 1.21.11 et versions Paper stables ciblées. Quand un joueur est sous effet **Invisibilité**, son pseudo n'apparaît
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
les logs serveur le conservent pour la modération tant que `log-real-names` est actif.

## Installation

Déposer `AnonymousPotion.jar` dans le dossier `plugins/`, puis redémarrer le serveur.

## Configuration

`plugins/AnonymousPotion/config.yml`

| Clé | Défaut | Rôle |
|---|---|---|
| `obfuscated-length` | `8` | Nombre de caractères du brouillage, entre 1 et 32. Fixe pour tous les joueurs : une longueur variable trahirait le tueur. |
| `filler-character` | `a` | Caractère de base. Minecraft remplace chaque caractère par un glyphe de même largeur, ce réglage fixe donc la largeur affichée. |
| `obfuscate-weapon-name` | `true` | Brouille aussi le nom des armes personnalisées, qui peuvent trahir le tueur. |
| `log-real-names` | `true` | Écrit dans les logs serveur le pseudo réel des joueurs brouillés. |
| `telemetry` | `true` | Envoi de statistiques d'usage anonymes. `false` coupe tout envoi. |
| `telemetry-host` | `https://eu.i.posthog.com` | Adresse de l'instance PostHog destinataire. À changer pour viser une instance auto-hébergée. |

`/anonymouspotion reload` recharge à chaud (permission `anonymouspotion.admin`, OP par défaut).

## Télémétrie

Le plugin envoie des statistiques d'usage anonymes à PostHog. `telemetry: false` dans
`config.yml` les désactive.

**Ce qui est envoyé :** un identifiant d'installation tiré au hasard au premier démarrage,
quatre propriétés jointes à tous les événements, plus cinq événements.

Les quatre propriétés présentes sur chaque événement, quel qu'il soit :

- **`app`** : le nom court du plugin, toujours `anonymouspotion`. Il distingue ce plugin des
  autres projets qui partagent le même projet PostHog.
- **`source`** : la plateforme du serveur, toujours `paper`.
- **`mc_version`** : la version de Minecraft du serveur, par exemple `1.21.11`.
- **`component_version`** : la version du plugin, par exemple `1.0.0`.

Le corps envoyé porte en plus `$ip` à `null` : ce n'est pas une donnée, c'est la consigne qui
demande à PostHog de ne pas relever l'adresse IP de la requête ni d'en déduire une
géolocalisation. Sans elle, l'IP publique du serveur serait enregistrée par le service.

Les cinq événements :

- **`plugin_enabled`** (au démarrage) : version du serveur, `obfuscated-length`,
  `obfuscate-weapon-name`, `log-real-names`, et le mode en ligne du serveur (un réglage du
  serveur, pas du plugin).
- **`death_obfuscated`** (à chaque mort dont le pseudo a été brouillé) : nombre de pseudos
  brouillés, si un nom d'arme a effectivement été brouillé à cette mort-là, si l'un des tueurs
  était hors ligne, et le type de mort (la clé de traduction vanilla, jamais le texte affiché).
- **`obfuscation_failed`** (en cas d'échec du brouillage) : le type de l'erreur Java, jamais
  son message.
- **`command_used`** (à chaque commande `/anonymouspotion`) : la sous-commande utilisée
  (`reload`).
- **`session_heartbeat`** (toutes les 30 minutes) : la durée de fonctionnement du serveur et
  le nombre de morts brouillées depuis le démarrage.

**Ce qui ne l'est jamais :** aucun pseudo, aucune adresse IP, aucun UUID de joueur, aucun
contenu de message. Ce plugin existe pour empêcher un pseudo de fuiter — il ne va pas
expédier ces mêmes pseudos ailleurs.

## Limitations connues

**L'invisibilité est lue au moment de la mort.** Si l'effet expire pendant le vol d'une flèche,
l'archer est nommé en clair : à l'instant du message, il n'est plus invisible. C'est le
comportement voulu — l'état affiché est celui de la mort, pas celui du tir.

**Un joueur qui se déconnecte invisible reste brouillé une minute.** Passé ce délai, s'il est
encore cité dans un message de mort, son pseudo réapparaît en clair : la mémoire des joueurs
partis est volontairement courte.

**`log-real-names: false` supprime toute trace du tueur.** Le log serveur affiche lui aussi le
pseudo brouillé — l'identité réelle n'existe nulle part ailleurs. Désactiver cette option
n'allège donc pas la modération : elle la rend impossible.

## Compatibilité

Le plugin réécrit le message de mort en priorité `HIGHEST`, la dernière avant `MONITOR`. Un
plugin tiers qui remplace le message de mort par du texte plat — sans les nœuds d'entité qui
portent les UUID — ou qui écrit après nous, contourne le brouillage. En cas de doute, dérouler
la checklist ci-dessous avec les autres plugins chargés.

## Compilation

```bash
./gradlew build
```

Le jar est produit dans `build/libs/AnonymousPotion.jar`.

Les cibles Paper vérifiées et leurs toolchains Java sont documentées dans
[`docs/paper-target-support.md`](docs/paper-target-support.md). Pour construire une cible
26.x explicitement, par exemple :

```bash
./gradlew clean test build -PpaperTarget=26.2
```

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
   `[AnonymousPotion] Mort de <victime> — pseudo(s) brouillé(s) : <vrai pseudo>`.
8. **Sans invisibilité** — un kill normal produit un message de mort strictement vanilla.
9. **Déconnexion du tueur** — invisible, frapper la victime puis se déconnecter aussitôt ;
   laisser la victime mourir de sa chute ou du poison dans la minute : le pseudo du tueur
   déconnecté est brouillé lui aussi.
