# Configuration Files

KaGuilds runtime files are stored in `plugins/KaGuilds/`.

| Path | Purpose | Documentation |
| --- | --- | --- |
| `config.yml` | Database, proxy, economy, teleport, task display, and menu defaults | [Main configuration](config.md) |
| `levels.yml` | Levels, member limits, bank limits, interest, vaults, and buff unlocks | [Level configuration](levels.md) |
| `buffs.yml` | Buff type, price, amplifier, duration, and name | [Buff configuration](buffs.md) |
| `task.yml` | Daily tasks, global tasks, events, and reward actions | [Task configuration](task.md) |
| `lang/zh_CN.yml` | Chinese messages | - |
| `lang/en_US.yml` | English messages | - |
| `gui/*.yml` | Menu layouts, icons, conditions, and actions | [Custom menus](../menu/README.md) |
| `arena.yml` | PvP region, spawn points, and kits | - |
| `storage.db` | SQLite database for single-server mode | - |

## Editing Guidance

1. Back up a file before changing it.
2. Preserve YAML indentation and use spaces instead of tabs.
3. Keep menus, languages, and functional configuration aligned across all backends.
4. Restart the server after changing the database type, connection settings, or proxy mode.
5. Use `/kg reload` for ordinary configuration changes, but never reload the whole plugin with PlugMan.

{% hint style="warning" %}
`/kg admin release <CN|EN>` extracts built-in menus again. Back up `gui` first so customized files are not lost.
{% endhint %}
