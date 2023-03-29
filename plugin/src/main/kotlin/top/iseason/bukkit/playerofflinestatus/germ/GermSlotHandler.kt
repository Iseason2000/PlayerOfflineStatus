package top.iseason.bukkit.playerofflinestatus.germ

import com.germ.germplugin.api.GermSlotAPI
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import org.jetbrains.exposed.sql.selectAll
import sun.audio.AudioPlayer.player
import sun.misc.Queue
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.dto.GermSlotIds
import top.iseason.bukkit.playerofflinestatus.dto.GermSlots
import top.iseason.bukkittemplate.config.dbTransaction
import top.iseason.bukkittemplate.debug.debug
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.checkAir
import top.iseason.bukkittemplate.utils.other.CoolDown
import top.iseason.bukkittemplate.utils.other.submit
import java.lang.Thread.sleep
import java.util.Stack
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object GermSlotHandler : GermSlotAPI.SlotDAOHandler, org.bukkit.event.Listener {
    private val map = ConcurrentHashMap<String, ItemStack>()
    private val keys = ConcurrentHashMap.newKeySet<String>()
    private val air = ItemStack(Material.AIR)

    init {
        val ids = dbTransaction {
            GermSlotIds.selectAll().map { it[GermSlotIds.id] }
        }
        keys.addAll(ids)
        if (Config.germSlotSyncPeriod > 0)
            submit(async = true, period = Config.germSlotSyncPeriod) {
                val stack = Stack<String>()
                stack.addAll(map.keys)
                val task: BukkitTask? = null
                submit(async = true, period = 1) {
                    if (stack.isEmpty()) task?.cancel()
                    else {
                        val key = stack.pop()
                        val itemStack = map[key]
                        if (itemStack != null && !itemStack.checkAir())
                            GermSlots.setItem(key, itemStack)
                    }
                }

            }

    }

    override fun getFromIdentitys(name: String?, ids: MutableCollection<String>?): MutableMap<String, ItemStack> {
        if (name == null || ids == null) return mutableMapOf()
        return ids
            .associateWith { getFromIdentity(name, it) }.toMutableMap()
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
    }

    private fun removePlayerCache(player: String) {
        keys.forEach {
            map.remove(GermSlots.getKey(player, it))
        }
    }

    private fun getPlayerCache(player: String) {
        getFromIdentitys(player, keys)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val name = event.player.name
        submit(async = true) {
            keys.forEach {
                val key = GermSlots.getKey(name, it)
                GermSlots.setItem(key, map[key])
            }
            removePlayerCache(name)
        }
    }

    @EventHandler
    fun onPlayerLogin(event: PlayerLoginEvent) {
        submit(async = true) {
            getPlayerCache(event.player.name)
        }
    }


}