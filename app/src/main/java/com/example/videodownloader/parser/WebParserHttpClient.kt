package com.example.videodownloader.parser

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object WebParserHttpClient {
    fun default(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
