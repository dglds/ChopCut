package com.chopcut

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.runBlocking

/**
 * Localiza um vídeo real no device para os testes instrumentados de compactação.
 *
 * Os vídeos vivem em `Movies/ChopCut/teste/`. A consulta é feita via **MediaStore**
 * (não por path), o que exige apenas a permissão de runtime `READ_MEDIA_VIDEO` —
 * concedida nos testes por `GrantPermissionRule`. Evitamos de propósito o
 * `MANAGE_EXTERNAL_STORAGE`: concedê-lo reinicia o processo do app e derruba o teste.
 *
 * A escolha é **aleatória** entre os candidatos que passam no filtro, para que o
 * teste não dependa de um arquivo específico. Quando não houver vídeo apto, os
 * testes devem ser *ignorados* (Assume), não falhar.
 */
object DeviceVideoProvider {

    /** Limite de duração: só vídeos com menos de 10 minutos entram nos testes. */
    const val MAX_DURATION_MS = 10 * 60 * 1000L

    /** Caminho relativo da pasta de testes, como o MediaStore o indexa. */
    const val RELATIVE_PATH = "ChopCut/teste/"

    /** Lista os vídeos da pasta de teste como pares (uri de conteúdo, duração em ms). */
    private fun listVideos(context: Context): List<Pair<Uri, Long>> {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DURATION)
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%$RELATIVE_PATH%")

        val out = mutableListOf<Pair<Uri, Long>>()
        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                out += uri to cursor.getLong(durCol)
            }
        }
        return out
    }

    /**
     * Escolhe aleatoriamente um vídeo da pasta de testes com duração `< 10min`
     * que satisfaça [viable] (ex.: ser compactável num dado nível).
     *
     * @return o [VideoInfo] do vídeo escolhido, ou `null` se nenhum candidato servir.
     */
    fun pickRandom(context: Context, viable: (VideoInfo) -> Boolean): VideoInfo? = runBlocking {
        val repo = VideoRepository(context)
        val candidates = listVideos(context)
            .filter { (_, durationMs) -> durationMs in 1 until MAX_DURATION_MS }
            .shuffled()
        for ((uri, _) in candidates) {
            val info = repo.getMetadata(uri) ?: continue
            if (viable(info)) return@runBlocking info
        }
        null
    }
}
