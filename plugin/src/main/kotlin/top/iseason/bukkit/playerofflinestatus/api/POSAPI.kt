package top.iseason.bukkit.playerofflinestatus.api

import org.bukkit.inventory.ItemStack
import com.google.common.cache.CacheStats

/**
 * PlayerOfflineStatus 公开 API 接口
 *
 * 供其他插件通过 Bukkit Services API 调用，用于读取离线玩家的萌芽背包槽位物品和 PAPI 变量缓存。
 *
 * 使用方法：
 * ```java
 * RegisteredServiceProvider<POSAPI> provider = getServer().getServicesManager()
 *     .getRegistration(POSAPI.class);
 * if (provider != null) {
 *     POSAPI api = provider.getProvider();
 *     String level = api.getOfflinePAPI("Steve", "player_level");
 * }
 * ```
 */
interface POSAPI {

    /**
     * 获取指定玩家某个萌芽槽位的物品（支持在线/离线）。
     *
     * @param playerName 玩家名称
     * @param identity 萌芽槽位 identity（如 "germplugin_armor_helmet"）
     * @return 槽位中的物品，若无数据则返回 null
     */
    fun getOfflineGermSlot(playerName: String, identity: String): ItemStack?

    /**
     * 获取指定玩家所有已缓存的萌芽槽位物品（支持在线/离线）。
     *
     * @param playerName 玩家名称
     * @return 槽位 identity -> ItemStack 的映射，若无数据则返回空 Map
     */
    fun getOfflineGermSlots(playerName: String): Map<String, ItemStack>

    /**
     * 获取指定离线玩家某个 PAPI 变量的缓存值。
     *
     * @param playerName 玩家名称
     * @param papi PAPI 变量标识符（不含 %）
     * @return 变量的值，若无缓存数据则返回 null
     */
    fun getOfflinePAPI(playerName: String, papi: String): String?

    /**
     * 获取指定离线玩家所有已缓存的 PAPI 变量。
     *
     * @param playerName 玩家名称
     * @return papi 变量标识符 -> 值 的映射，若无数据则返回空 Map
     */
    fun getOfflineAllPAPIs(playerName: String): Map<String, String>

    /**
     * 检查萌芽槽位功能是否可用。
     *
     * @return true 表示萌芽插件已加载且槽位功能已启用
     */
    fun isGermSlotAvailable(): Boolean

    /**
     * 获取当前所有已注册的萌芽槽位 identity 列表。
     *
     * @return identity 集合，若萌芽不可用则返回空集合
     */
    fun getAllGermIdentities(): Collection<String>

    /**
     * 获取 PAPI 缓存的统计信息。
     *
     * @return CacheStats 对象，若 PAPI 功能未启用则返回 null
     */
    fun getPAPICacheStats(): CacheStats?

    /**
     * 获取 Germ 槽位缓存的统计信息。
     *
     * @return CacheStats 对象，若 Germ 功能未启用则返回 null
     */
    fun getGermSlotCacheStats(): CacheStats?

    /**
     * 获取 Redis 槽位缓存的统计信息。
     *
     * @return CacheStats 对象，若 Redis 模式未启用则返回 null
     */
    fun getRedisCacheStats(): CacheStats?
}
