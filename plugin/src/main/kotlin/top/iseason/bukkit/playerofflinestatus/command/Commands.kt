package top.iseason.bukkit.playerofflinestatus.command

import com.germ.germplugin.api.GermSlotAPI
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.permissions.PermissionDefault
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.config.Lang
import top.iseason.bukkit.playerofflinestatus.dto.GermSlotBackup
import top.iseason.bukkit.playerofflinestatus.dto.PlayerGermSlots
import top.iseason.bukkit.playerofflinestatus.dto.PlayerPAPIs
import top.iseason.bukkittemplate.command.*
import top.iseason.bukkittemplate.config.DatabaseConfig
import top.iseason.bukkittemplate.config.MySqlLogger
import top.iseason.bukkittemplate.debug.SimpleLogger
import top.iseason.bukkittemplate.utils.bukkit.MessageUtils.sendColorMessage
import java.time.LocalDateTime

/**
 * 插件的命令都在这
 */
fun setupCommands() = command("PlayerOfflineStatus") {
    alias = arrayOf("pos", "post", "pyos", "pofs")
    default = PermissionDefault.OP
    node("reload") {
        default = PermissionDefault.OP
        async = true
        description = "重载配置"
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
        description = "重新连接数据库"
        default = PermissionDefault.OP
        async = true
        executor { _, sender ->
            DatabaseConfig.reConnected()
            sender.sendColorMessage("&a数据库已重新连接!")
        }
    }
    node("debug") {
        description = "切换debug模式"
        default = PermissionDefault.OP
        async = true
        node("msg") {
            description = "是否显示debug 消息"
            default = PermissionDefault.OP
            executor { _, sender ->
                SimpleLogger.isDebug = !SimpleLogger.isDebug
                sender.sendColorMessage("&aDebug 消息: &6${SimpleLogger.isDebug}")
            }
        }
        node("sql") {
            description = "是否显示 debug sql (数据库相关)"
            default = PermissionDefault.OP
            executor { _, sender ->
                MySqlLogger.enable = !MySqlLogger.enable
                sender.sendColorMessage("&aDebug SQL: &6${SimpleLogger.isDebug}")
            }
        }
    }

    node("placeholder") {
        description = "离线变量相关"
        default = PermissionDefault.OP
        node("save") {
            description = "保存某个玩家的变量"
            default = PermissionDefault.OP
            async = true
            param("<player>", suggestRuntime = ParamSuggestCache.playerParam)
            executor { params, sender ->
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                val player = params.next<Player>()
                sender.sendColorMessage("&6正在保存玩家变量...")
                PlayerPAPIs.upload(player)
                sender.sendColorMessage("&a保存成功!")
            }
        }
        node("all") {
            description = "保存所有玩家的变量"
            default = PermissionDefault.OP
            async = true
            executor { _, sender ->
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                PlayerPAPIs.uploadAll(sender)
            }
        }
    }
    node("germ") {
        description = "离线槽相关"
        default = PermissionDefault.OP
        node("save") {
            description = "保存某个玩家的槽"
            default = PermissionDefault.OP
            async = true
            param("<player>", suggestRuntime = ParamSuggestCache.playerParam)
            executor { params, sender ->
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                val player = params.next<Player>()
                sender.sendColorMessage("&6正在保存玩家槽...")
                PlayerGermSlots.upload(player)
                sender.sendColorMessage("&a保存成功!")
            }
        }
        node("all") {
            description = "保存所有玩家的槽"
            default = PermissionDefault.OP
            async = true
            executor { _, sender ->
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                PlayerGermSlots.uploadAll(sender)
            }
        }
    }
    node("backup") {
        description = "萌芽的槽备份功能"
        node("show") {
            description = "查看某玩家的所有备份"
            default = PermissionDefault.OP
            async = true
            param("<player>", suggestRuntime = ParamSuggestCache.playerParam)
            executor { params, sender ->
                if (!Config.germ_slot_backup__enable) throw ParmaException("未开启萌芽槽备份功能!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                val player = params.next<String>()
                val queryBackup = GermSlotBackup.queryBackup(player)
                if (queryBackup.isEmpty()) throw ParmaException("该玩家没有数据备份!")
                val isPlayer = sender is Player
                queryBackup.forEach { (id, time) ->
                    val message = TextComponent("ID: $id 时间: $time")
                    if (isPlayer) {
                        val clickOpen = TextComponent("[点击打开]")
                        clickOpen.clickEvent =
                            ClickEvent(ClickEvent.Action.RUN_COMMAND, "playerofflinestatus backup open $id")
                        message.addExtra(clickOpen)
                        message.addExtra("    ")
                        val clickRollback = TextComponent("[点击回滚]")
                        clickOpen.clickEvent =
                            ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                "playerofflinestatus backup rollback $player $id"
                            )
                        message.addExtra(clickRollback)
                    }
                    sender.spigot().sendMessage(message)
                }
                sender.sendColorMessage("&6该玩家的萌芽槽备份如上")
            }
        }
        node("open") {
            description = "查看某个备份的内容"
            default = PermissionDefault.OP
            async = true
            param("<id>")
            isPlayerOnly = true
            executor { params, sender ->
                if (!Config.germ_slot_backup__enable) throw ParmaException("未开启萌芽槽备份功能!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                val id = params.next<Int>()
                val backupItemsDate = GermSlotBackup.getBackupItemsDate(id) ?: throw ParmaException("该ID没有数据!")
                val items = PlayerGermSlots.fromByteArray(backupItemsDate.bytes)
                val player = sender as Player
                val inventory = Bukkit.createInventory(player, 54, "备份ID: $id   修改是没有意义的")
                items.forEach { (key, item) ->
                    val l = "萌芽槽ID: $key"
                    item.itemMeta = item.itemMeta!!.apply {
                        if (hasLore()) {
                            lore = lore!!.apply { add(0, l) }
                        } else lore = listOf(l)
                    }
                    inventory.addItem(item)
                }
                player.openInventory(inventory)
            }
        }

        node("rollback") {
            description = "回滚某玩家的备份"
            default = PermissionDefault.OP
            async = true
            param("<player>", suggestRuntime = ParamSuggestCache.playerParam)
            param("<id>")
            executor { params, sender ->
                if (!Config.germ_slot_backup__enable) throw ParmaException("未开启萌芽槽备份功能!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                val player = params.next<Player>()
                val id = params.next<Int>()
                val backupItemsDate = GermSlotBackup.getBackupItemsDate(id) ?: throw ParmaException("该ID没有数据!")
                val items = PlayerGermSlots.fromByteArray(backupItemsDate.bytes)
                items.forEach { (key, item) ->
                    GermSlotAPI.saveItemStackToDatabase(player, key, item)
                }
                sender.sendColorMessage("&a玩家 &6${player.name} &a的槽已回滚至 &b${id}")
            }
        }

        node("player") {
            description = "为玩家创建备份"
            default = PermissionDefault.OP
            async = true
            param("<player>", suggestRuntime = ParamSuggestCache.playerParam)
            executor { params, sender ->
                if (!Config.germ_slot_backup__enable) throw ParmaException("未开启萌芽槽备份功能!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                val player = params.next<Player>()
                GermSlotBackup.backup(player.name)
                GermSlotBackup.checkNums(player.name)
                sender.sendColorMessage("&a玩家 &6${player.name} &a的槽已备份 ${LocalDateTime.now()}")
            }
        }

        node("all") {
            description = "为所有玩家创建备份"
            default = PermissionDefault.OP
            async = true
            executor { params, sender ->
                if (!Config.germ_slot_backup__enable) throw ParmaException("未开启萌芽槽备份功能!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                GermSlotBackup.backupAll(sender)
            }
        }
    }
}
