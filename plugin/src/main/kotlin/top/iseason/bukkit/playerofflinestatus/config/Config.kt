package top.iseason.bukkit.playerofflinestatus.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.MemorySection
import org.bukkit.scheduler.BukkitTask
import top.iseason.bukkit.playerofflinestatus.dto.GermSlotBackup
import top.iseason.bukkit.playerofflinestatus.dto.PlayerGermSlots
import top.iseason.bukkit.playerofflinestatus.dto.PlayerPAPIs
import top.iseason.bukkit.playerofflinestatus.germ.GermHook
import top.iseason.bukkittemplate.config.SimpleYAMLConfig
import top.iseason.bukkittemplate.config.annotations.Comment
import top.iseason.bukkittemplate.config.annotations.FilePath
import top.iseason.bukkittemplate.config.annotations.Key
import top.iseason.bukkittemplate.utils.other.submit
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Random
import kotlin.math.abs

@FilePath("setting.yml")
object Config : SimpleYAMLConfig() {

    @Key
    @Comment("服务端ID，范围0~31，用于雪花算法")
    var serverId: Long = abs(Random().nextLong() % 32)

    @Key
    @Comment("", "离线缓存设置")
    var placeholder: MemorySection? = null

    @Key
    @Comment("", "当玩家上线时是否显示实时的变量")
    var placeholder__proxy_online: Boolean = true

    @Key
    @Comment("", "需要离线缓存的变量，请不要带 % 或者 {}")
    var placeholder__offline_placeholders = setOf<String>()

    @Key
    @Comment(
        "",
        "更新缓存的时间间隔, 单位tick ,-1 关闭",
        "除了这个之外玩家登录也会更新一次"
    )
    var placeholder__update_period = 600L

    @Key
    @Comment(
        "",
        "每次更新缓存时，将遍历此时在线的玩家，此项为遍历的时间间隔, 单位tick"
    )
    var placeholder__queue_delay = 20L

    @Key
    @Comment(
        "", "papi的缓存时间, 单位秒，用于防止高频读取导致数据库繁忙, 重启生效",
        " -1 表示禁用缓存",
    )
    var placeholder__cache_time = 30L

    @Key
    @Comment(
        "", "插件默认使用 PlaceholderAPI 2.11 ，以下版本请打开此选项"
    )
    var placeholder__old_placeHolder_version = true

    @Key
    @Comment("", "", "离线萌芽槽设置")
    var germ: MemorySection? = null

    @Key
    @Comment("", "是否开启,重启生效")
    var germ__enable = false

    @Key
    @Comment("", "萌芽离线槽的dosId, 重启生效")
    var germ__dos_id = "pos"

    @Key
    @Comment("", "当玩家在线时是否显示实时的槽")
    var germ__proxy_online: Boolean = true

    @Key
    @Comment(
        "",
        "插件保存的萌芽槽",
        "germplugin_armor_helmet 支持实体 头部",
        "germplugin_armor_chestplate 支持实体 胸部",
        "germplugin_armor_leggings 支持实体 腿部",
        "germplugin_armor_boots 支持实体 鞋子",
        "germplugin_main_hand 支持实体 主手",
        "germplugin_off_hand 支持实体 副手",
        "dos格式为 pos<->玩家ID@装备id",
        "对于实体有 pos<->玩家ID@body 一次设置4个装备",
        "对于实体有 pos<->玩家ID@body_with_hands 一次设置4个装备+2只手",
    )
    var germ__offline_slots = setOf(
        "germplugin_armor_helmet",
        "germplugin_armor_chestplate",
        "germplugin_armor_leggings",
        "germplugin_armor_boots"
    )

    @Key
    @Comment(
        "",
        "更新缓存的时间间隔, 单位tick -1 关闭",
        "除了这个之外玩家登录也会更新一次"
    )
    var germ__update_period = 600L

    @Key
    @Comment(
        "",
        "每次更新缓存时，将遍历此时在线的玩家，此项为遍历的时间间隔, 单位tick"
    )
    var germ__queue_delay = 20L

    @Key
    @Comment(
        "", "萌芽dos的缓存时间, 单位秒，用于防止高频读取导致数据库繁忙, 重启生效",
        "-1 表示禁用缓存，将实时从数据库获取",
    )
    var germ__cache_time = 600L

    @Key
    @Comment("", "接管萌芽的槽，如果是第一次用会导致原来的槽数据丢失，请注意,重启生效。")
    var germ__slot_holder = false

    @Key
    @Comment(
        "", "服务器将定时把缓存中的槽同步到数据库，此为周期，单位tick",
        "设为 -1 不定时同步, 设为 0 实时同步（玩家将放入槽中就保存到数据库）"
    )
    var germ__slot_sync_period = 300L

    @Key
    @Comment("", "", "萌芽槽备份设置")
    var germ_slot_backup: MemorySection? = null

    @Key
    @Comment("", "是否开启萌芽槽备份")
    var germ_slot_backup__enable = false

    @Key
    @Comment(
        "",
        "萌芽备份槽的dosId, 重启生效",
        "格式: {dosId}<->玩家ID@auto_canvas 只适用于 canvas 类型",
        "以relativeParts下第一个组件为模板设置所有备份槽",
        "具有变量: %thisPart_backup_id% 为 备份ID; %thisPart_backup_time% 为备份时间",
        "其他格式: {dosId}<->备份ID@槽id 将设置物品为某个槽的物品",
        "其他格式: {dosId}<->备份ID@all 只适用于 canvas 类型 自动填充物品，也需要一个模板",
    )
    var germ_slot_backup__dos_id = "posb"

    @Key
    @Comment("", "备份的时间格式")
    var germ_slot_backup__dos_time_format = "yyyy-MM-dd HH:mm:ss"

    var germSlotBackupTimeFormat = DateTimeFormatter.ofPattern(germ_slot_backup__dos_time_format)

    @Key
    @Comment("", "使用萌芽备份槽的dos时, 同一个备份的缓存时间，单位秒, -1关闭,重启生效")
    var germ_slot_backup__dos_cache_time = 3L

    @Key
    @Comment("", "备份的萌芽槽，留空表示所有。所有槽的顺序是随机的，自己配置是有序的")
    var germ_slot_backup__slots = mutableListOf<String>()

    @Key
    @Comment("", "最大备份数量，超过将删除, 小于等于0 表示 无限制")
    var germ_slot_backup__max = 3

    @Key
    @Comment("", "玩家退出时立即备份一次, 只有开启了 germ.slot-holder 才是异步的")
    var germ_slot_backup__backup_on_quit = true

    @Key
    @Comment("", "备份周期, 单位tick, -1 关闭")
    var germ_slot_backup__period = 6000L

    @Key
    @Comment("", "每次更新缓存时，将遍历此时在线的玩家，此项为遍历的时间间隔, 单位tick")
    var germ_slot_backup__queue_delay = 10L

    private var papiTask: BukkitTask? = null
    private var germTask: BukkitTask? = null
    private var germBackupTask: BukkitTask? = null
    var isInit = false
    override fun onLoaded(section: ConfigurationSection) {
        if (placeholder__queue_delay < 0) placeholder__queue_delay = 0L
        if (germ__queue_delay < 0) germ__queue_delay = 0L
        if (germ_slot_backup__queue_delay < 0) germ_slot_backup__queue_delay = 0L
        germSlotBackupTimeFormat = DateTimeFormatter.ofPattern(germ_slot_backup__dos_time_format)
        if (!isInit) return
        updateTask()
    }

    fun updateTask() {
        papiTask?.cancel()
        germTask?.cancel()
        germBackupTask?.cancel()
        if (placeholder__update_period > 0) {
            papiTask = submit(period = placeholder__update_period, async = true, delay = placeholder__update_period) {
                PlayerPAPIs.uploadAll()
            }
        }
        if (GermHook.hasHooked && germ__enable && germ__update_period > 0) {
            germTask = submit(period = germ__update_period, async = true, delay = placeholder__update_period) {
                PlayerGermSlots.uploadAll()
            }
        }
        if (GermHook.hasHooked && germ_slot_backup__enable && germ_slot_backup__period > 0) {
            germBackupTask = submit(async = true, delay = germ_slot_backup__period, period = germ_slot_backup__period) {
                GermSlotBackup.backupAll()
            }
        }
    }
}