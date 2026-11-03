package com.mcp.server.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.mcp.server.core.ToolDefinition
import com.mcp.server.core.inCategory
import com.mcp.server.core.param
import com.mcp.server.core.paramSchema
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 1.4.1 手机上的动静（用户：手机主动推送）。服务运行时记下几类变化——解锁、亮屏 / 熄屏、插拔充电器、
 * 戴上 / 摘下耳机、到达 / 离开起过名字的 Wi-Fi 地点——放在内存里的环形队列（最多 200 条，不写文件）。
 * Violet 每分钟用只读工具 phone_events 取“某个序号之后的新动静”，所以中继不用改，手机离线时也不会堆积。
 * epoch 每次服务启动都变：Violet 看到 epoch 变了就从头取。
 */
object PhoneEvents {

    private const val MAX = 200
    val epoch: String = UUID.randomUUID().toString().substring(0, 8)
    private val buffer = ArrayDeque<JSONObject>()
    private var seq = 0L
    private var lastScreenOff = 0L
    private var lastPlace: String? = null
    private var receiver: BroadcastReceiver? = null
    private var audioCallback: AudioDeviceCallback? = null

    @Synchronized
    fun record(type: String, detail: JSONObject = JSONObject(), at: Long = System.currentTimeMillis()) {
        seq += 1
        buffer.addLast(JSONObject().put("seq", seq).put("type", type).put("at", TimeFmt.stamp(at)).put("detail", detail))
        while (buffer.size > MAX) buffer.removeFirst()
    }

    @Synchronized
    fun after(after: Long, limit: Int): Pair<Long, List<JSONObject>> =
        seq to buffer.filter { it.getLong("seq") > after }.take(limit)

    @Synchronized
    internal fun resetForTest() {
        buffer.clear()
        seq = 0
        lastScreenOff = 0
        lastPlace = null
    }

    /** 屏幕 / 解锁 / 充电：系统广播（动态注册，服务停止时注销） */
    fun start(ctx: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                onBroadcast(intent.action ?: return)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
        else ctx.registerReceiver(r, filter)
        receiver = r
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val cb = object : AudioDeviceCallback() {
            private var ready = false            // 注册时系统会把已连着的设备报一遍，那不是“刚戴上”

            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                if (ready) added.filter { isHeadphones(it) }.forEach {
                    record("headphones_connected", JSONObject().put("name", it.productName?.toString() ?: ""))
                }
                ready = true
            }

            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                removed.filter { isHeadphones(it) }.forEach {
                    record("headphones_disconnected", JSONObject().put("name", it.productName?.toString() ?: ""))
                }
            }
        }
        am.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        audioCallback = cb
        lastPlace = currentPlace(ctx)
    }

    fun stop(ctx: Context) {
        receiver?.let { runCatching { ctx.unregisterReceiver(it) } }
        receiver = null
        audioCallback?.let { (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(it) }
        audioCallback = null
    }

    internal fun onBroadcast(action: String, now: Long = System.currentTimeMillis()) {
        when (action) {
            Intent.ACTION_SCREEN_OFF -> { synchronized(this) { lastScreenOff = now }; record("screen_off", at = now) }
            Intent.ACTION_SCREEN_ON -> record("screen_on", at = now)
            Intent.ACTION_USER_PRESENT -> {
                val off = synchronized(this) { lastScreenOff }
                val detail = JSONObject()
                if (off > 0) detail.put("screenOffMinutes", (now - off) / 60_000L)
                record("unlock", detail, now)
            }
            Intent.ACTION_POWER_CONNECTED -> record("charger_connected", at = now)
            Intent.ACTION_POWER_DISCONNECTED -> record("charger_disconnected", at = now)
        }
    }

    /** 网络变化时调用（McpServerService 的网络回调）：到达 / 离开起过名字的 Wi-Fi 地点 */
    fun onNetworkChanged(ctx: Context) {
        val place = currentPlace(ctx)
        val before = synchronized(this) { lastPlace.also { lastPlace = place } }
        if (place == before) return
        if (before != null) record("place_left", JSONObject().put("place", before))
        if (place != null) record("place_arrived", JSONObject().put("place", place))
    }

    private fun currentPlace(ctx: Context): String? =
        LifeTools.currentWifiKey(ctx)?.let { PlaceStore.label(ctx, it) }

    private fun isHeadphones(d: AudioDeviceInfo): Boolean = d.type in setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_USB_HEADSET,
    ) || (android.os.Build.VERSION.SDK_INT >= 31 && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    fun tool(): ToolDefinition = ToolDefinition(
        "phone_events",
        "读取手机上最近的动静（按发生顺序）：unlock（解锁，带 screenOffMinutes：之前熄屏了多久）、screen_on、" +
            "screen_off、charger_connected / charger_disconnected、headphones_connected / headphones_disconnected、" +
            "place_arrived / place_left（到达 / 离开用户起过名字的 Wi-Fi 地点，带 place）。只读。" +
            "after 填上次拿到的 latestSeq，只返回之后的；epoch 变了说明手机上的服务重启过，从 0 重新取。",
        paramSchema(listOf(
            param("after", "integer", example = 0, minimum = 0, description = "只要这个序号之后的动静，默认 0"),
            param("limit", "integer", example = 50, minimum = 1, maximum = 200, description = "最多返回多少条，默认 50"),
        )),
    ) { args ->
        val (latest, events) = after(args.optLong("after", 0L).coerceAtLeast(0L), args.optInt("limit", 50).coerceIn(1, 200))
        JSONObject().apply {
            put("epoch", epoch)
            put("latestSeq", latest)
            put("events", JSONArray(events))
            put("queryTime", TimeFmt.stamp(System.currentTimeMillis()))
        }
    }.inCategory(LifeTools.CATEGORY)
}
