package top.iseason.bukkit.playerofflinestatus.dto

import me.clip.placeholderapi.PlaceholderAPI
import me.clip.placeholderapi.PlaceholderAPIPlugin
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.*
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkittemplate.config.dbTransaction
import top.iseason.bukkittemplate.debug.debug

object PlayerPAPIs : Table("player_papi") {
    private val id = integer("id").autoIncrement()
    var name = varchar("name", 255)
    var papi = varchar("papi", 255)
    var value = varchar("value", 255)
    override val primaryKey = PrimaryKey(id)

    init {
        index(true, name, papi)
    }

    /**
     * 更新玩家papi缓存
     */
    fun update(player: Player) {
        val currentTimeMillis = System.currentTimeMillis()
        val name = player.name
        Config.offlinePAPIs.forEach { papi ->
            val result = getPAPIResult(player, papi) ?: return@forEach
            val update = dbTransaction {
                PlayerPAPIs.update(
                    { PlayerPAPIs.name eq name and (PlayerPAPIs.papi eq papi) }
                ) {
                    it[value] = result
                }
            }
            if (update != 1) {
                dbTransaction {
                    PlayerPAPIs.insert {
                        it[PlayerPAPIs.name] = name
                        it[PlayerPAPIs.papi] = papi
                        it[value] = result
                    }
                }
            }
        }
        debug("&a已更新 &6${player.name} &7变量缓存, 耗时 &b${System.currentTimeMillis() - currentTimeMillis} &7毫秒")
    }

    /**
     * 直接解析papi
     */
    fun getPAPIResult(player: Player, str: String): String? {
        val split = str.split('_', limit = 2)
        if (split.size != 2) return null
        if (!Config.oldPlaceHolder) {
            val expansion =
                PlaceholderAPIPlugin.getInstance().localExpansionManager.getExpansion(split[0]) ?: return null
            return expansion.onRequest(player, split[1])
        } else {
            val papi = "%${str}%"
            val result = PlaceholderAPI.setPlaceholders(player, papi)
            return if (papi == result) null
            else result
        }
    }
}