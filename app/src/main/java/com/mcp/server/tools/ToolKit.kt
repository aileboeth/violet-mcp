package com.mcp.server.tools

import android.content.Context
import com.mcp.server.core.PrivacyPolicy
import com.mcp.server.core.ToolDefinition
import com.mcp.server.core.ToolRegistry

/**
 * App 侧的工具集合：把只读工具注册进 [ToolRegistry]，并保存每个工具的开关（第二层权限）。
 * - 注册表拒绝任何不在只读白名单里的工具名；开关只能关掉白名单内的工具。
 * - 默认只开启 [PrivacyPolicy.DEFAULT_ENABLED]；其余（尤其 [PrivacyPolicy.SENSITIVE]）需用户在“工具与权限”页单独开启。
 * - 开关只能在 App 界面修改，没有任何 MCP 方法或中继帧可以改动它。
 */
object ToolKit {

    private const val PREFS = "tool_enabled"

    private lateinit var ctx: Context

    @Volatile
    lateinit var registry: ToolRegistry
        private set

    /** 工具需要的 Android 权限（界面展示用） */
    enum class Requirement(val label: String, val required: Boolean = true) {
        NONE(""),
        USAGE_ACCESS("需要“使用情况访问权限”"),
        USAGE_ACCESS_OPTIONAL("“使用情况访问权限”可选（有了能说出亮屏 / 熄屏多久了）", false),
        NOTIFICATION_ACCESS("需要“通知使用权”（只用来看正在播放什么，不读通知内容）"),
        ACTIVITY("需要“身体活动”权限"),
        CALENDAR("需要“日历”权限"),
        LOCATION_OPTIONAL("“位置”权限可选（有了才认得出是哪个 Wi-Fi；不读位置）", false),
    }

    fun requirement(name: String): Requirement = when (name) {
        "get_foreground_app", "get_app_usage_stats" -> Requirement.USAGE_ACCESS
        "get_screen_state" -> Requirement.USAGE_ACCESS_OPTIONAL
        "now_playing" -> Requirement.NOTIFICATION_ACCESS
        "step_count" -> Requirement.ACTIVITY
        "calendar_events" -> Requirement.CALENDAR
        "network_state" -> Requirement.LOCATION_OPTIONAL
        else -> Requirement.NONE
    }

    /**
     * 1.4.1（用户：工具与权限里各个开关的介绍比较难懂）：界面上用的中文名和大白话说明——开了以后 Violet 能知道什么。
     * 工具自己的 description 是写给模型看的，只在详情里显示。
     */
    data class Plain(val title: String, val text: String)

    val PLAIN: Map<String, Plain> = mapOf(
        "get_screen_state" to Plain("屏幕亮没亮", "她能知道你的手机现在亮着还是熄屏、锁没锁，以及亮了 / 熄了多久。"),
        "get_foreground_app" to Plain("正在用的 App", "她能知道你此刻在用哪个 App（比如在刷 B 站）。熄屏或锁屏时不会告诉她。"),
        "get_app_usage_stats" to Plain("各 App 用了多久", "她能知道你今天（或某段时间）各个 App 用了多长时间。"),
        "next_alarm" to Plain("下一个闹钟", "她能知道你明早几点的闹钟，好在晚上提醒你早点睡、早上踩着点说早安。"),
        "now_playing" to Plain("正在播放", "她能知道你在听什么歌、哪个歌手（音乐、播客、视频都算）。"),
        "audio_output" to Plain("耳机", "她能知道你是不是戴着耳机或连着蓝牙音箱、手机有没有在放声音。"),
        "network_state" to Plain("Wi-Fi 还是流量", "她能知道你在用 Wi-Fi 还是流量。给 Wi-Fi 起了名字（如“家”）时，她能知道你在那儿。"),
        "step_count" to Plain("今天的步数", "她能知道你今天走了多少步。"),
        "calendar_events" to Plain("今天和明天的日程", "她能看到你日历里今天和明天的安排（只看标题和时间）。"),
        "phone_events" to Plain("手机上的动静", "她能在一分钟内知道你刚解锁手机、插拔充电器、戴上耳机、到家（起过名字的 Wi-Fi）" +
            "这些变化，好在合适的时候找你，比如你早上第一次解锁时说早安。"),
        "battery" to Plain("电量", "她能知道手机还剩多少电、是不是在充电。"),
        "device_info" to Plain("手机型号", "她能知道你的手机品牌、型号和系统版本。"),
        "screen_info" to Plain("屏幕参数", "她能知道屏幕分辨率、刷新率和亮度设置。"),
        "storage_info" to Plain("存储空间", "她能知道手机存储还剩多少。"),
        "locale_info" to Plain("语言和时区", "她能知道手机的语言和时区。"),
        "system_properties" to Plain("系统版本细节", "她能看到系统的一些版本信息（不含序列号等身份标识）。"),
        "time_now" to Plain("手机时间", "她能读手机上的当前时间。"),
        "app_info" to Plain("某个 App 装没装", "她能查某个 App 有没有装、是什么版本。"),
        "installed_apps" to Plain("装了哪些 App", "她能看到你手机上装了哪些 App。"),
        "permission_state" to Plain("本应用的权限", "她能看到本应用拿到了哪些权限、哪些工具开着。"),
        "health" to Plain("连接自检", "她能确认这个手机服务还在正常运行。"),
    )

    fun plain(name: String): Plain = PLAIN[name] ?: Plain(name, "")

    @Synchronized
    fun init(context: Context) {
        if (::registry.isInitialized) return
        build(context)
    }

    /** 仅测试用：每个 Robolectric 测试都有新的 Application，需要重新构建 */
    @Synchronized
    internal fun initForTest(context: Context) = build(context)

    private fun build(context: Context) {
        ctx = context.applicationContext
        val r = ToolRegistry { isEnabled(it) }
        val list = mutableListOf<ToolDefinition>()
        UsageTools.register(ctx, list)
        LifeTools.register(ctx, list)
        SystemTools.register(ctx, list)
        AppTools.register(ctx, list)
        MetaTools.register(ctx, list)
        list.forEach { r.register(it) }
        registry = r
    }

    fun isEnabled(name: String): Boolean = prefs().getBoolean("tool_$name", PrivacyPolicy.defaultEnabled(name))

    fun setEnabled(name: String, enabled: Boolean) {
        prefs().edit().putBoolean("tool_$name", enabled).apply()
        // 1.4.1：“手机上的动静”开关一拨就开始 / 停止记录（服务在运行时）
        if (name == "phone_events") {
            if (enabled && com.mcp.server.server.McpServerService.running) PhoneEvents.start(ctx) else if (!enabled) PhoneEvents.stop(ctx)
        }
    }

    fun isSensitive(name: String): Boolean = PrivacyPolicy.isSensitive(name)

    fun enabledCount(): Int = registry.enabled().size

    fun totalCount(): Int = registry.all().size

    /** 恢复默认开关 */
    fun resetToDefaults() {
        prefs().edit().clear().apply()
    }

    /** 固定分类顺序 */
    private val categoryOrder = listOf("手机感知", LifeTools.CATEGORY, "设备信息", "应用信息", "服务器")

    /** 分类 -> 工具列表（全部工具，含已关闭的） */
    fun groupedTools(): List<Pair<String, List<ToolDefinition>>> {
        val byCat = registry.all().groupBy { it.category }
        return categoryOrder.mapNotNull { cat -> byCat[cat]?.let { cat to it } } +
            byCat.filterKeys { it !in categoryOrder }.toList()
    }

    private fun prefs(): android.content.SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

}
