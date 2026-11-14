package io.github.fran2402.kderemote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.fran2402.kderemote.ui.RemoteScreen
import io.github.fran2402.kderemote.ui.theme.KdeRemoteTheme

class MainActivity : ComponentActivity() {

    private val app get() = application as RemoteApplication
    private val manager get() = app.manager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The Home APIs launch their consent screen through this activity.
        app.homeLights?.register(this)
        setContent {
            KdeRemoteTheme {
                RemoteScreen(manager, app.homeLights)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        manager.refresh()
    }
}
