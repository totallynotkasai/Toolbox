package com.toolbox.app.games.twentyfortyeight

import kotlin.random.Random

/** 2048 rules on a 4×4 board, stored row by row with 0 for an empty square. */
const val N = 4

enum class Move { Left, Right, Up, Down }

/** A new tile, a 2 nine times in ten and otherwise a 4, on a random empty square. */
fun spawn(board: List<Int>, random: Random = Random): List<Int> {
    val empty = board.indices.filter { board[it] == 0 }
    if (empty.isEmpty()) return board
    return board.toMutableList().also { it[empty.random(random)] = if (random.nextFloat() < 0.9f) 2 else 4 }
}

fun newBoard(random: Random = Random): List<Int> = spawn(spawn(List(N * N) { 0 }, random), random)

/** Slides one row toward index 0, merging equal neighbours once. Returns the new row and points scored. */
fun slide(row: List<Int>): Pair<List<Int>, Int> {
    val tiles = row.filter { it != 0 }
    val out = mutableListOf<Int>()
    var gained = 0
    var i = 0
    while (i < tiles.size) {
        if (i + 1 < tiles.size && tiles[i] == tiles[i + 1]) {
            out += tiles[i] * 2; gained += tiles[i] * 2; i += 2
        } else { out += tiles[i]; i++ }
    }
    while (out.size < N) out += 0
    return out to gained
}

/** The board after sliding every line in direction [m], and the points scored. No new tile is added. */
fun move(board: List<Int>, m: Move): Pair<List<Int>, Int> {
    val out = MutableList(N * N) { 0 }
    var gained = 0
    for (line in 0 until N) {
        // Index of the k-th cell along this line, ordered in the direction of travel.
        val idx = (0 until N).map { k ->
            when (m) {
                Move.Left -> line * N + k
                Move.Right -> line * N + (N - 1 - k)
                Move.Up -> k * N + line
                Move.Down -> (N - 1 - k) * N + line
            }
        }
        val (slid, g) = slide(idx.map { board[it] })
        gained += g
        idx.forEachIndexed { k, cell -> out[cell] = slid[k] }
    }
    return out to gained
}

/** False once no direction changes the board: the game is over. */
fun canMove(board: List<Int>): Boolean = Move.entries.any { move(board, it).first != board }

/**
 * A whole turn: slide, then add a tile. Null when the move changes nothing, which doesn't count as a turn and
 * adds no tile.
 */
fun play(board: List<Int>, m: Move, random: Random = Random): Pair<List<Int>, Int>? {
    val (next, gained) = move(board, m)
    return if (next == board) null else spawn(next, random) to gained
}
