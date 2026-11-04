package com.nectar.vpn.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

private suspend fun loadApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map { ai ->
            val icon = runCatching { pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            AppEntry(ai.packageName, pm.getApplicationLabel(ai).toString(), icon)
        }
        .sortedBy { it.label.lowercase() }
}

private val GRAYSCALE = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun AppPickerScreen(
    title: String,
    hint: String,
    initial: List<String>,
    onDone: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(initial.toSet()) }
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) { value = loadApps(context) }
    BackHandler { onDone(selected.toList()) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Mono.Black)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(onClick = { onDone(selected.toList()) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Mono.White)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Mono.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("Выбрано: ${selected.size}", color = Mono.Muted, fontSize = 12.sp)
                }
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { selected = emptySet() }) { Text("Сбросить", color = Mono.White) }
                }
            }
            Text(
                hint, color = Mono.Soft, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Поиск приложения", color = Mono.Muted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Mono.Soft) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Mono.White, unfocusedBorderColor = Mono.LineStrong,
                    focusedTextColor = Mono.White, unfocusedTextColor = Mono.White, cursorColor = Mono.White,
                ),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            )
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Mono.White, strokeWidth = 2.dp)
                }
            } else {
                val q = query.trim().lowercase()
                val filtered = list
                    .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }
                    .sortedByDescending { it.pkg in selected }
                LazyColumn(contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp)) {
                    items(filtered, key = { it.pkg }) { app ->
                        Box(Modifier.animateItem()) {
                            AppRow(app, app.pkg in selected) {
                                selected = if (app.pkg in selected) selected - app.pkg else selected + app.pkg
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onToggle: () -> Unit) {
    val box by animateColorAsState(if (checked) Mono.White else Mono.Black, tween(200), label = "box")
    val iconAlpha by animateFloatAsState(if (checked) 1f else 0.55f, tween(250), label = "ia")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app.icon != null) {
            Image(
                app.icon, null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                alpha = iconAlpha,
                colorFilter = if (checked) null else GRAYSCALE,
            )
        } else {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Mono.Raised))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = Mono.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.pkg, color = Mono.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(box)
                .border(1.5.dp, if (checked) Mono.White else Mono.LineStrong, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Default.Check, null, tint = Mono.Black, modifier = Modifier.size(16.dp))
        }
    }
}
