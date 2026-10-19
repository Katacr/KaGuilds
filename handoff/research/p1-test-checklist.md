# KaGuilds P1 修复 —— 引导测试清单

> 部署日期：2026-10-08
> 环境：Lobby + Survival1，KaGuilds v1.1.2
> 产物：`/home/Plugins/KaGuilds-refactor/build/libs/KaGuilds-1.1.2.jar`（md5 `5048ec8250d3e05fa3616a65419b5a93`）
> 备份：`/home/Backups/kaguilds-deploy-20261008/`（含清库前 `kaguild-before-p1-clear-*.sql`）
> 状态：已部署启动，无异常；测试库 `kaguild` 已清空
> 重点：P1 是并发/一致性修复，**单次操作不易复现问题**，多数需"并发/多服/重启"才能验证。

## 〇、前置准备

- **2 个测试账号**（会长 `A`、成员 `B`），跨服场景需在两服分别在线。
- 经济充足（≥ 20000）；记录测试公会 `#N`。
- 关键配置（Lobby `plugins/KaGuilds/`）：
  - 创建 10000、改名 3000、settp 1000、seticon 1000、motd 100、公会战 300
  - 利息：Lv1 `bank-interest: 0.5%`/日（`levels.yml`）
  - 任务 `reset_time: 00:00:00`
- 监控命令：

```bash
tail -f /home/Server/Lobby/logs/latest.log | grep -iE "KaGuilds|Exception|SEVERE"
mysql -uroot -p'MMPokHsgcHIItnXC' kaguild -e "SELECT * FROM guild_members; SELECT * FROM guild_task_progress;"
```

---

## 一、P1-1 schema 唯一约束（并发数据完整性）

**背景**：原先无唯一约束 + 先查后改，并发可产生双公会/重复申请/任务重复行。

### 1.1 唯一索引存在性（快速）
| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | `SHOW INDEX FROM guild_members;` | 存在 `uk_member_uuid(player_uuid)` |
| 2 | `SHOW INDEX FROM guild_requests;` | 存在 `uk_request_guild_player(guild_id,player_uuid)` |
| 3 | `SHOW INDEX FROM guild_task_progress;` | 存在 `uk_guild_task_player` |

### 1.2 并发创建/加入（防双公会）
| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 两个账号 `B`、`C` 同时对同一公会发起 `/kg join #N` | `guild_requests` 中 `(guild_id,player_uuid)` 只有一行 |
| 2 | `B` 被两个管理员同时批准（若可模拟） | `guild_members` 中该玩家仅一行，`guild_id` 正确 |
| 3 | `B` 快速 `/kg leave` 后立即重新 `/kg join`+被批准 | 仍只有一行，无重复 |

### 1.3 换公会
| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | `B` 在公会 1，加入公会 2 | `guild_members` 中 `B` 仍只有一行，`guild_id` 指向公会 2 |
| 2 | 旧公会 `#1` 成员列表 | 不再包含 `B` |

### 1.4 任务唯一约束
| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 全局任务（如 `donate_global`）由多人同时推进 | `guild_task_progress` 中该任务仅一行（`player_uuid=''`） |
| 2 | 查 `SELECT player_uuid FROM guild_task_progress WHERE player_uuid IS NULL` | 结果为空（统一用 `''`） |

---

## 二、P1-3 MembershipCache 兜底（不误判退会）

**背景**：原先查询异常被当"无公会"缓存 10s，导致误判退会。

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 玩家在线时，短暂让 MySQL 不可用（可选：`systemctl stop mysql` 数秒后恢复） | 聊天/身份相关操作不因查询失败被误判为"无公会" |
| 2 | 恢复 DB 后玩家执行 `/kg info` | 正常显示公会信息 |
| 3 | 玩家**确实**退会后执行 `/kg info` | 正确提示无公会（"确无公会"仍正常缓存） |

> 无 DB 中断条件时，可只验证正常路径：退会后立刻 `/kg`、`/kg chat` 均提示无公会。

---

## 三、P1-5 线程安全 + 生命周期清理

**背景**：共享集合改并发容器；插件卸载清理 BossBar；Vault 租约任务不再空转。

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 多玩家并发使用聊天模式/邀请/传送/Buff/任务 | 无 `ConcurrentModificationException` |
| 2 | 触发任务进度出现 BossBar 后，执行 `/reload`（或重启） | 玩家屏幕**无残留** BossBar |
| 3 | 打开公会仓库 `/kg vault 1` 后不关闭，直接切换打开其他 GUI | 约 5s 后租约任务停止（不再无意义刷新）；仓库锁到租约过期后释放 |
| 4 | 玩家打开仓库后直接退出游戏 | 锁释放、其他服可打开 |

---

## 四、P1-2 线程边界（跨服收包/回调主线程）

**背景**：跨服收包原在 Netty 线程操作 Bukkit API；Service 回调原在异步线程发消息。

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 跨服聊天：A 服发言，B 服成员收消息 | 正常收到，无异步线程异常 |
| 2 | 跨服申请通知：B 服玩家申请 A 服公会 | A 服管理员收到通知 |
| 3 | 跨服成员变动/踢出/改名/银行/Buff 通知 | 各服成员正常收到 |
| 4 | 各指令回调（创建/解散/踢人/改名/升级等） | 玩家收到成功/失败提示，无 `IllegalStateException: Asynchronous player...` |
| 5 | 大量挖矿/击杀/钓鱼事件 | 任务进度正常推进，服务器无因主线程查库导致的卡顿/TPS 下降 |
| 6 | 观察日志 | 无 `Asynchronous`/`AsyncPlayer` 相关报错 |

---

## 五、P1-4 原子性/重复收敛

### 5.1 解散公会清理关联表
| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 公会 `#N` 先积累数据：仓库放物品、银行存取产生日志、完成任务产生进度、发起过 PvP | 各表有该 `guild_id` 数据 |
| 2 | `/kg delete` + `/kg confirm` | 解散成功 |
| 3 | 查各表 | `guild_vaults`/`guild_bank_logs`/`guild_task_progress`/`guild_task_reward_claims`/`guild_pvp_history`/`guild_requests` 中该 `guild_id` **全部为 0 行**；`guild_members` 该公会成员置 `-1/NONE` |

### 5.2 转让会长
| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 会长 `A` 转让给 `B` | `guild_data.owner_uuid=B`；`guild_members` 中 `B=OWNER`、`A=MEMBER` |
| 2 | 若 `A`/`B` 曾在其他公会（测试库已清，通常无） | 不影响其他公会行（补 `guild_id` 条件生效） |

### 5.3 利息条件更新（防重复计息）
| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 公会存入 10000，记录余额与 `last_interest_date` | — |
| 2 | 手动把 `guild_data.last_interest_date` 改早 1 天（模拟隔日） | — |
| 3 | 触发计息（玩家上线 / 等午夜任务；或重启触发） | 余额按 `bank-interest` 增加约 0.5%；`last_interest_date` 推进到今日 |
| 4 | 立即再触发一次计息 | **不再重复计息**（日期已是今日，条件不满足） |
| 5 | 跨服：两服同时有该公会玩家上线 | 只计息一次，无翻倍 |

### 5.4 跨公会任务完成状态不污染
| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | `B` 在公会 1 完成每日任务 `fishing_daily` | 记录完成 |
| 2 | `B` 退出公会 1、加入公会 2（同日） | — |
| 3 | 在公会 2 查看该每日任务 | **未完成**（不因旧公会完成状态被跳过） |

---

## 六、回归测试（P0 + 基础功能）

确保 P1 未破坏既有功能：

- 创建/邀请/加入/踢出/退会/转让/解散
- 聊天（本服 + 跨服）、银行存取与日志、仓库存取、Buff
- 任务：玩家完成发奖、`/kg admin task ... add <值> <玩家名>`
- PvP：发起/准备/结算/超时、背包快照还原
- `/kg list` 打开公会列表
- 跨服解散通知

---

## 七、回滚步骤

```bash
export NO_PROXY=127.0.0.1,localhost no_proxy=127.0.0.1,localhost
/usr/local/bin/mcsm stop lobby
/usr/local/bin/mcsm stop survival1
cp /home/Backups/kaguilds-deploy-20261008/KaGuilds-1.1.2.jar.lobby-list.bak /home/Server/Lobby/plugins/KaGuilds-1.1.2.jar
cp /home/Backups/kaguilds-deploy-20261008/KaGuilds-1.1.2.jar.survival1-list.bak /home/Server/Survival1/plugins/KaGuilds-1.1.2.jar
/usr/local/bin/mcsm start lobby
/usr/local/bin/mcsm start survival1
```

> 注：P1 新增了数据库唯一约束。若回滚到旧版本且旧代码不满足约束，可能写入报错；如需完全回滚，建议同时 `DROP INDEX uk_member_uuid ON guild_members` 等（或恢复清库前备份）。

---

## 八、已知未修复（本次不在范围）

P2 项：跨服 NetworkBus 契约化、`GuildService`/`MenuManager`/`TaskManager` 上帝类拆分、指令层公共基类、PAPI/分页/菜单渲染主线程查库性能、`NotifyStaff`/`CLEAR_GUILD` 死子频道清理。详见 `research/full-audit-and-refactor-plan.md` 第三节。
