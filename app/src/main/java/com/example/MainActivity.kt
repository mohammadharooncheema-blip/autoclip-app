package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AutoClipMainScreen
import com.example.ui.theme.BgDark
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AutoClipViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Notification permission for the background clipping notification.
        // The foreground service still runs without it, but the progress
        // notification is only visible with this permission on Android 13+.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001
                )
            }
        }
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgDark
                ) {
                    val viewModel: AutoClipViewModel = viewModel()
                    AutoClipMainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
