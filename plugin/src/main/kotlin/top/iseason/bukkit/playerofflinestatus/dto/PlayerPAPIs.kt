package top.iseason.bukkit.playerofflinestatus.dto

import me.clip.placeholderapi.PlaceholderAPI
import me.clip.placeholderapi.PlaceholderAPIPlugin
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.scheduler.BukkitTask
import org.jetbrains.exposed.sql.*
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.germ.GermHook
import top.iseason.bukkit.playerofflinestatus.papi.PAPI
import top.iseason.bukkittemplate.config.DatabaseConfig
import top.iseason.bukkittemplate.config.dbTransaction
import top.iseason.bukkittemplate.debug.debug
import top.iseason.bukkittemplate.debug.info
import top.iseason.bukkittemplate.utils.bukkit.MessageUtils.sendColorMessage
import top.iseason.bukkittemplate.utils.other.submit

object PlayerPAPIs : Table("player_papi"), org.bukkit.event.Listener {
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
    fun upload(player: Player) {
        val currentTimeMillis = System.currentTimeMillis()
        val name = player.name
        Config.placeholder__offline_placeholders.forEach { papi ->
            val result = getPAPIResult(player, papi) ?: return@forEach
            PAPI.putCache(papi, result)
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

    fun uploadAll(sender: CommandSender? = null) {
        var submit: BukkitTask? = null
        val onlinePlayers = Bukkit.getOnlinePlayers()
        if (onlinePlayers.isEmpty()) return
        if (sender == null)
            info("&6开始更新变量缓存...共 ${onlinePlayers.size} 人")
        else sender.sendColorMessage("&6开始更新变量缓存...共 ${onlinePlayers.size} 人")
        val iterator = onlinePlayers.iterator()
        submit = submit(period = Config.placeholder__queue_delay, async = true) mit@{
            if (!iterator.hasNext()) {
                submit?.cancel()
                if (sender == null)
                    info("&a变量缓存更新结束")
                else sender.sendColorMessage("&a变量缓存更新结束")
                return@mit
            }
            val player = iterator.next()
            upload(player)
        }
    }

    /**
     * 直接解析papi
     */
    fun getPAPIResult(player: Player, str: String): String? {
        val split = str.split('_', limit = 2)
        if (split.size != 2) return null
        if (!Config.placeholder__old_placeHolder_version) {
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

    @EventHandler
    fun onLogin(event: PlayerLoginEvent) {
        if (Config.placeholder__offline_placeholders.isEmpty() || !DatabaseConfig.isConnected) return
        val player = event.player
        submit(async = true, delay = 100) {
            if (!player.isOnline) return@submit
            PlayerPAPIs.upload(player)
        }
    }

}