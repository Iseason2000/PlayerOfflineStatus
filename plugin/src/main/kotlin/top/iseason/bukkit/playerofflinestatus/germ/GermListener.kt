package top.iseason.bukkit.playerofflinestatus.germ

import com.germ.germplugin.api.dynamic.gui.GermGuiEntity
import com.germ.germplugin.api.dynamic.gui.GermGuiItem
import com.germ.germplugin.api.dynamic.gui.GermGuiSlot
import com.germ.germplugin.api.event.GermReceiveDosEvent
import org.bukkit.event.EventHandler
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.select
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.dto.PlayerGermSlots
import top.iseason.bukkit.playerofflinestatus.dto.PlayerPAPIs.papi
import top.iseason.bukkittemplate.config.dbTransaction
import top.iseason.bukkittemplate.debug.warn
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.checkAir
import top.iseason.bukkittemplate.utils.other.CoolDown
import java.util.concurrent.ConcurrentHashMap

object GermListener : org.bukkit.event.Listener {
    private val playerCaches = hashMapOf<String, Map<String, ItemStack>>()
    private val noCache = ConcurrentHashMap.newKeySet<String>()
    private val coolDown = CoolDown<String>()

    @EventHandler
    fun onGermReceiveDosEvent(event: GermReceiveDosEvent) {
        if (event.dosId != "pos") return
        val dosContent = event.dosContent
        val germGuiPart = event.germGuiPart ?: return
        val split = dosContent.split('@', limit = 2)
        if (split.size != 2) return
        val player = split[0]
        val itemId = split[1]
        if (itemId == "body") {
            val germGuiEntity = germGuiPart as? GermGuiEntity ?: return
            val helmet = getItemCache(dosContent, player, "head")
            if (!helmet.checkAir()) germGuiEntity.helmet = helmet
            val chestplate = getItemCache(dosContent, player, "chest")
            if (!chestplate.checkAir()) germGuiEntity.chestplate = chestplate
            val leggings = getItemCache(dosContent, player, "legs")
            if (!leggings.checkAir()) germGuiEntity.leggings = leggings
            val boots = getItemCache(dosContent, player, "feet")
            if (!boots.checkAir()) germGuiEntity.boots = boots
            return
        }
        val item = getItemCache(dosContent, player, itemId) ?: return
        if (item.checkAir()) return
        when (itemId) {
            "head" -> (germGuiPart as? GermGuiEntity)?.helmet = item
            "chest" -> (germGuiPart as? GermGuiEntity)?.chestplate = item
            "legs" -> (germGuiPart as? GermGuiEntity)?.leggings = item
            "feet" -> (germGuiPart as? GermGuiEntity)?.boots = item
            else -> {
                when (germGuiPart) {
                    is GermGuiSlot -> germGuiPart.itemStack = item
                    is GermGuiItem -> germGuiPart.itemStack = item
                }
            }
        }
    }

    fun removeCache(name: String) {
        playerCaches.remove(name)
    }

    private fun getItemCache(key: String, name: String, itemName: String): ItemStack? {
        var item = playerCaches[name]?.get(itemName)
        //命中缓存不过期
        val germCacheTime = Config.germ__cache_time
        if (germCacheTime != 0L && item != null &&
            (germCacheTime < 0 || coolDown.check(key, germCacheTime))
        ) {
            return item
        }
        // 未命中的缓存
        val noCaChe = noCache.contains(key)
        if (noCaChe && coolDown.check("nocache-${key}", 1000)) return null
        val value = dbTransaction {
            PlayerGermSlots.slice(PlayerGermSlots.items).select {
                PlayerGermSlots.name eq name
            }.limit(1).firstOrNull()?.get(PlayerGermSlots.items)
        }
        //未命中的警告
        if (value == null) {
            noCache.add(key)
            warn("Dos $papi 没有数据缓存，请检查名称或配置缓存!")
        } else // 未命中转已命中
            if (noCaChe) noCache.remove(key)
        //更新缓存
        if (value != null) {
            val fromByteArray = PlayerGermSlots.fromByteArray(value.bytes)
            playerCaches[name] = fromByteArray
            item = fromByteArray[itemName]
        }
        if (item == null) noCache.add(key)
        return item
    }
}