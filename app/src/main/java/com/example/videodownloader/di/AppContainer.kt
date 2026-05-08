package com.example.videodownloader.di

import android.content.Context
import androidx.room.Room
import com.example.videodownloader.core.text.AndroidAppText
import com.example.videodownloader.data.local.AppDatabase
import com.example.videodownloader.data.local.AppDatabaseMigrations
import com.example.videodownloader.data.local.AppSettingsStore
import com.example.videodownloader.data.local.DouyinCookieStore
import com.example.videodownloader.data.local.XCookieStore
import com.example.videodownloader.data.repository.DownloadTaskRepository
import com.example.videodownloader.data.repository.DownloadTaskRepositoryImpl
import com.example.videodownloader.data.repository.ParseRecordRepository
import com.example.videodownloader.data.repository.ParseRecordRepositoryImpl
import com.example.videodownloader.domain.usecase.CreateDownloadTaskUseCase
import com.example.videodownloader.domain.usecase.PauseDownloadTaskUseCase
import com.example.videodownloader.domain.usecase.ParseLinkUseCase
import com.example.videodownloader.domain.usecase.ResumeDownloadTaskUseCase
import com.example.videodownloader.domain.usecase.RetryDownloadTaskUseCase
import com.example.videodownloader.domain.usecase.SyncDownloadStatusUseCase
import com.example.videodownloader.download.AndroidDownloadGateway
import com.example.videodownloader.download.DownloadGateway
import com.example.videodownloader.parser.HybridParserGateway
import com.example.videodownloader.parser.ParserGateway
import com.example.videodownloader.parser.DouyinSignedWebParserGateway
import com.example.videodownloader.parser.WebParserGateway
import com.example.videodownloader.parser.XCookieValidator
import com.example.videodownloader.parser.YtDlpParserGateway

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    private val db: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "video_downloader.db",
    )
        .addMigrations(
            AppDatabaseMigrations.MIGRATION_1_2,
            AppDatabaseMigrations.MIGRATION_2_3,
        )
        .build()

    val repository: DownloadTaskRepository = DownloadTaskRepositoryImpl(db.downloadTaskDao())
    val parseRecordRepository: ParseRecordRepository = ParseRecordRepositoryImpl(db.parseRecordDao())
    val parseResultStore = ParseResultStore()
    val appText = AndroidAppText(appContext)
    val appSettingsStore = AppSettingsStore(appContext)
    val xCookieStore = XCookieStore(appContext)
    val douyinCookieStore = DouyinCookieStore(appContext)
    val xCookieValidator = XCookieValidator(xCookieStore, appText)
    private val webParser = WebParserGateway(appText) { xCookieStore.getCookie() }
    private val ytDlpParser = YtDlpParserGateway(appContext, appText) { xCookieStore.getCookie() }
    private val douyinSignedParser = DouyinSignedWebParserGateway(
        appText = appText,
        cookieProvider = { douyinCookieStore.getCookie() },
    )
    val parserGateway: ParserGateway = HybridParserGateway(webParser, ytDlpParser, douyinSignedParser, appText)
    val downloadGateway: DownloadGateway = AndroidDownloadGateway(
        context = appContext,
        appText = appText,
        xCookieProvider = { xCookieStore.getCookie() },
        notificationEnabledProvider = { appSettingsStore.isDownloadNotificationEnabled() },
    )

    val parseLinkUseCase = ParseLinkUseCase(parserGateway, appText)
    val createDownloadTaskUseCase = CreateDownloadTaskUseCase(repository, parseRecordRepository, downloadGateway, appText)
    val retryDownloadTaskUseCase = RetryDownloadTaskUseCase(repository, downloadGateway, appText)
    val syncDownloadStatusUseCase = SyncDownloadStatusUseCase(repository, parseRecordRepository, downloadGateway, appText)
    val pauseDownloadTaskUseCase = PauseDownloadTaskUseCase(repository, downloadGateway, appText)
    val resumeDownloadTaskUseCase = ResumeDownloadTaskUseCase(repository, downloadGateway, appText)
}
