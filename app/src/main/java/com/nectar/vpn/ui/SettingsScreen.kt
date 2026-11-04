package com.nectar.vpn.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nectar.vpn.BuildConfig
import com.nectar.vpn.core.XrayCore
import com.nectar.vpn.data.AppData
import com.nectar.vpn.data.Links
import kotlinx.coroutines.delay

private val DNS_OPTIONS = listOf(
    "1.1.1.1" to "Cloudflare",
    "8.8.8.8" to "Google",
    "9.9.9.9" to "Quad9",
    "94.140.14.14" to "AdGuard (без рекламы)",
)

@Composable
fun SettingsScreen(
    data: AppData,
    onBack: () -> Unit,
    onChange: ((AppData) -> AppData) -> Unit,
    onRefreshAll: () -> Unit,
    onPickFastest: () -> Unit = {},
    onAutoVpn: (Boolean) -> Unit = {},
    onConnectionSettingsChanged: () -> Unit = {},
    remote: com.nectar.vpn.data.Remote = com.nectar.vpn.data.Remote(),
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var ua by remember { mutableStateOf(data.userAgent) }
    var picker by remember { mutableStateOf<String?>(null) } // "split" | "auto"
    var hasUsage by remember { mutableStateOf(com.nectar.vpn.vpn.AppUsage.hasPermission(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        hasUsage = com.nectar.vpn.vpn.AppUsage.hasPermission(context)
        onPauseOrDispose {}
    }
    fun changeConnection(block: (AppData) -> AppData) {
        onChange(block)
        onConnectionSettingsChanged()
    }
    val coreVersion = remember { runCatching { XrayCore.version() }.getOrDefault("?") }

    fun saveUa() = onChange { it.copy(userAgent = ua.trim().ifBlank { AppData.DEFAULT_USER_AGENT }) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Mono.Black)
    ) {
        AnimatedBackground(active = false)
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item {
                Row(
                    Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIconButton(onClick = { saveUa(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Mono.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("НАСТРОЙКИ", color = Mono.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                }
            }

            item {
                Appear(0) {
                    Column(
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, Mono.Line, RoundedCornerShape(20.dp))
                            .padding(18.dp),
                    ) {
                        Text("Подписка Nectar VPN", color = Mono.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Купить, продлить или получить ссылку — в нашем Telegram-боте", color = Mono.Soft, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        ShineButton(
                            "Получить подписку",
                            onClick = { openUrl(context, remote.bot) },
                            modifier = Modifier.fillMaxWidth(),
                            leading = { PlaneGlyph() },
                        )
                    }
                }
            }

            item { Section("ПОДКЛЮЧЕНИЕ", 1) }
            item {
                Appear(2) {
                    Group {
                        ToggleRow("Обход локальной сети", "Роутер, принтер и т.п. — напрямую", data.bypassLan) { v -> changeConnection { it.copy(bypassLan = v) } }
                        Divider()
                        ToggleRow("IPv6 через VPN", "Отключите, если сайты открываются медленно", data.ipv6) { v -> changeConnection { it.copy(ipv6 = v) } }
                        Divider()
                        Text(
                            "DNS-сервер",
                            color = Mono.White, fontSize = 15.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                        )
                        DNS_OPTIONS.forEach { (ip, name) ->
                            ChoiceRow(name, ip, data.dns == ip) { changeConnection { it.copy(dns = ip) } }
                        }
                        Spacer(Modifier.height(6.dp))
                        Divider()
                        LinkRow("Kill switch / постоянный VPN", "Системные настройки VPN Android") {
                            runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                        }
                    }
                }
            }

            item { Section("СКОРОСТЬ И СТАБИЛЬНОСТЬ", 3) }
            item {
                Appear(4) {
                    Group {
                        ToggleRow("Автоперезапуск", "Перезапускать соединение, если сервер не отвечает", data.autoReconnect) { v -> onChange { it.copy(autoReconnect = v) } }
                        Divider()
                        ToggleRow("Автосмена сервера", "Если перезапуск не помог — переключить на самый быстрый", data.autoSwitch) { v -> onChange { it.copy(autoSwitch = v) } }
                        Divider()
                        ToggleRow("При смене сети", "Мгновенно переподключаться при переходе Wi-Fi ↔ мобильная", data.reconnectOnNetworkChange) { v -> onChange { it.copy(reconnectOnNetworkChange = v) } }
                        Divider()
                        Column(Modifier.padding(16.dp)) {
                            ShineButton("Найти самый быстрый сервер", { onPickFastest(); onBack() }, Modifier.fillMaxWidth(), shine = false)
                        }
                    }
                }
            }

            if (remote.featSplit || remote.featAutoVpn) item { Section("ПРИЛОЖЕНИЯ", 5) }
            if (remote.featSplit || remote.featAutoVpn) item {
                Appear(6) {
                    Group {
                      if (remote.featSplit) {
                        Text(
                            "Раздельное туннелирование",
                            color = Mono.White, fontSize = 15.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                        )
                        ChoiceRow("Все приложения через VPN", "", data.splitMode == AppData.SPLIT_ALL) { changeConnection { it.copy(splitMode = AppData.SPLIT_ALL) } }
                        ChoiceRow("Только выбранные", "", data.splitMode == AppData.SPLIT_ONLY) { changeConnection { it.copy(splitMode = AppData.SPLIT_ONLY) } }
                        ChoiceRow("Все, кроме выбранных", "", data.splitMode == AppData.SPLIT_EXCEPT) { changeConnection { it.copy(splitMode = AppData.SPLIT_EXCEPT) } }
                        if (data.splitMode != AppData.SPLIT_ALL) {
                            LinkRow("Выбрать приложения", "Выбрано: ${data.splitApps.size}") { picker = "split" }
                        }
                        Spacer(Modifier.height(6.dp))
                        Divider()
                      }
                      if (remote.featAutoVpn) {
                        ToggleRow(
                            "Авто-VPN по приложениям",
                            "VPN включится при входе в выбранное приложение и выключится при выходе",
                            data.autoVpn,
                        ) { v -> onAutoVpn(v) }
                        if (data.autoVpn) {
                            LinkRow("Приложения для авто-VPN", "Выбрано: ${data.autoApps.size}") { picker = "auto" }
                            if (!hasUsage) {
                                Divider()
                                LinkRow("⚠ Дайте доступ к статистике", "Нужно, чтобы видеть, какое приложение открыто") {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                }
                            }
                        }
                      }
                    }
                }
            }

            item { Section("ПОДПИСКИ", 7) }
            item {
                Appear(4) {
                    Group {
                        ToggleRow("Автообновление", "Обновлять при запуске по интервалу подписки", data.autoUpdate) { v -> onChange { it.copy(autoUpdate = v) } }
                        Divider()
                        ToggleRow("Пинг после обновления", "Сразу проверять все серверы", data.pingAfterUpdate) { v -> onChange { it.copy(pingAfterUpdate = v) } }
                        Divider()
                        Column(Modifier.padding(16.dp)) {
                          if (remote.featUserAgent) {
                            OutlinedTextField(
                                value = ua,
                                onValueChange = { ua = it },
                                label = { Text("User-Agent") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Mono.White, unfocusedBorderColor = Mono.LineStrong,
                                    focusedTextColor = Mono.White, unfocusedTextColor = Mono.White,
                                    cursorColor = Mono.White, focusedLabelColor = Mono.White, unfocusedLabelColor = Mono.Muted,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                          }
                            GhostButton("Обновить все подписки", { saveUa(); onRefreshAll() }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            item { Section("ВНЕШНИЙ ВИД", 9) }
            item {
                Appear(6) {
                    Group {
                        ToggleRow("Сортировать по пингу", "Быстрые серверы сверху", data.sortByPing) { v -> onChange { it.copy(sortByPing = v) } }
                        Divider()
                        ToggleRow("Чёрно-белые флаги", "Android 12 и новее", data.monoFlags) { v -> onChange { it.copy(monoFlags = v) } }
                        Divider()
                        ToggleRow("Вибрация", "При нажатии на кнопку подключения", data.haptics) { v -> onChange { it.copy(haptics = v) } }
                    }
                }
            }

            item { Section("О ПРИЛОЖЕНИИ", 11) }
            item {
                Appear(8) {
                    Group {
                        LinkRow("Telegram-бот", remote.bot.removePrefix("https://t.me/").let { "@$it" }) { openUrl(context, remote.bot) }
                        if (remote.support.isNotBlank()) { Divider(); LinkRow("Поддержка", remote.support) { openUrl(context, remote.support) } }
                        if (remote.iphone.isNotBlank()) { Divider(); LinkRow("Nectar VPN на iPhone", "Инструкция") { openUrl(context, remote.iphone) } }
                        Divider()
                        InfoRow("Версия", BuildConfig.VERSION_NAME)
                        Divider()
                        InfoRow("Ядро Xray", coreVersion)
                    }
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = picker != null,
            enter = androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.fadeOut(),
        ) {
            val kind = picker
            if (kind == "split") {
                AppPickerScreen(
                    title = "Раздельное туннелирование",
                    hint = if (data.splitMode == AppData.SPLIT_ONLY) "Через VPN пойдут только отмеченные приложения"
                    else "Отмеченные приложения будут работать без VPN",
                    initial = data.splitApps,
                ) { list -> picker = null; changeConnection { it.copy(splitApps = list) } }
            } else if (kind == "auto") {
                AppPickerScreen(
                    title = "Авто-VPN",
                    hint = "VPN включится сам, когда вы откроете одно из отмеченных приложений, и выключится, когда выйдете",
                    initial = data.autoApps,
                ) { list -> picker = null; onChange { it.copy(autoApps = list) }; onAutoVpn(true) }
            }
        }
    }
}

/** Fade + slide-up entrance with a small stagger. */
@Composable
fun Appear(index: Int, content: @Composable () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index % 12) * 45L)
        anim.animateTo(1f, tween(420))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = anim.value
            translationY = (1f - anim.value) * 40f
        }
    ) { content() }
}

@Composable
private fun Section(title: String, index: Int) {
    Appear(index) {
        Text(
            title,
            color = Mono.Muted, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Mono.Surface)
    ) { content() }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Mono.Line)
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!value) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Mono.White, fontSize = 15.sp)
            Text(subtitle, color = Mono.Muted, fontSize = 12.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Mono.Black,
                checkedTrackColor = Mono.White,
                checkedBorderColor = Mono.White,
                uncheckedThumbColor = Mono.Muted,
                uncheckedTrackColor = Mono.Black,
                uncheckedBorderColor = Mono.LineStrong,
            ),
        )
    }
}

@Composable
private fun ChoiceRow(title: String, value: String, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(if (selected) Mono.White else Mono.LineStrong, tween(250), label = "r")
    val dot by animateColorAsState(if (selected) Mono.White else Mono.Surface, tween(250), label = "d")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .width(18.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(1.5.dp, ring, RoundedCornerShape(9.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(dot)
        )
        Text(title, color = Mono.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Mono.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Mono.White, fontSize = 15.sp)
            Text(subtitle, color = Mono.Muted, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Mono.Soft)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Mono.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Mono.Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}
