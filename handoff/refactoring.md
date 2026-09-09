# 重构进度

> 原始记录见仓库根目录 `REFACTORING.md`。此处按模块提炼当前状态，并标注风险优先级。
> 重构原则：小步重构保持 `compileKotlin` 通过；优先修数据丢失/资金异常/线程安全问题；最小必要改动；不写死本机路径。

## 已完成

### 指令层拆分
- `GuildCommand` 从大文件拆分为 ~117 行的入口 + 多个 `*CommandHandler`：
  - `GuildMembershipCommandHandler`(invite/join/requests/accept/deny/kick/leave/transfer + 确认执行)
  - `GuildLifecycleCommandHandler`(create/delete/confirm)
  - `GuildSettingsCommandHandler`(settp/tp/rename/seticon/motd/upgrade + 改名确认)
  - `GuildInfoCommandHandler`(info/help/menu/默认菜单/成员在线渲染)
  - `GuildUtilityCommandHandler`(buff/chat/bank/yes/no/vault)
  - `GuildRoleCommandHandler`(promote/demote)
  - `GuildTabCompleter`(Tab 补全)
  - `AdminCommandHandler`(总分发) + `AdminGuild/Task/Contribution/Bank/Arena/UtilityCommandHandler`
  - `PvpCommandHandler`

### 数据访问层拆分
- `DatabaseManager` 从 ~636 行降至 ~64 行：仅保留连接池生命周期、repository 实例、暂存数据模型。
- 抽出 repository：`GuildRepository`、`MemberRepository`、`BankRepository`、`VaultRepository`、`RequestRepository`、`TaskRepository`、`GuildLifecycleRepository`(跨表事务)、`PvPRepository`、`SchemaManager`(建表/迁移 SQL)。
- `GuildModels.kt` 接管 `GuildData`/`MemberData`/`GuildTaskProgress`。
- 合并重复 SQL：repository 内统一走 `findGuild`/`mapGuildData`、`executeGuildUpdate`、`executeBalanceUpdate`、`findPlayerUuidByName` 等。

### 服务层抽取
- `VaultService`(仓库打开、锁同步、租约刷新、管理员打开)、`BankService`(日志/存款/取款流程)。
- `OperationResult` 统一结果类型。

### 线程安全 / 数据一致性修复
- `runMain`/`runAsync` 统一线程入口(主线程/异步)。
- `GuildService` 的 teleportToGuild/openVault/upgradeGuild：异步只查库计算，Vault 检查/扣款/传送/广播回主线程。
- `DatabaseManager.saveVault()` 改为按 `last_editor` 校验锁持有者后更新。
- `depositGuildBalanceIfWithinLimit` / `withdrawGuildBalanceIfEnough` 数据库条件更新，防超存/超取。
- `/kg bank add/take` 余额更新用条件更新；存款失败尝试退还玩家。
- 修复 `/guild chat` 状态残留：多种退出/解散/跨服同步场景清理 `guildChatPlayers`。
- 修复每日上线任务在公会身份生效后才触发(`TaskManager.activatePlayerTasks()`)。

### 任务奖励加固
- 新增 `guild_task_reward_claims` 唯一领取凭证表(个人按 UUID+任务键+日期；全局按 GuildID+任务键+日期)。
- 进度更新事务内：`FOR UPDATE` 行锁 + 全局进度行以空串作用域写入避免可空唯一键并发插入问题。
- 启动迁移回填当日完成进度为凭证，防升级后重复领奖。
- 任务周期按 `task.reset_time` 计算，不再固定自然日零点。

### 文档(2026-07-29)
- 重写 README/README_CN 及 docs/docs-en 首页、安装、Velocity、命令、权限索引。
- 配置文档注明战斗计时器读 `guild.arena.battle-time`(旧 `pvp-time` 不生效)。

### 跨服架构重构(2026-09-07/08,P0-P2)
- 详见 `research/cross-server-arch-redesign.md`(方案与进度)与 `research/cross-server-bugs.md`(根因调研)。
- P0:`MembershipCache`(并发+TTL+查库兜底,身份判断唯一入口)、`ProxyCapability`(运行时探测+SQLite 前置拦截+降级写回 config)、`effectiveServerId`(代理 `ServerIdentify` 下发覆盖)。
- P1:传播统一(本地先处理再广播,消除 proxy 分叉);`invitePlayer` 本地优先;接收端改用 `membershipCache` + `removeLocal`/`updateLocal` 防回声;~18 处 `config.getBoolean("proxy")` → `isProxyEnabled()`。
- P2:仓库物品消失根因修复(`initSql` 初始化 `last_editor=''`、`saveVault` 放宽 NULL 匹配、保存失败保留锁)。
- KaProxy 双向修正:`ServerHello`/`ServerIdentify`(真实 serverId 下发)、`broadcastToServer`、周期广播 `OnlinePlayersList`(每 5s,解决空服探测)。
- 跨服探测流程详见 `cross-server.md`。

### 业务变更:银行不再联动贡献度(2026-09-08)
- `BankService.deposit` 删除加贡献度;`withdraw` 删除贡献度检查与扣除(取款不再要求贡献度)。
- 贡献度改为纯管理员管理(`/kg admin contribution`);管理员命令、PAPI、菜单显示不受影响。
- 删除 `config.yml`/`config_en.yml` 的 `contribution` 配置段;删除 lang 键 `bank-insufficient-contribution`(zh_CN/en_US)。
- 中英文文档同步:`docs{,-en}/config/config.md` 删 contribution 章节;`player-commands.md`/`admin-commands.md` 银行行为描述更新。

### 公会累计总贡献度(2026-09-08)
- `guild_data` 表新增 `total_contribution BIGINT DEFAULT 0`(SchemaManager 迁移兼容已有库)。
- `GuildData` 模型新增 `totalContribution: Long = 0`。
- `GuildRepository.mapGuildData` 读取新列;新增 `incrementTotalContribution(guildId, amount)` 原子 SQL。
- `GuildLifecycleRepository.createGuild` INSERT 包含 `total_contribution = 0`。
- `AdminContributionCommandHandler` 的 `add`(单人和 `-all`)成功后调用 `guildRepository.incrementTotalContribution`;`set`/`clear` 不影响公会总贡献度(仅管理员操作改变)。
- PAPI 新增 `%kaguilds_guild_contribution%`;中英文文档已更新。

## 进行中(可继续)

- 指令层第一轮结构拆分完成；可抽公共命令辅助组件(权限检查/确认提示)。
- 数据访问层结构迁移完成；重点转向事务边界、线程安全、跨服一致性校验。
- Folia 兼容仅议案(`FOLIA_COMPATIBILITY_PROPOSAL.md`)，未改调度实现/构建基线/plugin.yml。

## 未完成 / 待办(按风险优先级)

| 优先级 | 项 | 说明 |
|--------|----|------|
| 高 | 内存缓存线程安全未收敛 | `guildChatPlayers`、`pendingConfirmations`、任务缓存仍是普通 `MutableMap`/`MutableSet`;`playerGuildCache` 已由 `MembershipCache` 收敛(2026-09-07) |
| 中 | 任务奖励两步提交原子性 | 写凭证后执行命令间异常退出会凭证占用但奖励未发；建议 reward outbox/补偿队列 |
| 中 | 仓库保存错误类型细化 | 区分数据库异常 vs 锁已失效(已加部分区分日志) |
| 低 | 指令 Handler 重复 `checkPermission`/`sendConfirmHint` | 可收敛为公共辅助类，不改行为 |
| 低 | Gradle wrapper 外网超时 | 当前用本机 Gradle 绕过(Windows 用 JDK 12 + wrapper --offline) |
| 低 | P3/P4 收尾 | `NetworkBus` 离线补发、清理 `NotifyStaff` 死代码、单服/两服/断网降级实测 |

## 跨服代理注意事项(红线)
- 代理插件 `/home/plugins/KaProxy`，Guilds 模块处理公会消息；核心通道 `kaguilds:chat`。
- 代理侧透明转发，Bukkit 侧不能随意改子频道名或 `DataOutputStream` 字段顺序(`VaultSync`/`SyncCache`/`CrossInvite`/`BuffSync` 等)。
- 代理广播 `OnlinePlayersList` 字段顺序：`writeUTF("OnlinePlayersList")` → `writeInt(count)` → 循环 `writeUTF(name)`/`writeUTF(serverId)`。
- 改动仓库锁/邀请/任务/聊天/在线缓存需同步检查代理转发行为。
