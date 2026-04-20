# ⛳ Configuration File: config.yml

This page details each configuration file in KaGuilds and their options.

***

## Basic Settings

### `language`

The language file used by the plugin.

```yaml
language: zh_CN  # Options: zh_CN or en_US
```

* **Default**: `zh_CN`
* **Available Options**: `zh_CN` (Simplified Chinese), `en_US` (English)
* **Description**: Determines the display language for the plugin interface

### `proxy`

Whether to enable proxy mode (cross-server communication).

```yaml
proxy: false
```

* **Default**: `false`
* **Available Options**: `true`, `false`
* **Description**:
  * `true`: Enables cross-server synchronization for Velocity network communication
  * `false`: Single-server mode, no cross-server communication

### `server-id`

Unique identifier for the server.

```yaml
server-id: server
```

* **Default**: `server`
* **Type**: String
* **Description**:
  * In cross-server mode, each sub-server must have a different ID
  * Used to distinguish messages from different servers
  * Examples: `survival`, `creative`, `minigames`

### `date-format`

Date and time display format.

```yaml
date-format: "yyyy-MM-dd HH:mm:ss"
```

* **Default**: `yyyy-MM-dd HH:mm:ss`
* **Type**: String
* **Description**:
  * Uses Java SimpleDateFormat
  * `yyyy`: Year, `MM`: Month, `dd`: Day
  * `HH`: Hour (24-hour), `mm`: Minute, `ss`: Second

***

## Guild Settings (`guild`)

### Name Settings (`name-settings`)

```yaml
guild:
  name-settings:
    min-length: 3
    max-length: 16
    regex: "^[\\u4e00-\\u9fa5a-zA-Z0-9]+$"
```

#### `min-length`

Minimum length of guild names.

* **Default**: `3`
* **Type**: Integer
* **Description**: Player-created guild names cannot be shorter than this value

#### `max-length`

Maximum length of guild names.

* **Default**: `16`
* **Type**: Integer
* **Description**: Player-created guild names cannot exceed this value

#### `regex`

Regular expression validation rule for guild names.

* **Default**: `^[\\u4e00-\\u9fa5a-zA-Z0-9]+$`
* **Type**: Regex string
* **Description**:
  * `\\u4e00-\\u9fa5`: Allows Chinese characters
  * `a-zA-Z`: Allows English letters
  * `0-9`: Allows numbers
  * Modify this rule to customize allowed characters

### `motd`

Default guild announcement.

```yaml
guild:
  motd: "Welcome to my guild"
```

* **Default**: `"Welcome to my guild"`
* **Type**: String
* **Description**:
  * New guilds automatically use this announcement
  * Can be modified via the `/kg motd` command

### `icon`

Default guild icon configuration, supports material, custom model data, and item models.

```yaml
icon:
  material: "SHIELD"
  item_model: "minecraft:shield"
  custom_data: 0
```

* **Description**:
  * New guilds automatically use this configuration as the icon
  * Can be set via the `/kg seticon` command with the held item
  * Supports version compatibility:
    * 1.21.4+: Prefers `item_model`
    * 1.21.4-: Uses `custom_data`
    * If neither is configured, uses `material`


### `chat-format`

Guild chat message format.

```yaml
guild:
  chat-format: "&7[&eGuild Chat&7] &f{role} &f%player_level% &6{player}&f: {message}"
```

* **Default**: `"&7[&eGuild Chat&7] &f{role} &f%player_level% &6{player}&f: {message}"`
* **Type**: String
* **Description**:
  * `{role}`: Player's rank
  * `{player}`: Player name
  * `{message}`: Chat message content
  * Supports color codes `&`
  * Supports PlaceholderAPI variables

***

### Teleport Settings (`teleport`)

```yaml
guild:
  teleport:
    disabled-worlds:
      - "world_nether"
      - "world_the_end"
    cooldown: 3
```

#### `disabled-worlds`

List of worlds where setting guild teleports is prohibited.

* **Default**: `["world_nether", "world_the_end"]`
* **Type**: List
* **Description**:
  * Players cannot set teleports in these worlds
  * Add world names to prohibit

#### `cooldown`

Teleport cooldown time (seconds).

* **Default**: `3`
* **Type**: Integer
* **Description**:
  * Cooldown after player uses `/kg tp`
  * Prevents players from teleporting too frequently

***

### PvP Arena Settings (`arena`)

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

#### `cooldown`

Guild battle cooldown time (seconds).

* **Default**: `300` (5 minutes)
* **Type**: Integer
* **Description**:
  * Minimum interval between two battles for the same guild
  * Prevents guilds from initiating battles too frequently

#### `min-players`

Minimum number of players per team.

* **Default**: `2`
* **Type**: Integer
* **Description**:
  * Minimum number of players needed per team to start a battle
  * Ensures battle fairness

#### `max-players`

Maximum number of players per team.

* **Default**: `5`
* **Type**: Integer
* **Description**:
  * Maximum number of players per team
  * Players beyond this limit cannot join

#### `ready-time`

Ready phase time (seconds).

* **Default**: `30`
* **Type**: Integer
* **Description**:
  * Preparation time before battle starts
  * Players can prepare equipment and strategy during this time

#### `pvp-time`

Battle time (seconds).

* **Default**: `600` (10 minutes)
* **Type**: Integer
* **Description**:
  * Maximum duration of the battle
  * Winner determined by kill count after timeout

#### `kit`

Whether to enable preset equipment.

* **Default**: `true`
* **Type**: Boolean
* **Description**:
  * `true`: Players receive preset equipment when entering the arena
  * `false`: Players use their own equipment

#### `reward-command`

Battle reward command list.

* **Default**: See example above
* **Type**: List
* **Description**:
  * Commands executed by console after battle ends
  * `{win_id}`: Winning guild's ID
  * `{lose_id}`: Losing guild's ID
  * Can add any commands

***

## 🗄️ database - Database Settings

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

Database type.

* **Default**: `"SQLite"`
* **Available Options**: `"SQLite"`, `"MySQL"`
* **Description**:
  * `SQLite`: Suitable for single-server, simple configuration
  * `MySQL`: Suitable for cross-server architecture, easier data sharing

***

## 💰 balance - Economy System Settings

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

Gold required to create a guild.

* **Default**: `10000.0`
* **Type**: Float
* **Description**:
  * Fee deducted when player uses `/kg create`
  * Set to 0 for free creation

### `rename`

Gold required to rename a guild.

* **Default**: `3000.0`
* **Type**: Float
* **Description**:
  * Fee deducted when owner uses `/kg rename`
  * Requires confirmation to execute

### `settp`

Gold required to set guild teleport point.

* **Default**: `1000.0`
* **Type**: Float
* **Description**:
  * Fee deducted when owner uses `/kg settp`
  * Cannot set teleport in prohibited worlds

### `seticon`

Gold required to set guild icon.

* **Default**: `1000.0`
* **Type**: Float
* **Description**:
  * Fee deducted when owner uses `/kg seticon` with held item
  * Icon used to display guild in menus

### `setmotd`

Gold required to set guild announcement.

* **Default**: `100.0`
* **Type**: Float
* **Description**:
  * Fee deducted when owner uses `/kg motd`
  * Announcement displayed in guild info

### `pvp`

Gold required to initiate a guild battle.

* **Default**: `300.0`
* **Type**: Float
* **Description**:
  * Fee deducted when initiating a guild battle
  * Deducted from guild bank

***

## 📋 task - Task System Settings

```yaml
task:
  daily_boss_bar: true
  global_boss_bar: true
  reset_time: "00:00:00"
```

### `daily_boss_bar`

Whether to display BossBar for daily task progress.

* **Default**: `true`
* **Type**: Boolean
* **Description**:
  * `true`: Display progress bar when completing daily tasks
  * `false`: Don't display progress bar

### `global_boss_bar`

Whether to display BossBar for global task progress.

* **Default**: `true`
* **Type**: Boolean
* **Description**:
  * `true`: Display progress bar when completing global tasks
  * `false`: Don't display progress bar

### `reset_time`

Task reset time.

* **Default**: `"00:00:00"`
* **Type**: String
* **Description**:
  * Format: `HH:mm:ss`
  * Daily tasks and global tasks reset at this time
  * Example: `"04:00:00"` resets at 4 AM

***

## 🎖️ contribution - Contribution System Settings

```yaml
contribution:
  enabled: true
  bank-deposit-ratio: 1.0
  bank-withdraw-ratio: 1.0
```

### `enabled`

Whether to enable the contribution system.

* **Default**: `true`
* **Type**: Boolean
* **Description**:
  * `true`: Enable contribution system
  * `false`: Disable contribution system

### `bank-deposit-ratio`

Contribution ratio when depositing gold.

* **Default**: `1.0`
* **Type**: Float
* **Description**:
  * How much contribution earned per gold deposited
  * Example: `1.0` means 1:1, `0.5` means 1 gold = 0.5 contribution

### `bank-withdraw-ratio`

Contribution deducted when withdrawing gold.

* **Default**: `1.0`
* **Type**: Float
* **Description**:
  * How much contribution deducted per gold withdrawn
  * Example: `1.0` means 1:1, `2.0` means 1 gold withdrawn costs 2 contribution

***

## 🎨 menu-default-icon - Menu Default Icon Settings

Used to configure default icons for various menus, used when menu items don't have individually configured icons.

```yaml
menu-default-icon:
  tasks:
    unfinished:
      material: "BOOK"
      custom_data: 0
      item_model: "minecraft:book"
    finished:
      material: "ENCHANTED_BOOK"
  buffs:
    lock:
      material: "GLASS_BOTTLE"
      custom_data: 0
      item_model: "minecraft:glass_bottle"
    unlock:
      material: "HONEY_BOTTLE"
  levels:
    lock:
      material: "BOOK"
    unlock:
      material: "ENCHANTED_BOOK"
  vaults:
    lock:
      material: "MINECART"
    unlock:
      material: "CHEST_MINECART"
```

### Configuration Priority

* **Priority**: `item_model` > `custom_data` > `material`
* **Description**:
  * 1.21.4+: Prefers `item_model`
  * 1.21.4-: Uses `custom_data`
  * If neither is configured, uses `material`

---

### `tasks` - Task Menu Icons

Used when `display` key is not configured in task settings (`task.yml`).

#### `unfinished` - Unfinished Task Icon

```yaml
tasks:
  unfinished:
    material: "BOOK"
    custom_data: 0
    item_model: "minecraft:book"
```

* **material**: Item material name (must be uppercase), e.g., `BOOK`, `PAPER`, `BARRIER`
* **custom_data**: Custom model data (only for versions below 1.21.4), default `0`
* **item_model**: Item model (only for 1.21.4+), format is `namespace:model_key`, e.g., `kaguilds:task_unfinished`

#### `finished` - Finished Task Icon

```yaml
tasks:
  finished:
    material: "ENCHANTED_BOOK"
```

Configuration items are the same as above.

---

### `buffs` - Buff Shop Menu Icons

Used for icons in the guild buff shop.

#### `lock` - Locked Buff Icon

```yaml
buffs:
  lock:
    material: "GLASS_BOTTLE"
    custom_data: 0
    item_model: "minecraft:glass_bottle"
```

* **material**: Item material name (must be uppercase)
* **custom_data**: Custom model data (only for versions below 1.21.4)
* **item_model**: Item model (only for 1.21.4+)

#### `unlock` - Unlocked Buff Icon

```yaml
buffs:
  unlock:
    material: "HONEY_BOTTLE"
```

Configuration items are the same as above.

---

### `levels` - Guild Upgrade Menu Icons

Used for level icons in the guild upgrade menu.

#### `lock` - Locked Level Icon (unlocked level)

```yaml
levels:
  lock:
    material: "BOOK"
```

* **material**: Item material name (must be uppercase)
* **custom_data**: Custom model data (only for versions below 1.21.4)
* **item_model**: Item model (only for 1.21.4+)

#### `unlock` - Unlocked Level Icon (unlocked/upgradeable level)

```yaml
levels:
  unlock:
    material: "ENCHANTED_BOOK"
```

Configuration items are the same as above.

---

### `vaults` - Guild Storage Menu Icons

Used for vault icons in the guild storage menu.

#### `lock` - Locked Vault Icon (unlocked vault)

```yaml
vaults:
  lock:
    material: "MINECART"
```

* **material**: Item material name (must be uppercase)
* **custom_data**: Custom model data (only for versions below 1.21.4)
* **item_model**: Item model (only for 1.21.4+)

#### `unlock` - Unlocked Vault Icon (unlocked vault)

```yaml
vaults:
  unlock:
    material: "CHEST_MINECART"
```

Configuration items are the same as above.

***

## 🔧 Reloading Configuration

After modifying configuration files, use the following command to reload:

```bash
/kg reload
```

{% hint style="info" %}
Some configurations require a server restart to take effect, such as database settings and proxy mode.
{% endhint %}
