package top.iseason.bukkit.playerofflinestatus

import com.germ.germplugin.api.GermDosAPI
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.config.Lang
import top.iseason.bukkit.playerofflinestatus.dto.PlayerItems
import top.iseason.bukkit.playerofflinestatus.dto.PlayerPAPIs
import top.iseason.bukkit.playerofflinestatus.germ.GermHook
import top.iseason.bukkit.playerofflinestatus.germ.GermListener
import top.iseason.bukkit.playerofflinestatus.papi.Listener
import top.iseason.bukkit.playerofflinestatus.papi.PAPIListener
import top.iseason.bukkittemplate.BukkitPlugin
import top.iseason.bukkittemplate.BukkitTemplate
import top.iseason.bukkittemplate.command.CommandHandler
import top.iseason.bukkittemplate.config.DatabaseConfig
import top.iseason.bukkittemplate.debug.info
import top.iseason.bukkittemplate.hook.PlaceHolderHook
import top.iseason.bukkittemplate.utils.bukkit.EventUtils.register

object PlayerOfflineStatus : BukkitPlugin {

    override fun onEnable() {
        PlaceHolderHook.checkHooked()
        if (!PlaceHolderHook.hasHooked) {
            BukkitTemplate.getPlugin().onDisable()
            return
        }
        GermHook.checkHooked()
        if (GermHook.hasHooked) {
            GermListener.register()
            GermDosAPI.registerDos("pos")
        }
        PAPIListener.register()
        Config.load(false)
        Lang.load(false)
        DatabaseConfig.load(false)
        DatabaseConfig.initTables(PlayerPAPIs, PlayerItems)
        Listener.register()
        command()
        CommandHandler.updateCommands()
        info("&a 插件已加载! &7请在变量前面添加 pos_[玩家名称]_ 使用离线变量")
    }

    override fun onDisable() {
//        info("&7插件已卸载!")
    }

}