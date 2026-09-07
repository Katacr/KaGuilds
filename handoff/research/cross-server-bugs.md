# 跨服逻辑缺陷调研 (cross-server bugs)

> 调研日期：2026-09-07
> 范围：`PluginMessageListener.kt`、`GuildService.kt`、`VaultService.kt`、`VaultRepository.kt`、`VaultListener.kt`、`GuildListener.kt`、`GuildUtilityCommandHandler.kt`、`KaGuilds.kt`
> 现象：`proxy: true` + MySQL 下，公会仓库物品消失、管理员收不到加入申请、公会聊天不可用。

## 关键架构事实

- 跨服消息通道只有 `kaguilds:chat` 一个，子频道由 `DataOutputStream.writeUTF` 首字段区分。
- 所有发包都借用 `plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(...)` 作为管道（即必须有在线玩家才能发出；代理/KaProxy 负责转发到其他子服）。
- 本服玩家公会身份判断几乎完全依赖内存 `playerGuildCache: MutableMap<UUID, Int>`（非线程安全 Map）。
- `playerGuildCache` 的写入时机：玩家 Join 异步读库(GuildListener:21-33)、创建/加入/接受邀请成功时、收到 `SyncCache` 时、部分命令内。
- Join 事件是**异步**读库写入缓存，**存在竞态窗口**：玩家上线后到缓存就绪前的任何操作读到 null。

## 根因 1：仓库物品消失（proxy + MySQL）

### 现象
放入物品，重新打开后消失。

### 根因链路
- `openVault` 流程（VaultService:20-56）：先 `tryGrabLock`（写 DB 锁 + `last_editor`），再 `getVaultData` 读内容，主线程填充 Inventory。
- `onVaultClose`（VaultListener:14-52）：序列化 → `saveVault(guildId, index, data, playerUuid)` → 成功才 `releaseLock` + 清内存锁。
- `saveVault`（VaultRepository:12-36）SQL：
  ```sql
  UPDATE guild_vaults SET items_data = ?, lock_expire = 0
  WHERE guild_id = ? AND vault_index = ? AND last_editor = ?
  ```
  带 `last_editor = playerUuid` 条件。

**核心缺陷：跨服内存锁与数据库锁是两套独立状态，且 `SyncCache`/VaultSync 锁同步存在竞态。**
1. 服务器 A 上玩家 P 打开仓库 → 写 DB 锁（`last_editor=P`）+ 本地内存锁 `vaultLocks[(g,p)]=P`。
2. 跨服模式 `tryLockVault` 会发 `VaultSync/Lock` 到其他服（VaultService:83-85），其他服 `syncRemoteLock` 写入本地 `vaultLocks`。
3. 关闭时 `saveVault` 用 `last_editor=P` 更新。**问题在 MySQL 并发 + 租约刷新**：`startVaultLeaseTask`（VaultService:192-204）每 5 秒（100 tick）跑一次，但内部 `runAsync` 刷新 `last_editor=playerUuid` 的租约。若玩家在 A 服打开，租约刷新在 A 服线程；这本身没问题。
4. **真正致命点**：`tryGrabLock`（VaultRepository:68-109）的 `initSql` 对 MySQL 用 `INSERT IGNORE`，只插 `(guild_id, vault_index, lock_expire=0)`，**不初始化 `last_editor` 与 `items_data`**。第一次对某公会新仓库打开时，`guild_vaults` 行可能 `last_editor=NULL`。此时：
   - `saveVault` 的 `WHERE last_editor = ?` 对 `last_editor IS NULL` 的行**永远匹配不上** → `executeUpdate() == 0` → 返回 false → 物品**不保存**。
   - 但是 `VaultListener` 在 `saveSuccess==false` 分支仍清了内存锁（VaultListener:45），并提示 `error-database`，玩家以为存了实际没存 → 物品消失。
5. 另一路径：若 A 服关闭窗口后 `saveVault` 因 `last_editor` 不匹配（已被 B 服 `VaultSync/Lock` 的 `syncRemoteLock` 覆盖？不会，syncRemoteLock 只改内存不改 DB last_editor）失败 → 物品丢失。
6. 还有：同一公会仓库在 A 服 P 打开、B 服 Q 也打开，`tryGrabLock` 的 `WHERE (lock_expire < now OR last_editor = me)` 允许第二个人在租约过期前抢到（若 `last_editor` 初始 NULL），两边同时编辑，后关者覆盖先关者 → 物品丢失/错乱。

**结论**：`saveVault` 的 `last_editor` 强条件 + `initSql` 不初始化 `last_editor` + 跨服内存/DB 锁不一致，是物品消失的主因。单服模式因为 `tryLockVault` 在 proxy=false 时走不同分支（不查 DB 锁，只查本地内存锁），反而绕过了该 bug。

## 根因 2：管理员收不到加入申请

### 现象
玩家提交 `/kg join <guild>`（申请），管理员无通知。

### 根因链路
- 申请提交：`requestJoin`（GuildService ~360-404）→ `dispatchGuildNotification(guildId, "NotifyRequest", guildId, guildName, player.name)`。
- 跨服分支（GuildService:414-427）：只把 `(subChannel, guildId, guildName, appName)` 打包发 `kaguilds:chat`，**依赖代理转发到其它服**，由 `PluginMessageListener` 的 `"NotifyRequest"` 分支（PluginMessageListener:246-277）处理。
- `"NotifyRequest"` 分支逻辑：遍历本服在线玩家，`if (playerGuildCache[uuid] == targetId)` 才发。
- **致命点 1**：`dispatchGuildNotification` 的 `data` 是 `vararg Any`，且只按 `is Int`/`is String` 写入。注意 `guildId` 是 `Int`，但 `guildName`/`appName` 是 `String`，`targetGuildId` 也是 `Int`。打包顺序：`subChannel, guildId(Int), guildName(String), appName(String)`。而接收端 `NotifyRequest` 读取顺序：`readInt()`(targetId) → `readUTF()`(guildName, 注释说"未使用") → `readUTF()`(appName)。**顺序一致**，这里没问题。
- **致命点 2（真正原因）**：`NotifyRequest` 接收端用 `playerGuildCache[p.uniqueId] == targetId` 判断本服目标管理员。但在 `proxy: true` 下，管理员的 `playerGuildCache` 只在 Join 时异步加载（GuildListener）。**若申请由 B 服玩家发起，A 服管理员能收到 NotifyRequest，但 A 服管理员的缓存可能因未同步或过慢而不在**。更关键：
  - **`NotifyRequest` 接收端根本不做异步权限校验的"发送"前提**——它确实会发。但如果 A 服管理员所在服**没有在线玩家作为发包管道**（其实有管理员自己在线就有），包能发出。
  - **真正的根因**：`dispatchGuildNotification` 在 `isProxy` 分支**只发包，不本地发送**。而 `NotifyRequest` 接收端遍历 `onlinePlayers` 用 `playerGuildCache == targetId` 过滤。在 `proxy` 模式下，提交申请的那台服务器（B 服）的管理员如果是**同一台服务器**的，本应能收到——但注意：提交申请时 `dispatchGuildNotification` 已经把包发给代理，代理转发回**所有**子服（含 B 服自己），B 服 `NotifyRequest` 处理时用 `playerGuildCache` 过滤。
  - **竞态**：B 服提交申请瞬间，B 服管理员的 `playerGuildCache` 已加载（他在线一段时间）→ 能收到。**但若管理员在 A 服，且 A 服刚启动 / 该管理员刚上线，缓存异步加载未完成** → 过滤失败 → 收不到。
  - **还有一个更直接的 bug**：`NotifyRequest` 用 `playerGuildCache` 判断，但**跨服 `SyncCache` 可能把缓存清掉**（`gId == -1` 时 `guildChatPlayers.remove` 但 `playerGuildCache.remove(targetUuid)` 始终执行，见 PluginMessageListener:128-144）。`SyncCache` 只在该玩家身份变更时发，正常不影响他人。
- **补充**：`NotifyStaff` 分支（PluginMessageListener:161-188）是另一个申请通知通道（由谁发？grep 显示 GuildService 用的是 `NotifyRequest`，不是 `NotifyStaff`）。`NotifyStaff` 几乎未被发送端调用 → 死代码，说明两条通知路径设计混乱。

**结论**：管理员收不到申请的直接原因是 `NotifyRequest` 依赖 `playerGuildCache` 且过滤严格 + 跨服缓存加载异步竞态；间接原因是 `proxy` 模式只发不本地发 + 通知通道设计混乱（`NotifyRequest` vs `NotifyStaff` 并存）。

## 根因 3：公会聊天不可用（proxy + MySQL）

### 现象
`/kg chat` 发消息，公会成员收不到，或自己进不了聊天模式。

### 根因链路
- 进入聊天模式：`handleChat`（GuildUtilityCommandHandler:33-60）：无参数时 `guildChatPlayers.contains`？否则 `playerGuildCache[uuid] ?: getGuildIdByPlayer`。**若 `playerGuildCache` 因异步未加载而返回 null，且查库也因 proxy 模式其它逻辑异常 → 进不了聊天模式**（已修复部分：`REFACTORING.md` 记录修了 chat 残留）。
- 发送：`sendGuildChat`（GuildService:615-673）：异步查库 `getGuildIdByPlayer` 拿到 guildId → 更新缓存 → 构建消息 → `dispatchGuildNotification(guildId, "Chat", guildId, name, msg, role, colored)`。
- 跨服分支只发包；接收端 `"Chat"`（PluginMessageListener:59-75）遍历本服在线玩家 `if (playerGuildCache[uuid] == targetGuildId)` 才 `sendMessage(coloredMessage)`。
- **致命点**：接收端用 `playerGuildCache` 判断接收者是否属于该公会。在 `proxy: true` 下：
  1. 收消息的服务器上，目标玩家的 `playerGuildCache` **必须已加载**。玩家 Join 异步加载，若加载慢或失败（如 `getGuildIdByPlayer` 在 MySQL 上慢/异常），则收不到。
  2. **更严重的架构缺陷**：`sendGuildChat` 在 proxy 模式下把 `coloredMessage` 作为数据发出，接收端直接用 `coloredMessage` 发送，**发送端已 PAPI 解析过**。但接收端判断 `playerGuildCache == targetGuildId` 仍依赖缓存。
  3. **终极根因**：跨服聊天的"本服接收"也依赖 `playerGuildCache`，而 `playerGuildCache` 在 proxy 模式下的可靠性完全取决于 Join 异步加载与 `SyncCache` 同步。任何缓存缺失/错误 → 收不到。而 `sendGuildChat` 发送端在 proxy 模式下**不走本地发送分支**（GuildService:660-670 `if (proxy) dispatch... else 本地遍历`），但接收端同一台服务器也会收到自己发出的包并由 `Chat` 分支处理——此时若本服玩家缓存齐全则能收到，若不全则丢。
  4. 此外 `dispatchGuildNotification` 的 `Chat` 数据打包：`data = (guildId:Int, name:String, msg:String, role:String, colored:String)`。接收端 `Chat` 读取：`readInt`(targetGuildId) → `readUTF`(senderName) → `readUTF`(msgContent) → `readUTF`(roleDisplayName) → `readUTF`(coloredMessage)。**顺序一致**，字段没问题。

**结论**：公会聊天不可用的根因与申请通知同源——**跨服接收端一律依赖 `playerGuildCache` 过滤，而该缓存在 proxy 模式下存在异步加载竞态与同步缺失**，导致大量消息被静默丢弃。单服模式因为走本地遍历且缓存通常由本服 Join 加载，问题较轻。

## 共同根因（架构级）

1. **`playerGuildCache` 是正确性基石但设计脆弱**：非线程安全 `MutableMap`；只在 Join 异步加载；跨服同步靠 `SyncCache`/`GuildTransfer` 等零散消息；任何缺失/延迟/竞态都让聊天、通知、仓库、权限全部失效。
2. **proxy 模式"只发包不本地发"**：`dispatchGuildNotification` / `syncPlayerCache` 等在 `isProxy` 时仅发包，依赖代理转发回本服再由 `PluginMessageListener` 处理。若代理转发字段顺序/子频道名不符（KaProxy Guilds 模块），全部失败——这是 `REFACTORING.md` 强调的红线，但**发送端数据打包与接收端解析虽在本插件内一致，KaProxy 侧是否真正转发/格式匹配未知**。
3. **借用 `onlinePlayers.firstOrNull()` 发包**：若服务器瞬时无在线玩家，包直接丢弃，且无重试/队列。
4. **DB 锁与内存锁两套状态不一致**：仓库相关最具破坏性。

## 建议修复方向（待与用户确认范围）

- **仓库**：`saveVault` 改为先确保行存在且 `last_editor` 已置为当前玩家（打开时即写）；或在 save 时用 `WHERE guild_id=? AND vault_index=? AND (last_editor=? OR lock_expire=0)` 兜底；`initSql` 初始化 `last_editor` 与 `items_data=''`；跨服内存锁与 DB 锁统一以 DB 为准，避免双状态。
- **缓存**：`playerGuildCache` 改为按需查库 + 短缓存（或每次关键操作强制查库，如 `sendGuildChat` 已做）；接收端判断不再强依赖缓存，改用查库或携带角色信息。
- **通知/聊天**：proxy 模式下发送端**同时本地处理 + 发包**（不要只发包），或接收端对 `NotifyRequest`/`Chat` 用查库兜底。
- **发包管道**：无在线玩家时不丢包（队列/延迟发），或确保代理本身可收。
- 优先修复仓库物品消失（数据丢失，最高危）与聊天/通知（功能不可用）。

## 待核对（未读文件）

- `KaProxy` Guilds 模块转发行为（外部，无法读）：需确认 `kaguilds:chat` 子频道是否按当前字段顺序/子频道名转发，以及 `OnlinePlayersList` 是否真的由代理主动广播。
- `GuildService` 其余约 1500 行（调用点如 1795 `checkAndNotifyRequests`、1035、1412 等）是否也依赖缓存竞态。
- `TaskManager` 跨服 BossBar 逻辑（738/828/1028 发包）是否正常。
