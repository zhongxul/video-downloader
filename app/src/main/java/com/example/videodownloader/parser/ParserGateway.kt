package com.example.videodownloader.parser

import com.example.videodownloader.domain.model.ParsedVideoInfo

interface ParserGateway {
    suspend fun parse(url: String): ParsedVideoInfo
}
