package top.iseason.bukkit.playerofflinestatus.dto

import com.germ.germplugin.api.GermSlotAPI
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import top.iseason.bukkit.playerofflinestatus.config.Config
import top.iseason.bukkit.playerofflinestatus.dto.PlayerGermSlots.toByteArray
import top.iseason.bukkittemplate.config.dbTransaction
import top.iseason.bukkittemplate.debug.debug
import top.iseason.bukkittemplate.debug.info
import top.iseason.bukkittemplate.utils.bukkit.MessageUtils.sendColorMessage
import top.iseason.bukkittemplate.utils.other.submit
import java.time.LocalDateTime

object GermSlotBackup : Table("germ_slot_backup"), org.bukkit.event.Listener {

    private val id = integer("id").autoIncrement()
    private val name = varchar("name", 255).index()
    private var backupItem = blob("backup_items")
    private val time = datetime("time")
    override val primaryKey = PrimaryKey(id)

    fun init() {
        if (Config.germ_slot_backup__period > 0) {
            submit(async = true, delay = Config.germ_slot_backup__period, period = Config.germ_slot_backup__period) {
                backupAll()
            }
        }
    }

    fun backup(player: String) {
        val start = System.currentTimeMillis()
        val slots =
            GermSlotAPI.getGermSlotIdentitysAndItemStacks(player, GermSlotAPI.getAllGermSlotIdentity())
        if (slots.isEmpty()) {
            debug("玩家 $player 没有槽物品，跳过备份")
            return
        }
        val toByteArray = slots.toByteArray()
        dbTransaction {
            GermSlotBackup.insert {
                it[GermSlotBackup.name] = player
                it[GermSlotBackup.backupItem] = ExposedBlob(toByteArray)
                it[GermSlotBackup.time] = LocalDateTime.now()
            }
        }
        debug("玩家 $player 槽备份成功, 耗时 ${System.currentTimeMillis() - start} ms")
    }

    fun backupAll(sender: CommandSender? = null): Int {
        val itr = Bukkit.getOnlinePlayers().iterator()
        if (!itr.hasNext()) return 0
        if (sender == null)
            info("开始备份萌芽槽......")
        else sender.sendColorMessage("&6开始备份萌芽槽......")
        var task: BukkitTask? = null
        var count = 0
        task = submit(async = true, period = Config.germ_slot_backup__queue_delay) sub@{
            if (itr.hasNext()) {
                task?.cancel()
                if (sender == null)
                    info("备份结束，共 $count 份")
                else sender.sendColorMessage("备份结束，共 $count 份")
                return@sub
            }
            val player = itr.next()
            if (!player.isOnline) {
                return@sub
            }
            count++
            backup(player.name)
            checkNums(player.name)
        }
        return count
    }

    fun checkNums(player: String) {
        if (Config.germ_slot_backup__max <= 0) return
        val count = dbTransaction {
            GermSlotBackup.slice(GermSlotBackup.id.count()).select(GermSlotBackup.name eq player)
                .first()[GermSlotBackup.id.count()]
        }
        if (count > Config.germ_slot_backup__max) {
            val num = count - Config.germ_slot_backup__max
            dbTransaction {
                val ids = GermSlotBackup
                    .slice(GermSlotBackup.id)
                    .select(GermSlotBackup.name eq player)
                    .orderBy(GermSlotBackup.id, SortOrder.ASC)
                    .limit(num.toInt()).map { it[GermSlotBackup.id] }
                for (id in ids) {
                    GermSlotBackup.deleteWhere { GermSlotBackup.id eq id }
                }
            }
            debug("已删除 $player 多余的 $num 老备份数据")
        }
    }

    fun queryBackup(player: String): List<Pair<Int, LocalDateTime>> {
        return dbTransaction {
            GermSlotBackup.slice(GermSlotBackup.id, GermSlotBackup.time)
                .select(GermSlotBackup.name eq player)
        }.map { it[GermSlotBackup.id] to it[GermSlotBackup.time] }
    }

    fun getBackupItemsDate(id: Int) = dbTransaction {
        GermSlotBackup
            .slice(GermSlotBackup.backupItem)
            .select { GermSlotBackup.id eq id }
            .firstOrNull()?.get(GermSlotBackup.backupItem)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!Config.germ_slot_backup__backup_on_quit) return
        if (Config.germ__slot_holder) {
            submit(async = true) {
                backup(event.player.name)
            }
        } else backup(event.player.name)
    }
}