package top.iseason.bukkit.playerofflinestatus.config

import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.scheduler.BukkitTask
import top.iseason.bukkit.playerofflinestatus.germ.GermHook
import top.iseason.bukkit.playerofflinestatus.dto.PlayerItems
import top.iseason.bukkit.playerofflinestatus.dto.PlayerPAPIs
import top.iseason.bukkittemplate.config.SimpleYAMLConfig
import top.iseason.bukkittemplate.config.annotations.Comment
import top.iseason.bukkittemplate.config.annotations.FilePath
import top.iseason.bukkittemplate.config.annotations.Key
import top.iseason.bukkittemplate.debug.debug
import top.iseason.bukkittemplate.utils.other.submit

@FilePath("config.yml")
object Config : SimpleYAMLConfig() {

    @Key
    @Comment("", "当玩家上线时是否显示实时的变量")
    var deletedOnline: Boolean = true

    @Key
    @Comment("", "需要离线缓存的变量，请不要带 % 或者 {}")
    var offlinePAPIs = setOf<String>()

    @Key
    @Comment(
        "",
        "更新缓存的时间间隔, 单位tick",
        "除了这个之外玩家登录也会更新一次"
    )
    var updatePeriod = 600L

    @Key
    @Comment(
        "",
        "每次更新缓存时，将遍历此时在线的玩家，此项为遍历的时间间隔, 单位tick"
    )
    var queueDelay = 20L

    @Key
    @Comment("", "是否异步更新缓存")
    var updateAsync = true

    @Key
    @Comment(
        "", "papi的缓存时间, 单位毫秒，用于防止高频读取导致数据库繁忙",
        "-1 表示服务器运行时不过期"
    )
    var cacheTime = 30000L

    @Key
    @Comment(
        "", "插件默认使用 PlaceholderAPI 2.11 ，以下版本请打开此选项"
    )
    var oldPlaceHolder = true

    @Key
    @Comment(
        "",
        "插件保存的装备",
        "model_开头表示界面的模型",
        "head、chest、legs、feet 分别对应实体 头、胸甲、腿、脚",
        "除以上都是萌芽槽ID",
        "dos格式为 pos<->玩家ID@装备id",
        "对于实体有 pos<->玩家ID@body 一次设置装备",
    )
    var equipments = setOf(
        "head",
        "chest",
        "legs",
        "feet"
    )

    @Key
    @Comment(
        "", "萌芽dos的缓存时间, 单位毫秒，用于防止高频读取导致数据库繁忙",
        "-1 表示服务器运行时不过期"
    )
    var germCacheTime = 600000L


    @Key
    @Comment("", "槽备份功能，将会接管萌芽的槽，导致原来的数据丢失，请注意,重启生效。")
    var germSlotBackup = false

    @Key
    @Comment("", "服务器将定时把缓存中的槽同步到数据库，此为周期，单位tick。-1不定时同步")
    var germSlotSyncPeriod = 300L

//    @Key
//    var maxGermSlotBackup = 3

    private var task: BukkitTask? = null

    override fun onLoaded(section: ConfigurationSection) {
        task?.cancel()
        task = submit(period = updatePeriod, async = updateAsync) {
            var submit: BukkitTask? = null
            val onlinePlayers = Bukkit.getOnlinePlayers()
            if (onlinePlayers.isEmpty()) return@submit
            debug("&7开始更新缓存...共 ${onlinePlayers.size} 人")
            val iterator = onlinePlayers.iterator()
            submit = submit(period = queueDelay, async = updateAsync) mit@{
                if (!iterator.hasNext()) {
                    submit?.cancel()
                    return@mit
                }
                val player = iterator.next()
                PlayerPAPIs.update(player)
                if (GermHook.hasHooked)
                    PlayerItems.update(player)
            }
        }
    }
}