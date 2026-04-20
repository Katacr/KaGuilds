# 🎲 Icons

The `display` node defines the visual appearance of each button (icon) in the menu, including material, name, description, amount, custom model, and other properties.

## 📋 Configuration Structure

```yaml
buttons:
  X:
    display:
      material: DIAMOND_SWORD
      name: "&aMy Sword"
      lore:
        - "&7A sharp sword"
        - "&7Attack: 10"
      amount: 1
      custom_data: 1234
      item_model: "mynamespace:mykey"
      glow: false
    actions:
      left:
        - "command: say hello"
```

## 🔧 Configuration Options Explained

### material - Item Material

Defines the Minecraft item type for the button icon.

**Type:** `String`

**Format:** Minecraft material name (supports uppercase, lowercase, or mixed case)

**Example:**

```yaml
display:
  material: DIAMOND_SWORD          # Diamond Sword
```

***

### name - Display Name

Defines the display name of the button icon (shown on mouse hover).

**Type:** `String`

**Format:** Supports color codes (using `&`) and placeholder variables

**Example:**

```yaml
display:
  name: "&6&l Guild Settings"
```

**Placeholder Variables:**

Supports plugin internal variables and PAPI variables:

```yaml
# Plugin internal variables
display:
  name: "&a {player}'s Guild"           # Player name
```

```yaml
# PAPI variables
display:
  name: "&a %player_name%"
```

***

### lore - Description Text

Defines the description text of the button icon (multi-line text shown on mouse hover).

**Type:** `List<String>`

**Format:** List, each line corresponds to one line of description

**Example:**

```yaml
display:
  lore:
    - "&7This is a normal button"
    - ""
    - "&e[Left Click] Click to execute action"
```

***

### amount - Item Count

Defines the stack count of the button icon (number displayed at the top-right corner of the item).

**Type:** `Integer`

**Range:** 1-64

**Default:** `1`

**Example:**

```yaml
display:
  amount: 16    # 16 items
```

**Notes:**

* Restricted to 1-64; values outside range are automatically adjusted
* For non-stackable items (e.g., weapons, tools), count always displays as 1
* Can implement dynamic count display with variables

***

### custom_data - Custom Model Data

Defines the custom model data of the item (for texture pack models on versions below 1.21.4).

**Type:** `Integer`

**Format:** Integer (usually provided by texture pack author)

**Example:**

```yaml
display:
  material: DIAMOND_SWORD
  custom_data: 1234
  name: "&aCustom Sword"
```

**Use Cases:**

```yaml
# Custom material weapon
display:
  material: DIAMOND_SWORD
  custom_data: 1001
  name: "&aSword of Light"
```

**Notes:**

* Requires server texture pack
* Custom model data must match the configuration in the texture pack
* On 1.21.4+ versions, `item_model` is recommended instead

***

### item_model - Item Model (1.21.4+)

Defines the custom model of the item (uses new API from 1.21.4+, supports any namespace).

**Type:** `String`

**Format:** `namespace:key`

**Version Requirement:** Minecraft 1.21.4+

**Use Cases:**

```yaml
# Oraxen item
display:
  material: PAPER
  item_model: "oraxen:mana_crystal"
  name: "&bMagic Crystal"
```

```yaml
# Custom texture pack item
display:
  material: DIAMOND_PICKAXE
  item_model: "myserver:epic_pickaxe"
  name: "&dEpic Pickaxe"
```

**Version Compatibility:**

```yaml
# 1.21.4+ version
display:
  item_model: "mynamespace:mykey"
```

```yaml
# Below 1.21.4 version (use custom_data)
display:
  custom_data: 1234
```

**Notes:**

* Requires server version 1.21.4+
* When both are configured, `item_model` takes priority; falls back to `custom_data` if unsupported
* Supports any namespace, including Oraxen, ItemsAdder, etc.
* Automatically ignored on older server versions

***

### glow - Enchantment Glow Effect

Defines whether the item displays an enchantment glow effect (purple aura).

**Type:** `Boolean`

**Default:** `false`

**Example:**

```yaml
display:
  material: DIAMOND_SWORD
  name: "&aGlowing Sword"
  glow: true
```

***

## 🔧 Special List Types

### type - Dynamic List Type

When `type` is set to a special list type, multiple icon items are automatically generated dynamically from the data source, with pagination support.

### Layout Design Reference

```yaml
# Example layout
layout:
  - "#########"
  - "#MMMMMMM#"  # If M = member list, automatically fills 21 character positions
  - "#MMMMMMM#"
  - "#MMMMMMM#"
  - "#<     >#"  # '<' = previous page, '>' = next page
  - "#########"
```
The plugin automatically calculates the number of `M` characters in this example layout and constructs them through the configuration below. If the character count exceeds the list size, the excess displays as air.

### Configuration Template

```yaml
buttons:
  M:
    type: LIST_TYPE  # Replace with the list type to construct
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

Each type has different built-in variables; see the table below for details.

#### 📋 Supported List Types

| Type | Description | Use Case |
|:-----|:------------|:---------|
| `GUILDS_LIST` | Guild list | Display all server guilds |
| `MEMBERS_LIST` | Member list | Display all members of current guild |
| `ALL_PLAYER` | All server player list | Display all online players for invites, etc. |
| `BUFF_LIST` | Buff list | Display purchasable/unlocked buffs |
| `GUILD_VAULTS` | Vault list | Display guild vault unlock status |
| `GUILD_UPGRADE` | Upgrade list | Display guild upgrade options and requirements |
| `TASK_DAILY` | Daily task list | Display current player's daily tasks |
| `TASK_GLOBAL` | Guild task list | Display guild collaborative tasks |

---

### 🏰 GUILDS_LIST - Guild List

Displays a list of all server guilds, each icon representing one guild.

#### Built-in Variables

| Variable | Description | Example Value |
|:---------|:------------|:--------------|
| `{guild_id}` | Guild ID | `5` |
| `{guild_name}` | Guild name | `Star Guild` |
| `{guild_level}` | Guild level | `3` |
| `{guild_members}` | Member count | `15` |
| `{guild_max_members}` | Max members | `20` |
| `{guild_online}` | Online members | `8` |
| `{guild_balance}` | Guild balance | `1500.00` |
| `{guild_announcement}` | Guild announcement | `Welcome!` |
| `{guild_create_time}` | Creation time | `2024-01-15 10:30:00` |
| `{guild_owner}` | Owner's name | `Player1` |
| `{player}` | Viewer's name | `Player2` |
| `{role}` | Viewer's rank | `MEMBER` |
| `{role_node}` | Rank numeric code | `1` (MEMBER=1, ADMIN=2, OWNER=3) |

#### Configuration Example

```yaml
buttons:
  G:
    type: GUILDS_LIST
    display:
      material: BOOK
      name: "&6{guild_name}"
      lore:
        - "&7Level: &f{guild_level}"
        - "&7Members: &f{guild_members}/{guild_max_members}"
        - "&7Online: &a{guild_online}"
        - "&7Balance: &e${guild_balance}"
        - "&7Owner: &f{guild_owner}"
        - "&7Created: &f{guild_create_time}"
        - ""
        - "&8Click for details"
    actions:
      left:
        - "command: guild info {guild_id}"
```

---

### 👥 MEMBERS_LIST - Member List

Displays all members of the current guild, each icon representing one member.

#### Built-in Variables

| Variable | Description | Example Value |
|:---------|:------------|:--------------|
| `{members_name}` | Member name | `Player1` |
| `{members_role}` | Member rank (localized) | `Admin` |
| `{members_contribution}` | Contribution value | `500` |
| `{members_join_time}` | Join time | `2024-01-15 10:30:00` |

#### Features
- Icon is player head, automatically loads corresponding player skin
- Supports executing actions on members through actions (kick, transfer, etc.)

#### Configuration Example

```yaml
buttons:
  M:
    type: MEMBERS_LIST
    display:
      material: PLAYER_HEAD
      name: "&a{members_name}"
      lore:
        - "&7Rank: &f{members_role}"
        - "&7Contribution: &e{members_contribution}"
        - "&7Joined: &f{members_join_time}"
        - ""
        - "&8Left Click: View details"
        - "&8Right Click: Kick member"
    actions:
      left:
        - "tell: Viewed info of member {members_name}"
      right:
        - "command: guild kick {members_name}"
```

---

### 🎮 ALL_PLAYER - All Server Player List

Displays all online players, used for invites and other operations.

#### Built-in Variables

| Variable | Description | Example Value |
|:---------|:------------|:--------------|
| `{player_name}` | Player name | `Player1` |
| `{player_uuid}` | Player UUID | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` |
| `{player_guild_name}` | Player's guild (if any) | `Star Guild` or `None` |

#### Configuration Example

```yaml
buttons:
  P:
    type: ALL_PLAYER
    display:
      material: PLAYER_HEAD
      name: "&a{player_name}"
      lore:
        - "&7UUID: &f{player_uuid}"
        - "&7Guild: &f{player_guild_name}"
        - ""
        - "&8Left Click: Invite to guild"
    actions:
      left:
        - "command: guild invite {player_name}"
```

---

### 🧪 BUFF_LIST - Buff List

Displays purchasable buff list, including unlocked and locked states.

#### Built-in Variables

| Variable | Description | Example Value |
|:---------|:------------|:--------------|
| `{buff_keyname}` | Buff config key | `strength_1` |
| `{buff_name}` | Buff display name | `Strength I` |
| `{buff_price}` | Purchase price | `1000.0` |
| `{buff_time}` | Duration (minutes) | `90` |
| `{buff_status}` | Unlock status (localized) | `Unlocked` / `Locked` |

#### Features
- Locked state: material is `GLASS_BOTTLE` (glass bottle)
- Unlocked state: material is `HONEY_BOTTLE` (honey bottle)
- Status text from language configuration

#### Configuration Example

```yaml
buttons:
  B:
    type: BUFF_LIST
    display:
      material: HONEY_BOTTLE
      name: "&6{buff_name}"
      lore:
        - "&7Status: {buff_status}"
        - "&7Price: &e${buff_price}"
        - "&7Duration: &f{buff_time} minutes"
        - ""
        - "&8Left Click: Purchase/Activate"
    actions:
      left:
        - "command: guild buff {buff_keyname}"
```

---

### 💎 GUILD_VAULTS - Vault List

Displays guild vault unlock status, each icon representing one vault slot.

#### Built-in Variables

| Variable | Description | Example Value |
|:---------|:------------|:--------------|
| `{vault_num}` | Vault number | `1` |
| `{vault_status}` | Unlock status (localized) | `Unlocked` / `Locked` |

#### Features
- Locked state: material is `MINECART` (minecart)
- Unlocked state: material is `CHEST_MINECART` (chest minecart)

#### Configuration Example

```yaml
buttons:
  V:
    type: GUILD_VAULTS
    display:
      material: CHEST_MINECART
      name: "&6Vault #{vault_num}"
      lore:
        - "&7Status: {vault_status}"
        - ""
        - "&8Left Click: Open vault"
    actions:
      left:
        - "command: guild vault {vault_num}"
```

---

### ⬆️ GUILD_UPGRADE - Upgrade List

Displays available guild upgrade options and requirements.

#### Built-in Variables

| Variable | Description | Example Value |
|:---------|:------------|:--------------|
| `{upgrade_level}` | Target level | `4` |
| `{upgrade_max_members}` | Level member limit | `25` |
| `{upgrade_max_money}` | Level balance limit | `50000` |
| `{upgrade_max_vaults}` | Level vault limit | `5` |
| `{upgrade_tp_money}` | Level teleport cost | `50.0` |
| `{upgrade_use_buff}` | Usable buff count | `10` |
| `{upgrade_bank_interest}` | Bank interest type count | `3` |
| `{upgrade_current_exp}` | Current experience | `1500` |
| `{upgrade_need_exp}` | Required experience | `2000` |
| `{upgrade_status}` | Status (localized) | `Can Upgrade` / `Insufficient Experience` / `Locked` |


#### Configuration Example

```yaml
buttons:
  U:
    type: GUILD_UPGRADE
    display:
      material: BOOK
      name: "&6Upgrade to Level {upgrade_level}"
      lore:
        - "&7Status: {upgrade_status}"
        - ""
        - "&7Member Limit: &f{upgrade_max_members}"
        - "&7Balance Limit: &f${upgrade_max_money}"
        - "&7Vault Limit: &f{upgrade_max_vaults}"
        - "&7Teleport Cost: &f${upgrade_tp_money}"
        - "&7Usable Buffs: &f{upgrade_use_buff}"
        - ""
        - "&7Experience: &f{upgrade_current_exp}/{upgrade_need_exp}"
        - ""
        - "&8Left Click: Upgrade guild"
    actions:
      left:
        - "command: guild upgrade"
```

---

### 📋 TASK_DAILY - Daily Task List

Displays the current player's daily task list, tracking individual task progress.

#### Built-in Variables

| Variable | Description | Example Value |
|:---------|:------------|:--------------|
| `{task_key}` | Task config key | `mine_stone_100` |
| `{task_name}` | Task name | `Mine Stone` |
| `{task_lore}` | Task description (multi-line) | `Mine 100 stones in the wild` |
| `{task_progress}` | Current progress | `45` |
| `{task_amount}` | Target amount | `100` |
| `{task_status}` | Completion status (localized) | `Completed` / `Incomplete` |

#### Features
- When `{task_material}` equals `{task_item}`:
  - Completed material: `ENCHANTED_BOOK` (enchanted book)
  - Incomplete material: `BOOK` (book)
- Data tracked individually per player; each player sees different progress

#### Configuration Example

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
        - "&7Progress: &f{task_progress}/{task_amount}"
        - "&7Status: {task_status}"
```

---

### 🌍 TASK_GLOBAL - Guild Task List

Displays the guild's collaborative task list, completed together by all members.

#### Built-in Variables

Variables are the same as `TASK_DAILY`, but data is shared across the entire guild.

#### Configuration Example

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
        - "&7Guild Progress: &f{task_progress}/{task_amount}"
        - "&7Status: {task_status}"
        - ""
        - "&8All members complete this task together"
    actions:
      left:
        - "command: guild claimglobaltask {task_key}"
```

---

## 📝 List Type Configuration Summary

### Pagination Support
All list types automatically support pagination:
- Menu calculates items per page based on the number of list-type slots in the layout
- Use `PAGE_PREV` and `PAGE_NEXT` actions to flip pages
- Each list item's actions can use the list's built-in variables

### Refresh Mechanism
- List data loads when menu is opened
- If real-time updates are needed, set `update` refresh interval in menu configuration
- Refresh fetches the latest data from the database
