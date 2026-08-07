# AnonymousPotion

A player under **Invisibility** never has their username shown in a death message. It is replaced with *obfuscated* text — the scrambling characters Minecraft animates client-side.

One exception: when the invisible player **dies**, their own name is shown in plain text.

```
Zeffut was slain by ▓╫≡┼╪▒≈╬
Zeffut tried to swim in lava to escape ▓╫≡┼╪▒≈╬
Steve was slain by Zeffut          ← Zeffut is invisible, but he is the victim
```

No player ever sees the real username — operators included. The gameplay is the same for everyone, and the server log keeps the real identity for moderation.

## Features

- **Every kind of death.** Melee, bow, trident, tamed wolves, indirect deaths such as *"tried to swim in lava to escape…"*. The plugin rewrites the message tree and never parses the sentence, so every death type and every client language works without a list of cases.
- **Fixed-length obfuscation.** Always the same width, whoever the killer is. A length that matched the real username would give it away on a server with few players online.
- **No leak on hover.** In vanilla, hovering a username shows the real name and UUID, and clicking it pre-fills `/tell <name>`. Both are stripped from the replacement.
- **Custom weapon names too.** A renamed sword can identify its owner just as well as a username. Obfuscated by default, switchable off.
- **Offline killers covered.** Hit someone, disconnect, and let them die of the fall — you stay anonymous for 60 seconds. Without this, quitting was a free bypass.
- **Fail-closed.** If anything throws while handling a death, the message is dropped rather than sent through. A bug must never leak a username — that is the one thing this plugin exists to prevent.
- **Nothing else is touched.** Chat, join and quit messages, advancements, the tab list and nametags all stay vanilla.

## Configuration

| Key | Default | What it does |
|---|---|---|
| `obfuscated-length` | `8` | Number of characters in the obfuscated text, 1–32. The same for every player. |
| `filler-character` | `a` | Base character. Minecraft swaps each character for a random glyph of the **same width**, so this setting fixes the width on screen. |
| `obfuscate-weapon-name` | `true` | Also obfuscate custom weapon names, which can give the killer away. |
| `log-real-names` | `true` | Write the real usernames of obfuscated players to the server log, for moderation. |
| `telemetry` | `true` | Send anonymous usage statistics. `false` turns every send off. |
| `telemetry-host` | `https://eu.i.posthog.com` | The PostHog instance the statistics are sent to. Change it to point at a self-hosted instance. |

`/anonymouspotion reload` reloads the config in place — permission `anonymouspotion.admin`, operators by default.

## Known limitations

- If the Invisibility effect expires while an arrow is in flight, the archer is named in plain text. The effect is read at the moment of death, not at the moment of the shot.
- Turning `log-real-names` off removes every trace of the killer: the server's own log shows the obfuscated name too, so the option does not merely trim moderation output, it removes it.
- The plugin rewrites the death message at `HIGHEST` priority. Another plugin that replaces the message with flat text, or that writes after this one, can bypass the obfuscation.

## Telemetry

The plugin sends anonymous usage statistics to PostHog. Set `telemetry: false` in
`config.yml` to turn it off.

**What is sent:** a random installation id generated on first start, four properties attached to
every event, plus five events.

The four properties present on every event, whichever it is:

- **`app`**: the plugin's short name, always `anonymouspotion`. It tells this plugin apart from
  other projects sharing the same PostHog project.
- **`source`**: the server platform, always `paper`.
- **`mc_version`**: the server's Minecraft version, for example `1.21.11`.
- **`component_version`**: the plugin version, for example `1.0.0`.

The body also carries `$ip` set to `null`. That is not data — it is the instruction telling
PostHog not to record the request's IP address, nor derive a geolocation from it. Without it,
the server's public IP would be stored by the service.

The five events:

- **`plugin_enabled`** (on startup): server version, `obfuscated-length`,
  `obfuscate-weapon-name`, `log-real-names`, and the server's online mode (a server setting,
  not a plugin setting).
- **`death_obfuscated`** (each death where a name was obfuscated): how many names were
  obfuscated, whether a weapon name was actually obfuscated at that death, whether one of the
  killers was offline, and the kind of death (the vanilla translation key, never the rendered
  text).
- **`obfuscation_failed`** (if obfuscation fails): the Java exception type, never its message.
- **`command_used`** (each `/anonymouspotion` command): the subcommand used (`reload`).
- **`session_heartbeat`** (every 30 minutes): server uptime and how many deaths have been
  obfuscated since startup.

**What is never sent:** no usernames, no IP addresses, no player UUIDs, no message contents.
This plugin exists to stop a username from leaking — it is not going to ship those same
usernames somewhere else.

## Requirements

Paper 1.21.x, Java 21. No dependencies.
