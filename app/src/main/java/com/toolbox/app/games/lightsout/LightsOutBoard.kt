package com.toolbox.app.games.lightsout

import kotlin.random.Random

/**
 * Immutable 5×5 Lights Out board, one bit per cell (bit `row * 5 + col` set = light on).
 * Pressing a cell toggles it and its orthogonal neighbours.
 */
@JvmInline
value class LightsOutBoard(val bits: Int) {
    val isSolved: Boolean get() = bits == 0
    val lightsOn: Int get() = Integer.bitCount(bits)

    fun isOn(cell: Int): Boolean = (bits shr cell) and 1 == 1

    fun press(cell: Int): LightsOutBoard = LightsOutBoard(bits xor pressMask(cell))

    /**
     * The fewest presses that turn every light off (each cell at most once; order doesn't matter), or null if the
     * board can't be solved. Solves the 25×25 system over GF(2) by Gaussian elimination; the 5×5 matrix has a
     * 2-dimensional null space, so every solvable board has 4 solutions and this picks the shortest.
     */
    fun solution(): List<Int>? {
        // Row r: which presses toggle cell r (the press matrix is symmetric), plus cell r's current state at bit 25.
        val rows = IntArray(CELLS) { r -> pressMask(r) or (((bits shr r) and 1) shl CELLS) }
        val pivotCol = IntArray(CELLS)
        var rank = 0
        for (col in 0 until CELLS) {
            val p = (rank until CELLS).firstOrNull { (rows[it] shr col) and 1 == 1 } ?: continue
            rows[p] = rows[rank].also { rows[rank] = rows[p] }
            for (r in 0 until CELLS) {
                if (r != rank && (rows[r] shr col) and 1 == 1) rows[r] = rows[r] xor rows[rank]
            }
            pivotCol[rank++] = col
        }
        // A row reduced to 0 = 1 means no combination of presses works.
        if ((rank until CELLS).any { (rows[it] shr CELLS) and 1 == 1 }) return null

        // Particular solution with every free variable 0...
        var x = 0
        for (r in 0 until rank) if ((rows[r] shr CELLS) and 1 == 1) x = x or (1 shl pivotCol[r])
        // ...plus any combination of null-space vectors (one per free variable) is also a solution.
        val pivots = pivotCol.take(rank).toSet()
        val nullBasis = (0 until CELLS).filter { it !in pivots }.map { free ->
            var v = 1 shl free
            for (r in 0 until rank) if ((rows[r] shr free) and 1 == 1) v = v or (1 shl pivotCol[r])
            v
        }
        var best = x
        for (combo in 1 until (1 shl nullBasis.size)) {
            var v = x
            nullBasis.forEachIndexed { i, b -> if ((combo shr i) and 1 == 1) v = v xor b }
            if (Integer.bitCount(v) < Integer.bitCount(best)) best = v
        }
        return (0 until CELLS).filter { (best shr it) and 1 == 1 }
    }

    companion object {
        const val SIZE = 5
        const val CELLS = SIZE * SIZE
        val SOLVED = LightsOutBoard(0)

        /** Bits toggled by pressing [cell]: itself and its up/down/left/right neighbours. */
        fun pressMask(cell: Int): Int {
            val r = cell / SIZE
            val c = cell % SIZE
            var m = 1 shl cell
            if (r > 0) m = m or (1 shl (cell - SIZE))
            if (r < SIZE - 1) m = m or (1 shl (cell + SIZE))
            if (c > 0) m = m or (1 shl (cell - 1))
            if (c < SIZE - 1) m = m or (1 shl (cell + 1))
            return m
        }

        /**
         * A puzzle made by pressing 8–15 distinct random cells on a solved board, so it is always solvable.
         * Retries in the rare case the presses cancel out to a blank board.
         */
        fun random(random: Random = Random.Default): LightsOutBoard {
            while (true) {
                var b = SOLVED
                (0 until CELLS).shuffled(random).take(random.nextInt(8, 16)).forEach { b = b.press(it) }
                if (!b.isSolved) return b
            }
        }
    }
}
