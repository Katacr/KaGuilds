# ⌨️ Player Command List

KaGuilds provides a comprehensive command system supporting all guild operations. All commands use `/kg` or `/guild` as prefix.

***

## 📋 Command Overview

### Main Commands

```bash
/kg                    # Open guild menu (if in guild) or creation menu
/kg help [page]        # View help menu
/guild help [page]     # Same as above
```

### Quick Access

| Command | Function | Permission |
|:--------|:---------|:-----------|
| `/kg` | Open menu | `kaguilds.command.main` |

***

### 📝 Player Command Quick Reference

| Command | Function | Required | Permission Node |
|:--------|:---------|:---------|:----------------|
| `/kg` | Open menu | - | `kaguilds.command.main` |
| `/kg help` | View help | - | `kaguilds.command.help` |
| `/kg create` | Create guild | - | `kaguilds.command.create` |
| `/kg join` | Request to join | - | `kaguilds.command.join` |
| `/kg yes` | Accept invite | - | `kaguilds.command.yes` |
| `/kg no` | Decline invite | - | `kaguilds.command.no` |
| `/kg info` | View info | - | `kaguilds.command.info` |
| `/kg menu` | Open menu | - | `kaguilds.command.menu` |
| `/kg leave` | Leave guild | - | `kaguilds.command.leave` |
| `/kg delete` | Delete guild | Owner | `kaguilds.command.delete` |
| `/kg rename` | Rename guild | Owner | `kaguilds.command.rename` |
| `/kg seticon` | Set icon | Owner | `kaguilds.command.seticon` |
| `/kg motd` | Set announcement | Guild Admin | `kaguilds.command.motd` |
| `/kg invite` | Invite member | Guild Admin | `kaguilds.command.invite` |
| `/kg accept` | Approve request | Guild Admin | `kaguilds.command.accept` |
| `/kg deny` | Deny request | Guild Admin | `kaguilds.command.deny` |
| `/kg requests` | View requests | Guild Admin | `kaguilds.command.requests` |
| `/kg kick` | Kick member | Guild Admin | `kaguilds.command.kick` |
| `/kg promote` | Promote member | Owner | `kaguilds.command.promote` |
| `/kg demote` | Demote member | Owner | `kaguilds.command.demote` |
| `/kg transfer` | Transfer guild | Owner | `kaguilds.command.transfer` |
| `/kg chat` | Guild chat | - | `kaguilds.command.chat` |
| `/kg settp` | Set teleport | Owner | `kaguilds.command.settp` |
| `/kg tp` | Guild teleport | - | `kaguilds.command.tp` |
| `/kg bank` | Bank management | - | `kaguilds.command.bank` |
| `/kg buff` | Buy Buff | - | `kaguilds.command.buff` |
| `/kg vault` | Access vault | - | `kaguilds.command.vault` |
| `/kg upgrade` | Upgrade guild | Guild Admin | `kaguilds.command.upgrade` |
| `/kg pvp` | Guild battle | Guild Admin | `kaguilds.command.pvp` |
| `/kg confirm` | Confirm action | - | `kaguilds.command.confirm` |
| `/kg reload` | Reload config | Guild Admin | `kaguilds.admin.reload` |

***

## 🏠 Basic Commands

### `/kg help [page]`

View plugin help menu.

**Permission**: `kaguilds.command.help`

**Usage**:

```bash
/kg help        # View page 1 of help
/kg help 2      # View page 2 of help
```

**Description**:

* Each page shows 10 commands
* Displayed by function category
* Includes brief command descriptions

**Example Output**:

```
------- KaGuilds Help Menu -------
/kg create <name>     Create a new guild
/kg join <guild>       Request to join a guild
/kg info               View guild info
...
```

***

## 👥 Guild Management Commands

### `/kg create <guild_name>`

Create a new guild.

**Permission**: `kaguilds.command.create`

**Usage**:

```bash
/kg create MyServerGuild
```

**Description**:

* Creation fee required (configured in `config.yml`)
* Guild name must follow naming rules (length, characters, etc.)
* After creation, the player automatically becomes the guild owner

***

### `/kg delete`

Delete the current guild.

**Permission**: `kaguilds.command.delete`

**Usage**:

```bash
/kg delete
/kg confirm    # Confirm deletion
```

**Description**:

* Requires owner permission
* Requires action confirmation (`/kg confirm`)
* All members leave the guild after deletion
* Guild data will be deleted

**Note**: This action is irreversible!

***

### `/kg rename <new_name>`

Rename the guild.

**Permission**: `kaguilds.command.rename`

**Usage**:

```bash
/kg rename NewGuildName
```

**Description**:

* Requires owner permission
* Fee required (configured in `config.yml`)
* New name must follow naming rules

***

### `/kg info [guild_name]`

View guild information.

**Permission**: `kaguilds.command.info`

**Usage**:

```bash
/kg info              # View your own guild's info
/kg info StrongGuild  # View specified guild's info
```

**Description**:

* Displays detailed guild information
* Includes: name, level, member count, funds, etc.

**Output Example**:

```
Guild Info: My Guild
Level: Lv.5
Members: 12/20
Funds: 1500.0
Owner: Steve
Announcement: Welcome to our guild!
```

***

### `/kg motd <announcement>`

Set guild announcement.

**Permission**: `kaguilds.command.motd`

**Usage**:

```bash
/kg motd Welcome to our guild!
```

**Description**:

* Requires admin or owner permission
* Supports color codes

***

### `/kg seticon`

Set guild icon.

**Permission**: `kaguilds.command.seticon`

**Usage**:

```bash
/kg seticon
```

**Description**:

* Hold the item you want to set
* Requires owner or admin permission
* Fee required (configured in `config.yml`)
* Supports special material items (e.g., Oraxen, ItemsAdder custom items)

***

## 🤝 Member Management Commands

### `/kg invite <player_name>`

Invite a player to the guild.

**Permission**: `kaguilds.command.invite`

**Usage**:

```bash
/kg invite Steve
```

**Description**:

* Requires admin or owner permission
* Invitation valid for 60 seconds
* Target player can use `/kg yes` or click the prompt to accept

***

### `/kg accept <player_name>`

Approve a player's join request.

**Permission**: `kaguilds.command.accept`

**Usage**:

```bash
/kg accept Steve
```

**Description**:

* Requires admin or owner permission
* Target player must have submitted a join request
* Use `/kg requests` to view pending requests

***

### `/kg deny <player_name>`

Deny a player's join request.

**Permission**: `kaguilds.command.deny`

**Usage**:

```bash
/kg deny Steve
```

**Description**:

* Requires admin or owner permission
* Target player will receive a denial notification

***

### `/kg requests`

View pending join requests.

**Permission**: `kaguilds.command.requests`

**Usage**:

```bash
/kg requests
```

**Description**:

* Requires admin or owner permission
* Displays list of all pending join requests

***

### `/kg join <guild_name_or_id>`

Request to join a guild.

**Permission**: `kaguilds.command.join`

**Usage**:

```bash
/kg join StrongGuild
/kg join guild_id
```

**Description**:

* Guild admins will receive a request notification
* Can be approved with `/kg accept <player_name>` or denied with `/kg deny <player_name>`

***

### `/kg yes`

Accept a guild invitation.

**Permission**: `kaguilds.command.yes`

**Usage**:

```bash
/kg yes
```

**Description**:

* Accepts guild admin's join invitation
* Invitation valid for 60 seconds

***

### `/kg no`

Decline a guild invitation.

**Permission**: `kaguilds.command.no`

**Usage**:

```bash
/kg no
```

**Description**:

* Declines guild admin's join invitation

***

### `/kg kick <player_name>`

Kick a guild member.

**Permission**: `kaguilds.command.kick`

**Usage**:

```bash
/kg kick Steve
```

**Description**:

* Requires admin or owner permission
* Kicked player will receive a notification

***

### `/kg promote <player_name>`

Promote a member to admin.

**Permission**: `kaguilds.command.promote`

**Usage**:

```bash
/kg promote Steve
```

**Description**:

* Requires owner permission
* Target player must be a member
* Can only promote to admin

***

### `/kg demote <player_name>`

Demote an admin to member.

**Permission**: `kaguilds.command.demote`

**Usage**:

```bash
/kg demote Steve
```

**Description**:

* Requires owner permission
* Target player must be an admin
* After demotion, becomes a member

***

### `/kg transfer <player_name>`

Transfer guild ownership.

**Permission**: `kaguilds.command.transfer`

**Usage**:

```bash
/kg transfer Steve
/kg confirm    # Confirm transfer
```

**Description**:

* Requires owner permission
* Target player must be a guild member
* Requires action confirmation (`/kg confirm`)
* Original owner becomes a member after transfer

**Note**: This action is irreversible!

***

### `/kg leave`

Leave the guild.

**Permission**: `kaguilds.command.leave`

**Usage**:

```bash
/kg leave
/kg confirm    # Confirm leaving
```

**Description**:

* Requires action confirmation (`/kg confirm`)

***

## 💰 Economy System Commands

### `/kg bank [add/take/log] [amount]`

Manage the guild bank.

**Permission**: `kaguilds.command.bank`

**Usage**:

```bash
/kg bank add 1000     # Deposit 1000 gold
/kg bank take 500     # Withdraw 500 gold
/kg bank log              # View transaction log
```

**Description**:

* `add` - Deposit gold to guild bank
* `take` - Withdraw gold from guild bank
* `log` - View bank transaction log (admin only)
* Deposits and withdrawals affect contribution points based on the contribution system

**Contribution Calculation** (config.yml):

* Deposit contribution = Deposit amount × `bank-deposit-ratio`
* Withdrawal contribution = Withdrawal amount × `bank-withdraw-ratio`

***

### `/kg upgrade`

Upgrade guild level.

**Permission**: `kaguilds.command.upgrade`

**Usage**:

```bash
/kg upgrade
```

**Description**:

* Requires admin or owner permission
* Requires sufficient guild experience
* Unlocks new features and increases attribute limits after upgrade

**Upgrade Effects** (see `levels.yml` for configuration):

***

## 🎮 Teleport System Commands

### `/kg settp`

Set guild teleport point.

**Permission**: `kaguilds.command.settp`

**Usage**:

```bash
/kg settp
```

**Description**:

* Requires owner or admin permission
* Sets current location as guild teleport point
* Fee required (configured in `config.yml`)

***

### `/kg tp`

Teleport to guild location.

**Permission**: `kaguilds.command.tp`

**Usage**:

```bash
/kg tp
```

**Description**:

* Fee required (configured in `config.yml`)
* Movement prohibited during teleport
* Some worlds may be prohibited (configured in `config.yml`)

***

## 🎁 Buff System Commands

### `/kg buff <buff_name>`

Purchase guild buff effect.

**Permission**: `kaguilds.command.buff`

**Usage**:

```bash
/kg buff Speed
/kg buff NightVision
```

**Description**:

* Requires sufficient guild bank balance
* Purchased buffs are distributed to all online guild members

***

## 📦 Vault System Commands

### `/kg vault <vault_number>`

Access guild cloud vault.

**Permission**: `kaguilds.command.vault`

**Usage**:

```bash
/kg vault 1     # Open vault 1
/kg vault 2     # Open vault 2
```

**Description**:

* Vaults need to be unlocked in `levels.yml`
* Supports cross-server synchronization
* When one player is operating a vault, others temporarily cannot use it

***

## 💬 Chat System Commands

### `/kg chat [message]`

Send guild chat message.

**Permission**: `kaguilds.command.chat`

**Usage**:

```bash
/kg chat                              # Toggle to guild chat mode
/kg chat Hello everyone!              # Send a single message
```

**Description**:

* `/kg chat`: Toggle chat mode (enter again to exit)
* `/kg chat <message>`: Send a single message
* After entering guild chat mode, all messages go to the guild channel

***

## ⚔️ PvP System Commands

### `/kg pvp <start/accept/ready/exit>`

Guild battle related commands.

**Permission**: `kaguilds.command.pvp`

**Usage**:

```bash
/kg pvp start guild_id         # Challenge specified guild
/kg pvp accept         # Accept guild battle
/kg pvp ready          # Ready
/kg pvp exit           # Exit guild battle
```

**Description**:

* Requires admin or owner permission
* Guild battles have participant limits (configured in `config.yml`)
* Has cooldown limitations
* Battle rewards are all configurable (configured in `config.yml`)

***

## 🖼️ Menu System Commands

### `/kg menu`

Open guild main menu.

**Permission**: `kaguilds.command.menu`

**Usage**:

```bash
/kg menu
```

**Description**:

* Opens a graphical guild management interface
* Provides convenient guild operations
* Supports custom menu layouts (configured in `gui/` directory)

***

## 🔧 System Commands

### `/kg reload`

Reload plugin configuration.

**Permission**: `kaguilds.admin.reload`

**Usage**:

```bash
/kg reload
```

**Description**:

* Reloads all configuration files
* Includes `config.yml`, `levels.yml`, `buffs.yml`, `task.yml`, etc.
* No server restart required
* Supports console execution

***

### `/kg confirm`

Confirm dangerous actions.

**Permission**: `kaguilds.command.confirm`

**Usage**:

```bash
/kg confirm
```

**Description**:

* Used to confirm dangerous actions (e.g., delete guild, transfer, etc.)
* Action valid for 30 seconds

***
