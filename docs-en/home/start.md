# 🚀 Getting Started

This guide will help you quickly install and configure the KaGuilds plugin.

---

## 🎮 System Requirements

| Item | Supported Details |
|:-----|:----------------|
| Minecraft Version | 1.16.1 ~ 1.21.11 |
| Java Version | Java 12 ~ 21 |
| Server Type | Spigot, Paper, and derivative cores |
| Proxy Server (optional) | Velocity |
| Database | SQLite, MySQL 5.7+ |

---

## 📥 Installation Steps

### 1. Download the Plugin

Download the latest version of KaGuilds from GitHub Releases:

{% embed url="https://github.com/Katacr/KaGuilds/releases" %}

### 2. Install Dependencies

KaGuilds requires the following dependency plugins to function:

**Required Dependencies:**
- [Vault](https://www.spigotmc.org/resources/vault.34315/) — Economy system support

**Optional Dependencies:**
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) — Placeholder support

Download these plugins and place their `.jar` files into your server's `plugins` folder.

### 3. Install the Plugin

1. Place the downloaded KaGuilds `.jar` file into your server's `plugins` folder
2. Start the server
3. The plugin will automatically create configuration files and database tables

{% hint style="info" %}
Do NOT use plugin loaders (such as PlugMan) to load or reload KaGuilds, otherwise you may encounter unknown errors.
{% endhint %}
