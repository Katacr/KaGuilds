# 指令层

> GuildCommand 已瘦身为入口+分发，业务逻辑迁入各 `*CommandHandler`。

## 当前结构

- `GuildCommand.kt`(~117 行)：Bukkit `onCommand` 入口、基础校验、handler 分发、`onTabComplete` 委托 `GuildTabCompleter`、保留 `performRename()` 等少量回调。
- `command/` 各 Handler：
  - `GuildMembershipCommandHandler`(300)：invite/join/requests/accept/deny/kick/leave/transfer + 确认执行。
  - `GuildLifecycleCommandHandler`(138)：create/delete/confirm。
  - `GuildSettingsCommandHandler`(199)：settp/tp/rename/seticon/motd/upgrade + 改名确认。
  - `GuildInfoCommandHandler`(141)：info/help/menu/默认菜单/在线渲染。
  - `GuildUtilityCommandHandler`(132)：buff/chat/bank/yes/no/vault。
  - `GuildRoleCommandHandler`(68)：promote/demote。
  - `GuildTabCompleter`(196)：Tab 补全 + 跨服在线玩家/Buff/成员名补全。
  - `PvpCommandHandler`(215)：`/kg pvp`。
  - `AdminCommandHandler`(113)：`/kg admin` 总分发。
  - `AdminGuildCommandHandler`(192)：rename/delete/info/transfer/kick/join。
  - `AdminTaskCommandHandler`(215)：任务查看/重置/加进度。
  - `AdminContributionCommandHandler`(206)：单人/全员贡献度设置/增/清零。
  - `AdminBankCommandHandler`(108)：余额/日志/修改。
  - `AdminArenaCommandHandler`(125)：区域/出生点/队伍装备/状态查看。
  - `AdminUtilityCommandHandler`(146)：vault/unlockall/setlevel/exp/open/release 等。
- 管理员帮助：`/kg admin help [page]`，按 `kaguilds.admin` / `kaguilds.admin.<action>` 过滤可见子命令。

## 关键决策

- 保留 `GuildCommand.handleXxx()` 作为委托入口，减少命令/监听器/跨服消息连锁改动。
- 权限检查接入 `kaguilds.use` + 细分权限节点；管理员帮助/补全按细分权限过滤。
- 静默参数 `-s`：`/kg admin bank #ID` 与 `/kg admin exp #ID` 的 add/remove/set。

## 待办

- 多个 handler 重复 `checkPermission()`、`sendConfirmHint()`，可收敛为公共命令辅助类(不改行为)。
- 确认类(confirm)执行逻辑分布：Rename 确认仍回调 `GuildCommand.performRename()`，leave/kick/transfer 确认在 `GuildMembershipCommandHandler`，统一确认流可进一步收敛。
