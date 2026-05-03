package com.example.videodownloader.ui.screen.history

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.videodownloader.R
import com.example.videodownloader.domain.model.DownloadTask
import java.io.File

data class LocalVideoInfo(
    val path: String,
    val sizeBytes: Long,
    val exists: Boolean,
)

object MediaActionHelper {
    fun openVideo(context: Context, task: DownloadTask): String? {
        return openMedia(context, task.saveUri, "video/*")
    }

    fun openMedia(context: Context, saveUri: String?, mimeType: String): String? {
        val uri = resolveShareUri(context, saveUri) ?: return context.getString(R.string.media_error_not_found)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            null
        }.getOrElse {
            if (it is ActivityNotFoundException) {
                context.getString(R.string.media_error_no_player)
            } else {
                context.getString(R.string.media_error_open_failed)
            }
        }
    }

    fun shareVideos(context: Context, tasks: List<DownloadTask>): String? {
        val uris = tasks.mapNotNull { resolveShareUri(context, it.saveUri) }
        if (uris.isEmpty()) return context.getString(R.string.media_error_no_shareable)

        val intent = Intent(
            if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE,
        ).apply {
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.media_share_chooser)))
            null
        }.getOrElse { context.getString(R.string.media_error_share_failed) }
    }

    fun readLocalVideoInfo(saveUri: String?): LocalVideoInfo? {
        val path = saveUri
            ?.takeIf { it.startsWith("file://", ignoreCase = true) }
            ?.removePrefix("file://")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val file = File(path)
        return LocalVideoInfo(
            path = path,
            sizeBytes = if (file.exists()) file.length() else 0L,
            exists = file.exists(),
        )
    }

    private fun resolveShareUri(context: Context, saveUri: String?): Uri? {
        if (saveUri.isNullOrBlank()) return null
        if (saveUri.startsWith("content://", ignoreCase = true)) {
            return Uri.parse(saveUri)
        }
        val path = saveUri
            .takeIf { it.startsWith("file://", ignoreCase = true) }
            ?.removePrefix("file://")
            ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
}
