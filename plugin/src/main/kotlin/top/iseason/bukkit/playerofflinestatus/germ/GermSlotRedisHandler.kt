package top.iseason.bukkit.playerofflinestatus.germ

import com.germ.germplugin.api.GermSlotAPI
import com.germ.germplugin.api.event.GermClientLinkedEvent
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheStats
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.JedisPooled
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkittemplate.DisableHook
import top.iseason.bukkittemplate.debug.SimpleLogger
import top.iseason.bukkittemplate.debug.info
import top.iseason.bukkittemplate.debug.warn
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.checkAir
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.toBase64
import top.iseason.bukkittemplate.utils.other.EasyCoolDown
import java.util.concurrent.TimeUnit
import kotlin.math.max


object GermSlotRedisHandler : GermSlotAPI.SlotDAOHandler, org.bukkit.event.Listener {
    private val PREFIX = Config.germ__slot_holder_redis__prefix
    private const val IDENTITIES_KEY = "Identities"
    private val cache = CacheBuilder.newBuilder()
        .expireAfterWrite(max(Config.germ__slot_holder_redis__cache_time, 0), TimeUnit.SECONDS)
        .softValues()
        .recordStats()
        .build<String, ItemStack>()
    private var identity: Collection<String> = emptyList()
    fun getCacheStats(): CacheStats = cache.stats()

    private val jedis = JedisPooled(
        ConnectionPoolConfig(),
        Config.germ__slot_holder_redis__host,
        Config.germ__slot_holder_redis__port,
        2000,
        Config.germ__slot_holder_redis__password,
        Config.germ__slot_holder_redis__database,
        Config.germ__slot_holder_redis__use_ssl,
    )
    private val air = ItemStack(Material.AIR)

    init {
        DisableHook.addTask { jedis.close() }
    }

    override fun getFromIdentitys(name: String?, ids: MutableCollection<String>?): Map<String, ItemStack> {
        if (name == null || ids.isNullOrEmpty()) return emptyMap()
        return ids.associateWith { getFromIdentity(name, it) }
    }

    override fun getAllIdentitys(): Collection<String> {
        if (EasyCoolDown.check("germ_slot_redis_identities_cache", 600000)) {
            return identity
        }
        identity = jedis.smembers(getRedisKey(IDENTITIES_KEY))
        return identity
    }

    override fun saveToIdentity(name: String?, identity: String?, item: ItemStack?) {
        if (name == null || identity == null) return
        val itemKey = getItemKey(name, identity)
        if (cache.getIfPresent(itemKey) == item) return
        if (item.checkAir()) jedis.del(itemKey)
        else {
            jedis.set(itemKey, item!!.toBase64())
            jedis.sadd(getRedisKey(IDENTITIES_KEY), identity)
        }
        cache.put(itemKey, item ?: air)
    }

    override fun getFromIdentity(name: String?, identity: String?): ItemStack {
        if (name == null || identity == null) return air
        val itemKey = getItemKey(name, identity)
        val get = cache.get(itemKey) {
            val base64 = jedis.get(itemKey) ?: return@get air
            val item = try {
                ItemUtils.fromBase64ToItemStack(base64)
            } catch (e: Exception) {
                warn("物品反序列化失败, RedisKey: $itemKey")
                air
            }
            item
        }

        return get
    }

    private fun getRedisKey(key: String) = "$PREFIX:$key"
    private fun getItemKey(name: String, identity: String) = "$PREFIX:germ_slot:$name:$identity"

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerLogin(event: GermClientLinkedEvent) {
        val player = event.player.name
        allIdentitys.forEach {
            cache.invalidate(getItemKey(player, it))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player.name
        allIdentitys.forEach {
            cache.invalidate(getItemKey(player, it))
        }
    }
}