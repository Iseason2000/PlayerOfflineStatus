package top.iseason.bukkit.playerofflinestatus

import com.germ.germplugin.api.GermDosAPI
import com.germ.germplugin.api.GermSlotAPI
import org.jetbrains.exposed.sql.Table
import top.iseason.bukkit.playerofflinestatus.command.setupCommands
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.config.Lang
import top.iseason.bukkit.playerofflinestatus.dto.*
import top.iseason.bukkit.playerofflinestatus.germ.*
import top.iseason.bukkit.playerofflinestatus.papi.PAPI
import top.iseason.bukkit.playerofflinestatus.util.Snowflake
import top.iseason.bukkittemplate.BukkitPlugin
import top.iseason.bukkittemplate.BukkitTemplate
import top.iseason.bukkittemplate.command.CommandHandler
import top.iseason.bukkittemplate.config.DatabaseConfig
import top.iseason.bukkittemplate.debug.info
import top.iseason.bukkittemplate.debug.warn
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
        PAPI.register()
        Config.load(false)
        Lang.load(false)
        //加载数据表
        val tables = mutableListOf<Table>(PlayerPAPIs)
        PlayerPAPIs.register()
        if (GermHook.hasHooked) {
            if (Config.germ__enable) {
                GermListener.register()
                GermDosAPI.registerDos(Config.germ__dos_id)
                PlayerGermSlots.register()
                tables.add(PlayerGermSlots)
            }
            if (Config.germ__slot_holder_redis__enable) {
                val old = GermSlotAPI.getSlotDAOHandler()
                try {
                    GermSlotAPI.setSlotDAOHandler(GermSlotRedisHandler)
                    GermSlotRedisHandler.allIdentitys
                } catch (e: Exception) {
                    e.printStackTrace()
                    warn("Redis配置错误")
                    GermSlotAPI.setSlotDAOHandler(old)
                }
            } else if (Config.germ__slot_holder) {
                tables.add(GermSlots)
                tables.add(GermSlotIds)
                GermSlotHandler.register()
                GermSlotAPI.setSlotDAOHandler(GermSlotHandler)
            }
            if (Config.germ_slot_backup__enable) {
                GermSlotBackup.register()
                GermBackupListener.register()
                tables.add(GermSlotBackup)
                GermDosAPI.registerDos(Config.germ_slot_backup__dos_id)
            }
        }
        DatabaseConfig.load(false)
        DatabaseConfig.initTables(*tables.toTypedArray())
        //初始化数据
        if (GermHook.hasHooked) {
            if (Config.germ__slot_holder) GermSlotHandler.init()
        }
        setupCommands()
        CommandHandler.updateCommands()
        Config.isInit = true
        Config.updateTask()
        val trimIndent = """
            　
               _ (`-.                .-')    
              ( (OO  )              ( OO ).  
             _.`     \ .-'),-----. (_)---\_) 
            (__...--''( OO'  .-.  '/    _ |  
             |  /  | |/   |  | |  |\  :` `.  
             |  |_.' |\_) |  |\|  | '..`''.) 
             |  .___.'  \ |  | |  |.-._)   \ 
             |  |        `'  '-'  '\       / 
             `--'          `-----'  `-----'  
            　
             作者: Iseason 
             QQ: 1347811744
             　
        """.trimIndent()
        info(trimIndent)
        info("&a插件已加载! &7请在变量前面添加 pos_[玩家名称]_ 使用离线变量")
    }

    override fun onDisable() {
        if (Config.germ__slot_holder) {
            GermSlotIds.upload()
        }
        info("&7插件已卸载!")
    }

}