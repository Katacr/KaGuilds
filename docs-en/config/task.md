# 📆 Task System: task.yml

The `task.yml` configuration file defines the guild task system. The task system has two types: **Daily Tasks** and **Global Tasks**, providing rich gameplay goals for guild members.

***

## 📋 Configuration Structure

```yaml
tasks:
  TaskKey:                # Unique task identifier
    display:               # Task display configuration in GUI (optional)
      unfinished:           # Icon when task is unfinished
        material: "PAPER"
        custom_data: 0
        item_model: "minecraft:book"
      finished:           # Icon when task is finished
        material: "PAPER"
        custom_data: 0
        item_model: "minecraft:enchanted_book"
    name: "Task Name"            # Task display name
    type: "global"              # Task type: global or daily
    event:                      # Event trigger configuration
      type: "kill_mobs"          # Event type
      target: "zombie"          # Target type
      amount: 100               # Target amount
    lore:                       # Task description
      - "§7Description: Task details"
      - "§7Reward: Reward content"
    actions:                    # Actions after completion
      - "console: <command>"
      - "tell: <message>"
```

***

## 🔧 Configuration Options Explained

### `TaskKey` (Required)

* **Type**: String
* **Description**: Unique identifier for the task, used for internal referencing
* **Examples**: `global_1`, `daily_1`, `fishing_daily`
* **Rules**:
  * Must be unique
  * Recommended: lowercase letters, numbers, and underscores
  * Should be descriptive for easy identification

### `display` (Optional)

Display configuration for the task in the GUI menu.

* Supports version compatibility:
    * 1.21.4+: Prefers `item_model`
    * 1.21.4-: Uses `custom_data`
    * If neither is configured, uses `material`

#### `material`

* **Type**: String
* **Description**: Item material for the task icon

#### `item_model` (Optional)

* **Type**: String
* **Description**: Custom item model

#### `custom_data` (Optional)

* **Type**: Integer
* **Description**: Custom texture pack data

### `name` (Required)

* **Type**: String
* **Description**: Display name of the task, shown in GUI and messages

### `type` (Required)

* **Type**: String
* **Description**: Task type
* **Default**: `"global"`
* **Available Options**:
  * `"global"` - Global task, completed collaboratively by all guild members
  * `"daily"` - Daily task, completed individually by each player
* **Difference**:
  * **Global Tasks**:
    * Progress from all guild members accumulates
    * Progress displayed with yellow BossBar
    * Only resets at task reset time
  * **Daily Tasks**:
    * Completed individually by each player
    * Only rewards the current player upon completion
    * Progress displayed with green BossBar
    * Resets daily at task reset time

### `event` (Required)

Task trigger event configuration.

#### `event.type` (Required)

* **Type**: String
* **Description**: Event type that triggers the task
* **Available Event Types**:

| Event Type | Description | Notes |
|:----------|:------------|:------|
| `login` | Player login | - |
| `kill_mobs` | Kill mobs | Kill specified entity types |
| `break_block` | Break blocks | Mine specified blocks |
| `donate` | Donate gold | Donate to guild vault |
| `chat` | Send messages | Send chat messages |
| `fishing` | Fishing | Catch fish |
| `milk` | Milking | Milk cows |
| `shear` | Shearing | Shear sheep |
| `trade` | Villager trade | Trade with villagers |
| `eat_food` | Eat food | Consume food |
| `bonemeal` | Use bone meal | Grow crops with bone meal |
| `smelt` | Smelting | Smelt items |

#### `event.target` (Required)

* **Type**: String
* **Description**: Target object of the event
* **Rules**:
  * `"*"` means any target or no target required
  * For `kill_mobs`: Use entity type (e.g., `zombie`, `skeleton`)
  * For `break_block`: Use block type (e.g., `stone`, `diamond_ore`)
  * For `smelt`: Use item type (e.g., `iron_ingot`, `gold_ingot`)
  * For `eat_food`: Use food type (e.g., `cooked_beef`, `bread`)

{% hint style="info" %}
**Tip**: `target` uses lowercase Bukkit enum names or item IDs.
{% endhint %}

#### `event.amount` (Required)

* **Type**: Integer
* **Description**: Target amount for the task
* **Rules**:
  * Must be greater than 0
  * For `donate` event, unit is gold amount

### `lore` (Required)

* **Type**: List
* **Description**: Task description list, displayed in GUI
* **Rules**:
  * Supports color codes
  * One string per line
  * Should include task description and reward information

### `actions` (Required)

* **Type**: List
* **Description**: Action list executed after task completion
* **Default**: None
* **Supported Variables**:
  * `{player}` - Player name
  * `{guild_id}` - Guild ID
  * `{guild_name}` - Guild name
  * Supports all PlaceholderAPI variables
* **Available Action Types**:

| Action Type | Format | Description |
|:----------|:-------|:------------|
| `console` | `console: <command>` | Execute command as console |
| `command` | `command: <command>` | Execute command as player |
| `tell` | `tell: <message>` | Send message to player |

* **Examples**:

    ```yaml
    actions:
      - "console: money give {player} 100"        # Give player 100 gold
      - "console: kg admin exp {guild_id} add 80" # Give guild 80 experience
      - "tell: §aTask completed! You received 100 gold"
      - "command: say I completed the task"        # Player says something
    ```

***

## 💡 Configuration Examples

### Daily Task Examples

#### Login Task

```yaml
daily_login:
  display:
    material: "COMPASS"
  name: "§aDaily Task: Login"
  type: "daily"
  event:
    type: "login"
    target: "*"
    amount: 1
  lore:
    - "§7Description: Log in and play daily"
    - "§7Reward: §e10 Experience"
  actions:
    - "console: kg admin exp %kaguilds_id% add 10"
```

#### Fishing Task

```yaml
fishing_daily:
  display:
    material: "FISHING_ROD"
  name: "§aDaily Task: Fisherman"
  type: "daily"
  event:
    type: "fishing"
    target: "*"
    amount: 10
  lore:
    - "§7Description: Fish 10 times daily"
    - "§7Reward: §e20 Experience"
  actions:
    - "console: kg admin exp %kaguilds_id% add 20"
```

#### Food Task

```yaml
eat_daily:
  display:
    material: "COOKED_BEEF"
  name: "§aDaily Task: Foodie"
  type: "daily"
  event:
    type: "eat_food"
    target: "cooked_beef"
    amount: 5
  lore:
    - "§7Description: Eat steak 5 times daily"
    - "§7Reward: §e10 Experience"
  actions:
    - "console: kg admin exp %kaguilds_id% add 10"
```

### Global Task Examples

#### Kill Mobs Task

```yaml
global_zombie:
  display:
    material: "IRON_SWORD"
  name: "§aCumulative Task: Guild Battle"
  type: "global"
  event:
    type: "kill_mobs"
    target: "zombie"
    amount: 100
  lore:
    - "§7Description: Guild collectively kills 100 zombies"
    - "§7Reward: §e80 Experience §7100 Gold"
  actions:
    - "console: money give {player} 100"
    - "console: kg admin exp %kaguilds_id% add 80"
```

#### Smelting Task

```yaml
smelt_iron_global:
  display:
    material: "FURNACE"
  name: "§aCumulative Task: Blacksmith"
  type: "global"
  event:
    type: "smelt"
    target: "iron_ingot"
    amount: 64
  lore:
    - "§7Description: Guild collectively smelts 64 iron ingots"
    - "§7Reward: §e80 Experience"
  actions:
    - "console: kg admin exp %kaguilds_id% add 80"
```

#### Donation Task

```yaml
donate_global:
  display:
    material: "GOLD_INGOT"
  name: "§aCumulative Task: Generous Heart"
  type: "global"
  event:
    type: "donate"
    target: "*"
    amount: 1000
  lore:
    - "§7Description: Guild collectively donates 1000 gold"
    - "§7Reward: §e100 Experience"
  actions:
    - "console: kg admin exp %kaguilds_id% add 100"
```

***

### Task Reset

Configure task reset time in `config.yml`:

```yaml
task:
  reset_time: "00:00:00"  # Reset daily at midnight
```
