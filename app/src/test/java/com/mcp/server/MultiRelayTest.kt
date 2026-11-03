package com.mcp.server

import android.content.Context
import com.mcp.server.server.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 【模拟环境】1.4.2 最多 3 个中继服务器：1.4.x 的配置就是服务器 1（密钥不迁移），每个服务器的密钥分开存，
 * 删除 / 撤销 / 清除只影响该影响的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiRelayTest {
    private fun fresh(): Context {
        val app = TestSupport.fresh()
        Settings.cipherFactory = { SecretStoreTest.FakeCipher() }
        return app
    }

    private val s1 = "device-secret-one-0123456789"
    private val s2 = "device-secret-two-0123456789"

    @Test
    fun oldSingleRelayBecomesServerOne() {
        val app = fresh()
        Settings.saveRelay(app, "wss://old.example/device", s1, "")      // the 1.4.x call
        Settings.setRelayEnabled(app, true)
        // a 1.4.x install never wrote the "added" flag
        Settings.prefs(app).edit().remove("relay_added").remove("relay_name").commit()
        val list = Settings.relaySlots(app)
        assertEquals(1, list.size)
        assertEquals(1, list[0].slot)
        assertEquals("我的服务器", list[0].name)
        assertEquals("old.example", list[0].host)
        assertTrue(list[0].enabled)
        assertEquals(s1, Settings.relaySecret(app, 1))
        assertEquals(2, Settings.freeRelaySlot(app))
    }

    @Test
    fun threeServersAtMostEachWithItsOwnSecret() {
        val app = fresh()
        assertTrue(Settings.relaySlots(app).isEmpty())
        assertFalse(Settings.anyRelayEnabled(app))
        Settings.saveRelay(app, 1, "家里", "wss://a.example/device", s1, "")
        Settings.saveRelay(app, 2, "测试", "wss://b.example/device", s2, "")
        Settings.saveRelay(app, 3, "", "wss://c.example/device", "device-secret-three-0123456789", "")
        assertNull(Settings.freeRelaySlot(app))
        assertEquals(listOf("家里", "测试", "服务器 3"), Settings.relaySlots(app).map { it.name })
        Settings.resetForTest()
        Settings.cipherFactory = { SecretStoreTest.FakeCipher() }
        assertEquals(s1, Settings.relaySecret(app, 1))
        assertEquals(s2, Settings.relaySecret(app, 2))
        assertEquals("wss://b.example/device", Settings.relayConfig(app, "t", 2).url)
        assertEquals(s2, Settings.relayConfig(app, "t", 2).deviceSecret)
        // every secret in use is masked in logs
        Settings.relaySecret(app, 3)
        assertTrue(Settings.secrets(app).containsAll(listOf(s1, s2, "device-secret-three-0123456789")))
    }

    @Test
    fun switchesAreSeparate() {
        val app = fresh()
        Settings.saveRelay(app, 1, "a", "wss://a.example/device", s1, "")
        Settings.saveRelay(app, 2, "b", "wss://b.example/device", s2, "")
        Settings.setRelayEnabled(app, 2, true)
        assertFalse(Settings.relayEnabled(app, 1))
        assertTrue(Settings.relayEnabled(app, 2))
        assertTrue(Settings.anyRelayEnabled(app))
    }

    @Test
    fun deletingOneLeavesTheOthersAndFreesItsSlot() {
        val app = fresh()
        Settings.saveRelay(app, 1, "a", "wss://a.example/device", s1, "")
        Settings.saveRelay(app, 2, "b", "wss://b.example/device", s2, "sha256/x")
        Settings.setRelayEnabled(app, 1, true)
        Settings.deleteRelay(app, 1)
        assertEquals(listOf(2), Settings.relaySlots(app).map { it.slot })
        assertEquals("", Settings.relaySecret(app, 1))
        assertFalse(Settings.relayEnabled(app, 1))
        assertEquals(s2, Settings.relaySecret(app, 2))
        assertEquals(1, Settings.freeRelaySlot(app))
    }

    @Test
    fun revokeAndClearAllCoverEveryServer() {
        val app = fresh()
        Settings.saveRelay(app, 1, "a", "wss://a.example/device", s1, "sha256/x")
        Settings.saveRelay(app, 2, "b", "wss://b.example/device", s2, "sha256/y")
        Settings.setRelayEnabled(app, 1, true)
        Settings.setRelayEnabled(app, 2, true)
        Settings.revokeRelayCredentials(app)
        assertFalse(Settings.anyRelayEnabled(app))
        assertEquals("", Settings.relaySecret(app, 2))
        assertEquals("", Settings.relayPin(app, 2))
        assertEquals(2, Settings.relaySlots(app).size)              // addresses and names stay
        Settings.clearAllCredentials(app)
        assertTrue(Settings.relaySlots(app).isEmpty())
    }
}
