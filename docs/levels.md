# 📊 等级系统配置

`levels.yml` 文件定义了公会的等级系统，包括升级所需经验、成员上限、金库容量、可用的 Buff 等内容。

---

## 📁 文件位置

```
plugins/KaGuilds/levels.yml
```

---

## 📖 配置结构

```yaml
# KaGuilds Levels 配置文件
# 公会等级设置 - 此文件包含所有公会等级的配置信息

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

---

## 🔧 配置选项详解

### `need-exp`
升级到该等级所需的经验值。

```yaml
need-exp: 0
```

- **类型**: 整数
- **说明**:
  - 公会达到此等级需要的总经验
  - 1 级通常设置为 0（默认等级）
  - 后续等级递增，如 5000, 10000, 20000...
- **示例**:
  ```yaml
  levels:
    1:
      need-exp: 0      # 1 级无需经验
      #...
    2:
      need-exp: 5000   # 从 1 级升到 2 级需要 5000 经验
      #...
    3:
      need-exp: 10000  # 从 2 级升到 3 级需要 10000 经验
      #...
  ```

---

### `max-members`
该等级下的公会最大成员数量。

```yaml
max-members: 10
```

- **类型**: 整数
- **说明**:
  - 公会达到此等级后，最多可以拥有的成员数
  - 超过此限制，新成员无法加入
  - 通常随等级提升而增加

---

### `max-money`
该等级下的公会金库最大金币容量。

```yaml
max-money: 50000
```

- **类型**: 整数
- **说明**:
  - 公会金库可以存储的最大金额
  - 超过此限制，无法继续存入金币
  - 通常随等级提升而增加

---

### `tp-money`
使用公会传送所需的金币（从公会金库扣除）。

```yaml
tp-money: 200
```

- **类型**: 整数
- **说明**:
  - 成员使用 `/kg tp` 传送到公会地点时扣除的费用
  - 等级越高，费用越低（优惠）
  - 从公会金库中扣除，不是个人钱包

{% hint style="info" %}
如果公会金库余额不足，玩家无法使用传送功能。
{% endhint %}

---

### `vaults`
该等级下可使用的公会仓库数量。

```yaml
vaults: 1
```

- **类型**: 整数
- **说明**:
  - 公会可以使用的仓库数量
  - 最大支持 9 个仓库
  - 通常随等级提升而增加

---

### `bank-interest`
公会金库每日利息百分比。

```yaml
bank-interest: 0.5
```

- **类型**: 浮点数
- **说明**:
  - 每日自动为公会金库增加利息
  - 基于当前金库余额计算
  - 0.5 表示每天增加 0.5%
  - 通常随等级提升而增加

**计算示例**:
```
金库余额: 10000 金币
等级: 1 (利息 0.5%)
每日增加: 10000 × 0.005 = 50 金币
新余额: 10050 金币
```

{% hint style="warning" %}
利息在服务器每日任务重置时间计算（见 `task.reset_time` 配置）。
{% endhint %}

---

### `use-buff`
该等级下可使用的 Buff 列表。

```yaml
use-buff:
  - NightVision
  - Speed
  - Haste
```

- **类型**: 列表
- **说明**:
  - 公会成员可以购买的 Buff 类型
  - 必须在 `buffs.yml` 中定义过的 Buff
  - 通常随等级提升解锁更多 Buff
---
