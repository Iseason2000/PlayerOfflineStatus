package top.iseason.bukkit.playerofflinestatus.dto

import org.jetbrains.exposed.sql.Table

object GermSlotIds : Table("germ_slot_ids") {
    val id = varchar("id", 255)
    override val primaryKey = PrimaryKey(id)
}