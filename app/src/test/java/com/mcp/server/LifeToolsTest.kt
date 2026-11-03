package com.mcp.server

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mcp.server.tools.PlaceStore
import com.mcp.server.tools.StepStore
import com.mcp.server.tools.ToolKit
import com.mcp.server.core.ReadOnlyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** 【模拟环境】1.4.1 生活状态工具：结果形状、没权限时不猜、步数的起点、Wi-Fi 名称不外传、每个工具都有大白话说明。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LifeToolsTest {

    @Before
    fun setUp() {
        val app = TestSupport.fresh()
        app.getSharedPreferences("steps", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("wifi_places", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun result(name: String) = TestSupport.call(name).getJSONObject("result").getJSONObject("structuredContent")

    @Test
    fun alarmAndAudioAreOnByDefaultAndAnswer() {
        val app = TestSupport.app()
        assertTrue(ToolKit.isEnabled("next_alarm") && ToolKit.isEnabled("audio_output"))
        assertFalse(result("next_alarm").getBoolean("hasAlarm"))
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getActivity(app, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 7 * 3_600_000L, pi), pi)
        val alarm = result("next_alarm")
        assertTrue(alarm.getBoolean("hasAlarm"))
        assertTrue(alarm.getLong("minutesUntil") in 418L..420L)
        assertFalse(result("audio_output").getBoolean("headphonesOrBluetooth"))
    }

    @Test
    fun withoutTheSystemPermissionTheyRefuseInsteadOfGuessing() {
        for (n in listOf("now_playing", "step_count", "calendar_events")) {
            ToolKit.setEnabled(n, true)
            val r = TestSupport.call(n).getJSONObject("result").getJSONObject("structuredContent")
            assertEquals(n, "PERMISSION_DENIED", r.getString("errorCode"))
        }
    }

    @Test
    fun networkNeverReturnsTheWifiName() {
        ToolKit.setEnabled("network_state", true)
        val r = result("network_state")
        assertTrue(r.has("type"))
        assertFalse(r.toString().contains("ssid", ignoreCase = true))
        PlaceStore.set(TestSupport.app(), "MyHomeWifi", "家")
        assertEquals("家", PlaceStore.label(TestSupport.app(), "MyHomeWifi"))
        PlaceStore.set(TestSupport.app(), "MyHomeWifi", "")
        assertNull(PlaceStore.label(TestSupport.app(), "MyHomeWifi"))
    }

    @Test
    fun stepsCountFromTheDayStartAndSurviveAReboot() {
        val app = TestSupport.app()
        val now = System.currentTimeMillis()
        // yesterday's last reading 10000, today 10800 -> 800 since midnight
        app.getSharedPreferences("steps", Context.MODE_PRIVATE).edit()
            .putString("day", LocalDate.now(ZoneId.systemDefault()).minusDays(1).toString())
            .putFloat("last", 10_000f).putFloat("base", 2_000f).commit()
        StepStore.record(app, 10_800f, now)
        assertEquals(800, StepStore.today(app, now)!!.first)
        StepStore.record(app, 11_000f, now)
        assertEquals(1_000, StepStore.today(app, now)!!.first)
        StepStore.record(app, 50f, now)                       // rebooted: the counter starts again from 0
        assertEquals(1_050, StepStore.today(app, now)!!.first)
    }

    @Test
    fun phoneEventsComeInOrderAfterACursor() {
        val events = com.mcp.server.tools.PhoneEvents
        events.resetForTest()
        ToolKit.setEnabled("phone_events", true)
        val t0 = System.currentTimeMillis()
        events.onBroadcast(Intent.ACTION_SCREEN_OFF, t0)
        events.onBroadcast(Intent.ACTION_USER_PRESENT, t0 + 7 * 3_600_000L)
        events.onBroadcast(Intent.ACTION_POWER_DISCONNECTED, t0 + 7 * 3_600_000L + 60_000L)
        val all = result("phone_events")
        assertEquals(3L, all.getLong("latestSeq"))
        val unlock = all.getJSONArray("events").getJSONObject(1)
        assertEquals("unlock", unlock.getString("type"))
        assertEquals(420L, unlock.getJSONObject("detail").getLong("screenOffMinutes"))
        val later = TestSupport.call("phone_events", org.json.JSONObject().put("after", 2))
            .getJSONObject("result").getJSONObject("structuredContent").getJSONArray("events")
        assertEquals(1, later.length())
        assertEquals("charger_disconnected", later.getJSONObject(0).getString("type"))
        assertFalse(ToolKit.PLAIN["phone_events"]!!.text.isBlank())
    }

    @Test
    fun everyToolHasAPlainTitleAndText() {
        for (n in ReadOnlyPolicy.ALLOWED_TOOLS) {
            val p = ToolKit.plain(n)
            assertTrue("$n 需要中文名", p.title != n && p.title.isNotBlank())
            assertTrue("$n 需要大白话说明", p.text.startsWith("她") && p.text.length >= 8)
        }
    }
}
