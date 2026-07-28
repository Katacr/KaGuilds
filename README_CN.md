# KaGuilds

[![许可证](https://img.shields.io/github/license/katacr/KaGuilds)](LICENSE)
[![版本](https://img.shields.io/badge/version-1.1.0-blue)](https://github.com/katacr/KaGuilds)
[![Minecraft](https://img.shields.io/badge/编译基线-Spigot%201.16.5-brightgreen)](https://www.spigotmc.org/)

[English](README.md)

KaGuilds 是一款面向 Spigot、Paper 和 Velocity 多服务器网络的公会插件，提供成员管理、经济、共享仓库、任务、公会战、可配置菜单及中英文语言支持。

## 功能

- 公会创建、入会申请、邀请、三级职位、会长转让和公会解散
- 公会金库、分页交易日志、个人贡献度、等级和可配置 Buff
- 每日个人任务和全局公会任务，并持久记录奖励领取资格
- 使用数据库租约锁保护的共享公会仓库
- 公会聊天、传送点、图标、公告和 PlaceholderAPI 集成
- 支持队伍装备、准备阶段、比赛计时、战绩和奖励命令的公会战竞技场
- YAML GUI 菜单及中英文语言文件
- 通过 KaProxy Guilds 模块同步跨服聊天、邀请、通知和缓存

## 兼容性

| 项目 | 当前要求 |
| --- | --- |
| Minecraft API | 以 Spigot API 1.16.5 为编译基线；更高版本需在目标服务端实际验证 |
| Java | 字节码目标为 Java 12；实际 Java 还必须满足服务器核心要求 |
| 服务端 | Spigot、Paper 或兼容分支 |
| 网络代理 | Velocity 和 KaProxy Guilds 模块 |
| 数据库 | 单服可用 SQLite 或 MySQL；多服必须使用共享 MySQL |
| 经济 | Vault 和兼容 Vault 的经济插件 |
| 可选依赖 | PlaceholderAPI |

Folia 兼容目前仍处于议案阶段，当前版本未启用 Folia 支持。

## 安装

1. 在后端服务器安装 Vault 和兼容的经济插件。
2. 将 KaGuilds JAR 放入后端服务器的 `plugins` 目录。
3. 启动一次服务器，等待生成 `plugins/KaGuilds` 和数据库表。
4. 停止服务器，修改生成的配置，再正常启动。
5. 需要 KaGuilds 占位符时安装 PlaceholderAPI。

不要使用 PlugMan 等工具热加载或热重载 KaGuilds。升级前应备份数据库和完整的 `plugins/KaGuilds` 目录。

## 多服务器部署

每台后端服务器必须：

- 运行相同版本的 KaGuilds；
- 连接同一个 MySQL 数据库；
- 设置 `proxy: true`；
- 使用唯一且稳定的 `server-id`。

Velocity 只安装 KaProxy，并启用 Guilds 模块和 `legacy-channel-enabled` 设置。后端服务器应只允许玩家通过 Velocity 接入。

完整步骤和验证清单见 [Velocity 配置](docs/home/velocity.md)。

## 命令

主命令为 `/kaguilds`，别名为 `/kg` 和 `/guild`。

```text
/kg help [页码]
/kg create <名称>
/kg join <名称|#ID>
/kg info
/kg menu
/kg chat [消息]
/kg bank <add|take|log> ...
/kg pvp <start|accept|ready|exit> ...
/kg admin help [页码]
```

创建、解散、退出、踢人、转让和改名等玩家操作需要再执行 `/kg confirm`。服务器管理员的删除和转让命令会立即执行。

完整说明见[玩家命令](docs/perm/player-commands.md)、[管理员命令](docs/perm/admin-commands.md)和[权限](docs/perm/permissions.md)。

## 配置

运行时文件位于 `plugins/KaGuilds/`：

| 路径 | 用途 |
| --- | --- |
| `config.yml` | 数据库、代理、经济、传送、任务显示和通用设置 |
| `levels.yml` | 等级要求、各类上限、利息、仓库和 Buff 解锁 |
| `buffs.yml` | Buff 效果、价格、等级、持续时间和显示名称 |
| `task.yml` | 每日任务、全局任务和奖励动作 |
| `lang/*.yml` | 玩家可见消息 |
| `gui/*.yml` | 菜单布局、显示、条件和动作 |
| `arena.yml` | 公会战区域、出生点和队伍装备 |

详细内容见[中文文档](docs/README.md)或[英文文档](docs-en/README.md)。

## 构建

项目使用 Gradle，需要可编译 Java 12 目标的 JDK：

```bash
bash ./gradlew shadowJar
```

Windows 使用：

```bat
gradlew.bat shadowJar
```

构建产物位于 `build/libs/`。

## 许可证

KaGuilds 使用 [GPL-3.0 许可证](LICENSE)。
