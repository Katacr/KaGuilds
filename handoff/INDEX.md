# 交接文件索引 (INDEX)

> KaGuilds-refactor 项目交接文档。新会话先读此文件恢复上下文。
> 项目根目录：`C:\Users\LiXin\IdeaProjects\KaGuilds-refactor`
> 原项目(未参与本轮改动)：`/home/plugins/KaGuilds`(Linux)

## 模块文档

| 文件 | 主题 | 关键内容 |
|------|------|----------|
| [architecture.md](./architecture.md) | 项目架构与构建环境 | 技术栈、目录结构、构建/运行方式、构建环境约束 |
| [refactoring.md](./refactoring.md) | 重构进度 | 已完成 / 进行中 / 未完成清单，风险优先级 |
| [data-layer.md](./data-layer.md) | 数据访问层 | DatabaseManager、repository 拆分、SchemaManager、数据模型 |
| [command-layer.md](./command-layer.md) | 指令层 | GuildCommand 拆分、各 Handler 职责、TabCompleter |
| [concurrency.md](./concurrency.md) | 线程安全与并发 | 主/异步线程边界、锁、条件更新、未收敛的缓存 |
| [cross-server.md](./cross-server.md) | 跨服通信 | KaProxy 通道约定、字段顺序、重构红线 |

## 调研报告 (research/)

| 文件 | 主题 | 最后更新 |
|------|------|----------|
| [cross-server-bugs.md](./research/cross-server-bugs.md) | 跨服逻辑缺陷调研 | 2026-09-07 |
| [cross-server-arch-redesign.md](./research/cross-server-arch-redesign.md) | 跨服架构重构方案(含 KaProxy 双向修正) | 2026-09-07 |

## 引用说明

- 重构进度原始记录见仓库根目录 `REFACTORING.md`，本文档按模块提炼并标注当前状态。
- 全局调研报告(ItemsAdder/CraftEngine/EcoQuests/BetterHud)位于 `~/.config/opencode/skills/` 与 `C:\Users\LiXin\handoff\research/`，与本项目无直接关系，仅 KaMenu 集成可能涉及。

## 接手后优先处理

1. 未收敛的内存缓存线程安全(`playerGuildCache`/`guildChatPlayers`/`pendingConfirmations`/任务缓存)。
2. 贡献度扣减仍是先查后改，需补充条件更新或事务。
3. 任务奖励两步提交(写凭证 + 执行命令)的原子性风险。
4. 指令 Handler 重复的 `checkPermission`/`sendConfirmHint` 收敛为公共辅助类。
5. Folia 兼容议案(`FOLIA_COMPATIBILITY_PROPOSAL.md`)暂未实施。
