package com.example.videodownloader.di

import android.content.Context
import androidx.room.Room
import com.example.videodownloader.data.local.AppDatabase
import com.example.videodownloader.data.local.XCookieStore
import com.example.videodownloader.data.repository.DownloadTaskRepository
import com.example.videodownloader.data.repository.DownloadTaskRepositoryImpl
import com.example.videodownloader.data.repository.ParseRecordRepository
import com.example.videodownloader.data.repository.ParseRecordRepositoryImpl
import com.example.videodownloader.domain.usecase.CreateDownloadTaskUseCase
import com.example.videodownloader.domain.usecase.ClearFinishedHistoryUseCase
import com.example.videodownloader.domain.usecase.ObserveHistoryUseCase
import com.example.videodownloader.domain.usecase.ObserveTaskDetailUseCase
import com.example.videodownloader.domain.usecase.PauseDownloadTaskUseCase
import com.example.videodownloader.domain.usecase.ParseLinkUseCase
import com.example.videodownloader.domain.usecase.ResumeDownloadTaskUseCase
import com.example.videodownloader.domain.usecase.RetryDownloadTaskUseCase
import com.example.videodownloader.domain.usecase.SyncDownloadStatusUseCase
import com.example.videodownloader.download.AndroidDownloadGateway
import com.example.videodownloader.download.DownloadGateway
import com.example.videodownloader.parser.HybridParserGateway
import com.example.videodownloader.parser.ParserGateway
import com.example.videodownloader.parser.WebParserGateway
import com.example.videodownloader.parser.XCookieValidator
import com.example.videodownloader.parser.YtDlpParserGateway

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val db: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "video_downloader.db",
    ).fallbackToDestructiveMigration().build()

    val repository: DownloadTaskRepository = DownloadTaskRepositoryImpl(db.downloadTaskDao())
    val parseRecordRepository: ParseRecordRepository = ParseRecordRepositoryImpl(db.parseRecordDao())
    val xCookieStore = XCookieStore(appContext)
    val xCookieValidator = XCookieValidator(xCookieStore)
    private val webParser = WebParserGateway { xCookieStore.getCookie() }
    private val ytDlpParser = YtDlpParserGateway(appContext) { xCookieStore.getCookie() }
    val parserGateway: ParserGateway = HybridParserGateway(webParser, ytDlpParser)
    val downloadGateway: DownloadGateway = AndroidDownloadGateway(appContext) { xCookieStore.getCookie() }

    val parseLinkUseCase = ParseLinkUseCase(parserGateway)
    val createDownloadTaskUseCase = CreateDownloadTaskUseCase(repository, parseRecordRepository, downloadGateway)
    val observeHistoryUseCase = ObserveHistoryUseCase(repository)
    val observeTaskDetailUseCase = ObserveTaskDetailUseCase(repository)
    val retryDownloadTaskUseCase = RetryDownloadTaskUseCase(repository, downloadGateway)
    val syncDownloadStatusUseCase = SyncDownloadStatusUseCase(repository, parseRecordRepository, downloadGateway)
    val pauseDownloadTaskUseCase = PauseDownloadTaskUseCase(repository, downloadGateway)
    val resumeDownloadTaskUseCase = ResumeDownloadTaskUseCase(repository, downloadGateway)
    val clearFinishedHistoryUseCase = ClearFinishedHistoryUseCase(repository)
}
