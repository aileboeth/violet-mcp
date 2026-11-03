package com.mcp.server

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mcp.server.core.ToolDefinition
import com.mcp.server.server.Settings
import com.mcp.server.tools.LifeTools
import com.mcp.server.tools.PlaceStore
import com.mcp.server.tools.ToolKit
import com.mcp.server.tools.ToolKit.Requirement
import com.mcp.server.tools.UsageTools
import com.mcp.server.ui.theme.McpTheme
import org.json.JSONArray

class ToolsActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) resumeTick.intValue++
        })
        setContent {
            McpTheme {
                ToolsScreen(onBack = { finish() }, resumeTick = resumeTick.intValue)
            }
        }
    }
}

/** 这项权限现在有没有 */
internal fun requirementMet(context: Context, r: Requirement): Boolean = when (r) {
    Requirement.NONE -> true
    Requirement.USAGE_ACCESS, Requirement.USAGE_ACCESS_OPTIONAL -> UsageTools.hasUsageAccess(context)
    Requirement.NOTIFICATION_ACCESS -> LifeTools.hasNotificationAccess(context)
    Requirement.ACTIVITY -> Build.VERSION.SDK_INT < 29 ||
        context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    Requirement.CALENDAR ->
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    Requirement.LOCATION_OPTIONAL ->
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

/** 运行时权限（弹系统对话框）；其余的跳到系统设置页 */
private fun runtimePermissions(r: Requirement): Array<String>? = when (r) {
    Requirement.ACTIVITY -> if (Build.VERSION.SDK_INT >= 29) arrayOf(Manifest.permission.ACTIVITY_RECOGNITION) else null
    Requirement.CALENDAR -> arrayOf(Manifest.permission.READ_CALENDAR)
    Requirement.LOCATION_OPTIONAL -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    else -> null
}

private fun openNotificationAccessSettings(context: Context) {
    try {
        context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(onBack: () -> Unit = {}, resumeTick: Int = 0) {
    val context = LocalContext.current
    // 切换开关或从系统设置返回后刷新
    var refresh by remember { mutableIntStateOf(0) }
    val groups = remember(refresh) { ToolKit.groupedTools() }
    val tick = refresh + resumeTick
    var fgDetail by remember { mutableStateOf(Settings.foregroundDetail(context)) }
    var selected by remember { mutableStateOf<ToolDefinition?>(null) }
    var confirmSensitive by remember { mutableStateOf<ToolDefinition?>(null) }
    var namingWifi by remember { mutableStateOf<String?>(null) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    fun grant(r: Requirement) {
        val perms = runtimePermissions(r)
        when {
            perms != null -> askPermission.launch(perms)
            r == Requirement.NOTIFICATION_ACCESS -> openNotificationAccessSettings(context)
            else -> openUsageAccessSettings(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工具与权限", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { ToolKit.resetToDefaults(); refresh++ }) { Text("恢复默认") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "notice") {
                Text(
                    "打开哪一项，Violet 就能知道哪一项；关着的她看不到，问了也会被拒绝。" +
                        "所有项目都只能“看”，不能改你的手机。标“敏感”的会透露比较多的隐私，默认关着。" +
                        "这些开关只能在手机上改。点一行可以看详细说明。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            groups.forEach { (category, tools) ->
                item(key = "header_$category") {
                    Text(
                        category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(tools, key = { it.name }) { tool ->
                    val req = ToolKit.requirement(tool.name)
                    val met = remember(tick, tool.name) { requirementMet(context, req) }
                    ToolRow(
                        tool = tool,
                        enabled = ToolKit.isEnabled(tool.name),
                        sensitive = ToolKit.isSensitive(tool.name),
                        requirement = req,
                        granted = met,
                        onToggle = {
                            val on = ToolKit.isEnabled(tool.name)
                            if (!on && ToolKit.isSensitive(tool.name)) {
                                confirmSensitive = tool
                            } else {
                                ToolKit.setEnabled(tool.name, !on)
                                refresh++
                            }
                        },
                        onInfo = { selected = tool },
                        onGrant = { grant(req) },
                    )
                    if (tool.name == "network_state" && ToolKit.isEnabled(tool.name)) {
                        WifiPlaceRow(tick = tick, onName = { namingWifi = it })
                    }
                }
                if (category == "手机感知") {
                    item(key = "fg_detail") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("正在用的 App：多告诉她一点", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (fgDetail) "开着：熄屏、锁屏或看不准的时候，也告诉她你最后用的是哪个 App"
                                    else "关着：只有确定你正在用某个 App 时才告诉她是哪个",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = fgDetail, onCheckedChange = {
                                fgDetail = it
                                Settings.setForegroundDetail(context, it)
                            })
                        }
                    }
                }
            }
        }
    }

    selected?.let { tool -> ToolDetailDialog(tool = tool, onDismiss = { selected = null }) }

    namingWifi?.let { key ->
        var text by remember(key) { mutableStateOf(PlaceStore.label(context, key) ?: "") }
        AlertDialog(
            onDismissRequest = { namingWifi = null },
            title = { Text("给现在的 Wi-Fi 起个名字") },
            text = {
                Column {
                    Text("比如“家”“学校”“公司”。以后连上这个 Wi-Fi，Violet 只会知道你在“${text.ifBlank { "…" }}”，" +
                        "不会知道 Wi-Fi 的名字。留空就是不再认这个 Wi-Fi。",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(value = text, onValueChange = { text = it.take(20) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { PlaceStore.set(context, key, text); namingWifi = null; refresh++ }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { namingWifi = null }) { Text("取消") } },
        )
    }

    confirmSensitive?.let { tool ->
        val plain = ToolKit.plain(tool.name)
        AlertDialog(
            onDismissRequest = { confirmSensitive = null },
            title = { Text("打开“${plain.title}”？") },
            text = { Text("${plain.text}\n这项比较私密，只在需要时打开，随时可以关掉。") },
            confirmButton = {
                TextButton(onClick = {
                    ToolKit.setEnabled(tool.name, true)
                    confirmSensitive = null
                    refresh++
                }) { Text("打开") }
            },
            dismissButton = { TextButton(onClick = { confirmSensitive = null }) { Text("取消") } },
        )
    }
}

/** network_state 下面：现在连着的 Wi-Fi 叫什么（你起的名字），可以起名 / 改名 */
@Composable
private fun WifiPlaceRow(tick: Int, onName: (String) -> Unit) {
    val context = LocalContext.current
    val key = remember(tick) { LifeTools.currentWifiKey(context) }
    val label = key?.let { PlaceStore.label(context, it) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                key == null -> "要认出你在哪个 Wi-Fi，先给“位置”权限并连上 Wi-Fi"
                label == null -> "现在连着的 Wi-Fi 还没有名字"
                else -> "现在连着的 Wi-Fi：$label"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (key != null) TextButton(onClick = { onName(key) }) { Text(if (label == null) "起名" else "改名") }
    }
}

@Composable
private fun ToolRow(
    tool: ToolDefinition,
    enabled: Boolean,
    sensitive: Boolean,
    requirement: Requirement,
    granted: Boolean,
    onToggle: () -> Unit,
    onInfo: () -> Unit,
    onGrant: () -> Unit,
) {
    val plain = ToolKit.plain(tool.name)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onInfo)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        plain.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sensitive) {
                        Spacer(Modifier.width(8.dp))
                        Text("敏感", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    plain.text.ifEmpty { tool.description },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
                if (requirement != Requirement.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            requirement.label + if (granted) "（已给）" else "（还没给）",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!granted && requirement.required) MaterialTheme.colorScheme.error
                            else if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (!granted) TextButton(onClick = onGrant) { Text("去授权") }
                    }
                    if (!granted && enabled && requirement.required) {
                        Text("没给这个权限时，她问了只会得到“没有权限”", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = onToggle) {
                Icon(
                    if (enabled) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (enabled) "关掉：${plain.title}" else "打开：${plain.title}",
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolDetailDialog(tool: ToolDefinition, onDismiss: () -> Unit) {
    val plain = ToolKit.plain(tool.name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(plain.title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(plain.text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(6.dp))
                Text("分类：${tool.category} · 工具名：${tool.name}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.padding(6.dp))
                Text("给 AI 看的说明", style = MaterialTheme.typography.titleSmall)
                Text(tool.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(6.dp))
                Text("参数", style = MaterialTheme.typography.titleSmall)
                val props = tool.inputSchema.optJSONObject("properties")
                val required = tool.inputSchema.optJSONArray("required")
                if (props != null && props.length() > 0) {
                    props.keys().forEach { key ->
                        val p = props.optJSONObject(key) ?: return@forEach
                        val req = contains(required, key)
                        Text("• $key (${p.optString("type", "any")})${if (req) " 必填" else ""}",
                            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    Text("无参数", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

private fun contains(arr: JSONArray?, key: String): Boolean {
    if (arr == null) return false
    for (i in 0 until arr.length()) {
        if (arr.optString(i) == key) return true
    }
    return false
}
