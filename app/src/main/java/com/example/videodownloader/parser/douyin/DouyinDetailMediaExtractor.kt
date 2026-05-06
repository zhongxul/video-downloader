package com.example.videodownloader.parser.douyin

import com.example.videodownloader.core.text.AppText
import com.example.videodownloader.core.text.DefaultAppText
import com.example.videodownloader.domain.model.ParsedVideoInfo
import com.example.videodownloader.domain.model.VideoFormat
import org.json.JSONArray
import org.json.JSONObject

class DouyinDetailMediaExtractor(
    private val appText: AppText = DefaultAppText,
) {
    fun extract(root: JSONObject): ParsedVideoInfo {
        val detail = findFirstObject(root, "aweme_detail") ?: root
        val title = findFirstString(detail, setOf("desc", "title", "share_title"))
            ?.let(::sanitizeTitle)
            ?.takeIf { it.isNotBlank() }
            ?: appText.douyinGalleryTitle()

        val workMedia = collectWorkMedia(detail)

        val formats = workMedia.candidates
            .asSequence()
            .map { it.copy(url = normalizeUrl(it.url)) }
            .filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
            .filterNot { isAudioUrl(it.url) }
            .filter { isImageUrl(it.url) || isVideoUrl(it.url) }
            .filterNot { isLowPriorityLogoVideo(it.url) }
            .distinctBy { canonicalMediaKey(it.url) }
            .mapIndexed { index, media ->
                val isVideo = isVideoUrl(media.url)
                VideoFormat(
                    formatId = "douyin_detail_$index",
                    resolution = if (isVideo) appText.gifLabel(index + 1) else appText.imageLabel(index + 1),
                    ext = inferExt(media.url, if (isVideo) "mp4" else "jpg"),
                    sizeText = null,
                    downloadUrl = media.url,
                    downloadable = true,
                    thumbnailUrl = media.thumbnailUrl?.let(::normalizeUrl),
                    pairedImageUrl = if (isVideo) media.thumbnailUrl?.let(::normalizeUrl) else null,
                )
            }
            .toList()

        if (formats.isEmpty()) {
            throw IllegalArgumentException(appText.parseNoDownloadableVideo())
        }

        return ParsedVideoInfo(
            title = title,
            coverUrl = workMedia.coverUrl,
            formats = formats,
        )
    }

    private fun collectWorkMedia(detail: JSONObject): WorkMedia {
        val output = mutableListOf<MediaCandidate>()
        var coverUrl: String? = null
        val images = detail.optJSONArray("images")
        if (images != null) {
            for (index in 0 until images.length()) {
                val image = images.optJSONObject(index) ?: continue
                val imageUrls = collectUrlsFromKnownContainers(image, imageUrlContainers)
                    .filter(::isImageUrl)
                val imageUrl = imageUrls.lastOrNull()
                if (coverUrl == null) {
                    coverUrl = imageUrls.firstOrNull() ?: imageUrl
                }

                val videoUrl = image.optJSONObject("video")
                    ?.let { video -> collectUrlsFromKnownContainers(video, videoUrlContainers) }
                    .orEmpty()
                    .filter(::isVideoUrl)
                    .filterNot(::isLowPriorityLogoVideo)
                    .let(::pickPreferredVideoUrl)

                if (videoUrl != null) {
                    output += MediaCandidate(MediaKind.Video, videoUrl, imageUrl)
                } else if (imageUrl != null) {
                    output += MediaCandidate(MediaKind.Image, imageUrl, imageUrl)
                }
            }
        }

        detail.optJSONObject("video")?.let { video ->
            collectUrlsFromKnownContainers(video, videoUrlContainers)
                .filter(::isVideoUrl)
                .filterNot(::isLowPriorityLogoVideo)
                .let(::pickPreferredVideoUrl)
                ?.let { output += MediaCandidate(MediaKind.Video, it, coverUrl) }
        }

        return WorkMedia(
            coverUrl = coverUrl?.let(::normalizeUrl),
            candidates = output,
        )
    }

    private fun collectUrlsFromKnownContainers(root: JSONObject, names: Set<String>): List<String> {
        val output = linkedSetOf<String>()
        collectUrlsFromNamedContainers(root, names, output)
        return output.toList()
    }

    private fun collectUrlsFromNamedContainers(value: Any?, names: Set<String>, output: MutableSet<String>) {
        when (value) {
            is JSONObject -> {
                val hasDirectUrl = value.optString("url").isNotBlank()
                if (hasDirectUrl && value.keys().asSequence().any { it in names }) {
                    value.optString("url").takeIf { it.isNotBlank() }?.let(output::add)
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = value.opt(key)
                    if (key in names) {
                        collectRawUrls(child, output)
                    } else {
                        collectUrlsFromNamedContainers(child, names, output)
                    }
                }
            }

            is JSONArray -> {
                for (i in 0 until value.length()) {
                    collectUrlsFromNamedContainers(value.opt(i), names, output)
                }
            }
        }
    }

    private fun collectRawUrls(value: Any?, output: MutableSet<String>) {
        when (value) {
            is JSONObject -> {
                value.optString("url").takeIf { it.isNotBlank() }?.let(output::add)
                val keys = value.keys()
                while (keys.hasNext()) {
                    collectRawUrls(value.opt(keys.next()), output)
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    collectRawUrls(value.opt(i), output)
                }
            }
            is String -> if (value.startsWith("http://") || value.startsWith("https://")) {
                output += value
            }
        }
    }

    private fun findFirstString(value: Any?, names: Set<String>): String? {
        when (value) {
            is JSONObject -> {
                names.forEach { name ->
                    value.optString(name).takeIf { it.isNotBlank() }?.let { return it }
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    findFirstString(value.opt(keys.next()), names)?.let { return it }
                }
            }

            is JSONArray -> {
                for (i in 0 until value.length()) {
                    findFirstString(value.opt(i), names)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findFirstObject(value: Any?, name: String): JSONObject? {
        when (value) {
            is JSONObject -> {
                value.optJSONObject(name)?.let { return it }
                val keys = value.keys()
                while (keys.hasNext()) {
                    findFirstObject(value.opt(keys.next()), name)?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    findFirstObject(value.opt(i), name)?.let { return it }
                }
            }
        }
        return null
    }

    private fun normalizeUrl(url: String): String {
        return url
            .trim()
            .replace("\\/", "/")
            .replace("&amp;", "&")
    }

    private fun isImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("douyinpic.com") ||
            lower.contains("aweme_images") ||
            lower.contains("sc=image") ||
            lower.substringBefore('?').endsWith(".jpg") ||
            lower.substringBefore('?').endsWith(".jpeg") ||
            lower.substringBefore('?').endsWith(".png") ||
            lower.substringBefore('?').endsWith(".webp") ||
            lower.substringBefore('?').endsWith(".heic")
    }

    private fun isVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("douyinvod.com") ||
            lower.contains("mime_type=video") ||
            lower.contains("/video/tos/") ||
            lower.substringBefore('?').endsWith(".mp4") ||
            lower.substringBefore('?').endsWith(".m3u8")
    }

    private fun isAudioUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("ies-music") ||
            lower.contains("mime_type=audio") ||
            lower.substringBefore('?').endsWith(".mp3") ||
            lower.substringBefore('?').endsWith(".m4a")
    }

    private fun isLowPriorityLogoVideo(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("logo_type=") || lower.contains("/mps/logo/")
    }

    private fun canonicalMediaKey(url: String): String {
        val clean = url.substringBefore('#').substringBefore('?')
        return clean
            .replace("https://v26-web.douyinvod.com/", "https://douyinvod/")
            .replace("https://v11-weba.douyinvod.com/", "https://douyinvod/")
    }

    private fun pickPreferredVideoUrl(urls: List<String>): String? {
        val normalized = urls.map(::normalizeUrl).distinct()
        if (normalized.isEmpty()) return null
        // 抖音动图通常同一资源返回两个顺序 CDN；实测第二个更符合 App 展示链路。
        return normalized.getOrNull(1) ?: normalized.first()
    }

    private fun inferExt(url: String, fallback: String): String {
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            clean.endsWith(".jpeg") -> "jpeg"
            clean.endsWith(".jpg") -> "jpg"
            clean.endsWith(".png") -> "png"
            clean.endsWith(".webp") -> "webp"
            clean.endsWith(".heic") -> "heic"
            clean.endsWith(".m3u8") -> "m3u8"
            clean.endsWith(".mp4") || url.contains("mime_type=video_mp4", ignoreCase = true) -> "mp4"
            else -> fallback
        }
    }

    private fun sanitizeTitle(raw: String): String {
        return raw.replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_").trim()
    }

    private data class MediaCandidate(
        val kind: MediaKind,
        val url: String,
        val thumbnailUrl: String?,
    )

    private data class WorkMedia(
        val coverUrl: String?,
        val candidates: List<MediaCandidate>,
    )

    private enum class MediaKind {
        Image,
        Video,
    }

    private companion object {
        val imageUrlContainers = setOf("url_list", "download_url_list")
        val videoUrlContainers = setOf("url_list", "download_url_list", "play_addr", "play_addr_lowbr")
    }
}
