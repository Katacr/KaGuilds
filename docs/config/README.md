# 配置文件

KaGuilds 的运行时文件位于 `plugins/KaGuilds/`。

| 路径 | 用途 | 文档 |
| --- | --- | --- |
| `config.yml` | 数据库、代理、经济、传送、任务显示和菜单默认值 | [主配置](config.md) |
| `levels.yml` | 等级、成员上限、银行上限、利息、仓库和 Buff 解锁 | [等级配置](levels.md) |
| `buffs.yml` | Buff 类型、价格、等级、持续时间和名称 | [Buff 配置](buffs.md) |
| `task.yml` | 每日任务、全局任务、事件和奖励动作 | [任务配置](task.md) |
| `lang/zh_CN.yml` | 中文消息 | - |
| `lang/en_US.yml` | 英文消息 | - |
| `gui/*.yml` | 菜单布局、图标、条件和动作 | [自定义菜单](../menu/README.md) |
| `arena.yml` | PvP 区域、出生点和套装 | - |
| `storage.db` | SQLite 数据库，仅单服务器模式 | - |

## 修改建议

1. 修改前备份原文件。
2. 保持 YAML 缩进，使用空格而不是制表符。
3. 多服务器环境在所有后端同步菜单、语言和功能配置。
4. 数据库类型、连接参数或代理模式变更后完整重启服务器。
5. 普通配置可以使用 `/kg reload`，但不要用 PlugMan 重载整个插件。

{% hint style="warning" %}
`/kg admin release <CN|EN>` 用于重新释放内置菜单。执行前请备份 `gui`，避免覆盖自定义文件。
{% endhint %}
