# 权限

KaGuilds 在 `plugin.yml` 中声明玩家命令权限和服务器管理员权限。权限插件中的显式拒绝、继承关系和通配符行为取决于所使用的权限管理插件。

## 推荐配置

### 开放全部玩家功能

保持默认设置即可：

```text
kaguilds.command.main = true
kaguilds.use = true
```

### 精细控制玩家命令

1. 保持 `kaguilds.command.main` 为允许，否则 Bukkit 会在进入 KaGuilds 命令处理器前拒绝整个主命令。
2. 显式拒绝 `kaguilds.use`。
3. 按需授予 `kaguilds.command.<子命令>`。

只拒绝某个细分权限无效，因为大多数命令在玩家拥有 `kaguilds.use` 时会直接放行。

## 玩家权限

| 权限节点 | 默认值 | 用途 |
| --- | --- | --- |
| `kaguilds.use` | `true` | 放行大多数玩家公会功能 |
| `kaguilds.command.main` | `true` | 允许进入 `/kaguilds`、`/kg`、`/guild` 主命令 |
| `kaguilds.command.help` | `true` | 声明的玩家帮助权限 |
| `kaguilds.command.create` | `false` | 创建公会 |
| `kaguilds.command.delete` | `false` | 解散公会 |
| `kaguilds.command.promote` | `false` | 提升成员职位 |
| `kaguilds.command.demote` | `false` | 降低成员职位 |
| `kaguilds.command.accept` | `false` | 批准入会申请 |
| `kaguilds.command.deny` | `false` | 拒绝入会申请 |
| `kaguilds.command.confirm` | `false` | 确认待处理操作 |
| `kaguilds.command.bank` | `false` | 使用公会金库 |
| `kaguilds.command.chat` | `false` | 使用公会聊天 |
| `kaguilds.command.menu` | `false` | 打开公会主菜单 |
| `kaguilds.command.motd` | `false` | 修改公会公告 |
| `kaguilds.command.rename` | `false` | 重命名公会 |
| `kaguilds.command.seticon` | `false` | 设置公会图标 |
| `kaguilds.command.settp` | `false` | 设置公会传送点 |
| `kaguilds.command.tp` | `false` | 使用公会传送 |
| `kaguilds.command.upgrade` | `false` | 升级公会 |
| `kaguilds.command.info` | `false` | 查看自己的公会信息 |
| `kaguilds.command.invite` | `false` | 邀请玩家加入公会 |
| `kaguilds.command.join` | `false` | 申请加入公会 |
| `kaguilds.command.kick` | `false` | 踢出公会成员 |
| `kaguilds.command.leave` | `false` | 退出公会 |
| `kaguilds.command.vault` | `false` | 打开公会仓库 |
| `kaguilds.command.transfer` | `false` | 转让会长 |
| `kaguilds.command.pvp` | `false` | 使用公会战命令 |
| `kaguilds.command.admin` | `false` | 声明的管理员命令入口权限 |
| `kaguilds.command.yes` | `false` | 接受公会邀请 |
| `kaguilds.command.no` | `false` | 拒绝公会邀请 |
| `kaguilds.command.requests` | `false` | 查看入会申请 |
| `kaguilds.command.buff` | `false` | 购买公会 Buff |

## 服务器管理员权限

| 权限节点 | 默认值 | 用途 |
| --- | --- | --- |
| `kaguilds.admin` | `op` | 放行全部服务器管理功能 |
| `kaguilds.admin.reload` | `op` | 重载插件配置 |
| `kaguilds.admin.help` | `op` | 在管理员帮助页显示帮助项 |
| `kaguilds.admin.rename` | `op` | 重命名任意公会 |
| `kaguilds.admin.delete` | `op` | 解散任意公会 |
| `kaguilds.admin.info` | `op` | 查看任意公会信息 |
| `kaguilds.admin.bank` | `op` | 查看或修改任意公会金库 |
| `kaguilds.admin.transfer` | `op` | 转让任意公会会长 |
| `kaguilds.admin.kick` | `op` | 踢出指定公会成员 |
| `kaguilds.admin.join` | `op` | 强制在线玩家加入公会 |
| `kaguilds.admin.vault` | `op` | 打开任意公会仓库 |
| `kaguilds.admin.unlockall` | `op` | 释放全部公会仓库锁 |
| `kaguilds.admin.setlevel` | `op` | 设置公会等级 |
| `kaguilds.admin.exp` | `op` | 修改公会经验 |
| `kaguilds.admin.arena` | `op` | 配置公会战竞技场 |
| `kaguilds.admin.open` | `op` | 打开任意已加载 GUI 菜单 |
| `kaguilds.admin.task` | `op` | 查看或修改公会任务进度 |
| `kaguilds.admin.contribution` | `op` | 修改成员贡献度 |
| `kaguilds.admin.release` | `op` | 释放内置菜单文件 |

## 当前实现注意事项

- `/kg help` 当前没有单独检查 `kaguilds.command.help`，但仍受主命令 `kaguilds.command.main` 控制。
- `/kg admin` 的具体动作检查 `kaguilds.admin` 或对应的 `kaguilds.admin.<动作>`；`kaguilds.command.admin` 当前不单独控制这些动作。
- `/kg admin help` 会根据发送者拥有的管理员动作权限过滤内容。即使没有 `kaguilds.admin.help`，拥有某个动作权限的发送者仍可看到对应帮助项。
- 权限只控制命令入口。会长、管理员、普通成员等公会职位限制仍由业务逻辑单独检查。

命令语法和职位限制见[玩家命令](player-commands.md)与[管理员命令](admin-commands.md)。
