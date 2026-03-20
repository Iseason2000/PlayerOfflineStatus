package top.iseason.bukkit.playerofflinestatus.command

import com.germ.germplugin.api.GermSlotAPI
import com.google.common.cache.CacheStats
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.permissions.PermissionDefault
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.config.Lang
import top.iseason.bukkit.playerofflinestatus.dto.GermSlotBackup
import top.iseason.bukkit.playerofflinestatus.dto.PlayerGermSlots
import top.iseason.bukkit.playerofflinestatus.dto.PlayerPAPIs
import top.iseason.bukkit.playerofflinestatus.germ.GermBackupListener
import top.iseason.bukkit.playerofflinestatus.germ.GermHook
import top.iseason.bukkit.playerofflinestatus.germ.GermListener
import top.iseason.bukkit.playerofflinestatus.germ.GermSlotRedisHandler
import top.iseason.bukkit.playerofflinestatus.papi.PAPI
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
    alias = arrayOf("pos", "poss", "post")
    default = PermissionDefault.OP

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
                sender.sendColorMessage("&a 保存成功!")
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
                if (!GermHook.hasHooked || !Config.germ__enable) throw ParmaException("萌芽不存在或功能未开启!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                val player = params.next<Player>()
                sender.sendColorMessage("&6正在保存玩家槽...")
                PlayerGermSlots.upload(player)
                sender.sendColorMessage("&a 保存成功!")
            }
        }
        node("all") {
            description = "保存所有玩家的槽"
            default = PermissionDefault.OP
            async = true
            executor { _, sender ->
                if (!GermHook.hasHooked || !Config.germ__enable) throw ParmaException("萌芽不存在或功能未开启!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                PlayerGermSlots.uploadAll(sender)
            }
        }
    }
    node("backup") {
        description = "萌芽的槽备份功能"
        default = PermissionDefault.OP

        node("show") {
            description = "查看某玩家的所有备份"
            default = PermissionDefault.OP
            async = true
            param("<player>", suggestRuntime = ParamSuggestCache.playerParam)
            param("[page]")
            executor { params, sender ->
                if (!GermHook.hasHooked || !Config.germ_slot_backup__enable) throw ParmaException("萌芽不存在或功能未开启!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                val player = params.next<String>()
                val page = params.nextOrDefault(0)
                
                // 查询所有备份并分页
                val allBackups = GermSlotBackup.queryBackup(player).sortedByDescending { it.second }
                if (allBackups.isEmpty()) throw ParmaException("该玩家没有数据备份!")
                
                val perPage = Config.backup__show_per_page
                val totalPages = (allBackups.size + perPage - 1) / perPage
                val pagedBackups = allBackups.chunked(perPage).getOrNull(page) 
                    ?: throw ParmaException("该页没有数据!")
                
                // 发送分页提示
                sender.sendColorMessage("&6玩家 &e$player &6的备份 (第 ${page + 1}/$totalPages 页):")
                
                // 构建每条备份消息（Hover 显示时间，点击打开 GUI）
                pagedBackups.forEach { (id, time) ->
                    val message = TextComponent("  • 备份 ID: $id")
                    
                    // Hover 显示时间
                    message.hoverEvent = HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Text(time.toString())
                    )
                    
                    // 点击打开箱子 GUI
                    message.clickEvent = ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/playerofflinestatus backup view $id"
                    )
                    
                    sender.spigot().sendMessage(message)
                }
                
                // 翻页按钮
                if (page > 0) {
                    val prevBtn = TextComponent("&a[« 上一页]")
                    prevBtn.clickEvent = ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/playerofflinestatus backup show $player ${page - 1}"
                    )
                    sender.spigot().sendMessage(prevBtn)
                }
                
                if (page < totalPages - 1) {
                    val nextBtn = TextComponent("&a[下一页 »]")
                    nextBtn.clickEvent = ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/playerofflinestatus backup show $player ${page + 1}"
                    )
                    sender.spigot().sendMessage(nextBtn)
                }
            }
        }
        node("view") {
            description = "查看某个备份的物品（箱子 GUI）"
            default = PermissionDefault.OP
            async = true
            param("<id>")
            isPlayerOnly = true
            executor { params, sender ->
                if (!GermHook.hasHooked || !Config.germ_slot_backup__enable) throw ParmaException("萌芽不存在或功能未开启!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                
                val id = params.next<Long>()
                val items = GermBackupListener.getBackupCaches(id.toString())
                
                if (items.isEmpty()) throw ParmaException("该备份没有物品数据!")
                
                val player = sender as Player
                
                // 创建箱子 GUI（根据物品数量动态调整大小，最大 54 格）
                val size = ((items.size - 1) / 9 + 1) * 9
                val inventorySize = maxOf(9, minOf(54, size))
                
                val inventory = Bukkit.createInventory(player, inventorySize, "备份 #$id")
                
                // 填充物品
                items.values.forEach { item ->
                    inventory.addItem(item)
                }
                
                player.openInventory(inventory)
            }
        }
        node("open") {
            description = "查看某个备份的内容"
            default = PermissionDefault.OP
            async = true
            param("<id>")
            param("[page]")
            isPlayerOnly = true
            executor { params, sender ->
                if (!GermHook.hasHooked || !Config.germ_slot_backup__enable) throw ParmaException("萌芽不存在或功能未开启!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                val id = params.next<Long>()
                val page = params.nextOrDefault<Int>(0)
                val items = GermBackupListener.getBackupCaches(id.toString())
                if (items.isEmpty()) throw ParmaException("该 ID 没有数据!")
                val player = sender as Player
                val chunked = items.entries.chunked(54)
                val pageItems = chunked.getOrNull(page) ?: throw ParmaException("该页没有数据")
                val inventory = Bukkit.createInventory(player, 54, "备份 ID: $id   修改是没有意义的")
                pageItems.forEach { (_, item) ->
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
                if (!GermHook.hasHooked || !Config.germ_slot_backup__enable) throw ParmaException("萌芽不存在或功能未开启!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                val player = params.next<Player>()
                val id = params.next<Long>()
                val items = GermBackupListener.getBackupCaches(id.toString())
                if (items.isEmpty()) throw ParmaException("该 ID 没有数据!")
                items.forEach { (key, item) ->
                    GermSlotAPI.saveItemStackToDatabase(player, key, item)
                }
                sender.sendColorMessage("&a 玩家 &6${player.name} &a 的槽已回滚至 &b${id}")
            }
        }

        node("player") {
            description = "为玩家创建备份"
            default = PermissionDefault.OP
            async = true
            param("<player>", suggestRuntime = ParamSuggestCache.playerParam)
            executor { params, sender ->
                if (!GermHook.hasHooked || !Config.germ_slot_backup__enable) throw ParmaException("萌芽不存在或功能未开启!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                val player = params.next<Player>()
                GermSlotBackup.backup(player.name)
                GermSlotBackup.checkNums(player.name)
                sender.sendColorMessage("&a 玩家 &6${player.name} &a 的槽已备份 ${LocalDateTime.now()}")
            }
        }

        node("all") {
            description = "为所有玩家创建备份"
            default = PermissionDefault.OP
            async = true
            executor { _, sender ->
                if (!GermHook.hasHooked || !Config.germ_slot_backup__enable) throw ParmaException("萌芽不存在或功能未开启!")
                if (!DatabaseConfig.isConnected) throw ParmaException("数据库异常，请检查数据库!")
                GermSlotBackup.backupAll(sender)
            }
        }
    }
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
            sender.sendColorMessage("&a 插件已重载!")
        }
    }
    node("reConnect") {
        description = "重新连接数据库"
        default = PermissionDefault.OP
        async = true
        executor { _, sender ->
            DatabaseConfig.reConnected()
            sender.sendColorMessage("&a 数据库已重新连接!")
        }
    }
    node("debug") {
        description = "切换 debug 模式"
        default = PermissionDefault.OP
        async = true
        node("msg") {
            description = "是否显示 debug 消息"
            default = PermissionDefault.OP
            executor { _, sender ->
                SimpleLogger.isDebug = !SimpleLogger.isDebug
                sender.sendColorMessage("&aDebug 消息：&6${SimpleLogger.isDebug}")
            }
        }
        node("sql") {
            description = "是否显示 debug sql (数据库相关)"
            default = PermissionDefault.OP
            executor { _, sender ->
                MySqlLogger.enable = !MySqlLogger.enable
                sender.sendColorMessage("&aDebug SQL: &6${MySqlLogger.enable}")
            }
        }

        node("cache") {
            description = "输出缓存情况"
            default = PermissionDefault.OP
            fun CacheStats.toStr(): String {
                val stringBuilder = StringBuilder()
                stringBuilder.append("请求数：")
                stringBuilder.append(requestCount())
                stringBuilder.append(" | 命中率：")
                stringBuilder.append("%.2f".format(hitRate()))
                stringBuilder.append(" | 平均 SQL 耗时：")
                stringBuilder.append("%.2f".format(averageLoadPenalty() / 1000000))
                stringBuilder.append(" 毫秒")
                return stringBuilder.toString()
            }
            executor { _, sender ->
                sender.sendColorMessage("&a 变量：&7${PAPI.getCacheStats().toStr()}")
                if (Config.germ__enable)
                    sender.sendColorMessage("&6 萌芽：&7${GermListener.getCacheStats().toStr()}")
                if (Config.germ_slot_backup__enable)
                    sender.sendColorMessage("&b 萌芽备份：&7${GermBackupListener.getCacheStats().toStr()}")
                if (Config.germ__slot_holder_redis__enable)
                    sender.sendColorMessage("&c 萌芽 Redis 代理：&7${GermSlotRedisHandler.getCacheStats().toStr()}")

            }
        }
    }
}


