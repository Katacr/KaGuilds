# ⚙️ 配置文件详解

本页面详细介绍 KaGuilds 插件的各个配置文件及其选项。

---

### 基础设置

#### `language`
插件使用的语言文件。

```yaml
language: zh_CN  # 可选: zh_CN 或 en_US
```

- **默认值**: `zh_CN`
- **可用选项**: `zh_CN` (简体中文), `en_US` (English)
- **说明**: 决定插件界面的显示语言

#### `proxy`
是否启用代理模式（跨服同步）。

```yaml
proxy: false
```

- **默认值**: `false`
- **可用选项**: `true`, `false`
- **说明**: 
  - `true`: 启用跨服同步，用于 Velocity 网络通讯
  - `false`: 单服模式，不进行跨服通信

#### `server-id`
服务器的唯一标识符。

```yaml
server-id: server
```

- **默认值**: `server`
- **类型**: 字符串
- **说明**: 
  - 在跨服模式下，每个子服必须设置不同的 ID
  - 用于区分不同服务器发来的消息
  - 示例: `survival`, `creative`, `minigames`

#### `date-format`
日期和时间的显示格式。

```yaml
date-format: "yyyy-MM-dd HH:mm:ss"
```

- **默认值**: `yyyy-MM-dd HH:mm:ss`
- **类型**: 字符串
- **说明**: 
  - 使用 Java SimpleDateFormat 格式
  - `yyyy`: 年, `MM`: 月, `dd`: 日
  - `HH`: 时(24小时制), `mm`: 分, `ss`: 秒

---

### 公会设置 (`guild`)

#### 名称设置 (`name-settings`)

```yaml
guild:
  name-settings:
    min-length: 3
    max-length: 16
    regex: "^[\\u4e00-\\u9fa5a-zA-Z0-9]+$"
```

##### `min-length`
公会名称的最小长度。

- **默认值**: `3`
- **类型**: 整数
- **说明**: 玩家创建公会时，名称长度不能小于此值

##### `max-length`
公会名称的最大长度。

- **默认值**: `16`
- **类型**: 整数
- **说明**: 玩家创建公会时，名称长度不能超过此值

##### `regex`
公会名称的正则表达式验证规则。

- **默认值**: `^[\\u4e00-\\u9fa5a-zA-Z0-9]+$`
- **类型**: 正则表达式字符串
- **说明**: 
  - `\\u4e00-\\u9fa5`: 允许中文字符
  - `a-zA-Z`: 允许英文字母
  - `0-9`: 允许数字
  - 修改此规则可以自定义允许的字符

#### `motd`
公会默认公告。

```yaml
guild:
  motd: "欢迎加入我的公会"
```

- **默认值**: `"欢迎加入我的公会"`
- **类型**: 字符串
- **说明**: 
  - 新创建的公会自动使用此公告
  - 可通过 `/kg motd` 命令修改

#### `icon`
公会默认图标。

```yaml
guild:
  icon: "SHIELD"
```

- **默认值**: `"SHIELD"`
- **类型**: 字符串 (Material 枚举名)
- **说明**: 
  - 新创建的公会自动使用此材质作为图标
  - 必须是 Minecraft 有效的 Material 名称
  - 可通过 `/kg seticon` 命令修改

#### `chat-format`
公会聊天消息的格式。

```yaml
guild:
  chat-format: "&7[&e公会聊天&7] &f{role} &f%player_level% &6{player}&f: {message}"
```

- **默认值**: `"&7[&e公会聊天&7] &f{role} &f%player_level% &6{player}&f: {message}"`
- **类型**: 字符串
- **说明**: 
  - `{role}`: 玩家职位
  - `{player}`: 玩家名称
  - `{message}`: 聊天消息内容
  - 支持颜色代码 `&`
  - 支持 PlaceholderAPI 变量

---

#### 传送设置 (`teleport`)

```yaml
guild:
  teleport:
    disabled-worlds:
      - "world_nether"
      - "world_the_end"
    cooldown: 3
```

##### `disabled-worlds`
禁止设置公会传送点的世界列表。

- **默认值**: `["world_nether", "world_the_end"]`
- **类型**: 列表
- **说明**: 
  - 玩家不能在这些世界中设置传送点
  - 添加世界名称即可禁止

##### `cooldown`
传送冷却时间（秒）。

- **默认值**: `3`
- **类型**: 整数
- **说明**: 
  - 玩家使用 `/kg tp` 后的冷却时间
  - 防止玩家频繁传送

---

#### PvP 竞技场设置 (`arena`)

```yaml
guild:
  arena:
    cooldown: 300
    min-players: 2
    max-players: 5
    ready-time: 30
    pvp-time: 600
    kit: true
    reward-command:
      - 'kg admin bank {win_id} add 1000'
      - 'kg admin exp {win_id} add 500'
      - 'kg admin bank {lose_id} add 100'
      - 'kg admin exp {lose_id} add 50'
```

##### `cooldown`
公会战冷却时间（秒）。

- **默认值**: `300` (5 分钟)
- **类型**: 整数
- **说明**: 
  - 同一公会两次对战之间的最小间隔
  - 防止公会频繁发起对战

##### `min-players`
单方队伍的最小人数。

- **默认值**: `2`
- **类型**: 整数
- **说明**: 
  - 每方至少需要多少人才能开始对战
  - 确保对战的公平性

##### `max-players`
单方队伍的最大人数。

- **默认值**: `5`
- **类型**: 整数
- **说明**: 
  - 每方最多可以有多少人参与对战
  - 超出此人数的玩家无法加入

##### `ready-time`
准备阶段时间（秒）。

- **默认值**: `30`
- **类型**: 整数
- **说明**: 
  - 对战开始前的准备时间
  - 玩家在此时间内可以准备装备和策略

##### `pvp-time`
对战时间（秒）。

- **默认值**: `600` (10 分钟)
- **类型**: 整数
- **说明**: 
  - 对战的最大持续时间
  - 超时后根据击杀数判定胜负

##### `kit`
是否启用预设装备。

- **默认值**: `true`
- **类型**: 布尔值
- **说明**: 
  - `true`: 玩家进入竞技场时获得预设装备
  - `false`: 玩家使用自己的装备

##### `reward-command`
对战奖励命令列表。

- **默认值**: 见上方示例
- **类型**: 列表
- **说明**: 
  - 对战结束后由控制台执行的命令
  - `{win_id}`: 胜利公会的 ID
  - `{lose_id}`: 战败公会的 ID
  - 可以添加任意命令

---

## 🗄️ database - 数据库设置

```yaml
database:
  type: "SQLite"
  host: "localhost"
  port: 3306
  db: "kaguilds"
  user: "root"
  password: "password"
```

### `type`
数据库类型。

- **默认值**: `"SQLite"`
- **可用选项**: `"SQLite"`, `"MySQL"`
- **说明**: 
  - `SQLite`: 适合单服，配置简单
  - `MySQL`: 适合跨服架构，数据共享更方便

---

## 💰 balance - 经济系统设置

```yaml
balance:
  create: 10000.0
  rename: 3000.0
  settp: 1000.0
  seticon: 1000.0
  setmotd: 100.0
  pvp: 300.0
```

### `create`
创建公会所需金币。

- **默认值**: `10000.0`
- **类型**: 浮点数
- **说明**: 
  - 玩家使用 `/kg create` 时扣除的费用
  - 设置为 0 可免费创建

### `rename`
公会改名所需金币。

- **默认值**: `3000.0`
- **类型**: 浮点数
- **说明**: 
  - 会长使用 `/kg rename` 时扣除的费用
  - 需要确认才能执行

### `settp`
设置公会传送点所需金币。

- **默认值**: `1000.0`
- **类型**: 浮点数
- **说明**: 
  - 会长使用 `/kg settp` 时扣除的费用
  - 不能在禁止的世界设置传送点

### `seticon`
设置公会图标所需金币。

- **默认值**: `1000.0`
- **类型**: 浮点数
- **说明**: 
  - 会长手持物品使用 `/kg seticon` 时扣除的费用
  - 图标用于在菜单中显示公会

### `setmotd`
设置公会公告所需金币。

- **默认值**: `100.0`
- **类型**: 浮点数
- **说明**: 
  - 会长使用 `/kg motd` 时扣除的费用
  - 公告显示在公会信息中

### `pvp`
发起公会战所需金币。

- **默认值**: `300.0`
- **类型**: 浮点数
- **说明**: 
  - 发起公会对战时扣除的费用
  - 从公会金库中扣除

---

## 📋 task - 任务系统设置

```yaml
task:
  daily_boss_bar: true
  global_boss_bar: true
  reset_time: "00:00:00"
```

### `daily_boss_bar`
每日任务进度是否显示 BossBar。

- **默认值**: `true`
- **类型**: 布尔值
- **说明**: 
  - `true`: 完成每日任务时显示进度条
  - `false`: 不显示进度条

### `global_boss_bar`
全局任务进度是否显示 BossBar。

- **默认值**: `true`
- **类型**: 布尔值
- **说明**: 
  - `true`: 完成全局任务时显示进度条
  - `false`: 不显示进度条

### `reset_time`
任务重置时间。

- **默认值**: `"00:00:00"`
- **类型**: 字符串
- **说明**: 
  - 格式: `HH:mm:ss`
  - 每日任务和全局任务在此时间重置
  - 示例: `"04:00:00"` 表示凌晨 4 点重置

---

## 🎖️ contribution - 贡献度系统设置

```yaml
contribution:
  enabled: true
  bank-deposit-ratio: 1.0
  bank-withdraw-ratio: 1.0
```

### `enabled`
是否启用贡献度系统。

- **默认值**: `true`
- **类型**: 布尔值
- **说明**: 
  - `true`: 启用贡献度系统
  - `false`: 禁用贡献度系统

### `bank-deposit-ratio`
存入金币时获得贡献度的比例。

- **默认值**: `1.0`
- **类型**: 浮点数
- **说明**: 
  - 存入 1 金币获得多少贡献度
  - 示例: `1.0` 表示 1:1，`0.5` 表示 1 金币 = 0.5 贡献度

### `bank-withdraw-ratio`
提取金币时扣除贡献度的比例。

- **默认值**: `1.0`
- **类型**: 浮点数
- **说明**: 
  - 提取 1 金币扣除多少贡献度
  - 示例: `1.0` 表示 1:1，`2.0` 表示提取 1 金币需扣除 2 贡献度

---

## 🔧 重新加载配置

修改配置文件后，使用以下命令重新加载：

```bash
/kg reload
```

{% hint style="info" %}
部分配置需要重启服务器才能生效，如数据库设置和代理模式。
{% endhint %}
