package top.iseason.bukkit.playerofflinestatus.germ

import com.germ.germplugin.api.dynamic.gui.GermGuiCanvas
import com.germ.germplugin.api.dynamic.gui.GermGuiItem
import com.germ.germplugin.api.dynamic.gui.GermGuiPart
import com.germ.germplugin.api.dynamic.gui.GermGuiSlot
import com.germ.germplugin.api.event.GermReceiveDosEvent
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheStats
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.inventory.ItemStack
import sun.audio.AudioPlayer.player
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.dto.GermSlotBackup
import top.iseason.bukkit.playerofflinestatus.dto.PlayerGermSlots
import top.iseason.bukkittemplate.debug.warn
import top.iseason.bukkittemplate.utils.other.CoolDown
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

object GermBackupListener : org.bukkit.event.Listener {
    private val playerCaches = CacheBuilder.newBuilder()
        .expireAfterWrite(max(Config.germ_slot_backup__dos_cache_time, 0), TimeUnit.SECONDS)
        .softValues()
        .recordStats()
        .build<String, Map<String, ItemStack>>()
    private val backListCaches = CacheBuilder.newBuilder()
        .expireAfterWrite(max(Config.germ_slot_backup__dos_cache_time, 0), TimeUnit.SECONDS)
        .softValues()
        .recordStats()
        .build<String, List<Pair<Long, LocalDateTime>>>()
    private val empty = emptyMap<String, ItemStack>()
    private val noCache = ConcurrentHashMap.newKeySet<String>()
    private val coolDown = CoolDown<String>()
    private val air = ItemStack(Material.AIR)

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGermReceiveDosEvent(event: GermReceiveDosEvent) {
        if (event.dosId != Config.germ_slot_backup__dos_id) return
        val germGuiPart = event.germGuiPart ?: return
        val dosContent = event.dosContent.trim()
        val split = dosContent.split('@', limit = 2)
        if (split.size != 2) return
        val first = split[0]
        val type = split[1]
        //自动设置
        val backupId = first.toLongOrNull()
        if (backupId == null && type == "auto_canvas") {
            val canvas = germGuiPart as? GermGuiCanvas ?: return
            val guiParts = canvas.guiParts
            val template = guiParts.firstOrNull() ?: return
            val player = event.player
            canvas.clearGuiPart()
            val queryBackup = getBackupList(first)
            queryBackup.forEach { (id, time) ->
                val clone = template.clone() as GermGuiPart<*>
                clone.setIndexName(id.toString())
                canvas.addGuiPart(clone)
//                clone.simpleMap.putAndPlaceholder(player, "backup_id", id.toString())
                clone.simpleMap.putAndPlaceholder(player, "${clone.realName}_backup_id", id.toString())
                clone.simpleMap.putAndPlaceholder(
                    player,
                    "${clone.realName}_backup_time",
                    Config.germSlotBackupTimeFormat.format(time)
                )
            }
            return
        }
        if (backupId == null) {
            warn("$first 不是一个有效的备份ID(Long)")
            return
        }
        if (type == "all") {
            val canvas = germGuiPart as? GermGuiCanvas ?: return
            val guiParts = canvas.guiParts
            val template = guiParts.firstOrNull() ?: return
            canvas.clearGuiPart()
            val backupCaches = getBackupCaches(backupId.toString())
            backupCaches.forEach { (id, time) ->
                val clone = template.clone() as GermGuiPart<*>
                clone.setIndexName(id)
                when (clone) {
                    is GermGuiSlot -> clone.itemStack = time
                    is GermGuiItem -> clone.itemStack = time
                }
                canvas.addGuiPart(clone)
            }
            return
        }
        val itemStack = getBackCache(backupId.toString(), type)
        when (germGuiPart) {
            is GermGuiSlot -> germGuiPart.itemStack = itemStack
            is GermGuiItem -> germGuiPart.itemStack = itemStack
        }
    }

    fun getCacheStats(): CacheStats = playerCaches.stats().plus(backListCaches.stats())

    private fun getBackCache(id: String, itemId: String) = getBackupCaches(id)[itemId] ?: air

    private fun getBackupList(id: String): List<Pair<Long, LocalDateTime>> =
        backListCaches.get(id) { GermSlotBackup.queryBackup(id) }.sortedByDescending { it.first }

    fun getBackupCaches(id: String): Map<String, ItemStack> {
        // 未命中的缓存
        val noCaChe = noCache.contains(id)
        if (noCaChe && coolDown.check(id, 3000)) return empty
        val callable = Callable {
            val value = GermSlotBackup.getBackupItemsDate(id.toLong())
            if (value != null) {
                return@Callable PlayerGermSlots.fromByteArray(value.bytes)
            } else if (!noCaChe) {
                noCache.add(id)
                warn("Dos ${Config.germ_slot_backup__dos_id}<->$id 没有数据缓存，请检查名称或配置缓存!")
            } else noCache.remove(id)
            return@Callable empty
        }
        //不要缓存
        if (Config.germ_slot_backup__dos_cache_time < 0) {
            return callable.call()
        }
        val get = playerCaches.get(id, callable)
        if (get == empty) {
            playerCaches.invalidate(id)
            return empty
        }
        return get
    }
}