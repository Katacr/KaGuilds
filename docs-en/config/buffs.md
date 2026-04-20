# 🍼 Buff Configuration: buffs.yml

The `buffs.yml` configuration file defines buff effects (Buffs) that guilds can purchase and apply. Each Buff has its own price, effect type, level, duration, and other properties.

***

## 📋 Configuration Structure

```yaml
buffs:
  BuffKey:                    # Unique buff identifier (used as command parameter)
    type: POTION_EFFECT_TYPE  # Bukkit potion effect enum name
    price: 1000.0             # Purchase price
    amplifier: 0              # Effect level (0 = Level I, 1 = Level II)
    name: "Display Name"       # Display name in GUI and messages
    time: 120                 # Duration (seconds)
```

***

## 🔧 Configuration Options Explained

### `BuffKey` (Required)

* **Type**: String
* **Description**: Unique identifier for the buff, used as command parameter
* **Examples**: `NightVision`, `Speed`, `Haste`
* **Rules**:
  * Must be unique
  * Recommended: PascalCase naming
  * Can only contain letters and numbers
  * **Important**: This key must be referenced in the `use-buff` list in `levels.yml` to be usable

### `type` (Required)

* **Type**: String
* **Description**: Bukkit potion effect enum name
* **Default**: None
* **Available Options**:

| Enum Name | Display Name | Description |
|:----------|:------------|:------------|
| `SPEED` | Speed | Increases movement speed |
| `SLOW` | Slowness | Decreases movement speed |
| `FAST_DIGGING` | Haste | Increases mining and attack speed |
| `SLOW_DIGGING` | Mining Fatigue | Decreases mining and attack speed |
| `INCREASE_DAMAGE` | Strength | Increases melee damage |
| `DECREASE_DAMAGE` | Weakness | Decreases melee damage |
| `JUMP` | Jump Boost | Increases jump height |
| `NAUSEA` | Nausea | Creates screen distortion effect |
| `REGENERATION` | Regeneration | Restores health |
| `DAMAGE_RESISTANCE` | Resistance | Reduces damage taken |
| `FIRE_RESISTANCE` | Fire Resistance | Immunity to fire damage |
| `WATER_BREATHING` | Water Breathing | Can breathe underwater |
| `INVISIBILITY` | Invisibility | Player becomes invisible |
| `BLINDNESS` | Blindness | Reduces sight distance |
| `NIGHT_VISION` | Night Vision | Clear vision in darkness |
| `HUNGER` | Hunger | Increases hunger consumption |
| `WEAKNESS` | Weakness | Decreases melee damage |
| `POISON` | Poison | Continuous health loss |
| `WITHER` | Wither | Continuous health loss (wither effect) |
| `HEALTH_BOOST` | Health Boost | Increases max health |
| `ABSORPTION` | Absorption | Provides extra shield |
| `SATURATION` | Saturation | Restores hunger and saturation |
| `GLOWING` | Glowing | Player outline visible (can see through walls) |
| `LEVITATION` | Levitation | Floats slowly upward |
| `LUCK` | Luck | Increases loot quality |
| `UNLUCK` | Bad Luck | Decreases loot quality |
| `SLOW_FALLING` | Slow Falling | Slow descent, no fall damage |
| `CONDUIT_POWER` | Conduit Power | Underwater speed, mining, and night vision |
| `DOLPHINS_GRACE` | Dolphin's Grace | Underwater movement speed boost |
| `BAD_OMEN` | Bad Omen | Triggers raid |
| `HERO_OF_THE_VILLAGE` | Hero of the Village | Villager trade discount |

{% hint style="warning" %}
This list is organized by AI; potion effects and names supported by different Minecraft versions may vary.
{% endhint %}

### `price` (Required)

* **Type**: Double
* **Description**: Price deducted from the guild bank to purchase this buff
* **Default**: None
* **Unit**: In-game currency
* **Rules**:
  * Must be greater than 0
  * Decimal values supported

### `amplifier` (Required)

* **Type**: Integer
* **Description**: Effect level (amplifier)
* **Default**: 0
* **Rules**:
  * 0 = Level I effect
  * 1 = Level II effect
  * 2 = Level III effect
  * And so on
* **Examples**:

    ```yaml
    amplifier: 0  # Level I
    ```

    ```yaml
    amplifier: 1  # Level II
    ```

    ```yaml
    amplifier: 2  # Level III
    ```

{% hint style="info" %}
**Tip**: Not all effects support high-level amplifiers. Some effects already reach maximum effect at Level II.
{% endhint %}

### `name` (Required)

* **Type**: String
* **Description**: Display name of the buff shown in GUI and messages
* **Default**: None
* **Rules**:
  * Supports color codes (e.g., `&a`, `&b`)
  * Keep it concise and clear
  * Can include text in any language

### `time` (Required)

* **Type**: Integer
* **Description**: Buff duration
* **Default**: 90
* **Unit**: Seconds
* **Rules**:
  * Must be greater than 0
  * Unit is seconds; server internally converts to ticks (1 second = 20 ticks)
