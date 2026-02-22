

# 🛡️ KaGuilds - High-Performance Cross-Server Guild System

[![License](https://img.shields.io/github/license/katacr/KaGuilds)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.0-blue)](https://github.com/katacr/KaGuilds)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.16%2B-brightgreen)](https://www.minecraft.net/)

**KaGuilds** is a highly customized guild plugin designed for Minecraft networks (BungeeCord/Velocity). It utilizes **SQL Transactions** and a **Cross-Server Message Bus** to ensure data consistency and security across a distributed environment.

---

## ✨ Key Features

### 🔄 Cross-Server Synchronization
- Global guild chat, notifications, and invitations powered by Velocity/BungeeCord channels
- Real-time sync of guild info and permissions regardless of the sub-server members are on
- Cross-server player invitations and join requests

### 💰 Deep Economy Integration
- **Creation Cost**: Configurable fees deducted automatically via Vault API
- **Guild Bank**: Supports deposits and withdrawals with paginated transaction logs for staff
- **Vault System**: Multiple lockable guild vaults with inventory lease management

### 👥 Rigid Role Hierarchy
- Three tiers of authority: **Owner**, **Administrator**, and **Member**
- Built-in logic for Promotion and Demotion with clear permission boundaries
- Ownership transfer capabilities

### 📈 Dynamic Leveling System
- Automated leveling based on guild EXP
- Dynamically increasing member limits and bank capacity
- Upgrade system with configurable tiers

### 📋 Comprehensive Invite & Request System
- **Invitations**: Global player invites with automated expiration
- **Requests**: Staff can view, approve, or deny pending applications at any time

### 🎮 PvP Arena System
- Guild vs Guild battles with custom kits
- Match timing and win conditions
- Statistics tracking (wins/losses/draws)
- Reward commands on victory

### 🖼️ GUI Menu System
- Main menu with guild operations
- Member list with pagination
- Guilds list browser
- Buff shop
- Upgrade shop
- Bank management

### 📊 PlaceholderAPI Support
Integrated variables for scoreboards or chat:
- `%kaguilds_name%` - Current guild name
- `%kaguilds_role%` - Player rank (Owner/Admin/Member)
- `%kaguilds_level%` - Current guild level
- `%kaguilds_balance%` - Current guild balance

### 🌍 Localization
Full language switching support in `config.yml`:
- **Simplified Chinese**: `zh_CN.yml`
- **English**: `en_US.yml`

---

## 🏗️ Architecture

### Core Components

| Component | Description |
|-----------|-------------|
| `KaGuilds` | Main plugin class, manages lifecycle and services |
| `DatabaseManager` | SQL operations using HikariCP connection pooling |
| `GuildService` | Core business logic for guild operations |
| `GuildCommand` | Command executor with tab completion |
| `LanguageManager` | Multi-language support system |
| `MenuManager` | GUI menu rendering and interaction |
| `PvPManager` | Guild battle matchmaking and management |
| `ArenaManager` | Arena configuration and kit storage |

### Data Structures

- **GuildData**: Guild information including level, exp, balance, members
- **MemberData**: Player membership with role and join time
- **ArenaData**: PvP arena configuration with team spawns
- **ActiveMatch**: Ongoing PvP match state

---

## 🎮 Command Reference

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/kg create <name>` | Pay to create a new guild | `kaguilds.use` |
| `/kg join <name>` | Apply to join a specific guild | `kaguilds.use` |
| `/kg info [name]` | View detailed guild information | `kaguilds.use` |
| `/kg chat <message>` | Communicate in the internal guild channel | `kaguilds.use` |
| `/kg bank <add/get>` | Deposit or withdraw guild funds | `kaguilds.use` |
| `/kg leave` | Leave your current guild | `kaguilds.use` |
| `/kg tp` | Teleport to guild location | `kaguilds.use` |
| `/kg settp` | Set guild teleport location | `kaguilds.use` |
| `/kg rename <name>` | Rename your guild (confirmation required) | `kaguilds.use` |
| `/kg vault <index>` | Open guild vault | `kaguilds.use` |
| `/kg motd <message>` | Set guild announcement | `kaguilds.use` |
| `/kg icon` | Set guild icon from held item | `kaguilds.use` |
| `/kg upgrade` | Upgrade guild level | `kaguilds.use` |
| `/kg buffs` | Open buff shop | `kaguilds.use` |
| `/kg pvp <guild>` | Challenge another guild to PvP | `kaguilds.use` |

### Staff Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/kg invite <player>` | Invite online/cross-server players | `kaguilds.use` |
| `/kg accept <player>` | Approve a join application | `kaguilds.use` |
| `/kg deny <player>` | Deny a join application | `kaguilds.use` |
| `/kg promote <player>` | Promote a member to Admin | `kaguilds.use` |
| `/kg demote <player>` | Demote Admin to Member | `kaguilds.use` |
| `/kg kick <player>` | Kick a member from the guild | `kaguilds.use` |
| `/kg transfer <player>` | Transfer guild ownership | `kaguilds.use` |
| `/kg bank log [page]` | View transaction history | `kaguilds.use` |
| `/kg delete` | Delete guild (owner only) | `kaguilds.use` |
| `/kg list [page]` | List all guilds | `kaguilds.use` |
| `/kg members [page]` | View guild members | `kaguilds.use` |
| `/kg requests` | View all join requests | `kaguilds.use` |

### Admin Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/kg admin rename <guild> <name>` | Admin rename guild | `kaguilds.admin` |
| `/kg admin delete <guild>` | Admin delete guild | `kaguilds.admin` |
| `/kg admin info <guild>` | Admin view guild info | `kaguilds.admin` |
| `/kg admin bank <guild> <add/remove> <amount>` | Manage guild bank | `kaguilds.admin` |
| `/kg admin exp <guild> <add/set> <amount>` | Modify guild EXP | `kaguilds.admin` |
| `/kg admin kick <guild> <player>` | Admin kick member | `kaguilds.admin` |
| `/kg admin transfer <guild> <player>` | Admin transfer ownership | `kaguilds.admin` |
| `/kg admin vault <guild> <index>` | Admin open vault | `kaguilds.admin` |
| `/kg reload` | Reload configuration files | `kaguilds.admin` |

---

## 📋 Permissions

```yaml
kaguilds.use      # Basic player functionality
kaguilds.admin    # Administrative commands
```

---

## 💾 Database Schema

The plugin uses MySQL with the following main tables:
- `guilds` - Guild information and settings
- `members` - Member data and roles
- `bank_logs` - Transaction history
- `requests` - Join requests
- `vaults` - Guild vault data

---

## 🔧 Configuration

### config.yml
- Database connection settings
- Economy settings (creation cost, level costs)
- Guild limits (max members, max vaults)
- PvP settings (match duration, arena config)
- Message channel names

### GUI Files
- `main_menu.yml` - Main guild menu
- `guilds_list.yml` - Guild browser
- `guild_members.yml` - Member list
- `guild_bank.yml` - Bank interface
- `guild_vaults.yml` - Vault selector
- `guild_buffs.yml` - Buff shop
- `guild_upgrade.yml` - Upgrade menu

---

## 🛠️ Building

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`

---

## 📝 License

This project is licensed under the terms included in the [LICENSE](LICENSE) file.

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

## ⚠️ Requirements

- **Minecraft Server**: 1.8+
- **Server Type**: Spigot, Paper, or compatible
- **Proxy**: BungeeCord or Velocity (for cross-server features)
- **Dependencies**: 
  - Vault (for economy)
  - PlaceholderAPI (optional, for placeholders)
