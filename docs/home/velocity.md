# Velocity 配置

KaGuilds 的多服务器模式由共享 MySQL 保存持久数据，并由 KaProxy Guilds 模块转发聊天、邀请、通知和缓存同步消息。

## 部署结构

每个后端服务器都需要：

- 安装相同版本的 KaGuilds
- 连接同一个 MySQL 数据库
- 设置 `proxy: true`
- 使用唯一且稳定的 `server-id`

Velocity 只安装 KaProxy，不安装后端版 KaGuilds。在 KaProxy 中启用 Guilds 模块，由它负责与各后端 KaGuilds 交换跨服消息。

## 安装代理插件

1. 将 `KaProxy.jar` 放入 Velocity 的 `plugins` 目录。
2. 重启 Velocity。
3. 打开 `plugins/kaproxy/config.yml`。
4. 确认 `modules.guilds.enabled` 和 `legacy-channel-enabled` 均为 `true`。

## 配置后端服务器

在每个后端的 `plugins/KaGuilds/config.yml` 中使用相同的数据库参数，但为每台服务器设置不同的 `server-id`：

```yaml
proxy: true
server-id: survival

database:
  type: "MySQL"
  host: "127.0.0.1"
  port: 3306
  db: "kaguilds"
  user: "kaguilds"
  password: "replace_with_a_strong_password"
```

例如，生存服使用 `survival`，资源服使用 `resource`，大厅服使用 `lobby`。修改后应完整重启所有后端服务器。

## 验证

1. 在两个后端分别登录测试玩家。
2. 创建或加入同一个公会。
3. 验证公会聊天、邀请、申请通知和成员变更可以跨服到达。
4. 在一个后端打开公会仓库，确认另一个后端无法同时打开同一仓库。
5. 退出、踢出和解散测试公会后，确认其他后端的玩家缓存及时清理。
6. 检查 Velocity 和所有后端控制台是否存在插件消息或数据库异常。

## 安全与运维

- 后端服务器只应接受来自 Velocity 的连接，不要直接暴露给公网玩家。
- MySQL 账户使用最小权限，不要使用 `root`，并限制数据库来源地址。
- 所有后端保持 KaGuilds、菜单和语言配置版本一致。
- 不要在运行期间切换 `server-id` 或数据库。
- 插件消息通常需要在线玩家作为传输载体；网络空闲时部分即时通知可能延迟。
- 升级代理或后端插件前同时备份数据库和各服务器配置。
