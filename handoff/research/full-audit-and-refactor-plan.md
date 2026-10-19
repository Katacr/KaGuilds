# KaGuilds 全量调研与改造建议

> 调研日期：2026-10-08
> 范围：`src/main/kotlin/org/katacr/kaguilds` 全部约 14,335 行 Kotlin（50 个源文件）+ 资源文件 + plugin.yml
> 方法：按模块并行子任务深读，逐条核对文件:行号；本报告为汇总。
> 背景：插件为 Java 初期风格演进而来的代码，已做过一轮结构拆分（repository/service/command）。本次目标是找出**根因级**问题并给出可落地的改造顺序。

## 〇、总体判断

问题可归为 5 个根因，其余多为表现：

1. **线程模型无契约**：Bukkit API（玩家/世界/GUI/Vault）与 JDBC 的线程边界在代码里是随机的。既存在"异步线程直接发消息/开 GUI"，又存在"主线程同步查库"，方向相反但同样致命。
2. **数据一致性依赖"先查后改"且无唯一约束**：schema 缺唯一键/索引，配合 read-modify-write，导致双公会、重复申请、任务重复计数、余额透支、重复计息。
3. **跨服通信无统一契约**：字段顺序靠人工约定、发包借 `onlinePlayers` 管道（空服丢包）、"本地先处理"与"依赖代理回环"两套互斥策略并存、多处死子频道。
4. **上帝类**：`GuildService`(1999)、`MenuManager`(1860)、`TaskManager`(1038) 职责严重混杂。
5. **无失败补偿**：奖励发放、退款、物品保存等"两步操作"缺少 outbox/回滚，异常即静默丢失。

## 一、P0 立即修复（数据丢失 / 崩溃 / 必现 Bug）

> **状态：2026-10-08 全部 8 项已修复并编译/打包通过（详见 `handoff/refactoring.md`）。**
> 下方保留问题描述与根因，作为修复依据与回归参考。

这些是"用户已经能感知"的确定性缺陷，应优先且可小步独立修复。

### P0-1 解散公会必抛异常（已复现）
- 现象：`/kg confirm` 解散公会时报 `UnsupportedOperationException`（见 `/home/Server/Survival1/logs/latest.log:179-184`）。
- 根因：`KaGuilds.kt:36-55` 的 `playerGuildCache` 匿名 `MutableMap`，`entries/keys/values/size` 一律 `throw`；而 `GuildService.kt:1991` 的 `clearLocalGuildState` 调用 `playerGuildCache.filterValues { ... }`，`filterValues` 内部遍历 `entries` → 必抛。
- 影响：解散流程在 DB 删除**之后**抛异常，成员缓存不清理、`callback(Success)` 不执行，玩家看不到成功提示，内存缓存残留。
- 修复：`clearLocalGuildState` 改用 `membershipCache.invalidateGuildLocal(guildId)`（该方法已存在，`MembershipCache.kt:136`）；同时给 `guildChatPlayers` 做清理。

### P0-2 PvP 背包快照确定性丢装备
- 根因：`PvPManager.kt:100` `takeSnapshot` 只备份 `inventory.contents`（0-35）；`PvPManager.kt:306-315` 发放套装时覆盖 `armorContents`(36-39) 与副手(40)；`PvPManager.kt:110` `restoreSnapshot` 只还原主背包。护甲/副手永久丢失。
- 加重：`PvPManager.kt:394` `stopBattle` 只对在线玩家 `restoreSnapshot`；离线玩家不恢复也不清理；`KaGuilds.kt:250-282` `onDisable` 完全未处理 `inventorySnapshots` → 服务器重启即永久丢装备。
- 修复：快照改用 `player.inventory.contents + armorContents + itemInOffHand`（或序列化整个 `PlayerInventory`）；`onDisable` 恢复/持久化所有未结算快照（建议落盘，防崩溃）。

### P0-3 PvP 超时永不结算，比赛卡死
- 根因：`checkWinCondition(forceCutoff)` 从未以 `true` 调用（`PvPManager.kt:348`、`PvPListener.kt:54/78`、`PvpCommandHandler.kt:203`）；`PvPManager.kt:346-350` 倒计时归零时若双方存活则直接 `cancel()` 不结束。`forceCutoff=true` 分支是死代码。
- 影响：`currentMatch` 永久非空、玩家永久 ADVENTURE、无法开新比赛。
- 修复：计时器归零时调用 `checkWinCondition(forceCutoff = true)`；同时保存计时器句柄，`stopBattle` 显式 `cancel`，避免旧计时器污染新比赛（`PvPManager.kt:152-195`）。

### P0-4 全服玩家菜单必现 NPE
- 根因：`MenuManager.kt:815` `playerUUID.toString()`，但 `getUUIDByName` 可能返回 null（跨服/未入库玩家，`MenuManager.kt:1852-1859`）。
- 修复：null 时用占位符/跳过该行。

### P0-5 奖励凭证已写但奖励不发放（任务）
- 根因：`TaskManager.kt:468` 事务内先写 reward claim，提交后 `TaskManager.kt:528-531` 才执行奖励命令；两步非原子，异常/关服即"claim 占用、奖励丢失"且永不重试。
- 加重：`AdminTaskCommandHandler.kt:180-199` 管理员加进度路径**只更新缓存、从不调用 `executeTaskActions`**，claim 已写 → 奖励永久丢失。
- 修复方向：claim 表加 `state(pending/done)`，事务内写 pending，执行成功后置 done；启动时补偿所有 pending；或引入 outbox 表。

### P0-6 管理员加进度写错行（daily 恒 null）
- 根因：`AdminTaskCommandHandler.kt:183` `if (taskDef.type == "daily") null else null`，两分支恒 null。daily 任务进度被写到 `player_uuid=''` 的全局行。
- 修复：daily 传 `playerUuid`，global 传 null。

### P0-7 仓库锁在普通玩家路径形同虚设 + 跨服解锁可被任意覆盖
- 根因 A：`VaultService.kt:20-56` `openVault` 直接 `tryGrabLock`，**不检查/不写入内存锁、不发 `VaultSync Lock`**（对比 `adminOpenVault` 走 `tryLockVault`）。跨服时其他服不知情。
- 根因 B：`VaultService.kt:123-132` `releaseVaultLock` 收到远端 Unlock 无条件删内存锁，不校验操作者；`PluginMessageListener.kt:460` 丢弃了包里的 uuid。
- 根因 C：`VaultListener.kt:30-47` 正常关闭仓库只做 DB `releaseLock` + 本地内存锁 remove，**不发 Unlock 广播** → 其他服内存锁残留到租约过期。
- 修复：`openVault` 统一走 `tryLockVault`；Unlock 广播校验 uuid；关闭时补发 Unlock。

### P0-8 免费传送 / 公会透支 / 玩家丢钱
- 根因：多处扣费为无 `balance >= cost` 条件的 `updateGuildBalance(-cost)` 且**不检查返回值**：`setGuildTP:844-848`、`renameGuild:1013-1016`、`setGuildIcon:1678-1681`、`setGuildMotd:1769-1772`。
- 更甚：`GuildService.kt:927-954` `executeFinalTeleport` 玩家扣款 `withdrawPlayer` 不检查结果仍传送；`BankService.kt:148-179` 公会余额已原子扣减后 Vault 入账不检查结果；`BankService.kt:97-146` 退款不检查结果。
- 修复：统一改用 `withdrawGuildBalanceIfEnough`（已存在，`BankRepository.kt:32`）并检查布尔返回值；Vault 操作检查 `EconomyResponse.transactionSuccess()`。

## 二、P1 结构性修复（并发 / 一致性 / 数据模型）

> **状态：2026-10-08 已全部完成并部署（详见 `handoff/refactoring.md`）。** 下方保留问题描述与根因。

### P1-1 schema 唯一约束与索引缺失（数据损坏根因）
- `guild_members.player_uuid` 无 UNIQUE（`SchemaManager.kt:43-53`）+ `MemberRepository.addMember:147-175` 先查后插 → 竞态双公会。
- `guild_requests(guild_id, player_uuid)` 无 UNIQUE（`RequestRepository.kt:35-45`）→ 重复申请。
- 全局任务行 `UNIQUE(guild_id, task_key, player_uuid)` 对 NULL 失效（`SchemaManager.kt:100-112`），代码用 `''` 规避但查询兼容 NULL（`TaskRepository.kt:158-214`）→ 历史 NULL 行与 `''` 行并存、进度拆分。
- `guild_bank_logs`/`guild_pvp_history`/`guild_requests` 无索引。
- 无 schema 版本表；`addColumnIfMissing` 吞所有异常（`SchemaManager.kt:169-175`）；`backfillTaskRewardClaims` 每次启动全表 INSERT…SELECT（`SchemaManager.kt:135/146-167`）。
- 修复：补唯一约束（先去重历史数据）+ 索引 + 轻量 `schema_version` 表；`addMember`/`addRequest` 改为 INSERT 冲突处理（`INSERT IGNORE`/`ON DUPLICATE KEY UPDATE`）。

### P1-2 线程边界统一
- 异步线程调 Bukkit API（大量）：`GuildService.kt:44-98`(getOfflinePlayer)、`:274-345`(sendMessage)、`:504-564/589-619/677-715`(成员通知)、`:235-270`(playSound)、`:1876-1903`(sender.sendMessage)；命令层 `GuildMembershipCommandHandler.kt:130-160/215-226`、`GuildSettingsCommandHandler.kt:65/72`、`GuildRoleCommandHandler.kt:34-53`、`AdminContributionCommandHandler.kt:42-133`。
- 主线程同步查库：`TaskManager.kt:447-534`(handleEvent)、`MenuManager` 渲染与 refresh（`:533/544/711/809/953/1007/1015/1858`）、`MenuListener.kt:372/383/420`、`PvpCommandHandler.kt:61-169`、`KaGuildsExpansion.kt:56-119`。
- `PluginMessageListener.onPluginMessageReceived` 是 **Netty 线程**（`PluginMessageListener.kt:12`），分支内直接遍历 `onlinePlayers`、`sendMessage`、`playSound`、`createBossBar`（`:83-88/99-105/110-118/262-284/...`）且对每个在线玩家调 `membershipCache.getGuildId`（会同步查库）。
- 修复：确立契约——**Service 内回调统一用 `runMain` 包裹再回调**；事件处理只收集数据、回主线程渲染；跨服收包整体切主线程；DB 一律异步。

### P1-3 MembershipCache 的"兜底"反成隐患
- `queryDb` 异常返回 null 并缓存为"无公会"10s（`MembershipCache.kt:154-161`）→ DB 抖动时成员被误判退会。
- 主线程无缓存时同步查库（`:48-58/76-84`），叠加 SQLite 连接池 size=1（`DatabaseManager.kt:48`）可能卡主线程。
- `playerGuildCache` 与 `membershipCache` 两套 API 并存，全项目仍 100+ 处直接读 `playerGuildCache`。
- 修复：查询失败与"确无公会"区分（失败不缓存/短负缓存）；主线程无缓存时改为"返回 null + 异步刷新 + 关键路径再校验"；收口所有 `playerGuildCache` 读取。

### P1-4 非原子/重复实现
- 利息计算两份重复且 read-then-write：`GuildListener.kt:44-85` 与 `TaskManager.kt:298-347`；`calculateDaysBetween` 混用 UTC epoch day 与本地零点（`TaskManager.kt:353-360`、`GuildListener.kt:93-100`）→ 时区差 1 天。
- `deleteGuild` 遗漏 5 张关联表（`GuildLifecycleRepository.kt:13-39`）：`guild_vaults/guild_bank_logs/guild_task_progress/guild_task_reward_claims/guild_pvp_history` → 孤儿数据 + 自增 ID 复用串数据。
- `transferGuildOwnership` 角色更新无 `guild_id` 条件（`GuildLifecycleRepository.kt:96/109-119`）。
- `updateContribution`/`getPlayerContribution` 无 `guild_id` 条件（`MemberRepository.kt:293-332`）。
- `getCompletedTaskKeys` 每日 legacy 查询漏 `guild_id`（`TaskRepository.kt:388`）→ 换公会污染完成状态。
- 修复：计息收敛为单一 `BankService.calculateInterest` 条件更新（`WHERE last_interest_date=旧值`）；删除关联表纳入事务；补 guild_id 条件。

### P1-5 线程安全集合
- 非线程安全：`inviteCache`/`crossServerOnlinePlayers`/`guildChatPlayers`（`KaGuilds.kt:27/57/59`）、`vaultLocks`（`VaultService.kt:15`）、`guildDoneCache`/`dailyDoneCache` 及 BossBar 缓存（`TaskManager.kt:21-32`）、`guildMemberNamesCache`（`GuildService.kt:39`）、`pendingConfirmations`（`GuildService.kt:34`）、`MenuListener.chatCatchers`（`:654-657`）。
- `LanguageManager.messages` 普通 HashMap + `get()` 缺键时写整个 YAML（`LanguageManager.kt:21-22/139-144`），异步并发调用可能损坏语言文件。
- 修复：换 `ConcurrentHashMap`/`Collections.synchronized*`；`lang.get` 缺键写文件移出热路径（启动集中补齐或仅内存补默认）。

## 三、P2 架构改造（可维护性）

> **状态：2026-10-08 已完成主要项并部署（详见 `handoff/refactoring.md`）。**
> 已做：指令层 `CommandSupport`、性能(PAPI 缓存/分页 N+1/ps-rs)、生命周期清理、`ItemModelSupport`/`NetworkOutput`/`InterestService` 抽取、跨服索引 bug 修复 + `NetworkBus` + 死代码清理。
> **暂缓（风险高，需实机回归或两端协同）**：GuildService/MenuManager 整体类拆分。
> 跨服契约化已按**方案 A**完成：新增 `NetworkCodec`(集中字段定义) + `NetworkBus`(统一发送/空服补发)、身份统一 UUID、6 频道改为本地先处理再广播、清理死子频道；通道名与首字段名不变，**KaProxy 无需改动**，但 6 后端需同版本升级。方案 B(新通道+信封+版本握手)仍留待多版本混跑/引入第二传输层时再做。

### P2-1 跨服通信契约化
- 字段顺序靠人工约定（`PluginMessageListener.kt` 的 `when` vs 各发送点内联 `writeUTF/writeInt`）。
- vararg 反射写包 + 索引魔法值已出错：`dispatchGuildNotification` 读 `data.getOrNull(6)`（`GuildService.kt:416`），但 `Chat` 只传 5 个元素（`:670`）→ 跨服着色分支死代码。
- 空服丢包：所有发送点 `onlinePlayers.firstOrNull()`（`GuildService` 12 处、`TaskManager` 3 处、`VaultService`、`PvPManager`）。
- 两套互斥策略："本地先处理再广播"（Chat/Member*）vs "只广播依赖代理回环"（BankSync/BuffSync）。
- 死子频道：`NotifyStaff`/`CLEAR_GUILD` 只收不发；`RequestPlayersSync` 只收不发；`GuildService.sendVaultSyncPacket` 无调用；`MembershipCache.invalidateGuild`/`loadOnJoin` 无调用。
- 修复：建立统一的 `NetworkMessage` 编解码层（每个子频道一个 encode/decode，集中字段定义 + 版本号），发送统一走 `NetworkBus`（`isProxyEnabled()` 校验 + 空服入队补发）；统一为"本地先处理再广播"单一策略；清理死子频道。

### P2-2 上帝类拆分
- `GuildService`(1999) 承担约 17 类业务（生命周期/成员/转让/聊天/传送/Buff/图标/公告/PvP/银行/仓库转发/跨服/成员缓存/确认/信息/经验/网络工具）。
- `MenuManager`(1860)：渲染+查库+跨服+版本反射(4 份约 300 行重复)+皮肤+刷新，`menuCache` 死代码，`refreshMenu` 内 O(槽位²) 重复扫描。
- `TaskManager`(1038)：任务进度+BossBar+计息+缓存。
- 建议拆分：`GuildService` → `GuildMembershipService`/`GuildTeleportService`/`GuildBankService`(已有)/`GuildVaultService`(已有)/`GuildInfoService`/`NetworkBus`；`MenuManager` → `MenuRenderer`/`MenuDataSource`/`ItemModelSupport`；`TaskManager` → `TaskProgressService`/`TaskBossBarService`/`InterestService`。

### P2-3 指令层收敛
- 逐字复制：`checkPermission` 7 份、`checkAdminPermission` 6 份、`sendConfirmHint` 2 份。
- 异步回调里 `sendMessage`（见 P1-2）。
- `OperationResult.Error(message)` 携带已本地化文本，`else -> {}` 吞掉失败无反馈（`GuildLifecycleCommandHandler.kt:101` 等）。
- 修复：抽 `CommandSupport` 基类/扩展（权限、确认提示、参数解析、主线程回发）；`OperationResult` 改为类型化错误码，统一渲染。

### P2-4 性能
- `KaGuildsExpansion` 每个 PAPI 请求实时查库无缓存无 try/catch（`:20-130`）。
- `GuildRepository.getGuildsByPage` N+1（`:90-110/296`）；`getMemberCount` 复用连接时不关 `ps/rs`（`MemberRepository.kt:25-39`）。
- Tab 补全 `getBuffTab` 每次查库（`GuildTabCompleter.kt:188-192`）。
- 跨服 BossBar 每次进度发包（`TaskManager.kt:652-654/714-716`）。
- 修复：PAPI 短 TTL 缓存；分页改 JOIN 计数；`ps/rs` 统一 `use`。

### P2-5 生命周期清理
- `onDisable`（`KaGuilds.kt:250-282`）未清理：TaskManager BossBar 缓存/`removeAll`、`inventorySnapshots` 恢复、Vault 租约任务。
- `VaultService.startVaultLeaseTask` 玩家切走后 `holder==null` 直接 return，任务不取消、空转（`:192-204`）。
- `PvPManager` 的 `guildBattleCooldown`/`pendingInvites`/`inviteTimestamp` 永不清理。
- `TaskManager` BossBar 空 map 驻留（`:1010-1011`）。

## 四、建议实施顺序（小步、每步可编译验证）

| 批次 | 内容 | 涉及 P0/P1 | 风险 |
|------|------|-----------|------|
| 批次 1 | 纯 Bug 修复，不动架构 | P0-1/2/3/4/6/7 | 低 |
| 批次 2 | 资金与奖励一致性 | P0-5/8 | 中 |
| 批次 3 | schema 唯一约束+索引+去重迁移 | P1-1 | 中（需数据迁移） |
| 批次 4 | 线程边界契约（先监听器/跨服收包，再 Service 回调） | P1-2/1-5 | 中高 |
| 批次 5 | 缓存收口 + 计息收敛 + 关联表事务 | P1-3/1-4 | 中 |
| 批次 6 | 跨服 NetworkBus 契约化 | P2-1 | 高（涉代理契约红线） |
| 批次 7 | 上帝类拆分 + 指令层收敛 + 性能 | P2-2/3/4/5 | 中 |

> 说明：批次 6 触碰 `kaguilds:chat` 子频道名/字段顺序红线，必须与 KaProxy 双向同步（见 `handoff/cross-server.md`）。建议在批次 6 之前先完成批次 1-5 的稳定性修复。

## 五、附：与既有交接文档的关系

- 本报告是**全量复核**，覆盖并细化了 `handoff/concurrency.md`、`data-layer.md`、`cross-server.md` 中已记录的待办。
- 已修正的既有认知：`handoff/concurrency.md` 称 `playerGuildCache` 已由 `MembershipCache` 收敛——实际仍有 100+ 处直接读取，且废弃适配器的 `entries` 抛异常直接导致 P0-1。
- `handoff/refactoring.md` 记录"银行存取不再联动贡献度"已完成，本次未发现回退。
