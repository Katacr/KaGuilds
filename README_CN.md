# 🛡️ KaGuilds - 高性能跨服公会系统

[![许可证](https://img.shields.io/github/license/katacr/KaGuilds)](LICENSE) 
[![版本](https://img.shields.io/badge/version-1.1.0-blue)](https://github.com/katacr/KaGuilds) 
[![Minecraft](https://img.shields.io/badge/Minecraft-1.16%2B-brightgreen)](https://www.minecraft.net/) 

**KaGuilds** 是一款专为 Minecraft 网络服务器（BungeeCord/Velocity）设计的深度定制公会插件。它利用 **SQL 事务**和**跨服消息总线**确保在分布式环境下的数据一致性和安全性。

---

## ✨ 核心特性

### 🔄 跨服同步
- 基于 Velocity/BungeeCord 消息通道的全局公会聊天、通知和邀请
- 无论成员在哪个子服，实时同步公会信息和权限
- 跨服玩家邀请和加入申请

### 💰 深度经济整合
- **创建费用**：通过 Vault API 自动扣除的可配置费用
- **公会金库**：支持存取款，并为管理员提供带翻页功能的交易日志
- **仓库系统**：支持多个可锁定的公会仓库，具有库存租期管理功能

### 👥 严谨的职位体系
- 三级权限结构：**会长 (Owner)**、**管理员 (Administrator)**和**成员 (Member)**
- 内置晋升和降职逻辑，权限边界清晰
- 支持所有权转移

### 📈 动态等级系统
- 基于公会经验自动升级
- 动态增加成员上限和金库容量
- 具有可配置等级的升级系统

### 📋 完善的邀请与申请系统
- **邀请**：全局玩家邀请，具备自动过期机制
- **申请**：管理员可随时查看、批准或拒绝待处理的入会申请

### 🎮 PvP 竞技场系统
- 公会对战，配备自定义装备包
- 比赛计时和获胜条件
- 统计追踪（胜/负/平局）
- 获胜后执行奖励命令

### 🖼️ GUI 菜单系统
- 带有公会操作的主菜单
- 带翻页功能的成员列表
- 公会列表浏览器
- 增益商店
- 升级商店
- 金库管理

### 📊 PlaceholderAPI 支持
为记分板或聊天集成的变量：
- `%kaguilds_name%` - 当前公会名称
- `%kaguilds_role%` - 玩家职位（会长/管理员/成员）
- `%kaguilds_level%` - 当前公会等级
- `%kaguilds_balance%` - 当前公会资金

### 🌍 本地化
在 `config.yml` 中完全支持语言切换：
- **简体中文**：`zh_CN.yml`
- **English**：`en_US.yml`

---

## 🎮 命令参考

### 玩家命令

| 命令                       | 说明             | 权限                          |
|--------------------------|----------------|-----------------------------|
| `/kg create <名称>`        | 支付创建新公会        | `kaguilds.command.create`   |
| `/kg join <名称>`          | 申请加入指定公会       | `kaguilds.command.join`     |
| `/kg info [名称]`          | 查看公会详细信息       | `kaguilds.command.info`     |
| `/kg chat <消息>`          | 在公会内部频道交流      | `kaguilds.command.chat`     |
| `/kg bank <add/get/log>` | 向公金库存/取款或查看日志  | `kaguilds.command.bank`     |
| `/kg leave`              | 退出当前公会         | `kaguilds.command.leave`    |
| `/kg tp`                 | 传送到公会位置        | `kaguilds.command.tp`       |
| `/kg settp`              | 设置公会传送位置       | `kaguilds.command.settp`    |
| `/kg rename <名称>`        | 重命名公会（需确认）     | `kaguilds.command.rename`   |
| `/kg vault <索引>`         | 打开公会仓库（1-9）    | `kaguilds.command.vault`    |
| `/kg motd <消息>`          | 设置公会公告         | `kaguilds.command.motd`     |
| `/kg seticon`            | 从手持物品设置公会图标    | `kaguilds.command.seticon`  |
| `/kg upgrade`            | 升级公会等级         | `kaguilds.command.upgrade`  |
| `/kg buff <名称>`          | 购买公会增益         | `kaguilds.command.buff`     |
| `/kg pvp <公会>`           | 向其他公会发起 PvP 挑战 | `kaguilds.command.pvp`      |
| `/kg yes`                | 接受公会邀请         | `kaguilds.command.yes`      |
| `/kg no`                 | 拒绝公会邀请         | `kaguilds.command.no`       |
| `/kg confirm`            | 确认待处理操作        | `kaguilds.command.confirm`  |
| `/kg menu`               | 打开公会主菜单        | `kaguilds.command.menu`     |
| `/kg help [页码]`          | 查看命令帮助菜单       | `kaguilds.command.help`     |
| `/kg invite <玩家>`        | 邀请在线/跨服玩家      | `kaguilds.command.invite`   |
| `/kg accept <玩家>`        | 批准入会申请         | `kaguilds.command.accept`   |
| `/kg deny <玩家>`          | 拒绝入会申请         | `kaguilds.command.deny`     |
| `/kg promote <玩家>`       | 将成员提升为管理员      | `kaguilds.command.promote`  |
| `/kg demote <玩家>`        | 将管理员降职为成员      | `kaguilds.command.demote`   |
| `/kg kick <玩家>`          | 将成员踢出公会        | `kaguilds.command.kick`     |
| `/kg transfer <玩家>`      | 转让公会所有权        | `kaguilds.command.transfer` |
| `/kg bank log [页码]`      | 查看交易历史         | `kaguilds.command.bank`     |
| `/kg delete`             | 删除公会（仅会长）      | `kaguilds.command.delete`   |
| `/kg requests`           | 查看所有入会申请       | `kaguilds.command.requests` |
| `/kg confirm`            | 确认公会删除或转让      | `kaguilds.command.confirm`  |

### 管理员命令

| 命令                                              | 说明         | 权限                         |
|-------------------------------------------------|------------|----------------------------|
| `/kg admin rename <公会> <名称>`                    | 管理员重命名公会   | `kaguilds.admin.rename`    |
| `/kg admin delete <公会>`                         | 管理员删除公会    | `kaguilds.admin.delete`    |
| `/kg admin info <公会>`                           | 管理员查看公会信息  | `kaguilds.admin.info`      |
| `/kg admin bank <公会> <see/log/add/remove/set>`  | 管理公会金库     | `kaguilds.admin.bank`      |
| `/kg admin exp <公会> <add/remove/set> <金额>`      | 修改公会经验值    | `kaguilds.admin.exp`       |
| `/kg admin setlevel <公会> <等级>`                  | 设置公会等级     | `kaguilds.admin.setlevel`  |
| `/kg admin kick <公会> <玩家>`                      | 管理员踢出成员    | `kaguilds.admin.kick`      |
| `/kg admin join <公会> <玩家>`                      | 强制添加玩家到公会  | `kaguilds.admin.join`      |
| `/kg admin transfer <公会> <玩家>`                  | 管理员转移所有权   | `kaguilds.admin.transfer`  |
| `/kg admin vault <公会> <索引>`                     | 管理员打开仓库    | `kaguilds.admin.vault`     |
| `/kg admin unlockall`                           | 强制重置所有仓库锁定 | `kaguilds.admin.unlockall` |
| `/kg admin arena <setpos/setspawn/setkit/info>` | 配置 PvP 竞技场 | `kaguilds.admin.arena`     |
| `/kg reload`                                    | 重载配置文件     | `kaguilds.admin.reload`    | 

---

## 📋 权限

### 基础权限
```yaml
kaguilds.use            # 访问所有基础公会功能
kaguilds.admin          # 访问所有管理员命令
```

### 命令特定权限
```yaml
kaguilds.command.main    # 访问主公会命令
kaguilds.command.help    # 查看命令帮助
kaguilds.command.menu    # 打开主菜单 GUI
```

### 玩家命令
```yaml
kaguilds.command.create  # 创建新公会
kaguilds.command.join    # 申请加入公会
kaguilds.command.info    # 查看公会信息
kaguilds.command.leave   # 退出当前公会
kaguilds.command.tp      # 传送到公会
kaguilds.command.settp   # 设置公会传送点
kaguilds.command.chat    # 发送公会聊天消息
kaguilds.command.bank    # 访问公会金库
kaguilds.command.vault   # 访问公会仓库
kaguilds.command.motd    # 设置公会公告
kaguilds.command.rename  # 重命名公会
kaguilds.command.seticon # 设置公会图标
kaguilds.command.upgrade # 升级公会等级
kaguilds.command.buff    # 购买公会增益
kaguilds.command.pvp     # 访问 PvP 系统
kaguilds.command.yes     # 接受公会邀请
kaguilds.command.no      # 拒绝公会邀请
kaguilds.command.confirm # 确认待处理操作
kaguilds.command.invite  # 邀请玩家
kaguilds.command.accept  # 批准入会申请
kaguilds.command.deny    # 拒绝入会申请
kaguilds.command.promote # 晋升成员
kaguilds.command.demote  # 降职管理员
kaguilds.command.kick    # 踢出成员
kaguilds.command.transfer # 转让所有权
kaguilds.command.delete  # 删除公会
kaguilds.command.requests # 查看入会申请
```

### 管理员命令
```yaml
kaguilds.admin.rename   # 管理员重命名公会
kaguilds.admin.delete   # 管理员删除公会
kaguilds.admin.info     # 管理员查看公会信息
kaguilds.admin.bank     # 管理员管理金库
kaguilds.admin.exp      # 管理员修改经验值
kaguilds.admin.setlevel # 管理员设置等级
kaguilds.admin.kick     # 管理员踢出成员
kaguilds.admin.join     # 管理员添加成员
kaguilds.admin.transfer # 管理员转让所有权
kaguilds.admin.vault    # 管理员打开仓库
kaguilds.admin.unlockall # 强制解锁所有仓库
kaguilds.admin.arena    # 配置 PvP 竞技场
kaguilds.admin.reload   # 重载插件配置
```

---


## 🔧 配置

### config.yml
- 数据库连接设置
- 经济设置（创建费用、等级费用）
- 公会限制（最大成员数、最大仓库数）
- PvP 设置（比赛时长、竞技场配置）
- 消息通道名称

### GUI 文件
- `main_menu.yml` - 公会主菜单
- `guilds_list.yml` - 公会浏览器
- `guild_members.yml` - 成员列表
- `guild_bank.yml` - 金库界面
- `guild_vaults.yml` - 仓库选择器
- `guild_buffs.yml` - 增益商店
- `guild_upgrade.yml` - 升级菜单

---
