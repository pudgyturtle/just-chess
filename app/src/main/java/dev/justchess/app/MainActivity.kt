package dev.justchess.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.justchess.app.ui.JustChessRoot
import dev.justchess.app.ui.theme.JustChessTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JustChessTheme {
                JustChessRoot()
            }
        }
    }
}
