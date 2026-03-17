# 图标显示配置

`display` 节点用于定义菜单中每个按钮（图标）的视觉外观，包括材质、名称、描述、数量、自定义模型等属性。

## 📋 配置结构

```yaml
buttons:
  X:
    display:
      material: DIAMOND_SWORD
      name: "&a我的剑"
      lore:
        - "&7一把锋利的剑"
        - "&7攻击力: 10"
      amount: 1
      custom_data: 1234
      item_model: "mynamespace:mykey"
      glow: false
    actions:
      left:
        - "command: say hello"
```

## 🔧 配置选项详解

### material - 物品材质

定义按钮图标的 Minecraft 物品类型。

**类型:** `String`

**格式:** Minecraft 材质名称（支持大写、小写或混合）

**示例:**

```yaml
display:
  material: DIAMOND_SWORD          # 钻石剑
```

---

### name - 显示名称

定义按钮图标的显示名称（鼠标悬停时显示）。

**类型:** `String`

**格式:** 支持颜色代码（使用 `&` 符号）和占位符变量

**示例:**

```yaml
display:
  name: "&6&l 公会设置"      
```

**占位符变量:**

支持插件内部变量和 PAPI 变量:

```yaml
# 插件内部变量
display:
  name: "&a {player} 的公会"           # 玩家名
```
```yaml
# PAPI 变量
display:
  name: "&a %player_name%"
```

---

### lore - 描述文本

定义按钮图标的描述文本（鼠标悬停时显示的多行文本）。

**类型:** `List<String>`

**格式:** 列表，每行对应一行描述文本

**示例:**

```yaml
display:
  lore:
    - "&7这是一个普通的按钮"
    - ""
    - "&e[左键] 点击执行操作"
```
---

### amount - 物品数量

定义按钮图标的堆叠数量（显示在物品右上角的数字）。

**类型:** `Integer`

**范围:** 1-64

**默认值:** `1`

**示例:**

```yaml
display:
  amount: 16    # 16 个物品
```

**注意事项:**
- 会被限制在 1-64 之间，超出范围会被自动调整
- 对于不可堆叠的物品（如武器、工具），数量始终显示为 1
- 可以配合变量实现动态数量显示

---

### custom_data - 自定义模型数据

定义物品的自定义模型数据（用于 1.21.4 以下版本的资源包模型）。

**类型:** `Integer`

**格式:** 整数（通常由资源包作者提供）

**示例:**

```yaml
display:
  material: DIAMOND_SWORD
  custom_data: 1234
  name: "&a自定义剑"
```

**使用场景:**

```yaml
# 自定义材质的武器
display:
  material: DIAMOND_SWORD
  custom_data: 1001
  name: "&a光之剑"
```


**注意事项:**
- 需要配合服务器资源包使用
- 自定义模型数据必须与资源包中的配置一致
- 在 1.21.4+ 版本中，建议使用 `item_model` 替代

---

### item_model - 物品模型 (1.21.4+)

定义物品的自定义模型（使用 1.21.4+ 的新 API，支持任何命名空间）。

**类型:** `String`

**格式:** `namespace:key`

**版本要求:** Minecraft 1.21.4+

**使用场景:**

```yaml
# Oraxen 物品
display:
  material: PAPER
  item_model: "oraxen:mana_crystal"
  name: "&b魔法水晶"
```
```yaml
# 自定义资源包物品
display:
  material: DIAMOND_PICKAXE
  item_model: "myserver:epic_pickaxe"
  name: "&d史诗镐"
```

**版本兼容性:**

```yaml
# 1.21.4+ 版本
display:
  item_model: "mynamespace:mykey"
```
```yaml
# 1.21.4 以下版本（使用 custom_data）
display:
  custom_data: 1234
```

**注意事项:**
- 需要 1.21.4+ 的服务器版本
- 同时配置两者时，优先使用 item_model，不支持则降级到 custom_data。
- 支持任何命名空间，包括 Oraxen、ItemsAdder 等插件
- 在旧版本服务器上使用时，会自动忽略此配置

---

### glow - 附魔发光效果

定义物品是否显示附魔发光效果（紫色光晕）。

**类型:** `Boolean`

**默认值:** `false`

**示例:**

```yaml
display:
  material: DIAMOND_SWORD
  name: "&a发光剑"
  glow: true
```

---

