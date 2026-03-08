package com.chopcut.cache.corruption

import android.graphics.BitmapFactory
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chopcut.MainActivity
import com.chopcut.data.thumbnail.ThumbnailCacheManager
import com.chopcut.data.thumbnail.ThumbnailStripManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class CacheCorruptionTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private lateinit var cacheDir: File
    private lateinit var stripManager: ThumbnailStripManager

    @Before
    fun setUp() {
        ThumbnailCacheManager.clearMemoryCache()
        ThumbnailCacheManager.clearAll(testContext)

        cacheDir = File(testContext.cacheDir, "thumbnail_strips")
        cacheDir.mkdirs()

        stripManager = ThumbnailStripManager(
            context = testContext,
            thumbWidth = 80,
            thumbHeight = 54,
            thumbsPerStrip = 5,
            adaptiveStrips = false
        )
    }

    @After
    fun tearDown() {
        ThumbnailCacheManager.clearMemoryCache()
        ThumbnailCacheManager.clearAll(testContext)
    }

    private fun copyTestVideo(): File {
        val file = File(activityRule.scenario.getTempDir(), "test_corruption.mp4")
        testContext.assets.open("sample.mp4").use { it.copyTo(file.outputStream()) }
        return file
    }

    @Test
    fun corruptedCacheFile_isDeletedAndReextracted() {
        val videoFile = copyTestVideo()
        val uri = android.net.Uri.fromFile(videoFile)
        val segmentIndex = 0
        val durationMs = 3_000L


        val cacheKey = generateCacheKey(uri, segmentIndex, videoFile)
        val cacheFile = File(cacheDir, cacheKey)

        runBlocking {
            stripManager.extractSegment(uri, segmentIndex, durationMs)?.let { strip ->
                stripManager.saveToCache(uri, segmentIndex, strip)
            }
        }

        assert(cacheFile.exists()) { "Cache file deve existir após extração" }
        val originalSize = cacheFile.length()

        RandomAccessFile(cacheFile, "rw").use { raf ->
            raf.setLength(originalSize / 2)
        }


        runBlocking {
            val strip = stripManager.loadFromCache(uri, segmentIndex)

            assert(strip == null) { "Strip corrompido deve retornar null" }
            assert(!cacheFile.exists()) { "Arquivo corrompido deve ser deletado" }

            val reextracted = stripManager.extractSegment(uri, segmentIndex, durationMs)
            assert(reextracted != null) { "Reextração deve ter sucesso" }
        }

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    ARQUIVO CORROMPIDO — RESULTADOS                       ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Arquivo original      : $originalSize bytes              ║")
        println("║  Após corrupção       : ${originalSize / 2} bytes         ║")
        println("║  Arquivo deletado      : ✓                                ║")
        println("║  Reextração           : ✓ Sucesso                         ║")
        println("╚══════════════════════════════════════════════════════════╝")
    }

    @Test
    fun truncatedFile_headerCorrupted_isDeleted() {
        val videoFile = copyTestVideo()
        val uri = android.net.Uri.fromFile(videoFile)
        val segmentIndex = 1
        val durationMs = 3_000L


        val cacheKey = generateCacheKey(uri, segmentIndex, videoFile)
        val cacheFile = File(cacheDir, cacheKey)

        runBlocking {
            stripManager.extractSegment(uri, segmentIndex, durationMs)?.let { strip ->
                stripManager.saveToCache(uri, segmentIndex, strip)
            }
        }

        RandomAccessFile(cacheFile, "rw").use { raf ->
            val originalBytes = ByteArray(100)
            raf.read(originalBytes)
            raf.seek(0)
            raf.write(originalBytes.reversedArray())
        }


        runBlocking {
            val strip = stripManager.loadFromCache(uri, segmentIndex)

            assert(strip == null) { "Strip com header corrompido deve retornar null" }
            assert(!cacheFile.exists()) { "Arquivo corrompido deve ser deletado" }
        }

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    HEADER CORROMPIDO — RESULTADOS                        ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Header corrompido    : Bytes invertidos                 ║")
        println("║  Leitura              : Retornou null                     ║")
        println("║  Arquivo deletado      : ✓                                ║")
        println("╚══════════════════════════════════════════════════════════╝")
    }

    @Test
    fun emptyCacheFile_isDeleted() {
        val videoFile = copyTestVideo()
        val uri = android.net.Uri.fromFile(videoFile)
        val segmentIndex = 2
        val durationMs = 3_000L


        val cacheKey = generateCacheKey(uri, segmentIndex, videoFile)
        val cacheFile = File(cacheDir, cacheKey)

        runBlocking {
            stripManager.extractSegment(uri, segmentIndex, durationMs)?.let { strip ->
                stripManager.saveToCache(uri, segmentIndex, strip)
            }
        }

        cacheFile.writeBytes(ByteArray(0))


        runBlocking {
            val strip = stripManager.loadFromCache(uri, segmentIndex)

            assert(strip == null) { "Strip vazio deve retornar null" }
            assert(!cacheFile.exists()) { "Arquivo vazio deve ser deletado" }
        }

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    ARQUIVO VAZIO — RESULTADOS                             ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Tamanho              : 0 bytes                           ║")
        println("║  Leitura              : Retornou null                     ║")
        println("║  Arquivo deletado      : ✓                                ║")
        println("╚══════════════════════════════════════════════════════════╝")
    }

    @Test
    fun invalidBitmapDimensions_handledGracefully() {
        val videoFile = copyTestVideo()
        val uri = android.net.Uri.fromFile(videoFile)
        val segmentIndex = 3
        val durationMs = 3_000L


        val cacheKey = generateCacheKey(uri, segmentIndex, videoFile)
        val cacheFile = File(cacheDir, cacheKey)

        runBlocking {
            stripManager.extractSegment(uri, segmentIndex, durationMs)?.let { strip ->
                stripManager.saveToCache(uri, segmentIndex, strip)
            }
        }

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(cacheFile.absolutePath, options)

        val fakeHeader = createFakeWebPHeader(0, 0)
        RandomAccessFile(cacheFile, "rw").use { raf ->
            raf.seek(0)
            raf.write(fakeHeader)
        }


        runBlocking {
            val strip = stripManager.loadFromCache(uri, segmentIndex)

            assert(strip == null) { "Strip com dimensões inválidas deve retornar null" }
            assert(!cacheFile.exists()) { "Arquivo deve ser deletado" }
        }

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    DIMENSÕES INVÁLIDAS — RESULTADOS                      ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Dimensões modificadas: 0x0                              ║")
        println("║  Leitura              : Retornou null                     ║")
        println("║  Arquivo deletado      : ✓                                ║")
        println("╚══════════════════════════════════════════════════════════╝")
    }

    @Test
    fun multipleCorruptedFiles_allDeleted() {
        val videoFile = copyTestVideo()
        val uri = android.net.Uri.fromFile(videoFile)
        val segments = listOf(0, 1, 2, 3, 4)
        val durationMs = 15_000L


        runBlocking {
            segments.forEach { segIdx ->
                stripManager.extractSegment(uri, segIdx, durationMs / segments.size)?.let { strip ->
                    stripManager.saveToCache(uri, segIdx, strip)
                }
            }
        }

        val corruptedFiles = mutableListOf<File>()
        segments.forEach { segIdx ->
            val cacheKey = generateCacheKey(uri, segIdx, videoFile)
            val cacheFile = File(cacheDir, cacheKey)

            RandomAccessFile(cacheFile, "rw").use { raf ->
                raf.setLength(cacheFile.length() / 2)
            }
            corruptedFiles.add(cacheFile)
        }


        runBlocking {
            segments.forEach { segIdx ->
                stripManager.loadFromCache(uri, segIdx)
            }
        }

        val remainingCorrupted = corruptedFiles.count { it.exists() }
        val allDeleted = remainingCorrupted == 0

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    MÚLTIPLOS ARQUIVOS CORROMPIDOS — RESULTADOS          ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Arquivos corrompidos: ${corruptedFiles.size}           ║")
        println("║  Arquivos restantes  : $remainingCorrupted              ║")
        println("║  Todos deletados      : ${if (allDeleted) "✓" else "✗"}                      ║")
        println("╚══════════════════════════════════════════════════════════╝")

        assert(allDeleted) { "Todos os arquivos corrompidos devem ser deletados" }
    }

    private fun generateCacheKey(uri: android.net.Uri, segmentIndex: Int, videoFile: File): String {
        val displayName = videoFile.name
        val size = videoFile.length()
        val cacheVersion = 3
        return "strip_v${cacheVersion}_${displayName}_${size}_${segmentIndex}.webp"
    }

    private fun createFakeWebPHeader(width: Int, height: Int): ByteArray {
        val header = ByteArray(20)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        header[8] = 'W'.code.toByte()
        header[9] = 'E'.code.toByte()
        header[10] = 'B'.code.toByte()
        header[11] = 'P'.code.toByte()

        header[12] = 'V'.code.toByte()
        header[13] = 'P'.code.toByte()
        header[14] = '8'.code.toByte()
        header[15] = ' '.code.toByte()

        return header
    }
}