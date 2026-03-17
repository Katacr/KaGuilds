# 🚀 快速开始

本指南将帮助你快速安装和配置 KaGuilds 插件。

---
 
## 🎮 系统要求

| 项目 | 支持详情               | 
|------|--------------------|
| Minecraft 版本 | 1.16.1 ~ 1.21.11   | 
| Java 版本 | Java 12 ~ 21       | 
| 服务器类型 | Paper及衍生核心         | 
| 代理服务器（可选） | Velocity           | 
| 数据库 | SQLite, MySQL 5.7+ | 

---

## 📥 安装步骤

### 1. 下载插件

从 GitHub Releases 下载最新版本的 KaGuilds 插件：

{% embed url="https://github.com/Katacr/KaGuilds/releases" %}

### 2. 安装依赖

KaGuilds 需要以下依赖插件才能正常工作：

**必需依赖：**
- [Vault](https://www.spigotmc.org/resources/vault.34315/) - 经济系统支持

**可选依赖：**
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) - 占位符支持

下载这些插件并将它们的 `.jar` 文件放入服务器的 `plugins` 文件夹中。

### 3. 安装插件

1. 将下载的 KaGuilds `.jar` 文件放入服务器的 `plugins` 文件夹
2. 启动服务器
3. 插件会自动创建配置文件和数据库表
   {% hint style="info" %}
   请勿使用插件加载器(如PlunMan)加载或重载KaGuilds，否则可能遇到未知错误。
   {% endhint %}