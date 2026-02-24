

# 🛡️ KaGuilds - High-Performance Cross-Server Guild System

[![License](https://img.shields.io/github/license/katacr/KaGuilds)](LICENSE)
|[![Version](https://img.shields.io/badge/version-1.0.0-blue)](https://github.com/katacr/KaGuilds)
|[![Minecraft](https://img.shields.io/badge/Minecraft-1.16%2B-brightgreen)](https://www.minecraft.net/)

**KaGuilds** is a highly customized guild plugin designed for Minecraft networks (BungeeCord/Velocity). It utilizes **SQL Transactions** and a **Cross-Server Message Bus** to ensure data consistency and security across a distributed environment.

---

## ✨ Key Features

### 🔄 Cross-Server Synchronization
|- Global guild chat, notifications, and invitations powered by Velocity/BungeeCord channels
|- Real-time sync of guild info and permissions regardless of sub-server members are on
|- Cross-server player invitations and join requests

### 💰 Deep Economy Integration
|- **Creation Cost**: Configurable fees deducted automatically via Vault API
|- **Guild Bank**: Supports deposits and withdrawals with paginated transaction logs for staff
|- **Vault System**: Multiple lockable guild vaults with inventory lease management

### 👥 Rigid Role Hierarchy
|- Three tiers of authority: **Owner**, **Administrator**, and **Member**
|- Built-in logic for Promotion and Demotion with clear permission boundaries
|- Ownership transfer capabilities

### 📈 Dynamic Leveling System
|- Automated leveling based on guild EXP
|- Dynamically increasing member limits and bank capacity
|- Upgrade system with configurable tiers

### 📋 Comprehensive Invite & Request System
|- **Invitations**: Global player invites with automated expiration
|- **Requests**: Staff can view, approve, or deny pending applications at any time

### 🎮 PvP Arena System
|- Guild vs Guild battles with custom kits
|- Match timing and win conditions
|- Statistics tracking (wins/losses/draws)
|- Reward commands on victory

### 🖼️ GUI Menu System
|- Main menu with guild operations
|- Member list with pagination
|- Guilds list browser
|- Buff shop
|- Upgrade shop
|- Bank management

### 📊 PlaceholderAPI Support
Integrated variables for scoreboards or chat:
|- `%kaguilds_name%` - Current guild name
|- `%kaguilds_role%` - Player rank (Owner/Admin/Member)
|- `%kaguilds_level%` - Current guild level
|- `%kaguilds_balance%` - Current guild balance

### 🌍 Localization
Full language switching support in `config.yml`:
|- **Simplified Chinese**: `zh_CN.yml`
|- **English**: `en_US.yml`

---

## 🎮 Command Reference

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/kg create <name>` | Pay to create a new guild | `kaguilds.command.create` |
| `/kg join <name>` | Apply to join a specific guild | `kaguilds.command.join` |
| `/kg info [name]` | View detailed guild information | `kaguilds.command.info` |
| `/kg chat <message>` | Communicate in internal guild channel | `kaguilds.command.chat` |
| `/kg bank <add/get/log>` | Deposit, withdraw guild funds or view logs | `kaguilds.command.bank` |
| `/kg leave` | Leave your current guild | `kaguilds.command.leave` |
| `/kg tp` | Teleport to guild location | `kaguilds.command.tp` |
| `/kg settp` | Set guild teleport location | `kaguilds.command.settp` |
| `/kg rename <name>` | Rename your guild (confirmation required) | `kaguilds.command.rename` |
| `/kg vault <index>` | Open guild vault (1-9) | `kaguilds.command.vault` |
| `/kg motd <message>` | Set guild announcement | `kaguilds.command.motd` |
| `/kg seticon` | Set guild icon from held item | `kaguilds.command.seticon` |
| `/kg upgrade` | Upgrade guild level | `kaguilds.command.upgrade` |
| `/kg buff <name>` | Purchase guild buff | `kaguilds.command.buff` |
| `/kg pvp <guild>` | Challenge another guild to PvP | `kaguilds.command.pvp` |
| `/kg yes` | Accept guild invitation | `kaguilds.command.yes` |
| `/kg no` | Decline guild invitation | `kaguilds.command.no` |
| `/kg confirm` | Confirm pending actions | `kaguilds.command.confirm` |
| `/kg menu` | Open main guild menu | `kaguilds.command.menu` |
| `/kg help [page]` | View command help menu | `kaguilds.command.help` |

### Staff Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/kg invite <player>` | Invite online/cross-server players | `kaguilds.command.invite` |
| `/kg accept <player>` | Approve a join application | `kaguilds.command.accept` |
| `/kg deny <player>` | Deny a join application | `kaguilds.command.deny` |
| `/kg promote <player>` | Promote a member to Admin | `kaguilds.command.promote` |
| `/kg demote <player>` | Demote Admin to Member | `kaguilds.command.demote` |
| `/kg kick <player>` | Kick a member from guild | `kaguilds.command.kick` |
| `/kg transfer <player>` | Transfer guild ownership | `kaguilds.command.transfer` |
| `/kg bank log [page]` | View transaction history | `kaguilds.command.bank` |
| `/kg delete` | Delete guild (owner only) | `kaguilds.command.delete` |
| `/kg requests` | View all join requests | `kaguilds.command.requests` |
| `/kg confirm` | Confirm guild deletion or transfer | `kaguilds.command.confirm` |

### Admin Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/kg admin rename <guild> <name>` | Admin rename guild | `kaguilds.admin` / `kaguilds.admin.rename` |
| `/kg admin delete <guild>` | Admin delete guild | `kaguilds.admin` / `kaguilds.admin.delete` |
| `/kg admin info <guild>` | Admin view guild info | `kaguilds.admin` / `kaguilds.admin.info` |
| `/kg admin bank <guild> <see/log/add/remove/set>` | Manage guild bank | `kaguilds.admin` / `kaguilds.admin.bank` |
| `/kg admin exp <guild> <add/remove/set> <amount>` | Modify guild EXP | `kaguilds.admin` / `kaguilds.admin.exp` |
| `/kg admin setlevel <guild> <level>` | Set guild level | `kaguilds.admin` / `kaguilds.admin.setlevel` |
| `/kg admin kick <guild> <player>` | Admin kick member | `kaguilds.admin` / `kaguilds.admin.kick` |
| `/kg admin join <guild> <player>` | Force add player to guild | `kaguilds.admin` / `kaguilds.admin.join` |
| `/kg admin transfer <guild> <player>` | Admin transfer ownership | `kaguilds.admin` / `kaguilds.admin.transfer` |
| `/kg admin vault <guild> <index>` | Admin open vault | `kaguilds.admin` / `kaguilds.admin.vault` |
| `/kg admin unlockall` | Force reset all vault locks | `kaguilds.admin` / `kaguilds.admin.unlockall` |
| `/kg admin arena <setpos/setspawn/setkit/info>` | Configure PvP arena | `kaguilds.admin` / `kaguilds.admin.arena` |
| `/kg reload` | Reload configuration files | `kaguilds.admin` / `kaguilds.command.reload` |

---

## 📋 Permissions

### Basic Permissions
```yaml
kaguilds.use            # Access all basic guild features
kaguilds.admin          # Access all administrative commands
```

### Command-Specific Permissions
```yaml
kaguilds.command.main    # Access main guild command
kaguilds.command.help    # View command help
kaguilds.command.menu    # Open main menu GUI
```

### Player Commands
```yaml
kaguilds.command.create  # Create a new guild
kaguilds.command.join    # Apply to join a guild
kaguilds.command.info    # View guild information
kaguilds.command.leave   # Leave current guild
kaguilds.command.tp      # Teleport to guild
kaguilds.command.settp   # Set guild teleport point
kaguilds.command.chat    # Send guild chat messages
kaguilds.command.bank    # Access guild bank
kaguilds.command.vault   # Access guild vaults
kaguilds.command.motd    # Set guild announcement
kaguilds.command.rename  # Rename guild
kaguilds.command.seticon # Set guild icon
kaguilds.command.upgrade # Upgrade guild level
kaguilds.command.buff    # Purchase guild buffs
kaguilds.command.pvp     # Access PvP system
kaguilds.command.yes     # Accept guild invitations
kaguilds.command.no      # Decline guild invitations
kaguilds.command.confirm # Confirm pending actions
kaguilds.command.invite  # Invite players
kaguilds.command.accept  # Accept join applications
kaguilds.command.deny    # Deny join applications
kaguilds.command.promote # Promote members
kaguilds.command.demote  # Demote admins
kaguilds.command.kick    # Kick members
kaguilds.command.transfer # Transfer ownership
kaguilds.command.delete  # Delete guild
kaguilds.command.requests # View join requests
kaguilds.command.reload  # Reload plugin config
```

### Admin Commands
```yaml
kaguilds.admin.rename   # Admin rename guild
kaguilds.admin.delete   # Admin delete guild
kaguilds.admin.info     # Admin view guild info
kaguilds.admin.bank     # Admin manage bank
kaguilds.admin.exp      # Admin modify EXP
kaguilds.admin.setlevel # Admin set level
kaguilds.admin.kick     # Admin kick member
kaguilds.admin.join     # Admin add member
kaguilds.admin.transfer # Admin transfer ownership
kaguilds.admin.vault    # Admin open vault
kaguilds.admin.unlockall # Force unlock all vaults
kaguilds.admin.arena    # Configure PvP arena
```

---

## 💾 Database Schema

The plugin uses MySQL with following main tables:
|- `guilds` - Guild information and settings
|- `members` - Member data and roles
|- `bank_logs` - Transaction history
|- `requests` - Join requests
|- `vaults` - Guild vault data

---

## 🔧 Configuration

### config.yml
|- Database connection settings
|- Economy settings (creation cost, level costs)
|- Guild limits (max members, max vaults)
|- PvP settings (match duration, arena config)
|- Message channel names

### GUI Files
|- `main_menu.yml` - Main guild menu
|- `guilds_list.yml` - Guild browser
|- `guild_members.yml` - Member list
|- `guild_bank.yml` - Bank interface
|- `guild_vaults.yml` - Vault selector
|- `guild_buffs.yml` - Buff shop
|- `guild_upgrade.yml` - Upgrade menu

---
