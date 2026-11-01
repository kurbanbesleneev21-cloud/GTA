package com.nectar.vpn.ui

import android.content.Context
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}

/** Slowly drifting soft light blobs and twinkling dots on a black background. */
@Composable
fun AnimatedBackground(active: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bg")
    val phase by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart), label = "phase",
    )
    val glow by animateFloatAsState(if (active) 1f else 0.45f, tween(1200), label = "glow")
    val stars = remember {
        val r = Random(7)
        List(46) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val a = phase * 2f * PI.toFloat()
        val blobs = listOf(
            Offset(w * (0.25f + 0.18f * sin(a)), h * (0.18f + 0.08f * cos(a * 2))),
            Offset(w * (0.80f + 0.12f * cos(a)), h * (0.42f + 0.10f * sin(a))),
            Offset(w * (0.35f + 0.20f * cos(a + 1.7f)), h * (0.80f + 0.06f * sin(a * 2 + 0.5f))),
        )
        blobs.forEachIndexed { i, c ->
            val radius = w * (0.55f + 0.08f * i)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.07f * glow), Color.Transparent),
                    center = c, radius = radius,
                ),
                radius = radius, center = c,
            )
        }
        stars.forEach { (x, y, k) ->
            val tw = 0.5f + 0.5f * sin(a * (2f + k * 4f) + k * 10f)
            drawCircle(
                Color.White.copy(alpha = (0.05f + 0.22f * tw) * (0.6f + 0.4f * glow)),
                radius = (0.6f + k * 1.2f).dp.toPx(),
                center = Offset(x * w, y * h),
            )
        }
    }
}

/** Country flag in a rounded tile; falls back to a monogram. */
@Composable
fun CountryIcon(name: String, size: Int, highlighted: Boolean, mono: Boolean) {
    val flag = remember(name) { Format.flag(name) }
    val auto = remember(name) { Format.isAuto(name) }
    val border by animateColorAsState(if (highlighted) Mono.White else Mono.LineStrong, tween(300), label = "b")
    val shape = RoundedCornerShape((size * 0.28f).dp)
    Box(
        Modifier
            .size(size.dp)
            .clip(shape)
            .background(Mono.Raised)
            .border(if (highlighted) 2.dp else 1.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            flag != null -> {
                Text(
                    flag,
                    fontSize = (size * 0.9f).sp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 1.45f; scaleY = 1.45f
                        if (mono && Build.VERSION.SDK_INT >= 31) renderEffect = grayscaleEffect()
                    },
                )
            }
            auto -> AutoGlyph(size)
            else -> Text(
                Format.monogram(name),
                color = Mono.White,
                fontSize = (size * 0.32f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
    }
}

@androidx.annotation.RequiresApi(31)
private fun grayscaleEffect() = RenderEffect.createColorFilterEffect(
    ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
).asComposeRenderEffect()

/** Rotating orbit glyph for "auto select" servers. */
@Composable
private fun AutoGlyph(size: Int) {
    val t = rememberInfiniteTransition(label = "auto")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "rot")
    Canvas(Modifier.size((size * 0.62f).dp).graphicsLayer { rotationZ = rot }) {
        val r = this.size.minDimension / 2f
        drawCircle(Mono.White, radius = r * 0.22f)
        drawCircle(Mono.Soft, radius = r * 0.85f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
        drawCircle(Mono.White, radius = r * 0.16f, center = Offset(center.x + r * 0.85f, center.y))
    }
}

@Composable
fun PingBadge(ping: Long, pinging: Boolean) {
    val shown by animateIntAsState(if (ping > 0) ping.toInt() else 0, tween(600), label = "ping")
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
                ping > 0 -> "$shown мс"
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
                val c by animateColorAsState(if (i <= bars) Mono.White else Mono.Line, tween(300 + i * 120), label = "bar")
                Box(
                    Modifier
                        .width(3.dp)
                        .height((4 + i * 3).dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(c)
                )
            }
        }
    }
}

/** White button with a moving shine and a press-scale animation. */
@Composable
fun ShineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shine: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "s")
    val t = rememberInfiniteTransition(label = "shine")
    val x by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2600, delayMillis = 900, easing = LinearEasing)), label = "x")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(Mono.White)
            .drawWithContent {
                drawContent()
                if (shine) {
                    val cx = size.width * x
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.10f), Color.Transparent),
                            start = Offset(cx - 80f, 0f), end = Offset(cx + 80f, size.height),
                        )
                    )
                }
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = Mono.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
        }
    }
}

/** Outlined secondary button. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "s")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Mono.LineStrong, RoundedCornerShape(16.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Mono.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** Paper-plane glyph (Telegram-like), drawn in the current monochrome style. */
@Composable
fun PlaneGlyph(color: Color = Mono.Black, size: Int = 18) {
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.04f, h * 0.46f)
            lineTo(w * 0.96f, h * 0.08f)
            lineTo(w * 0.78f, h * 0.92f)
            lineTo(w * 0.50f, h * 0.68f)
            lineTo(w * 0.36f, h * 0.86f)
            lineTo(w * 0.34f, h * 0.60f)
            close()
        }
        drawPath(path, color)
    }
}

/** Timer whose digits roll when they change. */
@Composable
fun RollingText(text: String, color: Color, fontSize: TextUnit) {
    Row {
        text.forEachIndexed { index, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    (slideInVertically(tween(260)) { it } + fadeIn(tween(260))) togetherWith
                        (slideOutVertically(tween(260)) { -it } + fadeOut(tween(200)))
                },
                label = "digit$index",
            ) { c ->
                Text(
                    c.toString(),
                    color = color,
                    fontSize = fontSize,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Light,
                )
            }
        }
    }
}

/** Small round outlined icon button with press animation. */
@Composable
fun CircleIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "s")
    Box(
        Modifier
            .padding(4.dp)
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .border(1.dp, Mono.Line, CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
