package top.iseason.bukkit.playerofflinestatus.dto

import com.germ.germplugin.api.GermSlotAPI
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.update
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkittemplate.config.dbTransaction
import top.iseason.bukkittemplate.debug.debug
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.toByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.HashSet
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object PlayerItems : Table("player_items") {
    private val placeholder = ItemStack(Material.AIR)
    private val id = integer("id").autoIncrement()
    var name = varchar("name", 255)
    var items = blob("items")
    override val primaryKey = PrimaryKey(id)

    init {
        index(true, name)
    }

    fun update(player: Player) {
        val currentTimeMillis = System.currentTimeMillis()
        val name = player.name
        val mutableMapOf = mutableMapOf<String, ItemStack>()
        // 解析原版的
        val keys = Config.equipments
        if (keys.contains("head")) {
            mutableMapOf["head"] = player.equipment?.helmet ?: placeholder
        }
        if (keys.contains("chest")) {
            mutableMapOf["chest"] = player.equipment?.chestplate ?: placeholder
        }
        if (keys.contains("legs")) {
            mutableMapOf["legs"] = player.equipment?.leggings ?: placeholder
        }
        if (keys.contains("feet")) {
            mutableMapOf["feet"] = player.equipment?.boots ?: placeholder
        }
        val hashSet = HashSet<String>()
        hashSet.addAll(keys)
        hashSet.remove("head")
        hashSet.remove("chest")
        hashSet.remove("legs")
        hashSet.remove("feet")
        println(GermSlotAPI.getSlotDAOHandler().javaClass.name)
        hashSet.forEach { println(it) }
        val germs = GermSlotAPI.getGermSlotIdentitysAndItemStacks(player, hashSet)
        germs.forEach { (k, v) ->
            println("$k -> ${v.type}")
        }
        for (s in hashSet) {
            mutableMapOf[s] = germs[s] ?: placeholder
        }
        val blob = ExposedBlob(mutableMapOf.toByteArray())
        dbTransaction {
            val update = PlayerItems.update(
                { PlayerItems.name eq name }
            ) {
                it[items] = blob
            }
            if (update != 1) {
                PlayerItems.insert {
                    it[PlayerItems.name] = name
                    it[items] = blob
                }
            }
        }
        debug("&a已更新 &6${player.name} &7物品缓存, 耗时 &b${System.currentTimeMillis() - currentTimeMillis} &7毫秒")
    }

    private fun Map<String, ItemStack>.toByteArray(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        BukkitObjectOutputStream(outputStream).use {
            it.writeInt(this.size)
            this.forEach { (k, v) ->
                v.toByteArray()
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
}