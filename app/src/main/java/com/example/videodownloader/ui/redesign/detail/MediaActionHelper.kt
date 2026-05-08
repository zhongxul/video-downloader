package com.example.videodownloader.ui.redesign.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.videodownloader.R
import java.io.File

object MediaActionHelper {
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
