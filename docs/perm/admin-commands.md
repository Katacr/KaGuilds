# ⌨️ 管理员命令列表

KaGuilds 提供了完整的管理员命令系统，用于管理和控制公会的各项功能。

***

## 🔐 管理员命令概览

### 主命令

```bash
/kg admin <动作> [参数...]
```

**权限**: `kaguilds.admin`

**所有管理员操作均无需公会权限，可直接管理任意公会。**

***

## 📋 管理员命令速查表

| 命令 | 功能 | 权限 |
| --- | --- | --- |
| `/kg admin rename` | 重命名公会 | `kaguilds.admin.rename` |
| `/kg admin delete` | 解散公会 | `kaguilds.admin.delete` |
| `/kg admin info` | 查看公会信息 | `kaguilds.admin.info` |
| `/kg admin bank` | 管理公会金库 | `kaguilds.admin.bank` |
| `/kg admin transfer` | 转让公会 | `kaguilds.admin.transfer` |
| `/kg admin kick` | 踢出成员 | `kaguilds.admin.kick` |
| `/kg admin join` | 加入玩家 | `kaguilds.admin.join` |
| `/kg admin vault` | 访问仓库 | `kaguilds.admin.vault` |
| `/kg admin unlockall` | 解锁所有仓库 | `kaguilds.admin.unlockall` |
| `/kg admin setlevel` | 设置公会等级 | `kaguilds.admin.setlevel` |
| `/kg admin exp` | 管理公会经验 | `kaguilds.admin.exp` |
| `/kg admin arena` | 竞技场管理 | `kaguilds.admin.arena` |
| `/kg admin open` | 打开指定菜单 | `kaguilds.admin.open` |
| `/kg admin release` | 释放菜单文件 | `kaguilds.admin.release` |

***

## 📦 公会管理

### `/kg admin rename <公会ID> <新名称>`

重命名指定公会。

**权限**: `kaguilds.admin.rename`

**用法**:

```bash
/kg admin rename 1 新公会名称
```

**说明**:

* 无需公会权限
* 直接修改公会名称
* 新名称必须符合命名规则

***

### `/kg admin delete <公会ID>`

解散指定公会。

**权限**: `kaguilds.admin.delete`

**用法**:

```bash
/kg admin delete 1
/kg confirm    # 确认解散
```

**说明**:

* 无需公会权限
* 需要确认操作（`/kg confirm`）
* 解散后所有成员离开公会
* 公会数据会被删除

**注意**: 此操作不可逆！

***

### `/kg admin info <公会ID>`

查看指定公会信息。

**权限**: `kaguilds.admin.info`

**用法**:

```bash
/kg admin info 1
```

**说明**:

* 显示公会的完整详细信息
* 包括：名称、等级、成员列表、资金、公告等

***

### `/kg admin transfer <公会ID> <新会长>`

转让指定公会。

**权限**: `kaguilds.admin.transfer`

**用法**:

```bash
/kg admin transfer 1 新会长名称
/kg confirm    # 确认转让
```

**说明**:

* 无需公会权限
* 目标玩家可以是任意在线玩家
* 需要确认操作（`/kg confirm`）
* 转让后原会长变为成员

**注意**: 此操作不可逆！

***

## 👥 成员管理

### `/kg admin kick <公会ID> <玩家名称>`

从指定公会踢出成员。

**权限**: `kaguilds.admin.kick`

**用法**:

```bash
/kg admin kick 1 玩家名称
```

**说明**:

* 无需公会权限
* 被踢出的玩家会收到通知
* 可以踢出任意成员（包括会长）

***

### `/kg admin join <公会ID> <玩家名称>`

将玩家加入指定公会。

**权限**: `kaguilds.admin.join`

**用法**:

```bash
/kg admin join 1 玩家名称
```

**说明**:

* 无需公会权限
* 直接将玩家加入公会（无需申请/批准）
* 玩家必须在线

***

## 💰 经济管理

### `/kg admin bank <公会ID> <操作> [金额]`

管理指定公会金库。

**权限**: `kaguilds.admin.bank`

**用法**:

```bash
/kg admin bank 1 add 1000    # 添加 1000 金币
/kg admin bank 1 remove 500    # 移除 500 金币
/kg admin bank 1 set 500    # 设置为 500 金币
/kg admin bank 1 see    # 查看当前余额
/kg admin bank 1 log    # 查看银行日志
```

**说明**:

* 无需公会权限
* `add` - 向金库添加金币
* `remove` - 从金库移除金币
* `set` - 直接设置金库余额
* `see` - 查看当前金库余额
* `log` - 查看金库交易历史记录

***

### `/kg admin setlevel <公会ID> <等级>`

设置公会等级。

**权限**: `kaguilds.admin.setlevel`

**用法**:

```bash
/kg admin setlevel 1 5
```

**说明**:

* 无需公会权限
* 直接设置公会等级
* 等级解锁的功能会在设置后立即生效

***

### `/kg admin exp <公会ID> <操作> <数值>`

管理公会经验。

**权限**: `kaguilds.admin.exp`

**用法**:

```bash
/kg admin exp 1 add 100     # 增加 100 经验
/kg admin exp 1 set 500     # 设置为 500 经验
/kg admin exp 1 remove 50   # 减少 50 经验
```

**说明**:

* 无需公会权限
* `add` - 增加公会经验
* `set` - 直接设置公会经验值
* `remove` - 减少公会经验

***

## 📦 仓库管理

### `/kg admin vault <公会ID> <仓库编号>`

访问指定公会仓库。

**权限**: `kaguilds.admin.vault`

**用法**:

```bash
/kg admin vault 1 1
```

**说明**:

* 无需公会权限
* 可以访问任意公会的任意仓库
* 仓库编号必须在公会已解锁范围内

***

### `/kg admin unlockall`

解锁公会的所有仓库访问锁，仅用于测试或异常情况。

**权限**: `kaguilds.admin.unlockall`

**用法**:

```bash
/kg admin unlockall
```

**说明**:

* 无需公会权限
* 解锁所有公会的仓库占用锁
* 仅用于异常情况（如玩家掉线导致仓库被锁定）
* 正常使用时请勿随意执行此命令

***

## ⚔️ 竞技场管理

### `/kg admin arena <setpos/setspawn/setkit/info>`

竞技场管理。

**权限**: `kaguilds.admin.arena`

**用法**:

```bash
/kg admin arena setpos 1    # 设置竞技场第一个位置
/kg admin arena setpos 2    # 设置竞技场第二个位置
/kg admin arena setspawn red  # 设置红队出生点
/kg admin arena setspawn blue  # 设置蓝队出生点
/kg admin arena setkit red     # 设置红队预设装备包
/kg admin arena setkit blue    # 设置蓝队预设装备包
/kg admin arena info       # 查看竞技场信息
```

**说明**:

* 无需公会权限
* `setpos` - 设置竞技场范围（需要设置两个位置）
* `setspawn` - 设置队伍出生点（red/blue）
* `setkit` - 设置队伍预设装备包（red/blue）
* `info` - 显示当前竞技场配置信息

**竞技场设置步骤**:

1. 使用 `/kg admin arena setpos 1` 在竞技场第一个位置执行
2. 使用 `/kg admin arena setpos 2` 在竞技场第二个位置执行
3. 使用 `/kg admin arena setspawn red` 在红队出生点执行
4. 使用 `/kg admin arena setspawn blue` 在蓝队出生点执行
5. 使用 `/kg admin arena info` 查看配置

***

## 🖼️ 菜单管理

### `/kg admin open <菜单名称>`

打开指定菜单。

**权限**: `kaguilds.admin.open`

**用法**:

```bash
/kg admin open main_menu
/kg admin open guild_list
/kg admin open member_list
```

**说明**:

* 无需公会权限
* 可以打开任意菜单文件
* 菜单文件位于 `gui/` 目录下（不包括 `.yml` 后缀）
* 用于测试和调试菜单配置

***

### `/kg admin release <语言>`

释放插件内的指定语言菜单文件。

**权限**: `kaguilds.admin.release`

**用法**:

```bash
/kg admin release EN
/kg admin release CN
```

**说明**:

* 需要管理员权限
* 释放时会替换当前已有文件名的文件
* `EN` - 释放英文版菜单文件
* `CN` - 释放中文版菜单文件
* 用于恢复默认菜单配置

**使用场景**:

* 菜单文件损坏需要恢复
* 想要查看最新的默认菜单配置
* 需要基于默认配置进行自定义

**注意**: 执行此命令会覆盖现有同名文件，请提前备份！

***

## 🔍 使用建议

### 日常管理

对于日常公会管理，推荐使用以下命令：

```bash
/kg admin info <公会ID>           # 查看公会状态
/kg admin bank <公会ID> see      # 检查公会资金
/kg admin bank <公会ID> log      # 查看资金变动记录
```

### 紧急处理

遇到紧急情况时，可以使用：

```bash
/kg admin unlockall             # 解锁所有仓库占用
/kg admin delete <公会ID>        # 解散问题公会（需确认）
/kg admin transfer <公会ID> <玩家> # 紧急转让公会
```

### 调试测试

测试功能时可以使用：

```bash
/kg admin open <菜单名称>        # 打开测试菜单
/kg admin setlevel <公会ID> <等级> # 设置测试等级
/kg admin exp <公会ID> add <数值>  # 添加测试经验
```

### 注意事项

1. **确认操作**: 涉及解散、转让等危险操作需要使用 `/kg confirm` 确认
2. **备份重要数据**: 执行 `/kg admin release` 前请备份现有菜单文件
3. **谨慎使用**: 管理员命令权限很高，操作前请确认公会ID和参数
4. **查看日志**: 所有管理操作都会记录在日志中，可用于审计

***

## 📞 常见问题

### Q: 如何获取公会ID？

A: 使用 `/kg info` 查看公会信息，或直接使用 `/kg admin info <公会名称>`。

### Q: 可以强制踢出会长吗？

A: 可以，使用 `/kg admin kick <公会ID> <会长名称>`。

### Q: 玩家掉线导致仓库被锁怎么办？

A: 使用 `/kg admin unlockall` 解锁所有仓库占用。

### Q: 如何重置菜单配置？

A: 使用 `/kg admin release EN` 或 `/kg admin release CN` 释放默认菜单。

### Q: 管理员命令会记录在日志中吗？

A: 是的，所有管理操作都会记录在服务器日志和插件日志中。

***
