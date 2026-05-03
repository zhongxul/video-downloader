package com.example.videodownloader.di

import com.example.videodownloader.domain.model.ParsedVideoInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ParseResultPayload(
    val parsedInfo: ParsedVideoInfo,
    val sourceUrl: String,
    val parseRecordId: String?,
    val recommendedFormatId: String?,
)

class ParseResultStore {
    private val _payload = MutableStateFlow<ParseResultPayload?>(null)
    val payload: StateFlow<ParseResultPayload?> = _payload.asStateFlow()

    fun save(payload: ParseResultPayload) {
        _payload.value = payload
    }

    fun clear() {
        _payload.value = null
    }
}
