package com.nectar.vpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nectar.vpn.data.Links
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
    val haptics = LocalHapticFeedback.current

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
    val connected = status.phase == Phase.CONNECTED

    Box(
        Modifier
            .fillMaxSize()
            .background(Mono.Black)
    ) {
        AnimatedBackground(active = connected)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item(key = "top") {
                Appear(0) { TopBar(onAdd = { showAdd = true }, onSettings = { showSettings = true }) }
            }
            item(key = "hero") {
                Hero(
                    status = status,
                    onToggle = {
                        if (data.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        when (status.phase) {
                            Phase.CONNECTED, Phase.CONNECTING -> vm.disconnect()
                            Phase.STOPPED -> if (selected == null) showAdd = true else onConnect()
                            Phase.STOPPING -> {}
                        }
                    },
                )
            }
            item(key = "current") { Appear(2) { CurrentServer(selected, status, data.monoFlags) } }

            if (data.subscriptions.none { !it.isManual }) {
                item(key = "empty") {
                    Appear(3) {
                        GetSubscriptionCard(
                            hasServers = data.subscriptions.isNotEmpty(),
                            onGet = { openUrl(context, Links.BOT) },
                            onHaveLink = { showAdd = true },
                        )
                    }
                }
            } else {
                item(key = "bot") {
                    Appear(3) { BotStrip(data.subscriptions) { openUrl(context, Links.BOT) } }
                }
            }

            if (data.subscriptions.isNotEmpty()) {
                item(key = "loc") {
                    Appear(4) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "ЛОКАЦИИ",
                                color = Mono.Muted, fontSize = 12.sp, letterSpacing = 3.sp,
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.pingAll() }) { Text("Пинг", color = Mono.White, fontSize = 13.sp) }
                            val allCollapsed = collapsed.containsAll(data.subscriptions.map { it.id })
                            TextButton(onClick = { vm.collapseAll() }) {
                                AnimatedContent(allCollapsed, label = "c") { c ->
                                    Text(if (c) "Развернуть" else "Свернуть", color = Mono.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            data.subscriptions.forEach { sub ->
                val isCollapsed = sub.id in collapsed
                item(key = "h-${sub.id}") {
                    Box(Modifier.animateItem()) {
                        Appear(5) {
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
                    }
                }
                if (!isCollapsed) {
                    val servers = if (data.sortByPing) sub.servers.sortedWith(
                        compareBy<Server> { if (it.ping > 0) 0 else if (it.ping == 0L) 1 else 2 }.thenBy { it.ping }
                    ) else sub.servers
                    itemsIndexed(servers, key = { _, s -> "s-${s.id}" }) { index, server ->
                        Box(Modifier.animateItem()) {
                            Appear(index + 1) {
                                ServerRow(
                                    server = server,
                                    selected = server.id == selected?.id,
                                    active = server.id == status.serverId && connected,
                                    pinging = server.id in pinging,
                                    manual = sub.isManual,
                                    monoFlags = data.monoFlags,
                                    onClick = { vm.select(server) },
                                    onPing = { vm.ping(server) },
                                    onDelete = { vm.deleteServer(server) },
                                )
                            }
                        }
                    }
                    item(key = "f-${sub.id}") {
                        Box(
                            Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                                .background(Mono.Surface)
                        )
                    }
                }
                item(key = "sp-${sub.id}") { Spacer(Modifier.height(14.dp)) }
            }
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(320)),
            exit = slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(280)),
        ) {
            SettingsScreen(
                data = data,
                onBack = { showSettings = false },
                onChange = { vm.updateSettings(it) },
                onRefreshAll = { vm.refreshAll() },
            )
        }
    }

    if (showAdd) {
        AddDialog(
            onDismiss = { showAdd = false },
            onAdd = { vm.addInput(it); showAdd = false },
            onGet = { openUrl(context, Links.BOT) },
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
            Text("NECTAR", color = Mono.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp)
            Text("V P N", color = Mono.Muted, fontSize = 10.sp, letterSpacing = 4.sp)
        }
        CircleIconButton(onAdd) { Icon(Icons.Default.Add, "Добавить", tint = Mono.White, modifier = Modifier.size(22.dp)) }
        CircleIconButton(onSettings) { Icon(Icons.Default.Settings, "Настройки", tint = Mono.White, modifier = Modifier.size(22.dp)) }
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
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "pulse",
    )
    val spin by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "spin",
    )
    val orbit by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart), label = "orbit",
    )
    val breathe by transition.animateFloat(
        0.97f, 1.03f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    val fill by animateColorAsState(if (on) Mono.White else Mono.Black, tween(500), label = "fill")
    val glyph by animateColorAsState(if (on) Mono.Black else Mono.White, tween(500), label = "glyph")
    val tickColor by animateColorAsState(if (on) Mono.Soft else Mono.LineStrong, tween(700), label = "ticks")

    // entrance
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "press")

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
            .padding(top = 20.dp)
            .graphicsLayer {
                alpha = enter.value.coerceIn(0f, 1f)
                scaleX = 0.8f + 0.2f * enter.value
                scaleY = 0.8f + 0.2f * enter.value
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val base = size.minDimension / 2f
                // slow orbiting dashed ring
                rotate(orbit) {
                    drawCircle(
                        Mono.LineStrong,
                        radius = base - 2.dp.toPx(),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 14f))),
                    )
                    drawCircle(Mono.White, radius = 3.dp.toPx(), center = Offset(c.x + base - 2.dp.toPx(), c.y))
                }
                // tick dial
                val ticks = 60
                for (i in 0 until ticks) {
                    val a = Math.toRadians(i * 360.0 / ticks - 90)
                    val r1 = base - 14.dp.toPx()
                    val r2 = base - (if (i % 5 == 0) 24.dp else 19.dp).toPx()
                    drawLine(
                        tickColor,
                        Offset(c.x + (r1 * Math.cos(a)).toFloat(), c.y + (r1 * Math.sin(a)).toFloat()),
                        Offset(c.x + (r2 * Math.cos(a)).toFloat(), c.y + (r2 * Math.sin(a)).toFloat()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                // radar pulse when connected
                if (on) {
                    for (k in 0..2) {
                        val p = (pulse + k / 3f) % 1f
                        val r = 84.dp.toPx() + p * (base - 34.dp.toPx() - 84.dp.toPx())
                        drawCircle(Color.White.copy(alpha = (1f - p) * 0.4f), radius = r, style = Stroke(1.5.dp.toPx()))
                    }
                }
                // spinner while connecting
                if (busy) {
                    val r = 98.dp.toPx()
                    for (k in 0..1) {
                        drawArc(
                            Mono.White.copy(alpha = if (k == 0) 1f else 0.35f),
                            startAngle = spin * (if (k == 0) 1f else -0.6f),
                            sweepAngle = if (k == 0) 80f else 40f,
                            useCenter = false,
                            topLeft = Offset(c.x - r, c.y - r),
                            size = Size(r * 2, r * 2),
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
            }
            val scale = press * (if (on) breathe else 1f)
            Box(
                Modifier
                    .size(160.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(fill)
                    .border(BorderStroke(2.dp, Mono.White), CircleShape)
                    .clickable(interactionSource = source, indication = null, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                val glyphRot by animateFloatAsState(if (on) 360f else 0f, tween(700, easing = FastOutSlowInEasing), label = "gr")
                Canvas(Modifier.size(54.dp).graphicsLayer { rotationZ = glyphRot }) {
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

        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (slideInVertically(tween(300)) { it / 2 } + fadeIn(tween(300))) togetherWith
                    (slideOutVertically(tween(250)) { -it / 2 } + fadeOut(tween(200)))
            },
            label = "status",
        ) { p ->
            Text(
                when (p) {
                    Phase.CONNECTED -> "ЗАЩИЩЕНО"
                    Phase.CONNECTING -> "ПОДКЛЮЧЕНИЕ…"
                    Phase.STOPPING -> "ОТКЛЮЧЕНИЕ…"
                    Phase.STOPPED -> "НЕ ЗАЩИЩЕНО"
                },
                color = Mono.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        val timerColor by animateColorAsState(if (on) Mono.White else Mono.LineStrong, tween(500), label = "tc")
        RollingText(if (on) Format.duration(now - status.since) else "00:00:00", timerColor, 34.sp)
    }
}

// ---------------------------------------------------------------- current server card

@Composable
private fun CurrentServer(server: Server?, status: VpnStatus, monoFlags: Boolean) {
    val connected = status.phase == Phase.CONNECTED
    val border by animateColorAsState(if (connected) Mono.Soft else Mono.Line, tween(500), label = "cb")
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Mono.Black.copy(alpha = 0.6f))
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = server,
            transitionSpec = {
                (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(250)) { -it / 3 } + fadeOut(tween(200)))
            },
            contentKey = { it?.id },
            label = "current",
            modifier = Modifier.weight(1f),
        ) { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s != null) CountryIcon(s.name, 48, connected, monoFlags)
                else Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).border(1.dp, Mono.LineStrong, RoundedCornerShape(13.dp)))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("ТЕКУЩАЯ ЛОКАЦИЯ", color = Mono.Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        s?.let { Format.cleanName(it.name) } ?: "Не выбрана",
                        color = Mono.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (s != null) {
                        Text(s.label, color = Mono.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }
            }
        }
        if (server != null) PingBadge(server.ping, pinging = false)
    }
}

// ---------------------------------------------------------------- get subscription

@Composable
private fun GetSubscriptionCard(hasServers: Boolean, onGet: () -> Unit, onHaveLink: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Mono.Surface)
            .border(1.dp, Mono.Line, RoundedCornerShape(22.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (hasServers) "Нужна подписка?" else "Подключитесь к Nectar VPN",
            color = Mono.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Получите подписку в нашем Telegram-боте — ссылка добавится в один клик",
            color = Mono.Soft, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        ShineButton("Получить подписку", onGet, Modifier.fillMaxWidth(), leading = { PlaneGlyph() })
        Spacer(Modifier.height(10.dp))
        GhostButton("У меня уже есть ссылка", onHaveLink, Modifier.fillMaxWidth())
    }
}

/** Compact "renew" strip shown when a subscription is already added. */
@Composable
private fun BotStrip(subs: List<Subscription>, onClick: () -> Unit) {
    val nowSec = System.currentTimeMillis() / 1000
    val soonest = subs.filter { !it.isManual && it.expire > 0 }.minOfOrNull { it.expire }
    val daysLeft = soonest?.let { ((it - nowSec) / 86400).toInt() }
    val urgent = daysLeft != null && daysLeft <= 3
    val t = rememberInfiniteTransition(label = "strip")
    val blink by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "blink")
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (urgent) Mono.White else Mono.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaneGlyph(if (urgent) Mono.Black else Mono.White, 20)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    daysLeft != null && daysLeft < 0 -> "Подписка закончилась"
                    urgent -> "Подписка заканчивается через ${daysLeft} дн."
                    else -> "Продлить или получить подписку"
                },
                color = if (urgent) Mono.Black else Mono.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            Text("@NECTARVPN_xbot", color = if (urgent) Mono.Black.copy(alpha = 0.6f) else Mono.Muted, fontSize = 12.sp)
        }
        Text(
            "→",
            color = if (urgent) Mono.Black else Mono.White,
            fontSize = 20.sp,
            modifier = Modifier.graphicsLayer { alpha = if (urgent) blink else 1f },
        )
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
    val radius by animateDpAsState(if (collapsed) 20.dp else 0.dp, tween(250), label = "r")
    val chevron by animateFloatAsState(if (collapsed) 0f else 180f, tween(300), label = "ch")
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = radius, bottomEnd = radius))
            .background(Mono.Surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Mono.Soft, modifier = Modifier.graphicsLayer { rotationZ = chevron })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    sub.title.ifBlank { "Подписка" }.uppercase(),
                    color = Mono.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val sub2 = if (sub.isManual) "Ключей: ${sub.servers.size}"
                else buildString {
                    append(if (sub.updatedAt > 0) "Обновлено ${Format.dateTime(sub.updatedAt)}" else "Не обновлялась")
                    append(" · каждые ${Format.hoursLabel(sub.updateHours)}")
                }
                Text(sub2, color = Mono.Muted, fontSize = 11.sp)
            }
            if (!sub.isManual) {
                val t = rememberInfiniteTransition(label = "rf")
                val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "rot")
                IconButton(onClick = onRefresh, enabled = !refreshing) {
                    Icon(
                        Icons.Default.Refresh, "Обновить", tint = Mono.White,
                        modifier = Modifier.graphicsLayer { rotationZ = if (refreshing) rot else 0f },
                    )
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
                sub.announce, color = Mono.Soft, fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        }
        AnimatedVisibility(!collapsed) { Box(Modifier.fillMaxWidth().height(1.dp).background(Mono.Line)) }
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
            val target = (used.toFloat() / sub.total).coerceIn(0f, 1f)
            val anim = remember { Animatable(0f) }
            LaunchedEffect(target) { anim.animateTo(target, tween(900, easing = FastOutSlowInEasing)) }
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Mono.Line)) {
                Box(Modifier.fillMaxWidth(anim.value).height(3.dp).background(Mono.White))
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
    monoFlags: Boolean,
    onClick: () -> Unit,
    onPing: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (selected) Mono.Raised else Mono.Surface, tween(300), label = "bg")
    val bar by animateDpAsState(if (selected) 36.dp else 0.dp, spring(Spring.DampingRatioMediumBouncy), label = "bar")
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(bg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CountryIcon(server.name, 42, selected, monoFlags)
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
                    AnimatedVisibility(active, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        Row {
                            Spacer(Modifier.width(8.dp))
                            LiveDot()
                        }
                    }
                }
                Text(
                    server.label, color = Mono.Muted, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable { menu = true }) {
                PingBadge(server.ping, pinging)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Mono.Raised) {
                    DropdownMenuItem(text = { Text("Проверить пинг") }, onClick = { menu = false; onPing() })
                    if (manual) DropdownMenuItem(
                        text = { Text("Удалить") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .height(bar)
                .clip(RoundedCornerShape(2.dp))
                .background(Mono.White)
        )
    }
}

@Composable
private fun LiveDot() {
    val t = rememberInfiniteTransition(label = "live")
    val s by t.animateFloat(1f, 2.4f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "s")
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(6.dp)
                .graphicsLayer { scaleX = s; scaleY = s; alpha = (2.4f - s) / 1.4f }
                .clip(CircleShape)
                .background(Mono.White)
        )
        Box(Modifier.size(6.dp).clip(CircleShape).background(Mono.White))
    }
}

// ---------------------------------------------------------------- add dialog

@Composable
private fun AddDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit, onGet: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mono.Raised,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Добавить подписку", color = Mono.White, fontWeight = FontWeight.Bold) },
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Mono.White, unfocusedBorderColor = Mono.LineStrong,
                        focusedTextColor = Mono.White, unfocusedTextColor = Mono.White,
                        cursorColor = Mono.White, focusedLabelColor = Mono.White, unfocusedLabelColor = Mono.Muted,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                GhostButton("Вставить из буфера", {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!clip.isNullOrBlank()) text = clip.trim()
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                ShineButton("Получить подписку", onGet, Modifier.fillMaxWidth(), leading = { PlaneGlyph() })
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
