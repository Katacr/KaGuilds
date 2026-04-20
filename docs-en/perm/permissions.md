# 🔐 Permission List

KaGuilds uses a complete permission system to control features accessible to players and administrators. Permissions are divided into two major categories: **Player Permissions** (normal guild member features) and **Admin Permissions** (server management features).

***

## Permission Bundles

### Usage Recommendations

| Permission Node | Description |
|:---------------|:------------|
| `kaguilds.use` | Allows using all guild features |
| `kaguilds.admin` | Allows using all admin features |

* In most cases, you only need to give players the `kaguilds.use` permission, and they can use **all guild operations**.
* If you want to customize which features players can use, you need to **disable** the player's `kaguilds.use` permission, then **selectively assign** permissions from the list below.

## 📝 Complete Permission List

### Player Permissions

| Permission Node | Description | Default |
|:---------------|:------------|:--------|
| `kaguilds.use` | Allows using all guild features | true |
| `kaguilds.command.main` | Allows using basic guild commands | true |
| `kaguilds.command.help` | Allows using the help command | true |
| `kaguilds.command.create` | Allows using guild creation command | false |
| `kaguilds.command.delete` | Allows using guild deletion command | false |
| `kaguilds.command.promote` | Allows using member promotion command | false |
| `kaguilds.command.demote` | Allows using member demotion command | false |
| `kaguilds.command.accept` | Allows using join request approval command | false |
| `kaguilds.command.deny` | Allows using join request denial command | false |
| `kaguilds.command.confirm` | Allows using confirmation command | false |
| `kaguilds.command.bank` | Allows using guild bank command | false |
| `kaguilds.command.chat` | Allows using guild chat command | false |
| `kaguilds.command.menu` | Allows using guild main menu command | false |
| `kaguilds.command.motd` | Allows using guild announcement command | false |
| `kaguilds.command.rename` | Allows using guild rename command | false |
| `kaguilds.command.seticon` | Allows using guild icon command | false |
| `kaguilds.command.settp` | Allows using guild teleport point command | false |
| `kaguilds.command.tp` | Allows using guild teleport command | false |
| `kaguilds.command.upgrade` | Allows using guild upgrade command | false |
| `kaguilds.command.info` | Allows using guild info command | false |
| `kaguilds.command.invite` | Allows using guild invite command | false |
| `kaguilds.command.join` | Allows using join request command | false |
| `kaguilds.command.kick` | Allows using member kick command | false |
| `kaguilds.command.leave` | Allows using guild leave command | false |
| `kaguilds.command.vault` | Allows using guild vault command | false |
| `kaguilds.command.transfer` | Allows using guild transfer command | false |
| `kaguilds.command.pvp` | Allows using guild PvP commands | false |
| `kaguilds.command.admin` | Allows using guild admin commands | false |
| `kaguilds.command.yes` | Allows using invite accept command | false |
| `kaguilds.command.no` | Allows using invite decline command | false |
| `kaguilds.command.requests` | Allows using join request list command | false |
| `kaguilds.command.buff` | Allows players to use guild Buff commands | false |

### Admin Permissions

| Permission Node | Description | Default |
|:---------------|:------------|:--------|
| `kaguilds.admin` | Allows using all admin features | op |
| `kaguilds.admin.reload` | Allows using config reload command | op |
| `kaguilds.admin.rename` | Allows admin to use guild rename command | op |
| `kaguilds.admin.delete` | Allows admin to use guild deletion command | op |
| `kaguilds.admin.info` | Allows admin to use guild info command | op |
| `kaguilds.admin.bank` | Allows admin to use guild bank command | op |
| `kaguilds.admin.transfer` | Allows admin to use guild transfer command | op |
| `kaguilds.admin.kick` | Allows admin to use member kick command | op |
| `kaguilds.admin.join` | Allows admin to use guild join command | op |
| `kaguilds.admin.vault` | Allows admin to use guild vault command | op |
| `kaguilds.admin.unlockall` | Allows admin to unlock all vault locks | op |
| `kaguilds.admin.setlevel` | Allows admin to use guild level command | op |
| `kaguilds.admin.exp` | Allows admin to use guild experience command | op |
| `kaguilds.admin.arena` | Allows admin to use guild arena command | op |
| `kaguilds.admin.open` | Allows admin to open any GUI menu | op |
| `kaguilds.admin.release` | Release GUI menu files for specified language | op |

***
