# 指令和权限

玩家指令使用 `/kg` 或 `/guild`。服务器管理指令位于 `/kg admin` 下。

- [玩家指令](player-commands.md)
- [管理员指令](admin-commands.md)
- [权限列表](permissions.md)

`kaguilds.use` 当前默认授予所有玩家，并会放行全部玩家子命令。若要使用细分的 `kaguilds.command.*` 权限，必须先通过权限插件显式拒绝 `kaguilds.use`，再按需授予具体节点。

管理员可以使用 `/kg admin help [页码]` 查看当前账号实际可用的管理指令。
