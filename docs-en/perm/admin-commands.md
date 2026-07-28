# Admin Commands

Server administration commands use `/kg admin <action> [arguments...]`. On this page, "admin" means a server permission administrator, not the in-guild admin role.

## Arguments and Senders

- This documentation uses `#ID` for guild IDs, for example `#12`. The current implementation also accepts numbers without `#`.
- Admin commands can run from a player or the console, except for vault, menu, and arena commands.
- `kaguilds.admin` grants all admin commands. Individual actions can instead be granted with `kaguilds.admin.<action>`.
- Admin delete and transfer operations run immediately and do not use `/kg confirm`.

## Quick Reference

| Command | Purpose | Sender | Permission |
| --- | --- | --- | --- |
| `/kg admin help [page]` | Show admin commands available to the sender | Player or console | `kaguilds.admin.help` or any visible action permission |
| `/kg admin info #ID` | Show guild details | Player or console | `kaguilds.admin.info` |
| `/kg admin rename #ID <name>` | Rename a guild | Player or console | `kaguilds.admin.rename` |
| `/kg admin delete #ID` | Delete a guild immediately | Player or console | `kaguilds.admin.delete` |
| `/kg admin transfer #ID <player>` | Transfer guild ownership | Player or console | `kaguilds.admin.transfer` |
| `/kg admin kick #ID <player>` | Remove a specified member | Player or console | `kaguilds.admin.kick` |
| `/kg admin join #ID <player>` | Force an online player to join | Player or console | `kaguilds.admin.join` |
| `/kg admin bank #ID see` | Show the bank balance | Player or console | `kaguilds.admin.bank` |
| `/kg admin bank #ID log [page]` | Show bank logs | Player or console | `kaguilds.admin.bank` |
| `/kg admin bank #ID <add\|remove\|set> <amount> [-s]` | Modify the bank balance | Player or console | `kaguilds.admin.bank` |
| `/kg admin setlevel #ID <level>` | Set the guild level | Player or console | `kaguilds.admin.setlevel` |
| `/kg admin exp #ID <add\|remove\|set> <amount> [-s]` | Modify guild experience | Player or console | `kaguilds.admin.exp` |
| `/kg admin vault #ID [1-9]` | Open a specified guild vault | Player only | `kaguilds.admin.vault` |
| `/kg admin unlockall` | Force-release every vault lock | Player or console | `kaguilds.admin.unlockall` |
| `/kg admin task #ID <task-key> see` | Show task definition and progress | Player or console | `kaguilds.admin.task` |
| `/kg admin task #ID <task-key> reset` | Reset task progress | Player or console | `kaguilds.admin.task` |
| `/kg admin task #ID <task-key> add <amount>` | Add task progress | Player or console | `kaguilds.admin.task` |
| `/kg admin contribution #ID <player\|-all> set <amount>` | Set contribution points | Player or console | `kaguilds.admin.contribution` |
| `/kg admin contribution #ID <player\|-all> add <amount>` | Add contribution points | Player or console | `kaguilds.admin.contribution` |
| `/kg admin contribution #ID <player\|-all> clear` | Clear contribution points | Player or console | `kaguilds.admin.contribution` |
| `/kg admin open <menu>` | Open a GUI menu | Player only | `kaguilds.admin.open` |
| `/kg admin arena setpos <1\|2>` | Set an arena boundary | Player only | `kaguilds.admin.arena` |
| `/kg admin arena setspawn <red\|blue>` | Set a team spawn | Player only | `kaguilds.admin.arena` |
| `/kg admin arena setkit <red\|blue>` | Save the current inventory as a team kit | Player only | `kaguilds.admin.arena` |
| `/kg admin arena info` | Show arena configuration status | Player only | `kaguilds.admin.arena` |
| `/kg admin release <CN\|EN>` | Release bundled menus for one language | Player or console | `kaguilds.admin.release` |

## Guild and Member Management

- `rename` still enforces the name length and regular expression configured in `config.yml`.
- `delete` immediately removes the guild and its relationships. Verify the ID and back up the database before use.
- The `transfer` target must exist in player data and should belong to the target guild.
- `kick` cannot remove the guild owner.
- `join` only accepts an online player and bypasses the request and approval flow. It returns an error if the target already belongs to another guild.

## Bank and Experience

```bash
/kg admin bank #1 add 100
/kg admin bank #1 add 100 -s
/kg admin exp #1 set 5000
/kg admin exp #1 set 5000 -s
```

- `add` increases a value, `remove` decreases it, and `set` replaces it.
- `bank` accepts decimal values; `exp` accepts integers.
- `-s` suppresses successful output only. Invalid arguments, missing guilds, and database failures are still reported.
- Admin bank changes do not apply the player-facing guild-level cap or contribution rules. Use them carefully.

## Vault Management

- `/kg admin vault #ID` opens vault 1 by default.
- Valid vault numbers range from `1` to `9`.
- Admin vault access does not check whether the target guild level has unlocked that vault number.
- `/kg admin unlockall` releases stale locks after abnormal disconnects. It does not unlock level-gated guild features.

## Tasks and Contributions

- `<task-key>` must match a task key in `task.yml`.
- `task see` displays the task definition, target, rewards, and recorded progress.
- `task reset` resets the guild record for global tasks and existing player records for daily tasks.
- The current `task add` command cannot target a player, so it cannot reliably manage personal daily progress. Use it for global tasks only.
- Contribution amounts must be non-negative integers. `-all` applies the operation to every guild member.

## Arena and Menus

- `setpos` and `setspawn` use the executing player's current location.
- `setkit` saves the executing player's current inventory as the selected team kit.
- The `open` menu name must match a loaded menu file.
- `release CN` or `release EN` extracts bundled menu files. Back up customized files before allowing replacements.

## Reload Command

Plugin reload is not under `/kg admin`. Use:

```bash
/kg reload
```

It requires `kaguilds.admin` or `kaguilds.admin.reload`. Restart the server fully when updating the plugin JAR, database schema, or dependencies. Do not use hot-loading tools such as PlugMan.
