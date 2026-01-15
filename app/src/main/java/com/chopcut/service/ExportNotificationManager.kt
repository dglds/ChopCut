package com.chopcut.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.chopcut.MainActivity
import com.chopcut.R

/**
 * Gerenciador de notificações para o ExportForegroundService
 */
class ExportNotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "chopcut_export_channel"
        private const val CHANNEL_NAME = "Exportação de Vídeo"
        private const val NOTIFICATION_ID = 1001

        // Ações da notificação
        private const val ACTION_CANCEL = "com.chopcut.ACTION_CANCEL_EXPORT"
        const val ACTION_SHARE = "com.chopcut.ACTION_SHARE"
        const val ACTION_OPEN = "com.chopcut.ACTION_OPEN"

        // Extras para passar dados nas ações
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_NAME = "video_name"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    /**
     * Cria o canal de notificação (necessário para Android O+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificações de progresso de exportação de vídeo"
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Cria a notificação inicial de exportação
     */
    fun createExportNotification(videoName: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = Intent(context, ExportForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            context,
            0,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Exportando vídeo")
            .setContentText(videoName)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                cancelPendingIntent
            )
            .setProgress(100, 0, false)
            .build()
    }

    /**
     * Atualiza o progresso da notificação
     */
    fun updateProgress(progress: Int, videoName: String? = null) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Exportando vídeo")
            .setContentText(videoName ?: "${progress}%")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Mostra notificação de progresso indeterminado
     */
    fun showIndeterminateProgress(videoName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Exportando vídeo")
            .setContentText(videoName)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Mostra notificação de sucesso
     */
    fun showSuccess(videoName: String, videoUri: Uri? = null) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Exportação concluída")
            .setContentText(videoName)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // Adicionar botões de ação se tiver URI do vídeo
        videoUri?.let { uri ->
            // Botão de compartilhar
            val shareIntent = Intent(ACTION_SHARE).apply {
                putExtra(EXTRA_VIDEO_URI, uri.toString())
                putExtra(EXTRA_VIDEO_NAME, videoName)
            }
            val sharePendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                shareIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                android.R.drawable.ic_menu_share,
                "Compartilhar",
                sharePendingIntent
            )

            // Botão de abrir
            val openIntent = Intent(ACTION_OPEN).apply {
                putExtra(EXTRA_VIDEO_URI, uri.toString())
                putExtra(EXTRA_VIDEO_NAME, videoName)
                setDataAndType(uri, "video/mp4")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                android.R.drawable.ic_menu_view,
                "Abrir",
                openPendingIntent
            )
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Mostra notificação de erro
     */
    fun showError(error: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Erro na exportação")
            .setContentText(error)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Cancela a notificação
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * Retorna o ID da notificação
     */
    fun getNotificationId(): Int = NOTIFICATION_ID
}
