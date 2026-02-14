package com.example.videodownloader.download

import android.app.DownloadManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class AndroidDownloadGateway(
    private val context: Context,
    private val xCookieProvider: (() -> String?)? = null,
) : DownloadGateway {
    private companion object {
        const val TAG = "AndroidDownloadGateway"
        const val M3U8_SEGMENT_MAX_RETRY = 3
        const val M3U8_SEGMENT_RETRY_BASE_DELAY_MS = 1200L
    }

    private val destinationFolder = "VideoDownloader"
    private val scannedDownloadIds = mutableSetOf<Long>()
    private val enqueuedPathById = mutableMapOf<Long, String>()

    private val preflightClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val m3u8Client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val internalTaskIdSeed = AtomicLong(-1L)
    private val internalM3u8Tasks = ConcurrentHashMap<Long, InternalM3u8Task>()

    override suspend fun startDownload(
        url: String,
        fileName: String,
    ): StartDownloadResult = withContext(Dispatchers.IO) {
        if (isLikelyM3u8Url(url)) {
            return@withContext startM3u8Download(url, fileName)
        }

        validateBeforeEnqueue(url)?.let { message ->
            throw IllegalArgumentException(message)
        }

        val finalFileName = ensureUniqueFileName(fileName)
        val relativePath = "$destinationFolder/$finalFileName"
        val absolutePath = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            relativePath,
        ).absolutePath

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(finalFileName)
            .setMimeType(inferMimeType(finalFileName))
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setVisibleInDownloadsUi(true)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DCIM,
                relativePath,
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        buildHeaders(url).forEach { (key, value) ->
            request.addRequestHeader(key, value)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)

        synchronized(enqueuedPathById) {
            enqueuedPathById[id] = absolutePath
        }

        StartDownloadResult(
            externalId = id,
            saveUri = "file://$absolutePath",
            fileName = finalFileName,
        )
    }

    override suspend fun queryDownloadProgress(externalId: Long): DownloadProgressSnapshot =
        withContext(Dispatchers.IO) {
            if (externalId < 0L) {
                return@withContext queryInternalM3u8Progress(externalId)
            }

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(externalId)
            manager.query(query).use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@withContext DownloadProgressSnapshot(
                        state = DownloadProgressState.FAILED,
                        progress = null,
                        saveUri = null,
                        errorMessage = "涓嬭浇浠诲姟涓嶅瓨鍦ㄦ垨宸茶绯荤粺娓呯悊",
                    )
                }

                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                val status = cursor.getInt(statusIndex)
                val downloaded = if (downloadedIndex >= 0) cursor.getLong(downloadedIndex) else -1L
                val total = if (totalIndex >= 0) cursor.getLong(totalIndex) else -1L
                val progress = if (downloaded >= 0 && total > 0) {
                    ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                } else {
                    null
                }
                val localUri = if (localUriIndex >= 0) cursor.getString(localUriIndex) else null
                val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else 0

                when (status) {
                    DownloadManager.STATUS_PENDING -> DownloadProgressSnapshot(
                        state = DownloadProgressState.QUEUED,
                        progress = progress ?: 0,
                        saveUri = localUri,
                        errorMessage = null,
                    )

                    DownloadManager.STATUS_PAUSED,
                    DownloadManager.STATUS_RUNNING,
                    -> DownloadProgressSnapshot(
                        state = DownloadProgressState.DOWNLOADING,
                        progress = progress,
                        saveUri = localUri,
                        errorMessage = null,
                    )

                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val resolvedPath = resolvePathFromLocalUri(localUri)
                            ?: synchronized(enqueuedPathById) { enqueuedPathById[externalId] }
                        val validation = validateDownloadedResult(localUri, resolvedPath)
                        if (!validation.valid) {
                            deleteInvalidResult(localUri, resolvedPath)
                            synchronized(scannedDownloadIds) {
                                scannedDownloadIds.remove(externalId)
                            }
                            synchronized(enqueuedPathById) {
                                enqueuedPathById.remove(externalId)
                            }
                            return@withContext DownloadProgressSnapshot(
                                state = DownloadProgressState.FAILED,
                                progress = null,
                                saveUri = null,
                                errorMessage = validation.message,
                            )
                        }
                        if (!resolvedPath.isNullOrBlank()) {
                            ensureMediaIndexed(externalId, resolvedPath)
                        }
                        DownloadProgressSnapshot(
                            state = DownloadProgressState.SUCCESS,
                            progress = 100,
                            saveUri = resolvedPath?.let { "file://$it" } ?: localUri,
                            errorMessage = null,
                        )
                    }

                    DownloadManager.STATUS_FAILED -> DownloadProgressSnapshot(
                        state = DownloadProgressState.FAILED,
                        progress = progress,
                        saveUri = localUri,
                        errorMessage = mapFailureReason(reason),
                    )

                    else -> DownloadProgressSnapshot(
                        state = DownloadProgressState.FAILED,
                        progress = progress,
                        saveUri = localUri,
                        errorMessage = "下载状态未知",
                    )
                }
            }
        }

    override suspend fun cancelDownload(externalId: Long): Unit = withContext(Dispatchers.IO) {
        if (externalId < 0L) {
            val task = internalM3u8Tasks[externalId]
            task?.job?.cancel()
            task?.let {
                deleteFileQuietly(it.absolutePath)
                it.state = DownloadProgressState.FAILED
                it.progress = it.progress ?: 0
                it.saveUri = null
                it.errorMessage = "下载已取消"
                it.job = null
            }
            return@withContext Unit
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.remove(externalId)
        synchronized(scannedDownloadIds) {
            scannedDownloadIds.remove(externalId)
        }
        synchronized(enqueuedPathById) {
            enqueuedPathById.remove(externalId)
        }
        Unit
    }

    private fun startM3u8Download(url: String, fileName: String): StartDownloadResult {
        val normalizedName = if (fileName.endsWith(".m3u8", ignoreCase = true)) {
            fileName.replace(Regex("\\.m3u8$", RegexOption.IGNORE_CASE), ".mp4")
        } else {
            fileName
        }
        val finalFileName = ensureUniqueFileName(normalizedName)
        val relativePath = "$destinationFolder/$finalFileName"
        val absolutePath = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            relativePath,
        ).absolutePath

        val internalId = internalTaskIdSeed.getAndDecrement()
        val state = InternalM3u8Task(
            state = DownloadProgressState.QUEUED,
            progress = 0,
            saveUri = "file://$absolutePath",
            errorMessage = null,
            absolutePath = absolutePath,
            sourceUrl = url,
            job = null,
        )
        internalM3u8Tasks[internalId] = state

        state.job = internalScope.launch {
            runCatching {
                downloadAndMergeM3u8(internalId, state.sourceUrl, state.absolutePath)
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                deleteFileQuietly(state.absolutePath)
                updateInternalTask(
                    internalId = internalId,
                    newState = DownloadProgressState.FAILED,
                    progress = state.progress ?: 0,
                    saveUri = null,
                    errorMessage = throwable.message ?: "m3u8 涓嬭浇澶辫触",
                )
            }
        }

        return StartDownloadResult(
            externalId = internalId,
            saveUri = state.saveUri,
            fileName = finalFileName,
        )
    }

    private suspend fun queryInternalM3u8Progress(externalId: Long): DownloadProgressSnapshot {
        val state = internalM3u8Tasks[externalId]
            ?: return DownloadProgressSnapshot(
                state = DownloadProgressState.FAILED,
                progress = null,
                saveUri = null,
                errorMessage = "涓嬭浇浠诲姟涓嶅瓨鍦ㄦ垨宸茶娓呯悊",
            )

        return DownloadProgressSnapshot(
            state = state.state,
            progress = state.progress,
            saveUri = state.saveUri,
            errorMessage = state.errorMessage,
        )
    }

    private suspend fun downloadAndMergeM3u8(
        internalId: Long,
        sourceUrl: String,
        absolutePath: String,
    ) {
        updateInternalTask(
            internalId = internalId,
            newState = DownloadProgressState.DOWNLOADING,
            progress = 0,
            saveUri = "file://$absolutePath",
            errorMessage = null,
        )

        val targetFile = File(absolutePath)
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val mediaPlaylistUrl = resolveMediaPlaylistUrl(sourceUrl)
            ?: throw IllegalArgumentException("鏈幏鍙栧埌鏈夋晥 m3u8 鎾斁鍒楄〃")
        val mediaPlaylistText = fetchText(mediaPlaylistUrl)
            ?: throw IllegalArgumentException("m3u8 鎾斁鍒楄〃璇诲彇澶辫触")
        if (mediaPlaylistText.contains("#EXT-X-KEY")) {
            throw IllegalArgumentException("检测到加密 m3u8，当前不支持下载")
        }

        val segmentUrls = parseSegmentUrls(mediaPlaylistUrl, mediaPlaylistText)
        if (segmentUrls.isEmpty()) {
            throw IllegalArgumentException("m3u8 未找到可下载分片")
        }

        FileOutputStream(targetFile).buffered().use { output ->
            val total = segmentUrls.size
            segmentUrls.forEachIndexed { index, segmentUrl ->
                currentCoroutineContext().ensureActive()
                appendSegmentToOutput(segmentUrl, output)
                val progress = (((index + 1).toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(1, 100)
                updateInternalTask(
                    internalId = internalId,
                    newState = DownloadProgressState.DOWNLOADING,
                    progress = progress,
                    saveUri = "file://$absolutePath",
                    errorMessage = null,
                )
            }
        }

        if (!targetFile.exists() || targetFile.length() <= 0L) {
            throw IllegalArgumentException("鍚堝苟鍚庣殑鏂囦欢涓虹┖")
        }

        val validation = validateDownloadedResult(
            localUri = "file://${targetFile.absolutePath}",
            absolutePath = targetFile.absolutePath,
        )
        if (!validation.valid) {
            deleteFileQuietly(targetFile.absolutePath)
            throw IllegalArgumentException(validation.message ?: "m3u8 涓嬭浇缁撴灉鏍￠獙澶辫触")
        }

        ensureMediaIndexed(internalId, targetFile.absolutePath)
        updateInternalTask(
            internalId = internalId,
            newState = DownloadProgressState.SUCCESS,
            progress = 100,
            saveUri = "file://${targetFile.absolutePath}",
            errorMessage = null,
        )
    }

    private fun resolveMediaPlaylistUrl(sourceUrl: String): String? {
        val text = fetchText(sourceUrl) ?: return null
        if (!text.contains("#EXTM3U", ignoreCase = true)) return null
        if (!text.contains("#EXT-X-STREAM-INF", ignoreCase = true)) {
            return sourceUrl
        }

        val variantUrls = parseVariantPlaylistUrls(sourceUrl, text)
        if (variantUrls.isEmpty()) return sourceUrl
        return variantUrls.maxByOrNull { it.bandwidth }?.url ?: variantUrls.first().url
    }

    private fun parseVariantPlaylistUrls(baseUrl: String, text: String): List<VariantPlaylist> {
        val result = mutableListOf<VariantPlaylist>()
        val lines = text.lines()
        var pendingBandwidth: Int? = null
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                pendingBandwidth = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 1
                continue
            }
            val currentBandwidth = pendingBandwidth ?: continue
            if (line.isBlank() || line.startsWith("#")) continue
            resolveRelativeUrl(baseUrl, line)?.let { resolved ->
                result += VariantPlaylist(resolved, currentBandwidth)
            }
            pendingBandwidth = null
        }
        return result
    }

    private fun parseSegmentUrls(baseUrl: String, text: String): List<String> {
        val result = mutableListOf<String>()
        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            resolveRelativeUrl(baseUrl, line)?.let { resolved ->
                result += resolved
            }
        }
        return result
    }

    private fun resolveRelativeUrl(baseUrl: String, candidate: String): String? {
        val clean = candidate.trim()
        if (clean.isBlank()) return null
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            return clean
        }
        return runCatching { URI(baseUrl).resolve(clean).toString() }.getOrNull()
    }

    private fun fetchText(url: String): String? {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36",
            )
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        buildHeaders(url).forEach { (k, v) -> requestBuilder.header(k, v) }

        return runCatching {
            m3u8Client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull()
    }

    private suspend fun appendSegmentToOutput(segmentUrl: String, output: OutputStream) {
        var lastError: Throwable? = null
        repeat(M3U8_SEGMENT_MAX_RETRY) { attempt ->
            currentCoroutineContext().ensureActive()
            val failure = runCatching {
                val requestBuilder = Request.Builder()
                    .url(segmentUrl)
                    .header("Accept", "*/*")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36",
                    )
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                buildHeaders(segmentUrl).forEach { (k, v) -> requestBuilder.header(k, v) }

                m3u8Client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw SegmentHttpException(response.code)
                    }
                    val input = response.body?.byteStream()
                        ?: throw IOException("segment body is empty")
                    input.use { stream -> stream.copyTo(output) }
                }
            }.exceptionOrNull()

            if (failure == null) return
            lastError = failure

            val isLastTry = attempt >= M3U8_SEGMENT_MAX_RETRY - 1
            if (!shouldRetrySegmentError(failure) || isLastTry) return@repeat

            val delayMs = M3U8_SEGMENT_RETRY_BASE_DELAY_MS * (attempt + 1)
            Log.w(
                TAG,
                "segment retry " + (attempt + 1) + "/" + M3U8_SEGMENT_MAX_RETRY +
                    ", url=" + segmentUrl + ", reason=" + failure.message,
            )
            delay(delayMs)
        }

        throw IllegalStateException(
            "m3u8 segment download failed after retries: " + segmentUrl,
            lastError,
        )
    }

    private fun shouldRetrySegmentError(throwable: Throwable): Boolean {
        val root = rootCauseOf(throwable)
        return when (root) {
            is SegmentHttpException -> {
                root.code == 403 || root.code == 408 || root.code == 429 || root.code in 500..599
            }
            is SocketTimeoutException,
            is InterruptedIOException,
            is IOException,
            -> true
            else -> false
        }
    }

    private fun rootCauseOf(throwable: Throwable): Throwable {
        var current = throwable
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    private class SegmentHttpException(val code: Int) : IOException("HTTP " + code)

    private fun updateInternalTask(
        internalId: Long,
        newState: DownloadProgressState,
        progress: Int?,
        saveUri: String?,
        errorMessage: String?,
    ) {
        val task = internalM3u8Tasks[internalId] ?: return
        task.state = newState
        task.progress = progress
        task.saveUri = saveUri
        task.errorMessage = errorMessage
        if (newState == DownloadProgressState.SUCCESS || newState == DownloadProgressState.FAILED) {
            task.job = null
        }
    }

    private fun isLikelyM3u8Url(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8")
    }

    private fun ensureUniqueFileName(fileName: String): String {
        val cleanName = fileName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .trim('.')
            .ifBlank { "video.mp4" }
        val (baseName, ext) = splitNameAndExt(cleanName)
        val targetDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            destinationFolder,
        ).apply { mkdirs() }

        var index = 0
        while (true) {
            val suffix = if (index == 0) "" else "($index)"
            val candidate = if (ext.isBlank()) {
                "$baseName$suffix"
            } else {
                "$baseName$suffix.$ext"
            }
            if (!File(targetDir, candidate).exists()) {
                return candidate
            }
            index++
        }
    }

    private fun splitNameAndExt(fileName: String): Pair<String, String> {
        val lastDotIndex = fileName.lastIndexOf('.')
        val hasExt = lastDotIndex in 1 until fileName.length - 1
        if (!hasExt) {
            return fileName.ifBlank { "video" } to ""
        }
        val baseName = fileName.substring(0, lastDotIndex).ifBlank { "video" }
        val ext = fileName.substring(lastDotIndex + 1).trim().lowercase()
        return baseName to ext
    }

    private fun buildHeaders(url: String): List<Pair<String, String>> {
        val headers = mutableListOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36",
            "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
        )

        if (url.contains("douyin.com", true) || url.contains("iesdouyin.com", true)) {
            headers += "Referer" to "https://www.douyin.com/"
        }
        if (url.contains("x.com", true) || url.contains("twitter.com", true)) {
            headers += "Referer" to "https://x.com/"
            xCookieProvider?.invoke()
                ?.takeIf { it.isNotBlank() }
                ?.let { cookie -> headers += "Cookie" to cookie }
        }

        return headers
    }

    private fun mapFailureReason(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "涓嬭浇鏃犳硶鎭㈠"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "未找到存储设备"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "目标文件已存在"
            DownloadManager.ERROR_FILE_ERROR -> "鏂囦欢璇诲啓澶辫触"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "缃戠粶鏁版嵁閿欒"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "瀛樺偍绌洪棿涓嶈冻"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向过多"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "鏈嶅姟鍣ㄨ繑鍥炲紓甯哥姸鎬佺爜"
            DownloadManager.ERROR_UNKNOWN -> "鏈煡閿欒"
            else -> "涓嬭浇澶辫触"
        }
    }

    private fun inferMimeType(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
            else -> "video/*"
        }
    }

    private fun resolvePathFromLocalUri(localUri: String?): String? {
        if (localUri.isNullOrBlank()) return null
        return if (localUri.startsWith("file://", ignoreCase = true)) {
            Uri.parse(localUri).path
        } else {
            null
        }
    }

    private fun ensureMediaIndexed(externalId: Long, absolutePath: String) {
        val shouldScan = synchronized(scannedDownloadIds) {
            if (scannedDownloadIds.contains(externalId)) {
                false
            } else {
                scannedDownloadIds += externalId
                true
            }
        }
        if (!shouldScan) return

        val file = File(absolutePath)
        if (!file.exists()) return

        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(inferMimeType(file.name)),
            null,
        )
    }

    private data class FileValidationResult(
        val valid: Boolean,
        val message: String? = null,
    )

    private fun validateDownloadedResult(localUri: String?, absolutePath: String?): FileValidationResult {
        val file = absolutePath?.let { File(it) }
        if (file != null) {
            if (!file.exists()) {
                return FileValidationResult(
                    valid = false,
                    message = "涓嬭浇鏂囦欢涓嶅瓨鍦紝宸叉寜澶辫触澶勭悊",
                )
            }
            if (file.length() <= 0L) {
                return FileValidationResult(
                    valid = false,
                    message = "涓嬭浇鏂囦欢涓虹┖锛屽凡鑷姩鍒犻櫎",
                )
            }
        }

        val header = readHeader(localUri, file, 512) ?: return FileValidationResult(valid = true)
        val normalizedTextHeader = header
            .toString(Charsets.UTF_8)
            .trimStart('\uFEFF')
            .trimStart()
            .lowercase()

        if (
            normalizedTextHeader.startsWith("#extm3u") ||
            normalizedTextHeader.contains("#ext-x-stream-inf") ||
            normalizedTextHeader.contains("#extinf")
        ) {
            return FileValidationResult(
                valid = false,
                message = "下载结果仍是 m3u8 播放列表，已按失败处理并删除文件",
            )
        }

        if (
            normalizedTextHeader.startsWith("<!doctype html") ||
            normalizedTextHeader.startsWith("<html") ||
            normalizedTextHeader.startsWith("{\"errors\"") ||
            normalizedTextHeader.startsWith("{\"error\"")
        ) {
            return FileValidationResult(
                valid = false,
                message = "下载结果不是视频文件（网页或接口错误内容），已按失败处理并删除文件",
            )
        }

        // 对明显的文本返回做兜底拦截，避免空文件/错误文件进入已完成。
        if (!looksLikeVideoContainer(header) && isMostlyText(header)) {
            return FileValidationResult(
                valid = false,
                message = "下载文件格式异常，疑似不可播放，已按失败处理并删除文件",
            )
        }

        return FileValidationResult(valid = true)
    }

    private fun readHeader(localUri: String?, file: File?, maxBytes: Int): ByteArray? {
        file?.takeIf { it.exists() }?.let { existingFile ->
            return runCatching {
                FileInputStream(existingFile).use { input -> readBytes(input, maxBytes) }
            }
                .onFailure { throwable ->
                    Log.w(TAG, "readHeader(file) failed: path=${existingFile.absolutePath}", throwable)
                }
                .getOrNull()
        }

        if (!localUri.isNullOrBlank() && localUri.startsWith("content://", ignoreCase = true)) {
            val uri = Uri.parse(localUri)
            return runCatching {
                context.contentResolver.openInputStream(uri)?.use { input -> readBytes(input, maxBytes) }
            }
                .onFailure { throwable ->
                    Log.w(TAG, "readHeader(content) failed: uri=$localUri", throwable)
                }
                .getOrNull()
        }

        return null
    }

    private fun readBytes(input: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        val read = input.read(buffer)
        if (read <= 0) return ByteArray(0)
        return buffer.copyOf(read)
    }

    private fun looksLikeVideoContainer(header: ByteArray): Boolean {
        if (header.isEmpty()) return false
        val isMp4Family = header.size >= 8 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()
        val isEbml = header.size >= 4 &&
            header[0] == 0x1A.toByte() &&
            header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() &&
            header[3] == 0xA3.toByte()
        val isMpegTs = header[0] == 0x47.toByte()
        val isFlv = header.size >= 3 &&
            header[0] == 'F'.code.toByte() &&
            header[1] == 'L'.code.toByte() &&
            header[2] == 'V'.code.toByte()
        return isMp4Family || isEbml || isMpegTs || isFlv
    }

    private fun isMostlyText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        var printableCount = 0
        bytes.forEach { b ->
            val value = b.toInt() and 0xFF
            if (value == 0x09 || value == 0x0A || value == 0x0D || (value in 0x20..0x7E)) {
                printableCount++
            }
        }
        return printableCount.toDouble() / bytes.size.toDouble() >= 0.9
    }

    private fun deleteInvalidResult(localUri: String?, absolutePath: String?) {
        absolutePath
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->
                runCatching { File(path).delete() }
                    .onFailure { throwable ->
                        Log.w(TAG, "deleteInvalidResult(file) failed: path=$path", throwable)
                    }
            }

        if (!localUri.isNullOrBlank() && localUri.startsWith("content://", ignoreCase = true)) {
            runCatching {
                context.contentResolver.delete(Uri.parse(localUri), null, null)
            }
                .onFailure { throwable ->
                    Log.w(TAG, "deleteInvalidResult(content) failed: uri=$localUri", throwable)
                }
        }
    }

    private fun validateBeforeEnqueue(url: String): String? {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=0-1023")
            .header("Accept", "*/*")
        buildHeaders(url).forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val response = runCatching { preflightClient.newCall(requestBuilder.build()).execute() }
            .onFailure { throwable ->
                Log.w(TAG, "preflight request failed, url=$url", throwable)
            }
            .getOrNull()
            ?: return null

        response.use { res ->
            if (!res.isSuccessful) {
                return "下载预检请求失败，HTTP ${res.code}"
            }

            val contentType = res.header("Content-Type").orEmpty().lowercase()
            if (
                contentType.contains("application/vnd.apple.mpegurl") ||
                contentType.contains("application/x-mpegurl") ||
                contentType.contains("text/html") ||
                contentType.contains("application/json")
            ) {
                return "该链接返回非视频内容类型：${contentType}"
            }

            val header = res.body?.byteStream()?.use { input -> readBytes(input, 512) } ?: ByteArray(0)
            if (header.isNotEmpty()) {
                val textHeader = header
                    .toString(Charsets.UTF_8)
                    .trimStart('\uFEFF')
                    .trimStart()
                    .lowercase()
                if (
                    textHeader.startsWith("#extm3u") ||
                    textHeader.startsWith("<!doctype html") ||
                    textHeader.startsWith("<html") ||
                    textHeader.startsWith("{\"errors\"") ||
                    textHeader.startsWith("{\"error\"")
                ) {
                    return "璇ヤ笅杞介€夐」杩斿洖鐨勪笉鏄彲鐩存帴鎾斁鐨勮棰戯紝璇锋洿鎹㈠叾浠栭€夐」"
                }
            }
        }

        return null
    }

    private fun deleteFileQuietly(path: String?) {
        path?.takeIf { it.isNotBlank() }?.let {
            runCatching { File(it).delete() }
                .onFailure { throwable -> Log.w(TAG, "delete file failed: path=$it", throwable) }
        }
    }

    private data class InternalM3u8Task(
        @Volatile var state: DownloadProgressState,
        @Volatile var progress: Int?,
        @Volatile var saveUri: String?,
        @Volatile var errorMessage: String?,
        val absolutePath: String,
        val sourceUrl: String,
        @Volatile var job: kotlinx.coroutines.Job?,
    )

    private data class VariantPlaylist(
        val url: String,
        val bandwidth: Int,
    )
}



