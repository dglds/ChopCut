package com.chopcut.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * Gerenciador de compartilhamento de vídeos exportados.
 *
 * Fornece métodos para compartilhar vídeos para outros apps,
 * incluindo Instagram, TikTok, YouTube e apps genéricos.
 */
class ShareManager(private val context: Context) {

    companion object {
        private const val FILE_PROVIDER_AUTHORITY = "com.chopcut.fileprovider"
    }

    /**
     * Resultado de uma tentativa de compartilhamento
     */
    sealed class ShareResult {
        data object Success : ShareResult()
        data class Error(val message: String) : ShareResult()
        data object NoAppAvailable : ShareResult()
    }

    /**
     * Compartilha um vídeo usando um intent genérico ACTION_SEND.
     * O usuário poderá escolher qual app usar para compartilhar.
     *
     * @param videoUri URI do vídeo a ser compartilhado
     * @param text Texto opcional para acompanhar o compartilhamento
     */
    fun shareVideo(videoUri: Uri, text: String? = null): ShareResult {
        return try {
            val contentUri = getContentUri(videoUri)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                text?.let {
                    putExtra(Intent.EXTRA_TEXT, it)
                }
            }

            // Criar chooser para usuário escolher o app
            val chooser = Intent.createChooser(shareIntent, "Compartilhar vídeo")

            // Verificar se há apps disponíveis
            if (shareIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooser)
                ShareResult.Success
            } else {
                ShareResult.NoAppAvailable
            }
        } catch (e: Exception) {
            ShareResult.Error(e.message ?: "Erro ao compartilhar")
        }
    }

    /**
     * Tenta compartilhar diretamente para um app específico.
     *
     * @param videoUri URI do vídeo a ser compartilhado
     * @param packageName Package name do app destino
     * @param text Texto opcional para acompanhar o compartilhamento
     */
    fun shareToApp(
        videoUri: Uri,
        packageName: String,
        text: String? = null
    ): ShareResult {
        return try {
            val contentUri = getContentUri(videoUri)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                setPackage(packageName)
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                text?.let {
                    putExtra(Intent.EXTRA_TEXT, it)
                }
            }

            // Verificar se o app está instalado
            if (shareIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(shareIntent)
                ShareResult.Success
            } else {
                ShareResult.NoAppAvailable
            }
        } catch (e: Exception) {
            ShareResult.Error(e.message ?: "Erro ao compartilhar")
        }
    }

    /**
     * Compartilha para Instagram (Stories ou Feed, dependendo da versão)
     */
    fun shareToInstagram(videoUri: Uri): ShareResult {
        return try {
            val contentUri = getContentUri(videoUri)

            // Tentar usar Intent específico do Instagram
            val instagramIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                setPackage("com.instagram.android")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (instagramIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(instagramIntent)
                ShareResult.Success
            } else {
                // Fallback para chooser genérico
                shareVideo(videoUri)
            }
        } catch (e: Exception) {
            ShareResult.Error(e.message ?: "Erro ao compartilhar")
        }
    }

    /**
     * Compartilha para TikTok
     */
    fun shareToTikTok(videoUri: Uri): ShareResult {
        return try {
            val contentUri = getContentUri(videoUri)

            val tiktokIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                setPackage("com.zhiliaoapp.musically")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (tiktokIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(tiktokIntent)
                ShareResult.Success
            } else {
                // Fallback para chooser genérico
                shareVideo(videoUri)
            }
        } catch (e: Exception) {
            ShareResult.Error(e.message ?: "Erro ao compartilhar")
        }
    }

    /**
     * Compartilha para YouTube (YouTube Shorts)
     */
    fun shareToYouTube(videoUri: Uri): ShareResult {
        return try {
            val contentUri = getContentUri(videoUri)

            val youtubeIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                setPackage("com.google.android.youtube")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (youtubeIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(youtubeIntent)
                ShareResult.Success
            } else {
                // Fallback para chooser genérico
                shareVideo(videoUri)
            }
        } catch (e: Exception) {
            ShareResult.Error(e.message ?: "Erro ao compartilhar")
        }
    }

    /**
     * Compartilha para WhatsApp
     */
    fun shareToWhatsApp(videoUri: Uri): ShareResult {
        return try {
            val contentUri = getContentUri(videoUri)

            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (whatsappIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(whatsappIntent)
                ShareResult.Success
            } else {
                // Tentar WhatsApp Business
                val whatsappBusinessIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    setPackage("com.whatsapp.w4b")
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                if (whatsappBusinessIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(whatsappBusinessIntent)
                    ShareResult.Success
                } else {
                    // Fallback para chooser genérico
                    shareVideo(videoUri)
                }
            }
        } catch (e: Exception) {
            ShareResult.Error(e.message ?: "Erro ao compartilhar")
        }
    }

    /**
     * Abre o vídeo em um player externo.
     *
     * @param videoUri URI do vídeo a ser aberto
     */
    fun openInPlayer(videoUri: Uri): ShareResult {
        return try {
            val contentUri = getContentUri(videoUri)

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (viewIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(viewIntent)
                ShareResult.Success
            } else {
                ShareResult.NoAppAvailable
            }
        } catch (e: Exception) {
            ShareResult.Error(e.message ?: "Erro ao abrir vídeo")
        }
    }

    /**
     * Retorna uma URI de conteúdo que pode ser compartilhada com outros apps.
     * Converte file:// URIs para content:// URIs usando FileProvider.
     */
    private fun getContentUri(uri: Uri): Uri {
        return when (uri.scheme) {
            "file" -> {
                // Converter file:// para content:// usando FileProvider
                val file = File(uri.path!!)
                FileProvider.getUriForFile(
                    context,
                    FILE_PROVIDER_AUTHORITY,
                    file
                )
            }
            "content" -> uri
            else -> uri
        }
    }

    /**
     * Verifica se um app específico está instalado.
     */
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Retorna a lista de apps de compartilhamento disponíveis.
     */
    fun getAvailableShareApps(): List<ShareApp> {
        val apps = mutableListOf<ShareApp>()

        if (isAppInstalled("com.instagram.android")) {
            apps.add(ShareApp.Instagram)
        }
        if (isAppInstalled("com.zhiliaoapp.musically")) {
            apps.add(ShareApp.TikTok)
        }
        if (isAppInstalled("com.google.android.youtube")) {
            apps.add(ShareApp.YouTube)
        }
        if (isAppInstalled("com.whatsapp")) {
            apps.add(ShareApp.WhatsApp)
        }
        if (isAppInstalled("com.twitter.android")) {
            apps.add(ShareApp.Twitter)
        }

        return apps
    }

    /**
     * Deleta um arquivo de vídeo exportado.
     */
    fun deleteExportedVideo(videoUri: Uri): Boolean {
        return try {
            when (videoUri.scheme) {
                "file" -> {
                    val file = File(videoUri.path!!)
                    file.delete()
                }
                "content" -> {
                    // Para content URIs, tentar deletar usando ContentResolver
                    context.contentResolver.delete(videoUri, null, null)
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Retorna informações sobre o arquivo de vídeo.
     */
    fun getVideoFileInfo(videoUri: Uri): VideoFileInfo? {
        return try {
            val file = when (videoUri.scheme) {
                "file" -> File(videoUri.path!!)
                "content" -> {
                    // Tentar obter caminho real do content URI
                    val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE)
                    context.contentResolver.query(videoUri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            val name = cursor.getString(nameIndex)
                            val size = cursor.getLong(sizeIndex)

                            // Obter caminho do cache para cálculo de tamanho
                            val cacheFile = File(context.cacheDir, name)
                            return VideoFileInfo(
                                name = name,
                                sizeBytes = size,
                                path = cacheFile.absolutePath
                            )
                        }
                    }
                    return null
                }
                else -> return null
            }

            VideoFileInfo(
                name = file.name,
                sizeBytes = file.length(),
                path = file.absolutePath
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Representa um app de compartilhamento suportado.
 */
sealed class ShareApp(val displayName: String, val packageName: String) {
    data object Instagram : ShareApp("Instagram", "com.instagram.android")
    data object TikTok : ShareApp("TikTok", "com.zhiliaoapp.musically")
    data object YouTube : ShareApp("YouTube", "com.google.android.youtube")
    data object WhatsApp : ShareApp("WhatsApp", "com.whatsapp")
    data object Twitter : ShareApp("X (Twitter)", "com.twitter.android")
    data object Generic : ShareApp("Outros", "")
}

/**
 * Informações sobre um arquivo de vídeo.
 */
data class VideoFileInfo(
    val name: String,
    val sizeBytes: Long,
    val path: String
) {
    val sizeFormatted: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            sizeBytes < 1024 * 1024 * 1024 -> "${sizeBytes / (1024 * 1024)} MB"
            else -> String.format("%.1f GB", sizeBytes / (1024.0 * 1024 * 1024))
        }
}
