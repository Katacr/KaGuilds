# 玩家命令

KaGuilds 的主命令为 `/kaguilds`，可使用 `/kg` 或 `/guild` 作为别名。本文统一使用 `/kg`。

## 参数约定

- `<参数>`：必填参数。
- `[参数]`：可选参数。
- `#ID`：公会 ID，例如 `#12`。支持 ID 的命令也可能接受不带 `#` 的数字，但建议统一使用 `#ID`。
- 除 `/kg help`、`/kg reload` 和 `/kg admin` 外，命令只能由玩家执行。

## 权限说明

大多数玩家命令满足以下任一条件即可执行：

1. 玩家拥有 `kaguilds.use`；
2. 玩家拥有该命令对应的 `kaguilds.command.<子命令>`。

`kaguilds.use` 默认授予所有玩家，因此仅拒绝某个细分权限不会限制命令。需要精细控制时，应先拒绝 `kaguilds.use`，再按需授予细分权限。公会职位限制仍会在权限检查后继续生效。

## 命令速查

| 命令 | 功能 | 公会职位或状态 | 权限节点 |
| --- | --- | --- | --- |
| `/kg` | 打开默认公会菜单 | 无 | `kaguilds.command.main` |
| `/kg help [页码]` | 查看玩家帮助 | 无 | 当前仅受主命令权限控制 |
| `/kg create <名称>` | 创建公会 | 当前未加入公会 | `kaguilds.command.create` |
| `/kg join <名称\|#ID>` | 申请加入公会 | 当前未加入公会 | `kaguilds.command.join` |
| `/kg yes` | 接受待处理的公会邀请 | 当前未加入公会 | `kaguilds.command.yes` |
| `/kg no` | 拒绝待处理的公会邀请 | 无 | `kaguilds.command.no` |
| `/kg info` | 查看自己的公会信息 | 公会成员 | `kaguilds.command.info` |
| `/kg menu` | 打开公会菜单 | 无 | `kaguilds.command.menu` |
| `/kg invite <玩家>` | 邀请玩家 | 会长或管理员 | `kaguilds.command.invite` |
| `/kg requests` | 查看本公会入会申请 | 公会成员 | `kaguilds.command.requests` |
| `/kg accept <玩家>` | 批准入会申请 | 会长或管理员 | `kaguilds.command.accept` |
| `/kg deny <玩家>` | 拒绝入会申请 | 公会成员 | `kaguilds.command.deny` |
| `/kg kick <玩家>` | 踢出成员 | 会长或管理员 | `kaguilds.command.kick` |
| `/kg promote <玩家>` | 将成员提升为管理员 | 会长 | `kaguilds.command.promote` |
| `/kg demote <玩家>` | 将管理员降为成员 | 会长 | `kaguilds.command.demote` |
| `/kg transfer <玩家>` | 转让会长 | 会长 | `kaguilds.command.transfer` |
| `/kg leave` | 退出公会 | 非会长 | `kaguilds.command.leave` |
| `/kg delete` | 解散公会 | 会长 | `kaguilds.command.delete` |
| `/kg rename <名称>` | 重命名公会 | 会长 | `kaguilds.command.rename` |
| `/kg seticon` | 将主手物品设为公会图标 | 会长或管理员 | `kaguilds.command.seticon` |
| `/kg motd <内容>` | 修改公会公告 | 会长或管理员 | `kaguilds.command.motd` |
| `/kg settp` | 设置公会传送点 | 会长或管理员 | `kaguilds.command.settp` |
| `/kg tp` | 传送到公会传送点 | 公会成员 | `kaguilds.command.tp` |
| `/kg upgrade` | 按 `levels.yml` 升级公会 | 会长或管理员 | `kaguilds.command.upgrade` |
| `/kg bank add <金额>` | 向公会金库存款 | 公会成员 | `kaguilds.command.bank` |
| `/kg bank take <金额>` | 从公会金库取款 | 公会成员 | `kaguilds.command.bank` |
| `/kg bank log [页码]` | 查看公会金库日志 | 会长或管理员 | `kaguilds.command.bank` |
| `/kg buff <Buff键>` | 购买公会 Buff | 公会成员 | `kaguilds.command.buff` |
| `/kg vault [1-9]` | 打开公会仓库，默认第 1 个 | 公会成员 | `kaguilds.command.vault` |
| `/kg chat` | 切换持续公会聊天模式 | 公会成员 | `kaguilds.command.chat` |
| `/kg chat <消息>` | 发送一条公会消息 | 公会成员 | `kaguilds.command.chat` |
| `/kg pvp start <名称\|#ID>` | 向目标公会发起对战 | 会长或管理员 | `kaguilds.command.pvp` |
| `/kg pvp accept` | 接受收到的对战邀请 | 会长或管理员 | `kaguilds.command.pvp` |
| `/kg pvp ready` | 在准备阶段确认参战 | 对战成员 | `kaguilds.command.pvp` |
| `/kg pvp exit` | 退出当前对战 | 对战成员 | `kaguilds.command.pvp` |
| `/kg confirm` | 执行当前待确认操作 | 取决于原操作 | `kaguilds.command.confirm` |
| `/kg reload` | 重载插件配置 | 服务器管理员或控制台 | `kaguilds.admin` 或 `kaguilds.admin.reload` |
| `/kg admin help [页码]` | 查看服务器管理员命令 | 服务器管理员或控制台 | 见管理员权限表 |

## 需要确认的操作

下列玩家操作不会立即执行，必须随后运行 `/kg confirm`：

- 创建公会；
- 解散公会；
- 退出公会；
- 踢出成员；
- 转让会长；
- 重命名公会。

每名玩家同时只保留一个待确认操作，新操作会替换旧操作。当前实现没有确认超时机制，因此文档不承诺待确认操作会自动过期。

## 成员与职位限制

- 会长不能直接退出公会，应先转让会长或解散公会。
- 公会管理员不能踢出会长或其他管理员；会长可以踢出管理员和普通成员。
- `/kg promote` 和 `/kg demote` 只能由会长执行，且不能用于会长本人。
- `/kg requests` 和 `/kg deny` 当前没有额外的公会职位检查；能通过命令权限检查的公会成员即可执行。
- `/kg transfer` 的目标必须是该公会中的玩家。

## 经济、仓库与 Buff

- 金库存取金额必须是正整数，并依赖 Vault 及可用的经济插件。
- 存款不能超过当前公会等级在 `levels.yml` 中配置的上限。
- 取款可能消耗个人贡献度，具体比例由 `config.yml` 控制。
- 可用 Buff 键及价格由 `buffs.yml` 定义。
- 仓库编号范围为 `1` 到 `9`，玩家只能打开当前等级已解锁的仓库。

## 聊天与公会战

- `/kg chat` 可进入或退出持续公会聊天模式；`/kg chat <消息>` 不改变聊天模式。
- 玩家退出、被踢出或公会被解散后，插件会清理其本地公会聊天状态。
- `/kg pvp start` 的裸数字会被当作公会名称；按 ID 发起时必须使用 `#ID`。
- 公会战同一时间只能进行一场，并受人数、冷却、费用和竞技场配置限制。

## 服务器管理命令

`/kg reload` 和 `/kg admin ...` 属于服务器管理功能，不是公会管理员职位功能。完整语法见[管理员命令](admin-commands.md)。
