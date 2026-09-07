# 架构与构建环境

## 技术栈

- 语言：Kotlin(JVM)，Kotlin 编译器 `2.3.20-Beta1`
- 服务端 API：`org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT`(compileOnly)
- 字节码目标：Java 12(`jvmToolchain(12)` + `options.release = 12`)
- 构建：Gradle Kotlin DSL + shadowJar(`com.gradleup.shadow` 8.3.0) + `run-paper` 2.3.1
- 依赖：libby-bukkit(依赖加载)、bstats、HikariCP、sqlite-jdbc、VaultAPI、PlaceholderAPI
- 包名：`org.katacr.kaguilds`，group `org.katacr`，version `1.1.0`

## 目录结构(src/main/kotlin/org/katacr/kaguilds)

| 包/文件 | 行数(约) | 职责 |
|---------|----------|------|
| `KaGuilds.kt` | 566 | 主类，插件入口，`runMain`/`runAsync` 线程统一入口 |
| `GuildCommand.kt` | 117 | Bukkit 命令入口、基础校验、handler 分发、TabCompleter 委托 |
| `MenuManager.kt` | 1860 | YAML GUI 菜单渲染(最大文件) |
| `DatabaseManager.kt` | 64 | 连接池生命周期、repository 实例、暂存数据模型 |
| `command/` | - | 各子命令 Handler(`*CommandHandler`) + `GuildTabCompleter` |
| `repository/` | - | 数据访问层拆分(`*Repository` + `SchemaManager`) |
| `service/` | - | `GuildService`(1994)、`TaskManager`(977)、`VaultService`、`BankService`、`OperationResult` |
| `listener/` | - | `MenuListener`(797)、`PluginMessageListener`(539)、`TaskListener`、`GuildListener`、`VaultListener`、`PvPListener`、`NotifyListener`、`GuildMenuHolder`、`VaultHolder` |
| `arena/` | - | `PvPManager`(704)、`ArenaManager`、`ArenaData` |
| `model/` | - | `GuildModels.kt`(`GuildData`/`MemberData`/`GuildTaskProgress`) |
| `util/` | - | `MessageUtil`、`SerializationUtil` |
| `KaGuildsExpansion.kt` | 167 | PlaceholderAPI 扩展 |

## 构建与运行

```bash
# Windows 构建
gradlew.bat shadowJar
# 产物：build/libs/KaGuilds-1.1.0.jar（archiveClassifier 清空）

# 本地跑服务端（run-paper）
gradlew.bat runServer   # minecraftVersion("1.16")
```

## 构建环境约束(重要)

- 原 Linux 环境使用本机 Gradle `/opt/gradle/gradle-8.8` + JDK 12 `/opt/jdks/jdk-12.0.2+10`，通过 `/root/.gradle/gradle.properties` 指定，避免 wrapper 外网下载超时。
- Windows 同步开发时，若 wrapper 下载超时，优先用本机 Gradle 或国内镜像(阿里云/腾讯/NJU)，不要改仓库源以免影响 Windows 与 Linux 同步。
- 阿里云镜像已在 `build.gradle.kts` repositories 中配置。
- `libs/` 下放置本地 jar(compileOnly fileTree)，注意平台无关性。

## 运行配置(runtime)

运行时文件在后端服务器 `plugins/KaGuilds/`：`config.yml`、`levels.yml`、`buffs.yml`、`task.yml`、`lang/*.yml`、`gui/*.yml`(及 CN/EN 子目录)、`arena.yml`。

## 已知构建风险

- `MenuManager.kt` 使用 `getOfflinePlayer(String)` 已 deprecated，仅 warning。
- Kotlin 2.3.20-Beta1 为 Beta 编译器，升级需谨慎验证。
- 不要使用 PlugMan 热加载插件；升级前备份数据库与 `plugins/KaGuilds`。
