# 数据访问层

> DatabaseManager 已瘦身为连接池 + repository 持有者。所有 SQL 下沉到 repository。

## 当前结构

- `DatabaseManager.kt`(~64 行)：HikariCP 连接池生命周期、`xxxRepository` 实例、暂存数据模型。
- `repository/`：
  - `SchemaManager.kt`(173)：建表与轻量迁移 SQL。`DatabaseManager.createTables()` 仅委托。
  - `GuildRepository.kt`(273)：公会单表查询/分页/等级经验/图标/公告/传送点/计息日期。`findGuild()`/`mapGuildData()`/`executeGuildUpdate()` 统一入口。
  - `MemberRepository.kt`(358)：成员关系/职位/贡献度/玩家名UUID映射。`findPlayerUuidByName()` 合并重复 SQL。
  - `BankRepository.kt`(127)：金库余额 + 银行日志 SQL。`executeBalanceUpdate()` 统一。
  - `VaultRepository.kt`(153)：仓库内容、数据库锁、租约刷新 `refreshLockLease()`。
  - `RequestRepository.kt`(45)：加入申请 CRUD。
  - `TaskRepository.kt`(513)：任务进度查询/递增/重置/完成列表/过期检查/奖励凭证写入。
  - `GuildLifecycleRepository.kt`(132)：`createGuild`/`deleteGuild`/`transferGuildOwnership` 跨表事务。
  - `PvPRepository.kt`(48)：战绩计数 + `guild_pvp_history` 写入。
- `model/GuildModels.kt`(66)：`GuildData`(含 `totalContribution`)、`MemberData`、`GuildTaskProgress`。

## 关键决策

- 旧方法名保留为兼容委托，逐步迁移调用点到 `dbManager.xxxRepository.xxx()`(部分已完成)。
- 仓库锁保存校验 `last_editor`，降低关闭事件覆盖新仓库数据风险。
- 余额更新走条件更新(防超存/超取)，贡献度更新走原子 SQL(`UPDATE ... SET contribution = contribution + ?`)无先查后改竞态。
- 公会累计总贡献度(`total_contribution`)走 `incrementTotalContribution` 原子 SQL，`set`/`clear` 不影响公会总贡献度。

## 待办

- 贡献度扣减条件更新(2026-09-08 已解除高风险)：银行存取款不再联动贡献度(`BankService` 改动)；现仅管理员命令 `/kg admin contribution` 直接写贡献度，走 `UPDATE ... SET contribution = contribution + ?` 原子 SQL，无先查后改竞态。
- 仓库保存区分错误类型(数据库异常 vs 锁失效)——已加部分区分日志(P2)，可继续细化错误类型供上层提示。
- 任务进度写入事务边界已在 `TaskRepository` 内统一，仍需核对其他调用点是否绕过。
