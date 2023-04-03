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
import top.iseason.bukkit.playerofflinestatus.germ.GermListener
import top.iseason.bukkit.playerofflinestatus.util.Snowflake
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
    private val snowflake = Snowflake(Config.serverId)
    private val id = long("id")
    var name = varchar("name", 255)
    var items = blob("items")
    override val primaryKey = PrimaryKey(id)

    init {
        index(true, name)
    }

    /**
     * 更新玩家的槽到数据库
     */
    fun upload(player: Player) {
        val name = player.name
        val currentTimeMillis = System.currentTimeMillis()
        val germs = Config.germ__offline_slots.associateWith { GermSlotAPI.getItemStackFromIdentity(player, it) }
        //更新缓存
        GermListener.putCache(name, germs)
        val blob = ExposedBlob(germs.toByteArray())
        dbTransaction {
            val update = PlayerGermSlots.update({ PlayerGermSlots.name eq name }) { it[items] = blob }
            if (update != 1) {
                PlayerGermSlots.insert {
                    it[PlayerGermSlots.id] = snowflake.nextId()
                    it[PlayerGermSlots.name] = name
                    it[PlayerGermSlots.items] = blob
                }
            }
        }
        debug("&a已更新 &6${name} &7物品缓存, 耗时 &b${System.currentTimeMillis() - currentTimeMillis} &7毫秒")
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