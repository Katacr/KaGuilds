# 交接文件索引 (INDEX)

> KaGuilds-refactor 项目交接文档。新会话先读此文件恢复上下文。
> 项目根目录：`C:\Users\LiXin\IdeaProjects\KaGuilds-refactor`
> 原项目(未参与本轮改动)：`/home/plugins/KaGuilds`(Linux)
> 最后更新：2026-10-19（P0/P1/P2 全部完成 + 跨服 NetworkCodec + 创建公会唯一键冲突修复；7 个后端已统一部署）

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
| [full-audit-and-refactor-plan.md](./research/full-audit-and-refactor-plan.md) | 全量调研(14,335 行)与分 7 批次的改造建议(P0-P2 分级) | 2026-10-08 |
| [p0-test-checklist.md](./research/p0-test-checklist.md) | P0 修复部署到 Lobby 后的引导测试清单 | 2026-10-08 |
| [p1-test-checklist.md](./research/p1-test-checklist.md) | P1 修复(一致性/线程/原子性)的引导测试清单 | 2026-10-08 |

## 接手后优先处理

> 完整清单与证据见 [research/full-audit-and-refactor-plan.md](./research/full-audit-and-refactor-plan.md)。以下为 P0 速览。

1. ~~**P0 崩溃/数据丢失**~~ **已完成(2026-10-08)**:解散公会抛异常、PvP 背包快照丢装备、PvP 超时卡死、全服菜单 NPE、任务奖励凭证已写不发奖、管理员加进度 daily 恒 null、仓库锁普通路径失效、扣费不检查返回值。详见 `refactoring.md`。
2. ~~**P1 一致性**~~ **已完成(2026-10-08)**:schema 唯一约束/索引、线程边界统一、MembershipCache 误判、利息重复计息、deleteGuild 漏关联表。详见 `refactoring.md`。
3. **P2 架构(主要项已完成)**:指令层 `CommandSupport`、PAPI 缓存、分页 N+1、`ItemModelSupport`/`NetworkOutput`/`InterestService` 抽取、跨服索引 bug + `NetworkBus` 已完成。**暂缓**:GuildService/MenuManager 整体类拆分、跨服 `NetworkMessage` 全量契约化(涉 KaProxy 红线)。
4. Folia 兼容议案(`FOLIA_COMPATIBILITY_PROPOSAL.md`)暂未实施。
5. Redis:后续选择性引入(软依赖,解决跨服聊天/邀请/锁),本次未做。
