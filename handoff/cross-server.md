# 跨服通信

> 多服部署依赖 Velocity + KaProxy Guilds 模块。重构改动通信字段是红线。

## 部署前提

- 每后端同版本 KaGuilds，连同一 MySQL，`proxy: true`，唯一稳定 `server-id`。
- Velocity 仅装 KaProxy，启用 Guilds 模块 + `legacy-channel-enabled`。
- 后端仅允许经 Velocity 接入。

## 通道约定(不可随意改)

- 核心通道：`kaguilds:chat`。
- 代理侧透明转发，不解析业务字段；Bukkit 侧必须保持子频道名与 `DataOutputStream` 字段顺序。
- 涉及子频道：`VaultSync`、`SyncCache`、`CrossInvite`、`BuffSync` 等。
- 代理主动广播 `OnlinePlayersList`，字段顺序固定：
  1. `writeUTF("OnlinePlayersList")`
  2. `writeInt(playerCount)`
  3. 循环：`writeUTF(playerName)`、`writeUTF(serverId)`

## 重构红线

- 改动仓库锁 / 邀请 / 任务 / 聊天 / 在线缓存，必须同步检查 `PluginMessageListener.kt`(539 行)与代理转发行为。
- 不能改变子频道名或字段顺序，否则代理转发破坏、跨服状态不一致。
- 跨服同步场景：`plugin.dbManager` 迁移到 repository 时，勿遗漏跨服消息读写路径。

## 涉及文件

- `listener/PluginMessageListener.kt`(539)：插件消息收发核心。
- `KaProxy`(外部，`/home/plugins/KaProxy`，Guilds 模块)：代理侧转发。
- 跨服同步触发点：`GuildService`、`VaultService`、`TaskManager`、成员缓存清理等。
