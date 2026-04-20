# 🎚️ Level Configuration: levels.yml

The `levels.yml` file defines the guild level system, including experience required for upgrades, member limits, vault capacity, available Buffs, and more.

***

## 📁 File Location

```
plugins/KaGuilds/levels.yml
```

***

## 📖 Configuration Structure

```yaml
# KaGuilds Levels Configuration File
# Guild Level Settings - This file contains all level configuration information

levels:
  1:
    need-exp: 0
    max-members: 10
    max-money: 50000
    tp-money: 200
    vaults: 1
    bank-interest: 0.5
    use-buff:
      - NightVision
      - Speed

  2:
    need-exp: 5000
    max-members: 20
    max-money: 100000
    tp-money: 150
    vaults: 2
    bank-interest: 0.6
    use-buff:
      - NightVision
      - Speed
      - Haste
      - Jump

  3:
    need-exp: 10000
    max-members: 30
    max-money: 200000
    tp-money: 100
    vaults: 3
    bank-interest: 0.7
    use-buff:
      - NightVision
      - Speed
      - Haste
      - Jump
      - WaterBreathing
      - Luck

  4:
    need-exp: 20000
    max-members: 40
    max-money: 400000
    tp-money: 60
    vaults: 4
    bank-interest: 0.8
    use-buff:
      - NightVision
      - Speed
      - Haste
      - Jump
      - WaterBreathing
      - Luck
      - Strength
      - Resistance

  5:
    need-exp: 50000
    max-members: 50
    max-money: 600000
    tp-money: 30
    vaults: 5
    bank-interest: 1.0
    use-buff:
      - NightVision
      - Speed
      - Haste
      - Jump
      - WaterBreathing
      - Luck
      - Strength
      - Resistance
      - FireResist
      - Regeneration
      - Absorption
```

***

## 🔧 Configuration Options Explained

### `need-exp`

Experience required to level up to this level.

```yaml
need-exp: 0
```

* **Type**: Integer
* **Description**:
  * Total experience needed for the guild to reach this level
  * Level 1 is typically set to 0 (default level)
  * Subsequent levels increase, e.g., 5000, 10000, 20000...
* **Examples**:

    ```yaml
    levels:
      1:
        need-exp: 0      # Level 1 requires no experience
        #...
      2:
        need-exp: 5000   # 5000 experience to level up from 1 to 2
        #...
      3:
        need-exp: 10000  # 10000 experience to level up from 2 to 3
        #...
    ```

***

### `max-members`

Maximum guild member count at this level.

```yaml
max-members: 10
```

* **Type**: Integer
* **Description**:
  * Maximum number of members the guild can have at this level
  * New members cannot join if this limit is exceeded
  * Usually increases with level

***

### `max-money`

Maximum guild vault capacity at this level.

```yaml
max-money: 50000
```

* **Type**: Integer
* **Description**:
  * Maximum amount the guild vault can hold
  * Cannot deposit more gold once this limit is exceeded
  * Usually increases with level

***

### `tp-money`

Gold required to use guild teleport (deducted from guild vault).

```yaml
tp-money: 200
```

* **Type**: Integer
* **Description**:
  * Fee deducted when member uses `/kg tp` to teleport to guild location
  * Higher levels = lower fee (discount)
  * Deducted from guild vault, not personal wallet

{% hint style="info" %}
If the guild vault balance is insufficient, players cannot use the teleport feature.
{% endhint %}

***

### `vaults`

Number of guild vaults available at this level.

```yaml
vaults: 1
```

* **Type**: Integer
* **Description**:
  * Number of vaults the guild can use
  * Maximum supports 9 vaults
  * Usually increases with level

***

### `bank-interest`

Daily interest rate for the guild vault.

```yaml
bank-interest: 0.5
```

* **Type**: Float
* **Description**:
  * Daily automatic interest added to the guild vault
  * Calculated based on current vault balance
  * 0.5 means 0.5% daily increase
  * Usually increases with level

**Calculation Example**:

```
Vault Balance: 10000 gold
Level: 1 (Interest 0.5%)
Daily Increase: 10000 × 0.005 = 50 gold
New Balance: 10050 gold
```

{% hint style="warning" %}
Interest is calculated at the server's daily task reset time (see `task.reset_time` configuration).
{% endhint %}

***

### `use-buff`

List of usable Buffs at this level.

```yaml
use-buff:
  - NightVision
  - Speed
  - Haste
```

* **Type**: List
* **Description**:
  * Buff types that guild members can purchase
  * Buffs must be defined in `buffs.yml`
  * Usually unlocks more Buffs as level increases

***
