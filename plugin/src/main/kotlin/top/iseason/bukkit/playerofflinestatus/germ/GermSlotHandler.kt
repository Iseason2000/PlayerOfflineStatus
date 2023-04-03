package top.iseason.bukkit.playerofflinestatus.germ

import com.germ.germplugin.api.GermSlotAPI
import com.germ.germplugin.api.event.GermClientLinkedEvent
import com.germ.germplugin.api.event.gui.GermGuiOpenEvent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import redis.clients.jedis.JedisPooled
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.dto.GermSlotIds
import top.iseason.bukkit.playerofflinestatus.dto.GermSlots
import top.iseason.bukkittemplate.debug.debug
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.checkAir
import top.iseason.bukkittemplate.utils.other.runAsync
import top.iseason.bukkittemplate.utils.other.submit
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

object GermSlotHandler : GermSlotAPI.SlotDAOHandler, org.bukkit.event.Listener {
    private val map = ConcurrentHashMap<String, ItemStack>()
    internal val keys = ConcurrentHashMap.newKeySet<String>()
    private val air = ItemStack(Material.AIR)

    fun init() {
        GermSlotIds.download()
        val germSlotSyncPeriod = Config.germ__slot_sync_period
        if (germSlotSyncPeriod > 0) {
            submit(async = true, period = germSlotSyncPeriod, delay = germSlotSyncPeriod) {
                updateGracefully()
            }
        }
    }

    override fun getFromIdentitys(name: String?, ids: MutableCollection<String>?): Map<String, ItemStack> {
        if (name == null || ids == null) return mutableMapOf()
        return ids
            .associateWith {
                val player = Bukkit.getPlayer(name)
                if (player != null)
                    GermSlotAPI.getItemStackFromIdentity(player, it)
                else getFromIdentity(name, it)
            }
    }

    override fun getFromIdentity(name: String?, identity: String?): ItemStack {
        if (name == null || identity == null) return air
        if (!keys.contains(identity)) return air
        val key = GermSlots.getKey(name, identity)
        return map.computeIfAbsent(key) { GermSlots.getByKey(key) ?: air }
    }

    override fun getAllIdentitys(): MutableCollection<String> {
        return keys
    }

    override fun saveToIdentity(name: String?, identity: String?, item: ItemStack?) {
        if (name == null || identity == null) return
        val key = GermSlots.getKey(name, identity)
        if (item.checkAir()) {
            map[key] = air
        } else {
            map[key] = item!!
            keys.add(identity)
        }
        if (Config.germ__slot_sync_period == 0L) {
            runAsync {
                GermSlots.setItem(key, item)
            }
        }
    }

    private fun removePlayerCache(player: String) {
        keys.forEach {
            map.remove(GermSlots.getKey(player, it))
        }
    }

    private fun getPlayerCache(player: String) {
        return keys.forEach { getFromIdentity(player, it) }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerLogin(event: GermClientLinkedEvent) {
        val player = event.player.name
        submit(async = true, delay = 5) {
            runCatching {
                keys.forEach {
                    val key = GermSlots.getKey(player, it)
                    map[key] = GermSlots.getByKey(key) ?: air
                }
                debug("已同步玩家 $player 数据 ${Bukkit.isPrimaryThread()}")
//                getPlayerCache(player)
            }.getOrElse { it.printStackTrace() }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val name = event.player.name
        if (Config.germ__slot_sync_period == 0L) {
            removePlayerCache(name)
            return
        }
        debug("玩家已退出，更新槽数据... 是否主线程: ${Bukkit.isPrimaryThread()}")
        submit(async = true) {
            keys.forEach {
                val key = GermSlots.getKey(name, it)
                GermSlots.setItem(key, map[key])
            }
            removePlayerCache(name)
        }
    }


    private fun updateGracefully() {
        GermSlotIds.upload()
        if (map.keys.isEmpty()) return
        val list = LinkedList(map.keys.shuffled())
        val size = list.size
        var task: BukkitTask? = null
        var time = 0L
        task = submit(async = true, period = 1) {
            if (list.isEmpty()) {
                task?.cancel()
                debug("总共更新了$size 个槽, SQL耗时 $time 毫秒")
            } else {
                val key = list.pop()
                val itemStack = map[key]
                val currentTimeMillis = System.currentTimeMillis()
                GermSlots.setItem(key, itemStack)
                time += (System.currentTimeMillis() - currentTimeMillis)
            }
        }
    }

}