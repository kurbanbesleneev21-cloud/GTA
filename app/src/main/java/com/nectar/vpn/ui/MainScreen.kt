package com.nectar.vpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nectar.vpn.BuildConfig
import com.nectar.vpn.core.XrayCore
import com.nectar.vpn.data.AppData
import com.nectar.vpn.data.Server
import com.nectar.vpn.data.Subscription
import com.nectar.vpn.vpn.Phase
import com.nectar.vpn.vpn.VpnStatus
import kotlinx.coroutines.delay

@Composable
fun MainScreen(vm: MainViewModel, onConnect: () -> Unit) {
    val data by vm.data.collectAsState()
    val status by vm.vpn.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val pinging by vm.pinging.collectAsState()
    val collapsed by vm.collapsed.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current

    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.consumeMessage()
        }
    }
    LaunchedEffect(status.error) {
        status.error?.let { Toast.makeText(context, "Ошибка: $it", Toast.LENGTH_LONG).show() }
    }

    val selected = data.subscriptions.flatMap { it.servers }.let { all ->
        all.firstOrNull { it.id == data.selectedServerId } ?: all.firstOrNull()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Mono.Black)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item { TopBar(onAdd = { showAdd = true }, onSettings = { showSettings = true }) }
            item {
                Hero(
                    status = status,
                    onToggle = {
                        when (status.phase) {
                            Phase.CONNECTED, Phase.CONNECTING -> vm.disconnect()
                            Phase.STOPPED -> if (selected == null) showAdd = true else onConnect()
                            Phase.STOPPING -> {}
                        }
                    },
                )
            }
            item { CurrentServer(selected, status) }

            if (data.subscriptions.isEmpty()) {
                item { EmptyState(onAdd = { showAdd = true }) }
            } else {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 28.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "ЛОКАЦИИ",
                            color = Mono.Muted,
                            fontSize = 12.sp,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.pingAll() }) {
                            Text("Пинг", color = Mono.White, fontSize = 13.sp)
                        }
                        val allCollapsed = collapsed.containsAll(data.subscriptions.map { it.id })
                        TextButton(onClick = { vm.collapseAll() }) {
                            Text(if (allCollapsed) "Развернуть" else "Свернуть", color = Mono.White, fontSize = 13.sp)
                        }
                    }
                }
                data.subscriptions.forEach { sub ->
                    val isCollapsed = sub.id in collapsed
                    item(key = "h-${sub.id}") {
                        SubscriptionHeader(
                            sub = sub,
                            collapsed = isCollapsed,
                            refreshing = sub.id in refreshing,
                            onToggle = { vm.toggleCollapsed(sub) },
                            onRefresh = { vm.refresh(sub) },
                            onPing = { vm.pingSubscription(sub) },
                            onCopy = { copy(context, sub.url) },
                            onDelete = { vm.delete(sub) },
                        )
                    }
                    if (!isCollapsed) {
                        items(sub.servers, key = { "s-${it.id}" }) { server ->
                            ServerRow(
                                server = server,
                                selected = server.id == selected?.id,
                                active = server.id == status.serverId && status.phase == Phase.CONNECTED,
                                pinging = server.id in pinging,
                                manual = sub.isManual,
                                onClick = { vm.select(server) },
                                onPing = { vm.ping(server) },
                                onDelete = { vm.deleteServer(server) },
                            )
                        }
                        item(key = "f-${sub.id}") {
                            Box(
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                                    .background(Mono.Surface)
                            )
                        }
                    }
                    item(key = "sp-${sub.id}") { Spacer(Modifier.height(14.dp)) }
                }
            }
        }
    }

    if (showAdd) {
        AddDialog(
            onDismiss = { showAdd = false },
            onAdd = { vm.addInput(it); showAdd = false },
        )
    }
    if (showSettings) {
        SettingsDialog(
            data = data,
            onDismiss = { showSettings = false },
            onChange = { vm.updateSettings(it) },
            onRefreshAll = { vm.refreshAll(); showSettings = false },
        )
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("Nectar", text))
    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}

// ---------------------------------------------------------------- top bar

@Composable
private fun TopBar(onAdd: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "NECTAR",
                color = Mono.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
            )
            Text("V P N", color = Mono.Muted, fontSize = 10.sp, letterSpacing = 4.sp)
        }
        RoundIcon(Icons.Default.Add, "Добавить", onAdd)
        Spacer(Modifier.width(4.dp))
        RoundIcon(Icons.Default.Settings, "Настройки", onSettings)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(4.dp)
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, Mono.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Mono.White, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------- hero / power button

@Composable
private fun Hero(status: VpnStatus, onToggle: () -> Unit) {
    val phase = status.phase
    val on = phase == Phase.CONNECTED
    val busy = phase == Phase.CONNECTING || phase == Phase.STOPPING

    val transition = rememberInfiniteTransition(label = "hero")
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val spin by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val fill by animateColorAsState(if (on) Mono.White else Mono.Black, tween(450), label = "fill")
    val glyph by animateColorAsState(if (on) Mono.Black else Mono.White, tween(450), label = "glyph")

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(on) {
        while (on) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val base = size.minDimension / 2f
                // static guide rings with tick marks
                drawCircle(Mono.Line, radius = base - 2.dp.toPx(), style = Stroke(1.dp.toPx()))
                val ticks = 60
                for (i in 0 until ticks) {
                    val a = Math.toRadians(i * 360.0 / ticks)
                    val r1 = base - 10.dp.toPx()
                    val r2 = base - (if (i % 5 == 0) 18.dp else 14.dp).toPx()
                    val col = if (on) Mono.Soft else Mono.LineStrong
                    drawLine(
                        col,
                        Offset(c.x + (r1 * Math.cos(a)).toFloat(), c.y + (r1 * Math.sin(a)).toFloat()),
                        Offset(c.x + (r2 * Math.cos(a)).toFloat(), c.y + (r2 * Math.sin(a)).toFloat()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                // radar pulse when connected
                if (on) {
                    for (k in 0..1) {
                        val p = (pulse + k * 0.5f) % 1f
                        val r = 82.dp.toPx() + p * (base - 30.dp.toPx() - 82.dp.toPx())
                        drawCircle(Color.White.copy(alpha = (1f - p) * 0.35f), radius = r, style = Stroke(1.5.dp.toPx()))
                    }
                }
                // spinner while connecting
                if (busy) {
                    val r = 96.dp.toPx()
                    drawArc(
                        Mono.White,
                        startAngle = spin,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            // the button itself
            Box(
                Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .border(BorderStroke(2.dp, Mono.White), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(54.dp)) {
                    val w = 5.dp.toPx()
                    val r = size.minDimension / 2f - w
                    drawArc(
                        glyph, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                        style = Stroke(w, cap = StrokeCap.Round),
                    )
                    drawLine(glyph, Offset(center.x, 0f + w / 2), Offset(center.x, center.y), w, StrokeCap.Round)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            when (phase) {
                Phase.CONNECTED -> "ЗАЩИЩЕНО"
                Phase.CONNECTING -> "ПОДКЛЮЧЕНИЕ"
                Phase.STOPPING -> "ОТКЛЮЧЕНИЕ"
                Phase.STOPPED -> "НЕ ЗАЩИЩЕНО"
            },
            color = Mono.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 5.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (on) Format.duration(now - status.since) else "00:00:00",
            color = if (on) Mono.White else Mono.LineStrong,
            fontSize = 34.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
        )
    }
}

// ---------------------------------------------------------------- current server card

@Composable
private fun CurrentServer(server: Server?, status: VpnStatus) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Mono.Line, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(server?.name ?: "", filled = status.phase == Phase.CONNECTED, size = 46)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("ТЕКУЩАЯ ЛОКАЦИЯ", color = Mono.Muted, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                server?.let { Format.cleanName(it.name) } ?: "Не выбрана",
                color = Mono.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (server != null) {
                Text(server.label, color = Mono.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
        if (server != null) PingBadge(server.ping, pinging = false)
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Mono.Line, RoundedCornerShape(18.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Нет серверов", color = Mono.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Добавьте ссылку на подписку Nectar VPN или ключ vless:// / hysteria2://",
            color = Mono.Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Добавить подписку", onAdd)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Mono.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Mono.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------- subscription

@Composable
private fun SubscriptionHeader(
    sub: Subscription,
    collapsed: Boolean,
    refreshing: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = if (collapsed) RoundedCornerShape(18.dp) else RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Mono.Surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                null, tint = Mono.Soft,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    sub.title.ifBlank { "Подписка" }.uppercase(),
                    color = Mono.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val sub2 = if (sub.isManual) "${sub.servers.size} ключ(ей)"
                else buildString {
                    append(if (sub.updatedAt > 0) "Обновлено ${Format.dateTime(sub.updatedAt)}" else "Не обновлялась")
                    append(" · каждые ${Format.hoursLabel(sub.updateHours)}")
                }
                Text(sub2, color = Mono.Muted, fontSize = 11.sp)
            }
            if (!sub.isManual) {
                if (refreshing) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Mono.White, strokeWidth = 2.dp)
                    }
                } else {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Обновить", tint = Mono.White) }
                }
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Меню", tint = Mono.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Mono.Raised) {
                    DropdownMenuItem(text = { Text("Проверить пинг") }, onClick = { menu = false; onPing() })
                    if (!sub.isManual) {
                        DropdownMenuItem(text = { Text("Копировать ссылку") }, onClick = { menu = false; onCopy() })
                    }
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                }
            }
        }
        if (!sub.isManual && (sub.expire > 0 || sub.total > 0 || sub.download + sub.upload > 0)) {
            UsageStrip(sub)
        }
        if (sub.announce.isNotBlank()) {
            Text(
                sub.announce,
                color = Mono.Soft, fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        }
        if (!collapsed) Box(Modifier.fillMaxWidth().height(1.dp).background(Mono.Line))
    }
}

@Composable
private fun UsageStrip(sub: Subscription) {
    val used = sub.upload + sub.download
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (sub.expire > 0) "Активна до ${Format.date(sub.expire * 1000)}" else "Бессрочно",
                color = Mono.Soft, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            Text(
                "${Format.bytes(used)} / ${if (sub.total > 0) Format.bytes(sub.total) else "∞"}",
                color = Mono.Soft, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            )
        }
        if (sub.total > 0) {
            Spacer(Modifier.height(8.dp))
            val frac = (used.toFloat() / sub.total).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Mono.Line)) {
                Box(Modifier.fillMaxWidth(frac).height(3.dp).background(Mono.White))
            }
        }
    }
}

// ---------------------------------------------------------------- server row

@Composable
private fun ServerRow(
    server: Server,
    selected: Boolean,
    active: Boolean,
    pinging: Boolean,
    manual: Boolean,
    onClick: () -> Unit,
    onPing: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(if (selected) Mono.Raised else Mono.Surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(server.name, filled = selected, size = 40)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Format.cleanName(server.name),
                        color = Mono.White, fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (active) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Mono.White))
                    }
                }
                Text(
                    server.label, color = Mono.Muted, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clickable { menu = true }) {
                PingBadge(server.ping, pinging)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Mono.Raised) {
                    DropdownMenuItem(text = { Text("Проверить пинг") }, onClick = { menu = false; onPing() })
                    if (manual) DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = {
                        Icon(Icons.Default.Delete, null)
                    }, onClick = { menu = false; onDelete() })
                }
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(36.dp)
                    .background(Mono.White)
            )
        }
    }
}

@Composable
private fun Monogram(name: String, filled: Boolean, size: Int) {
    val code = if (name.isEmpty()) "—" else Format.monogram(name)
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (filled) Mono.White else Mono.Black)
            .border(1.dp, if (filled) Mono.White else Mono.LineStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            code,
            color = if (filled) Mono.Black else Mono.White,
            fontSize = (size * 0.32f).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun PingBadge(ping: Long, pinging: Boolean) {
    Row(
        Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pinging) {
            CircularProgressIndicator(Modifier.size(14.dp), color = Mono.Soft, strokeWidth = 1.5.dp)
            return@Row
        }
        Text(
            when {
                ping > 0 -> "$ping мс"
                ping < 0 -> "нет"
                else -> "—"
            },
            color = if (ping > 0) Mono.White else Mono.Muted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(8.dp))
        val bars = when {
            ping <= 0 -> 0
            ping < 300 -> 3
            ping < 900 -> 2
            else -> 1
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 1..3) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height((4 + i * 3).dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (i <= bars) Mono.White else Mono.Line)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- dialogs

@Composable
private fun monoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Mono.White,
    unfocusedBorderColor = Mono.LineStrong,
    focusedTextColor = Mono.White,
    unfocusedTextColor = Mono.White,
    cursorColor = Mono.White,
    focusedLabelColor = Mono.White,
    unfocusedLabelColor = Mono.Muted,
)

@Composable
private fun AddDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mono.Raised,
        shape = RoundedCornerShape(22.dp),
        title = { Text("Добавить", color = Mono.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Вставьте ссылку на подписку (https://…) или ключи vless://, hysteria2://, trojan://, ss://",
                    color = Mono.Soft, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Ссылка или ключ") },
                    minLines = 2,
                    maxLines = 6,
                    colors = monoFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!clip.isNullOrBlank()) text = clip.trim()
                }) { Text("Вставить из буфера", color = Mono.White) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) {
                Text("Добавить", color = if (text.isNotBlank()) Mono.White else Mono.Muted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Mono.Soft) } },
    )
}

@Composable
private fun SettingsDialog(
    data: AppData,
    onDismiss: () -> Unit,
    onChange: ((AppData) -> AppData) -> Unit,
    onRefreshAll: () -> Unit,
) {
    var ua by remember { mutableStateOf(data.userAgent) }
    val coreVersion = remember { runCatching { XrayCore.version() }.getOrDefault("?") }
    AlertDialog(
        onDismissRequest = { onChange { it.copy(userAgent = ua.ifBlank { AppData.DEFAULT_USER_AGENT }) }; onDismiss() },
        containerColor = Mono.Raised,
        shape = RoundedCornerShape(22.dp),
        title = { Text("Настройки", color = Mono.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                SettingSwitch("IPv6 через VPN", data.ipv6) { v -> onChange { it.copy(ipv6 = v) } }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ua,
                    onValueChange = { ua = it },
                    label = { Text("User-Agent подписки") },
                    singleLine = true,
                    colors = monoFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Обновить все подписки", onRefreshAll, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text("Nectar VPN ${BuildConfig.VERSION_NAME}", color = Mono.Muted, fontSize = 12.sp)
                Text("Ядро: $coreVersion", color = Mono.Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onChange { it.copy(userAgent = ua.ifBlank { AppData.DEFAULT_USER_AGENT }) }
                onDismiss()
            }) { Text("Готово", color = Mono.White, fontWeight = FontWeight.Bold) }
        },
    )
}

@Composable
private fun SettingSwitch(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!value) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Mono.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Mono.Black,
                checkedTrackColor = Mono.White,
                uncheckedThumbColor = Mono.Muted,
                uncheckedTrackColor = Mono.Black,
                uncheckedBorderColor = Mono.LineStrong,
            ),
        )
    }
}
