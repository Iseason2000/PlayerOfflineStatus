package top.iseason.bukkit.playerofflinestatus.api

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.dto.PlayerGermSlots
import top.iseason.bukkit.playerofflinestatus.dto.PlayerPAPIs
import top.iseason.bukkit.playerofflinestatus.germ.GermHook
import top.iseason.bukkit.playerofflinestatus.germ.GermSlotHandler
import top.iseason.bukkit.playerofflinestatus.germ.GermSlotRedisHandler
import top.iseason.bukkittemplate.config.dbTransaction

import com.google.common.cache.CacheStats

/**
 * PlayerOfflineStatus 对外 API
 *
 * 供其他插件调用，用于读取离线玩家的萌芽背包槽位物品和 PAPI 变量缓存。
 *
 * 注意：
 * - 所有方法均为同步调用，建议在异步线程中使用，避免阻塞主线程。
 * - 槽位物品和 PAPI 变量均来自数据库缓存，需要玩家曾经上线并触发过缓存写入。
 *
 * @author Iseason
 * @version 1.2.5
 *
 * ## 使用示例
 * ```kotlin
 * // 获取离线玩家的装备槽物品
 * val helmet = PlayerOfflineStatusAPI.getOfflineGermSlot("Steve", "germplugin_armor_helmet")
 *
 * // 获取离线玩家的所有装备槽
 * val slots = PlayerOfflineStatusAPI.getOfflineGermSlots("Steve")
 *
 * // 获取离线玩家的 PAPI 变量值
 * val balance = PlayerOfflineStatusAPI.getOfflinePAPI("Steve", "vault_eco_balance")
 * ```
 *
 * ## Bukkit Services API 使用示例
 * ```java
 * RegisteredServiceProvider<POSAPI> provider = getServer().getServicesManager()
 *     .getRegistration(POSAPI.class);
 * if (provider != null) {
 *     POSAPI api = provider.getProvider();
 *     String level = api.getOfflinePAPI("Steve", "player_level");
 * }
 * ```
 */
object PlayerOfflineStatusAPI : POSAPI {

    // ==================== 萌芽槽位物品 ====================

    /**
     * 获取指定玩家某个萌芽槽位的物品（支持在线/离线）。
     *
     * 优先级：
     * 1. 若玩家在线且配置了 germ__proxy_online，直接从 GermSlotAPI 读取实时数据。
     * 2. 若启用了 Redis 槽位代理（germ__slot_holder_redis__enable），从 Redis 读取。
     * 3. 若启用了数据库槽位代理（germ__slot_holder），从内存缓存/数据库读取。
     * 4. 若启用了离线槽位缓存（germ__enable），从 player_germ_slot 表读取。
     * 5. 以上均不满足则返回 null。
     *
     * @param playerName 玩家名称
     * @param identity   萌芽槽位 identity（如 "germplugin_armor_helmet"）
     * @return 槽位中的物品，若无数据则返回 null
     */
    override fun getOfflineGermSlot(playerName: String, identity: String): ItemStack? {
        if (!GermHook.hasHooked) return null

        // 在线玩家且开启了代理，直接从 GermSlotAPI 读取
        val onlinePlayer = Bukkit.getPlayerExact(playerName)
        if (onlinePlayer != null && Config.germ__proxy_online) {
            return try {
                com.germ.germplugin.api.GermSlotAPI.getItemStackFromIdentity(onlinePlayer, identity)
            } catch (e: Exception) {
                null
            }
        }

        // Redis 槽位代理模式
        if (Config.germ__slot_holder_redis__enable) {
            return runCatching {
                val item = GermSlotRedisHandler.getFromIdentity(playerName, identity)
                if (item.type == org.bukkit.Material.AIR) null else item
            }.getOrNull()
        }

        // 数据库槽位代理模式（GermSlotHandler 内存缓存 + 数据库）
        if (Config.germ__slot_holder) {
            return runCatching {
                val item = GermSlotHandler.getFromIdentity(playerName, identity)
                if (item.type == org.bukkit.Material.AIR) null else item
            }.getOrNull()
        }

        // 离线槽位缓存模式（player_germ_slot 表）
        if (Config.germ__enable) {
            return runCatching {
                val blob = dbTransaction(true) {
                    PlayerGermSlots
                        .slice(PlayerGermSlots.items)
                        .select(PlayerGermSlots.name eq playerName)
                        .limit(1)
                        .firstOrNull()
                        ?.get(PlayerGermSlots.items)
                } ?: return null
                PlayerGermSlots.fromByteArray(blob.bytes)[identity]
            }.getOrNull()
        }

        return null
    }

    /**
     * 获取指定玩家所有已缓存的萌芽槽位物品（支持在线/离线）。
     *
     * 优先级与 [getOfflineGermSlot] 相同。
     *
     * @param playerName 玩家名称
     * @return 槽位 identity -> ItemStack 的映射，若无数据则返回空 Map
     */
    override fun getOfflineGermSlots(playerName: String): Map<String, ItemStack> {
        if (!GermHook.hasHooked) return emptyMap()

        // 在线玩家且开启了代理，直接从 GermSlotAPI 读取所有已知槽位
        val onlinePlayer = Bukkit.getPlayerExact(playerName)
        if (onlinePlayer != null && Config.germ__proxy_online) {
            return runCatching {
                val identities = when {
                    Config.germ__slot_holder_redis__enable -> GermSlotRedisHandler.allIdentitys
                    Config.germ__slot_holder -> GermSlotHandler.allIdentitys
                    else -> Config.germ__offline_slots
                }
                identities.associateWith {
                    com.germ.germplugin.api.GermSlotAPI.getItemStackFromIdentity(onlinePlayer, it)
                }.filterValues { it.type != org.bukkit.Material.AIR }
            }.getOrElse { emptyMap() }
        }

        // Redis 槽位代理模式
        if (Config.germ__slot_holder_redis__enable) {
            return runCatching {
                val identities = GermSlotRedisHandler.allIdentitys
                GermSlotRedisHandler.getFromIdentitys(playerName, identities.toMutableList())
                    .filterValues { it.type != org.bukkit.Material.AIR }
            }.getOrElse { emptyMap() }
        }

        // 数据库槽位代理模式（GermSlotHandler 内存缓存 + 数据库）
        if (Config.germ__slot_holder) {
            return runCatching {
                val identities = GermSlotHandler.allIdentitys
                GermSlotHandler.getFromIdentitys(playerName, identities)
                    .filterValues { it.type != org.bukkit.Material.AIR }
            }.getOrElse { emptyMap() }
        }

        // 离线槽位缓存模式（player_germ_slot 表）
        if (Config.germ__enable) {
            return runCatching {
                val blob = dbTransaction(true) {
                    PlayerGermSlots
                        .slice(PlayerGermSlots.items)
                        .select(PlayerGermSlots.name eq playerName)
                        .limit(1)
                        .firstOrNull()
                        ?.get(PlayerGermSlots.items)
                } ?: return emptyMap()
                PlayerGermSlots.fromByteArray(blob.bytes)
                    .filterValues { it.type != org.bukkit.Material.AIR }
            }.getOrElse { emptyMap() }
        }

        return emptyMap()
    }

    // ==================== PAPI 变量 ====================

    /**
     * 获取指定离线玩家某个 PAPI 变量的缓存值。
     *
     * 优先级：
     * 1. 若玩家在线且配置了 placeholder__proxy_online，直接实时解析 PAPI 变量。
     * 2. 否则从数据库缓存（player_papi 表）中读取。
     *
     * @param playerName 玩家名称
     * @param papi       PAPI 变量标识符，格式为 "插件名_变量名"（不含 %），
     *                   例如 "vault_eco_balance"
     * @return 变量的值，若无缓存数据则返回 null
     */
    override fun getOfflinePAPI(playerName: String, papi: String): String? {
        // 在线玩家且开启了代理，直接实时解析
        val onlinePlayer = Bukkit.getPlayerExact(playerName)
        if (onlinePlayer != null && Config.placeholder__proxy_online) {
            return runCatching { PlayerPAPIs.getPAPIResult(onlinePlayer, papi) }.getOrNull()
        }

        // 从数据库缓存读取
        return runCatching {
            dbTransaction(true) {
                PlayerPAPIs
                    .slice(PlayerPAPIs.value)
                    .select { PlayerPAPIs.name eq playerName and (PlayerPAPIs.papi eq papi) }
                    .limit(1)
                    .firstOrNull()
                    ?.get(PlayerPAPIs.value)
            }
        }.getOrNull()
    }

    /**
     * 获取指定离线玩家所有已缓存的 PAPI 变量。
     *
     * @param playerName 玩家名称
     * @return papi 变量标识符 -> 值 的映射，若无数据则返回空 Map
     */
    override fun getOfflineAllPAPIs(playerName: String): Map<String, String> {
        // 在线玩家且开启了代理，直接实时解析所有配置的变量
        val onlinePlayer = Bukkit.getPlayerExact(playerName)
        if (onlinePlayer != null && Config.placeholder__proxy_online) {
            return runCatching {
                Config.placeholder__offline_placeholders.mapNotNull { papi ->
                    val result = PlayerPAPIs.getPAPIResult(onlinePlayer, papi) ?: return@mapNotNull null
                    papi to result
                }.toMap()
            }.getOrElse { emptyMap() }
        }

        // 从数据库缓存读取所有变量
        return runCatching {
            dbTransaction(true) {
                PlayerPAPIs
                    .slice(PlayerPAPIs.papi, PlayerPAPIs.value)
                    .select { PlayerPAPIs.name eq playerName }
                    .associate { it[PlayerPAPIs.papi] to it[PlayerPAPIs.value] }
            }
        }.getOrElse { emptyMap() }
    }

    // ==================== 工具方法 ====================

    /**
     * 检查萌芽槽位功能是否可用。
     *
     * @return true 表示萌芽插件已加载且槽位功能已启用
     */
    override fun isGermSlotAvailable(): Boolean {
        return GermHook.hasHooked && (Config.germ__enable || Config.germ__slot_holder || Config.germ__slot_holder_redis__enable)
    }

    /**
     * 获取当前所有已注册的萌芽槽位 identity 列表。
     *
     * @return identity 集合，若萌芽不可用则返回空集合
     */
    override fun getAllGermIdentities(): Collection<String> {
        if (!GermHook.hasHooked) return emptyList()
        return when {
            Config.germ__slot_holder_redis__enable -> runCatching { GermSlotRedisHandler.allIdentitys }.getOrElse { emptyList() }
            Config.germ__slot_holder -> GermSlotHandler.allIdentitys.toList()
            Config.germ__enable -> Config.germ__offline_slots
            else -> emptyList()
        }
    }

    // ==================== 缓存统计信息 ====================

    /**
     * 获取 PAPI 缓存的统计信息。
     *
     * @return CacheStats 对象，包含命中率、加载时间等统计信息
     *         若 PAPI 功能未启用则返回 null
     */
    override fun getPAPICacheStats(): CacheStats? {
        return try {
            val clazz = Class.forName("top.iseason.bukkit.playerofflinestatus.papi.PAPI")
            val method = clazz.getDeclaredMethod("getCacheStats")
            method.isAccessible = true
            method.invoke(null) as? CacheStats
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取 Germ 槽位缓存的统计信息。
     *
     * @return CacheStats 对象，包含命中率、加载时间等统计信息
     *         若 Germ 功能未启用则返回 null
     */
    override fun getGermSlotCacheStats(): CacheStats? {
        return try {
            val clazz = Class.forName("top.iseason.bukkit.playerofflinestatus.germ.GermListener")
            val method = clazz.getDeclaredMethod("getCacheStats")
            method.isAccessible = true
            method.invoke(null) as? CacheStats
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取 Redis 槽位缓存的统计信息（仅在使用 Redis 模式时有效）。
     *
     * @return CacheStats 对象，包含命中率、加载时间等统计信息
     *         若 Redis 模式未启用则返回 null
     */
    override fun getRedisCacheStats(): CacheStats? {
        if (!Config.germ__slot_holder_redis__enable) return null
        return try {
            val clazz = Class.forName("top.iseason.bukkit.playerofflinestatus.germ.GermSlotRedisHandler")
            val method = clazz.getDeclaredMethod("getCacheStats")
            method.isAccessible = true
            method.invoke(null) as? CacheStats
        } catch (e: Exception) {
            null
        }
    }
}
