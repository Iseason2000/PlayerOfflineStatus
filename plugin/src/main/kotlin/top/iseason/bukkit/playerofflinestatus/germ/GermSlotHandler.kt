package top.iseason.bukkit.playerofflinestatus.germ

import com.germ.germplugin.api.GermSlotAPI
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.dto.GermSlotIds
import top.iseason.bukkit.playerofflinestatus.dto.GermSlots
import top.iseason.bukkittemplate.debug.debug
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.checkAir
import top.iseason.bukkittemplate.utils.other.runAsync
import top.iseason.bukkittemplate.utils.other.submit
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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

    override fun getFromIdentitys(name: String?, ids: MutableCollection<String>?): MutableMap<String, ItemStack> {
        if (name == null || ids == null) return mutableMapOf()
        return ids
            .associateWith {
                val player = Bukkit.getPlayer(name)
                if (player != null)
                    GermSlotAPI.getItemStackFromIdentity(player, it)
                else getFromIdentity(name, it)
            }.toMutableMap()
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

    private fun getPlayerCache(player: String): MutableMap<String, ItemStack> {
        return getFromIdentitys(player, keys)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val name = event.player.name
        submit(async = true, delay = 20) {
            keys.forEach {
                val key = GermSlots.getKey(name, it)
                GermSlots.setItem(key, map[key])
            }
            removePlayerCache(name)
        }
    }

    @EventHandler
    fun onPlayerLogin(event: PlayerLoginEvent) {
        val player = event.player
        submit(async = true, delay = 20) {
            if (player.isOnline)
                getPlayerCache(player.name)
        }
    }

    fun updateGracefully() {
        GermSlotIds.upload()
        if (map.keys.isEmpty()) return
        val list = LinkedList(map.keys.shuffled())
        val size = list.size
        var task: BukkitTask? = null
        task = submit(async = true, period = 1) {
            if (list.isEmpty()) {
                task?.cancel()
                debug("updated $size slots to database")
            } else {
                val key = list.pop()
                val itemStack = map[key]
                if (itemStack != null && !itemStack.checkAir())
                    GermSlots.setItem(key, itemStack)
            }
        }
    }

}