# KaGuilds P0 修复 —— 引导测试清单

> 部署日期：2026-10-08
> 测试环境：Lobby（`/home/Server/Lobby`），KaGuilds v1.1.2
> 部署产物：`/home/Plugins/KaGuilds-refactor/build/libs/KaGuilds-1.1.2.jar`（md5 `257166bcc6eb479ba4e8bc802a505663`）
> 备份：`/home/Backups/kaguilds-deploy-20261008/KaGuilds-1.1.2.jar.bak`（md5 `6ffb0f1e25457567f68c0845a85c0edf`）
> 状态：已部署并启动成功（`Done (29.004s)`，无 KaGuilds 异常）

## 〇、前置准备

- 需要 **2 个以上测试账号**（一个会长 `A`，一个成员 `B`），另需一个第三方账号 `C` 用于申请/被踢等。
- 测试账号需有经济（Vault/XConomy），金额建议 ≥ 20000，便于创建公会与测试扣费。
- 建议先用管理员创建或准备一个测试公会；记录其 `#ID`（下文以 `#N` 表示）。
- 关键配置（Lobby `plugins/KaGuilds/config.yml`）：
  - 创建公会 10000，改名 3000，设置传送点 1000，设置图标 1000，修改公告 100，发起公会战 300
  - 公会战：冷却 300s，最小人数 2，准备 30s，战斗时长取 `guild.arena.battle-time` 默认 **300s**（配置里的 `pvp-time: 600` 不生效）
  - 任务 BossBar 开启，每日任务重置时间 `00:00:00`

### 日志与数据监控命令

```bash
# 实时看 KaGuilds 日志
tail -f /home/Server/Lobby/logs/latest.log | grep -iE "KaGuilds|Exception|SEVERE"

# 看 P0 相关异常
grep -nE "UnsupportedOperationException|KaGuilds.*(Exception|SEVERE)" /home/Server/Lobby/logs/latest.log | tail

# 数据库（公会库 kaguild）
mysql -uroot -p'MMPokHsgcHIItnXC' kaguild -e "SELECT * FROM guild_data; SELECT guild_id,player_name,role FROM guild_members;"
```

---

## 一、P0-1 解散公会不再抛异常（最高优先，先测）

**背景**：原 bug 在解散时对废弃缓存调用 `filterValues` 抛 `UnsupportedOperationException`（Survival1 日志实证）。

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 会长 `A` 在公会内，执行 `/kg delete`，再 `/kg confirm` | 收到「解散成功」提示 |
| 2 | 观察控制台/日志 | **不再出现** `UnsupportedOperationException` / `clearLocalGuildState` 堆栈 |
| 3 | `/kg info` 或 `/kg` | 提示已无公会 |
| 4 | 查库 `SELECT COUNT(*) FROM guild_data WHERE id=N` | 结果为 0 |
| 5 | 成员 `B`（在线）再执行 `/kg` | 提示已无公会（本地缓存已清理） |

**回归点**：解散后 `B` 若在公会聊天模式，应已退出（`guildChatPlayers` 清理）。

### 1.1 跨服解散通知（2026-10-08 追加修复）

**背景**：原 bug 普通 `/kg delete` 未发送跨服 `DeleteSync`，B 服成员收不到通知。

> 需要两个子服（如 Lobby + Survival1）同时在线，同公会成员分布在两服。

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 公会成员 `B` 在 B 服在线，会长 `A` 在 A 服 `/kg delete` + `/kg confirm` | A 服提示解散成功 |
| 2 | B 服 `B` 收到 | 收到解散通知（文案 `delete-notify`：「你的公会已被解散」） |
| 3 | B 服 `B` 执行 `/kg info` 或 `/kg` | 提示已无公会 |
| 4 | 管理员跨服解散：控制台 `/kg admin delete #N` | 目标服成员收到 `admin-delete-notify`（管理员文案） |
| 5 | B 服成员在聊天模式 | 解散后自动退出聊天模式 |

**注意**：UUID 精确通知需 **两服均为新版本**；若目标服为旧版，仍能按 guildId+缓存收到通知（兼容），但缓存缺失的成员可能漏收。建议全服统一升级。

---

## 二、P0-2 PvP 背包快照不丢装备（重点）

**背景**：原 bug 只备份主背包，护甲/副手在发放套装后永久丢失；且离线/重启不恢复。

**准备**：`A` 与 `B` 各自穿好**可识别的护甲**（如钻石头盔/胸甲/护腿/靴子）并在**副手放一个特殊物品**（如命名牌），主背包放若干物品，全部截图/记录。

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | `A` 公会（≥2 人）与 `B` 公会（≥2 人）双方 `/kg pvp start` + `/kg pvp accept` | 进入 30s 准备阶段 |
| 2 | 双方参战者 `/kg pvp ready` | 进入战斗，玩家被换上竞技场套装（`kit: true`） |
| 3 | 检查 `plugins/KaGuilds/pvp_snapshots.yml` | 文件存在且包含参战者 UUID 与序列化背包数据 |
| 4 | 战斗自然结束（击杀一方或等 300s 超时） | 参战者**护甲 + 副手 + 主背包全部还原**为参战前内容 |
| 5 | 比对截图 | 完全一致，无装备丢失 |

**崩溃/重启场景（关键）**：

| 步骤 | 操作 | 预期 |
|------|------|------|
| 6 | 战斗进行中，**直接强杀 Lobby 进程**（模拟崩溃），再启动 | 启动日志出现「已加载 N 份未结算的 PvP 背包快照」 |
| 7 | 该玩家重新上线 | 自动恢复参战前背包与生存模式 |

> 若测试服不方便强杀，可改为战斗进行中 `/stop`（正常关服）：`onDisable` 应恢复在线参战者并落盘。

**回归点**：`pvp_snapshots.yml` 在无未结算快照时应为空（正常关服后自动清空）。

---

## 三、P0-3 PvP 超时结算不再卡死

**背景**：原 bug 倒计时归零时若双方都存活则直接 `cancel`，比赛永久卡在 ADVENTURE。

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 发起并开始一场公会战，双方都有 ≥1 人存活 | 战斗中 |
| 2 | **双方都存活**，等待倒计时归零（`guild.arena.battle-time`，默认 300s；测试时建议临时改为 20s 加快） | 到达 0 时**立即结算**：人数多的一方获胜，人数相同平局 |
| 3 | 观察参战者 | 退出 ADVENTURE、传回、可正常再发起新战斗 |
| 4 | 结算后再次 `/kg pvp start` | 可正常开始新比赛（无旧计时器干扰） |

**回归点**：结算后 `/kg pvp exit` 不报错；BossBar 消失。

---

## 四、P0-4 全服玩家菜单不再 NPE

**准备**：需要有 `ALL_PLAYER` 类型按钮的菜单（如全服玩家列表/成员列表）。

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 打开含全服玩家列表的菜单 | 正常渲染，无 NPE |
| 2 | 对「未入库 / 跨服 / 名称无法解析」的玩家条目 | 显示空 UUID 占位，不再中断整个菜单 |
| 3 | 日志 | 无 `NullPointerException` |

---

## 五、P0-5 + P0-6 任务奖励与管理员加进度（重点）

**背景**：原 bug 管理员加进度只写凭证不发奖；daily 任务 `playerUuid` 恒 null 写错行。

### 5.1 玩家正常完成（P0-5）

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 以 `A` 完成一个 daily 任务（如上线/钓鱼/挤奶），触发完成 | 收到完成通知 + 奖励命令实际执行（如经验增加） |
| 2 | 查库 `SELECT * FROM guild_task_reward_claims ORDER BY claimed_at DESC LIMIT 3` | 有对应 claim 记录 |
| 3 | 当天重复触发同任务 | 不再发奖（凭证唯一） |

### 5.2 管理员加进度（P0-5 + P0-6）

| 步骤 | 操作 | 预期 |
|------|------|------|
| 4 | 控制台 `/kg admin task #N <daily任务键> add 1 <在线玩家名>` | 提示成功；**该玩家收到完成通知且奖励实际发放**（对照旧版此处不发奖） |
| 5 | 查库该 daily 任务的 `guild_task_progress` | 记录写在**该玩家 UUID** 行，而非 `player_uuid=''` 全局行 |
| 6 | `/kg admin task #N <daily任务键> add 1`（不填玩家名） | 提示「每日任务需指定玩家名」 |
| 7 | `/kg admin task #N <daily任务键> add 1 <离线玩家名>` | 提示需指定玩家名（拒绝写入，防奖励丢失） |
| 8 | `/kg admin task #N <global任务键> add 1` | 提示成功；全局任务完成时通知公会并在本服触发奖励动作 |
| 9 | `/kg admin task #N <daily任务键> see` | 显示各玩家进度 |

**回归点**：`/kg admin task` 帮助文案、Tab 补全（第 6 参数补全在线玩家名）正常。

---

## 六、P0-7 仓库锁（跨服重点）

**背景**：原 bug 普通玩家开仓库绕过内存锁/跨服广播；跨服 Unlock 可被任意玩家覆盖。

> 跨服测试需要至少 2 个子服同时在线且 KaProxy 正常（`检测到 KaProxy 通讯正常`）。

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | `A` 在 Lobby 打开公会仓库 `/kg vault 1` | 正常打开 |
| 2 | 另一子服上 `B`（同公会）尝试打开同一仓库 | 提示「仓库已被锁定」 |
| 3 | `A` 关闭仓库（放入物品后关闭） | 物品保存成功；其他服锁释放，`B` 可再打开 |
| 4 | 正常关闭仓库后检查其他服 | 其他服内存锁同步释放（旧版此处残留到租约过期） |
| 5 | 玩家 `A` 打开仓库后**直接退出游戏** | 锁被释放并广播，其他服可打开 |
| 6 | `A` 放入物品关闭后重新打开 | 物品仍在（P2 已修，回归确认） |

**异常场景**：

| 步骤 | 操作 | 预期 |
|------|------|------|
| 7 | `A` 打开仓库，另一服 `C` 触发远端 Unlock（非持有者） | 不影响 `A` 的锁（不再被误清） |

---

## 七、P0-8 扣费检查返回值（资金安全）

**准备**：把公会余额调低到不足，用于验证失败分支。

### 7.1 设置传送点（settp）

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 公会余额 ≥1000，会长 `/kg settp` | 成功，余额 -1000，银行日志 `SET_TP` |
| 2 | 余额 <1000，再 `/kg settp` | 提示余额不足，**余额不变、传送点不变** |

### 7.2 改名 / 图标 / 公告

| 步骤 | 操作 | 预期 |
|------|------|------|
| 3 | 余额 ≥3000，`/kg rename <新名>` | 成功，-3000，日志 `RENAME` |
| 4 | 余额 <3000，`/kg rename <新名>` | 余额不足，**名字不变、余额不变** |
| 5 | 同理测 `/kg seticon`（1000）、`/kg motd <内容>`（100） | 不足时不生效、不扣费 |
| 6 | 制造 DB 写入失败（如临时改错表名，可选） | 扣费被**退还**，提示错误 |

### 7.3 传送扣玩家钱

| 步骤 | 操作 | 预期 |
|------|------|------|
| 7 | 玩家余额充足 `/kg tp` | 扣玩家钱 + 传送成功 |
| 8 | 玩家余额不足 `/kg tp` | 不扣钱、不传送、提示余额不足（旧版可能免费传送） |

### 7.4 银行存取

| 步骤 | 操作 | 预期 |
|------|------|------|
| 9 | `/kg bank add <金额>`（超过金库上限） | 退款给玩家（日志无 severe 退款失败） |
| 10 | `/kg bank take <金额>`（正常） | 公会余额扣减 + 玩家到账 |
| 11 | 制造玩家入账失败（可选） | 公会余额回退 + 记 severe 日志 |

### 7.5 公会战挑战金 / Buff

| 步骤 | 操作 | 预期 |
|------|------|------|
| 12 | 余额 <300 发起 `/kg pvp start` | 余额不足，不发起 |
| 13 | 余额 <buff 价格购买 buff | 余额不足，不发放 |
| 14 | 并发场景（可选）：连续快速扣费 | 余额不为负 |

---

## 八、回归与回滚

### 回归检查
- 服务端启动无 `SEVERE`/异常；`Done` 正常。
- 单服核心功能：创建/邀请/加入/踢出/退会/转让/解散、聊天、银行、仓库、任务、PvP 全部走一遍。
- 跨服（若有第二子服）：聊天/申请通知/成员变动/仓库锁/PvP 通知。

### 回滚步骤（若发现严重问题）
```bash
export NO_PROXY=127.0.0.1,localhost no_proxy=127.0.0.1,localhost
/usr/local/bin/mcsm stop lobby
cp /home/Backups/kaguilds-deploy-20261008/KaGuilds-1.1.2.jar.bak /home/Server/Lobby/plugins/KaGuilds-1.1.2.jar
/usr/local/bin/mcsm start lobby
```

---

## 九、已知未修复（本次不在范围，测试时无需关注）

- 内存缓存线程安全（`guildChatPlayers`/`pendingConfirmations`/任务缓存）
- schema 缺唯一约束（双公会/重复申请的极端并发）
- 线程边界不统一（异步线程调 Bukkit API、主线程查库）
- 跨服 NetworkBus 离线补发、死子频道清理
- 指令 Handler 重复代码、PAPI 无缓存
- 利息重复计息、`deleteGuild` 遗漏关联表

详见 `research/full-audit-and-refactor-plan.md` 的 P1/P2。
