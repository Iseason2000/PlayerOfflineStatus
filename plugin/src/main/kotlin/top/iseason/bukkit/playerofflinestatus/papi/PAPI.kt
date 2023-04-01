package top.iseason.bukkit.playerofflinestatus.papi

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
import java.util.concurrent.ConcurrentHashMap

object PAPI : PlaceholderExpansion() {
    private val papiCache = ConcurrentHashMap<String, String>()
    private val noCache = ConcurrentHashMap.newKeySet<String>()
    private val coolDown = CoolDown<String>()
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
        val split = params.split('_', limit = 2)
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

    /**
     * 获取缓存的变量
     */
    private fun getCachePAPI(key: String, name: String, papi: String): String? {
        var value = papiCache[key]
        //命中缓存不过期
        val papiCacheTime = Config.placeholder__cache_time
        if (value != null && papiCacheTime != 0L &&
            (papiCacheTime < 0 || coolDown.check(key, papiCacheTime))
        ) {
            return value
        }
        // 未命中的缓存
        val noCaChe = noCache.contains(key)
        if (noCaChe && coolDown.check("nocache-${papi}", 2000)) return null
        value = dbTransaction {
            PlayerPAPIs.slice(PlayerPAPIs.value).select {
                PlayerPAPIs.name eq name and (PlayerPAPIs.papi eq papi)
            }.limit(1).firstOrNull()?.get(PlayerPAPIs.value)
        }
        //未命中的警告
        if (value == null) {
            noCache.add(key)
            warn("变量 $papi 没有数据缓存，请检查名称或配置缓存!")
        } else if (noCaChe) noCache.remove(key)
        //更新缓存
        if (value != null)
            papiCache[key] = value
        return value
    }
}