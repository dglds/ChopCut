package com.chopcut.cache

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chopcut.data.thumbnail.ThumbnailCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes instrumentados para ThumbnailCache.
 *
 * Cada grupo de testes cobre um requisito de negócio do cache:
 *   1. Detectar se um vídeo já tem cache
 *   2. Armazenar thumbs
 *   3. Deletar thumbs de um vídeo
 *   4. Rastreamento para evitar cache errado
 *   5. Proximidade do limite de capacidade
 *   6. Tamanho total do cache
 *   7. Apagar cache com segurança
 *   8. Quantidade de vídeos usando cache
 *
 * Rodar:
 *   ./gradlew runTest -Ptarget=com.chopcut.cache.ThumbnailCacheTest
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailCacheTest {

    private lateinit var cache: ThumbnailCache

    @Before
    fun setUp() {
        cache = ThumbnailCache(maxSize = 10)
    }

    @After
    fun tearDown() {
        cache.clear()
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Cria um bitmap sólido leve para uso nos testes. */
    private fun bitmap(width: Int = 80, height: Int = 54): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

    private fun report(title: String, vararg lines: String) {
        val width = 58
        println("╔${"═".repeat(width)}╗")
        println("║ ${title.padEnd(width - 1)}║")
        println("╠${"═".repeat(width)}╣")
        lines.forEach { println("║  ${it.padEnd(width - 2)}║") }
        println("╚${"═".repeat(width)}╝")
    }

    // ─── 1. Deve saber se um vídeo já tem cache ───────────────────────────────

    @Test
    fun videoHasCache_whenAtLeastOneEntryExists() {
        val uri = "content://video/1"
        cache.put(uri, 0L, bitmap())

        val result = cache.containsVideo(uri)

        report(
            "1. Vídeo tem cache?",
            "URI: $uri",
            "containsVideo: $result"
        )
        assertTrue("Deve detectar que o vídeo tem cache", result)
    }

    @Test
    fun videoHasNoCache_whenCacheIsEmpty() {
        val result = cache.containsVideo("content://video/sem-cache")

        report(
            "1. Vídeo sem cache",
            "containsVideo (vazio): $result"
        )
        assertFalse("Não deve detectar cache inexistente", result)
    }

    @Test
    fun videoHasNoCache_afterItsEntryIsRemoved() {
        val uri = "content://video/2"
        cache.put(uri, 0L, bitmap())
        cache.removeVideo(uri)

        val result = cache.containsVideo(uri)

        report(
            "1. Vídeo sem cache após remoção",
            "containsVideo após removeVideo: $result"
        )
        assertFalse("Não deve encontrar cache de vídeo removido", result)
    }

    // ─── 2. Deve saber armazenar thumbs em cache ──────────────────────────────

    @Test
    fun storesThumbnailAndRetrievesIt() {
        val uri = "content://video/3"
        val bmp = bitmap()
        cache.put(uri, 1000L, bmp)

        val retrieved = cache.get(uri, 1000L)

        report(
            "2. Armazenar e recuperar thumb",
            "URI: $uri  posição: 1000ms",
            "Recuperado: ${retrieved?.width}x${retrieved?.height} ${retrieved?.config}"
        )
        assertNotNull("Thumb deve ser recuperada após put()", retrieved)
        assertEquals("Bitmap recuperado deve ser o mesmo", bmp, retrieved)
    }

    @Test
    fun storeMultiplePositionsForSameVideo() {
        val uri = "content://video/4"
        listOf(0L, 1000L, 2000L).forEach { pos -> cache.put(uri, pos, bitmap()) }

        val results = listOf(0L, 1000L, 2000L).map { cache.get(uri, it) }

        report(
            "2. Múltiplas posições do mesmo vídeo",
            "Posições: 0ms, 1000ms, 2000ms",
            "Recuperadas: ${results.count { it != null }}/3"
        )
        results.forEachIndexed { i, bmp ->
            assertNotNull("Posição ${i * 1000}ms deve estar em cache", bmp)
        }
    }

    @Test
    fun returnsNull_forPositionNotInCache() {
        val result = cache.get("content://video/5", 9999L)

        report(
            "2. Posição inexistente retorna null",
            "get(posição inexistente): $result"
        )
        assertNull("Deve retornar null para posição não cacheada", result)
    }

    // ─── 3. Deve saber deletar thumbs de um vídeo ────────────────────────────

    @Test
    fun removeVideoDeletesAllItsEntries() {
        val uri = "content://video/6"
        listOf(0L, 1000L, 2000L).forEach { pos -> cache.put(uri, pos, bitmap()) }

        val removed = cache.removeVideo(uri)

        report(
            "3. Deletar todas as thumbs de um vídeo",
            "Entradas inseridas: 3",
            "removeVideo retornou: $removed",
            "containsVideo após remoção: ${cache.containsVideo(uri)}"
        )
        assertEquals("Deve remover exatamente 3 entradas", 3, removed)
        assertFalse("Vídeo não deve mais ter cache", cache.containsVideo(uri))
    }

    @Test
    fun removeVideoDoesNotAffectOtherVideos() {
        val uriA = "content://video/7"
        val uriB = "content://video/8"
        cache.put(uriA, 0L, bitmap())
        cache.put(uriB, 0L, bitmap())

        cache.removeVideo(uriA)

        report(
            "3. Deletar vídeo A não afeta vídeo B",
            "Vídeo A removido — containsVideo(A): ${cache.containsVideo(uriA)}",
            "Vídeo B intacto  — containsVideo(B): ${cache.containsVideo(uriB)}"
        )
        assertFalse("Vídeo A deve ser removido", cache.containsVideo(uriA))
        assertTrue("Vídeo B deve permanecer no cache", cache.containsVideo(uriB))
    }

    // ─── 4. Rastreamento para evitar cache errado ────────────────────────────

    @Test
    fun trackedUris_containsAllInsertedVideos() {
        val uris = listOf("content://video/9", "content://video/10", "content://video/11")
        uris.forEach { cache.put(it, 0L, bitmap()) }

        val tracked = cache.getTrackedUris()

        report(
            "4. Rastrear URIs em cache",
            "Inseridos: ${uris.size} URIs distintos",
            "Rastreados: ${tracked.size} — $tracked"
        )
        assertEquals("Deve rastrear todos os URIs inseridos", uris.toSet(), tracked)
    }

    @Test
    fun doesNotReturnDataFromWrongUri() {
        val correctUri = "content://video/correct"
        val wrongUri = "content://video/wrong"
        cache.put(correctUri, 0L, bitmap())

        val fromWrong = cache.get(wrongUri, 0L)

        report(
            "4. Não retorna dado de URI errada",
            "URI correto tem cache: ${cache.containsVideo(correctUri)}",
            "get(URI errado, 0ms): $fromWrong"
        )
        assertNull("Não deve retornar dado de URI diferente", fromWrong)
    }

    @Test
    fun uriWithSimilarPrefixDoesNotCollide() {
        // "content://video/1" e "content://video/10" não devem colidir
        val uriA = "content://video/1"
        val uriB = "content://video/10"
        cache.put(uriA, 0L, bitmap())

        report(
            "4. URIs similares não colidem",
            "uriA='$uriA' inserido",
            "containsVideo(uriB='$uriB'): ${cache.containsVideo(uriB)}"
        )
        assertFalse("URI similar não deve colidir com URI existente", cache.containsVideo(uriB))
    }

    // ─── 5. Capacidade próxima do limite ────────────────────────────────────

    @Test
    fun isNearCapacity_whenAboveDefaultThreshold() {
        // maxSize=10, threshold=80% → alerta quando size >= 8
        repeat(8) { i -> cache.put("content://video/$i", 0L, bitmap()) }

        val near = cache.isNearCapacity()

        report(
            "5. Cache próximo do limite (80%)",
            "maxSize: 10  itens: ${cache.size()}",
            "isNearCapacity(80%): $near"
        )
        assertTrue("Deve reportar proximidade do limite com 8/10 itens", near)
    }

    @Test
    fun isNotNearCapacity_whenBelowThreshold() {
        repeat(3) { i -> cache.put("content://video/$i", 0L, bitmap()) }

        val near = cache.isNearCapacity()

        report(
            "5. Cache longe do limite",
            "maxSize: 10  itens: ${cache.size()}",
            "isNearCapacity(80%): $near"
        )
        assertFalse("Não deve alertar com 3/10 itens", near)
    }

    @Test
    fun isNearCapacity_respectsCustomThreshold() {
        repeat(5) { i -> cache.put("content://video/$i", 0L, bitmap()) }

        val near50 = cache.isNearCapacity(thresholdPercent = 50)
        val near60 = cache.isNearCapacity(thresholdPercent = 60)

        report(
            "5. Threshold customizado",
            "maxSize: 10  itens: 5",
            "isNearCapacity(50%): $near50",
            "isNearCapacity(60%): $near60"
        )
        assertTrue("Deve alertar com threshold 50% e 5/10 itens", near50)
        assertFalse("Não deve alertar com threshold 60% e 5/10 itens", near60)
    }

    // ─── 6. Tamanho total do cache ───────────────────────────────────────────

    @Test
    fun totalSizeBytes_matchesSumOfBitmapBytes() {
        val bmp = bitmap(80, 54) // RGB_565 = 2 bytes/px → 80 × 54 × 2 = 8640 bytes
        cache.put("content://video/size", 0L, bmp)

        val total = cache.totalSizeBytes()
        val expected = bmp.byteCount.toLong()

        report(
            "6. Tamanho total em bytes",
            "Bitmap: 80×54 RGB_565 = ${bmp.byteCount}B",
            "totalSizeBytes(): $total"
        )
        assertEquals("Total de bytes deve corresponder ao bitmap inserido", expected, total)
    }

    @Test
    fun totalSizeBytes_isZeroWhenCacheIsEmpty() {
        val total = cache.totalSizeBytes()

        report("6. Tamanho zero quando vazio", "totalSizeBytes(): $total")
        assertEquals("Cache vazio deve ter 0 bytes", 0L, total)
    }

    @Test
    fun totalSizeFormatted_returnsKbForSmallBitmaps() {
        cache.put("content://video/fmt", 0L, bitmap(80, 54)) // ~8KB

        val formatted = cache.totalSizeFormatted()

        report(
            "6. Tamanho formatado",
            "totalSizeFormatted(): $formatted"
        )
        assertTrue("Deve formatar em KB para bitmaps pequenos", formatted.endsWith("KB"))
    }

    // ─── 7. Apagar cache com segurança ───────────────────────────────────────

    @Test
    fun clearSafely_cacheIsEmptyAfterwards() {
        repeat(3) { i -> cache.put("content://video/$i", 0L, bitmap()) }

        cache.clearSafely()

        report(
            "7. Cache vazio após clearSafely()",
            "Itens antes: 3",
            "isEmpty após clearSafely: ${cache.size() == 0}"
        )
        assertTrue("Cache deve estar vazio após clearSafely()", cache.size() == 0)
    }

    @Test
    fun clearSafely_returnsStatsBeforeClearing() {
        repeat(3) { i -> cache.put("content://video/$i", 0L, bitmap()) }

        val statsBefore = cache.clearSafely()

        report(
            "7. clearSafely() retorna stats anteriores",
            "statsBefore.size: ${statsBefore.size}",
            "cache.size() após clear: ${cache.size()}"
        )
        assertEquals("Stats devem refletir 3 itens antes da limpeza", 3, statsBefore.size)
        assertEquals("Cache deve estar vazio após a limpeza", 0, cache.size())
    }

    @Test
    fun clearSafely_callbackIsInvokedBeforeClearing() {
        cache.put("content://video/cb", 0L, bitmap())
        var capturedSize = -1

        cache.clearSafely { stats -> capturedSize = stats.size }

        report(
            "7. Callback chamado antes de limpar",
            "Tamanho capturado no callback: $capturedSize",
            "cache.size() após clear: ${cache.size()}"
        )
        assertEquals("Callback deve receber o tamanho antes de apagar", 1, capturedSize)
        assertTrue("Cache deve estar vazio após clearSafely", cache.size() == 0)
    }

    // ─── 8. Quantos vídeos estão usando cache ────────────────────────────────

    @Test
    fun videoCount_returnsCorrectNumberOfDistinctVideos() {
        cache.put("content://video/A", 0L, bitmap())
        cache.put("content://video/A", 1000L, bitmap()) // mesma URI, segunda posição
        cache.put("content://video/B", 0L, bitmap())
        cache.put("content://video/C", 0L, bitmap())

        val count = cache.getTrackedUris().size

        report(
            "8. Contagem de vídeos distintos",
            "URIs inseridas: A (2 posições), B, C",
            "videoCount(): $count"
        )
        assertEquals("Deve contar 3 vídeos distintos", 3, count)
    }

    @Test
    fun videoCount_isZeroWhenCacheIsEmpty() {
        val count = cache.getTrackedUris().size

        report("8. Contagem zero quando vazio", "videoCount(): $count")
        assertEquals("Cache vazio deve ter 0 vídeos", 0, count)
    }

    @Test
    fun videoCount_decreasesAfterRemovingAVideo() {
        cache.put("content://video/X", 0L, bitmap())
        cache.put("content://video/Y", 0L, bitmap())

        cache.removeVideo("content://video/X")
        val count = cache.getTrackedUris().size

        report(
            "8. Contagem reduz após remover vídeo",
            "Inseridos: X, Y — removido: X",
            "videoCount() após remoção: $count"
        )
        assertEquals("Deve ter 1 vídeo após remover um", 1, count)
    }
}
