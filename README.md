# ChestLootAlert

Paper plugin that alerts the server when someone loots or modifies another player's chest, barrel, or shulker — with exact item names and amounts.

## Requirements

- Paper 26.2+ server
- Java 25+ to build (Paper API 26.2; JDK 21 is used as a fallback by the build script)

## Features

- **Ownership tracking** — the player who places a chest, barrel, shulker box, or copper chest becomes its owner.
- **Item diff alerts** — when a non-owner opens a tracked container, the plugin snapshots its contents and, on close, reports exactly what was taken and what was added (item name + amount).
- **Break alerts** — alerts when a non-owner destroys a tracked container, including its coordinates.
- **Discord webhook** — mirrors alerts to a Discord channel via an incoming webhook, sent asynchronously so it never blocks the main thread.
- **Autosave** — ownership data is saved periodically and on shutdown.
- **Copper chests, barrels, and every shulker box color** are tracked; ender chests are always ignored (they're personal storage).

## Build

No global Maven install required — the repo includes a Maven Wrapper.

```powershell
.\build.ps1 package
```

Output: `target/ChestLootAlert.jar` — copy it into your server's `plugins/` folder.

## Configuration

`config.yml`:

| Key | Type | Description |
|---|---|---|
| `webhook-url` | string | Discord incoming webhook URL. Empty (`""`) disables Discord alerts. |
| `autosave-interval-minutes` | int | How often ownership data is saved to disk (minimum 1). Also saved on plugin disable. |
| `alerts.chest-open` | boolean | Toggle loot/add alerts. |
| `alerts.chest-break` | boolean | Toggle break alerts. |
| `messages.item-taken` | string | Message template for items taken. Placeholders: `{player}`, `{owner}`, `{amount}`, `{item}`. |
| `messages.item-added` | string | Message template for items added. Same placeholders as above. |
| `messages.chest-broken` | string | Message template for container breaks. Placeholders: `{player}`, `{owner}`, `{x}`, `{y}`, `{z}`. |
| `owners` | map | `"world:x:y:z" -> player UUID`. Managed automatically by the plugin. |

Message templates support legacy `&` color codes (e.g. `&c`, `&f`, `&a`).

## Behavior

- **Ownership**: placing a tracked container makes you its owner. Owners can open, edit, and break their own containers without triggering any alert.
- **Loot alerts**: the first click into a container opened by a non-owner takes a snapshot of its contents. On close, the snapshot is compared slot-by-slot against the current contents to determine what was taken and what was added. Opening a container without changing anything produces no alert.
- **Break alerts**: when a non-owner breaks a tracked container, an alert is sent and the ownership entry is removed. When the owner breaks it, the entry is removed silently.

Alerts are broadcast to players holding the `chestlootalert.receive` permission (granted to everyone by default) and mirrored to Discord if a webhook is configured.

### Discord webhook setup

1. In Discord, go to your channel's **Settings → Integrations → Webhooks** and create a new webhook.
2. Copy its URL.
3. Paste it into `webhook-url` in `config.yml` and restart the server (or run `/chestlootalert reload`).

### Commands & permissions

| Command / Permission | Description |
|---|---|
| `/chestlootalert reload` | Reloads `config.yml` without restarting the server. Requires `chestlootalert.admin` (default: op). |
| `chestlootalert.receive` | Receive chat alerts. Default: everyone. |

## Migration from ChestAlertPlugin

This is a fresh rewrite, but it reads and writes the exact same `owners` section format as the legacy `ChestAlertPlugin`, so migrating an existing server only requires a folder rename:

1. Stop the server.
2. Back up `plugins/ChestAlertPlugin/config.yml`.
3. Rename the folder: `plugins/ChestAlertPlugin/` → `plugins/ChestLootAlert/`.
4. Drop in `ChestLootAlert.jar`, start the server.

No data migration is needed — `webhook-url`, `autosave-interval-minutes`, `alerts`, and `owners` all load as-is. If the config predates the `messages` section, built-in Polish defaults are used in memory without rewriting the file.

## Credits

Inspired by [ChestAlertPlugin](https://modrinth.com/plugin/chestalertplugin) by Brak. This is an independent rewrite; it does not reuse its code or history.

## License

MIT — see [LICENSE](LICENSE).
