# 管理员命令

服务器管理员命令使用 `/kg admin <动作> [参数...]`。本文中的“管理员”指服务器权限管理员，不是公会内的管理员职位。

## 参数与执行主体

- 文档统一使用 `#ID` 表示公会 ID，例如 `#12`；当前实现也接受不带 `#` 的数字。
- 除仓库、菜单和竞技场命令外，管理员命令可由玩家或控制台执行。
- 拥有 `kaguilds.admin` 可执行全部管理员命令，也可仅授予 `kaguilds.admin.<动作>`。
- 管理员删除、转让等操作会直接执行，不使用 `/kg confirm`。

## 命令速查

| 命令 | 功能 | 执行者 | 权限节点 |
| --- | --- | --- | --- |
| `/kg admin help [页码]` | 查看有权使用的管理员命令 | 玩家或控制台 | `kaguilds.admin.help` 或任一可见动作权限 |
| `/kg admin info #ID` | 查看公会详情 | 玩家或控制台 | `kaguilds.admin.info` |
| `/kg admin rename #ID <名称>` | 重命名公会 | 玩家或控制台 | `kaguilds.admin.rename` |
| `/kg admin delete #ID` | 立即解散公会 | 玩家或控制台 | `kaguilds.admin.delete` |
| `/kg admin transfer #ID <玩家>` | 转让公会会长 | 玩家或控制台 | `kaguilds.admin.transfer` |
| `/kg admin kick #ID <玩家>` | 踢出指定成员 | 玩家或控制台 | `kaguilds.admin.kick` |
| `/kg admin join #ID <玩家>` | 强制加入在线玩家 | 玩家或控制台 | `kaguilds.admin.join` |
| `/kg admin bank #ID see` | 查看金库余额 | 玩家或控制台 | `kaguilds.admin.bank` |
| `/kg admin bank #ID log [页码]` | 查看金库日志 | 玩家或控制台 | `kaguilds.admin.bank` |
| `/kg admin bank #ID <add\|remove\|set> <金额> [-s]` | 修改金库余额 | 玩家或控制台 | `kaguilds.admin.bank` |
| `/kg admin setlevel #ID <等级>` | 设置公会等级 | 玩家或控制台 | `kaguilds.admin.setlevel` |
| `/kg admin exp #ID <add\|remove\|set> <数量> [-s]` | 修改公会经验 | 玩家或控制台 | `kaguilds.admin.exp` |
| `/kg admin vault #ID [1-9]` | 打开指定公会仓库 | 仅玩家 | `kaguilds.admin.vault` |
| `/kg admin unlockall` | 强制释放全部仓库锁 | 玩家或控制台 | `kaguilds.admin.unlockall` |
| `/kg admin task #ID <任务键> see` | 查看任务定义和进度 | 玩家或控制台 | `kaguilds.admin.task` |
| `/kg admin task #ID <任务键> reset` | 重置任务进度 | 玩家或控制台 | `kaguilds.admin.task` |
| `/kg admin task #ID <任务键> add <数量>` | 增加任务进度 | 玩家或控制台 | `kaguilds.admin.task` |
| `/kg admin contribution #ID <玩家\|-all> set <数量>` | 设置贡献度 | 玩家或控制台 | `kaguilds.admin.contribution` |
| `/kg admin contribution #ID <玩家\|-all> add <数量>` | 增加贡献度 | 玩家或控制台 | `kaguilds.admin.contribution` |
| `/kg admin contribution #ID <玩家\|-all> clear` | 清零贡献度 | 玩家或控制台 | `kaguilds.admin.contribution` |
| `/kg admin open <菜单>` | 打开指定 GUI 菜单 | 仅玩家 | `kaguilds.admin.open` |
| `/kg admin arena setpos <1\|2>` | 设置竞技场边界点 | 仅玩家 | `kaguilds.admin.arena` |
| `/kg admin arena setspawn <red\|blue>` | 设置队伍出生点 | 仅玩家 | `kaguilds.admin.arena` |
| `/kg admin arena setkit <red\|blue>` | 保存当前背包为队伍装备 | 仅玩家 | `kaguilds.admin.arena` |
| `/kg admin arena info` | 查看竞技场配置状态 | 仅玩家 | `kaguilds.admin.arena` |
| `/kg admin release <CN\|EN>` | 释放指定语言的内置菜单 | 玩家或控制台 | `kaguilds.admin.release` |

## 公会与成员管理

- `rename` 仍会检查 `config.yml` 中的名称长度和正则规则。
- `delete` 会立即删除公会及其关联关系，执行前应确认 ID 并做好数据库备份。
- `transfer` 的目标必须能在玩家数据中找到，并且应属于目标公会。
- `kick` 不能踢出公会会长。
- `join` 只接受当前在线玩家，并会绕过申请和审批流程；目标已在其他公会时会返回错误。

## 金库与经验

```bash
/kg admin bank #1 add 100
/kg admin bank #1 add 100 -s
/kg admin exp #1 set 5000
/kg admin exp #1 set 5000 -s
```

- `add` 增加数值，`remove` 减少数值，`set` 直接设为目标值。
- `bank` 接受小数，`exp` 接受整数。
- `-s` 只隐藏成功消息；参数错误、公会不存在或数据库失败仍会输出。
- 管理员金库修改不使用玩家等级上限和贡献度规则，应谨慎操作。

## 仓库管理

- `/kg admin vault #ID` 默认打开第 1 个仓库。
- 仓库编号有效范围为 `1` 到 `9`。
- 管理员打开仓库不检查目标公会等级是否已解锁该编号。
- `/kg admin unlockall` 用于异常断线后释放残留锁，不会解锁公会等级功能。

## 任务与贡献度

- `<任务键>` 必须与 `task.yml` 中的任务键一致。
- `task see` 会显示任务定义、目标、奖励和已有进度。
- `task reset` 对全局任务重置公会记录，对每日任务重置该公会已有的玩家记录。
- 当前 `task add` 不能指定玩家，无法可靠管理个人每日任务进度，建议仅用于全局任务。
- 贡献度命令的数值必须是非负整数；`-all` 会操作该公会所有成员。

## 竞技场与菜单

- `setpos` 和 `setspawn` 使用执行玩家当前所在位置。
- `setkit` 保存执行玩家当前背包内容作为指定队伍装备。
- `open` 的菜单名必须与已加载菜单文件名匹配。
- `release CN` 或 `release EN` 会释放插件内置菜单文件；覆盖自定义文件前应先备份。

## 重载命令

插件重载不在 `/kg admin` 下，使用：

```bash
/kg reload
```

权限为 `kaguilds.admin` 或 `kaguilds.admin.reload`。生产环境更新插件 JAR、数据库结构或依赖时，应完整重启服务器，不要使用 PlugMan 等热加载工具。
