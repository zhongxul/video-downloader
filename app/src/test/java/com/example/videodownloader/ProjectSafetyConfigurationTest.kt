package com.example.videodownloader

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSafetyConfigurationTest {
    @Test
    fun roomDatabaseKeepsSchemaAndDoesNotUseDestructiveMigration() {
        val appContainer = projectFile("src/main/java/com/example/videodownloader/di/AppContainer.kt").readText()
        val appDatabase = projectFile("src/main/java/com/example/videodownloader/data/local/AppDatabase.kt").readText()

        assertFalse(appContainer.contains("fallbackToDestructiveMigration"))
        assertTrue(appContainer.contains("addMigrations"))
        assertTrue(appDatabase.contains("exportSchema = true"))
    }

    @Test
    fun releaseBuildDoesNotInstallDebugLoggingTree() {
        val application = projectFile("src/main/java/com/example/videodownloader/VideoDownloaderApp.kt").readText()

        assertTrue(application.contains("BuildConfig.DEBUG"))
        assertTrue(application.contains("Timber.plant(Timber.DebugTree())"))
    }

    @Test
    fun cookieAndBackupConfigurationAvoidPlaintextBackup() {
        val manifest = projectFile("src/main/AndroidManifest.xml").readText()
        val xCookieStore = projectFile("src/main/java/com/example/videodownloader/data/local/XCookieStore.kt").readText()
        val douyinCookieStore = projectFile("src/main/java/com/example/videodownloader/data/local/DouyinCookieStore.kt").readText()

        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertTrue(xCookieStore.contains("EncryptedPreferenceStore"))
        assertTrue(douyinCookieStore.contains("EncryptedPreferenceStore"))
    }

    @Test
    fun internalM3u8DownloadsArePersistedForProcessRestart() {
        val gateway = projectFile("src/main/java/com/example/videodownloader/download/AndroidDownloadGateway.kt").readText()

        assertTrue(gateway.contains("persistInternalTask"))
        assertTrue(gateway.contains("restoreInternalM3u8Tasks"))
    }

    private fun projectFile(path: String): File {
        return File(System.getProperty("user.dir"), path)
    }
}
