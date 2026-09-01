package dev.mulvey.justchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.mulvey.justchess.ui.JustChessRoot
import dev.mulvey.justchess.ui.theme.JustChessTheme

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
