package com.mcp.server.server

import android.content.Context
import com.mcp.server.core.RelayConfig
import com.mcp.server.core.Secrets

/**
 * 设置。普通选项存 SharedPreferences（allowBackup=false，不进入备份）；
 * MCP 令牌和中继设备密钥经 [SecretStore] 用 Android Keystore 加密保存，并在内存中缓存解密结果，
 * 避免每个请求都做一次 Keystore 调用。
 */
object Settings {
    private const val PREFS = "mcp_server_settings"
    private const val KEY_TOKEN = "token"
    private const val KEY_RELAY_SECRET = "relay_secret"

    @Volatile
    private var store: SecretStore? = null

    @Volatile
    private var cachedToken: String? = null

    /** 每个服务器位置的设备密钥（解密后的内存缓存） */
    private val cachedRelaySecrets = java.util.concurrent.ConcurrentHashMap<Int, String>()

    /** 仅测试可替换：Robolectric 没有 AndroidKeyStore */
    @Volatile
    internal var cipherFactory: () -> SecretCipher? = { KeystoreCipher.createOrNull() }

    fun secretStore(context: Context): SecretStore {
        store?.let { return it }
        synchronized(this) {
            store?.let { return it }
            val app = context.applicationContext
            return SecretStore(prefs(app), cipherFactory()) { plaintextConsent(app) }.also { store = it }
        }
    }

    // ==================== 凭据存储兼容模式 ====================

    /**
     * 用户是否明确同意在 Keystore 不可用时以明文保存凭据（兼容模式）。默认 false。
     * 只能在“安全”页经确认对话框开启；开启后主页持续显示醒目警告。
     */
    fun plaintextConsent(context: Context): Boolean = prefs(context).getBoolean("plaintext_consent", false)

    /** 开启或关闭兼容模式。关闭时删除所有明文凭据副本并关闭中继（已加密的凭据不受影响） */
    fun setPlaintextConsent(context: Context, on: Boolean) {
        synchronized(this) {
            if (on) {
                prefs(context).edit().putBoolean("plaintext_consent", true).commit()
            } else {
                prefs(context).edit().putBoolean("plaintext_consent", false).commit()
                val st = secretStore(context)
                for (s in RELAY_SLOTS) {
                    if (st.mode(secretKey(s)) == SecretStore.Mode.PLAINTEXT_FALLBACK) {
                        prefs(context).edit().putBoolean(key(s, "enabled"), false).commit()
                    }
                }
                st.deletePlaintext(KEY_TOKEN, *allSecretKeys)
            }
            cachedToken = null
            cachedRelaySecrets.clear()
        }
    }

    /** 凭据存储状态（界面用） */
    enum class CredentialStorage {
        /** 正常：Keystore 加密 */
        ENCRYPTED,
        /** 用户同意的兼容模式：明文保存（醒目提示） */
        PLAINTEXT_CONSENTED,
        /** 只有明文副本或 Keystore 不可用，用户尚未同意：凭据不可用 */
        NEEDS_CONSENT,
        /** 已保存的密文无法解密：需要重新生成 */
        UNREADABLE,
    }

    fun credentialStorage(context: Context): CredentialStorage {
        val st = secretStore(context)
        val reads = listOf(st.read(KEY_TOKEN)) + allSecretKeys.map { st.read(it) }
        return when {
            reads.any { it is SecretStore.Read.Unreadable } -> CredentialStorage.UNREADABLE
            reads.any { it is SecretStore.Read.NeedsConsent } -> CredentialStorage.NEEDS_CONSENT
            reads.any { it is SecretStore.Read.Value && it.mode == SecretStore.Mode.PLAINTEXT_FALLBACK } ->
                CredentialStorage.PLAINTEXT_CONSENTED
            !st.keystoreAvailable && !plaintextConsent(context) -> CredentialStorage.NEEDS_CONSENT
            else -> CredentialStorage.ENCRYPTED
        }
    }

    // ==================== 本地端点（只监听 127.0.0.1） ====================

    fun port(context: Context): Int = prefs(context).getInt("port", 1145)

    fun setPort(context: Context, port: Int) {
        prefs(context).edit().putInt("port", port.coerceIn(1024, 65535)).apply()
    }

    /** 1.1.0 曾提供的“局域网访问”（明文 HTTP）已移除；旧配置仍为开启时在界面上提示一次 */
    fun legacyLanAccessWasOn(context: Context): Boolean = prefs(context).getBoolean("lan_access", false)

    fun clearLegacyLanAccess(context: Context) {
        prefs(context).edit().remove("lan_access").apply()
    }

    // ==================== 后台运行 ====================

    /** 开机（以及应用更新后）自动恢复运行 */
    fun autoStart(context: Context): Boolean = prefs(context).getBoolean("auto_start", false)

    fun setAutoStart(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("auto_start", on).apply()
    }

    /**
     * 用户期望的运行状态：点“启动”为 true，点“停止”为 false。
     * 开机自启、系统粘性重启都只在 true 时恢复，用户手动停止后不会被程序拉起。
     * 没有这个值时（新安装、或从 1.1.0 升级）取“开机自启”的设置：新安装为 false；
     * 1.1.0 开启了自启的用户升级后仍会自动恢复运行。
     */
    fun desiredRunning(context: Context): Boolean =
        prefs(context).getBoolean("desired_running", autoStart(context))

    fun setDesiredRunning(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("desired_running", on).commit()
    }

    /** 常驻 WakeLock（默认关闭；处理单个请求时总会持有 10 秒以内的短时 WakeLock） */
    fun wakeLockEnabled(context: Context): Boolean = prefs(context).getBoolean("wakelock", false)

    fun setWakeLockEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("wakelock", on).apply()
    }

    /** 日志级别：debug / info / warn */
    fun logLevel(context: Context): String = prefs(context).getString("log_level", "info") ?: "info"

    fun setLogLevel(context: Context, level: String) {
        prefs(context).edit().putString("log_level", level).apply()
    }

    /** 前台应用详细模式：非 current 状态时也返回最近一次前台应用，并附带判定说明（默认关闭） */
    fun foregroundDetail(context: Context): Boolean = prefs(context).getBoolean("fg_detail", false)

    fun setForegroundDetail(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("fg_detail", on).apply()
    }

    // ==================== MCP 令牌 ====================

    /**
     * 当前令牌。首次使用时生成 256 bit 随机值。以下情况返回空字符串（所有请求鉴权失败），不会悄悄生成新令牌，
     * 也不会改用明文副本：密文无法解密；只有未迁移的明文且用户未同意兼容模式；Keystore 不可用且未同意兼容模式。
     */
    fun token(context: Context): String {
        cachedToken?.let { return it }
        synchronized(this) {
            cachedToken?.let { return it }
            return when (val r = secretStore(context).read(KEY_TOKEN)) {
                is SecretStore.Read.Value ->
                    if (r.value.length >= Secrets.MIN_SECRET_LENGTH) r.value.also { cachedToken = it }
                    else tryRegenerateLocked(context)
                SecretStore.Read.Missing -> tryRegenerateLocked(context)
                is SecretStore.Read.Unreadable, is SecretStore.Read.NeedsConsent -> ""
            }
        }
    }

    private fun tryRegenerateLocked(context: Context): String = try {
        regenerateTokenLocked(context)
    } catch (_: SecretStorageUnavailableException) {
        ""
    }

    /** 令牌不可用的原因（界面用）；可用时返回 null */
    fun tokenProblem(context: Context): String? {
        if (token(context).isNotEmpty()) return null
        return when (val r = secretStore(context).read(KEY_TOKEN)) {
            is SecretStore.Read.Unreadable -> r.reason
            is SecretStore.Read.NeedsConsent -> r.reason
            else -> secretStore(context).lastError ?: "令牌不可用"
        }
    }

    /** 生成新令牌（旧令牌立即失效）。凭据存储不可用时抛出 [SecretStorageUnavailableException] */
    fun regenerateToken(context: Context): String = synchronized(this) { regenerateTokenLocked(context) }

    private fun regenerateTokenLocked(context: Context): String {
        val t = Secrets.newToken()
        cachedToken = null
        secretStore(context).write(KEY_TOKEN, t)
        cachedToken = t
        return t
    }

    fun tokenStorageMode(context: Context): SecretStore.Mode? = secretStore(context).mode(KEY_TOKEN)

    // ==================== 安全中继（1.4.2：最多 3 个服务器） ====================
    //
    // 每个服务器占一个固定的位置 1..3。位置 1 沿用 1.4.x 的键（relay_enabled / relay_url / relay_pin / relay_secret），
    // 升级后原来的配置就是“服务器 1”，设备密钥不用迁移、不用重填；位置 2、3 用 relay2_* / relay3_* 和
    // relay_secret_2 / relay_secret_3。连接参数（超时、心跳、重连间隔）所有服务器共用。

    const val MAX_RELAYS = 3
    val RELAY_SLOTS = 1..MAX_RELAYS

    /** 一个服务器的配置（不含设备密钥） */
    data class RelaySlot(val slot: Int, val name: String, val url: String, val pin: String, val enabled: Boolean) {
        val host: String get() = try { java.net.URI(url).host ?: "" } catch (_: Exception) { "" }
    }

    private fun key(slot: Int, field: String) = if (slot == 1) "relay_$field" else "relay${slot}_$field"
    private fun secretKey(slot: Int) = if (slot == 1) KEY_RELAY_SECRET else "relay_secret_$slot"
    private val allSecretKeys: Array<String> get() = RELAY_SLOTS.map { secretKey(it) }.toTypedArray()

    fun defaultRelayName(slot: Int) = if (slot == 1) "我的服务器" else "服务器 $slot"

    /** 这个位置有没有服务器（位置 1：1.4.x 填过地址或密钥也算） */
    fun relaySlotExists(context: Context, slot: Int): Boolean {
        val p = prefs(context)
        if (p.getBoolean(key(slot, "added"), false)) return true
        return slot == 1 && (relayUrl(context, 1).isNotBlank() || secretStore(context).mode(secretKey(1)) != null)
    }

    fun relaySlots(context: Context): List<RelaySlot> = RELAY_SLOTS.filter { relaySlotExists(context, it) }.map {
        RelaySlot(it, relayName(context, it), relayUrl(context, it), relayPin(context, it), relayEnabled(context, it))
    }

    /** 还空着的最小位置；3 个都用了时为 null */
    fun freeRelaySlot(context: Context): Int? = RELAY_SLOTS.firstOrNull { !relaySlotExists(context, it) }

    fun relayName(context: Context, slot: Int): String =
        prefs(context).getString(key(slot, "name"), null)?.takeIf { it.isNotBlank() } ?: defaultRelayName(slot)

    fun relayEnabled(context: Context, slot: Int = 1): Boolean = prefs(context).getBoolean(key(slot, "enabled"), false)

    /** 至少开着一个服务器 */
    fun anyRelayEnabled(context: Context): Boolean = RELAY_SLOTS.any { relaySlotExists(context, it) && relayEnabled(context, it) }

    fun setRelayEnabled(context: Context, on: Boolean) = setRelayEnabled(context, 1, on)

    fun setRelayEnabled(context: Context, slot: Int, on: Boolean) {
        prefs(context).edit().putBoolean(key(slot, "enabled"), on).apply()
    }

    fun relayUrl(context: Context, slot: Int = 1): String = prefs(context).getString(key(slot, "url"), "") ?: ""

    /** 一个或多个证书指纹（换行或逗号分隔） */
    fun relayPin(context: Context, slot: Int = 1): String = prefs(context).getString(key(slot, "pin"), "") ?: ""

    fun relaySecret(context: Context, slot: Int = 1): String {
        cachedRelaySecrets[slot]?.let { return it }
        synchronized(this) {
            cachedRelaySecrets[slot]?.let { return it }
            return when (val r = secretStore(context).read(secretKey(slot))) {
                is SecretStore.Read.Value -> r.value.also { cachedRelaySecrets[slot] = it }
                else -> ""
            }
        }
    }

    /** 设备密钥不可用的原因；未保存或可用时返回 null */
    fun relaySecretProblem(context: Context, slot: Int = 1): String? = when (val r = secretStore(context).read(secretKey(slot))) {
        is SecretStore.Read.Unreadable -> r.reason
        is SecretStore.Read.NeedsConsent -> r.reason
        else -> null
    }

    fun relaySecretStorageMode(context: Context, slot: Int = 1): SecretStore.Mode? = secretStore(context).mode(secretKey(slot))

    /** 保存服务器 1 的中继配置（1.4.x 的接口） */
    fun saveRelay(context: Context, url: String, secret: String, pin: String) =
        saveRelay(context, 1, relayName(context, 1), url, secret, pin)

    /** 保存一个服务器的配置。凭据存储不可用时抛出 [SecretStorageUnavailableException]，此时什么都不保存 */
    fun saveRelay(context: Context, slot: Int, name: String, url: String, secret: String, pin: String) {
        require(slot in RELAY_SLOTS) { "slot $slot" }
        synchronized(this) {
            cachedRelaySecrets.remove(slot)
            if (secret.isBlank()) secretStore(context).delete(secretKey(slot))
            else secretStore(context).write(secretKey(slot), secret.trim())
        }
        prefs(context).edit()
            .putBoolean(key(slot, "added"), true)
            .putString(key(slot, "name"), name.trim().take(24))
            .putString(key(slot, "url"), url.trim())
            .putString(key(slot, "pin"), pin.trim())
            .apply()
    }

    /** 删除一个服务器：地址、名字、指纹、开关和设备密钥都删掉，位置空出来 */
    fun deleteRelay(context: Context, slot: Int) {
        prefs(context).edit().putBoolean(key(slot, "enabled"), false)
            .remove(key(slot, "added")).remove(key(slot, "name")).remove(key(slot, "url")).remove(key(slot, "pin"))
            .commit()
        synchronized(this) {
            secretStore(context).delete(secretKey(slot))
            cachedRelaySecrets.remove(slot)
        }
        Diagnostics.forgetRelay(context, slot)
    }

    /** 撤销远程凭据：所有服务器的设备密钥和证书指纹都删除并关闭（地址和名字保留，方便重新配置） */
    fun revokeRelayCredentials(context: Context) {
        val e = prefs(context).edit()
        for (s in RELAY_SLOTS) e.putBoolean(key(s, "enabled"), false).remove(key(s, "pin"))
        e.commit()
        synchronized(this) {
            for (s in RELAY_SLOTS) secretStore(context).delete(secretKey(s))
            cachedRelaySecrets.clear()
        }
    }

    /** 清除本地全部认证配置：所有服务器的中继配置全部删除，并生成新的 MCP 令牌（旧令牌立即失效） */
    fun clearAllCredentials(context: Context) {
        val e = prefs(context).edit()
        for (s in RELAY_SLOTS) {
            e.putBoolean(key(s, "enabled"), false).remove(key(s, "added")).remove(key(s, "name"))
                .remove(key(s, "url")).remove(key(s, "pin"))
        }
        e.commit()
        synchronized(this) {
            for (s in RELAY_SLOTS) secretStore(context).delete(secretKey(s))
            cachedRelaySecrets.clear()
            secretStore(context).delete(KEY_TOKEN)
            cachedToken = null
            tryRegenerateLocked(context)
        }
        for (s in RELAY_SLOTS) Diagnostics.forgetRelay(context, s)
    }

    /** 中继连接参数（高级） */
    fun relayConnectTimeoutSec(context: Context): Int = prefs(context).getInt("relay_connect_timeout", 15)
    fun relayClientPingSec(context: Context): Int = prefs(context).getInt("relay_client_ping", 60)
    fun relayMaxRetrySec(context: Context): Int = prefs(context).getInt("relay_max_retry", 60)

    fun setRelayTiming(context: Context, connectTimeoutSec: Int, clientPingSec: Int, maxRetrySec: Int) {
        prefs(context).edit()
            .putInt("relay_connect_timeout", connectTimeoutSec)
            .putInt("relay_client_ping", clientPingSec)
            .putInt("relay_max_retry", maxRetrySec)
            .apply()
    }

    fun relayConfig(context: Context, appVersion: String, slot: Int = 1): RelayConfig = RelayConfig(
        url = relayUrl(context, slot),
        deviceSecret = relaySecret(context, slot),
        pin = relayPin(context, slot).ifBlank { null },
        deviceName = "android",
        appVersion = appVersion,
        connectTimeoutSec = relayConnectTimeoutSec(context),
        clientPingIntervalSec = relayClientPingSec(context),
        maxRetrySec = relayMaxRetrySec(context),
    )

    /** 当前生效的全部密钥（供日志脱敏；读的是内存缓存） */
    fun secrets(context: Context): List<String> =
        (listOfNotNull(cachedToken) + cachedRelaySecrets.values).filter { it.isNotEmpty() }

    internal fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 仅测试用：清空内存缓存 */
    internal fun resetForTest() {
        cipherFactory = { KeystoreCipher.createOrNull() }
        store = null
        cachedToken = null
        cachedRelaySecrets.clear()
    }
}

/** 全局 Application 上下文引用（Application.onCreate 时设置） */
object McpAppCtx {
    @Volatile
    lateinit var app: Context
}
