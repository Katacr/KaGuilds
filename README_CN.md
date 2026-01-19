# 🛡️ KaGuilds - 高性能跨服公会系统

**KaGuilds** 是一款专为群组服务器（BungeeCord/Velocity）设计的深度定制公会插件。它不仅提供基础的公会社交功能，还通过 **SQL 事务**与**跨服消息总线**确保了在复杂网络环境下的数据一致性与安全性。

---

## ✨ 核心特性

* **真正的跨服同步 (Cross-Server Architecture)**
    * 基于 Velocity/BungeeCord 消息通道，实现跨服邀请、公告和聊天。
    * 无论成员分布在哪个子服，公会信息与权限实时同步。
* **深度的经济整合 (Economy & Bank)**
    * **创建门槛**：可配置的创建费用，通过 Vault API 自动扣除。
    * **公会金库**：支持成员存取金币，并为管理员提供带翻页功能的交易日志 (Bank Logs)。
* **严谨的职位体系 (Role Hierarchy)**
    * 支持 **会长 (Owner)**、**管理员 (Admin)** 及 **成员 (Member)** 三级职位。
    * 内置提升 (Promote) 与降职 (Demote) 逻辑，权限划分明确。
* **动态等级系统 (Configurable Levels)**
    * 根据公会经验自动升级，动态调整公会人数上限及金库存储上限。
* **完善的邀请与申请 (Invite & Request)**
    * **邀请系统**：支持全服玩家邀请，具备自动过期机制。
    * **申请列表**：管理员可随时查看并批准或拒绝入会申请。
* **PlaceholderAPI 完美适配**
    * 提供公会名称、职位、人数、资金等多种变量，支持在计分板或聊天栏显示。

---

## 🎮 指令手册 (Command Reference)

### 玩家指令
| 指令 | 说明 | 权限 |
| :--- | :--- | :--- |
| `/kg create <名称>` | 支付金币创建公会 | `kaguilds.use` |
| `/kg join <名称>` | 申请加入指定公会 | `kaguilds.use` |
| `/kg info [名称]` | 查看公会详细信息、等级、资金等 | `kaguilds.use` |
| `/kg chat <消息>` | 在公会内部频道交流 | `kaguilds.use` |
| `/kg bank <add/get>` | 向公会金库存/取钱 | `kaguilds.use` |
| `/kg leave` | 退出当前公会 | `kaguilds.use` |

### 管理员指令
| 指令 | 说明 | 权限 |
| :--- | :--- | :--- |
| `/kg invite <玩家>` | 邀请在线/跨服玩家加入 | `kaguilds.use` |
| `/kg accept <玩家>` | 批准玩家的入会申请 | `kaguilds.use` |
| `/kg deny <玩家>` | 拒绝玩家的入会申请 | `kaguilds.use` |
| `/kg promote <玩家>` | 提升成员职位为管理员 | `kaguilds.use` |
| `/kg kick <玩家>` | 将成员移出公会 | `kaguilds.use` |
| `/kg bank log [页码]` | 查看金库交易记录 | `kaguilds.use` |
| `/kg reload` | 重载配置文件 | `kaguilds.admin` |

---

## 📊 变量列表 (Placeholders)

* `%kaguilds_name%`: 所在公会名称
* `%kaguilds_role%`: 职位显示（会长/管理员/成员）
* `%kaguilds_level%`: 当前等级
* `%kaguilds_balance%`: 公会当前资金

---

## 🌐 国际化支持

插件完美支持多语言切换，可在 `config.yml` 中更改 `language` 项：
* **简体中文**: `zh_CN.yml`
* **English**: `en_US.yml`