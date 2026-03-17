# 🔌 PlaceholderAPI 集成

KaGuilds 插件完全支持 PlaceholderAPI（PAPI），您可以在聊天、记分板、BossBar、Tab 列表等任何支持 PAPI 的地方使用 KaGuilds 的变量。

## 📋 基础信息

### 变量前缀

所有 KaGuilds 的占位符都使用 `kaguilds` 作为前缀：

```
%kaguilds_xxx%
```

### 前置要求

使用 PAPI 功能需要：

1. 安装 **PlaceholderAPI** 插件
2. 在服务器中启用 PAPI
3. 确保玩家已加入公会（部分变量）

### 安装步骤

1. 下载并安装 [PlaceholderAPI](https://www.spigotmc.org/resources/6245/) 插件
2. 将 KaGuilds 放入 `plugins` 文件夹
3. 重启服务器，PAPI 会自动加载 KaGuilds 扩展
4. 使用 `/papi parse player %kaguilds_name%` 测试变量

## 🎯 变量列表

### 📌 公会信息

| 变量 | 说明 | 示例 |
|------|------|------|
| `%kaguilds_name%` | 公会名称 | `星空公会` |
| `%kaguilds_id%` | 公会 ID | `5` |
| `%kaguilds_level%` | 公会等级 | `3` |
| `%kaguilds_balance%` | 公会余额 | `1500.0` |
| `%kaguilds_announcement%` | 公会公告 | `欢迎加入！` |
| `%kaguilds_owner%` | 会长名称 | `Player1` |
| `%kaguilds_create_time%` | 公会创建时间 | `2024-01-15 10:30:00` |
| `%kaguilds_exp%` | 当前经验值 | `1500` |
| `%kaguilds_need_exp%` | 下一级所需经验 | `2000` |

### 👥 成员信息

| 变量 | 说明 | 示例 |
|------|------|------|
| `%kaguilds_member_count%` | 当前成员数 | `15` |
| `%kaguilds_max_members%` | 最大成员数 | `20` |
| `%kaguilds_member_list%` | 成员列表 | `Player1, Player2, Player3` |
| `%kaguilds_contribution%` | 个人贡献值 | `500` |

### 🔐 职位权限

| 变量 | 说明 | 示例 |
|------|------|------|
| `%kaguilds_role_name%` | 职位名称 | `管理员` |
| `%kaguilds_is_owner%` | 是否是会长 | `是/否` |
| `%kaguilds_is_admin%` | 是否是管理员 | `是/否` |
| `%kaguilds_is_staff%` | 是否是管理成员 | `是/否` |

### ⚔️ PvP 数据

| 变量 | 说明 | 示例 |
|------|------|------|
| `%kaguilds_pvp_wins%` | 胜场数 | `10` |
| `%kaguilds_pvp_losses%` | 败场数 | `3` |
| `%kaguilds_pvp_draws%` | 平局数 | `2` |
| `%kaguilds_pvp_total%` | 总场次 | `15` |

### 📝 其他信息

| 变量 | 说明 | 示例 |
|------|------|------|
| `%kaguilds_pending_requests%` | 待处理申请数 | `3` |


