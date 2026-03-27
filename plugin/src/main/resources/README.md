# PlayerOfflineStatus 开发者 API 文档

## 概述

PlayerOfflineStatus 插件为其他插件提供 API，用于读取离线玩家的萌芽（GermPlugin）装备槽位物品和 PlaceholderAPI 变量缓存。

**最新版本**: 1.2.5
**包名**: `top.iseason.bukkit.playerofflinestatus.api`
**类名**: `PlayerOfflineStatusAPI`

---

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>top.iseason.bukkit</groupId>
    <artifactId>PlayerOfflineStatus</artifactId>
    <version>1.2.5</version>
    <scope>provided</scope>
</dependency>
```

### Kotlin 示例

```kotlin
import top.iseason.bukkit.playerofflinestatus.api.PlayerOfflineStatusAPI

// 获取离线玩家头盔
val helmet = PlayerOfflineStatusAPI.getOfflineGermSlot("Steve", "germplugin_armor_helmet")

// 获取离线玩家的经济余额
val balance = PlayerOfflineStatusAPI.getOfflinePAPI("Steve", "vault_eco_balance")
```

---

## API 方法详解

### 1. 获取单个萌芽槽位物品

```kotlin
fun getOfflineGermSlot(playerName: String, identity: String): ItemStack?
```

**参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| `playerName` | String | 玩家名称 |
| `identity` | String | 萌芽槽位标识符 |

**返回值**: `ItemStack?` - 槽位中的物品，无数据返回 null

**数据读取优先级**:
1. 在线玩家且启用代理 → 实时 GermSlotAPI 数据
2. Redis 代理模式 → Redis 缓存
3. 数据库代理模式 → 内存缓存/数据库
4. 离线缓存模式 → player_germ_slot 表

**示例**:
```kotlin
// 获取玩家头盔
val helmet = PlayerOfflineStatusAPI.getOfflineGermSlot("Steve", "germplugin_armor_helmet")

// 获取玩家主手物品
val mainHand = PlayerOfflineStatusAPI.getOfflineGermSlot("Steve", "germplugin_main_hand")

// 获取玩家副手物品
val offHand = PlayerOfflineStatusAPI.getOfflineGermSlot("Steve", "germplugin_off_hand")
```

---

### 2. 获取所有萌芽槽位物品

```kotlin
fun getOfflineGermSlots(playerName: String): Map<String, ItemStack>
```

**参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| `playerName` | String | 玩家名称 |

**返回值**: `Map<String, ItemStack>` - identity 到 ItemStack 的映射，无数据返回空 Map

**示例**:
```kotlin
val allSlots = PlayerOfflineStatusAPI.getOfflineGermSlots("Steve")
allSlots.forEach { (identity, item) ->
    println("${item.displayName} in slot: $identity")
}
```

---

### 3. 获取单个 PAPI 变量

```kotlin
fun getOfflinePAPI(playerName: String, papi: String): String?
```

**参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| `playerName` | String | 玩家名称 |
| `papi` | String | PAPI 变量标识符（不含 %），格式: `插件名_变量名` |

**返回值**: `String?` - 变量值，无缓存返回 null

**示例**:
```kotlin
// 获取玩家经济余额
val balance = PlayerOfflineStatusAPI.getOfflinePAPI("Steve", "vault_eco_balance")

// 获取玩家点券
val points = PlayerOfflineStatusAPI.getOfflinePAPI("Steve", "points_balance")

// 获取玩家等级
val level = PlayerOfflineStatusAPI.getOfflinePAPI("Steve", "level_system_level")
```

---

### 4. 获取所有 PAPI 变量

```kotlin
fun getOfflineAllPAPIs(playerName: String): Map<String, String>
```

**参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| `playerName` | String | 玩家名称 |

**返回值**: `Map<String, String>` - papi 标识符到值的映射

**示例**:
```kotlin
val allPAPIs = PlayerOfflineStatusAPI.getOfflineAllPAPIs("Steve")
allPAPIs.forEach { (papi, value) ->
    println("$papi = $value")
}
```

---

### 5. 检查功能可用性

```kotlin
fun isGermSlotAvailable(): Boolean
```

**返回值**: `Boolean` - true 表示萌芽插件已加载且槽位功能已启用

**示例**:
```kotlin
if (PlayerOfflineStatusAPI.isGermSlotAvailable()) {
    val helmet = PlayerOfflineStatusAPI.getOfflineGermSlot("Steve", "germplugin_armor_helmet")
}
```

---

### 6. 获取所有槽位标识符

```kotlin
fun getAllGermIdentities(): Collection<String>
```

**返回值**: `Collection<String>` - 所有已注册的槽位 identity 列表

**示例**:
```kotlin
val identities = PlayerOfflineStatusAPI.getAllGermIdentities()
println("可用槽位: ${identities.joinToString()}")
```

---

## 可用的槽位 Identity 列表

| Identity | 说明 |
|----------|------|
| `germplugin_armor_helmet` | 头盔 |
| `germplugin_armor_chestplate` | 胸甲 |
| `germplugin_armor_leggings` | 护腿 |
| `germplugin_armor_boots` | 鞋子 |
| `germplugin_main_hand` | 主手物品 |
| `germplugin_off_hand` | 副手物品 |

---

## Java 兼容性

由于 API 使用了 Kotlin 的默认参数和命名参数，Java 调用时需要显式传递所有参数：

```java
import top.iseason.bukkit.playerofflinestatus.api.PlayerOfflineStatusAPI;
import org.bukkit.inventory.ItemStack;

// 获取离线玩家头盔
ItemStack helmet = PlayerOfflineStatusAPI.INSTANCE.getOfflineGermSlot("Steve", "germplugin_armor_helmet");

// 获取离线玩家 PAPI 变量
String balance = PlayerOfflineStatusAPI.INSTANCE.getOfflinePAPI("Steve", "vault_eco_balance");

// 检查功能是否可用
boolean available = PlayerOfflineStatusAPI.INSTANCE.isGermSlotAvailable();
```

---

## 注意事项

1. **线程安全**: 所有方法均为同步调用，建议在异步线程中使用，避免阻塞主线程
2. **数据依赖**: 槽位物品和 PAPI 变量均来自数据库缓存，玩家必须曾经上线并触发过缓存写入
3. **插件依赖**: 使用 API 前请确保 PlayerOfflineStatus 插件已加载

---

## 错误处理

所有方法都使用 `runCatching` 进行异常处理，API 不会抛出异常，失败时返回 null 或空集合：

```kotlin
// 安全调用示例
val helmet = PlayerOfflineStatusAPI.getOfflineGermSlot("Steve", "germplugin_armor_helmet")
if (helmet != null) {
    // 处理物品
} else {
    // 无数据或发生错误
}
```

---

## 完整示例

### Kotlin - 检查玩家装备价值

```kotlin
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import top.iseason.bukkit.playerofflinestatus.api.PlayerOfflineStatusAPI

fun calculatePlayerEquipmentValue(playerName: String): Double {
    val slots = PlayerOfflineStatusAPI.getOfflineGermSlots(playerName)
    return slots.values.sumOf { item ->
        // 简单的价值计算逻辑
        when (item.type) {
            Material.DIAMOND_HELMET -> 500.0
            Material.DIAMOND_CHESTPLATE -> 800.0
            Material.DIAMOND_LEGGINGS -> 700.0
            Material.DIAMOND_BOOTS -> 400.0
            Material.DIAMOND_SWORD -> 300.0
            else -> 0.0
        }
    }
}
```

### Java - 获取玩家经济信息

```java
import top.iseason.bukkit.playerofflinestatus.api.PlayerOfflineStatusAPI;

public class EconomyChecker {

    public void checkPlayerBalance(String playerName) {
        // 检查功能是否可用
        if (!PlayerOfflineStatusAPI.INSTANCE.isGermSlotAvailable()) {
            System.out.println("PlayerOfflineStatus is not available");
            return;
        }

        // 获取经济余额
        String balance = PlayerOfflineStatusAPI.INSTANCE.getOfflinePAPI(playerName, "vault_eco_balance");
        if (balance != null) {
            System.out.println(playerName + " 的经济余额: " + balance);
        } else {
            System.out.println("无法获取 " + playerName + " 的经济数据");
        }
    }
}
```

---

## 更新日志

### v1.2.5
- 新增完整的对外 API
- 支持萌芽槽位物品离线读取
- 支持 PAPI 变量离线读取
- 提供工具方法获取功能状态和统计信息

---

## 联系方式

- 作者: Iseason
- QQ: 1347811744
