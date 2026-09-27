package com.toolbox.app.games.battleships

import kotlin.random.Random

const val GRID = 10

/** Cells are numbered y * [GRID] + x. */
fun cellOf(x: Int, y: Int) = y * GRID + x

data class Ship(val length: Int, val x: Int, val y: Int, val horizontal: Boolean) {
    val cells: List<Int> get() = List(length) { i -> if (horizontal) cellOf(x + i, y) else cellOf(x, y + i) }
    val inBounds: Boolean get() = x >= 0 && y >= 0 && (if (horizontal) x + length <= GRID && y < GRID else y + length <= GRID && x < GRID)
    fun rotated() = copy(horizontal = !horizontal)
    fun movedTo(nx: Int, ny: Int) = copy(x = nx, y = ny)
}

object Fleet {
    val LENGTHS = listOf(5, 4, 3, 3, 2)
    val NAMES = listOf("Carrier", "Battleship", "Cruiser", "Submarine", "Destroyer")

    /** Ships don't overlap and all fit on the grid. (Touching is allowed, as in the paper game.) */
    fun isValid(ships: List<Ship>): Boolean {
        if (ships.any { !it.inBounds }) return false
        val cells = ships.flatMap { it.cells }
        return cells.size == cells.toSet().size
    }

    /** A random layout, with no two ships touching (even diagonally), which reads more clearly. */
    fun random(random: Random = Random): List<Ship> {
        while (true) {
            val ships = ArrayList<Ship>()
            val blocked = HashSet<Int>()
            var ok = true
            for (len in LENGTHS) {
                val options = (0 until GRID).flatMap { x -> (0 until GRID).flatMap { y -> listOf(Ship(len, x, y, true), Ship(len, x, y, false)) } }
                    .filter { it.inBounds && it.cells.none { c -> c in blocked } }
                if (options.isEmpty()) { ok = false; break }
                val ship = options.random(random)
                ships += ship
                for (c in ship.cells) {
                    val cx = c % GRID; val cy = c / GRID
                    for (dx in -1..1) for (dy in -1..1) {
                        val nx = cx + dx; val ny = cy + dy
                        if (nx in 0 until GRID && ny in 0 until GRID) blocked += cellOf(nx, ny)
                    }
                }
            }
            if (ok) return ships
        }
    }
}

enum class ShotResult { Miss, Hit, Sunk }

/** One player's fleet and the shots the other player has fired at it. Immutable. */
data class Board(val ships: List<Ship>, val shots: List<Int> = emptyList()) {
    private val shotSet by lazy { shots.toSet() }

    fun isShot(cell: Int) = cell in shotSet
    fun shipAt(cell: Int): Int = ships.indexOfFirst { cell in it.cells }
    fun isHit(cell: Int) = isShot(cell) && shipAt(cell) >= 0
    fun isSunk(ship: Int) = ships[ship].cells.all { it in shotSet }
    val allSunk get() = ships.indices.all { isSunk(it) }

    /** Fires at [cell]; null if it was already fired at. */
    fun fire(cell: Int): Pair<Board, ShotResult>? {
        if (cell !in 0 until GRID * GRID || isShot(cell)) return null
        val next = copy(shots = shots + cell)
        val ship = shipAt(cell)
        val result = when {
            ship < 0 -> ShotResult.Miss
            next.isSunk(ship) -> ShotResult.Sunk
            else -> ShotResult.Hit
        }
        return next to result
    }

    fun encode(): String = ships.joinToString(";") { "${it.length},${it.x},${it.y},${if (it.horizontal) 1 else 0}" } +
        "|" + shots.joinToString(",")

    companion object {
        fun decode(s: String): Board? = runCatching {
            val (shipPart, shotPart) = s.split("|")
            val ships = shipPart.split(";").map { t ->
                val p = t.split(",").map(String::toInt)
                Ship(p[0], p[1], p[2], p[3] == 1)
            }
            val shots = if (shotPart.isEmpty()) emptyList() else shotPart.split(",").map(String::toInt)
            Board(ships, shots)
        }.getOrNull()
    }
}

/**
 * The computer's next shot at [board], using only what a player could know: it fires at random until it hits,
 * then tries the cells next to any hit on a ship that hasn't sunk yet.
 */
fun aiShot(board: Board, random: Random = Random): Int {
    val sunkCells = board.ships.indices.filter { board.isSunk(it) }.flatMap { board.ships[it].cells }.toSet()
    val openHits = board.shots.filter { board.isHit(it) && it !in sunkCells }
    val near = openHits.flatMap { neighbours(it) }.filter { !board.isShot(it) }.distinct()
    return if (near.isNotEmpty()) near.random(random) else (0 until GRID * GRID).filter { !board.isShot(it) }.random(random)
}

private fun neighbours(cell: Int): List<Int> {
    val x = cell % GRID; val y = cell / GRID
    return listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
        .filter { (a, b) -> a in 0 until GRID && b in 0 until GRID }
        .map { (a, b) -> cellOf(a, b) }
}
