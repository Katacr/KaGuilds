# 🚀 快速开始

本指南将帮助你快速安装和配置 KaGuilds 插件。

---

## 🎮 系统要求

| 项目 | 最低要求 | 推荐配置 |
|------|---------|---------|
| Minecraft 版本 | 1.16.1 | 1.20.4+ |
| Java 版本 | Java 8 | Java 12+ |
| 服务器类型 | Spigot/Paper | Paper |
| 代理服务器（可选） | BungeeCord | Velocity |
| 数据库 | SQLite | MySQL 5.7+ |

---

## 📥 安装步骤

### 1. 下载插件

从 GitHub Releases 下载最新版本的 KaGuilds 插件：

{% embed url="https://github.com/Katacr/KaGuilds/releases" %}

### 2. 安装依赖

KaGuilds 需要以下依赖插件才能正常工作：

**必需依赖：**
- [Vault](https://www.spigotmc.org/resources/vault.34315/) - 经济系统支持

**可选依赖：**
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) - 占位符支持

下载这些插件并将它们的 `.jar` 文件放入服务器的 `plugins` 文件夹中。

### 3. 安装插件

1. 将下载的 KaGuilds `.jar` 文件放入服务器的 `plugins` 文件夹
2. 启动服务器
3. 插件会自动创建配置文件和数据库表
   {% hint style="info" %}
   请勿使用插件加载器(如PlunMan)加载或重载KaGuilds，否则可能遇到未知错误。
   {% endhint %}
### 4. 配置数据库

编辑 `plugins/KaGuilds/config.yml` 文件，配置数据库连接：

```yaml
database:
  # 数据库类型: MySQL 或 SQLite
  type: "MySQL"  # 单服使用可选 SQLite
  host: "localhost"
  port: 3306
  db: "kaguilds"
  user: "root"
  password: "your_password"
```

**MySQL 配置：**
- 适合跨服架构，数据共享更方便
- 需要先在 MySQL 中创建数据库

**SQLite 配置：**
- 适合单服使用，配置简单
- 不需要额外的数据库服务器
- 数据文件位于 `plugins/KaGuilds/data.db`

### 5. 配置语言

编辑 `config.yml` 设置界面语言：

```yaml
language: zh_CN  # 可选: zh_CN 或 en_US
```

### 6. 重启服务器

完成配置后，重启服务器以加载所有设置：

```bash
# Linux/Mac
./stop.sh
./start.sh

# Windows
双击 stop.bat
双击 start.bat
```

启动后，查看控制台确认插件加载成功：

```
[KaGuilds] 插件已成功加载！
[KaGuilds] 数据库连接成功
[KaGuilds] 已加载语言文件: zh_CN.yml
```

---

## 🌐 跨服配置

如果你使用 BungeeCord 或 Velocity 代理服务器，需要进行额外配置：

### BungeeCord 配置

1. **确保所有子服都安装了 KaGuilds**
2. **在每个子服的 `config.yml` 中设置：**

```yaml
proxy: true
server-id: "survival"  # 每个子服设置不同的 ID
```

**示例配置：**

```yaml
# 子服 1 (生存服)
proxy: true
server-id: "survival"

# 子服 2 (创造服)
proxy: true
server-id: "creative"

# 子服 3 (小游戏服)
proxy: true
server-id: "minigames"
```

### Velocity 配置

Velocity 的配置与 BungeeCord 相同，只需按照上述步骤配置每个子服即可。

### 消息通道

插件会自动使用默认的消息通道进行跨服通信：

```yaml
# 默认消息通道名称（config.yml 中配置）
# 无需手动修改，插件会自动处理
```

如需自定义消息通道名称，可以在 `config.yml` 中修改。

---

## ✅ 验证安装

安装完成后，可以通过以下方式验证插件是否正常工作：

### 1. 检查控制台

查看服务器启动日志，确认没有错误信息：

```
[KaGuilds] 插件已成功启用
[KaGuilds] 数据库连接成功
[KaGuilds] 已加载 15 个等级配置
[KaGuilds] 已加载 10 个 Buff 配置
[KaGuilds] 已加载 20 个任务配置
```

### 2. 测试基本命令

在游戏中输入以下命令测试：

```bash
/kg help        # 查看帮助信息
/kg menu        # 打开主菜单
/kg create 测试  # 创建测试公会（需要足够的金钱）
```

### 3. 检查数据库

连接到数据库，确认表已创建：

```sql
-- MySQL
USE kaguilds;
SHOW TABLES;

-- 应该看到以下表：
-- guilds
-- members
-- bank_transactions
-- pvp_stats
-- task_progress
-- guild_vaults
```

---

## ⚙️ 初始配置

插件首次运行后，需要进行一些基本配置：

### 配置公会费用

编辑 `config.yml` 设置各项费用：

```yaml
balance:
  create: 10000.0   # 创建公会费用
  rename: 3000.0   # 重命名费用
  settp: 1000.0     # 设置传送点费用
  seticon: 1000.0   # 设置图标费用
  setmotd: 100.0    # 设置公告费用
  pvp: 300.0        # 发起公会战费用
```

### 配置公会限制

编辑 `config.yml` 设置公会限制：

```yaml
guild:
  name-settings:
    min-length: 3        # 公会名称最小长度
    max-length: 16       # 公会名称最大长度
    regex: "[\\u4e00-\\u9fa5a-zA-Z0-9]+"  # 名称正则表达式
```

### 配置语言文件

编辑 `lang/zh_CN.yml` 自定义界面文本：

```yaml
prefix: "&7[&6KaGuilds&7] &f"
guild-created: "&a成功创建公会 &e{name}!"
guild-not-found: "&c公会不存在！"
```

---

## 🐛 常见问题

### 插件无法加载

**问题：** 插件启动时报错

**解决方案：**
1. 检查 Java 版本是否满足要求（最低 Java 8）
2. 确认 Vault 插件已正确安装
3. 查看控制台错误信息，检查配置文件格式

### 数据库连接失败

**问题：** 控制台显示数据库连接错误

**解决方案：**
1. 检查 MySQL 服务是否正在运行
2. 确认数据库连接信息（host、port、user、password）正确
3. 确保数据库用户有足够的权限
4. 尝试使用 SQLite 数据库进行测试

### 跨服功能不工作

**问题：** 跨服聊天、邀请等功能无法使用

**解决方案：**
1. 确认所有子服都启用了 `proxy: true`
2. 检查 `server-id` 是否在每个子服上都是唯一的
3. 确认代理服务器（BungeeCord/Velocity）正常运行
4. 检查防火墙设置

### 权限问题

**问题：** 玩家无法使用某些命令

**解决方案：**
1. 确认权限插件（如 LuckPerms）已正确配置
2. 检查玩家是否拥有 `kaguilds.use` 权限
3. 查看权限文档了解详细的权限配置

---

## 📞 获取帮助

如果遇到问题无法解决，可以通过以下方式获取帮助：

- **GitHub Issues**: [提交问题](https://github.com/Katacr/KaGuilds/issues)
- **社区支持**: [加入我们的社区](https://github.com/Katacr/KaGuilds)

{% hint style="warning" %}
注意：只有购买插件的用户才会受到技术支持。
{% endhint %}

---

## 🎉 安装完成

恭喜！你已经成功安装并配置了 KaGuilds 插件。现在可以开始创建公会，体验丰富的公会功能了！

下一步，你可以：
- 查看 [配置文件文档](../configuration/) 了解更多配置选项
- 查看 [命令参考](../commands/) 学习所有可用命令
- 查看 [GUI 菜单文档](../gui/) 自定义你的公会菜单

开始你的公会之旅吧！🚀
