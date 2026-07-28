# Getting Started

This page covers a single-server KaGuilds installation. A network deployment must also follow the [Velocity Setup](velocity.md).

## Requirements

| Item | Requirement |
| --- | --- |
| Minecraft | Compiled against Spigot API 1.16.5; validate newer versions on the target server |
| Java | Java 12 or newer, while also meeting the server software requirement |
| Server | Spigot, Paper, or a compatible fork; Folia is not currently supported |
| Database | SQLite or MySQL for one server; shared MySQL is required for a network |
| Economy | Vault and a Vault-compatible economy plugin |
| Optional dependency | PlaceholderAPI |

## Installation

1. Download KaGuilds from [GitHub Releases](https://github.com/Katacr/KaGuilds/releases).
2. Install Vault and an economy plugin such as EssentialsX Economy.
3. Install PlaceholderAPI when placeholders are needed.
4. Place the plugin JAR in the server's `plugins` directory.
5. Start the server normally and wait for `plugins/KaGuilds` and the database tables to be created.
6. Stop the server, edit the generated configuration, and start it again.

A first-time single-server setup can keep:

```yaml
proxy: false

database:
  type: "SQLite"
```

## Verification

After startup, verify the following:

1. The console contains no KaGuilds database or dependency loading errors.
2. KaGuilds, Vault, and the economy provider are enabled in `/plugins`.
3. A player can open the help page with `/kg help`.
4. A test guild can use the bank, menus, and selected language.
5. For MySQL, the configured account can create tables and perform select, insert, update, and delete operations.

{% hint style="warning" %}
Do not load, unload, or reload KaGuilds with PlugMan or similar plugin loaders. Back up the database and the complete `plugins/KaGuilds` directory before upgrades.
{% endhint %}
