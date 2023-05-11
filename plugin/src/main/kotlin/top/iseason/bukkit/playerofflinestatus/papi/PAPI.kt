package top.iseason.bukkit.playerofflinestatus.papi

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheStats
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.dto.PlayerPAPIs
import top.iseason.bukkittemplate.BukkitTemplate
import top.iseason.bukkittemplate.config.dbTransaction
import top.iseason.bukkittemplate.debug.warn
import top.iseason.bukkittemplate.utils.other.CoolDown
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

object PAPI : PlaceholderExpansion() {
    private val papiCache = CacheBuilder.newBuilder()
        .softValues()
        .expireAfterWrite(max(Config.placeholder__cache_time, 0), TimeUnit.SECONDS)
        .recordStats()
        .build<String, String>()
    private val noCache = ConcurrentHashMap.newKeySet<String>()
    private val coolDown = CoolDown<String>()

    fun getCacheStats(): CacheStats = papiCache.stats()

    override fun getIdentifier(): String {
        return "pos"
    }

    override fun getAuthor(): String {
        return BukkitTemplate.getPlugin().description.authors.joinToString(",")
    }

    override fun getVersion(): String {
        return BukkitTemplate.getPlugin().description.version
    }

    // pos_player_xxxxxx
    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val split = params.split(Config.placeholder__separator, limit = 2)
        if (split.size != 2) return null
        val playerName = split[0]
        val p = Bukkit.getPlayer(playerName)
        val papi = split[1]
        //在线
        if (p != null && Config.placeholder__proxy_online) {
            return PlayerPAPIs.getPAPIResult(p, papi)
        }
        return getCachePAPI(params, playerName, papi)
    }

    fun putCache(key: String, papi: String) {
        papiCache.put(key, papi)
    }

    /**
     * 获取缓存的变量
     */
    private fun getCachePAPI(key: String, name: String, papi: String): String? {
        // 未命中的缓存
        val noCaChe = noCache.contains(key)
        if (noCaChe && coolDown.check("nocache-${papi}", 2000)) return null
        val callable = Callable {
            val value = dbTransaction(true) {
                PlayerPAPIs.slice(PlayerPAPIs.value).select {
                    PlayerPAPIs.name eq name and (PlayerPAPIs.papi eq papi)
                }.limit(1).firstOrNull()?.get(PlayerPAPIs.value)
            }
            if (value == null) {
                noCache.add(key)
                warn("变量 $key 没有数据缓存，请检查名称或配置缓存!")
            } else if (noCaChe) noCache.remove(key)
            return@Callable value ?: "961eeb25-56e1-4638-8ed8-38a79e39118e"
        }
        //不要缓存
        if (Config.placeholder__cache_time < 0) {
            return callable.call()
        }
        val v = papiCache.get(key, callable)
        if (v == "961eeb25-56e1-4638-8ed8-38a79e39118e") {
            papiCache.invalidate(key)
            return null
        }
        return v
    }
}