package com.mcp.server

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcp.server.core.RelayClient
import com.mcp.server.server.McpServerService
import com.mcp.server.server.SecretStorageUnavailableException
import com.mcp.server.server.Settings
import kotlinx.coroutines.delay

/**
 * 1.4.2（用户：mcp 可以同时向多个服务器提供中继服务，上限 3，UI 要好看、不要太突兀）：
 * 设置页的“远程访问”卡片。每个服务器一张圆角小卡片（状态圆点、名字、地址 · 状态、开关），点开就地展开编辑；
 * 底部“添加服务器”。连接参数（超时 / 心跳 / 重连）所有服务器共用，收在最下面。
 */
@Composable
internal fun RelayServersCard(initiallyExpanded: Int? = null) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val servers = remember(tick) { Settings.relaySlots(context) }
    var states by remember { mutableStateOf(McpServerService.relayStates()) }
    var running by remember { mutableStateOf(McpServerService.running) }
    LaunchedEffect(Unit) {
        while (true) {
            states = McpServerService.relayStates()
            running = McpServerService.running
            delay(1000)
        }
    }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    /** 正在添加、还没保存的服务器的位置 */
    var draft by remember { mutableStateOf<Int?>(null) }

    val on = servers.count { it.enabled }
    val connected = servers.count { it.enabled && states[it.slot]?.phase == RelayClient.Phase.CONNECTED }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("远程访问（TLS 中继）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (on > 0) {
                    val all = connected == on && running
                    Text(
                        "已连 $connected / $on",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (all) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (all) Color(0x1A2E7D32) else MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                "手机主动连接服务器上的中继，那台服务器上的 Violet 就能用手机的工具。最多 ${Settings.MAX_RELAYS} 个服务器，" +
                    "每个单独开关；每多开一个，手机就多一路心跳。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (srv in servers) {
                    RelayServerItem(
                        srv = srv,
                        state = states[srv.slot],
                        running = running,
                        others = servers.filter { it.slot != srv.slot },
                        expanded = expanded == srv.slot,
                        isNew = false,
                        onToggleExpand = { expanded = if (expanded == srv.slot) null else srv.slot },
                        onChanged = { tick++ },
                        onClose = { expanded = null; tick++ },
                    )
                }
                draft?.let { slot ->
                    RelayServerItem(
                        srv = Settings.RelaySlot(slot, Settings.defaultRelayName(slot), "", "", false),
                        state = null,
                        running = running,
                        others = servers,
                        expanded = true,
                        isNew = true,
                        onToggleExpand = {},
                        onChanged = { draft = null; expanded = null; tick++ },
                        onClose = { draft = null; expanded = null },
                    )
                }
            }

            if (draft == null && servers.size < Settings.MAX_RELAYS) {
                Spacer(Modifier.height(if (servers.isEmpty()) 0.dp else 10.dp))
                OutlinedButton(
                    onClick = {
                        draft = Settings.freeRelaySlot(context)
                        expanded = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (servers.isEmpty()) "添加服务器" else "添加服务器（还能加 ${Settings.MAX_RELAYS - servers.size} 个）")
                }
            }

            Spacer(Modifier.height(4.dp))
            ConnectionParams()
        }
    }
}

@Composable
private fun RelayServerItem(
    srv: Settings.RelaySlot,
    state: RelayClient.RelayState?,
    running: Boolean,
    others: List<Settings.RelaySlot>,
    expanded: Boolean,
    isNew: Boolean,
    onToggleExpand: () -> Unit,
    onChanged: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember(srv.slot, isNew) { mutableStateOf(if (isNew) "" else srv.name) }
    var url by remember(srv.slot, isNew) { mutableStateOf(srv.url) }
    var secret by remember(srv.slot, isNew) { mutableStateOf(if (isNew) "" else Settings.relaySecret(context, srv.slot)) }
    var pin by remember(srv.slot, isNew) { mutableStateOf(srv.pin) }
    var showSecret by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val (statusText, statusColor) = if (isNew) "还没保存" to MaterialTheme.colorScheme.onSurfaceVariant
    else relayText(srv, state, running)
    val border by animateColorAsState(
        if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
        label = "border",
    )
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isNew, onClick = onToggleExpand)
                    .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isNew) "新服务器" else srv.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // “已连接 relay.x.com”本身带地址；其余是“relay.x.com · 已关闭”
                        if (srv.host.isBlank() || statusText.contains(srv.host)) statusText else "${srv.host} · $statusText",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!isNew) {
                    Switch(
                        checked = srv.enabled,
                        onCheckedChange = { want ->
                            if (want) {
                                val problem = Settings.relaySecretProblem(context, srv.slot)
                                    ?: RelayClient.validate(Settings.relayConfig(context, BuildConfig.VERSION_NAME, srv.slot))
                                if (problem != null) {
                                    error = "先把配置填完整再打开：$problem"
                                    if (!expanded) onToggleExpand()
                                    return@Switch
                                }
                            }
                            Settings.setRelayEnabled(context, srv.slot, want)
                            McpServerService.notifyIfRunning(context, McpServerService.ACTION_APPLY)
                            onChanged()
                        },
                    )
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp).size(22.dp).rotate(arrow),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(24); error = null },
                        label = { Text("名字（只在手机上显示）") },
                        placeholder = { Text(Settings.defaultRelayName(srv.slot)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; error = null },
                        label = { Text("中继地址") },
                        placeholder = { Text("wss://relay.example.com:8443/device") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it; error = null },
                        label = { Text("设备密钥（中继的 device.secret）") },
                        singleLine = true,
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(if (showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showSecret) "隐藏" else "显示")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it; error = null },
                        label = { Text("证书指纹（自签证书必填，可填 2 个）") },
                        placeholder = { Text("sha256/…") },
                        minLines = 1,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isNew) {
                            TextButton(onClick = onClose) { Text("取消") }
                        } else {
                            TextButton(onClick = { confirmDelete = true }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        FilledTonalButton(onClick = {
                            val cfg = Settings.relayConfig(context, BuildConfig.VERSION_NAME, srv.slot).copy(
                                url = url.trim(), deviceSecret = secret.trim(), pin = pin.trim().ifBlank { null })
                            val same = others.firstOrNull { it.url.trim().equals(url.trim(), ignoreCase = true) }
                            val problem = RelayClient.validate(cfg)
                                ?: same?.let { "这个地址已经是“${it.name}”了，同一个中继不用加两次" }
                            if (problem != null) {
                                error = problem
                                return@FilledTonalButton
                            }
                            try {
                                Settings.saveRelay(context, srv.slot, name.ifBlank { Settings.defaultRelayName(srv.slot) },
                                    url, secret, pin)
                            } catch (e: SecretStorageUnavailableException) {
                                error = "无法安全保存设备密钥：${e.message}。可在“安全”页了解兼容模式。"
                                return@FilledTonalButton
                            }
                            if (isNew) Settings.setRelayEnabled(context, srv.slot, true)   // 新加的保存后直接开始连接
                            McpServerService.notifyIfRunning(context, McpServerService.ACTION_APPLY)
                            toast(context, if (McpServerService.running) "已保存，正在连接" else "已保存，启动服务后连接")
                            onChanged()
                        }) { Text(if (isNew) "保存并连接" else "保存并应用") }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除“${srv.name}”？") },
            text = {
                Text("手机上这个服务器的地址、设备密钥和证书指纹都会删除，那台服务器上的 Violet 将连不上手机。" +
                    "其他服务器不受影响。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Settings.deleteRelay(context, srv.slot)
                    McpServerService.notifyIfRunning(context, McpServerService.ACTION_APPLY)
                    toast(context, "已删除“${srv.name}”")
                    onClose()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

/** 所有服务器共用的连接参数，默认收起 */
@Composable
private fun ConnectionParams() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    var connectTimeout by remember { mutableStateOf(Settings.relayConnectTimeoutSec(context).toString()) }
    var clientPing by remember { mutableStateOf(Settings.relayClientPingSec(context).toString()) }
    var maxRetry by remember { mutableStateOf(Settings.relayMaxRetrySec(context).toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    TextButton(onClick = { show = !show }) { Text(if (show) "收起连接参数" else "连接参数（高级，所有服务器共用）") }
    AnimatedVisibility(visible = show, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
            NumberField("连接超时（秒，5–60）", connectTimeout) { connectTimeout = it; error = null }
            NumberField("手机侧心跳（秒，0 关闭，15–600）", clientPing) { clientPing = it; error = null }
            NumberField("最长重连间隔（秒，10–3600）", maxRetry) { maxRetry = it; error = null }
            Text(
                "主要心跳由中继发出（中继的 -ping-interval，默认 30 秒）。手机侧心跳只是兜底，间隔越短越耗电。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = {
                val c = Settings.relayConfig(context, BuildConfig.VERSION_NAME).copy(
                    // 只检查连接参数：地址和密钥用占位值
                    url = "wss://check.invalid/device", deviceSecret = "x".repeat(128), pin = null,
                    connectTimeoutSec = connectTimeout.toIntOrNull() ?: -1,
                    clientPingIntervalSec = clientPing.toIntOrNull() ?: -1,
                    maxRetrySec = maxRetry.toIntOrNull() ?: -1,
                )
                val problem = RelayClient.validate(c) ?: if (c.maxRetrySec !in 10..3600) "最长重连间隔应在 10–3600 秒" else null
                if (problem != null) {
                    error = problem
                    return@TextButton
                }
                Settings.setRelayTiming(context, c.connectTimeoutSec, c.clientPingIntervalSec, c.maxRetrySec)
                McpServerService.notifyIfRunning(context, McpServerService.ACTION_APPLY)
                toast(context, "连接参数已保存")
            }) { Text("保存连接参数") }
        }
    }
    Text(
        "正式证书（如 Let's Encrypt）可不填指纹；中继自动生成的自签证书要填中继启动日志里的 certificate pin。" +
            "更换证书时可先把新证书的指纹作为第二行加进来，换完后再删掉旧的。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
