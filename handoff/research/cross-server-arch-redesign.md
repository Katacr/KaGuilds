# 跨服架构重构方案 (KaGuilds + KaProxy 双向)

> 调研/设计日期：2026-09-07
> 范围：KaGuilds-refactor 跨服逻辑 + KaProxy 转发契约
> 目标：让单服/跨服走同一套可靠本地路径；proxy 不再是"假设可靠"的来源，而是"运行时验证的能力"；网络不可用时自动降级为单服。

## 一、已确认的 KaProxy 转发契约（来自源码，非猜测）

- 通道：`kaguilds:chat`（`LEGACY_GUILDS_CHANNEL`），由 `KaProxyVelocity.onPluginMessage`(113-116) 接收 → `handleLegacyGuildMessage` → `adapter.broadcast(..., sourceServer)`。
- `ProxyAdapter.broadcast`(183-190)：**排除来源服**，广播到所有其它后端。即 A 发的包只到 B/C，**绝不到 A**。
- 代理**完全不解析** `kaguilds:chat` 的 payload（原样转发），所以子频道名/字段顺序由 KaGuilds 自定，无契约风险。
- KaProxy 在 `ProxyInitializeEvent` 和每次 `playerTopologyChanged`（玩家进/出/切服）时主动 `broadcastLegacyGuildPresence()`，下发旧格式 `OnlinePlayersList`（`writeUTF("OnlinePlayersList")` → `writeInt(count)` → 循环 `writeUTF(name)`/`writeUTF(serverId)`），以及统一协议 `presence`。
- 配置默认：`modules.guilds.enabled=true`、`legacy-channel-enabled=true`、`sync-player-list=true`。
- 统一协议 `kaproxy:main` 有信封（MAGIC + VERSION + module + action），用于 tpa/back/kamenu；guilds 走 legacy 通道，绕过信封。

### 直接结论
1. **后端必须"本地先处理 + 额外广播"**。当前 KaGuilds `dispatchGuildNotification`/`sendGuildChat` 在 proxy 模式下"只发包不本地处理"，而代理排除来源服 → **本服自己的聊天/通知都发不出**。这是"跨服模式功能崩"的核心机制。
2. KaProxy 主动下发的 `OnlinePlayersList` 是天然的"代理存活信号"，可作 proxy 探测依据（被动，零侵入）。

## 二、总体原则（用户确认）

- 不把 `proxy: true` 当作可靠跨服来源；开服时验证与 KaProxy 能通讯才启用，否则运行时降级为 false 并写回配置。
- KaProxy 也是用户项目，两端可双向修正以确保功能可用。

## 三、目标架构：本地权威 + 运行时验证的广播

### 层 1：Proxy 能力 = 运行时验证，而非配置假设

KaGuilds 新增 `service/ProxyCapability`（或并入启动流程）：
- 读取 `config.proxy` 作为"期望模式"。
- 若期望 `true`：
  - 开服后进入探测窗口（建议 15s），**被动等待**来自代理的 `OnlinePlayersList` 或 `presence`（`PluginMessageListener` 收到即置 `proxyVerified=true`）。
  - 也可在 KaProxy 侧补一个显式 `backend_ready` 回执（双向修正选项，见层 5）。
  - 窗口结束仍未收到 → 判定不可用 → `effectiveProxy=false`，并把 `config.yml` 的 `proxy` 改为 `false` 并加注释说明降级原因，避免每次启动反复探测刷日志。
- 全部业务读取 `plugin.isProxyEnabled()`（返回 `effectiveProxy`），替换现有 `config.getBoolean("proxy")` 约 20+ 处。

### 层 2：本地先处理，再广播（修复"只发包不本地处理"）

- `dispatchGuildNotification`、`sendGuildChat`、`broadcastMemberStatus` 等统一为：
  ```
  fun notifyXxx(...) {
      sendToLocalMembers(...)        // 永远执行（遍历本服在线玩家）
      if (plugin.isProxyEnabled()) sendPluginMessage(...)  // 仅额外广播给其他服
  }
  ```
- `PluginMessageListener` 各分支只处理"来自其它服"的包（本来就是），且接收端**不再用 `playerGuildCache` 硬过滤**——改用 `membershipCache.getGuildId`（带查库兜底），不会漏发也不会因缓存缺失静默丢消息。

### 层 3：身份缓存统一（消除 25+ 处脆弱 Map 读取）

新增 `service/MembershipCache`（原 `playerGuildCache` 升格）：
- `ConcurrentHashMap<UUID, CachedGuildId>`，每项带 `expireAt`（TTL，如 30s）。
- `getGuildId(player): Int?`：命中未过期 → 返回；未命中/过期 → 触发异步查库刷新并返回最新（调用方多在异步线程，可直接查；主线程调用用"返回旧值+后台刷新"避免卡顿）。
- `set(uuid, guildId)` / `invalidate(uuid)`：本地成员变更时立即更新并广播 `SyncCache` 让其它服 `invalidate`。
- 替换全部 `plugin.playerGuildCache[x]` 读取（服务层/命令层/MenuManager/PvPManager/监听器/PluginMessageListener）。
- 把 `playerGuildCache/guildChatPlayers/vaultLocks` 改为线程安全集合。

### 层 4：仓库锁 DB 唯一权威（修复物品消失）

- `tryGrabLock` 的 `initSql`：MySQL/SQLite 都必须初始化 `last_editor`（=当前玩家）与 `items_data=''`（或 NULL 但 save 时允许首次写入），避免 `saveVault` 的 `WHERE last_editor=?` 对 NULL 行永远失败。
- `saveVault`：以 DB 锁为唯一权威，`WHERE guild_id=? AND vault_index=? AND (last_editor=? OR lock_expire=0)`；保存失败仍清本地内存锁但明确提示并保留数据安全风险日志。
- 内存锁只用于"同服快速拒绝"，不作为保存条件；`VaultSync` 跨服广播仅做内存快速拒绝 + 强制过期提示。

### 层 5（可选双向修正）：KaProxy 补"后端就绪"信号

若被动 `OnlinePlayersList` 不够（如代理启动晚于后端），可在 KaProxy 侧：
- 新增对 legacy 通道的 `Ping` 子频道回执：后端发 `kaguilds:chat` 的 `Ping` → 代理识别后回 `Pong` 给来源服（需改 `handleLegacyGuildMessage` 解析首字段，不再纯透传——这是唯一需要代理"解析" guilds 包的地方，影响小且可控）。
- 或在统一协议 `kaproxy:main` 加 `core` 的 `backend_hello`，后端启动发，代理回 `backend_ack`。
- 推荐：**优先用被动 `OnlinePlayersList`**；若实测代理启动晚于后端导致漏探，再上 `Ping/Pong`。

### 层 6：发包管线补强

新增 `NetworkBus`（或封装发送工具）：
- 发送前确认 `isProxyEnabled()`。
- 若 `onlinePlayers` 为空，包入队，等玩家 Join 或定时 flush 补发，避免"瞬时无在线玩家 → 包永久丢失"。
- 清理 `NotifyStaff` 死代码，统一通知通道为 `NotifyRequest`。

## 四、落地顺序

| 阶段 | KaGuilds 改动 | KaProxy 改动 |
|------|--------------|-------------|
| P0 | `MembershipCache` 替换 `playerGuildCache`；`ProxyCapability` 运行时探测 + 降级写回 + `isProxyEnabled()` | （无需，被动用现有 `OnlinePlayersList`） |
| P1 | 传播统一：本地先处理再广播；`PluginMessageListener` 接收端改用 `membershipCache` | 无需 |
| P2 | 仓库锁 DB 唯一权威 + `initSql`/`saveVault` 修复 | 无需 |
| P3 | `NetworkBus` 离线补发；清理 `NotifyStaff` | 无需 |
| P4 | 验证：单服跑全功能 → 两子服 + KaProxy 跑同用例 → 断网/无 KaProxy 验证自动降级 | 如需补 `Ping/Pong` 在此阶段；否则无 |
| P5 | 收尾：更新 REFACTORING.md、handoff、文档（proxy 降级行为说明） | 同步文档（若有改动） |

## 五、为什么能避免"单服能用跨服崩"

- 单服与跨服走完全相同的本地逻辑路径，差别仅是"是否额外广播给其他服"。
- 正确性（身份判断、仓库保存）全部落在本服 DB/本地缓存，不依赖网络往返。
- proxy 从"假设可用"变为"运行时验证"，网络不可用时自动降级，绝不建立错误假设。
- 两端可双向修正，协议对齐有保证。

## 六、落地进度（2026-09-07）

### 已确认决策
- Proxy 探测：被动等待代理下发信号（`OnlinePlayersList` / `ServerIdentify`），零侵入。
- 降级：写回 `config.yml: proxy: false` + 注释（已实现）。
- 主线程读 `getGuildId`：主线程缺失缓存时同步查一次（避免旧值为 null 误判），异步线程直接查。
- 仓库 `initSql`：P2 阶段处理。
- Redis：本次不引入，后续作为可选跨服增强（聊天/邀请/锁）。

### P0 已完成（编译通过）
- 新增 `service/MembershipCache`：并发安全（`ConcurrentHashMap`）+ TTL（有公会 30s / 无公会 10s）+ 查库兜底；全插件身份判断唯一入口；`playerGuildCache` 改为委托它的 `@Deprecated` 视图（保留旧读取点兼容）。
- 新增 `service/ProxyCapability`：
  - 前置拦截：proxy:true 但 database.type != MySQL → 直接降级 + 警告 + 写回 config。
  - 运行时探测：开服发 `ServerHello` 探针，被动等 `OnlinePlayersList`/`ServerIdentify` 标记可达；15s 窗口未收到 → 降级单服 + 写回 config。
  - `isProxyEnabled()` 作为业务统一入口。
- `KaGuilds`：接入 `membershipCache`/`proxyCapability`；新增 `effectiveServerId`（权威 server 标识，proxy 模式由代理下发覆盖）；`isProxyEnabled()`。
- `PluginMessageListener`：处理 `ServerIdentify` 分支覆盖 `effectiveServerId`；`OnlinePlayersList` 触发 `markProxyReachable`；`server-id` 读取全部改为 `effectiveServerId`。
- `GuildService`：`syncPlayerCache` 改公开（供 MembershipCache 跨服失效广播）；`server-id` 读取改 `effectiveServerId`。
- `PvPManager`：`server-id` 读取改 `effectiveServerId`；跨服判断改 `isProxyEnabled()`。
- **KaProxy 侧（双向修正）**：
  - `KaProxyCore.handleLegacyGuildMessage`：识别 `ServerHello` 探针，回 `ServerIdentify`（携带真实 serverId）给来源服，且不广播探针。
  - `ProxyAdapter` 新增 `broadcastToServer`；`KaProxyVelocity.VelocityAdapter` 实现之。

### P1 已完成（编译通过）
- `dispatchGuildNotification` 重构：先本服处理（`sendToLocalGuildMembers`/`sendNotifyRequestLocal`/`sendLocalChat`，均走 `membershipCache.getGuildId` 判断），再在 `isProxyEnabled()` 时额外广播给其他服。**消除 proxy 分叉，本服功能不再依赖代理转发。**
- `sendGuildChat`：`proxy` 分叉改为统一走 `dispatchGuildNotification`（本服发送 + 可选广播）；本地缓存写入改 `membershipCache.set`。
- `broadcastMemberStatus`：先 `sendLocalMemberStatus`（走 `sendToLocalGuildMembers`），再可选广播。
- `invitePlayer`：改为"本地优先（目标在本服直接处理）+ 跨服兜底（目标不在本服且 proxy 可用时发 `CrossInvite`）"，修复原 proxy 模式只发包导致本服目标收不到邀请的缺陷。
- `PluginMessageListener` 接收端：所有 `playerGuildCache[...]` 读取改为 `membershipCache.getGuildId(...)`（带查库兜底）；删除/写入型改为 `removeLocal`/`updateLocal`（不广播，避免回声）；`CLEAR_GUILD`/`DeleteSync` 遍历清理改用 `invalidateGuildLocal` 或 `onlinePlayers` + `getGuildId`。
- 全服 `config.getBoolean("proxy")` 约 18 处统一替换为 `plugin.isProxyEnabled()`（GuildService + TaskManager）。
- `MembershipCache` 新增 `updateLocal`/`removeLocal`/`invalidateGuildLocal`（接收端用，避免回声）。

### P2 已完成（编译通过）
- 仓库物品消失根因修复：
  - `VaultRepository.tryGrabLock` 的 `initSql` 显式初始化 `last_editor=''`（之前依赖 DEFAULT NULL），消除 `saveVault` 的 `WHERE last_editor=?` 对 NULL 行永远不匹配导致物品不保存。
  - `saveVault` 的 WHERE 放宽为 `last_editor = ? OR last_editor IS NULL OR last_editor = ''`，允许首次/本人写入；若是他人持有则拒绝（防覆盖）。并加区分日志。
  - `VaultListener.onVaultClose` 保存失败时**保留物理锁与内存锁**（不再清内存锁），避免"锁失效但内存锁清了可重复打开"的混乱，等待租约过期后玩家可重试。
- 仓库正确性回归 DB 锁唯一权威（tryGrabLock 原子抢锁），内存锁 `vaultLocks` 仅作同服快速拒绝。

### P3-P4 待办（未开始）
- P3：`NetworkBus` 离线补发（无在线玩家时不丢包）；清理 `NotifyStaff` 死代码（与 `NotifyRequest` 并存）。
- P4：验证（单服 / 两服+KaProxy / 断网降级）。

## 七、待用户确认（历史）
（已在实现中按推荐项落实，保留供追溯）
1. Proxy 探测用"被动等待 `OnlinePlayersList`"（已采用）。
2. 降级时写回 config（已采用）。
3. 主线程兜底策略（已采用：缺失时同步查一次）。
4. 仓库 initSql 初始化 last_editor（P2 处理）。
