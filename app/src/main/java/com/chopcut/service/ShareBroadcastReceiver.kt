package com.chopcut.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.chopcut.ui.share.ShareManager
import timber.log.Timber
import java.io.File

/**
 * BroadcastReceiver para lidar com ações de compartilhamento/abertura
 * a partir das notificações de exportação.
 */
class ShareBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val videoUriString = intent.getStringExtra(ExportNotificationManager.EXTRA_VIDEO_URI)
        val videoName = intent.getStringExtra(ExportNotificationManager.EXTRA_VIDEO_NAME) ?: "video.mp4"

        if (videoUriString == null) {
            Timber.e("Video URI is null in ShareBroadcastReceiver")
            return
        }

        val videoUri = Uri.parse(videoUriString)

        when (action) {
            ExportNotificationManager.ACTION_SHARE -> {
                handleShareAction(context, videoUri, videoName)
            }
            ExportNotificationManager.ACTION_OPEN -> {
                handleOpenAction(context, videoUri)
            }
        }
    }

    /**
     * Manipula a ação de compartilhar
     */
    private fun handleShareAction(context: Context, videoUri: Uri, videoName: String) {
        try {
            val contentUri = getContentUri(context, videoUri)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_TEXT, "Exportado com ChopCut")
            }

            val chooser = Intent.createChooser(shareIntent, "Compartilhar vídeo")

            if (shareIntent.resolveActivity(context.packageManager) != null) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                Timber.d("Share intent fired from notification")
            } else {
                Timber.w("No app available to share video")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error sharing video from notification")
        }
    }

    /**
     * Manipula a ação de abrir vídeo
     */
    private fun handleOpenAction(context: Context, videoUri: Uri) {
        try {
            val contentUri = getContentUri(context, videoUri)

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (viewIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(viewIntent)
                Timber.d("Open intent fired from notification")
            } else {
                Timber.w("No app available to open video")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error opening video from notification")
        }
    }

    /**
     * Retorna uma URI de conteúdo que pode ser compartilhada com outros apps.
     */
    private fun getContentUri(context: Context, uri: Uri): Uri {
        return when (uri.scheme) {
            "file" -> {
                val file = File(uri.path!!)
                FileProvider.getUriForFile(
                    context,
                    "com.chopcut.fileprovider",
                    file
                )
            }
            "content" -> uri
            else -> uri
        }
    }
}
