# 线程安全与并发

> 重构已修复多处高风险并发问题，但内存缓存与部分更新仍未收敛。

## 已完成加固

- `KaGuilds.runMain`/`runAsync` 统一线程入口。
- 异步只做查库/计算，主线程做 Vault 检查/扣款/传送/广播/GUI 操作。
- 金库余额：`depositGuildBalanceIfWithinLimit` / `withdrawGuildBalanceIfEnough` 数据库条件更新。
- 仓库保存：`saveVault` 按 `last_editor` 校验锁持有者。
- `/kg bank add/take`：余额条件更新 + 存款失败退还玩家。
- 任务进度更新：事务内 `FOR UPDATE` + 唯一领取凭证，防并发重复发奖。

## 未收敛(高优先级)

- 以下仍是普通 `MutableMap`/`MutableSet`，多线程访问边界未收敛：
  - `playerGuildCache`
  - `guildChatPlayers`
  - `pendingConfirmations`
  - 任务缓存
- 贡献度扣减仍是先查后改(读-改-写)，并发下可能超额扣减；需条件更新或事务。

## 待办

| 优先级 | 项 | 建议 |
|--------|----|------|
| 高 | 上述内存缓存线程安全 | 视并发场景用 `ConcurrentHashMap`/同步块/`volatile`，或收敛到单线程访问 |
| 高 | 贡献度条件更新 | 仿照余额条件更新，SQL 层加 WHERE 校验后再改 |
| 中 | 任务奖励两步提交原子性 | reward outbox / 补偿队列，防凭证占用但奖励未发 |
| 中 | 仓库保存错误细化 | 区分数据库异常 vs 锁已失效，便于日志与玩家提示 |
| 低 | Folia 兼容 | 仅议案，未实施；若后续实施需替换所有 Bukkit 调度为 Folia 感知调度 |
