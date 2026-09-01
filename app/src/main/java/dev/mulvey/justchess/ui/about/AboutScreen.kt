package dev.mulvey.justchess.ui.about

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.mulvey.justchess.BuildConfig
import dev.mulvey.justchess.SOURCE_URL
import android.content.ClipboardManager
import android.content.Context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val text = remember {
        buildString {
            appendLine("Just Chess ${BuildConfig.VERSION_NAME}")
            appendLine("applicationId ${BuildConfig.APPLICATION_ID}")
            appendLine()
            appendLine("License: GPL-3.0-or-later")
            appendLine("This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.")
            appendLine()
            appendLine("Engine: Stockfish ${BuildConfig.STOCKFISH_VERSION} (official android-armv8 build, NNUE embedded).")
            appendLine("Stockfish is copyright the Stockfish authors and licensed under GPL-3.0.")
            appendLine("Source: https://github.com/official-stockfish/Stockfish/tree/sf_${BuildConfig.STOCKFISH_VERSION}")
            appendLine()
            appendLine("Rules library: chesslib (Apache-2.0) by Ben-Hur Carlos Vieira Langoni Junior.")
            appendLine()
            appendLine("App source (copyable, no network):")
            append(SOURCE_URL)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("About") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                clipboard.setText(AnnotatedString(SOURCE_URL))
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Just Chess source", SOURCE_URL))
            }) { Text("Copy source URL") }
            Spacer(Modifier.height(12.dp))
            Text(
                "No accounts, analytics, ads, or network. Uninstall wipes local profile and history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
