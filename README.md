# PlayerOfflineStatus

一个 Minecraft Bukkit/Spigot 插件，用于缓存离线玩家的 PlaceholderAPI 变量和 GermPlugin 装备槽数据，让玩家即使离线也能展示状态信息。

![Version](https://img.shields.io/badge/version-1.2.5-blue)
![API](https://img.shields.io/badge/Minecraft-1.13+-green)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.21-purple)

## 功能特性

### 1. PlaceholderAPI 变量离线缓存
- 将任意 PAPI 变量缓存到数据库
- 支持通过 `%pos_玩家名_变量名%` 格式查看离线玩家数据
- 可配置的缓存时间和更新周期
- 适用于排行榜、状态展示等场景

### 2. GermPlugin 装备槽离线展示
- 缓存玩家的装备配置（头盔、胸甲、护腿、靴子、主手、副手）
- 通过 DOS 系统展示离线玩家的装备外观
- 支持实体头部、胸部、腿部、鞋子、主手、副手槽位
- 可用于 NPC、展示框等实体展示

### 3. 装备槽备份与回滚
- 定期自动备份玩家装备配置
- 支持手动创建备份
- 可查看和回滚到历史备份
- 防止装备丢失或被恶意修改

### 4. Redis 多服同步
- 支持使用 Redis 代替数据库存储槽位数据
- 实现跨服数据同步
- 适用于 BungeeCord/Velocity 网络环境

## 依赖要求

### 硬依赖
- **PlaceholderAPI** 2.11+

### 软依赖（可选）
- **GermPlugin** - 装备槽管理（用于 DOS 功能）

## 安装方法

1. 下载最新版本的 `PlayerOfflineStatus.jar`
2. 将 jar 文件放入服务器的 `plugins` 文件夹
3. 启动服务器生成配置文件
4. 编辑 `plugins/PlayerOfflineStatus/setting.yml` 配置插件
5. 重启服务器或使用 `/pos reload` 重载配置

## 配置说明

### 离线变量配置 (`setting.yml`)

```yaml
# 玩家名称与变量的分隔符
placeholder:
  separator: "_"
  # 在线时是否显示实时变量
  proxy_online: true
  # 需要缓存的变量列表（不要带 % 或 {}）
  offline_placeholders:
    - "player_level"
    - "money"
    - "kills"
  # 变量不存在时的默认值
  offline_placeholder_default: ""
  # 更新间隔 (tick, 20tick = 1 秒)
  update_period: 600
  # 遍历玩家的间隔 (tick)
  queue_delay: 20
  # PAPI 缓存时间 (秒，-1 表示禁用)
  cache_time: 30
```

### GermPlugin 槽位配置

```yaml
germ:
  enable: false  # 是否启用
  dos_id: "pos"  # DOS 插件 ID
  proxy_online: true  # 在线时显示实时槽
  # 需要缓存的槽位
  offline_slots:
    - germplugin_armor_helmet
    - germplugin_armor_chestplate
    - germplugin_armor_leggings
    - germplugin_armor_boots
    - germplugin_main_hand
    - germplugin_off_hand
  update_period: 600  # 更新间隔 (tick)
  cache_time: 600     # DOS 缓存时间 (秒)
  slot_holder: false  # 是否接管萌芽槽
  
  # Redis 代理配置（可选）
  slot_holder_redis:
    enable: false
    host: "127.0.0.1"
    port: 6379
    database: 0
    password: ""
    use_ssl: false
    prefix: "PlayerOfflineStatus"
    cache_time: 15  # 本地缓存时间 (秒)
```

### 槽位备份配置

```yaml
germ_slot_backup:
  enable: false  # 是否启用备份
  dos_id: "posb"  # 备份 DOS ID
  dos_time_format: "yyyy-MM-dd HH:mm:ss"  # 时间格式
  dos_cache_time: 3  # DOS 缓存时间 (秒)
  slots: []  # 备份的槽位（留空为所有）
  max: 3  # 最大备份数量
  backup_on_quit: true  # 退出时立即备份
  period: 6000  # 备份周期 (tick)
```

## 命令使用

主命令：`/playerofflinestatus` (别名：`/pos`, `/poss`, `/post`)

| 命令 | 权限 | 描述 |
|------|------|------|
| `/pos placeholder save <玩家>` | OP | 保存某个玩家的 PAPI 变量 |
| `/pos placeholder all` | OP | 保存所有玩家的 PAPI 变量 |
| `/pos germ save <玩家>` | OP | 保存某个玩家的 Germ 槽位 |
| `/pos germ all` | OP | 保存所有玩家的 Germ 槽位 |
| `/pos backup show <玩家>` | OP | 查看某玩家的所有备份 |
| `/pos backup open <id> [页码]` | OP | 查看某个备份的内容 |
| `/pos backup rollback <玩家> <id>` | OP | 回滚某玩家的备份 |
| `/pos backup player <玩家>` | OP | 为玩家创建备份 |
| `/pos backup all` | OP | 为所有玩家创建备份 |
| `/pos reload` | OP | 重载配置 |
| `/pos reConnect` | OP | 重新连接数据库 |
| `/pos debug msg` | OP | 切换 Debug 消息 |
| `/pos debug sql` | OP | 切换 Debug SQL 日志 |
| `/pos debug cache` | OP | 输出缓存统计信息 |

## PlaceholderAPI 占位符格式

```
%pos_玩家名_变量名%
```

示例：
- `%pos_Steve_player_level%` - 显示 Steve 玩家的等级
- `%pos_Alex_money%` - 显示 Alex 玩家的金钱

## DOS 槽位格式

### 基础格式
```
pos<->玩家 ID@装备 id
```

### 批量设置（适用于实体）
```
# 一次设置 4 个装备（头、胸、腿、鞋）
pos<->玩家 ID@body

# 一次设置 6 个装备（4 个装备 + 主手 + 副手）
pos<->玩家 ID@body_with_hands
```

### 支持的装备 ID
- `germplugin_armor_helmet` - 头部
- `germplugin_armor_chestplate` - 胸部
- `germplugin_armor_leggings` - 腿部
- `germplugin_armor_boots` - 鞋子
- `germplugin_main_hand` - 主手
- `germplugin_off_hand` - 副手

## 编译项目

### 环境要求
- JDK 8+
- Gradle 7+

### 编译步骤

```bash
# 克隆项目
git clone <repository-url>
cd PlayerOfflineStatus

# 编译项目
./gradlew build

# 输出位置
# 未混淆：build/PlayerOfflineStatus-1.2.5.jar
# 混淆后：build/PlayerOfflineStatus-1.2.5-obfuscated.jar
```

### 构建配置

在 `gradle.properties` 中可以配置：
- `obfuscated` - 是否启用代码混淆
- `shrink` - 是否删除未使用代码
- `version` - 插件版本号

## 数据存储

插件使用以下数据库表：

| 表名 | 用途 |
|------|------|
| `PlayerPAPIs` | 存储玩家 PAPI 变量缓存 |
| `PlayerGermSlots` | 存储玩家 Germ 槽位数据 |
| `GermSlots` | Germ 槽位定义 |
| `GermSlotIds` | 槽位 ID 映射 |
| `GermSlotBackup` | 槽位备份记录 |

数据库配置在 `plugins/PlayerOfflineStatus/database.yml` 中修改。

## 技术架构

- **语言**: Kotlin + Java
- **构建工具**: Gradle (Kotlin DSL)
- **数据库框架**: JetBrains Exposed ORM
- **连接池**: HikariCP 4.0.3
- **缓存**: Guava Cache
- **代码混淆**: ProGuard 7.4.1
- **打包**: ShadowJar

## 性能优化

1. **缓存机制**: Guava Cache 缓存 PAPI 变量和 Germ 槽位，可配置过期时间
2. **异步处理**: 所有数据库操作异步执行，定时任务异步更新缓存
3. **批量操作**: 支持批量更新所有玩家数据，队列式遍历避免卡顿
4. **冷却机制**: 防止高频读取导致数据库繁忙

## 常见问题

### Q: 插件不工作怎么办？
A: 确保已安装 PlaceholderAPI，并检查控制台是否有错误信息。

### Q: 如何查看调试信息？
A: 使用 `/pos debug msg` 开启调试消息，使用 `/pos debug sql` 开启 SQL 日志。

### Q: Redis 连接失败怎么办？
A: 检查 Redis 服务是否运行，确认 `setting.yml` 中的 Redis 配置正确。

### Q: 如何调整缓存更新时间？
A: 修改 `setting.yml` 中的 `update_period` 参数（单位：tick，20tick = 1 秒）。

## 开发者

- **作者**: Iseason
- **QQ**: 1347811744

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

## 更新日志

### v1.2.5
- 优化缓存机制
- 修复已知问题
- 改进 Redis 支持

---

如有问题或建议，欢迎通过 QQ 联系作者或提交 Issue。
