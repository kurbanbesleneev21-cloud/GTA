package com.nectar.vpn.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Strict monochrome palette. */
object Mono {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0B0B0B)
    val Raised = Color(0xFF141414)
    val Line = Color(0xFF242424)
    val LineStrong = Color(0xFF3A3A3A)
    val Muted = Color(0xFF6E6E6E)
    val Soft = Color(0xFFA8A8A8)
    val White = Color(0xFFFFFFFF)
}

@Composable
fun NectarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mono.White,
            onPrimary = Mono.Black,
            secondary = Mono.Soft,
            onSecondary = Mono.Black,
            background = Mono.Black,
            onBackground = Mono.White,
            surface = Mono.Surface,
            onSurface = Mono.White,
            surfaceVariant = Mono.Raised,
            onSurfaceVariant = Mono.Soft,
            outline = Mono.LineStrong,
            error = Mono.White,
            onError = Mono.Black,
        ),
        content = content,
    )
}
