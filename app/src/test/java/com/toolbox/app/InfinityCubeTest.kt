package com.toolbox.app

import androidx.compose.ui.geometry.Offset
import com.toolbox.app.toys.infinitycube.CubeCamera
import com.toolbox.app.toys.infinitycube.CubePose
import com.toolbox.app.toys.infinitycube.FoldClicks
import com.toolbox.app.toys.infinitycube.Hold
import com.toolbox.app.toys.infinitycube.InfinityCube
import com.toolbox.app.toys.infinitycube.Placement
import com.toolbox.app.toys.infinitycube.cubeAt
import com.toolbox.app.toys.infinitycube.cubeStateText
import com.toolbox.app.toys.infinitycube.distanceOutside
import com.toolbox.app.toys.infinitycube.followFold
import com.toolbox.app.toys.infinitycube.snapStep
import com.toolbox.app.toys.infinitycube.stepFrom
import com.toolbox.app.toys.infinitycube.toQuat
import com.toolbox.app.toys.infinitycube.transposed
import com.toolbox.app.ui.common.Quat
import com.toolbox.app.ui.common.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class InfinityCubeTest {
    private val folds = (-60..660).map { it / 100f } // a little over a full cycle, from a little before it

    private fun close(a: Vec3, b: Vec3, eps: Float = 1e-4f) = (a - b).length() < eps

    /** The squares (cube × 6 + face) facing out when the cubes are at [poses]: those with no cube against them. */
    private fun outside(poses: List<CubePose>): Set<Int> = poses.indices.flatMap { c ->
        (0 until 6).filter { f ->
            val beside = poses[c].centre + poses[c].rot * InfinityCube.squares[c * 6 + f].normal
            poses.none { close(it.centre, beside, 0.01f) }
        }.map { c * 6 + it }
    }.toSet()

    @Test fun everyHingeStaysJoinedAtEveryFold() {
        val closed = InfinityCube.pose(0f)
        for (h in InfinityCube.hinges) {
            // The hinge's ends as cube b sees them, from where they are when it's closed.
            val ends = listOf(h.from, h.to).map { closed[h.b].rot.local(closed[h.a].place(it) - closed[h.b].centre) }
            for (f in folds) {
                val poses = InfinityCube.pose(f)
                assertTrue("hinge ${h.a}-${h.b} at $f", close(poses[h.a].place(h.from), poses[h.b].place(ends[0])))
                assertTrue("hinge ${h.a}-${h.b} at $f", close(poses[h.a].place(h.to), poses[h.b].place(ends[1])))
            }
        }
    }

    @Test fun closedPositionsAreExactCubes() {
        for (step in listOf(0, 3, 6, -3)) {
            val poses = InfinityCube.pose(step.toFloat())
            // Every cube square to the axes, and the eight filling the eight cells of a 2 × 2 × 2 block exactly.
            for (p in poses) for (r in 0 until 3) for (c in 0 until 3) assertTrue(p.rot[r, c] in setOf(-1f, 0f, 1f))
            val cells = poses.map { Triple(it.centre.x, it.centre.y, it.centre.z) }.toSet()
            val block = listOf(-0.5f, 0.5f).let { h -> h.flatMap { x -> h.flatMap { y -> h.map { z -> Triple(x, y, z) } } } }.toSet()
            assertEquals("step $step", block, cells)
            assertTrue(InfinityCube.isClosed(step))
        }
        for (step in listOf(1, 2, 4, 5, -1)) {
            // The others lie flat: 4 × 2 × 1, one cube thick.
            val c = InfinityCube.pose(step.toFloat()).map { it.centre }
            val spans = listOf(c.maxOf { it.x } - c.minOf { it.x }, c.maxOf { it.y } - c.minOf { it.y }, c.maxOf { it.z } - c.minOf { it.z })
            assertEquals("step $step", listOf(0f, 1f, 3f), spans.sorted())
            assertFalse(InfinityCube.isClosed(step))
        }
    }

    @Test fun aFullCycleComesBackToTheStart() {
        // Each fold ends exactly where the next begins, and the sixth ends where the first began.
        for (step in 1..6) {
            val before = InfinityCube.pose(step - 1e-4f)
            val at = InfinityCube.pose(step.toFloat())
            for (c in 0 until InfinityCube.CUBES) assertTrue("cube $c at step $step", close(before[c].centre, at[c].centre, 1e-3f))
        }
        for (f in listOf(0.37f, 2.5f, 4.91f)) {
            val a = InfinityCube.pose(f)
            val b = InfinityCube.pose(f + InfinityCube.STEPS)
            val c = InfinityCube.pose(f - InfinityCube.STEPS)
            for (i in 0 until InfinityCube.CUBES) {
                assertTrue(close(a[i].centre, b[i].centre) && close(a[i].centre, c[i].centre))
                for (r in 0 until 3) for (k in 0 until 3) assertEquals(a[i].rot[r, k], b[i].rot[r, k], 1e-4f)
            }
        }
    }

    @Test fun noCubePassesThroughAnother() {
        for (f in folds) {
            val poses = InfinityCube.pose(f)
            for (i in poses.indices) for (j in i + 1 until poses.size) {
                assertTrue("cubes $i and $j at $f", overlap(poses[i], poses[j]) < 1e-3f)
            }
        }
    }

    @Test fun theInsideOfOneClosedCubeIsTheOutsideOfTheOther() {
        val first = outside(InfinityCube.pose(0f))
        val second = outside(InfinityCube.pose(3f))
        assertEquals(24, first.size)
        assertEquals(24, second.size)
        assertTrue(first.intersect(second).isEmpty())
        // The first cube's outside is coloured, four squares to a side; the rest is cream.
        val sides = InfinityCube.squares.withIndex().filter { it.value.side >= 0 }
        assertEquals(first, sides.map { it.index }.toSet())
        assertEquals((0 until 6).associateWith { 4 }, sides.groupingBy { it.value.side }.eachCount())
    }

    @Test fun lettingGoSnapsToTheNearestPosition() {
        assertEquals(2, snapStep(2.4f))
        assertEquals(3, snapStep(2.6f))
        assertEquals(0, snapStep(-0.4f))
        assertEquals(-1, snapStep(-0.6f))
    }

    @Test fun foldAndFoldBackGoToTheNextPositionThatWay() {
        assertEquals(3, stepFrom(2f, 1))
        assertEquals(1, stepFrom(2f, -1))
        // Part-way through a fold, they finish it or undo it.
        assertEquals(3, stepFrom(2.3f, 1))
        assertEquals(2, stepFrom(2.3f, -1))
        assertEquals(-1, stepFrom(-0.5f, -1))
        // A hair short of a position counts as on it.
        assertEquals(4, stepFrom(2.9995f, 1))
    }

    @Test fun clicksComeOncePerPositionReached() {
        val heard = mutableListOf<Int>()
        val clicks = FoldClicks(0f)
        clicks.move(0.5f) { heard += it }
        clicks.move(0.995f) { heard += it } // close enough to count
        assertEquals(listOf(1), heard)
        // Overshooting and settling back, or wobbling on it, doesn't click again.
        for (f in listOf(1.1f, 1f, 0.9f, 1f)) clicks.move(f) { heard += it }
        assertEquals(listOf(1), heard)
        // A fast fold clicks each position it passes, in order.
        clicks.move(3.1f) { heard += it }
        clicks.move(2.95f) { heard += it }
        assertEquals(listOf(1, 2, 3), heard)
        // Once well clear, coming back to a position clicks it again, either way.
        clicks.move(2.5f) { heard += it }
        clicks.move(3f) { heard += it }
        clicks.move(1.5f) { heard += it }
        assertEquals(listOf(1, 2, 3, 3, 2), heard)
    }

    @Test fun cubeSaysWhetherItsClosedOrOpen() {
        assertEquals("Closed, yellow on top", cubeStateText(0, "yellow"))
        assertEquals("Closed", cubeStateText(6, null))
        assertEquals("Open", cubeStateText(1, null))
        assertEquals("Open, rolled over", cubeStateText(2, null))
        assertEquals("Closed, inside out", cubeStateText(3, "yellow"))
        assertEquals("Open", cubeStateText(-2, null))
        assertEquals("Open, rolled over", cubeStateText(-1, null))
        assertTrue(InfinityCube.isInsideOut(3) && InfinityCube.isInsideOut(-3) && !InfinityCube.isInsideOut(0))
    }

    private val cam = CubeCamera(CubeCamera.Usual, 0f, Offset(500f, 500f), 100f)

    @Test fun theUsualViewShowsTheTopFrontAndRight() {
        // The toy's top (−z) faces up, the side that opens first (−x) faces you a little, and its right (+y) shows.
        assertTrue((CubeCamera.Usual * Vec3(0f, 0f, -1f)).y > 0.9f)
        assertTrue((CubeCamera.Usual * Vec3(-1f, 0f, 0f)).z > 0.6f)
        assertTrue((CubeCamera.Usual * Vec3(0f, 1f, 0f)).z > 0.4f)
    }

    @Test fun aPointDrawnOnAFaceMapsBackToItself() {
        val on = Vec3(0.3f, -0.2f, 1.1f)
        val normal = Vec3(0.2f, 0.4f, 0.9f).normalized()
        val inPlane = on + (Vec3(1f, 0f, 0f) - normal * (normal dot Vec3(1f, 0f, 0f))) * 0.7f
        val back = cam.unproject(cam.project(inPlane), on, normal)
        assertNotNull(back)
        assertTrue(close(inPlane, back!!, 1e-3f))
    }

    @Test fun aTouchTakesHoldOfTheCubeUnderIt() {
        val poses = InfinityCube.pose(0f)
        // The middle of the nearest cube's front face.
        val front = (0 until InfinityCube.CUBES).maxBy { cam.world(poses[it].centre).z }
        val point = Vec3(-0.5f, 0f, 0f) // its −x face, the one facing you
        val grab = cubeAt(cam.screenOf(poses, front, point), poses, cam, slop = 10f)
        assertNotNull(grab)
        assertEquals(front, grab!!.cube)
        assertTrue(close(point, grab.point, 1e-3f))
        // Well clear of the cube, nothing; just off its edge, the nearest face.
        assertNull(cubeAt(Offset(5f, 5f), poses, cam, slop = 10f))
        assertEquals(0f, distanceOutside(Offset(1f, 1f), listOf(Offset(0f, 0f), Offset(2f, 0f), Offset(2f, 2f), Offset(0f, 2f))))
        assertEquals(1f, distanceOutside(Offset(3f, 1f), listOf(Offset(0f, 0f), Offset(0f, 2f), Offset(2f, 2f), Offset(2f, 0f))))
    }

    @Test fun theFoldFollowsTheFinger() {
        // Cube 0 is in the half that swings up in the first fold, so dragging a point on it carries the fold along.
        val point = Vec3(-0.5f, 0.2f, -0.3f)
        val at = { f: Float -> cam.screenOf(InfinityCube.pose(f), 0, point) }
        assertEquals(0.45f, followFold(0.3f, at(0.45f), at = at), 0.003f)
        assertEquals(0.3f, followFold(0.45f, at(0.3f), at = at), 0.003f)
        // Little by little, through the open position and on into the next fold.
        var f = 0.6f
        for (target in listOf(0.7f, 0.8f, 0.9f, 1f, 1.1f, 1.2f)) f = followFold(f, at(target), at = at)
        assertEquals(1.2f, f, 0.01f)
        // With the finger where the point already is, nothing moves.
        assertEquals(0.3f, followFold(0.3f, at(0.3f), at = at), 0f)
    }

    @Test fun theHeldCubeIsTheOtherHalfTheMiddleOrTheNearerEnd() {
        // The first fold turns the top four (0–3) against the bottom four (4–7).
        assertTrue(InfinityCube.anchor(0, 0) in 4..7)
        assertTrue(InfinityCube.anchor(0, 6) in 0..3)
        // The third folds the end pairs 1–2 and 5–6 under the middle 0, 3, 4 and 7.
        assertTrue(InfinityCube.anchor(2, 1) in setOf(0, 3, 4, 7))
        assertTrue(InfinityCube.anchor(-4, 6) in setOf(0, 3, 4, 7))
        // Dragging the middle holds the end pair beside it.
        val near = InfinityCube.anchor(2, 0)
        val poses = InfinityCube.pose(2f)
        assertTrue(near in setOf(1, 2, 5, 6))
        assertTrue((poses[near].centre - poses[0].centre).length() <= 1.01f)
    }

    @Test fun aHeldPartStaysStillWhileTheRestFolds() {
        val start = Placement(Quat.axisAngle(Vec3(1f, 2f, 3f), 0.7f).toRot(), Vec3(0.3f, -0.2f, 0.1f))
        val hold = Hold(0, 0f, start)
        fun placed(f: Float, cube: Int) = hold.placementAt(f).place(InfinityCube.pose(f))[cube]
        val held = InfinityCube.anchor(0, 0)
        val first = start.place(InfinityCube.pose(0f))[held]
        for (f in listOf(0.2f, 0.5f, 0.99f, 1f)) {
            val p = placed(f, held)
            assertTrue("at $f", close(first.centre, p.centre))
            for (r in 0 until 3) for (c in 0 until 3) assertEquals(first.rot[r, c], p.rot[r, c], 1e-4f)
        }
        // On into the next fold, its held cube stays where it was at the whole step between.
        val next = InfinityCube.anchor(1, 0)
        assertTrue(close(placed(1f, next).centre, placed(1.4f, next).centre))
        // Folding back again puts everything back where it was.
        val back = Hold(0, 1.4f, hold.placementAt(1.4f)).placementAt(0.5f).place(InfinityCube.pose(0.5f))
        val there = hold.placementAt(0.5f).place(InfinityCube.pose(0.5f))
        for (c in 0 until InfinityCube.CUBES) assertTrue("cube $c", close(back[c].centre, there[c].centre, 1e-3f))
        // The toy is only turned and moved as a whole, so the cubes stay hinged.
        val h = InfinityCube.hinges[3]
        val closed = InfinityCube.pose(0f)
        val end = closed[h.b].rot.local(closed[h.a].place(h.from) - closed[h.b].centre)
        assertTrue(close(placed(1.3f, h.a).place(h.from), placed(1.3f, h.b).place(end)))
    }

    @Test fun aTurnBecomesTheSameQuaternion() {
        for (q in listOf(Quat.Identity, Quat.axisAngle(Vec3(0f, 0f, 1f), 3.1f), Quat.axisAngle(Vec3(1f, -2f, 0.5f), 2f), Quat.axisAngle(Vec3(0f, 1f, 0f), -1.2f))) {
            val back = q.toRot().toQuat().toRot()
            val r = q.toRot()
            for (i in 0 until 3) for (j in 0 until 3) assertEquals(r[i, j], back[i, j], 1e-4f)
        }
        val r = CubeCamera.Usual
        val t = r.transposed()
        assertEquals(1f, (r * t)[0, 0], 1e-5f)
        assertEquals(0f, (r * t)[0, 1], 1e-5f)
    }

    /** How deep unit cubes at [a] and [b] overlap (≤ 0 when apart), by separating axes. */
    private fun overlap(a: CubePose, b: CubePose): Float {
        val ax = (0 until 3).map { a.rot.column(it) }
        val bx = (0 until 3).map { b.rot.column(it) }
        val axes = ax + bx + ax.flatMap { u -> bx.map { u cross it } }.filter { it.length() > 1e-4f }.map { it.normalized() }
        val d = b.centre - a.centre
        return axes.minOf { l -> ax.sumOf { abs(it dot l).toDouble() }.toFloat() / 2f + bx.sumOf { abs(it dot l).toDouble() }.toFloat() / 2f - abs(d dot l) }
    }
}
