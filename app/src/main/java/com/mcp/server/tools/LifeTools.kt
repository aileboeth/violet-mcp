package com.mcp.server.tools

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings as AndroidSettings
import com.mcp.server.core.Err
import com.mcp.server.core.ErrorCodes
import com.mcp.server.core.ToolDefinition
import com.mcp.server.core.emptySchema
import com.mcp.server.core.inCategory
import com.mcp.server.core.param
import com.mcp.server.core.paramSchema
import com.mcp.server.server.MediaListenerService
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 1.4.1 生活状态（只读）：下一个闹钟、正在播放的音乐、网络（Wi-Fi / 流量 + 用户自己起名的地点）、今天的步数、
 * 今天和明天的日程、耳机 / 蓝牙音频。用户要求（2026-11-03）：让 Violet 知道你在干嘛，再判断要不要开口、怎么开口。
 * 不读勿扰模式（用户长期开着勿扰，靠手表提醒）。每个工具都要在“工具与权限”页单独打开；需要系统权限的，没授权时
 * 返回 PERMISSION_DENIED，不猜。
 */
object LifeTools {

    const val CATEGORY = "生活状态"

    fun register(ctx: Context, sink: MutableList<ToolDefinition>) {
        sink.add(nextAlarm(ctx).inCategory(CATEGORY))
        sink.add(nowPlaying(ctx).inCategory(CATEGORY))
        sink.add(audioOutput(ctx).inCategory(CATEGORY))
        sink.add(networkState(ctx).inCategory(CATEGORY))
        sink.add(stepCount(ctx).inCategory(CATEGORY))
        sink.add(calendarEvents(ctx).inCategory(CATEGORY))
        sink.add(PhoneEvents.tool())
    }

    private fun granted(ctx: Context, permission: String): Boolean =
        ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun denied(what: String, hint: String): JSONObject =
        Err.of(ErrorCodes.PERMISSION_DENIED, "没有$what", hint).put("queryTime", TimeFmt.stamp(System.currentTimeMillis()))

    // ==================== next_alarm ====================

    private fun nextAlarm(ctx: Context): ToolDefinition = ToolDefinition(
        "next_alarm",
        "读取手机上下一个闹钟的响铃时间（系统闹钟接口，任何闹钟 App 设置的都算）。只读。" +
            "返回 hasAlarm、time（本地时间）、minutesUntil；没有闹钟时 hasAlarm=false。",
        emptySchema(),
    ) {
        val now = System.currentTimeMillis()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = am.nextAlarmClock
        JSONObject().apply {
            put("hasAlarm", next != null)
            if (next != null) {
                put("time", TimeFmt.stamp(next.triggerTime))
                put("minutesUntil", ((next.triggerTime - now) / 60_000L).coerceAtLeast(0))
            }
            put("queryTime", TimeFmt.stamp(now))
        }
    }

    // ==================== now_playing ====================

    fun hasNotificationAccess(ctx: Context): Boolean {
        val flat = AndroidSettings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(ctx, MediaListenerService::class.java).flattenToString()
        return flat.split(":").any { it == me }
    }

    private fun nowPlaying(ctx: Context): ToolDefinition = ToolDefinition(
        "now_playing",
        "读取手机上正在播放的媒体（音乐 / 播客 / 视频）：应用、标题、歌手 / 作者、是否在播放。只读，" +
            "只看媒体会话，不读任何通知内容。需要用户在系统里给本应用“通知使用权”，没有时返回 PERMISSION_DENIED。",
        emptySchema(),
    ) {
        if (!hasNotificationAccess(ctx)) {
            return@ToolDefinition denied("“通知使用权”", "在手机上打开本应用 → 工具与权限 → 正在播放 → 去授权")
        }
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = try {
            msm.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java))
        } catch (_: SecurityException) {
            return@ToolDefinition denied("“通知使用权”", "在手机上打开本应用 → 工具与权限 → 正在播放 → 去授权")
        }
        val items = JSONArray()
        for (c in sessions) {
            val meta = c.metadata
            val state = c.playbackState?.state
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            if (title.isNullOrBlank()) continue
            items.put(JSONObject().apply {
                put("app", AppLabels.label(ctx, c.packageName) ?: c.packageName)
                put("title", title)
                put("artist", meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: JSONObject.NULL)
                put("album", meta.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: JSONObject.NULL)
                put("playing", state == PlaybackState.STATE_PLAYING)
            })
        }
        JSONObject().apply {
            put("playing", (0 until items.length()).any { items.getJSONObject(it).optBoolean("playing") })
            put("sessions", items)
            put("queryTime", TimeFmt.stamp(System.currentTimeMillis()))
        }
    }

    // ==================== audio_output ====================

    private fun audioOutput(ctx: Context): ToolDefinition = ToolDefinition(
        "audio_output",
        "读取声音现在从哪里出来：有没有连着有线耳机、蓝牙耳机 / 音箱，以及有没有在放声音。只读，不需要额外权限。",
        emptySchema(),
    ) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = JSONArray()
        var headphones = false
        for (d in am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            val kind = when (d.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired_headphones"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "usb_headset"
                else -> if (Build.VERSION.SDK_INT >= 31 && (d.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) "bluetooth" else null
            } ?: continue
            headphones = true
            devices.put(JSONObject().put("type", kind).put("name", d.productName?.toString() ?: JSONObject.NULL))
        }
        JSONObject().apply {
            put("headphonesOrBluetooth", headphones)
            put("devices", devices)
            put("musicActive", am.isMusicActive)
            put("queryTime", TimeFmt.stamp(System.currentTimeMillis()))
        }
    }

    // ==================== network_state ====================

    private fun networkState(ctx: Context): ToolDefinition = ToolDefinition(
        "network_state",
        "读取手机现在用的网络：wifi / cellular / none。连着 Wi-Fi 时，如果用户在本应用里给这个 Wi-Fi 起过名字" +
            "（如“家”“学校”），返回 place；不返回 Wi-Fi 名称本身。只读。",
        emptySchema(),
    ) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val type = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        JSONObject().apply {
            put("type", type)
            if (type == "wifi") {
                val key = currentWifiKey(ctx, caps)
                put("place", key?.let { PlaceStore.label(ctx, it) } ?: JSONObject.NULL)
                if (key == null) put("note", "没有定位权限，认不出是哪个 Wi-Fi（只知道连着 Wi-Fi）")
            }
            put("queryTime", TimeFmt.stamp(System.currentTimeMillis()))
        }
    }

    /** 当前 Wi-Fi 的识别用键（SSID）；需要定位权限，拿不到时 null。只在手机里比对，不对外返回。 */
    fun currentWifiKey(ctx: Context, caps: NetworkCapabilities? = null): String? {
        if (!granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) return null
        // Android 12+：从网络能力里拿（带定位权限才有名称）。更早的系统要 ACCESS_WIFI_STATE，本应用不申请，
        // 拿不到就当认不出（SecurityException 也一样）
        val info: WifiInfo? = try {
            if (Build.VERSION.SDK_INT >= 31) {
                (caps ?: (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).let {
                    it.getNetworkCapabilities(it.activeNetwork)
                })?.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION")
                (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo
            }
        } catch (_: SecurityException) {
            null
        }
        val ssid = info?.ssid?.trim('"') ?: return null
        return if (ssid.isEmpty() || ssid == "<unknown ssid>") null else ssid
    }

    // ==================== step_count ====================

    private fun stepCount(ctx: Context): ToolDefinition = ToolDefinition(
        "step_count",
        "读取今天走了多少步（手机计步传感器）。只读。需要“身体活动”权限；手机没有计步传感器时返回 UNAVAILABLE。" +
            "countedSince 是今天开始计数的时间：本应用当天第一次读到计步器之前的步数可能没算进去。",
        emptySchema(),
    ) {
        if (Build.VERSION.SDK_INT >= 29 && !granted(ctx, Manifest.permission.ACTIVITY_RECOGNITION)) {
            return@ToolDefinition denied("“身体活动”权限", "在手机上打开本应用 → 工具与权限 → 今天的步数 → 去授权")
        }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: return@ToolDefinition Err.of(ErrorCodes.UNAVAILABLE, "这台手机没有计步传感器")
        val value = readOnce(sm, sensor)
        val now = System.currentTimeMillis()
        if (value != null) StepStore.record(ctx, value, now)
        val today = StepStore.today(ctx, now)
            ?: return@ToolDefinition Err.of(ErrorCodes.UNAVAILABLE, "计步器暂时没有给出读数，稍后再试")
        JSONObject().apply {
            put("stepsToday", today.first)
            put("countedSince", TimeFmt.stamp(today.second))
            put("queryTime", TimeFmt.stamp(now))
        }
    }

    private fun readOnce(sm: SensorManager, sensor: Sensor): Float? {
        val latch = CountDownLatch(1)
        var value: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                value = event.values.firstOrNull()
                latch.countDown()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        try {
            latch.await(2, TimeUnit.SECONDS)
        } finally {
            sm.unregisterListener(listener)
        }
        return value
    }

    // ==================== calendar_events ====================

    private fun calendarEvents(ctx: Context): ToolDefinition = ToolDefinition(
        "calendar_events",
        "读取手机日历里今天和明天的日程：标题、开始 / 结束时间、是否全天。只读，不读地点、参与人和备注。" +
            "需要“日历”权限。",
        paramSchema(listOf(
            param("days", "integer", example = 2, minimum = 1, maximum = 7, description = "从今天起读几天，默认 2（今天和明天）"),
        )),
    ) { args ->
        if (!granted(ctx, Manifest.permission.READ_CALENDAR)) {
            return@ToolDefinition denied("“日历”权限", "在手机上打开本应用 → 工具与权限 → 今天和明天的日程 → 去授权")
        }
        val zone = ZoneId.systemDefault()
        val days = args.optInt("days", 2).coerceIn(1, 7)
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.now(zone).plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString()).appendPath(end.toString()).build()
        val cols = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.SELF_ATTENDEE_STATUS)
        val events = JSONArray()
        ctx.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && events.length() < 30) {
                if (c.getInt(4) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                events.put(JSONObject().apply {
                    put("title", c.getString(0) ?: "")
                    put("allDay", c.getInt(3) == 1)
                    put("start", TimeFmt.stamp(c.getLong(1)))
                    put("end", TimeFmt.stamp(c.getLong(2)))
                })
            }
        }
        JSONObject().apply {
            put("days", days)
            put("events", events)
            put("queryTime", TimeFmt.stamp(System.currentTimeMillis()))
        }
    }
}

/** 用户给 Wi-Fi 起的名字（SSID -> “家”），只存在手机上 */
object PlaceStore {
    private const val PREFS = "wifi_places"

    fun label(ctx: Context, key: String): String? = prefs(ctx).getString(key, null)

    fun set(ctx: Context, key: String, label: String?) {
        prefs(ctx).edit().apply { if (label.isNullOrBlank()) remove(key) else putString(key, label.trim().take(20)) }.apply()
    }

    fun all(ctx: Context): Map<String, String> =
        prefs(ctx).all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * 今天的步数：计步器从开机起累计，这里记下“今天从多少开始”。日期变了，用昨天最后一次读数作今天的起点
 * （本应用在后台时也会顺带读，见 McpServerService）；重启后读数变小，按已走的步数接上。
 */
object StepStore {
    private const val PREFS = "steps"

    @Synchronized
    fun record(ctx: Context, value: Float, now: Long) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = LocalDate.now(ZoneId.systemDefault()).toString()
        val lastDay = p.getString("day", null)
        val last = p.getFloat("last", -1f)
        var base = p.getFloat("base", 0f)
        var since = p.getLong("since", now)
        if (lastDay != day || !p.contains("base")) {
            // 新的一天：起点是昨天最后的读数（若昨天读过且没有重启），否则就是现在
            base = if (lastDay != null && last in 0f..value) last else value
            since = if (lastDay != null && last in 0f..value) startOfToday() else now
        } else if (value < last) {
            // 重启：计步器从 0 重新数。今天 = 重启前已走的 (last - base) + 现在的读数，所以起点 = -(last - base)
            // （可以是负数；“有没有起点”看 contains，不看正负）
            base = -(last - base)
        }
        p.edit().putString("day", day).putFloat("last", value).putFloat("base", base).putLong("since", since).apply()
    }

    /** (今天步数, 开始计数时间)；今天还没读到过时 null */
    fun today(ctx: Context, now: Long): Pair<Int, Long>? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString("day", null) != LocalDate.now(ZoneId.systemDefault()).toString()) return null
        val steps = (p.getFloat("last", 0f) - p.getFloat("base", 0f)).toInt().coerceAtLeast(0)
        return steps to p.getLong("since", now)
    }

    private fun startOfToday(): Long =
        LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
