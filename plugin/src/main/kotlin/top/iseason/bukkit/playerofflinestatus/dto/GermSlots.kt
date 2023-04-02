package top.iseason.bukkit.playerofflinestatus.dto

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import top.iseason.bukkittemplate.config.dbTransaction
import top.iseason.bukkittemplate.debug.debug
import top.iseason.bukkittemplate.debug.info
import top.iseason.bukkittemplate.debug.warn
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.checkAir
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.toByteArray

object GermSlots : Table("germ_slot") {
    //玩家名-萌芽id
    private val nameId = varchar("name_id", 255)
    private var item = blob("slot_item").nullable()

    override val primaryKey = PrimaryKey(nameId)

    fun getByKey(key: String): ItemStack? {
        val blob = dbTransaction {
            GermSlots.slice(GermSlots.item)
                .select(GermSlots.nameId eq key)
                .firstOrNull()?.get(GermSlots.item)
        }
        if (blob != null) {
            try {
                return ItemUtils.fromByteArray(blob.bytes)
            } catch (e: Exception) {
                warn("germ slot $key unable to deserialize to item")
            }
        }
        debug("${Bukkit.isPrimaryThread()} Germ slot -> get $key from database")
        return null
    }

    fun setItem(key: String, item: ItemStack?) {
        if (item.checkAir()) {
            dbTransaction { GermSlots.deleteWhere { GermSlots.nameId eq key } }
            return
        }
        val toByteArray = item!!.toByteArray()
        val exposedBlob = ExposedBlob(toByteArray)
        val i = dbTransaction {
            GermSlots.update({ GermSlots.nameId eq key }) {
                it[GermSlots.item] = exposedBlob
            }
        }
        if (i == 0) {
            dbTransaction {
                GermSlots.insert {
                    it[GermSlots.nameId] = key
                    it[GermSlots.item] = exposedBlob
                }
            }
        }
        debug("已更新槽 $key")
    }

    fun getKey(a: String, b: String) = "$a-$b"
}
