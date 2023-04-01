package top.iseason.bukkit.playerofflinestatus.germ

import com.germ.germplugin.api.GermSlotAPI
import com.germ.germplugin.api.dynamic.gui.GermGuiEntity
import com.germ.germplugin.api.dynamic.gui.GermGuiItem
import com.germ.germplugin.api.dynamic.gui.GermGuiSlot
import com.germ.germplugin.api.event.GermReceiveDosEvent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.dto.PlayerGermSlots
import top.iseason.bukkit.playerofflinestatus.dto.PlayerPAPIs.papi
import top.iseason.bukkittemplate.config.dbTransaction
import top.iseason.bukkittemplate.debug.warn
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.checkAir
import top.iseason.bukkittemplate.utils.other.CoolDown
import top.iseason.bukkittemplate.utils.other.submit
import java.util.concurrent.ConcurrentHashMap

object GermListener : org.bukkit.event.Listener {
    private val playerCaches = hashMapOf<String, Map<String, ItemStack>>()
    private val noCache = ConcurrentHashMap.newKeySet<String>()
    private val coolDown = CoolDown<String>()

    @EventHandler
    fun onGermReceiveDosEvent(event: GermReceiveDosEvent) {
        if (event.dosId != "pos") return
        val dosContent = event.dosContent.trim()
        val germGuiPart = event.germGuiPart ?: return
        val split = dosContent.split('@', limit = 2)
        if (split.size != 2) return
        val player = split[0]
        val itemId = split[1]
        if (itemId.startsWith("body")) {
            val germGuiEntity = germGuiPart as? GermGuiEntity ?: return
            germGuiEntity.helmet = getGermSlot(player, "germplugin_armor_helmet")
            germGuiEntity.chestplate = getGermSlot(player, "germplugin_armor_chestplate")
            germGuiEntity.leggings = getGermSlot(player, "germplugin_armor_leggings")
            germGuiEntity.boots = getGermSlot(player, "germplugin_armor_boots")
            if (itemId == "body_with_hands") {
                germGuiEntity.mainHand = getGermSlot(player, "germplugin_main_hand")
                germGuiEntity.offHand = getGermSlot(player, "germplugin_off_hand")
            }
            return
        }
        val item = getGermSlot(player, itemId)
        when (germGuiPart) {
            is GermGuiSlot -> germGuiPart.itemStack = item
            is GermGuiItem -> germGuiPart.itemStack = item
            is GermGuiEntity -> {
                when (itemId) {
                    "germplugin_armor_helmet" -> germGuiPart.helmet = item
                    "germplugin_armor_chestplate" -> germGuiPart.chestplate = item
                    "germplugin_armor_leggings" -> germGuiPart.leggings = item
                    "germplugin_armor_boots" -> germGuiPart.boots = item
                    "germplugin_main_hand" -> germGuiPart.mainHand = item
                    "germplugin_off_hand" -> germGuiPart.offHand = item
                }
            }
        }
    }

    fun removeCache(name: String) {
        playerCaches.remove(name)
    }

    private fun getGermSlot(name: String, id: String): ItemStack {
        val player = Bukkit.getPlayer(name)
        if (player != null && Config.germ__proxy_online) {
            return GermSlotAPI.getItemStackFromIdentity(player, id)
        }
        return getItemCache(name, id) ?: ItemStack(Material.AIR)
    }

    private fun getItemCache(name: String, itemName: String): ItemStack? {
        var item = playerCaches[name]?.get(itemName)
        val key = "$name@$itemName"
        //命中缓存不过期
        val germCacheTime = Config.germ__cache_time
        if (germCacheTime != 0L && item != null &&
            (germCacheTime < 0 || coolDown.check(key, germCacheTime))
        ) {
            return item
        }
        // 未命中的缓存
        val noCaChe = noCache.contains(key)
        if (noCaChe && coolDown.check("nocache-${key}", 3000)) return null
        val value = dbTransaction {
            PlayerGermSlots
                .slice(PlayerGermSlots.items)
                .select(PlayerGermSlots.name eq name)
                .limit(1)
                .firstOrNull()
                ?.get(PlayerGermSlots.items)
        }
        //未命中的警告
        if (value == null) {
            noCache.add(key)
            warn("Dos pos<->$key 没有数据缓存，请检查名称或配置缓存!")
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

    @EventHandler
    fun onLogin(event: PlayerLoginEvent) {
        val player = event.player
        submit(async = true, delay = 100) {
            if (player.isOnline)
                PlayerGermSlots.upload(player)
        }
    }
}