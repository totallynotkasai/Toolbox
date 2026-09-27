package com.toolbox.app.toys.bubblewrap

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

/** Roughly one bubble in this many needs two presses. */
internal const val STUBBORN_ODDS = 12

/** Seconds the little burst ring shows after a pop. */
internal const val POP_FLASH = 0.14f

/** Points around each popped bubble's crinkled outline. */
internal const val CRINKLE = 9

/** Row spacing of a hex grid, as a fraction of the bubble pitch (sin 60°). */
private const val ROW_STEP = 0.866f

internal enum class Press { None, Squeak, Pop }

/**
 * Offset ("hex") rows of bubbles filling [width] × [height] inside [margin], each about [target] px across.
 * Odd rows shift right by half a pitch; the pitch is stretched so the rows span the width exactly.
 */
internal class HexGrid(val width: Float, val height: Float, target: Float, margin: Float = 0f) {
    val cols: Int
    val rows: Int
    val pitch: Float
    private val rowStep: Float
    private val x0: Float
    private val y0: Float

    init {
        val w = (width - 2 * margin).coerceAtLeast(target)
        val h = (height - 2 * margin).coerceAtLeast(target)
        cols = max(1, floor(w / target - 0.5f).toInt())
        pitch = w / (cols + 0.5f)
        rowStep = pitch * ROW_STEP
        rows = max(1, floor((h - pitch) / rowStep).toInt() + 1)
        x0 = margin + pitch / 2
        y0 = margin + (h - ((rows - 1) * rowStep + pitch)) / 2 + pitch / 2
    }

    val count get() = rows * cols

    fun x(i: Int): Float = x0 + (i % cols) * pitch + if ((i / cols) % 2 == 1) pitch / 2 else 0f
    fun y(i: Int): Float = y0 + (i / cols) * rowStep

    /** Index of the bubble under ([px], [py]), or -1 if the point is on the film between bubbles. */
    fun hit(px: Float, py: Float): Int {
        val reach = pitch * 0.5f
        var best = -1
        var bestD = reach * reach
        for (i in 0 until count) {
            val dx = px - x(i)
            val dy = py - y(i)
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }
}

/**
 * One sheet of bubble wrap: which bubbles are popped, which are stubborn (the first press only squishes them),
 * plus the per-bubble animation state the screen draws from ([squish], [popAge]).
 */
internal class BubbleSheet(val count: Int, val tint: Int = 0, random: Random = Random.Default) {
    val stubborn = BooleanArray(count) { random.nextInt(STUBBORN_ODDS) == 0 }
    val softened = BooleanArray(count) // a stubborn bubble that has been squished once
    val popped = BooleanArray(count)
    val pressed = BooleanArray(count)  // set each frame from the fingers currently holding a bubble
    val squish = FloatArray(count)     // 0 = full dome, 1 = pressed flat
    val popAge = FloatArray(count) { POP_FLASH }
    val crinkle = FloatArray(count * CRINKLE) { 0.7f + 0.32f * random.nextFloat() }

    var poppedCount = 0
        private set
    val allPopped get() = count > 0 && poppedCount == count

    /** A finished press (finger lifted or held long enough) on bubble [i]. */
    fun press(i: Int): Press = when {
        popped[i] -> Press.None
        stubborn[i] && !softened[i] -> {
            softened[i] = true
            Press.Squeak
        }
        else -> {
            popped[i] = true
            popAge[i] = 0f
            poppedCount++
            Press.Pop
        }
    }

    /** Eases every bubble's squish toward its target and ages pop bursts; true while anything is still moving. */
    fun animate(dt: Float): Boolean {
        var moving = false
        val inRate = 1f - exp(-dt * 45f)  // presses in quickly
        val outRate = 1f - exp(-dt * 16f) // swells back a little slower
        for (i in 0 until count) {
            val target = if (pressed[i] && !popped[i]) 1f else 0f
            val s = squish[i]
            if (s != target) {
                val next = s + (target - s) * (if (target > s) inRate else outRate)
                squish[i] = if (abs(target - next) < 0.003f) target else next
                moving = true
            }
            if (popped[i] && popAge[i] < POP_FLASH) {
                popAge[i] += dt
                moving = true
            }
        }
        return moving
    }
}
