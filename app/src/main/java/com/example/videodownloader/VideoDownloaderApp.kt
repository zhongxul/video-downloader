package com.example.videodownloader

import android.app.Application
import com.example.videodownloader.di.AppContainer
import timber.log.Timber

class VideoDownloaderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        container = AppContainer(this)
    }
}
