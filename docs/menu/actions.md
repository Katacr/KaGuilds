# 🤖 动作

`actions` 节点用于定义菜单按钮点击时执行的操作，支持多种动作类型、点击类型、条件判断和延迟执行。

## 📋 配置结构

```yaml
buttons:
  X:
    display:
      #...
    actions:
      left:
        - "command: say hello"
        - "close"
      right:
        - "open: main_menu"
      shift_left:
        - "console: kg bank add 100"
      drop:
        - "tell: 你按了 Q 键"
```

## 🔧 点击类型

菜单支持 4 种点击类型，每种类型可以配置不同的动作列表：

| 点击类型       | 键名           | 说明              |
| ---------- | ------------ | --------------- |
| 左键点击       | `left`       | 鼠标左键点击按钮        |
| 右键点击       | `right`      | 鼠标右键点击按钮        |
| Shift + 左键 | `shift_left` | 按住 Shift + 左键点击 |
| 丢弃键 (Q)    | `drop`       | 按 Q 键点击         |

**示例:**

```yaml
actions:
  left:
    - "command: /kg info"      # 左键执行

  right:
    - "open: members_menu"     # 右键执行

  shift_left:
    - "console: kg promote {members_name}"  # Shift+左键执行

  drop:
    - "tell: 你按了 Q 键"     # 按 Q 键执行
```

***

## 🎯 动作类型

### command - 玩家指令

让执行点击的玩家执行一条命令。

**格式:** `command: <指令>`

**示例:**

```yaml
actions:
  left:
    - "command: kg info"
    - "command: kg bank add 100"
    - "command: msg %player_name% Hello World"
```

**注意事项:**

* 命令前无需包含 `/` 符号
* 玩家必须有执行该命令的权限
* 支持变量替换

***

### console - 控制台指令

从控制台执行一条命令（以 OP 权限执行）。

**格式:** `console: <指令>`

**示例:**

```yaml
actions:
  left:
    - "console: give %player_name% diamond 64"
```

**注意事项:**

* 从控制台执行，玩家无需权限
* 命令必须以 `/` 开头
* 支持变量替换

***

### tell - 聊天消息

向玩家发送一条聊天消息。

**格式:** `tell: <消息>`

**示例:**

```yaml
actions:
  left:
    - "tell: &a操作成功！"
    - "tell: &c操作失败，请联系管理员"
    - "tell: &7当前余额: &f{balance}"
```

**注意事项:**

* 支持颜色代码
* 支持变量替换

***

### actionbar - 动作栏消息

向玩家发送一条动作栏消息（屏幕底部的消息）。

**格式:** `actionbar: <消息>`

**示例:**

```yaml
actions:
  left:
    - "actionbar: &a操作成功！"
    - "actionbar: &7余额: &f{balance}"
    - "actionbar: &e正在处理..."
```

**注意事项:**

* 消息显示在屏幕底部
* 持续时间较短（约 3 秒）
* 支持颜色代码和变量替换

***

### title - 标题消息

向玩家发送标题和副标题。

**格式:** `title: title=标题;subtitle=副标题;in=淡入;keep=停留;out=淡出`

**参数说明:**

| 参数         | 说明    | 单位   | 默认值 |
| ---------- | ----- | ---- | --- |
| `title`    | 标题文本  | -    | 空   |
| `subtitle` | 副标题文本 | -    | 空   |
| `in`       | 淡入时间  | tick | 0   |
| `keep`     | 停留时间  | tick | 60  |
| `out`      | 淡出时间  | tick | 20  |

**示例:**

```yaml
actions:
  left:
    - "title: title=&a操作成功;subtitle=&7已完成"
    - "title: title=&6公会升级;subtitle=&7当前等级: {level};in=10;keep=80;out=20"
```

**注意事项:**

* 参数用分号 `;` 分隔
* 支持颜色代码和变量替换

***

### sound - 播放声音

在玩家位置播放一个声音。

**格式:** `sound: <声音名称>`

**示例:**

```yaml
actions:
  left:
    - "sound: entity.experience_orb.pickup"
    - "sound: entity.player.levelup"
    - "sound: block.note_block.pling"
```

**注意事项:**

* 声音名称中的 `.` 会被自动转换为 `_`
* 使用 Minecraft 原版声音名称
* 音量和音高固定为 1.0

***

### open - 打开菜单

为玩家打开另一个菜单。

**格式:** `open: <菜单名称>`

**示例:**

```yaml
actions:
  left:
    - "open: main_menu"
    - "open: members_menu"
    - "open: buffs_menu"
```

**注意事项:**

* 菜单文件必须存在于 `gui/` 目录
* 文件名不需要包含 `.yml` 扩展名
* 当前菜单会自动关闭

***

### close - 关闭菜单

关闭当前打开的菜单。

**格式:** `close`

**示例:**

```yaml
actions:
  left:
    - "close"
    - "tell: 菜单已关闭"
```

**注意事项:**

* 不需要参数
* 通常配合其他动作使用

***

### update - 刷新菜单

重新加载并刷新当前菜单内容。

**格式:** `update`

**示例:**

```yaml
actions:
  left:
    - "update"
```

**注意事项:**

* 不需要参数
* 会重新构建所有按钮
* 用于更新动态内容

***

### PAGE\_NEXT - 下一页

翻到菜单的下一页。

**格式:** `PAGE_NEXT`

**示例:**

```yaml
actions:
  left:
    - "PAGE_NEXT"
    - "sound: block.note_block.pling"
```

**注意事项:**

* 只适用于分页菜单（成员列表、公会列表等）
* 如果已经是最后一页则无效果

***

### PAGE\_PREV - 上一页

翻到菜单的上一页。

**格式:** `PAGE_PREV`

**示例:**

```yaml
actions:
  left:
    - "PAGE_PREV"
    - "sound: block.note_block.pling"
```

**注意事项:**

* 只适用于分页菜单（成员列表、公会列表等）
* 如果已经是第一页则无效果

***

### catcher - 聊天输入捕获

捕获玩家输入的聊天消息，并执行指定的命令。

**格式:** `catcher: <类型>`

**支持的捕获类型:**

| 类型             | 用途       | 说明                          |
| -------------- | -------- | --------------------------- |
| `bank_add`     | 给公会银行捐赠  | 捕获输入并执行 `kg bank add <输入>`  |
| `bank_take`    | 取出公会银行金额 | 捕获输入并执行 `kg bank take <输入>` |
| `guild_rename` | 公会重命名    | 捕获输入并执行 `kg rename <输入>`    |
| `guild_create` | 创建公会     | 捕获输入并执行 `kg create <输入>`    |
| `edit_motd`    | 修改公会公告   | 捕获输入并执行 `kg motd <输入>`      |

**示例:**

```yaml
actions:
  left:
    - "catcher: bank_add"
    - "tell: &7请在聊天中输入金额，输入 cancel 取消"
```

**玩家输入示例:**

```
玩家在聊天中输入: 100
=> 自动执行: /kg bank add 100
```

**注意事项:**

* 输入 `cancel` 可以取消捕获
* 菜单会自动关闭
* 优先级高于公会聊天

***

### hovertext - 可点击文本

发送带有悬停和点击功能的聊天消息。

**格式:** `hovertext: <text='显示文字';hover='悬停文字';command='指令';newline='false'>`

**参数说明:**

| 参数        | 说明                | 必需 |
| --------- | ----------------- | -- |
| `text`    | 显示的文字             | 是  |
| `hover`   | 悬停时显示的文字          | 否  |
| `command` | 点击时执行的指令          | 否  |
| `newline` | 是否换行 (true/false) | 否  |

**示例:**

```yaml
actions:
  left:
    - "hovertext: 你好，请 <text=`&a[点击这里]`;hover=`&7点击执行操作`;command=`/kg info`;newline=`false`> 来执行操作。"
```

**注意事项:**

* 使用反引号 `` ` `` 包裹参数值
* 参数用分号 `;` 分隔
* 按钮文本用 `< >` 包裹
* 支持颜色代码和变量替换

***

### wait - 延迟执行

延迟执行后续的动作。

**格式:** `wait: <tick 数>`

**示例:**

```yaml
actions:
  left:
    - "tell: &a开始执行..."
    - "wait: 40"  # 延迟 2 秒
    - "tell: &b执行完成！"
```

**注意事项:**

* 单位为 tick（1 tick = 0.05 秒，20 tick = 1 秒）
* 只影响 `wait` 之后的动作
* 不会延迟其他同时执行的动作

***

## 🔍 条件判断

### 基本语法

使用条件判断可以根据玩家状态执行不同的动作。

**格式:**

```yaml
actions:
  left:
    - condition:
        condition: "条件表达式"
        actions:
          - "满足条件时执行的动作"
        deny:
          - "不满足条件时执行的动作"
```

### 条件表达式语法

支持比较运算符和逻辑运算符。

**比较运算符:**

| 运算符  | 说明   | 示例                       |
| ---- | ---- | ------------------------ |
| `==` | 等于   | `%player_level% == 10` |
| `!=` | 不等于  | `%player_level% != 10` |
| `>`  | 大于   | `%player_health% > 10` |
| `<`  | 小于   | `%player_health% < 10` |
| `>=` | 大于等于 | `%player_level% >= 10` |
| `<=` | 小于等于 | `%player_level% <= 10` |

**逻辑运算符:**

| 运算符  | 说明        | 优先级 |
| ---- | --------- | --- |
| `&&` | 逻辑与（并且）   | 高   |
| \`   |           | \`  |
| `()` | 括号（改变优先级） | 最高  |

**示例:**

```yaml
# 单个条件
actions:
  left:
    - condition: "%player_level% >= 10"
      actions:
        - "tell: &a你的等级足够！"
      deny:
        - "tell: &c你的等级不足！"
```

```yaml
# 多个条件 (AND)
actions:
  left:
    - condition: "%player_level% >= 10 && %player_level% < 20"
      actions:
        - "tell: &a你是中级玩家！"
      deny:
        - "tell: &c你不是中级玩家！"
```

```yaml
# 多个条件 (OR)
actions:
  left:
    - condition: "%player_level% < 10 || %player_level% >= 20"
      actions:
        - "tell: &a你是新手或高级玩家！"
```

```yaml
# 使用括号
actions:
  left:
    - condition: "(%player_level% >= 10 && %player_level% < 20) || %player_has_permission% == true"
      actions:
        - "tell: &a你有权限！"
```

***

## 📝 完整配置示例

### 示例 1: 基础按钮

```yaml
buttons:
  X:
    display:
      material: DIAMOND_SWORD
      name: "&a执行命令"
    actions:
      left:
        - "command: /kg info"
        - "sound: entity.experience_orb.pickup"
      right:
        - "open: main_menu"
```

### 示例 2: 条件判断

```yaml
buttons:
  X:
    display:
      material: BOOK
      name: "&a提升职位"
    actions:
      left:
        - condition: "{role_node} >= 2"
          actions:
            - "command: /kg promote {members_name}"
            - "tell: &a已提升 {members_name} 的职位"
          deny:
            - "tell: &c你没有权限提升成员职位！"
```

### 示例 3: 输入捕获

```yaml
buttons:
  X:
    display:
      material: GOLD_INGOT
      name: "&6存入金库"
    actions:
      left:
        - "catcher: bank_add"
        - "tell: &7请在聊天中输入金额"
        - "tell: &7输入 cancel 取消"
        - "close"
```

### 示例 4: 多种点击类型

```yaml
buttons:
  X:
    display:
      material: PLAYER_HEAD
      name: "&e成员操作"
    actions:
      left:
        - "tell: &7查看成员信息"
      right:
        - "open: member_info_menu"
      shift_left:
        - "command: /kg promote {members_name}"
        - "tell: &a已提升职位"
      drop:
        - "tell: &c你按了 Q 键"
```

### 示例 5: 延迟执行

```yaml
buttons:
  X:
    display:
      material: BOOK
      name: "&a延迟操作"
    actions:
      left:
        - "tell: &a操作开始..."
        - "wait: 40"
        - "tell: &b第一步完成..."
        - "wait: 40"
        - "tell: &e第二步完成..."
        - "wait: 40"
        - "tell: &c所有操作完成！"
```

***
