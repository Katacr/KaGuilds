---
description: KaGuilds usage and configuration documentation
---

# KaGuilds

KaGuilds is a guild plugin for Spigot, Paper, and multi-server networks. It provides guild membership, economy, tasks, shared vaults, PvP, menus, and PlaceholderAPI integration.

## Main Features

- Guild creation, requests, invitations, roles, ownership transfer, and deletion
- Guild bank, transaction logs, contribution points, levels, and buffs
- Personal daily tasks and guild-wide global tasks
- Shared guild vaults protected by lease-based locking
- Configurable guild chat, teleport points, and PvP arena
- YAML menus, Chinese and English language files, and PlaceholderAPI placeholders
- Multi-server message synchronization through the KaProxy Guilds module

## Deployment Modes

| Mode | Database | Proxy setting | Use case |
| --- | --- | --- | --- |
| Single server | SQLite or MySQL | `proxy: false` | Standalone Spigot/Paper server |
| Multi-server | Shared MySQL | `proxy: true` | Multiple backend servers behind Velocity |

Every backend in a multi-server deployment must use the same MySQL database and a unique `server-id`. See [Velocity Setup](home/velocity.md) for the complete procedure.

## Compatibility

- The plugin targets Java 12 bytecode. The selected Java version must also satisfy the server software.
- Spigot API 1.16.5 is the current compile baseline. Test every newer Minecraft version on the exact target server.
- Folia support is still a proposal. Do not deploy the current version on Folia.
- Vault and an economy provider are required for economy features. PlaceholderAPI is optional.

## Documentation

- [Getting Started](home/start.md)
- [Configuration Files](config/README.md)
- [Commands and Permissions](perm/README.md)
- [Custom Menus](menu/README.md)
- [PlaceholderAPI](PlaceholderAPI.md)

{% hint style="warning" %}
Back up the database and `plugins/KaGuilds` before production upgrades. Do not hot-load KaGuilds with PlugMan or similar plugin loaders.
{% endhint %}

## Project

- [GitHub Repository](https://github.com/Katacr/KaGuilds/)
- [Issue Tracker](https://github.com/Katacr/KaGuilds/issues)
- [GPL-3.0 License](https://www.gnu.org/licenses/gpl-3.0.html)
