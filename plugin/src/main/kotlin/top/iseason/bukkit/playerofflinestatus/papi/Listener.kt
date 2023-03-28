package top.iseason.bukkit.playerofflinestatus.papi

import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerLoginEvent
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.dto.PlayerItems
import top.iseason.bukkit.playerofflinestatus.dto.PlayerPAPIs
import top.iseason.bukkittemplate.config.DatabaseConfig
import top.iseason.bukkittemplate.utils.other.submit

object Listener : org.bukkit.event.Listener {

    @EventHandler
    fun onLogin(event: PlayerLoginEvent) {
        if (Config.offlinePAPIs.isEmpty() || !DatabaseConfig.isConnected) return
        val player = event.player
        submit(async = Config.updateAsync, delay = 100) {
            if (!player.isOnline) return@submit
            PlayerPAPIs.update(player)
            PlayerItems.update(player)
        }
    }

}