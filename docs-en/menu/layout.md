# 🎬 Layout

The first layer of configuration in the menu system determines the overall appearance and layout structure of the menu. Each menu file needs to configure title, layout, and refresh parameters.

## 📋 Configuration Structure

**Example:**

```yaml
title: "KaGuilds!"
layout:
  - "XXXXXXXXX"
  - "X...1...X"
  - "X.11111.X"
  - "X...1...X"
  - "X..1.1..X"
  - "XXXXXXXXX"
buttons:
  1:
    display:
      material: DIAMOND
      name: " "
  X:
    display:
      material: LIME_STAINED_GLASS_PANE
      name: " "
```

**Preview:**

![Layout Preview Example](../.gitbook/assets/layout01.png)

## 🔧 Configuration Options Explained

### title - Menu Title

Defines the title text displayed at the top of the menu GUI.

**Type:** `String`

**Format:** Supports color codes (using `&`) and PlaceholderAPI variables

**Examples:**

```yaml
title: "&aGuild Main Menu"
```

```yaml
title: "&6&l{guild_name} - Member List"
```

```yaml
title: "&eBuff Shop &7(Page &f{page}&7)"
```

**Notes:**

* Title length has a limit; too long titles will be truncated
* Supports PAPI variables: `%player_name%`, `%player_level%`, etc.
* Supports plugin internal variables: `{player}`, `{guild_name}`, `{page}`, etc.

***

### layout - Virtual Layout

Defines the layout structure of the menu, using characters to represent different icon positions.

**Type:** `List<String>`

**Format:** Each line represents one row of the GUI, each line with 9 characters corresponding to 9 slots in the GUI

**Examples:**

```yaml
# 4-row layout (36 slots)
layout:
  - "XXXXXXXXX"  # Row 1
  - "X.......X"  # Row 2
  - "X.......X"  # Row 3
  - "XXXXXXXXX"  # Row 4

```

```yaml
# 6-row layout (54 slots)
layout:
  - "NNNNNNNNN"
  - "N       N"
  - "N A B C N"
  - "N D E F N"
  - "N       N"
  - "NNNNNNNNN"
```

**Character Legend:**

| Character | Description |
|:----------|:------------|
| Space | Empty slot, displays nothing |
| X/A/B/C | Any character, used to mark button positions, corresponding to keys in `buttons` node |

**Important Rules:**

* Each line must be exactly 9 characters
* Number of lines determines GUI size: 1 row = 9 slots, 2 rows = 18 slots, ..., 6 rows = 54 slots
* Space character represents empty slot
* Other characters must have corresponding configuration in the `buttons` node

***

### update - Refresh Interval

Defines the automatic refresh cycle of menu content (in ticks).

**Type:** `Long`

**Unit:** Minecraft tick (1 tick = 0.05 seconds, 20 ticks = 1 second)

**Default:** `0` (no auto-refresh)

**Examples:**

```yaml
# Refresh every 1 second
update: 20
```

```yaml
# Refresh every 5 seconds
update: 100
```

```yaml
# No auto-refresh
update: 0
```

```yaml
# Refresh every 0.5 seconds
update: 10
```

**Refresh Content:**

The refresh feature updates the following:

1. **Dynamic Variables**
   * PAPI variables: `%player_level%`, `%player_health%`, etc.
   * Plugin internal variables: `{balance}`, `{online}`, `{online}`, etc.
2. **Dynamic Lists**
   * Member list (`MEMBERS_LIST`)
   * Guild list (`GUILDS_LIST`)
   * Player list (`ALL_PLAYER`)
   * Buff list (`BUFF_LIST`)
   * Vault list (`GUILD_VAULTS`)
   * Upgrade list (`GUILD_UPGRADE`)
   * Task list (`TASK_DAILY`, `TASK_GLOBAL`)

**Performance Notes:**

* Too short refresh intervals increase server load
* Dynamic list menus (e.g., member list) query data from the database
* Choose appropriate refresh intervals based on actual needs

## 🔄 Reloading Configuration

After modifying menu configuration, reopening the menu will automatically reload it.
