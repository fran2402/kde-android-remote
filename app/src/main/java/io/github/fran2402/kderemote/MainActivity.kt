package io.github.fran2402.kderemote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.fran2402.kderemote.ui.RemoteScreen
import io.github.fran2402.kderemote.ui.theme.KdeRemoteTheme

class MainActivity : ComponentActivity() {

    private val manager get() = (application as RemoteApplication).manager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KdeRemoteTheme {
                RemoteScreen(manager)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        manager.refresh()
    }
}
