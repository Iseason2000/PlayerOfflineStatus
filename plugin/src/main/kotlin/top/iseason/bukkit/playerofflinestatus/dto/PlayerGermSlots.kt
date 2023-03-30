package top.iseason.bukkit.playerofflinestatus.dto

import com.germ.germplugin.api.GermSlotAPI
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.update
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkittemplate.config.DatabaseConfig
import top.iseason.bukkittemplate.config.dbTransaction
import top.iseason.bukkittemplate.debug.debug
import top.iseason.bukkittemplate.debug.info
import top.iseason.bukkittemplate.utils.bukkit.MessageUtils.sendColorMessage
import top.iseason.bukkittemplate.utils.other.submit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.HashSet
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object PlayerGermSlots : Table("player_germ_slot"), org.bukkit.event.Listener {
    private val placeholder = ItemStack(Material.AIR)
    private val id = integer("id").autoIncrement()
    var name = varchar("name", 255)
    var items = blob("items")
    override val primaryKey = PrimaryKey(id)

    init {
        index(true, name)
    }

    /**
     * 删除玩家的槽到数据库
     */
    fun upload(player: Player) {
        val currentTimeMillis = System.currentTimeMillis()
        val name = player.name
        val mutableMapOf = mutableMapOf<String, ItemStack>()
        // 解析原版的
        val keys = Config.germ__offline_slots
        val hashSet = HashSet(keys)
        if (hashSet.contains("head")) {
            mutableMapOf["head"] = player.equipment?.helmet ?: placeholder
            hashSet.remove("head")
        }
        if (hashSet.contains("chest")) {
            mutableMapOf["chest"] = player.equipment?.chestplate ?: placeholder
            hashSet.remove("chest")
        }
        if (hashSet.contains("legs")) {
            mutableMapOf["legs"] = player.equipment?.leggings ?: placeholder
            hashSet.remove("legs")
        }
        if (hashSet.contains("feet")) {
            mutableMapOf["feet"] = player.equipment?.boots ?: placeholder
            hashSet.remove("feet")
        }
        val germs = GermSlotAPI.getGermSlotIdentitysAndItemStacks(player, hashSet)
        for (s in hashSet) {
            mutableMapOf[s] = germs[s] ?: placeholder
        }
        val blob = ExposedBlob(mutableMapOf.toByteArray())
        dbTransaction {
            val update = PlayerGermSlots.update({ PlayerGermSlots.name eq name }) { it[items] = blob }
            if (update != 1) {
                PlayerGermSlots.insert {
                    it[PlayerGermSlots.name] = name
                    it[items] = blob
                }
            }
        }
        debug("&a已更新 &6${player.name} &7物品缓存, 耗时 &b${System.currentTimeMillis() - currentTimeMillis} &7毫秒")
    }

    fun uploadAll(sender: CommandSender? = null) {
        var submit: BukkitTask? = null
        val onlinePlayers = Bukkit.getOnlinePlayers()
        if (onlinePlayers.isEmpty()) return
        if (sender == null)
            info("&6开始更新萌芽槽缓存...共 ${onlinePlayers.size} 人")
        else sender.sendColorMessage("&6开始更新萌芽槽缓存...共 ${onlinePlayers.size} 人")
        val iterator = onlinePlayers.iterator()
        submit = submit(period = Config.germ__queue_delay, async = true) mit@{
            if (!iterator.hasNext()) {
                submit?.cancel()
                if (sender == null)
                    info("&a缓存更新结束")
                else sender.sendColorMessage("&a缓存更新结束")
                return@mit
            }
            val player = iterator.next()
            if (player.isOnline)
                upload(player)
        }
    }


    fun Map<String, ItemStack>.toByteArray(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        BukkitObjectOutputStream(outputStream).use {
            it.writeInt(this.size)
            this.forEach { (k, v) ->
                it.writeUTF(k)
                it.writeObject(v)
            }
        }
        val gzipStream = ByteArrayOutputStream()
        GZIPOutputStream(gzipStream).use { it.write(outputStream.toByteArray()) }
        return gzipStream.toByteArray()
    }

    fun fromByteArray(byteArray: ByteArray): Map<String, ItemStack> {
        val mutableListOf = mutableMapOf<String, ItemStack>()
        GZIPInputStream(ByteArrayInputStream(byteArray)).use { it1 ->
            BukkitObjectInputStream(it1).use { os ->
                val size = os.readInt()
                repeat(size) {
                    mutableListOf[os.readUTF()] = os.readObject() as ItemStack
                }
            }
        }
        return mutableListOf
    }

    @EventHandler
    fun onLogin(event: PlayerLoginEvent) {
        if (Config.placeholder__offline_placeholders.isEmpty() || !DatabaseConfig.isConnected) return
        val player = event.player
        submit(async = true, delay = 100) {
            if (!player.isOnline) return@submit
            upload(player)
        }
    }
}