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

### 全量调研 + P0 修复(2026-10-08)
- 全量调研(约 14,335 行)结论见 `research/full-audit-and-refactor-plan.md`,分 7 批次改造。
- 本批次完成全部 8 项 P0(崩溃/数据丢失/必现 Bug),`compileKotlin`/`shadowJar` 通过:
  1. **解散公会抛异常**:`GuildService.clearLocalGuildState` 曾对废弃 `playerGuildCache` 调 `filterValues`(遍历 `entries` 抛 `UnsupportedOperationException`,Survival1 日志实证)。改用 `onlinePlayers + membershipCache.removeLocal` + `invalidateGuildLocal`。
  2. **PvP 背包快照丢装备**:`PvPManager` 快照改为 `PvPSnapshot(contents+armor+offhand)`,恢复时全量还原;新增 `pvp_snapshots.yml` 落盘,`onEnable` 加载遗留、`onDisable` 恢复并落盘,防崩溃/重启丢装备。
  3. **PvP 超时永不结算**:战斗计时器归零改调 `checkWinCondition(forceCutoff = true)`;新增 `readyTask`/`matchTask` 句柄,`stopBattle` 显式 cancel,防旧计时器污染新比赛。
  4. **全服玩家菜单 NPE**:`MenuManager` 中 `playerUUID.toString()` 改 `playerUUID?.toString() ?: ""`。
  5. **任务奖励凭证已写不发奖**:新增 `TaskManager.grantTaskReward(guildId, task, player?)` 统一入口,玩家正常完成与管理员加进度两条路径都执行奖励;`executeTaskActions` 支持无触发者时回退本服该公会在线成员;未知动作类型由 `return` 改 `continue`(不再中止后续奖励)。
  6. **管理员加进度 daily 恒 null**:`/kg admin task ... add <数值> [玩家名]`,daily 解析玩家名→UUID(离线玩家拒绝写入以防奖励丢失),global 传 null;完成后按类型更新 `dailyDoneCache`/`guildDoneCache`。lang/帮助/Tab/中英文文档同步。
  7. **仓库锁**:`VaultService.openVault` 改走 `tryLockVault`(写内存锁+广播 Lock);DB 抢锁失败回滚内存锁并广播 Unlock;`releaseVaultLock` 增加 `occupant` 校验(远端 Unlock 携带真实持有者 UUID,不匹配则忽略);`VaultListener.onVaultClose` 保存成功后回主线程广播 Unlock;`vaultLocks` 改 `ConcurrentHashMap`;`VaultService` 内 `config.getBoolean("proxy")` 改 `isProxyEnabled()`。
  8. **扣费不检查返回值**:`setGuildTP`/`renameGuild`/`setGuildIcon`/`setGuildMotd`/`buyBuff`/`startPvPChallenge` 改 `withdrawGuildBalanceIfEnough`(原子条件更新),失败退款;`executeFinalTeleport` 检查 `withdrawPlayer` 结果;`BankService.withdraw` 玩家入账失败回退公会余额;`createGuild`/`deposit` 退款检查并记 severe 日志。
- **部署**:新 jar 已部署到 Lobby(`KaGuilds-1.1.2.jar`,md5 `257166bcc6eb479ba4e8bc802a505663`),旧 jar 备份于 `/home/Backups/kaguilds-deploy-20261008/`。启动成功无异常。测试清单见 `research/p0-test-checklist.md`。

### 跨服解散公会通知修复(2026-10-08,测试中追加)
- **问题**:`A 服解散公会，B 服该公会玩家收不到通知`。根因:普通 `/kg delete` **从未发送跨服 `DeleteSync`**(仅管理员删除发送);且原接收端依赖 `membershipCache` 判断,而 DB 删除后缓存兜底查库返回 null,判断失效。
- **修复**:
  - `GuildService.deleteGuild`/`adminDeleteGuild` 删除前先取 `memberUuids`,删除后本服先处理(按 UUID 通知在线成员 + 清理缓存/聊天),`isProxyEnabled()` 时广播 `broadcastGuildDeleted`。
  - `DeleteSync` 包扩展为 `guildId + notifyKey + count + UUID...`;接收端按 UUID **精确通知**(不依赖缓存),并读取 `notifyKey` 区分玩家/管理员解散文案。
  - 新增 lang 键 `delete-notify`(zh_CN/en_US)。
  - 双向兼容:接收端对旧格式(仅 guildId)回退缓存判断;新格式对旧版本服(只读 guildId)多余字段被忽略,不崩溃。
- **部署**:新 jar md5 `5345f0e4d530155c4e7f6992233a7024`,已部署 Lobby + Survival1,均启动成功、跨服正常。其余子服(PVE/PVP/Resource1/2/Survival2)仍为旧版,建议同步部署以启用 UUID 精确通知(旧版仍能收到 guildId 并按缓存处理,不影响解散本身)。

### 新增 `/kg list` 指令(2026-10-08,测试中追加)
- 需求:公会列表菜单此前只有主菜单按钮入口,无指令入口。新增 `/kg list` 直接打开服务器全部公会列表(`guilds_list` 菜单)。
- 改动:`GuildInfoCommandHandler.handleList` + `GuildCommand` 白名单与分发 + Tab 补全 + 帮助条目 + 权限 `kaguilds.command.list`(默认 `true`,未加公会也可用) + lang `desc-list`(中英) + 中英文命令/权限文档。
- 部署:新 jar md5 `1e9f4ea15c8cdfd61cc4ef1ea914f364`,已部署 Lobby + Survival1,启动正常。
- 备注:Survival1 本次为开服空服,未即时打印"检测到 KaProxy";`ProxyCapability` 探针为一次性(`firstProbeScheduled`),有玩家上线会补发 `ServerHello` 自愈。若首个玩家上线后探针失败并耗尽 3 次重试,则需重启才再探测——列入 P1 探测健壮性。

### P1 一致性修复(2026-10-08,顺序执行)
- 详见 `research/full-audit-and-refactor-plan.md` 第二节。本批次完成 P1-1~P1-5:
  1. **P1-1 schema 唯一约束与索引**:`guild_members` 加 `uk_member_uuid(player_uuid)`、`guild_requests` 加 `uk_request_guild_player(guild_id,player_uuid)`、`guild_task_progress` 加 `uk_guild_task_player(guild_id,task_key,player_uuid)`;补 `guild_bank_logs`/`guild_pvp_history`/`guild_requests` 索引;新增 `schema_version` 表;`addMember`/`addRequest` 改 `ON DUPLICATE KEY`(MySQL)/`ON CONFLICT`(SQLite) 冲突安全写入;任务表全局行统一 `player_uuid=''`(去掉 NULL 兼容)。测试阶段不处理历史数据(用户确认)。
  2. **P1-3 MembershipCache 兜底**:`queryDb` 区分"确无公会"与"查询失败",失败不缓存/不覆盖旧值;主线程无缓存查库同样区分。
  3. **P1-5 线程安全集合 + 生命周期**:`inviteCache`/`crossServerOnlinePlayers`/`guildChatPlayers`/`vaultLocks`/`teleportTasks`/`pendingConfirmations`/`guildMemberNamesCache`/任务与 BossBar 缓存/`chatCatchers`/PvP 各缓存改 `ConcurrentHashMap`/并发 Set;新增 `TaskManager.shutdown()` 清理 BossBar 与定时器并在 `onDisable` 调用;Vault 租约任务在玩家离线/切走时自行 `cancel`(不再空转)。
  4. **P1-2 线程边界**:跨服 `PluginMessageListener` 收包整体切主线程处理;`GuildService` 22 个带回调方法入口统一用 `runMain` 包装回调(122 处调用点不改);`runMain` 在主线程时立即执行;`TaskManager.handleEvent` 改异步写库 + 主线程做 BossBar/奖励/通知。菜单/PAPI 主线程查库归入 P2-4。
  5. **P1-4 非原子/重复收敛**:`deleteGuild` 事务内清理全部 6 张关联表(含 vaults/bank_logs/task_progress/task_reward_claims/pvp_history);`transferGuildOwnership` 角色更新补 `guild_id` 条件;`getCompletedTaskKeys` 个人任务补 `guild_id`;利息计算新增 `BankRepository.applyInterestIfDateUnchanged` 条件更新,`GuildListener` 与 `TaskManager` 两处统一走它(防并发重复计息);`calculateDaysBetween` 改按系统时区还原日期(修 DST 差一天)。
- **部署**:新 jar md5 `5048ec8250d3e05fa3616a65419b5a93`,已清空测试库 `kaguild`(已备份)后部署 Lobby + Survival1,启动正常、无异常、唯一索引已自动创建、跨服正常。

### P2 可维护性/性能改造(2026-10-08,顺序执行)
- 详见 `research/full-audit-and-refactor-plan.md` 第三节。本批次完成 P2-1~P2-5 的主要项:
  1. **P2-3 指令层收敛**:新增 `command/CommandSupport`(权限检查/确认提示/失败文案);13 个 Handler 的 `checkPermission`(7)、`checkAdminPermission`(6)、`sendConfirmHint`(3) 私有方法改为委托,调用点不变;`GuildInfoCommandHandler` 的 `"§cError: $result"`(打印对象地址)改 `failureMessage`。
  2. **P2-4 性能**:`KaGuildsExpansion` 用 `membershipCache` + 3s TTL 缓存公会/成员数 + 顶层 try/catch;`GuildRepository.getGuildsByPage` 改子查询一次带出成员数(消除 N+1);`MemberRepository.getMemberCount` 复用连接时用 `use` 关闭 Statement/ResultSet。
  3. **P2-5 生命周期**:`PvPManager.checkCooldown` 清理过期冷却条目;`sendChallenge` 清理 60s 过期邀请;`TaskManager.clearTaskBossBar` 清理空的 BossBar 内层 map(防驻留)。
  4. **P2-2 上帝类拆分(部分)**:抽 `util/ItemModelSupport` 收敛 MenuManager 中 4 份约 300 行重复的 ItemModel 反射逻辑;抽 `util/NetworkOutput` 统一 GuildService/VaultService 的字节输出封装;抽 `service/InterestService` 收敛 GuildListener 与 TaskManager 的重复计息实现(统一条件更新)。**未做** GuildService/MenuManager 的整体类拆分(风险高、需实机回归,暂缓)。
  5. **P2-1 跨服通信(部分)**:修复 `dispatchGuildNotification` 的 `Chat` 索引 bug(`getOrNull(6)`→`getOrNull(4)`,跨服预着色分支此前恒 null);新增 `service/NetworkBus` 统一发送入口(`isProxyEnabled` 校验 + 空服告警),VaultService/PvPManager 发送点接入;清理 GuildService 死方法 `sendVaultSyncPacket`。**未做** 全量 `NetworkMessage` 编解码契约化(触碰 KaProxy 透传红线,需两端协同,暂缓)。
- **部署**:新 jar md5 `ee34d2323a1c85cf2dafda693347d3dc`,已清空测试库(已备份)后部署 Lobby + Survival1,启动正常、无异常。

### P2-1 跨服通信契约化(2026-10-08,方案 A)
- **决策**:采用方案 A(后端内部重构,不改 wire 通道/首字段,KaProxy 无需改动),而非方案 B(新通道+信封+版本握手,flag-day)。理由:KaProxy 对 `kaguilds:chat` 是纯透传(仅解析 `ServerHello` 首字段),真正的缺陷是"策略不统一",A 可增量部署与回滚,B 的价值(滚动升级/多版本混跑)当前阶段用不上。
- **改动**:
  1. 新增 `util/NetworkCodec`:集中定义全部子频道名与字段序列,每个子频道一个 `encodeXxx`;发送端不再散落 `writeUTF/writeInt`,接收端字段顺序以它为准。
  2. **身份统一 UUID**:`MemberKick`(`guildId+targetUuid+targetName`)、`CrossInvite`(`targetUuid+guildId+guildName+senderName`)、`MemberJoin`(`guildId+playerUuid+playerName`)改为 UUID 定位;名称仅作显示(玩家可能从未进过接收服,名称用于避免空显示)。`SyncCache`/`DeleteSync`/`GuildTransfer`/`VaultSync` 本就用 UUID。
  3. **策略统一为"本地先处理再广播"**:修复 `MemberKick`/`BankSync`/`BuffSync`/`RenameSync`/`AdminRenameSync`/`TaskCompleted` 原先"只广播依赖代理回环"(在 KaProxy `broadcast(excludeSource)` 下来源服收不到)的缺陷。
  4. 新增 `service/NetworkBus`:统一发送入口(`isProxyEnabled` 校验 + 空服入队补发 `flushPending`,玩家上线时补发)。
  5. 清理死子频道/死代码:`RequestPlayersSync`/`PlayerJoin`/`CLEAR_GUILD`/`NotifyStaff`(收发两端均无有效链路,KaProxy 亦无引用)、`GuildService.createDataOutput/createDataOutputForTask`、`notifiedCount`;接收端每个分支包 try/catch(畸形包不再逃逸到网络线程)。
  6. `KaGuilds` 注册通道与 `ProxyCapability` 探针改用 `NetworkCodec` 常量。
- **红线**:通道名 `kaguilds:chat` 与子频道首字段名不变,KaProxy 无需改动;但**协议字段已变,6 个后端必须同版本升级**(新旧混跑会字段错位)。
- **部署**:新 jar md5 `41a56f2b502b6581a01f1411973f2bdb`,已清空测试库(已备份)后部署 Lobby + Survival1,启动正常、无异常。

### 修复:创建公会唯一键冲突(2026-10-19,实测发现)
- **现象**:`/kg confirm` 创建公会报 `SQLIntegrityConstraintViolationException: Duplicate entry 'c35bab50-...' for key 'guild_members.uk_member_uuid'`,创建失败(已退款)。
- **根因**:P1-1 给 `guild_members.player_uuid` 加了唯一约束后,`GuildLifecycleRepository.createGuild` 仍用**普通 INSERT** 写会长行;而玩家上线时 `GuildListener.onJoin → ensurePlayerExists` 已为其建了 `guild_id=-1` 的档案行 → 唯一键冲突。
- **同类隐患**:`MemberRepository.ensurePlayerExists` 先查 `guild_id=-1` 行,已入会玩家查不到该行会再 INSERT → 同样冲突。
- **修复**:
  - `createGuild` 的会长行改为 upsert(`ON DUPLICATE KEY UPDATE` / `ON CONFLICT DO UPDATE`),复用已有档案行。
  - `ensurePlayerExists` 改为 upsert,只更新 `player_name`,不再新建行。
  - `createGuild` 失败日志由 `printStackTrace` 改为单行结构化 warning(避免刷屏),仍回滚并触发退款。
- **部署**:新 jar md5 `b8e24f06097b9d8164c387a50b1dc7a3`,已部署**全部 7 个后端**(Lobby/Survival1/PVE/PVP/Resource1/Resource2/Survival2),20s 错峰启动,全部 `Done`、无异常、无 Duplicate entry。

## 进行中(可继续)

- 指令层第一轮结构拆分完成；可抽公共命令辅助组件(权限检查/确认提示)。
- 数据访问层结构迁移完成；重点转向事务边界、线程安全、跨服一致性校验。
- Folia 兼容仅议案(`FOLIA_COMPATIBILITY_PROPOSAL.md`)，未改调度实现/构建基线/plugin.yml。
- 全量调研剩余批次(见 `research/full-audit-and-refactor-plan.md`):P1 schema 唯一约束、线程边界统一、缓存收口、计息收敛;P2 跨服 NetworkBus 契约化、上帝类拆分。

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
