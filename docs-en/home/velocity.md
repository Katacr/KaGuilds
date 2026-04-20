# 🌐 Velocity Configuration Guide

KaGuilds supports running in a Velocity proxy server environment, enabling cross-server data synchronization and feature sharing. This document guides you through the complete Velocity-side configuration process.

## Installation

KaGuildsProxy is a Velocity plugin that only needs to be installed on the Velocity server — it does **not** need to be installed on sub-servers.

After downloading `KaGuildsProxy.jar`, copy it to your Velocity server's `plugins` folder and restart the Velocity server. No configuration file changes are required on the Velocity server.

## Configuration

### Configure All Sub-Servers

**Important: All sub-servers must use the same database configuration!**

Configure the database in each sub-server's `plugins/KaGuilds/config.yml`:

```yaml
# KaGuilds Configuration File
proxy: true  # ✅ Enable proxy mode
server-id: survival  # ⚠️ Each server must be different!

database:
  type: "MySQL"  # ⚠️ Must use MySQL
  host: "localhost"
  port: 3306
  db: "kaguilds"
  user: "kaguilds"
  password: "your_secure_password"
```
