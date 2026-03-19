# 🔐 权限列表

KaGuilds 使用完整的权限系统来控制玩家和管理员可以访问的功能。权限分为两大类：**玩家权限**（普通公会成员功能）和**管理员权限**（服务器管理功能）。

***

## 权限包

### 使用建议

| 权限节点           | 描述         |
| -------------- | ---------- |
| `kaguilds.use` | 允许使用所有公会功能 |
| `kaguilds.admin` | 允许使用所有管理功能 |

* 通常情况下，你*只需要*给予玩家`kaguilds.use`权限，玩家即可使用*所有公会内容*。
* 若你想自定义玩家可使用的功能，就*禁用*玩家的`kaguilds.use`权限，请在下方列表中*选择对应的权限*。

## 📝 完整权限列表

### 玩家权限

| 权限节点                        | 描述             | 默认值   |
| --------------------------- | -------------- |-------|
| `kaguilds.use`              | 允许使用所有公会功能     | true  |
| `kaguilds.command.main`     | 允许使用基础公会指令     | true  |
| `kaguilds.command.help`     | 允许使用帮助指令       | true  |
| `kaguilds.command.create`   | 允许使用创建公会指令     | false |
| `kaguilds.command.delete`   | 允许使用解散公会指令     | false |
| `kaguilds.command.promote`  | 允许使用提升公会成员指令   | false |
| `kaguilds.command.demote`   | 允许使用降级公会成员指令   | false |
| `kaguilds.command.accept`   | 允许使用批准入会申请指令   | false |
| `kaguilds.command.deny`     | 允许使用拒绝入会申请指令   | false |
| `kaguilds.command.confirm`  | 允许使用确认操作指令     | false |
| `kaguilds.command.bank`     | 允许使用公会金库指令     | false |
| `kaguilds.command.chat`     | 允许使用公会聊天指令     | false |
| `kaguilds.command.menu`     | 允许使用公会主菜单指令    | false |
| `kaguilds.command.motd`     | 允许使用公会公告指令     | false |
| `kaguilds.command.rename`   | 允许使用公会重命名指令    | false |
| `kaguilds.command.seticon`  | 允许使用公会图标指令     | false |
| `kaguilds.command.settp`    | 允许使用公会传送点指令    | false |
| `kaguilds.command.tp`       | 允许使用公会传送指令     | false |
| `kaguilds.command.upgrade`  | 允许使用公会升级指令     | false |
| `kaguilds.command.info`     | 允许使用查看公会信息指令   | false |
| `kaguilds.command.invite`   | 允许使用邀请入会指令     | false |
| `kaguilds.command.join`     | 允许使用申请入会指令     | false |
| `kaguilds.command.kick`     | 允许使用踢出公会成员指令   | false |
| `kaguilds.command.leave`    | 允许使用退出公会       | false |
| `kaguilds.command.vault`    | 允许使用公会云仓库指令    | false |
| `kaguilds.command.transfer` | 允许使用公会转让指令     | false |
| `kaguilds.command.pvp`      | 允许使用公会战相关指令    | false |
| `kaguilds.command.admin`    | 允许使用公会管理员管理指令  | false |
| `kaguilds.command.yes`      | 允许使用接受邀请入会指令   | false |
| `kaguilds.command.no`       | 允许使用拒绝邀请入会指令   | false |
| `kaguilds.command.requests` | 允许使用查看入会申请指令   | false |
| `kaguilds.command.buff`     | 允许玩家使用公会Buff指令 | false |

### 管理员权限

| 权限节点                      | 描述                | 默认值 |
|---------------------------|-------------------| --- |
| `kaguilds.admin`          | 允许使用所有管理功能        | op  |
| `kaguilds.admin.reload`   | 允许使用重载配置文件指令      | op  |
| `kaguilds.admin.rename`   | 允许管理员使用公会重命名指令    | op  |
| `kaguilds.admin.delete`   | 允许管理员使用公会解散指令     | op  |
| `kaguilds.admin.info`     | 允许管理员使用公会信息指令     | op  |
| `kaguilds.admin.bank`     | 允许管理员使用公会金库指令     | op  |
| `kaguilds.admin.transfer` | 允许管理员使用公会转让指令     | op  |
| `kaguilds.admin.kick`     | 允许管理员使用公会踢出指令     | op  |
| `kaguilds.admin.join`     | 允许管理员使用公会加入指令     | op  |
| `kaguilds.admin.vault`    | 允许管理员使用公会云仓库指令    | op  |
| `kaguilds.admin.unlockall` | 允许管理员使用公会存储解锁全部指令 | op  |
| `kaguilds.admin.setlevel` | 允许管理员使用公会等级指令     | op  |
| `kaguilds.admin.exp`      | 允许管理员使用公会经验指令     | op  |
| `kaguilds.admin.arena`    | 允许管理员使用公会战指令      | op  |
| `kaguilds.admin.open`     | 允许管理员打开任意GUI菜单    | op  |
| `kaguilds.admin.release`         | 释放指定语言的GUI菜单      | op  |

***
