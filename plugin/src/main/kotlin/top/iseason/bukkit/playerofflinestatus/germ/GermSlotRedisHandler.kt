package top.iseason.bukkit.playerofflinestatus.germ

import com.germ.germplugin.api.GermSlotAPI
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.util.SafeEncoder
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkittemplate.DisableHook
import top.iseason.bukkittemplate.debug.SimpleLogger
import top.iseason.bukkittemplate.debug.debug
import top.iseason.bukkittemplate.debug.info
import top.iseason.bukkittemplate.debug.warn
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.checkAir
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.toBase64
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.toByteArray
import java.nio.charset.Charset


object GermSlotRedisHandler : GermSlotAPI.SlotDAOHandler {
    private val PREFIX = Config.germ__slot_holder_redis__prefix
    private const val IDENTITYS_KEY = "Identitys"
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

    override fun getAllIdentitys(): MutableCollection<String> {
        return jedis.smembers(getRedisKey(IDENTITYS_KEY))
    }

    override fun saveToIdentity(name: String?, identity: String?, item: ItemStack?) {
        if (name == null || identity == null) return
        val itemKey = getItemKey(name, identity)
        if (item.checkAir()) jedis.del(itemKey)
        else {
            jedis.set(itemKey, item!!.toBase64())
            jedis.sadd(getRedisKey(IDENTITYS_KEY), identity)
        }
    }

    override fun getFromIdentity(name: String?, identity: String?): ItemStack {
        if (name == null || identity == null) return air
        val currentTimeMillis = System.currentTimeMillis()
        val itemKey = getItemKey(name, identity)
        val base64 = jedis.get(itemKey) ?: return air
        val item = try {
            ItemUtils.fromBase64ToItemStack(base64)
        } catch (e: Exception) {
            warn("物品反序列化失败, RedisKey: $itemKey")
            air
        }
        if (SimpleLogger.isDebug) {
            info("已从redis获取物品: $name@$identity 耗时: ${System.currentTimeMillis() - currentTimeMillis} 毫秒")
        }
        return item
    }

    private fun getRedisKey(key: String) = "$PREFIX:$key"
    private fun getItemKey(name: String, identity: String) = "$PREFIX:germ_slot:$name:$identity"
}