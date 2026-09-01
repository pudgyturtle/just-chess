package dev.mulvey.justchess.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.mulvey.justchess.GameViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: GameViewModel, onAbout: () -> Unit) {
    val state by vm.ui.collectAsState()
    val profile = state.profile
    var name by remember(profile.name) { mutableStateOf(profile.name) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = vm.exportBackup()
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            vm.importBackup(bytes)
        }
    }

    LaunchedEffect(state.importMessage) {
        if (state.importMessage != null) {
            kotlinx.coroutines.delay(3500)
            vm.clearImportMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("You") })
        Column(Modifier.padding(20.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { vm.saveName(name) } }) { Text("Save name") }
            Spacer(Modifier.height(20.dp))
            Text("Record  ${profile.wins}–${profile.draws}–${profile.losses}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Rating vs these engines: ${profile.ratingLabel()}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Local only. Uninstall wipes it. This is not a Chess.com or FIDE rating.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { exportLauncher.launch("just-chess-backup.zip") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export backup zip") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/zip", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import backup zip") }
            state.importMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onAbout) { Text("About & licenses") }
        }
    }
}
