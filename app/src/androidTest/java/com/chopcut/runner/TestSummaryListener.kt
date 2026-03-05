package com.chopcut.runner

import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

/**
 * RunListener com dois níveis de output:
 *
 *  1. TEMPO REAL — uma linha impressa assim que cada teste termina:
 *       ✅  [4/17]  methodName                   12ms   Δheap: +2KB   threads: 8
 *
 *  2. SUMÁRIO FINAL — tabela completa após toda a suite:
 *       ╔══...══╗
 *       ║  CHOPCUT — RESUMO DOS TESTES          ║
 *       ...
 *
 * Métricas coletadas por teste:
 *   - Duração (ms)         — System.currentTimeMillis()
 *   - Delta de heap (KB)   — Runtime.totalMemory - freeMemory, antes e depois
 *   - Thread count         — Thread.activeCount(), antes e depois
 *   - Progresso            — índice / total (total via testRunStarted)
 */
class TestSummaryListener : RunListener() {

    // ─── Modelo ───────────────────────────────────────────────────────────────

    private enum class Status(val icon: String) {
        PASSED("✅"), FAILED("❌"), SKIPPED("⏭"), IGNORED("⬜")
    }

    private data class Entry(
        val method: String,
        val className: String,
        val index: Int,
        var status: Status = Status.PASSED,
        var durationMs: Long = 0L,
        var heapBeforeKb: Long = 0L,
        var heapAfterKb: Long = 0L,
        var threadsAtStart: Int = 0,
        var threadsAtEnd: Int = 0,
        var failureMessage: String? = null
    ) {
        val heapDeltaKb: Long get() = heapAfterKb - heapBeforeKb
        val heapDeltaStr: String get() {
            val sign = if (heapDeltaKb >= 0) "+" else ""
            return "${sign}${heapDeltaKb}KB"
        }
    }

    // ─── Estado ───────────────────────────────────────────────────────────────

    private val entries = LinkedHashMap<String, Entry>()
    private val startTimes = HashMap<String, Long>()

    @Volatile private var totalTests = 0
    @Volatile private var counter = 0
    @Volatile private var suiteStartMs = 0L

    // ─── Helpers de métricas ──────────────────────────────────────────────────

    private fun heapKb(): Long =
        Runtime.getRuntime().run { (totalMemory() - freeMemory()) / 1_024L }

    private fun threads(): Int = Thread.activeCount()

    // ─── Eventos do runner ────────────────────────────────────────────────────

    override fun testRunStarted(description: Description) {
        totalTests = countTests(description)
        suiteStartMs = System.currentTimeMillis()

        println()
        println("┌─ CHOPCUT TESTS ─── ${timestamp()} " + "─".repeat(30))
        if (totalTests > 0) println("│  $totalTests testes encontrados")
        println("│")
    }

    override fun testStarted(description: Description) {
        val key = description.displayName
        counter++
        startTimes[key] = System.currentTimeMillis()
        entries[key] = Entry(
            method = description.methodName ?: description.displayName,
            className = description.className.substringAfterLast("."),
            index = counter,
            heapBeforeKb = heapKb(),
            threadsAtStart = threads()
        )
        // Linha de "rodando agora" (útil se o teste travar)
        val progress = if (totalTests > 0) "[${counter}/${totalTests}]" else "[$counter]"
        println("│  ▶  $progress  ${description.className.substringAfterLast(".")}::${description.methodName}")
    }

    override fun testFinished(description: Description) {
        val key = description.displayName
        val start = startTimes.remove(key) ?: return
        val entry = entries[key] ?: return

        entry.durationMs = System.currentTimeMillis() - start
        entry.heapAfterKb = heapKb()
        entry.threadsAtEnd = threads()

        printLiveLine(entry)
    }

    override fun testFailure(failure: Failure) {
        entries[failure.description.displayName]?.apply {
            status = Status.FAILED
            failureMessage = failure.message
                ?.lines()
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.take(60)
        }
    }

    override fun testAssumptionFailure(failure: Failure) {
        entries[failure.description.displayName]?.status = Status.SKIPPED
    }

    override fun testIgnored(description: Description) {
        counter++
        entries.getOrPut(description.displayName) {
            Entry(
                method = description.methodName ?: description.displayName,
                className = description.className.substringAfterLast("."),
                index = counter
            )
        }.status = Status.IGNORED
        println("│  ⬜  ${description.methodName}  (ignorado)")
    }

    override fun testRunFinished(result: Result) {
        println("│")
        println("└─" + "─".repeat(60))
        println()
        printSummary(result)
    }

    // ─── Output em tempo real ────────────────────────────────────────────────

    private fun printLiveLine(entry: Entry) {
        val progress = if (totalTests > 0) "[${entry.index}/${totalTests}]" else "[${entry.index}]"
        val name = entry.method.take(42).padEnd(42)
        val duration = "${entry.durationMs}ms".padStart(7)
        val heap = "Δheap: ${entry.heapDeltaStr}".padEnd(14)
        val threads = "threads: ${entry.threadsAtEnd}"

        println("│  ${entry.status.icon}  $progress  $name $duration   $heap  $threads")

        entry.failureMessage?.let {
            println("│       ↳ $it")
        }
    }

    // ─── Sumário final ────────────────────────────────────────────────────────

    private fun printSummary(result: Result) {
        val W = 66

        fun bar(l: String, f: String, r: String) = "$l${f.repeat(W)}$r"
        fun row(s: String) = "║ ${s.padEnd(W - 1)}║"
        fun blank() = row("")

        val byClass = entries.values.groupBy { it.className }
        val passed  = entries.values.count { it.status == Status.PASSED }
        val failed  = entries.values.count { it.status == Status.FAILED }
        val skipped = entries.values.count { it.status == Status.SKIPPED || it.status == Status.IGNORED }

        val avgMs   = if (entries.isNotEmpty()) entries.values.map { it.durationMs }.average().toLong() else 0L
        val slowest = entries.values.maxByOrNull { it.durationMs }
        val totalHeapKb = entries.values.sumOf { it.heapDeltaKb.coerceAtLeast(0L) }

        println(bar("╔", "═", "╗"))
        println(row("  CHOPCUT — RESUMO DOS TESTES"))
        println(bar("╠", "═", "╣"))
        println(blank())

        byClass.forEach { (className, tests) ->
            val cPass = tests.count { it.status == Status.PASSED }
            val cFail = tests.count { it.status == Status.FAILED }
            val cIcon = if (cFail == 0) "✅" else "❌"
            val header = "$cIcon  $className"
            val count  = "${cPass}/${tests.size}"
            println(row(header + " ".repeat((W - 2 - header.length - count.length).coerceAtLeast(1)) + count))
            println(row("   " + "─".repeat(W - 5)))

            tests.forEach { e ->
                val dur  = "${e.durationMs}ms".padStart(6)
                val heap = "Δ${e.heapDeltaStr}".padEnd(10)
                val thr  = "t:${e.threadsAtEnd}"
                val name = e.method.take(36).padEnd(36)
                println(row("  ${e.status.icon}  $name $dur  $heap  $thr"))
                e.failureMessage?.let { println(row("        ↳ $it")) }
            }
            println(blank())
        }

        println(bar("╠", "═", "╣"))
        val statusLine = if (failed == 0) "✅  TODOS OS TESTES PASSARAM" else "❌  $failed FALHA(S) DETECTADA(S)"
        println(row("  $statusLine"))
        println(bar("╠", "─", "╣"))
        println(row("  ✅ $passed   ❌ $failed   ⏭ $skipped   tempo: ${result.runTime}ms   média: ${avgMs}ms"))
        println(row("  heap total alocado: ${totalHeapKb}KB   teste mais lento: ${slowest?.method?.take(30)} (${slowest?.durationMs}ms)"))
        println(bar("╚", "═", "╝"))
        println()
    }

    // ─── Utilitários ─────────────────────────────────────────────────────────

    /** Conta recursivamente os nós folha (métodos de teste) na árvore de Description. */
    private fun countTests(desc: Description): Int =
        if (desc.children.isEmpty()) 1
        else desc.children.fold(0) { acc, child -> acc + countTests(child) }

    private fun timestamp(): String {
        val ms = System.currentTimeMillis()
        val s  = (ms / 1_000) % 60
        val m  = (ms / 60_000) % 60
        val h  = (ms / 3_600_000) % 24
        return "%02d:%02d:%02d".format(h, m, s)
    }
}
