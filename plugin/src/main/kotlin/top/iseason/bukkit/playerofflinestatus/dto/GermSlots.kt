package top.iseason.bukkit.playerofflinestatus.dto

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import top.iseason.bukkittemplate.config.dbTransaction
import top.iseason.bukkittemplate.debug.SimpleLogger
import top.iseason.bukkittemplate.debug.debug
import top.iseason.bukkittemplate.debug.warn
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.checkAir
import top.iseason.bukkittemplate.utils.bukkit.ItemUtils.getDisplayName
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
            return try {
                val itemStack = ItemUtils.fromByteArray(blob.bytes)
                debug("主线程: ${Bukkit.isPrimaryThread()} 成功从数据库获取 $key ${itemStack.type} ${itemStack.getDisplayName()}")
                itemStack
            } catch (e: Exception) {
                warn("反序列化物品 $key 失败，请检查数据完整性")
                null
            }
        }
        debug("萌芽槽 $key 是空的!")
        return null
    }

    fun setItem(key: String, item: ItemStack?) {
        kotlin.runCatching {
            if (item.checkAir()) {
                dbTransaction { GermSlots.deleteWhere { GermSlots.nameId eq key } }
                debug("已删除萌芽槽 $key 的物品")
                return
            }
            val toByteArray = item!!.toByteArray()
            val i = dbTransaction {
                GermSlots.update({ GermSlots.nameId eq key }) {
                    it[GermSlots.item] = ExposedBlob(toByteArray)
                }
            }
            if (i == 0) {
                dbTransaction {
                    GermSlots.insert {
                        it[GermSlots.nameId] = key
                        it[GermSlots.item] = ExposedBlob(toByteArray)
                    }
                }
            }
            debug("已更新槽 $key 数据大小: ${toByteArray.size} bytes")
        }.getOrElse {
            warn("保存槽 $key 错误!")
            it.printStackTrace()
        }
    }

    fun getKey(a: String, b: String) = "$a-$b"
}
