package com.chopcut.ui.components

data class TrimPosition(
    val positions: List<Long> = emptyList()
) {
    companion object {
        val Empty = TrimPosition()
    }

    val draftPosition: Long?
        get() = if (positions.size % 2 == 1) positions.last() else null

    val completeRanges: List<Pair<Long, Long>>
        get() = mergeRanges(positions)

    val isDraftMode: Boolean
        get() = positions.size % 2 == 1

    fun isPositionInList(pos: Long): Boolean = pos in positions

    fun isPositionInRange(pos: Long): Boolean = completeRanges.any { (s, e) -> pos in s..e }

    fun withPosition(pos: Long): TrimPosition {
        return if (pos in positions) this else copy(positions = positions + pos)
    }

    fun removeRangeAt(pos: Long): TrimPosition {
        val rangeToRemove = completeRanges.find { (s, e) -> pos in s..e }
        return if (rangeToRemove != null) {
            val newRanges = completeRanges.filterNot { it == rangeToRemove }
            val newPositions = newRanges.flatMap { listOf(it.first, it.second) }.toMutableList()
            if (isDraftMode) {
                newPositions.add(positions.last())
            }
            copy(positions = newPositions)
        } else {
            this
        }
    }

    private fun mergeRanges(positions: List<Long>): List<Pair<Long, Long>> {
        if (positions.size < 2) return emptyList()

        val rawRanges = positions.chunked(2).mapNotNull { chunk ->
            if (chunk.size == 2) minOf(chunk[0], chunk[1]) to maxOf(chunk[0], chunk[1])
            else null
        }

        return rawRanges.sortedBy { it.first }
            .fold(emptyList<Pair<Long, Long>>()) { acc, range ->
                if (acc.isEmpty() || range.first > acc.last().second) {
                    acc + range
                } else {
                    acc.dropLast(1) + (acc.last().first to maxOf(acc.last().second, range.second))
                }
            }
    }
}
