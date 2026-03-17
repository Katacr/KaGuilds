# ⌨️ 指令列表

KaGuilds 提供了丰富的命令系统，支持所有公会功能的操作。所有命令都以 `/kg` 或 `/guild` 为前缀。

***

## 📋 命令概览

### 主命令

```bash
/kg                    # 打开公会菜单（如果有公会）或创建菜单
/kg help [页码]        # 查看帮助菜单
```

### 快捷访问

| 命令       | 功能   | 权限                      |
| -------- | ---- | ----------------------- |
| `/kg`    | 打开菜单 | `kaguilds.command.main` |
| `/guild` | 打开菜单 | `kaguilds.command.main` |

***

## 🏠 基础命令

### `/kg help [页码]`

查看插件帮助菜单。

**权限**: `kaguilds.command.help`

**用法**:

```bash
/kg help        # 查看第1页帮助
/kg help 2      # 查看第2页帮助
```

**说明**:

* 每页显示 10 个命令
* 按功能分类显示
* 包含命令简短描述

**示例**:

```
------- KaGuilds 帮助菜单 -------
/kg create <名称>     创建新公会
/kg join <公会>       申请加入公会
/kg info              查看公会信息
...
```

***

## 👥 公会管理命令

### `/kg create <公会名称>`

创建一个新的公会。

**权限**: `kaguilds.command.create`

**用法**:

```bash
/kg create 我的世界公会
```

**说明**:

* 需要支付创建费用（在 `config.yml` 中配置）
* 公会名称必须符合命名规则（长度、字符等）
* 创建后玩家自动成为公会会长

***

### `/kg delete`

解散当前公会。

**权限**: `kaguilds.command.delete`

**用法**:

```bash
/kg delete
/kg confirm    # 确认解散
```

**说明**:

* 需要会长权限
* 需要确认操作（`/kg confirm`）
* 解散后所有成员离开公会
* 公会数据会被删除

**注意**: 此操作不可逆！

***

### `/kg rename <新名称>`

重命名公会。

**权限**: `kaguilds.command.rename`

**用法**:

```bash
/kg rename 新公会名称
```

**说明**:

* 需要会长权限
* 需要支付费用（在 `config.yml` 中配置）
* 新名称必须符合命名规则

***

### `/kg info [公会名称]`

查看公会信息。

**权限**: `kaguilds.command.info`

**用法**:

```bash
/kg info              # 查看自己公会的信息
/kg info 强者公会     # 查看指定公会的信息
```

**说明**:

* 显示公会的详细信息
* 包括：名称、等级、成员数、资金等

**输出示例**:

```
公会信息: 我的公会
等级: Lv.5
成员: 12/20
资金: 15000.0
会长: Steve
公告: 欢迎来到我们的公会！
```

***

### `/kg motd <公告内容>`

设置公会公告。

**权限**: `kaguilds.command.motd`

**用法**:

```bash
/kg motd 欢迎加入我们的公会！
```

**说明**:

* 需要管理员或会长权限
* 支持颜色代码

***

### `/kg seticon`

设置公会图标。

**权限**: `kaguilds.command.seticon`

**用法**:

```bash
/kg seticon 
```

**说明**:

* 手持想要设置的物品
* 需要会长或管理员权限
* 需要支付费用（在 `config.yml` 中配置）

***

## 🤝 成员管理命令

### `/kg invite <玩家名称>`

邀请玩家加入公会。

**权限**: `kaguilds.command.invite`

**用法**:

```bash
/kg invite Steve
```

**说明**:

* 需要管理员或会长权限
* 邀请有效期为 60 秒
* 目标玩家可以使用 `/kg yes` 或点击提示接受邀请

***

### `/kg accept <玩家名称>`

批准玩家的入会申请。

**权限**: `kaguilds.command.accept`

**用法**:
 
```bash
/kg accept Steve
```

**说明**:

* 需要管理员或会长权限
* 目标玩家必须已提交入会申请
* 使用 `/kg requests` 查看待处理的申请

***

### `/kg deny <玩家名称>`

拒绝玩家的入会申请。

**权限**: `kaguilds.command.deny`

**用法**:

```bash
/kg deny Steve
```

**说明**:

* 需要管理员或会长权限
* 目标玩家会收到拒绝通知

***

### `/kg requests`

查看待处理的入会申请。

**权限**: `kaguilds.command.requests`

**用法**:

```bash
/kg requests
```

**说明**:

* 需要管理员或会长权限
* 显示所有待处理的入会申请列表

***

### `/kg join <公会名称或ID>`

申请加入公会。

**权限**: `kaguilds.command.join`

**用法**:

```bash
/kg join 强者公会
/kg join 公会ID
```

**说明**:

* 公会管理员会收到申请通知
* 可以使用 `/kg accept <玩家名称>` 批准或 `/kg deny <玩家名称>` 拒绝

***

### `/kg yes`

接受公会邀请。

**权限**: `kaguilds.command.yes`

**用法**:

```bash
/kg yes
```

**说明**:

* 接受公会管理员的入会邀请
* 邀请有效期为 60 秒

***

### `/kg no`

拒绝公会邀请。

**权限**: `kaguilds.command.no`

**用法**:

```bash
/kg no
```

**说明**:

* 拒绝公会管理员的入会邀请

***

### `/kg kick <玩家名称>`

踢出公会成员。

**权限**: `kaguilds.command.kick`

**用法**:

```bash
/kg kick Steve
```

**说明**:

* 需要管理员或会长权限
* 被踢出的玩家会收到通知

***

### `/kg promote <玩家名称>`

提升成员为管理员。

**权限**: `kaguilds.command.promote`

**用法**:

```bash
/kg promote Steve
```

**说明**:

* 需要会长权限
* 目标玩家必须是成员
* 只能提升为管理员

***

### `/kg demote <玩家名称>`

降级管理员为成员。

**权限**: `kaguilds.command.demote`

**用法**:

```bash
/kg demote Steve
```

**说明**:

* 需要会长权限
* 目标玩家必须是管理员
* 降级后变为成员

***

### `/kg transfer <玩家名称>`

转让公会所有权。

**权限**: `kaguilds.command.transfer`

**用法**:

```bash
/kg transfer Steve
/kg confirm    # 确认转让
```

**说明**:

* 需要会长权限
* 目标玩家必须是公会成员
* 需要确认操作（`/kg confirm`）
* 转让后原会长变为成员

**注意**: 此操作不可逆！

***

### `/kg leave`

退出公会。

**权限**: `kaguilds.command.leave`

**用法**:

```bash
/kg leave
/kg confirm    # 确认退出
```

**说明**:

* 需要确认操作（`/kg confirm`）

***

## 💰 经济系统命令

### `/kg bank [add/take/log] [金额]`

管理公会金库。

**权限**: `kaguilds.command.bank`

**用法**:

```bash
/kg bank add 1000     # 存入 1000 金币
/kg bank take 500     # 取出 500 金币
/kg bank log              # 查看交易日志
```

**说明**:

* `add` - 存入金币到公会金库
* `take` - 从公会金库取出金币
* `log` - 查看金库交易日志（仅管理员）
* 存取款会根据贡献度系统增加或减少贡献度

**贡献度计算**（config.yml）:

* 存款贡献度 = 存款金额 × `bank-deposit-ratio`
* 取款贡献度 = 取款金额 × `bank-withdraw-ratio`

***

### `/kg upgrade`

升级公会等级。

**权限**: `kaguilds.command.upgrade`

**用法**:

```bash
/kg upgrade
```

**说明**:

* 需要管理员或会长权限
* 需要足够的公会经验
* 升级后解锁新功能和提高属性上限

**升级效果**（请前往 `levels.yml` 配置）:

***

## 🎮 传送系统命令

### `/kg settp`

设置公会传送点。

**权限**: `kaguilds.command.settp`

**用法**:

```bash
/kg settp
```

**说明**:

* 需要会长或管理员权限
* 将当前位置设置为公会传送点
* 需要支付费用（在 `config.yml` 中配置）

***

### `/kg tp`

传送到公会传送点。

**权限**: `kaguilds.command.tp`

**用法**:

```bash
/kg tp
```

**说明**:

* 需要支付费用（在 `config.yml` 中配置）
* 开始传送期间禁止移动
* 某些世界可能禁止传送（在 `config.yml` 中配置）

***

## 🎁 Buff 系统命令

### `/kg buff <Buff名称>`

购买公会增益效果。

**权限**: `kaguilds.command.buff`

**用法**:

```bash
/kg buff Speed
/kg buff NightVision
```

**说明**:

* 需要公会金库余额充足
* Buff 购买后会分发给所有公会在线成员

***

## 📦 仓库系统命令

### `/kg vault <仓库编号>`

访问公会云仓库。

**权限**: `kaguilds.command.vault`

**用法**:

```bash
/kg vault 1     # 打开 1 号仓库
/kg vault 2     # 打开 2 号仓库
```

**说明**:

* 仓库需要在 `levels.yml` 中解锁
* 支持跨服同步
* 当有玩家在操作仓库时，其他玩家暂时无法使用

***

## 💬 聊天系统命令

### `/kg chat [消息]`

发送公会聊天消息。

**权限**: `kaguilds.command.chat`

**用法**:

```bash
/kg chat                              # 切换到公会聊天模式
/kg chat 大家好！                    # 发送单条消息
```

**说明**:

* /kg chat ：切换聊天模式（再次输入退出）
* /kg chat 内容 ：发送单条消息
* 进入公会聊天模式后，所有消息都会发送到公会频道

***

## ⚔️ PvP 系统命令

### `/kg pvp <start/accept/ready/exit>`

公会战相关命令。

**权限**: `kaguilds.command.pvp`

**用法**:

```bash
/kg pvp start 公会ID         # 向指定公会发起公会战
/kg pvp accept         # 接受公会战
/kg pvp ready          # 准备就绪
/kg pvp exit           # 退出公会战
```

**说明**:

* 需要管理员或会长权限
* 公会战有参与者人数限制（在 `config.yml` 中配置）
* 有冷却时间限制
* 战斗奖励均可配置（在 `config.yml` 中配置）

***

## 🖼️ 菜单系统命令

### `/kg menu`

打开公会主菜单。

**权限**: `kaguilds.command.menu`

**用法**:

```bash
/kg menu
```

**说明**:

* 打开图形化的公会管理界面
* 提供便捷的公会操作
* 支持自定义菜单布局（在 `gui/` 目录下配置）

***

## 🔐 管理员命令

### `/kg admin <动作> [参数...]`

管理员管理命令。

**权限**: `kaguilds.admin`

**用法**:

```yaml
/kg admin rename <公会ID> <新名称>      # 重命名公会
/kg admin delete <公会ID>              # 解散公会
/kg admin info <公会ID>               # 查看公会信息
/kg admin bank <公会ID> <add/remove/set/see/log> [金额]  # 管理公会金库
/kg admin transfer <公会ID> <新会长>   # 转让公会
/kg admin kick <公会ID> <玩家>        # 踢出成员
/kg admin join <公会ID> <玩家>         # 加入玩家
/kg admin vault <公会ID> <仓库编号>    # 访问仓库
/kg admin unlockall               # 解锁所有仓库占用锁
/kg admin setlevel <公会ID> <等级>    # 设置公会等级
/kg admin exp <公会ID> <add/remove/set> <数值>  # 管理公会经验
/kg admin arena <setpos/setspawn/setkit/info>  # 竞技场管理
/kg admin open <菜单名称>                # 打开指定菜单
```

**说明**:

* 需要管理员权限
* 可以管理任意公会
* 无需公会权限

***

### `/kg admin rename <公会ID> <新名称>`

重命名指定公会。

**权限**: `kaguilds.admin.rename`

**用法**:

```bash
/kg admin rename 公会ID 新公会名称
```

***

### `/kg admin delete <公会ID>`

解散指定公会。

**权限**: `kaguilds.admin.delete`

**用法**:

```bash
/kg admin delete 公会ID
/kg confirm    # 确认解散
```

***

### `/kg admin info <公会ID>`

查看指定公会信息。

**权限**: `kaguilds.admin.info`

**用法**:

```bash
/kg admin info 公会ID
```

***

### `/kg admin bank <公会ID> <操作> [金额]`

管理指定公会金库。

**权限**: `kaguilds.admin.bank`

**用法**:

```bash
/kg admin bank 公会ID add 1000    # 添加 1000 金币
/kg admin bank 公会ID remove 500    # 移除 500 金币
/kg admin bank 公会ID set 500    # 设置为 500 金币
/kg admin bank 公会ID see    # 查看当前余额
/kg admin bank 公会ID log    # 查看银行日志
```

***

### `/kg admin transfer <公会ID> <新会长>`

转让指定公会。

**权限**: `kaguilds.admin.transfer`

**用法**:

```bash
/kg admin transfer 公会ID 新会长名称
/kg confirm    # 确认转让
```

***

### `/kg admin kick <公会ID> <玩家名称>`

从指定公会踢出成员。

**权限**: `kaguilds.admin.kick`

**用法**:

```bash
/kg admin kick 公会ID 玩家名称
```

***

### `/kg admin join <公会ID> <玩家名称>`

将玩家加入指定公会。

**权限**: `kaguilds.admin.join`

**用法**:

```bash
/kg admin join 公会ID 玩家名称
```

***

### `/kg admin vault <公会ID> <仓库编号>`

访问指定公会仓库。

**权限**: `kaguilds.admin.vault`

**用法**:

```bash
/kg admin vault 公会ID 1
```

***

### `/kg admin unlockall`

解锁公会的所有仓库访问锁，仅用于测试或异常情况。

**权限**: `kaguilds.admin.unlockall`

**用法**:

```bash
/kg admin unlockall
```

***

### `/kg admin setlevel <公会ID> <等级>`

设置公会等级。

**权限**: `kaguilds.admin.setlevel`

**用法**:

```bash
/kg admin setlevel 公会ID 5
```

***

### `/kg admin exp <公会ID> <add/set/remove> <数值>`

管理公会经验。

**权限**: `kaguilds.admin.exp`

**用法**:

```bash
/kg admin exp 公会ID add 100     # 增加 100 经验
/kg admin exp 公会ID set 500     # 设置为 500 经验
/kg admin exp 公会ID remove 50   # 减少 50 经验
```

***

### `/kg admin arena <setpos/setspawn/setkit/info>`

竞技场管理。

**权限**: `kaguilds.admin.arena`

**用法**:

```bash
/kg admin arena setpos 1/2    # 设置竞技场范围
/kg admin arena setspawn red/blue  # 设置红蓝队伍出生点
/kg admin arena setkit red/blue     # 设置红蓝队伍预设装备包
/kg admin arena info       # 查看竞技场信息
```

***

### `/kg admin open <菜单名称>`

打开指定菜单。

**权限**: `kaguilds.admin.open`

**用法**:

```bash
/kg admin open main_menu
/kg admin open guild_list
```

***

## 🔧 系统命令

### `/kg reload`

重载插件配置。

**权限**: `kaguilds.admin.reload`

**用法**:

```bash
/kg reload
```

**说明**:

* 重新加载所有配置文件
* 包括 `config.yml`, `levels.yml`, `buffs.yml`, `task.yml` 等
* 无需重启服务器
* 支持控制台执行

***

### `/kg confirm`

确认危险操作。

**权限**: `kaguilds.command.confirm`

**用法**:

```bash
/kg confirm
```

**说明**:

* 用于确认危险操作（如解散公会、转让等）
* 操作有效期为 30 秒

***

## 📝 命令速查表

| 命令             | 功能      | 权限                 | 需要  |
| -------------- | ------- | ------------------ | --- |
| `/kg`          | 打开菜单    | `command.main`     | -   |
| `/kg help`     | 查看帮助    | `command.help`     | -   |
| `/kg create`   | 创建公会    | `command.create`   | -   |
| `/kg join`     | 申请加入    | `command.join`     | -   |
| `/kg yes`      | 接受邀请    | `command.yes`      | -   |
| `/kg no`       | 拒绝邀请    | `command.no`       | -   |
| `/kg info`     | 查看信息    | `command.info`     | -   |
| `/kg menu`     | 打开菜单    | `command.menu`     | -   |
| `/kg leave`    | 退出公会    | `command.leave`    | -   |
| `/kg delete`   | 解散公会    | `command.delete`   | 会长  |
| `/kg rename`   | 重命名     | `command.rename`   | 会长  |
| `/kg seticon`  | 设置图标    | `command.seticon`  | 会长  |
| `/kg motd`     | 设置公告    | `command.motd`     | 管理员 |
| `/kg invite`   | 邀请成员    | `command.invite`   | 管理员 |
| `/kg accept`   | 批准申请    | `command.accept`   | 管理员 |
| `/kg deny`     | 拒绝申请    | `command.deny`     | 管理员 |
| `/kg requests` | 查看申请    | `command.requests` | 管理员 |
| `/kg kick`     | 踢出成员    | `command.kick`     | 管理员 |
| `/kg promote`  | 提升成员    | `command.promote`  | 会长  |
| `/kg demote`   | 降级成员    | `command.demote`   | 会长  |
| `/kg transfer` | 转让公会    | `command.transfer` | 会长  |
| `/kg chat`     | 公会聊天    | `command.chat`     | -   |
| `/kg settp`    | 设置传送点   | `command.settp`    | 会长  |
| `/kg tp`       | 公会传送    | `command.tp`       | -   |
| `/kg bank`     | 金库管理    | `command.bank`     | -   |
| `/kg buff`     | 购买 Buff | `command.buff`     | 管理员 |
| `/kg vault`    | 访问仓库    | `command.vault`    | -   |
| `/kg upgrade`  | 升级公会    | `command.upgrade`  | 管理员 |
| `/kg pvp`      | 公会战     | `command.pvp`      | 管理员 |
| `/kg confirm`  | 确认操作    | `command.confirm`  | -   |
| `/kg reload`   | 重载配置    | `admin.reload`     | 管理员 |
| `/kg admin`    | 管理命令    | `admin`            | 管理员 |

***
