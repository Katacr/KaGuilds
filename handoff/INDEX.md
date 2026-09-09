# 交接文件索引 (INDEX)

> KaGuilds-refactor 项目交接文档。新会话先读此文件恢复上下文。
> 项目根目录：`C:\Users\LiXin\IdeaProjects\KaGuilds-refactor`
> 原项目(未参与本轮改动)：`/home/plugins/KaGuilds`(Linux)
> 最后更新：2026-09-08（公会累计总贡献度）

## 模块文档

| 文件 | 主题 | 关键内容 |
|------|------|----------|
| [architecture.md](./architecture.md) | 项目架构与构建环境 | 技术栈、目录结构、构建/运行方式、构建环境约束 |
| [refactoring.md](./refactoring.md) | 重构进度与业务变更 | 已完成/进行中/待办；跨服架构重构(P0-P2)与银行-贡献度解耦记录 |
| [data-layer.md](./data-layer.md) | 数据访问层 | DatabaseManager、repository 拆分、SchemaManager、数据模型 |
| [command-layer.md](./command-layer.md) | 指令层 | GuildCommand 拆分、各 Handler 职责、TabCompleter |
| [concurrency.md](./concurrency.md) | 线程安全与并发 | 主/异步线程边界、锁、条件更新、未收敛的缓存 |
| [cross-server.md](./cross-server.md) | 跨服通信 | 探测/降级流程、KaProxy 双向修正、通道契约、重构红线 |

## 调研报告 (research/)

| 文件 | 主题 | 最后更新 |
|------|------|----------|
| [cross-server-bugs.md](./research/cross-server-bugs.md) | 跨服逻辑缺陷调研(物品消失/通知/聊天根因) | 2026-09-07 |
| [cross-server-arch-redesign.md](./research/cross-server-arch-redesign.md) | 跨服架构重构方案(含 KaProxy 双向修正)与 P0-P2 落地记录 | 2026-09-07 |

## 接手后优先处理

1. P3/P4 收尾:`NetworkBus` 离线补发、清理 `NotifyStaff` 死代码、单服/两服/断网降级实测。
2. 未收敛的内存缓存线程安全(`guildChatPlayers`/`pendingConfirmations`/任务缓存)。
3. 任务奖励两步提交(写凭证 + 执行命令)的原子性风险。
4. 指令 Handler 重复的 `checkPermission`/`sendConfirmHint` 收敛为公共辅助类。
5. Folia 兼容议案(`FOLIA_COMPATIBILITY_PROPOSAL.md`)暂未实施。
6. Redis:后续选择性引入(软依赖,解决跨服聊天/邀请/锁),本次未做。
