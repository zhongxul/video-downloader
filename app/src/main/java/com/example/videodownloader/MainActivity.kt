package com.example.videodownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.videodownloader.ui.AppNavHost
import com.example.videodownloader.ui.theme.VideoDownloaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VideoDownloaderApp
        setContent {
            VideoDownloaderTheme {
                AppNavHost(container = app.container)
            }
        }
    }
}
