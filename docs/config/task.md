# 📆 任务系统:task.yml

`task.yml` 配置文件用于定义公会的任务系统。任务系统分为两种类型：**每日任务**（Daily Tasks）和**全局任务**（Global Tasks），为公会成员提供丰富的游戏目标。

***

## 📋 配置结构

```yaml
tasks:
  TaskKey:                # 任务唯一标识符
    display:               # 任务在 GUI 中显示的配置（可选）
      unfinished:           # 任务未完成的图标
        material: "PAPER"
        custom_data: 0
        item_model: "minecraft:book"
      finished:           # 任务已完成的图标
        material: "PAPER"
        custom_data: 0
        item_model: "minecraft:enchanted_book"
    name: "任务名称"            # 任务显示名称
    type: "global"              # 任务类型：global 或 daily
    event:                      # 事件触发配置
      type: "kill_mobs"          # 事件类型
      target: "zombie"          # 目标类型
      amount: 100               # 目标数量
    lore:                       # 任务描述
      - "§7描述: 任务详细说明"
      - "§7奖励: 奖励内容"
    actions:                    # 完成后的动作
      - "console: <command>"
      - "tell: <message>"
```
 
***

## 🔧 配置选项详解

### `TaskKey`（必需）

* **类型**: String
* **描述**: 任务的唯一标识符，用于内部引用
* **示例**: `global_1`, `daily_1`, `fishing_daily`
* **规则**:
  * 必须唯一
  * 建议使用小写字母、数字和下划线
  * 具有描述性，便于识别

### `display`（可选）

任务在 GUI 菜单中的显示配置。

* 支持版本兼容性：
    * 1.21.4+: 优先使用 `item_model`
    * 1.21.4-: 使用 `custom_data`
    * 如果都没有配置，则使用 `material`

#### `material`

* **类型**: String
* **描述**: 任务图标的物品材质

#### `item_model`（可选）

* **类型**: String
* **描述**: 自定义物品模型

#### `custom_data`（可选）

* **类型**: Integer
* **描述**: 自定义材质包数据

### `name`（必需）

* **类型**: String
* **描述**: 任务的显示名称，出现在 GUI 和消息中

### `type`（必需）

* **类型**: String
* **描述**: 任务类型
* **默认值**: `"global"`
* **可用选项**:
  * `"global"` - 全局任务，公会所有成员共同完成
  * `"daily"` - 每日任务，每个玩家独立完成
* **区别**:
  * **全局任务**:
    * 公会所有成员的进度累加
    * 进度用黄色 BossBar 显示
    * 在任务重置时间才会重置
  * **每日任务**:
    * 每个玩家独立完成
    * 完成后只奖励当前玩家
    * 进度用绿色 BossBar 显示
    * 每天任务重置时间重置

### `event`（必需）

任务触发事件配置。

#### `event.type`（必需）

* **类型**: String
* **描述**: 触发任务的事件类型
* **可用事件类型**:

| 事件类型          | 说明   | 示例        |
| ------------- | ---- | --------- |
| `login`       | 玩家登录 | -         |
| `kill_mobs`   | 击杀怪物 | 击杀指定类型的生物 |
| `break_block` | 破坏方块 | 挖掘指定方块    |
| `donate`      | 捐赠金币 | 向公会金库捐赠   |
| `chat`        | 发送消息 | 发送聊天消息    |
| `fishing`     | 钓鱼   | 钓到鱼       |
| `milk`        | 挤奶   | 挤牛奶       |
| `shear`       | 剪羊毛  | 剪羊毛       |
| `trade`       | 村民交易 | 与村民交易     |
| `eat_food`    | 食用食物 | 吃食物       |
| `bonemeal`    | 使用骨粉 | 用骨粉催化农作物  |
| `smelt`       | 熔炼   | 熔炼物品      |

#### `event.target`（必需）

* **类型**: String
* **描述**: 事件的目标对象
* **规则**:
  * `"*"` 表示任意目标或无需目标
  * 对于 `kill_mobs`：使用实体类型（如 `zombie`, `skeleton`）
  * 对于 `break_block`：使用方块类型（如 `stone`, `diamond_ore`）
  * 对于 `smelt`：使用物品类型（如 `iron_ingot`, `gold_ingot`）
  * 对于 `eat_food`：使用食物类型（如 `cooked_beef`, `bread`）

{% hint style="info" %}
**提示**: `target` 使用小写的 Bukkit 枚举名或物品 ID。
{% endhint %}

#### `event.amount`（必需）

* **类型**: Integer
* **描述**: 任务目标数量
* **规则**:
  * 必须大于 0
  * 对于 `donate` 事件，单位是金币数量

### `lore`（必需）

* **类型**: List
* **描述**: 任务的描述列表，在 GUI 中显示
* **规则**:
  * 支持颜色代码
  * 每行一个字符串
  * 建议包含任务描述和奖励说明

### `actions`（必需）

* **类型**: List
* **描述**: 任务完成后执行的动作列表
* **默认值**: 无
* **支持的变量**:
  * `{player}` - 玩家名称
  * `{guild_id}` - 公会 ID
  * `{guild_name}` - 公会名称
  * 支持所有 PlaceholderAPI 变量
* **可用动作类型**:

| 动作类型      | 格式                   | 说明         |
| --------- | -------------------- | ---------- |
| `console` | `console: <command>` | 以控制台身份执行命令 |
| `command` | `command: <command>` | 以玩家身份执行命令  |
| `tell`    | `tell: <message>`    | 发送消息给玩家    |

*   **示例**:

    ```yaml
    actions:
      - "console: money give {player} 100"        # 给玩家 100 金币
      - "console: kg admin exp {guild_id} add 80" # 给公会加 80 经验
      - "tell: §a恭喜完成任务！你获得了 100 金币"
      - "command: say 我完成了任务"                # 玩家说一句话
    ```

***

## 💡 配置示例

### 每日任务示例

#### 登录任务

```yaml
daily_login:
  display:
    material: "COMPASS"
  name: "§a每日任务: 上线"
  type: "daily"
  event:
    type: "login"
    target: "*"
    amount: 1
  lore:
    - "§7描述: 每日上线游玩服务器"
    - "§7奖励: §e10经验"
  actions:
    - "console: kg admin exp %kaguilds_id% add 10"
```

#### 钓鱼任务

```yaml
fishing_daily:
  display:
    material: "FISHING_ROD"
  name: "§a每日任务: 渔夫"
  type: "daily"
  event:
    type: "fishing"
    target: "*"
    amount: 10
  lore:
    - "§7描述: 每日钓鱼10次"
    - "§7奖励: §e20经验"
  actions:
    - "console: kg admin exp %kaguilds_id% add 20"
```

#### 食物任务

```yaml
eat_daily:
  display:
    material: "COOKED_BEEF"
  name: "§a每日任务: 美食家"
  type: "daily"
  event:
    type: "eat_food"
    target: "cooked_beef"
    amount: 5
  lore:
    - "§7描述: 每日食用牛排5次"
    - "§7奖励: §e10经验"
  actions:
    - "console: kg admin exp %kaguilds_id% add 10"
```

### 全局任务示例

#### 击杀怪物任务

```yaml
global_zombie:
  display:
    material: "IRON_SWORD"
  name: "§a累计任务: 公会战斗"
  type: "global"
  event:
    type: "kill_mobs"
    target: "zombie"
    amount: 100
  lore:
    - "§7描述: 公会全体成员累计击杀100个僵尸"
    - "§7奖励: §e80经验 §7100金币"
  actions:
    - "console: money give {player} 100"
    - "console: kg admin exp %kaguilds_id% add 80"
```

#### 熔炼任务

```yaml
smelt_iron_global:
  display:
    material: "FURNACE"
  name: "§a累计任务: 铁匠"
  type: "global"
  event:
    type: "smelt"
    target: "iron_ingot"
    amount: 64
  lore:
    - "§7描述: 公会成员累计熔炼64个铁锭"
    - "§7奖励: §e80经验"
  actions:
    - "console: kg admin exp %kaguilds_id% add 80"
```

#### 捐赠任务

```yaml
donate_global:
  display:
    material: "GOLD_INGOT"
  name: "§a累计任务: 慷慨之心"
  type: "global"
  event:
    type: "donate"
    target: "*"
    amount: 1000
  lore:
    - "§7描述: 公会成员累计捐赠1000金币"
    - "§7奖励: §e100经验"
  actions:
    - "console: kg admin exp %kaguilds_id% add 100"
```

***

### 任务重置

在 `config.yml` 中配置任务重置时间：

```yaml
task:
  reset_time: "00:00:00"  # 每天凌晨重置
```
