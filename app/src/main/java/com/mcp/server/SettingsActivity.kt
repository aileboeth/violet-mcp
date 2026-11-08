package com.mcp.server

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mcp.server.core.RelayClient
import com.mcp.server.server.AppUpdater
import com.mcp.server.server.LogStore
import com.mcp.server.server.McpServerService
import com.mcp.server.server.Settings
import com.mcp.server.server.WakeLockHelper
import com.mcp.server.tools.UsageTools
import com.mcp.server.ui.theme.McpTheme
import com.mcp.server.update.Sources
import kotlinx.coroutines.delay

class SettingsActivity : ComponentActivity() {

    /** 权限状态 tick：+1 触发设置页重新计算权限状态 */
    private val permissionTick = mutableIntStateOf(0)

    private val batteryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { permissionTick.intValue++ }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionTick.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 从系统设置页（使用情况访问）返回时刷新
        lifecycle.addObserver(LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) permissionTick.intValue++
        })
        setContent {
            McpTheme {
                SettingsScreen(
                    onBack = { finish() },
                    permissionTick = permissionTick.intValue,
                    // 只有用户点了“申请”才会弹出系统确认（针对本应用，不要求关闭全局省电）
                    onRequestBattery = {
                        try {
                            batteryLauncher.launch(Intent(
                                AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName")))
                        } catch (_: Exception) {
                            batteryLauncher.launch(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    permissionTick: Int = 0,
    onRequestBattery: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current

    var wakeLock by remember { mutableStateOf(Settings.wakeLockEnabled(context)) }
    var autoStart by remember { mutableStateOf(Settings.autoStart(context)) }
    var logLevel by remember { mutableStateOf(Settings.logLevel(context)) }

    val hasUsage = remember(permissionTick) { UsageTools.hasUsageAccess(context) }
    val hasBattery = remember(permissionTick) { isIgnoringBatteryOptimizations(context) }
    val hasNotifications = remember(permissionTick) {
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ===== 远程访问（TLS 中继，1.5.0：最多 3 个服务器） =====
            RelayServersCard()

            // ===== 应用更新（1.4.0） =====
            UpdateCard(canInstall = remember(permissionTick) { AppUpdater.canInstall(context) })

            // ===== 后台运行 =====
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("后台运行", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    ToggleRow(
                        icon = Icons.Outlined.Power,
                        title = "开机自启",
                        subtitle = "开机（首次解锁后）和应用更新后恢复运行；你手动停止过则不会自动启动",
                        checked = autoStart,
                        onCheckedChange = {
                            autoStart = it
                            Settings.setAutoStart(context, it)
                        },
                    )
                    ToggleRow(
                        icon = Icons.Outlined.Circle,
                        title = "常驻 WakeLock",
                        subtitle = "一直不让 CPU 休眠，耗电明显，一般不需要开（处理每个请求时会自动短暂保持唤醒）",
                        checked = wakeLock,
                        onCheckedChange = {
                            wakeLock = it
                            Settings.setWakeLockEnabled(context, it)
                            if (it && McpServerService.running) WakeLockHelper.acquire(context)
                            if (!it) WakeLockHelper.release()
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "安卓系统和各厂商都可能限制后台应用，无法保证永远在线。建议允许“电池无限制”，部分品牌还需在手机管家里允许自启动。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ===== 日志 =====
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("日志详细程度", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((value, label) in listOf("warn" to "只记问题", "info" to "标准", "debug" to "详细（含每次请求耗时）")) {
                            FilterChip(selected = logLevel == value, onClick = {
                                logLevel = value
                                Settings.setLogLevel(context, value)
                                LogStore.setLevel(value)
                            }, label = { Text(label) })
                        }
                    }
                    Text("任何级别都不会记录令牌、请求内容或工具返回的数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ===== 权限 =====
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("权限", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    PermissionRow("使用情况访问权限", "前台应用、使用时长需要；不授权时这两个工具会返回“权限不足”", hasUsage, "去授权") {
                        openUsageAccessSettings(context)
                    }
                    PermissionRow("电池无限制", "可选：减少熄屏后被系统延迟或断开", hasBattery, "申请", onRequestBattery)
                    PermissionRow("通知", "可选：显示服务运行通知", hasNotifications, "申请", onRequestNotifications)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "本应用不需要 Root、无障碍、存储、悬浮窗、读取全部应用列表或修改系统设置等权限。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun UpdateCard(canInstall: Boolean) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(AppUpdater.mode(context)) }
    var sourceChoice by remember { mutableStateOf(AppUpdater.sourceChoice(context)) }
    var mirror by remember { mutableStateOf(AppUpdater.customMirror(context)) }
    var mirrorOk by remember { mutableStateOf(true) }
    var upd by remember { mutableStateOf(AppUpdater.state) }
    LaunchedEffect(Unit) {
        AppUpdater.restore(context)
        while (true) {
            upd = AppUpdater.state
            delay(500)
        }
    }
    val busy = upd.phase in setOf(AppUpdater.Phase.CHECKING, AppUpdater.Phase.DOWNLOADING, AppUpdater.Phase.INSTALLING)
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("应用更新", style = MaterialTheme.typography.titleSmall)
            Text(
                "当前版本 ${BuildConfig.VERSION_NAME}" +
                    (upd.latest?.let { "，最新发布 $it" } ?: "") +
                    (if (upd.checkedAt > 0) "（${formatClock(upd.checkedAt)} 检查）" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in listOf(AppUpdater.MODE_AUTO to "自动更新", AppUpdater.MODE_NOTIFY to "只提醒",
                    AppUpdater.MODE_MANUAL to "手动检查")) {
                    FilterChip(selected = mode == value, onClick = {
                        mode = value
                        AppUpdater.setMode(context, value)
                    }, label = { Text(label) })
                }
            }
            Text(
                when (mode) {
                    AppUpdater.MODE_AUTO -> "每 6 小时检查一次，有新版本就下载、校验，然后弹出系统的安装确认（通知栏提示）。"
                    AppUpdater.MODE_NOTIFY -> "每 6 小时检查一次，有新版本只发通知，在这里点“下载并安装”。"
                    else -> "只在你点“检查更新”时检查。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 1.4.4 (user 2026-11-08): phones in mainland China often cannot reach GitHub -> a GitHub mirror
            Spacer(Modifier.height(10.dp))
            Text("下载来源", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())) {
                for ((value, label) in Sources.CHOICES) {
                    FilterChip(selected = sourceChoice == value, onClick = {
                        sourceChoice = value
                        AppUpdater.setSource(context, value)
                    }, label = { Text(label) })
                }
            }
            if (sourceChoice == Sources.CUSTOM) {
                OutlinedTextField(
                    value = mirror,
                    onValueChange = { v ->
                        mirror = v.trim().take(300)
                        mirrorOk = mirror.isEmpty() || Sources.customChecked(mirror) != null
                        if (mirrorOk) AppUpdater.setSource(context, Sources.CUSTOM, mirror)
                    },
                    label = { Text("加速地址（https://…/）") },
                    singleLine = true,
                    isError = !mirrorOk,
                    supportingText = {
                        Text(if (mirrorOk) "放在 GitHub 地址前面的一段，例如 https://gh.example.com/"
                        else "要以 https:// 开头，不能是 github.com 本身，不能带用户名或 ? 参数")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                when (sourceChoice) {
                    Sources.AUTO -> "先连 GitHub，连不上再试加速站 p.jackyu.cn。"
                    Sources.GITHUB -> "只从 GitHub 下载。"
                    Sources.JACKYU -> "只从加速站 p.jackyu.cn 下载（别人提供的免费 GitHub 加速服务）。"
                    else -> "只从你填的加速地址下载；地址不对时按“自动”处理。"
                } + (upd.source?.let { " 上次检查来自：$it。" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            upd.message?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it + if (upd.phase == AppUpdater.Phase.DOWNLOADING && upd.progress >= 0) "（${upd.progress}%）" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (upd.phase == AppUpdater.Phase.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = { AppUpdater.checkNow(context) }) { Text("检查更新") }
                if (upd.newer) {
                    TextButton(enabled = !busy, onClick = { AppUpdater.installNow(context) }) { Text("下载并安装") }
                }
                if (upd.pageUrl != null) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(upd.pageUrl)))
                    }) { Text("更新说明") }
                }
            }
            if (!canInstall) {
                PermissionRow("允许安装更新", "系统要求为本应用单独打开“安装未知应用”，才能安装新版本", false, "去允许") {
                    context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")))
                }
            }
            Text(
                "从 GitHub（aileboeth/violet-mcp）或你选的加速站下载；安装前核对 SHA-256、包名、版本号和签名证书，" +
                    "任何一项不符都不安装，所以加速站改不了安装包。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatClock(ms: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))

@Composable
internal fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionText: String,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) {
            Text("已授权", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelLarge)
        } else {
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean = try {
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
} catch (_: Exception) {
    false
}
