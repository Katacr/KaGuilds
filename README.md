# 🛡️ KaGuilds - High-Performance Cross-Server Guild System

**KaGuilds** is a highly customized guild plugin designed for Minecraft networks (BungeeCord/Velocity). It utilizes **SQL Transactions** and a **Cross-Server Message Bus** to ensure data consistency and security across a distributed environment.

---

## ✨ Key Features

* **True Cross-Server Synchronization**
    * Global guild chat, notifications, and invitations powered by Velocity/BungeeCord channels.
    * Real-time sync of guild info and permissions regardless of the sub-server members are on.
* **Deep Economy Integration**
    * **Creation Cost**: Configurable fees deducted automatically via Vault API.
    * **Guild Bank**: Supports deposits and withdrawals with paginated transaction logs for staff.
* **Rigid Role Hierarchy**
    * Three tiers of authority: **Owner**, **Administrator**, and **Member**.
    * Built-in logic for Promotion and Demotion with clear permission boundaries.
* **Dynamic Leveling System**
    * Automated leveling based on guild EXP, dynamically increasing member limits and bank capacity.
* **Comprehensive Invite & Request System**
    * **Invitations**: Global player invites with automated expiration.
    * **Requests**: Staff can view, approve, or deny pending applications at any time.
* **PlaceholderAPI Support**
    * Integrated variables for guild name, rank, member count, and balance for scoreboards or chat.

---

## 🎮 Command Reference

### Player Commands
| Command | Description | Permission |
| :--- | :--- | :--- |
| `/kg create <name>` | Pay to create a new guild | `kaguilds.use` |
| `/kg join <name>` | Apply to join a specific guild | `kaguilds.use` |
| `/kg info [name]` | View detailed guild information | `kaguilds.use` |
| `/kg chat <message>` | Communicate in the internal guild channel | `kaguilds.use` |
| `/kg bank <add/get>` | Deposit or withdraw guild funds | `kaguilds.use` |
| `/kg leave` | Leave your current guild | `kaguilds.use` |

### Staff Commands
| Command | Description | Permission |
| :--- | :--- | :--- |
| `/kg invite <player>` | Invite online/cross-server players | `kaguilds.use` |
| `/kg accept <player>` | Approve a join application | `kaguilds.use` |
| `/kg deny <player>` | Deny a join application | `kaguilds.use` |
| `/kg promote <player>` | Promote a member to Admin | `kaguilds.use` |
| `/kg kick <player>` | Kick a member from the guild | `kaguilds.use` |
| `/kg bank log [page]` | View transaction history | `kaguilds.use` |
| `/kg reload` | Reload configuration files | `kaguilds.admin` |

---

## 📊 Placeholders

* `%kaguilds_name%`: Current guild name
* `%kaguilds_role%`: Player rank (Owner/Admin/Member)
* `%kaguilds_level%`: Current guild level
* `%kaguilds_balance%`: Current guild balance

---

## 🌐 Localization

The plugin supports full language switching in `config.yml`:
* **Simplified Chinese**: `zh_CN.yml`
* **English**: `en_US.yml`