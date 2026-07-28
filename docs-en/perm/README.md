# Commands and Permissions

Player commands use `/kg` or `/guild`. Server administration commands are grouped under `/kg admin`.

- [Player Commands](player-commands.md)
- [Admin Commands](admin-commands.md)
- [Permission List](permissions.md)

`kaguilds.use` is currently granted to every player by default and allows every player subcommand. To use granular `kaguilds.command.*` permissions, explicitly deny `kaguilds.use` through the permission plugin, then grant only the required command nodes.

Administrators can run `/kg admin help [page]` to list the management commands available to their account.
