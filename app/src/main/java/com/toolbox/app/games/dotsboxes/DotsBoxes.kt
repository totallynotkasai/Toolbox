package com.toolbox.app.games.dotsboxes

import kotlin.random.Random

/**
 * Dots and Boxes on a grid of [rows] × [cols] boxes. Players 1 and 2 take turns drawing a line between two
 * neighbouring dots; whoever draws the fourth side of a box claims it and must go again.
 *
 * Lines are numbered: horizontal ones first, row by row ((rows + 1) × cols of them), then vertical ones
 * (rows × (cols + 1)). Immutable: [play] returns a new game, and a game can be rebuilt from its move list.
 */
class DotsBoxes private constructor(
    val rows: Int,
    val cols: Int,
    private val lines: IntArray, // 0 = not drawn, else the player who drew it
    private val owners: IntArray, // per box: 0 or the player who closed it
    val toMove: Int,
    val moves: List<Int>,
) {
    constructor(rows: Int, cols: Int) : this(rows, cols, IntArray(lineCount(rows, cols)), IntArray(rows * cols), 1, emptyList())

    val lineCount get() = lines.size
    val horizontalCount get() = (rows + 1) * cols
    val isOver get() = moves.size == lines.size
    fun score(player: Int) = owners.count { it == player }
    /** 1 or 2, or 0 for a draw. Only meaningful once [isOver]. */
    val winner get() = score(1).compareTo(score(2)).let { if (it > 0) 1 else if (it < 0) 2 else 0 }

    fun lineOwner(line: Int) = lines[line]
    fun boxOwner(row: Int, col: Int) = owners[row * cols + col]
    fun isDrawn(line: Int) = lines[line] != 0
    val freeLines: List<Int> get() = lines.indices.filter { lines[it] == 0 }

    fun isHorizontal(line: Int) = line < horizontalCount

    /** Dot coordinates (row, col) at each end of [line]. */
    fun ends(line: Int): Pair<Pair<Int, Int>, Pair<Int, Int>> = if (isHorizontal(line)) {
        val r = line / cols; val c = line % cols
        (r to c) to (r to c + 1)
    } else {
        val i = line - horizontalCount
        val r = i / (cols + 1); val c = i % (cols + 1)
        (r to c) to (r + 1 to c)
    }

    fun horizontal(row: Int, col: Int) = row * cols + col
    fun vertical(row: Int, col: Int) = horizontalCount + row * (cols + 1) + col

    /** The four lines around box ([row], [col]): top, bottom, left, right. */
    fun sides(row: Int, col: Int) = intArrayOf(horizontal(row, col), horizontal(row + 1, col), vertical(row, col), vertical(row, col + 1))

    fun sidesDrawn(row: Int, col: Int) = sides(row, col).count { lines[it] != 0 }

    /** The one or two boxes that [line] is a side of, as box indices. */
    fun boxesOf(line: Int): List<Int> = buildList {
        if (isHorizontal(line)) {
            val r = line / cols; val c = line % cols
            if (r > 0) add((r - 1) * cols + c)
            if (r < rows) add(r * cols + c)
        } else {
            val i = line - horizontalCount
            val r = i / (cols + 1); val c = i % (cols + 1)
            if (c > 0) add(r * cols + c - 1)
            if (c < cols) add(r * cols + c)
        }
    }

    /** How many boxes drawing [line] would close. */
    fun closes(line: Int): Int = boxesOf(line).count { b -> sidesDrawn(b / cols, b % cols) == 3 }

    /** Draws [line] for [toMove]; returns this game unchanged if the line is already drawn or out of range. */
    fun play(line: Int): DotsBoxes {
        if (line !in lines.indices || lines[line] != 0) return this
        val l = lines.copyOf().also { it[line] = toMove }
        val o = owners.copyOf()
        var closed = 0
        for (b in boxesOf(line)) {
            val r = b / cols; val c = b % cols
            if (sides(r, c).all { l[it] != 0 }) { o[b] = toMove; closed++ }
        }
        return DotsBoxes(rows, cols, l, o, if (closed > 0) toMove else 3 - toMove, moves + line)
    }

    companion object {
        fun lineCount(rows: Int, cols: Int) = (rows + 1) * cols + rows * (cols + 1)

        fun fromMoves(rows: Int, cols: Int, moves: List<Int>) = moves.fold(DotsBoxes(rows, cols)) { g, m -> g.play(m) }
    }
}

/** The computer's line: it takes any box on offer, and otherwise plays at random. */
fun dotsMove(game: DotsBoxes, random: Random = Random): Int {
    val free = game.freeLines
    val closing = free.filter { game.closes(it) > 0 }
    if (closing.isNotEmpty()) return closing.maxBy { game.closes(it) }
    return free.random(random)
}
