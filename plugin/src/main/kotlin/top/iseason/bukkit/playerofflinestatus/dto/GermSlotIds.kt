package top.iseason.bukkit.playerofflinestatus.dto

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import top.iseason.bukkit.playerofflinestatus.germ.GermSlotHandler
import top.iseason.bukkittemplate.config.dbTransaction

object GermSlotIds : Table("germ_slot_ids") {
    private val id = varchar("id", 255)
    override val primaryKey = PrimaryKey(id)

    fun upload() {
        if (GermSlotHandler.allIdentitys.isEmpty()) return
        dbTransaction {
            GermSlotIds.batchInsert(GermSlotHandler.allIdentitys, true, false) {
                this[GermSlotIds.id] = it
            }
        }
    }

    fun download() {
        val ids = dbTransaction {
            GermSlotIds.selectAll().map { it[GermSlotIds.id] }
        }
        GermSlotHandler.keys.clear()
        GermSlotHandler.keys.addAll(ids)
    }
}