# 🌐 Velocity 端配置指南

KaGuilds 支持在 Velocity 代理服务器环境中运行，实现跨服数据同步和功能共享。本文档将指导您完成 Velocity 端的完整配置流程。


## 安装

KaGuildsProxy 是一个 Velocity 插件，只需要 Velocity 服务器上安装，**不需要**在子服务器上安装。

下载 `KaGuildsProxy.jar` 之后，将其复制到 Velocity 服务器的 `plugins` 文件夹中，并重启 Velocity 服务器。 Velocity 服务器无需修改任何配置文件。

## 配置

### 配置所有子服务器

**重要：所有子服务器必须使用相同的数据库配置！**

在每个子服务器的 `plugins/KaGuilds/config.yml` 中配置数据库：

```yaml
# KaGuilds 配置文件
proxy: true  # ✅ 启用代理模式
server-id: survival  # ⚠️ 每个服务器必须不同！

database:
  type: "MySQL"  # ⚠️ 必须使用 MySQL
  host: "localhost"  
  port: 3306
  db: "kaguilds"  
  user: "kaguilds" 
  password: "your_secure_password"  
```

