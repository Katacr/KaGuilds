# ⌨️ Admin Command List

KaGuilds provides a complete admin command system for managing and controlling all guild functions.

***

## 🔐 Admin Command Overview

### Main Command

```bash
/kg admin <action> [arguments...]
```

**Permission**: `kaguilds.admin`

**All admin operations do not require guild permissions and can directly manage any guild.**

***

## 📋 Admin Command Quick Reference

| Command | Function | Permission |
|:--------|:---------|:-----------|
| `/kg admin rename` | Rename guild | `kaguilds.admin.rename` |
| `/kg admin delete` | Delete guild | `kaguilds.admin.delete` |
| `/kg admin info` | View guild info | `kaguilds.admin.info` |
| `/kg admin bank` | Manage guild bank | `kaguilds.admin.bank` |
| `/kg admin transfer` | Transfer guild | `kaguilds.admin.transfer` |
| `/kg admin kick` | Kick member | `kaguilds.admin.kick` |
| `/kg admin join` | Add player | `kaguilds.admin.join` |
| `/kg admin vault` | Access vault | `kaguilds.admin.vault` |
| `/kg admin unlockall` | Unlock all vaults | `kaguilds.admin.unlockall` |
| `/kg admin setlevel` | Set guild level | `kaguilds.admin.setlevel` |
| `/kg admin exp` | Manage guild experience | `kaguilds.admin.exp` |
| `/kg admin arena` | Arena management | `kaguilds.admin.arena` |
| `/kg admin open` | Open specified menu | `kaguilds.admin.open` |
| `/kg admin release` | Release menu files | `kaguilds.admin.release` |

***

## 📦 Guild Management

### `/kg admin rename <guild_id> <new_name>`

Rename a specified guild.

**Permission**: `kaguilds.admin.rename`

**Usage**:

```bash
/kg admin rename 1 NewGuildName
```

**Description**:

* Does not require guild permission
* Directly modifies guild name
* New name must follow naming rules

***

### `/kg admin delete <guild_id>`

Delete a specified guild.

**Permission**: `kaguilds.admin.delete`

**Usage**:

```bash
/kg admin delete 1
/kg confirm    # Confirm deletion
```

**Description**:

* Does not require guild permission
* Requires action confirmation (`/kg confirm`)
* All members leave the guild after deletion
* Guild data will be deleted

**Note**: This action is irreversible!

***

### `/kg admin info <guild_id>`

View information of a specified guild.

**Permission**: `kaguilds.admin.info`

**Usage**:

```bash
/kg admin info 1
```

**Description**:

* Displays complete detailed guild information
* Includes: name, level, member list, funds, announcement, etc.

***

### `/kg admin transfer <guild_id> <new_owner>`

Transfer a specified guild.

**Permission**: `kaguilds.admin.transfer`

**Usage**:

```bash
/kg admin transfer 1 NewOwnerName
/kg confirm    # Confirm transfer
```

**Description**:

* Does not require guild permission
* Target player can be any online player
* Requires action confirmation (`/kg confirm`)
* Original owner becomes a member after transfer

**Note**: This action is irreversible!

***

## 👥 Member Management

### `/kg admin kick <guild_id> <player_name>`

Kick a member from a specified guild.

**Permission**: `kaguilds.admin.kick`

**Usage**:

```bash
/kg admin kick 1 PlayerName
```

**Description**:

* Does not require guild permission
* Kicked player will receive a notification
* Can kick any member (including owner)

***

### `/kg admin join <guild_id> <player_name>`

Add a player to a specified guild.

**Permission**: `kaguilds.admin.join`

**Usage**:

```bash
/kg admin join 1 PlayerName
```

**Description**:

* Does not require guild permission
* Directly adds player to guild (no request/approval needed)
* Player must be online

***

## 💰 Economy Management

### `/kg admin bank <guild_id> <action> [amount]`

Manage a specified guild's bank.

**Permission**: `kaguilds.admin.bank`

**Usage**:

```bash
/kg admin bank 1 add 1000    # Add 1000 gold
/kg admin bank 1 remove 500    # Remove 500 gold
/kg admin bank 1 set 500    # Set to 500 gold
/kg admin bank 1 see    # View current balance
/kg admin bank 1 log    # View bank log
```

**Description**:

* Does not require guild permission
* `add` - Add gold to vault
* `remove` - Remove gold from vault
* `set` - Directly set vault balance
* `see` - View current vault balance
* `log` - View vault transaction history

***

### `/kg admin setlevel <guild_id> <level>`

Set guild level.

**Permission**: `kaguilds.admin.setlevel`

**Usage**:

```bash
/kg admin setlevel 1 5
```

**Description**:

* Does not require guild permission
* Directly sets guild level
* Unlocked features take effect immediately after setting

***

### `/kg admin exp <guild_id> <action> <value>`

Manage guild experience.

**Permission**: `kaguilds.admin.exp`

**Usage**:

```bash
/kg admin exp 1 add 100     # Add 100 experience
/kg admin exp 1 set 500     # Set to 500 experience
/kg admin exp 1 remove 50   # Remove 50 experience
```

**Description**:

* Does not require guild permission
* `add` - Add guild experience
* `set` - Directly set guild experience
* `remove` - Remove guild experience

***

## 📦 Vault Management

### `/kg admin vault <guild_id> <vault_number>`

Access a specified guild's vault.

**Permission**: `kaguilds.admin.vault`

**Usage**:

```bash
/kg admin vault 1 1
```

**Description**:

* Does not require guild permission
* Can access any vault of any guild
* Vault number must be within the guild's unlocked range

***

### `/kg admin unlockall`

Unlock all vault access locks in the guild, for testing or exceptional situations only.

**Permission**: `kaguilds.admin.unlockall`

**Usage**:

```bash
/kg admin unlockall
```

**Description**:

* Does not require guild permission
* Unlocks vault occupation locks for all guilds
* For exceptional situations only (e.g., player disconnection causes vault lock)
* Do not execute this command casually during normal use

***

## ⚔️ Arena Management

### `/kg admin arena <setpos/setspawn/setkit/info>`

Arena management.

**Permission**: `kaguilds.admin.arena`

**Usage**:

```bash
/kg admin arena setpos 1    # Set arena position 1
/kg admin arena setpos 2    # Set arena position 2
/kg admin arena setspawn red  # Set red team spawn point
/kg admin arena setspawn blue  # Set blue team spawn point
/kg admin arena setkit red     # Set red team preset equipment
/kg admin arena setkit blue    # Set blue team preset equipment
/kg admin arena info       # View arena information
```

**Description**:

* Does not require guild permission
* `setpos` - Set arena boundaries (two positions required)
* `setspawn` - Set team spawn points (red/blue)
* `setkit` - Set team preset equipment (red/blue)
* `info` - Display current arena configuration

**Arena Setup Steps**:

1. Execute `/kg admin arena setpos 1` at arena position 1
2. Execute `/kg admin arena setpos 2` at arena position 2
3. Execute `/kg admin arena setspawn red` at red team spawn point
4. Execute `/kg admin arena setspawn blue` at blue team spawn point
5. Execute `/kg admin arena info` to view configuration

***

## 🖼️ Menu Management

### `/kg admin open <menu_name>`

Open a specified menu.

**Permission**: `kaguilds.admin.open`

**Usage**:

```bash
/kg admin open main_menu
/kg admin open guild_list
/kg admin open member_list
```

**Description**:

* Does not require guild permission
* Can open any menu file
* Menu files are in the `gui/` directory (without `.yml` extension)
* Used for testing and debugging menu configurations

***

### `/kg admin release <language>`

Release plugin's specified language menu file.

**Permission**: `kaguilds.admin.release`

**Usage**:

```bash
/kg admin release EN
/kg admin release CN
```

**Description**:

* Requires admin permission
* Existing files with the same name will be replaced during release
* `EN` - Release English menu files
* `CN` - Release Chinese menu files
* Used to restore default menu configuration

**Use Cases**:

* Menu file is corrupted and needs recovery
* Want to view the latest default menu configuration
* Need to base custom configuration on default settings

**Note**: Executing this command will overwrite existing files with the same name — please back up in advance!

***

