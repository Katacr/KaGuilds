# 🎲 图标

`display` 节点用于定义菜单中每个按钮（图标）的视觉外观，包括材质、名称、描述、数量、自定义模型等属性。

## 📋 配置结构

```yaml
buttons:
  X:
    display:
      material: DIAMOND_SWORD
      name: "&a我的剑"
      lore:
        - "&7一把锋利的剑"
        - "&7攻击力: 10"
      amount: 1
      custom_data: 1234
      item_model: "mynamespace:mykey"
      glow: false
    actions:
      left:
        - "command: say hello"
```

## 🔧 配置选项详解

### material - 物品材质

定义按钮图标的 Minecraft 物品类型。

**类型:** `String`

**格式:** Minecraft 材质名称（支持大写、小写或混合）

**示例:**

```yaml
display:
  material: DIAMOND_SWORD          # 钻石剑
```

***

### name - 显示名称

定义按钮图标的显示名称（鼠标悬停时显示）。

**类型:** `String`

**格式:** 支持颜色代码（使用 `&` 符号）和占位符变量

**示例:**

```yaml
display:
  name: "&6&l 公会设置"      
```

**占位符变量:**

支持插件内部变量和 PAPI 变量:

```yaml
# 插件内部变量
display:
  name: "&a {player} 的公会"           # 玩家名
```

```yaml
# PAPI 变量
display:
  name: "&a %player_name%"
```

***

### lore - 描述文本

定义按钮图标的描述文本（鼠标悬停时显示的多行文本）。

**类型:** `List<String>`

**格式:** 列表，每行对应一行描述文本

**示例:**

```yaml
display:
  lore:
    - "&7这是一个普通的按钮"
    - ""
    - "&e[左键] 点击执行操作"
```

***

### amount - 物品数量

定义按钮图标的堆叠数量（显示在物品右上角的数字）。

**类型:** `Integer`

**范围:** 1-64

**默认值:** `1`

**示例:**

```yaml
display:
  amount: 16    # 16 个物品
```

**注意事项:**

* 会被限制在 1-64 之间，超出范围会被自动调整
* 对于不可堆叠的物品（如武器、工具），数量始终显示为 1
* 可以配合变量实现动态数量显示

***

### custom\_data - 自定义模型数据

定义物品的自定义模型数据（用于 1.21.4 以下版本的资源包模型）。

**类型:** `Integer`

**格式:** 整数（通常由资源包作者提供）

**示例:**

```yaml
display:
  material: DIAMOND_SWORD
  custom_data: 1234
  name: "&a自定义剑"
```

**使用场景:**

```yaml
# 自定义材质的武器
display:
  material: DIAMOND_SWORD
  custom_data: 1001
  name: "&a光之剑"
```

**注意事项:**

* 需要配合服务器资源包使用
* 自定义模型数据必须与资源包中的配置一致
* 在 1.21.4+ 版本中，建议使用 `item_model` 替代

***

### item\_model - 物品模型 (1.21.4+)

定义物品的自定义模型（使用 1.21.4+ 的新 API，支持任何命名空间）。

**类型:** `String`

**格式:** `namespace:key`

**版本要求:** Minecraft 1.21.4+

**使用场景:**

```yaml
# Oraxen 物品
display:
  material: PAPER
  item_model: "oraxen:mana_crystal"
  name: "&b魔法水晶"
```

```yaml
# 自定义资源包物品
display:
  material: DIAMOND_PICKAXE
  item_model: "myserver:epic_pickaxe"
  name: "&d史诗镐"
```

**版本兼容性:**

```yaml
# 1.21.4+ 版本
display:
  item_model: "mynamespace:mykey"
```

```yaml
# 1.21.4 以下版本（使用 custom_data）
display:
  custom_data: 1234
```

**注意事项:**

* 需要 1.21.4+ 的服务器版本
* 同时配置两者时，优先使用 item\_model，不支持则降级到 custom\_data。
* 支持任何命名空间，包括 Oraxen、ItemsAdder 等插件
* 在旧版本服务器上使用时，会自动忽略此配置

***

### glow - 附魔发光效果

定义物品是否显示附魔发光效果（紫色光晕）。

**类型:** `Boolean`

**默认值:** `false`

**示例:**

```yaml
display:
  material: DIAMOND_SWORD
  name: "&a发光剑"
  glow: true
```

***

## 🔧 特殊列表类型

### type - 动态列表类型

当 `type` 设置为特殊列表类型时，会自动根据数据源动态生成多个图标项，并支持分页。

### 布局设计参考

```yaml
# 示例布局
layout:
  - "#########"
  - "#MMMMMMM#"  # 若 M = 成员列表，自动填充 21 个字符的位置
  - "#MMMMMMM#"
  - "#MMMMMMM#"
  - "#<     >#"  # '<' = 上一页, '>' = 下一页
  - "#########"
```
插件会自动计算该示例视图中的`M`键的数量，并通过下方配置进行构建。若字符数量大于列表的数量，多的部分会显示空气。
### 配置模板
 
```yaml
buttons:
  M:
    type: LIST_TYPE  # 替换为构建的列表类型
    display:
      material: MATERIAL_NAME
      name: "&6Display Name with {variable}" 
      lore:
        - "&7Line 1: {variable_1}"
        - "&7Line 2: {variable_2}"
        - ""
        - "&8Click to interact"
    actions:
      left:
        - "command: your command {variable}"
```
每个类型都有不同的内置变量，详情可查看下方表格。

#### 📋 支持的列表类型

| 类型 | 说明 | 使用场景 |
|------|------|----------|
| `GUILDS_LIST` | 公会列表 | 显示所有服务器公会 |
| `MEMBERS_LIST` | 成员列表 | 显示当前公会所有成员信息 |
| `ALL_PLAYER` | 全服玩家列表 | 显示所有在线玩家，用于邀请等操作 |
| `BUFF_LIST` | Buff 列表 | 显示可购买/已解锁的 Buff |
| `GUILD_VAULTS` | 金库列表 | 显示公会金库的解锁状态 |
| `GUILD_UPGRADE` | 升级列表 | 显示公会升级选项和要求 |
| `TASK_DAILY` | 每日任务列表 | 显示当前玩家的每日任务 |
| `TASK_GLOBAL` | 公会任务列表 | 显示公会的共同任务 |

---

### 🏰 GUILDS_LIST - 公会列表

显示服务器所有公会的列表项，每个图标代表一个公会。

#### 内置变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `{guild_id}` | 公会 ID | `5` |
| `{guild_name}` | 公会名称 | `星空公会` |
| `{guild_level}` | 公会等级 | `3` |
| `{guild_members}` | 成员数量 | `15` |
| `{guild_max_members}` | 最大成员数 | `20` |
| `{guild_online}` | 在线成员数 | `8` |
| `{guild_balance}` | 公会余额 | `1500.00` |
| `{guild_announcement}` | 公会公告 | `欢迎加入！` |
| `{guild_create_time}` | 创建时间 | `2024-01-15 10:30:00` |
| `{guild_owner}` | 会长名称 | `Player1` |
| `{player}` | 查看者名称 | `Player2` |
| `{role}` | 查看者职位 | `MEMBER` |
| `{role_node}` | 职位数字代码 | `1` (MEMBER=1, ADMIN=2, OWNER=3) |

#### 配置示例

```yaml
buttons:
  G:
    type: GUILDS_LIST
    display:
      material: BOOK
      name: "&6{guild_name}"
      lore:
        - "&7等级: &f{guild_level}"
        - "&7成员: &f{guild_members}/{guild_max_members}"
        - "&7在线: &a{guild_online}"
        - "&7余额: &e${guild_balance}"
        - "&7会长: &f{guild_owner}"
        - "&7创建于: &f{guild_create_time}"
        - ""
        - "&8点击查看详情"
    actions:
      left:
        - "command: guild info {guild_id}"
```

---

### 👥 MEMBERS_LIST - 成员列表

显示当前公会所有成员的列表项，每个图标代表一个成员。

#### 内置变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `{members_name}` | 成员名称 | `Player1` |
| `{members_role}` | 成员职位（语言化） | `管理员` |
| `{members_contribution}` | 贡献值 | `500` |
| `{members_join_time}` | 加入时间 | `2024-01-15 10:30:00` |

#### 特性
- 图标为玩家头颅，会自动加载对应玩家皮肤
- 支持通过 actions 对成员执行操作（踢出、转让等）

#### 配置示例

```yaml
buttons:
  M:
    type: MEMBERS_LIST
    display:
      material: PLAYER_HEAD
      name: "&a{members_name}"
      lore:
        - "&7职位: &f{members_role}"
        - "&7贡献值: &e{members_contribution}"
        - "&7加入时间: &f{members_join_time}"
        - ""
        - "&8左键: 查看详情"
        - "&8右键: 踢出成员"
    actions:
      left:
        - "tell: 查看了成员 {members_name} 的信息"
      right:
        - "command: guild kick {members_name}"
```

---

### 🎮 ALL_PLAYER - 全服玩家列表

显示所有在线玩家的列表项，用于邀请等操作。

#### 内置变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `{player_name}` | 玩家名称 | `Player1` |
| `{player_uuid}` | 玩家 UUID | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` |
| `{player_guild_name}` | 玩家所在公会（如有） | `星空公会` 或 `无` |
| `{player_guild_level}` | 玩家所在公会等级（如有） | `3` 或 `-` |
| `{player_is_online}` | 是否在线 | `true/false` |

#### 配置示例

```yaml
buttons:
  P:
    type: ALL_PLAYER
    display:
      material: PLAYER_HEAD
      name: "&a{player_name}"
      lore:
        - "&7UUID: &f{player_uuid}"
        - "&7公会: &f{player_guild_name}"
        - "&7等级: &f{player_guild_level}"
        - ""
        - "&8左键: 邀请加入公会"
    actions:
      left:
        - "command: guild invite {player_name}"
```

---

### 🧪 BUFF_LIST - Buff 列表

显示可购买的 Buff 列表，包括已解锁和未解锁状态。

#### 内置变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `{buff_keyname}` | Buff 配置键名 | `strength_1` |
| `{buff_name}` | Buff 显示名称 | `力量 I` |
| `{buff_price}` | 购买价格 | `1000.0` |
| `{buff_time}` | 持续时间（分钟） | `90` |
| `{buff_status}` | 解锁状态（语言化） | `已解锁` / `未解锁` |

#### 特性
- 未解锁时材质为 `GLASS_BOTTLE`（玻璃瓶）
- 已解锁时材质为 `HONEY_BOTTLE`（蜂蜜瓶）
- 状态文本来自语言配置

#### 配置示例

```yaml
buttons:
  B:
    type: BUFF_LIST
    display:
      material: HONEY_BOTTLE
      name: "&6{buff_name}"
      lore:
        - "&7状态: {buff_status}"
        - "&7价格: &e${buff_price}"
        - "&7持续时间: &f{buff_time} 分钟"
        - ""
        - "&8左键: 购买/激活"
    actions:
      left:
        - "command: guild buff {buff_keyname}"
```

---

### 💎 GUILD_VAULTS - 金库列表

显示公会金库的解锁状态，每个图标代表一个金库槽位。

#### 内置变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `{vault_num}` | 金库编号 | `1` |
| `{vault_status}` | 解锁状态（语言化） | `已解锁` / `未解锁` |

#### 特性
- 未解锁时材质为 `MINECART`（矿车）
- 已解锁时材质为 `CHEST_MINECART`（运输矿车）

#### 配置示例

```yaml
buttons:
  V:
    type: GUILD_VAULTS
    display:
      material: CHEST_MINECART
      name: "&6金库 #{vault_num}"
      lore:
        - "&7状态: {vault_status}"
        - ""
        - "&8左键: 打开金库"
    actions:
      left:
        - "command: guild vault {vault_num}"
```

---

### ⬆️ GUILD_UPGRADE - 升级列表

显示公会可升级的等级选项和要求。

#### 内置变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `{upgrade_level}` | 目标等级 | `4` |
| `{upgrade_max_members}` | 等级成员上限 | `25` |
| `{upgrade_max_money}` | 等级余额上限 | `50000` |
| `{upgrade_max_vaults}` | 等级金库上限 | `5` |
| `{upgrade_tp_money}` | 等级传送费用 | `50.0` |
| `{upgrade_use_buff}` | 可使用 Buff 数量 | `10` |
| `{upgrade_bank_interest}` | 银行利息类型数 | `3` |
| `{upgrade_current_exp}` | 当前经验值 | `1500` |
| `{upgrade_need_exp}` | 需要经验值 | `2000` |
| `{upgrade_status}` | 状态（语言化） | `可升级` / `经验不足` / `已锁定` |


#### 配置示例

```yaml
buttons:
  U:
    type: GUILD_UPGRADE
    display:
      material: BOOK
      name: "&6升级到 {upgrade_level} 级"
      lore:
        - "&7状态: {upgrade_status}"
        - ""
        - "&7成员上限: &f{upgrade_max_members}"
        - "&7余额上限: &f${upgrade_max_money}"
        - "&7金库上限: &f{upgrade_max_vaults}"
        - "&7传送费用: &f${upgrade_tp_money}"
        - "&7可使用 Buff: &f{upgrade_use_buff}"
        - ""
        - "&7经验: &f{upgrade_current_exp}/{upgrade_need_exp}"
        - ""
        - "&8左键: 升级公会"
    actions:
      left:
        - "command: guild upgrade"
```

---

### 📋 TASK_DAILY - 每日任务列表

显示当前玩家的每日任务列表，追踪个人任务进度。

#### 内置变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `{task_key}` | 任务配置键名 | `mine_stone_100` |
| `{task_name}` | 任务名称 | `挖掘石头` |
| `{task_lore}` | 任务描述（多行） | `在野外挖掘 100 个石头` |
| `{task_progress}` | 当前进度 | `45` |
| `{task_amount}` | 目标数量 | `100` |
| `{task_status}` | 完成状态（语言化） | `已完成` / `未完成` |

#### 特性
- 当 `{task_material}` 为 `{task_item}` 时：
  - 已完成材质为 `ENCHANTED_BOOK`（附魔书）
  - 未完成材质为 `BOOK`（书）
- 数据按玩家单独追踪，每个玩家看到的进度不同

#### 配置示例

```yaml
buttons:
  D:
    type: TASK_DAILY
    display:
      material: "{task_item}"
      name: "&6{task_name}"
      lore:
        - "&7{task_lore}"
        - ""
        - "&7进度: &f{task_progress}/{task_amount}"
        - "&7状态: {task_status}"
```

---

### 🌍 TASK_GLOBAL - 公会任务列表

显示公会的共同任务列表，所有成员共同完成。

#### 内置变量

变量与 `TASK_DAILY` 相同，但数据是全公会共享的。

#### 配置示例

```yaml
buttons:
  G:
    type: TASK_GLOBAL
    display:
      material: "{task_item}"
      name: "&b{task_name}"
      lore:
        - "&7{task_lore}"
        - ""
        - "&7公会进度: &f{task_progress}/{task_amount}"
        - "&7状态: {task_status}"
        - ""
        - "&8所有成员共同完成此任务"
    actions:
      left:
        - "command: guild claimglobaltask {task_key}"
```

---

## 📝 列表类型配置总结

### 分页支持
所有列表类型都自动支持分页功能：
- 菜单会根据布局中列表类型槽位的数量计算每页显示数量
- 使用 `PAGE_PREV` 和 `PAGE_NEXT` 动作翻页
- 每个列表项的 actions 可以使用列表的内置变量

### 刷新机制
- 列表数据在打开菜单时加载
- 如需实时更新，在菜单配置中设置 `update` 刷新间隔
- 刷新时会重新从数据库获取最新数据
