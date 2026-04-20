# 🤖 Actions

The `actions` node defines operations executed when menu buttons are clicked, supporting multiple action types, click types, condition checks, and delayed execution.

## 📋 Configuration Structure

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
        - "tell: You pressed Q"
```

## 🔧 Click Types

The menu supports 4 click types, each can be configured with different action lists:

| Click Type | Key | Description |
|:-----------|:----|:------------|
| Left Click | `left` | Mouse left button click |
| Right Click | `right` | Mouse right button click |
| Shift + Left Click | `shift_left` | Hold Shift + left click |
| Drop Key (Q) | `drop` | Press Q key |

**Example:**

```yaml
actions:
  left:
    - "command: /kg info"      # Execute on left click

  right:
    - "open: members_menu"     # Execute on right click

  shift_left:
    - "console: kg promote {members_name}"  # Execute on Shift+Left click

  drop:
    - "tell: You pressed Q"     # Execute on Q press
```

***

## 🎯 Action Types

### command - Player Command

Makes the player who clicked execute a command.

**Format:** `command: <command>`

**Example:**

```yaml
actions:
  left:
    - "command: kg info"
    - "command: kg bank add 100"
    - "command: msg %player_name% Hello World"
```

**Notes:**

* No need to include `/` prefix in command
* Player must have permission to execute the command
* Supports variable substitution

***

### console - Console Command

Executes a command from the console (with OP permissions).

**Format:** `console: <command>`

**Example:**

```yaml
actions:
  left:
    - "console: give %player_name% diamond 64"
```

**Notes:**

* Executed from console; player needs no permission
* No need to include `/` prefix in command
* Supports variable substitution

***

### tell - Chat Message

Sends a chat message to the player.

**Format:** `tell: <message>`

**Example:**

```yaml
actions:
  left:
    - "tell: &aOperation successful!"
    - "tell: &cOperation failed, contact admin"
    - "tell: &7Current balance: &f{balance}"
```

**Notes:**

* Supports color codes
* Supports variable substitution

***

### actionbar - Action Bar Message

Sends an action bar message to the player (message at the bottom of the screen).

**Format:** `actionbar: <message>`

**Example:**

```yaml
actions:
  left:
    - "actionbar: &aOperation successful!"
    - "actionbar: &7Balance: &f{balance}"
    - "actionbar: &eProcessing..."
```

**Notes:**

* Message displays at the bottom of the screen
* Short duration (~3 seconds)
* Supports color codes and variable substitution

***

### title - Title Message

Sends a title and subtitle to the player.

**Format:** `title: title=Title;subtitle=Subtitle;in=FadeIn;keep=Stay;out=FadeOut`

**Parameter Description:**

| Parameter | Description | Unit | Default |
|:----------|:------------|:-----|:--------|
| `title` | Title text | - | empty |
| `subtitle` | Subtitle text | - | empty |
| `in` | Fade-in time | ticks | 0 |
| `keep` | Stay time | ticks | 60 |
| `out` | Fade-out time | ticks | 20 |

**Example:**

```yaml
actions:
  left:
    - "title: title=&aOperation successful;subtitle=&7Completed"
    - "title: title=&6Guild Upgrade;subtitle=&7Current Level: {level};in=10;keep=80;out=20"
```

**Notes:**

* Parameters separated by `;`
* Supports color codes and variable substitution

***

### sound - Play Sound

Plays a sound at the player's location.

**Format:** `sound: <sound_name>`

**Example:**

```yaml
actions:
  left:
    - "sound: entity.experience_orb.pickup"
    - "sound: entity.player.levelup"
    - "sound: block.note_block.pling"
```

**Notes:**

* `.` in sound names is automatically converted to `_`
* Uses Minecraft vanilla sound names
* Volume and pitch are fixed at 1.0

***

### open - Open Menu

Opens another menu for the player.

**Format:** `open: <menu_name>`

**Example:**

```yaml
actions:
  left:
    - "open: main_menu"
    - "open: members_menu"
    - "open: buffs_menu"
```

**Notes:**

* Menu file must exist in `gui/` directory
* File name does not need to include `.yml` extension
* Current menu closes automatically

***

### close - Close Menu

Closes the currently open menu.

**Format:** `close`

**Example:**

```yaml
actions:
  left:
    - "close"
    - "tell: Menu closed"
```

**Notes:**

* No parameters needed
* Usually used together with other actions

***

### update - Refresh Menu

Reloads and refreshes the current menu content.

**Format:** `update`

**Example:**

```yaml
actions:
  left:
    - "update"
```

**Notes:**

* No parameters needed
* Rebuilds all buttons
* Used to update dynamic content

***

### PAGE_NEXT - Next Page

Goes to the next page of the menu.

**Format:** `PAGE_NEXT`

**Example:**

```yaml
actions:
  left:
    - "PAGE_NEXT"
    - "sound: block.note_block.pling"
```

**Notes:**

* Only applicable to paginated menus (member list, guild list, etc.)
* No effect if already on the last page

***

### PAGE_PREV - Previous Page

Goes to the previous page of the menu.

**Format:** `PAGE_PREV`

**Example:**

```yaml
actions:
  left:
    - "PAGE_PREV"
    - "sound: block.note_block.pling"
```

**Notes:**

* Only applicable to paginated menus (member list, guild list, etc.)
* No effect if already on the first page

***

### catcher - Chat Input Capture

Captures player chat input and executes the specified command.

**Format:** `catcher: <type>`

**Supported Capture Types:**

| Type | Purpose | Description |
|:-----|:--------|:------------|
| `bank_add` | Donate to guild bank | Captures input and executes `kg bank add <input>` |
| `bank_take` | Withdraw from guild bank | Captures input and executes `kg bank take <input>` |
| `guild_rename` | Rename guild | Captures input and executes `kg rename <input>` |
| `guild_create` | Create guild | Captures input and executes `kg create <input>` |
| `edit_motd` | Edit guild announcement | Captures input and executes `kg motd <input>` |

**Example:**

```yaml
actions:
  left:
    - "catcher: bank_add"
    - "tell: &7Please enter the amount in chat, type cancel to cancel"
```

**Player Input Example:**

```
Player types in chat: 100
=> Auto executes: /kg bank add 100
```

**Notes:**

* Typing `cancel` cancels the capture
* Menu closes automatically
* Takes priority over guild chat

***

### hovertext - Clickable Text

Sends a chat message with hover and click functionality.

**Format:** `hovertext: <text='display text';hover='hover text';command='command';newline='false'>`

**Parameter Description:**

| Parameter | Description | Required |
|:----------|:------------|:---------|
| `text` | Display text | Yes |
| `hover` | Text shown on hover | No |
| `command` | Command executed on click | No |
| `newline` | Whether to add newline (true/false) | No |

**Example:**

```yaml
actions:
  left:
    - "hovertext: Hello, please <text=`&a[Click Here]`;hover=`&7Click to execute`;command=`/kg info`;newline=`false`> to execute."
```

**Notes:**

* Use backticks `` ` `` to wrap parameter values
* Parameters separated by `;`
* Button text wrapped in `< >`
* Supports color codes and variable substitution

***

### wait - Delayed Execution

Delays execution of subsequent actions.

**Format:** `wait: <tick_count>`

**Example:**

```yaml
actions:
  left:
    - "tell: &aStarting execution..."
    - "wait: 40"  # Delay 2 seconds
    - "tell: &bExecution complete!"
```

**Notes:**

* Unit is ticks (1 tick = 0.05 seconds, 20 ticks = 1 second)
* Only affects actions after `wait`
* Does not delay other simultaneously executing actions

***

## 🔍 Condition Checks

### Basic Syntax

Use condition checks to execute different actions based on player status.

**Format:**

```yaml
actions:
  left:
    - condition: "condition_expression"
      actions:
        - "actions to execute when condition is met"
      deny:
        - "actions to execute when condition is not met"
```

### Condition Expression Syntax

Supports comparison operators and logical operators.

**Comparison Operators:**

| Operator | Description | Example |
|:---------|:------------|:--------|
| `==` | Equal to | `%player_level% == 10` |
| `!=` | Not equal to | `%player_level% != 10` |
| `>` | Greater than | `%player_health% > 10` |
| `<` | Less than | `%player_health% < 10` |
| `>=` | Greater than or equal | `%player_level% >= 10` |
| `<=` | Less than or equal | `%player_level% <= 10` |

**Logical Operators:**

| Operator | Description | Priority |
|:---------|:------------|:---------|
| `&&` | Logical AND | High |
| `\|\|` | Logical OR | High |
| `()` | Parentheses (change priority) | Highest |

**Examples:**

```yaml
# Single condition
actions:
  left:
    - condition: "%player_level% >= 10"
      actions:
        - "tell: &aYour level is 10 or above!"
      deny:
        - "tell: &cYour level is insufficient!"
```

```yaml
# Multiple conditions (AND)
actions:
  left:
    - condition: "%player_level% >= 10 && %player_level% < 20"
      actions:
        - "tell: &aYou are a mid-level player between level 10~20!"
      deny:
        - "tell: &cYou are not a mid-level player!"
```

```yaml
# Multiple conditions (OR)
actions:
  left:
    - condition: "%player_level% < 10 || %player_level% > 20"
      actions:
        - "tell: &aYou are a beginner below level 10 or a senior above level 20!"
```

```yaml
# Using parentheses
actions:
  left:
    - condition: "(%player_level% >= 10 && %player_level% < 20) || %player_has_permission% == true"
      actions:
        - "tell: &aYour level is between 10~20, or you have permission!"
```

***

## 📝 Complete Configuration Examples

### Example 1: Basic Button

```yaml
buttons:
  X:
    display:
      material: DIAMOND_SWORD
      name: "&aExecute Command"
    actions:
      left:
        - "command: kg info"
        - "sound: entity.experience_orb.pickup"
      right:
        - "open: main_menu"
```

### Example 2: Condition Check

```yaml
buttons:
  X:
    display:
      material: BOOK
      name: "&aPromote Rank"
    actions:
      left:
        - condition: "{role_node} >= 2"
          actions:
            - "command: kg promote {members_name}"
            - "tell: &aPromoted {members_name}"
          deny:
            - "tell: &cYou don't have permission to promote members!"
```

### Example 3: Input Capture

```yaml
buttons:
  X:
    display:
      material: GOLD_INGOT
      name: "&6Deposit to Vault"
    actions:
      left:
        - "catcher: bank_add"
        - "tell: &7Please enter the amount in chat"
        - "tell: &7Type cancel to cancel"
        - "close"
```

### Example 4: Multiple Click Types

```yaml
buttons:
  X:
    display:
      material: PLAYER_HEAD
      name: "&eMember Operations"
    actions:
      left:
        - "tell: &7View member info"
      right:
        - "open: member_info_menu"
      shift_left:
        - "command: kg promote {members_name}"
        - "tell: &aRank promoted"
      drop:
        - "tell: &cYou pressed Q"
```

### Example 5: Delayed Execution

```yaml
buttons:
  X:
    display:
      material: BOOK
      name: "&aDelayed Operation"
    actions:
      left:
        - "tell: &aOperation starting..."
        - "wait: 40"
        - "tell: &bStep 1 complete..."
        - "wait: 40"
        - "tell: &eStep 2 complete..."
        - "wait: 40"
        - "tell: &cAll operations complete!"
```

***
