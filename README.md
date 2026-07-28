# KaGuilds

[![License](https://img.shields.io/github/license/katacr/KaGuilds)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.1.0-blue)](https://github.com/katacr/KaGuilds)
[![Minecraft](https://img.shields.io/badge/compile%20baseline-Spigot%201.16.5-brightgreen)](https://www.spigotmc.org/)

[简体中文](README_CN.md)

KaGuilds is a guild plugin for Spigot, Paper, and Velocity-backed networks. It provides guild membership, economy, shared vaults, tasks, PvP, configurable menus, and Chinese and English localization.

## Features

- Guild creation, join requests, invitations, three member roles, ownership transfer, and deletion
- Guild bank, paginated transaction logs, contribution points, levels, and configurable buffs
- Personal daily tasks and guild-wide global tasks with persistent reward-claim protection
- Shared guild vaults with database-backed lease locks
- Guild chat, teleport locations, guild icons, announcements, and PlaceholderAPI integration
- Guild PvP arena with team kits, preparation, match timing, statistics, and reward commands
- YAML GUI menus and Chinese or English language files
- Cross-server chat, invitations, notifications, and cache synchronization through the KaProxy Guilds module

## Compatibility

| Item | Current requirement |
| --- | --- |
| Minecraft API | Compiled against Spigot API 1.16.5; validate newer versions on the exact target server |
| Java | Targets Java 12 bytecode; Java must also meet the server software requirement |
| Server | Spigot, Paper, or a compatible fork |
| Network proxy | Velocity with the KaProxy Guilds module |
| Database | SQLite or MySQL for one server; shared MySQL for multiple servers |
| Economy | Vault and a Vault-compatible economy provider |
| Optional | PlaceholderAPI |

Folia support is currently a proposal and is not enabled in this version.

## Installation

1. Install Vault and a compatible economy plugin on the backend server.
2. Place the KaGuilds JAR in the backend server's `plugins` directory.
3. Start the server once to generate `plugins/KaGuilds` and the database schema.
4. Stop the server, edit the generated configuration, and start it again.
5. Install PlaceholderAPI when KaGuilds placeholders are needed.

Do not load or reload KaGuilds with PlugMan or similar hot-loading tools. Back up the database and `plugins/KaGuilds` before upgrades.

## Multi-Server Setup

Every backend server must:

- Run the same KaGuilds version;
- Connect to the same MySQL database;
- Set `proxy: true`;
- Use a unique and stable `server-id`.

Install KaProxy only on Velocity and enable its Guilds module and legacy channel support. Do not run KaProxy and the former KaGuildsProxy plugin together. Backend servers should accept player connections only through Velocity.

See [Velocity Setup](docs-en/home/velocity.md) for the full procedure and verification checklist.

## Commands

The primary command is `/kaguilds`, with `/kg` and `/guild` as aliases.

```text
/kg help [page]
/kg create <name>
/kg join <name|#ID>
/kg info
/kg menu
/kg chat [message]
/kg bank <add|take|log> ...
/kg pvp <start|accept|ready|exit> ...
/kg admin help [page]
```

Several player operations, including create, delete, leave, kick, transfer, and rename, require `/kg confirm`. Server admin delete and transfer commands execute immediately.

See [Player Commands](docs-en/perm/player-commands.md), [Admin Commands](docs-en/perm/admin-commands.md), and [Permissions](docs-en/perm/permissions.md).

## Configuration

Runtime files are stored in `plugins/KaGuilds/`:

| Path | Purpose |
| --- | --- |
| `config.yml` | Database, proxy, economy, teleport, task display, and common settings |
| `levels.yml` | Level requirements, limits, interest, vaults, and buff unlocks |
| `buffs.yml` | Buff effects, price, amplifier, duration, and display name |
| `task.yml` | Daily and global task definitions and reward actions |
| `lang/*.yml` | Player-facing messages |
| `gui/*.yml` | Menu layout, display, conditions, and actions |
| `arena.yml` | PvP region, spawns, and team kits |

See the [English documentation](docs-en/README.md) or [Chinese documentation](docs/README.md).

## Build

The project uses Gradle and requires a JDK capable of compiling for Java 12:

```bash
bash ./gradlew shadowJar
```

On Windows:

```bat
gradlew.bat shadowJar
```

The output JAR is written to `build/libs/`.

## License

KaGuilds is distributed under the [GPL-3.0 License](LICENSE).
