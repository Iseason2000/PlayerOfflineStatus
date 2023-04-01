package top.iseason.bukkit.playerofflinestatus.germ

import com.germ.germplugin.api.GermSlotAPI
import com.germ.germplugin.api.dynamic.gui.GermGuiEntity
import com.germ.germplugin.api.dynamic.gui.GermGuiItem
import com.germ.germplugin.api.dynamic.gui.GermGuiSlot
import com.germ.germplugin.api.event.GermReceiveDosEvent
import com.google.common.cache.CacheBuilder
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.dto.PlayerGermSlots
import top.iseason.bukkittemplate.config.dbTransaction
import top.iseason.bukkittemplate.debug.debug
import top.iseason.bukkittemplate.debug.warn
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.checkAir
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.getDisplayName
import top.iseason.bukkittemplate.utils.other.CoolDown
import top.iseason.bukkittemplate.utils.other.submit
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

object GermListener : org.bukkit.event.Listener {
    private val playerCaches = CacheBuilder.newBuilder()
        .expireAfterWrite(max(Config.germ__cache_time, 0), TimeUnit.SECONDS)
        .softValues()
        .recordStats()
        .build<String, Map<String, ItemStack>>()

    private val noCache = ConcurrentHashMap.newKeySet<String>()
    private val coolDown = CoolDown<String>()

    private val empty = emptyMap<String, ItemStack>()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGermReceiveDosEvent(event: GermReceiveDosEvent) {
        if (event.dosId != "pos") return
        val dosContent = event.dosContent.trim()
        val germGuiPart = event.germGuiPart ?: return
        val split = dosContent.split('@', limit = 2)
        if (split.size != 2) return
        val playerName = split[0]
        val itemId = split[1]
        val player = Bukkit.getPlayerExact(playerName)
        if (itemId == "body" || itemId == "body_with_hands") {
            val germGuiEntity = germGuiPart as? GermGuiEntity ?: return
            val helmet = getGermSlot(playerName, "germplugin_armor_helmet", player)
            if (!helmet.checkAir())
                germGuiEntity.helmet = helmet
            val chestplate = getGermSlot(playerName, "germplugin_armor_chestplate", player)
            if (!chestplate.checkAir())
                germGuiEntity.chestplate = chestplate
            val leggings = getGermSlot(playerName, "germplugin_armor_leggings", player)
            if (!leggings.checkAir())
                germGuiEntity.leggings = leggings
            val boots = getGermSlot(playerName, "germplugin_armor_boots", player)
            if (!boots.checkAir())
                germGuiEntity.boots = boots
            if (itemId == "body_with_hands") {
                val mainHand = getGermSlot(playerName, "germplugin_main_hand", player)
                if (!mainHand.checkAir())
                    germGuiEntity.mainHand = mainHand
                val offHand = getGermSlot(playerName, "germplugin_off_hand", player)
                if (!offHand.checkAir())
                    germGuiEntity.offHand = offHand
            }
            return
        }
        val item = getGermSlot(playerName, itemId, player)
        debug("已设置 界面 ${event.germGuiScreen.hashCode()} 的 组件 ${germGuiPart.indexName} | $dosContent | ${item.type} | ${item.getDisplayName()}")
        when (germGuiPart) {
            is GermGuiSlot -> germGuiPart.itemStack = item
            is GermGuiItem -> germGuiPart.itemStack = item
            is GermGuiEntity -> {
                if (item.checkAir()) return
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

    fun getCacheStats() = playerCaches.stats()

    fun putCache(name: String, map: Map<String, ItemStack>) {
        playerCaches.put(name, map)
    }

    private fun getGermSlot(name: String, id: String, player: Player? = null): ItemStack {
        if (player != null && Config.germ__proxy_online) {
            return GermSlotAPI.getItemStackFromIdentity(player, id)
        }
        return getItemCache(name, id) ?: ItemStack(Material.AIR)
    }

    private fun getItemCache(name: String, itemName: String): ItemStack? {
        val key = "$name@$itemName"
        // 未命中的缓存
        val noCaChe = noCache.contains(key)
        if (noCaChe && coolDown.check(key, 2000)) return null
        val callable = Callable {
            val value = dbTransaction {
                PlayerGermSlots
                    .slice(PlayerGermSlots.items)
                    .select(PlayerGermSlots.name eq name)
                    .limit(1)
                    .firstOrNull()
                    ?.get(PlayerGermSlots.items)
            }
            if (value != null) {
                return@Callable PlayerGermSlots.fromByteArray(value.bytes)
            } else if (!noCaChe) {
                noCache.add(key)
                warn("Dos pos<->$key 没有数据缓存，请检查名称或配置缓存!")
            } else noCache.remove(key)
            return@Callable empty
        }
        //不要缓存
        if (Config.germ__cache_time < 0) {
            return callable.call()[itemName]
        }
        val get = playerCaches.get(key, callable)
        if (get == empty) {
            playerCaches.invalidate(key)
            return null
        }
        return playerCaches.get(key, callable)[itemName]
    }

    @EventHandler
    fun onLogin(event: PlayerLoginEvent) {
        val player = event.player
        submit(async = true, delay = 100) {
            if (player.isOnline)
                PlayerGermSlots.upload(player)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        submit(async = true) {
            PlayerGermSlots.upload(player)
        }
    }
}