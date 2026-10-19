# 跨服通信

> 多服部署依赖 Velocity/Bungee + KaProxy Guilds 模块。重构改动通信字段是红线。
> 2026-09-07/08 起架构已重构为"本地权威 + 运行时验证广播",方案与根因见 `research/cross-server-arch-redesign.md`、`research/cross-server-bugs.md`。

## 部署前提

- 每后端同版本 KaGuilds，连同一 MySQL（proxy 模式强制 MySQL，SQLite 会被前置拦截降级）。
- `proxy: true` 仅是"期望"；**真实可用性由 `ProxyCapability` 运行时验证**，失败自动降级单服并写回 `config.yml: proxy: false`。
- 代理装 KaProxy，启用 Guilds 模块 + `legacy-channel-enabled`。
- 后端仅允许经 Velocity/Bungee 接入。
- `server-id` 配置在 proxy 模式下会被代理下发的真实服务器名覆盖（`effectiveServerId`），多服误用默认值不再致命。

## 代理探测（ProxyCapability，2026-09-08 修复空服问题）

- 开服时若 `proxy: true` 且数据库为 MySQL：进入 25s 探测窗口。
- KaProxy 每 5s 周期性广播 `OnlinePlayersList`（`KaProxyCore.startBackgroundTasks()`，不依赖后端有在线玩家）。
- KaGuilds 收到 `OnlinePlayersList` / `ServerIdentify` → `markProxyReachable()` → 跨服启用。
- 有玩家上线时 `GuildListener.onJoin` 补发 `ServerHello` 探针 → 代理回 `ServerIdentify`（真实 serverId）+ 触发一次额外在线列表广播。
- 窗口结束仍无信号 → 降级单服 + 写回 `config.yml`（加注释说明原因）。
- 前置拦截：`proxy: true` 但 `database.type != MySQL` → 直接降级 + 警告，不进探测窗口。
- **注意**：Bukkit 插件消息必须借用在线玩家连接发送；KaGuilds → 代理方向的包（如 `ServerHello`、业务广播）在空服时无法发出。代理 → 后端方向不受此限制（代理主动推送）。因此本服功能正确性绝不依赖发包（P1 传播统一已保证）。

## 通道约定(不可随意改)

- 核心通道：`kaguilds:chat`（常量 `util/NetworkCodec.CHANNEL`）。
- **协议集中定义**：所有子频道名与字段序列集中在 `util/NetworkCodec`，收发两端都引用它；发送统一走 `service/NetworkBus`（`isProxyEnabled` 校验 + 空服入队补发）。
- **身份一律用 UUID**（`MemberKick`/`CrossInvite`/`MemberJoin`/`SyncCache`/`DeleteSync`/`GuildTransfer`/`VaultSync`）；玩家名/公会名仅作显示（玩家可能从未进过接收服）。
- **策略统一**：所有业务包"本地先处理再广播"，不依赖代理回环（KaProxy `broadcast` 排除来源服）。
- 已删除死子频道：`RequestPlayersSync`/`PlayerJoin`/`CLEAR_GUILD`/`NotifyStaff`。
- 改字段需全服后端同版本升级；通道名与子频道首字段名不变则 KaProxy 无需改动。
- 代理转发规则：**排除来源服**广播给其它后端（防回声）。因此后端必须"本地先处理再广播"——这是 P1 的核心修复（原 proxy 模式只发包不本地处理，导致本服功能死循环依赖代理回传，而代理根本不会回传来源服）。
- 子频道清单(以 `NetworkCodec` 为准)：`OnlinePlayersList`、`ServerHello`、`ServerIdentify`、`Chat`、`TaskCompleted`、`GlobalTaskProgress`、`GlobalTaskCompleted`、`SyncCache`、`NotifyRequest`、`CrossInvite`、`MemberJoin`、`MemberLeave`、`MemberKick`、`RenameSync`、`BankSync`、`BuffSync`、`DeleteSync`、`AdminRenameSync`、`VaultSync`、`GuildTransfer`、`ForceRefreshMembers`、`PvPBattleStart`。
  (已删除: `RequestPlayersSync`/`PlayerJoin`/`CLEAR_GUILD`/`NotifyStaff`)
- `OnlinePlayersList` 字段顺序固定：`writeUTF("OnlinePlayersList")` → `writeInt(count)` → 循环 `writeUTF(name)`/`writeUTF(serverId)`。
- `ServerHello`(后端→代理) 与 `ServerIdentify`(代理→后端) 为新增子频道，KaProxy 侧识别 `ServerHello` 不广播、定向回 `ServerIdentify`。

## 关键语义(重构后)

- 消息传播统一为"**本地先处理，再广播**"：`dispatchGuildNotification`/`broadcastMemberStatus`/`sendGuildChat` 永远先本服处理，`isProxyEnabled()` 时才额外发包。
- 接收端（`PluginMessageListener`）处理的是**来自其它服**的包；缓存更新一律用 `membershipCache.updateLocal/removeLocal/invalidateGuildLocal`（不回声，防广播风暴）。
- 身份判断唯一入口：`membershipCache.getGuildId(...)`（并发 + TTL + 查库兜底），不再依赖 `playerGuildCache` 原始 Map。
- 仓库锁：DB 唯一权威（`tryGrabLock` 原子抢锁），内存 `vaultLocks` 仅同服快速拒绝。

## 重构红线

- 改动仓库锁 / 邀请 / 任务 / 聊天 / 在线缓存，必须同步检查 `PluginMessageListener.kt` 与代理转发行为。
- 不能改变子频道名或字段顺序（代理对非 `ServerHello` 包是纯透传）。
- `ServerHello` 是唯一被代理解析的子频道（识别后回 `ServerIdentify` 且不广播）；新增子频道若不希望被广播，需在 KaProxy `handleLegacyGuildMessage` 加识别。

## 涉及文件

- `service/ProxyCapability.kt`：探测、降级、写回配置、`onPlayerJoinProbe()`。
- `service/MembershipCache.kt`：身份缓存与跨服失效广播。
- `listener/PluginMessageListener.kt`：插件消息收发核心（含 `ServerIdentify` 处理）。
- `KaProxy`（`C:\Users\LiXin\IdeaProjects\KaProxy`）：`KaProxyCore.handleLegacyGuildMessage`(ServerHello 识别)、`startBackgroundTasks()`(周期广播)、`ProxyAdapter.broadcastToServer`。
- 跨服同步触发点：`GuildService`、`VaultService`、`TaskManager`、`MembershipCache.invalidate` 等。
