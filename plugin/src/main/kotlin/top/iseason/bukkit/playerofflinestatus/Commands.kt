package top.iseason.bukkit.playerofflinestatus

import org.bukkit.permissions.PermissionDefault
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.config.Lang
import top.iseason.bukkittemplate.command.command
import top.iseason.bukkittemplate.command.executor
import top.iseason.bukkittemplate.command.node
import top.iseason.bukkittemplate.config.DatabaseConfig
import top.iseason.bukkittemplate.debug.SimpleLogger
import top.iseason.bukkittemplate.utils.bukkit.MessageUtils.sendColorMessage

fun command() {
    command("PlayerOfflineStatus") {
        alias = arrayOf("pos", "post", "pyos", "pofs")
        default = PermissionDefault.OP
        node("reload") {
            default = PermissionDefault.OP
            async = true
            executor { _, sender ->
                Config.load()
                Lang.load()
                DatabaseConfig.isAutoUpdate = false
                DatabaseConfig.load()
                DatabaseConfig.isAutoUpdate = true
                sender.sendColorMessage("&a插件已重载!")
            }
        }
        node("reConnect") {
            default = PermissionDefault.OP
            async = true
            executor { _, sender ->
                DatabaseConfig.reConnected()
                sender.sendColorMessage("&a数据库已重新连接!")
            }
        }
        node("debug") {
            default = PermissionDefault.OP
            async = true
            executor { _, sender ->
                SimpleLogger.isDebug = !SimpleLogger.isDebug
                sender.sendColorMessage("&aDebug模式: &6${SimpleLogger.isDebug}")
            }
        }
    }
}