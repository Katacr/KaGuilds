# Buffs 配置说明

`buffs.yml` 配置文件用于定义公会可以购买和应用的增益效果（Buff）。每个 Buff 都有自己的价格、效果类型、等级、持续时间等属性。

---

## 📋 配置结构

```yaml
buffs:
  BuffKey:                    # Buff 唯一标识符（用于命令参数）
    type: POTION_EFFECT_TYPE  # Bukkit 药水效果枚举名
    price: 1000.0             # 购买价格
    amplifier: 0              # 效果等级 (0 = I级, 1 = II级)
    name: "显示名称"          # GUI 和消息中显示的名称
    time: 120                 # 持续时间（秒）
```

---

## 🔧 配置选项详解

### `BuffKey`（必需）
- **类型**: String
- **描述**: Buff 的唯一标识符，用作命令参数
- **示例**: `NightVision`, `Speed`, `Haste`
- **规则**:
    - 必须唯一
    - 建议使用大驼峰命名（PascalCase）
    - 只能包含字母和数字
    - **重要**: 此 Key 必须在 `levels.yml` 的 `use-buff` 列表中引用才能被使用

### `type`（必需）
- **类型**: String
- **描述**: Bukkit 药水效果枚举名称
- **默认值**: 无
- **可用选项**:

| 枚举名 | 中文名 | 说明 |
|-------|--------|------|
| `SPEED` | 疾跑 | 提升移动速度 |
| `SLOW` | 缓慢 | 降低移动速度 |
| `FAST_DIGGING` | 急迫 | 提升挖掘和攻击速度 |
| `SLOW_DIGGING` | 挖掘疲劳 | 降低挖掘和攻击速度 |
| `INCREASE_DAMAGE` | 力量 | 提升近战伤害 |
| `DECREASE_DAMAGE` | 虚弱 | 降低近战伤害 |
| `JUMP` | 跳跃提升 | 提升跳跃高度 |
| `NAUSEA` | 反胃 | 产生屏幕扭曲效果 |
| `REGENERATION` | 生命恢复 | 恢复生命值 |
| `DAMAGE_RESISTANCE` | 抗性提升 | 减少受到的伤害 |
| `FIRE_RESISTANCE` | 防火 | 免疫火焰伤害 |
| `WATER_BREATHING` | 水下呼吸 | 可以在水下呼吸 |
| `INVISIBILITY` | 隐身 | 玩家隐形 |
| `BLINDNESS` | 失明 | 降低视野距离 |
| `NIGHT_VISION` | 夜视 | 在黑暗中视野清晰 |
| `HUNGER` | 饥饿 | 加快饥饿消耗 |
| `WEAKNESS` | 虚弱 | 降低近战伤害 |
| `POISON` | 中毒 | 持续扣血 |
| `WITHER` | 凋零 | 持续扣血，类似凋零效果 |
| `HEALTH_BOOST` | 生命提升 | 增加最大生命值 |
| `ABSORPTION` | 伤害吸收 | 提供额外护盾 |
| `SATURATION` | 饱和 | 恢复饥饿值和饱和度 |
| `GLOWING` | 发光 | 玩家轮廓可见（可穿墙） |
| `LEVITATION` | 漂浮 | 缓慢向上漂浮 |
| `LUCK` | 幸运 | 增加战利品品质 |
| `UNLUCK`` | 霉运 | 降低战利品品质 |
| `SLOW_FALLING` | 缓降 | 缓慢下落，无摔落伤害 |
| `CONDUIT_POWER` | 潮涌能量 | 水下速度、挖掘和夜视 |
| `DOLPHINS_GRACE`` | 海豚的恩惠` | 水下移动加速 |
| `BAD_OMEN`` | 不祥之兆` | 触发劫掠 |
| `HERO_OF_THE_VILLAGE`` | 村庄英雄` | 村民交易折扣 |

---
{% hint style="warning" %}
该列表由AI整理，不同 Minecraft 版本支持的药水效果和名称可能不同。
{% endhint %}

### `price`（必需）
- **类型**: Double
- **描述**: 购买此 Buff 需要从公会金库扣除的价格
- **默认值**: 无
- **单位**: 游戏货币
- **规则**:
    - 必须大于 0
    - 支持小数


### `amplifier`（必需）
- **类型**: Integer
- **描述**: 效果等级（放大器）
- **默认值**: 0
- **规则**:
    - 0 = I 级效果
    - 1 = II 级效果
    - 2 = III 级效果
    - 以此类推
- **示例**:
  ```yaml
  amplifier: 0  # I 级
  ```
  ```yaml
  amplifier: 1  # II 级
  ```
  ```yaml
  amplifier: 2  # III 级
  ```

{% hint style="info" %}
**提示**: 不是所有效果都支持高等级放大器。部分效果在 II 级时已经达到最大效果。
{% endhint %}

### `name`（必需）
- **类型**: String
- **描述**: Buff 在 GUI 和消息中显示的名称
- **默认值**: 无
- **规则**:
    - 支持颜色代码（如 `&a`, `&b`）
    - 建议简洁明了
    - 可包含中文


### `time`（必需）
- **类型**: Integer
- **描述**: Buff 持续时间
- **默认值**: 90
- **单位**: 秒
- **规则**:
    - 必须大于 0
    - 单位为秒，服务器内部会转换为 tick（1 秒 = 20 tick）

