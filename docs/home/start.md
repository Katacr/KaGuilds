# 快速开始

本页说明如何在单服务器上安装 KaGuilds。多服务器部署还需要完成 [Velocity 配置](velocity.md)。

## 系统要求

| 项目 | 要求 |
| --- | --- |
| Minecraft | 以 Spigot API 1.16.5 为编译基线；更高版本需在目标核心验证 |
| Java | 最低 Java 12，同时必须满足服务器核心要求 |
| 服务端 | Spigot、Paper 或兼容分支；当前不支持 Folia |
| 数据库 | 单服可用 SQLite 或 MySQL；多服必须使用共享 MySQL |
| 经济 | Vault 和一个 Vault 兼容的经济插件 |
| 可选前置 | PlaceholderAPI |

## 安装

1. 从 [GitHub Releases](https://github.com/Katacr/KaGuilds/releases) 下载 KaGuilds。
2. 安装 Vault 和一个经济插件，例如 EssentialsX Economy。
3. 如需占位符，再安装 PlaceholderAPI。
4. 将插件 JAR 放入服务器的 `plugins` 目录。
5. 完整启动服务器，等待生成 `plugins/KaGuilds` 和数据库表。
6. 停止服务器后修改配置，再重新启动。

单服务器首次使用可保留：

```yaml
proxy: false

database:
  type: "SQLite"
```

## 安装验证

启动后依次检查：

1. 控制台没有 KaGuilds 数据库或依赖加载错误。
2. `/plugins` 中 KaGuilds、Vault 和经济插件均已启用。
3. 玩家执行 `/kg help` 能看到帮助页。
4. 创建测试公会并验证银行、菜单和语言文件。
5. 使用 MySQL 时，确认数据库账户具有建表、查询、插入、更新和删除权限。

{% hint style="warning" %}
不要使用 PlugMan 等插件加载器加载、卸载或重载 KaGuilds。升级前备份数据库和整个 `plugins/KaGuilds` 目录。
{% endhint %}
