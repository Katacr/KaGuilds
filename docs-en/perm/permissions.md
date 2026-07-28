# Permissions

KaGuilds declares player-command and server-administration permissions in `plugin.yml`. Explicit denials, inheritance, and wildcard behavior depend on the permission manager installed on the server.

## Recommended Setup

### Allow All Player Features

Keep the defaults:

```text
kaguilds.command.main = true
kaguilds.use = true
```

### Control Player Commands Individually

1. Keep `kaguilds.command.main` granted. Otherwise, Bukkit rejects the entire command before the KaGuilds command handler runs.
2. Explicitly deny `kaguilds.use`.
3. Grant the required `kaguilds.command.<subcommand>` permissions.

Denying only one command-specific permission is ineffective because most commands immediately allow players who have `kaguilds.use`.

## Player Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `kaguilds.use` | `true` | Allow most player-facing guild features |
| `kaguilds.command.main` | `true` | Enter the `/kaguilds`, `/kg`, or `/guild` command |
| `kaguilds.command.help` | `true` | Declared player help permission |
| `kaguilds.command.create` | `false` | Create a guild |
| `kaguilds.command.delete` | `false` | Delete a guild |
| `kaguilds.command.promote` | `false` | Promote a guild member |
| `kaguilds.command.demote` | `false` | Demote a guild member |
| `kaguilds.command.accept` | `false` | Approve a join request |
| `kaguilds.command.deny` | `false` | Deny a join request |
| `kaguilds.command.confirm` | `false` | Confirm a pending operation |
| `kaguilds.command.bank` | `false` | Use the guild bank |
| `kaguilds.command.chat` | `false` | Use guild chat |
| `kaguilds.command.menu` | `false` | Open the main guild menu |
| `kaguilds.command.motd` | `false` | Change the guild announcement |
| `kaguilds.command.rename` | `false` | Rename a guild |
| `kaguilds.command.seticon` | `false` | Set the guild icon |
| `kaguilds.command.settp` | `false` | Set the guild teleport location |
| `kaguilds.command.tp` | `false` | Use guild teleport |
| `kaguilds.command.upgrade` | `false` | Upgrade a guild |
| `kaguilds.command.info` | `false` | View the player's own guild information |
| `kaguilds.command.invite` | `false` | Invite a player to the guild |
| `kaguilds.command.join` | `false` | Request to join a guild |
| `kaguilds.command.kick` | `false` | Remove a guild member |
| `kaguilds.command.leave` | `false` | Leave a guild |
| `kaguilds.command.vault` | `false` | Open a guild vault |
| `kaguilds.command.transfer` | `false` | Transfer guild ownership |
| `kaguilds.command.pvp` | `false` | Use guild battle commands |
| `kaguilds.command.admin` | `false` | Declared admin-command entry permission |
| `kaguilds.command.yes` | `false` | Accept a guild invitation |
| `kaguilds.command.no` | `false` | Decline a guild invitation |
| `kaguilds.command.requests` | `false` | View join requests |
| `kaguilds.command.buff` | `false` | Purchase a guild buff |

## Server Administrator Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `kaguilds.admin` | `op` | Allow all server administration features |
| `kaguilds.admin.reload` | `op` | Reload plugin configuration |
| `kaguilds.admin.help` | `op` | Show the help entry on the admin help page |
| `kaguilds.admin.rename` | `op` | Rename any guild |
| `kaguilds.admin.delete` | `op` | Delete any guild |
| `kaguilds.admin.info` | `op` | View any guild's information |
| `kaguilds.admin.bank` | `op` | View or modify any guild bank |
| `kaguilds.admin.transfer` | `op` | Transfer ownership of any guild |
| `kaguilds.admin.kick` | `op` | Remove a member from a specified guild |
| `kaguilds.admin.join` | `op` | Force an online player to join a guild |
| `kaguilds.admin.vault` | `op` | Open any guild vault |
| `kaguilds.admin.unlockall` | `op` | Release all guild vault locks |
| `kaguilds.admin.setlevel` | `op` | Set a guild level |
| `kaguilds.admin.exp` | `op` | Modify guild experience |
| `kaguilds.admin.arena` | `op` | Configure the guild battle arena |
| `kaguilds.admin.open` | `op` | Open any loaded GUI menu |
| `kaguilds.admin.task` | `op` | View or modify guild task progress |
| `kaguilds.admin.contribution` | `op` | Modify member contribution points |
| `kaguilds.admin.release` | `op` | Extract bundled menu files |

## Current Implementation Notes

- `/kg help` does not currently check `kaguilds.command.help` separately, but it is still gated by `kaguilds.command.main`.
- `/kg admin` actions check `kaguilds.admin` or their corresponding `kaguilds.admin.<action>` permission. `kaguilds.command.admin` does not currently gate those actions separately.
- `/kg admin help` filters entries by the sender's admin action permissions. A sender with an action permission can see that help entry even without `kaguilds.admin.help`.
- Permissions control command entry only. Owner, admin, and member role restrictions are enforced separately by guild business logic.

See [Player Commands](player-commands.md) and [Admin Commands](admin-commands.md) for syntax and guild-role restrictions.
