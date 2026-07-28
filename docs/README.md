---
description: KaGuilds 使用与配置文档
---

# KaGuilds

KaGuilds 是面向 Spigot、Paper 及多服务器网络的公会插件。它提供公会成员管理、经济、任务、共享仓库、PvP、菜单和 PlaceholderAPI 集成。

## 主要功能

- 公会创建、申请、邀请、职位调整、转让与解散
- 公会银行、交易日志、贡献度、等级和 Buff
- 每日个人任务与全局公会任务
- 带租约锁的共享公会仓库
- 可配置的公会聊天、传送点和 PvP 竞技场
- YAML 菜单、中文和英文语言文件、PlaceholderAPI 变量
- 通过 KaProxy Guilds 模块实现多服务器消息同步

## 部署模式

| 模式 | 数据库 | 代理设置 | 适用场景 |
| --- | --- | --- | --- |
| 单服务器 | SQLite 或 MySQL | `proxy: false` | 独立 Spigot/Paper 服务器 |
| 多服务器 | 共享 MySQL | `proxy: true` | Velocity 下的多个后端服务器 |

多服务器中的所有后端必须连接同一个 MySQL 数据库，并使用不同的 `server-id`。详细步骤参见 [Velocity 配置](home/velocity.md)。

## 兼容性

- 插件字节码目标为 Java 12，实际 Java 版本还必须满足服务器核心要求。
- 当前以 Spigot API 1.16.5 为编译基线；部署到更高版本前应在目标服务端测试。
- Folia 兼容仍处于议案阶段，当前版本不要部署到 Folia。
- Vault 和一个经济实现是经济功能的必要前置；PlaceholderAPI 为可选前置。

## 文档入口

- [快速开始](home/start.md)
- [配置文件](config/README.md)
- [指令和权限](perm/README.md)
- [自定义菜单](menu/README.md)
- [PlaceholderAPI](PlaceholderAPI.md)

{% hint style="warning" %}
生产环境升级前请备份数据库和 `plugins/KaGuilds`。不要使用 PlugMan 等插件加载器热加载 KaGuilds。
{% endhint %}

## 项目与反馈

- [GitHub 仓库](https://github.com/Katacr/KaGuilds/)
- [问题反馈](https://github.com/Katacr/KaGuilds/issues)
- [GPL-3.0 许可证](https://www.gnu.org/licenses/gpl-3.0.html)
