# Player Commands

The primary KaGuilds command is `/kaguilds`. `/kg` and `/guild` are aliases. This page uses `/kg` consistently.

## Argument Conventions

- `<argument>` is required.
- `[argument]` is optional.
- `#ID` is a guild ID, for example `#12`. Commands that support IDs may also accept a number without `#`, but `#ID` is recommended for clarity.
- Except for `/kg help`, `/kg reload`, and `/kg admin`, commands can only be run by players.

## Permission Behavior

Most player commands are allowed when either condition is true:

1. The player has `kaguilds.use`;
2. The player has the command-specific `kaguilds.command.<subcommand>` permission.

`kaguilds.use` is granted to all players by default, so denying only one command-specific permission does not restrict that command. For granular control, deny `kaguilds.use` first and then grant the required command permissions. Guild role checks still apply after the permission check.

## Quick Reference

| Command | Purpose | Guild role or state | Permission |
| --- | --- | --- | --- |
| `/kg` | Open the default guild menu | None | `kaguilds.command.main` |
| `/kg help [page]` | Show player help | None | Currently gated only by the main command permission |
| `/kg create <name>` | Create a guild | Not currently in a guild | `kaguilds.command.create` |
| `/kg join <name\|#ID>` | Request to join a guild | Not currently in a guild | `kaguilds.command.join` |
| `/kg yes` | Accept a pending guild invitation | Not currently in a guild | `kaguilds.command.yes` |
| `/kg no` | Decline a pending guild invitation | None | `kaguilds.command.no` |
| `/kg info` | Show the player's own guild | Guild member | `kaguilds.command.info` |
| `/kg menu` | Open the guild menu | None | `kaguilds.command.menu` |
| `/kg invite <player>` | Invite a player | Owner or admin | `kaguilds.command.invite` |
| `/kg requests` | List join requests for the guild | Guild member | `kaguilds.command.requests` |
| `/kg accept <player>` | Approve a join request | Owner or admin | `kaguilds.command.accept` |
| `/kg deny <player>` | Deny a join request | Guild member | `kaguilds.command.deny` |
| `/kg kick <player>` | Remove a member | Owner or admin | `kaguilds.command.kick` |
| `/kg promote <player>` | Promote a member to admin | Owner | `kaguilds.command.promote` |
| `/kg demote <player>` | Demote an admin to member | Owner | `kaguilds.command.demote` |
| `/kg transfer <player>` | Transfer guild ownership | Owner | `kaguilds.command.transfer` |
| `/kg leave` | Leave the guild | Non-owner | `kaguilds.command.leave` |
| `/kg delete` | Delete the guild | Owner | `kaguilds.command.delete` |
| `/kg rename <name>` | Rename the guild | Owner | `kaguilds.command.rename` |
| `/kg seticon` | Use the main-hand item as guild icon | Owner or admin | `kaguilds.command.seticon` |
| `/kg motd <text>` | Change the guild announcement | Owner or admin | `kaguilds.command.motd` |
| `/kg settp` | Set the guild teleport location | Owner or admin | `kaguilds.command.settp` |
| `/kg tp` | Teleport to the guild location | Guild member | `kaguilds.command.tp` |
| `/kg upgrade` | Upgrade according to `levels.yml` | Owner or admin | `kaguilds.command.upgrade` |
| `/kg bank add <amount>` | Deposit into the guild bank | Guild member | `kaguilds.command.bank` |
| `/kg bank take <amount>` | Withdraw from the guild bank | Guild member | `kaguilds.command.bank` |
| `/kg bank log [page]` | Show guild bank logs | Owner or admin | `kaguilds.command.bank` |
| `/kg buff <buff-key>` | Purchase a guild buff | Guild member | `kaguilds.command.buff` |
| `/kg vault [1-9]` | Open a guild vault; defaults to 1 | Guild member | `kaguilds.command.vault` |
| `/kg chat` | Toggle persistent guild chat mode | Guild member | `kaguilds.command.chat` |
| `/kg chat <message>` | Send one guild chat message | Guild member | `kaguilds.command.chat` |
| `/kg pvp start <name\|#ID>` | Challenge another guild | Owner or admin | `kaguilds.command.pvp` |
| `/kg pvp accept` | Accept a guild battle challenge | Owner or admin | `kaguilds.command.pvp` |
| `/kg pvp ready` | Mark ready during preparation | Battle participant | `kaguilds.command.pvp` |
| `/kg pvp exit` | Leave the current battle | Battle participant | `kaguilds.command.pvp` |
| `/kg confirm` | Run the current pending action | Depends on the original action | `kaguilds.command.confirm` |
| `/kg reload` | Reload plugin configuration | Server administrator or console | `kaguilds.admin` or `kaguilds.admin.reload` |
| `/kg admin help [page]` | Show server admin commands | Server administrator or console | See the admin permission table |

## Confirmation Flow

The following player operations do not run immediately and must be followed by `/kg confirm`:

- Create a guild;
- Delete a guild;
- Leave a guild;
- Kick a member;
- Transfer ownership;
- Rename a guild.

Only one pending action is stored per player. Starting another action replaces the previous one. The current implementation has no confirmation timeout, so this documentation does not claim that pending actions expire automatically.

## Membership and Role Rules

- An owner cannot leave directly. Transfer ownership first or delete the guild.
- A guild admin cannot kick the owner or another admin. The owner can kick admins and members.
- Only the owner can use `/kg promote` and `/kg demote`, and neither command can target the owner.
- `/kg requests` and `/kg deny` currently perform no additional guild-role check. Any guild member who passes the command permission check can use them.
- The target of `/kg transfer` must be a member of that guild.

## Economy, Vaults, and Buffs

- Bank deposit and withdrawal amounts must be positive integers. These operations require Vault and a working economy provider.
- Deposits cannot exceed the bank limit for the current guild level in `levels.yml`.
- Withdrawals may consume personal contribution points according to `config.yml`.
- Available buff keys and prices are defined in `buffs.yml`.
- Vault numbers range from `1` to `9`; players can only open vaults unlocked by the current guild level.

## Chat and Guild Battles

- `/kg chat` enters or leaves persistent guild chat mode. `/kg chat <message>` sends one message without changing the mode.
- Leaving, being kicked, or having the guild deleted clears the player's local guild chat state.
- A bare number passed to `/kg pvp start` is treated as a guild name. Prefix an ID with `#` when challenging by ID.
- Only one guild battle can run at a time, subject to team size, cooldown, cost, and arena configuration.

## Server Administration

`/kg reload` and `/kg admin ...` are server administration features, not features granted by the in-guild admin role. See [Admin Commands](admin-commands.md) for complete syntax.
