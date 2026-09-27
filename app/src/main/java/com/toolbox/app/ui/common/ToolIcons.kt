package com.toolbox.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn home-grid icons for tools that no Material icon fits. All are single-colour on a 24×24
 * viewport (the home grid tints them), written as SVG path data. Holes are cut with the non-zero
 * rule: a hole circle runs the opposite way round to the shape it sits in.
 */
object ToolIcons {
    /** Black ball with a white window showing an "8". */
    val EightBall: ImageVector by lazy {
        icon("EightBall") {
            fill(
                "M2,12a10,10 0 1,1 20,0a10,10 0 1,1 -20,0Z" +
                    "M6.6,12a5.4,5.4 0 1,0 10.8,0a5.4,5.4 0 1,0 -10.8,0Z" +
                    "M10,9.65a2,2 0 1,1 4,0a2,2 0 1,1 -4,0ZM11.15,9.65a0.85,0.85 0 1,0 1.7,0a0.85,0.85 0 1,0 -1.7,0Z" +
                    "M9.7,13.9a2.3,2.3 0 1,1 4.6,0a2.3,2.3 0 1,1 -4.6,0ZM10.95,13.9a1.05,1.05 0 1,0 2.1,0a1.05,1.05 0 1,0 -2.1,0Z",
            )
        }
    }

    /** A wheel in four quarters, two filled and two outlined. */
    val Spinner: ImageVector by lazy {
        icon("Spinner") {
            fill("M12,12V3A9,9 0 0,0 3,12ZM12,12V21A9,9 0 0,0 21,12Z")
            stroke("M3,12a9,9 0 1,1 18,0a9,9 0 1,1 -18,0ZM12,3V21M3,12H21", 1.6f)
        }
    }

    /** "+1". */
    val PlusOne: ImageVector by lazy {
        icon("PlusOne") {
            fill("M3,11h8.5v2.2h-8.5ZM6.15,7.85h2.2v8.5h-2.2Z")
            stroke("M13.9,7.9L17.2,5.1V19M14.2,19H20.2", 2.2f)
        }
    }

    /** Classic tri-spinner: three lobes joined by concave sides, a bearing in each lobe and the hub. */
    val FidgetSpinner: ImageVector by lazy {
        icon("FidgetSpinner") {
            fill(
                "M7.93,7.17A4.1,4.1 0 1,1 16.07,7.17A5.05,5.05 0 0,0 19.09,12.39A4.1,4.1 0 1,1 15.01,19.44" +
                    "A5.05,5.05 0 0,0 8.99,19.44A4.1,4.1 0 1,1 4.91,12.39A5.05,5.05 0 0,0 7.93,7.17Z" +
                    "M9.7,13a2.3,2.3 0 1,0 4.6,0a2.3,2.3 0 1,0 -4.6,0ZM10.8,13a1.2,1.2 0 1,1 2.4,0a1.2,1.2 0 1,1 -2.4,0Z" +
                    "M9.7,6.7a2.3,2.3 0 1,0 4.6,0a2.3,2.3 0 1,0 -4.6,0ZM10.85,6.7a1.15,1.15 0 1,1 2.3,0a1.15,1.15 0 1,1 -2.3,0Z" +
                    "M15.16,16.15a2.3,2.3 0 1,0 4.6,0a2.3,2.3 0 1,0 -4.6,0ZM16.31,16.15a1.15,1.15 0 1,1 2.3,0a1.15,1.15 0 1,1 -2.3,0Z" +
                    "M4.24,16.15a2.3,2.3 0 1,0 4.6,0a2.3,2.3 0 1,0 -4.6,0ZM5.39,16.15a1.15,1.15 0 1,1 2.3,0a1.15,1.15 0 1,1 -2.3,0Z",
            )
        }
    }

    /** Two "2"s tipping into each other, with impact sparks above. */
    val Merge2048: ImageVector by lazy {
        val two = "M0.2,3A2.8,2.8 0 1,1 5.29,4.61L0.2,9H6.2"
        icon("Merge2048") {
            addGroup(rotate = 14f, pivotX = 3.1f, pivotY = 4.5f, translationX = 3.3f, translationY = 8.6f)
            stroke(two, 1.9f)
            clearGroup()
            addGroup(rotate = -14f, pivotX = 3.1f, pivotY = 4.5f, translationX = 14.1f, translationY = 8.6f)
            stroke(two, 1.9f)
            clearGroup()
            stroke("M12,7.1V4.3M9.7,7.5L8.5,5.7M14.3,7.5L15.5,5.7", 1.4f)
        }
    }

    /** Two rows of bricks with the middle one knocked out, a ball and a paddle. */
    val BlockBreaker: ImageVector by lazy {
        icon("BlockBreaker") {
            fill(
                "M2.5,3h5.5v2.8h-5.5ZM9.25,3h5.5v2.8h-5.5ZM16,3h5.5v2.8h-5.5ZM2.5,7h5.5v2.8h-5.5ZM16,7h5.5v2.8h-5.5Z" +
                    "M10.5,13.4a1.5,1.5 0 1,1 3,0a1.5,1.5 0 1,1 -3,0ZM7,19h10v2h-10Z",
            )
        }
    }

    /** An S-shaped snake with an eye and a forked tongue. */
    val Snake: ImageVector by lazy {
        icon("Snake") {
            stroke("M4,19.6C9,21 16.2,19.8 15.6,15.6C15,11.8 8,12.8 8,9C8,5.8 11.5,4.6 14.8,5.1", 3f)
            fill("M14.5,5.3a2.5,2.5 0 1,1 5,0a2.5,2.5 0 1,1 -5,0ZM17.05,4.6a0.65,0.65 0 1,0 1.3,0a0.65,0.65 0 1,0 -1.3,0Z")
            stroke("M19.4,6.1L20.9,6.7L22,5.9M20.9,6.7L21.8,7.9", 0.9f)
        }
    }

    /** A "4" with a diagonal line through it. */
    val ConnectFour: ImageVector by lazy {
        icon("ConnectFour") {
            stroke("M15,20.5V3.5L4.5,15.5H19.5", 2.4f)
            stroke("M3.5,3.5L20.5,20.5", 1.6f)
        }
    }

    /** 2×2 grid with Xs in two opposite corners and Os in the other two. */
    val TicTacToe: ImageVector by lazy {
        icon("TicTacToe") {
            stroke("M12,3V21M3,12H21", 1.6f)
            stroke("M5.6,5.6L9.4,9.4M9.4,5.6L5.6,9.4M14.6,14.6L18.4,18.4M18.4,14.6L14.6,18.4", 1.7f)
            stroke("M14.5,7.5a2,2 0 1,1 4,0a2,2 0 1,1 -4,0ZM5.5,16.5a2,2 0 1,1 4,0a2,2 0 1,1 -4,0Z", 1.7f)
        }
    }

    /** Rows of bubbles, one of them popped. */
    val BubbleWrap: ImageVector by lazy {
        icon("BubbleWrap") {
            fill(
                "M2.6,5.5a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0ZM3.7,4.5a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z" +
                    "M9.1,5.5a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0Z" +
                    "M10.2,4.5a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z" +
                    "M15.6,5.5a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0Z" +
                    "M16.7,4.5a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z" +
                    "M5.85,12a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0ZM6.95,11a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z" +
                    "M12.35,12a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0Z" +
                    "M13.45,11a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z" +
                    "M2.6,18.5a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0Z" +
                    "M3.7,17.5a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z" +
                    "M15.6,18.5a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0Z" +
                    "M16.7,17.5a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z",
            )
            stroke("M10.2,16.8L11.3,18.1L10.3,19.4M13.8,16.8L12.7,18.1L13.7,19.4", 1.2f)
        }
    }

    /** A knob with a pointer, and notch marks round the top. */
    val Dial: ImageVector by lazy {
        icon("Dial") {
            fill("M4.7,12.8a7.3,7.3 0 1,1 14.6,0a7.3,7.3 0 1,1 -14.6,0ZM11.05,6.9v5.2h1.9v-5.2Z")
            stroke(
                "M5.49,19.31L4.36,20.44M2.8,12.8L1.2,12.8M5.49,6.29L4.36,5.16M12,3.6L12,2M18.51,6.29L19.64,5.16" +
                    "M21.2,12.8L22.8,12.8M18.51,19.31L19.64,20.44",
                1.5f,
            )
        }
    }

    /** A tilted tube with grains settled at the bottom. */
    val RainStick: ImageVector by lazy {
        icon("RainStick") {
            addGroup(rotate = 35f, pivotX = 12f, pivotY = 12f)
            stroke("M9.3,4.5a2.7,2.7 0 0,1 5.4,0V19.5a2.7,2.7 0 0,1 -5.4,0Z", 1.6f)
            fill(
                "M10.2,20a0.8,0.8 0 1,1 1.6,0a0.8,0.8 0 1,1 -1.6,0Z" +
                    "M12.2,20.3a0.8,0.8 0 1,1 1.6,0a0.8,0.8 0 1,1 -1.6,0Z" +
                    "M11.2,18.6a0.8,0.8 0 1,1 1.6,0a0.8,0.8 0 1,1 -1.6,0Z" +
                    "M10,17.2a0.8,0.8 0 1,1 1.6,0a0.8,0.8 0 1,1 -1.6,0Z" +
                    "M12.3,16.9a0.8,0.8 0 1,1 1.6,0a0.8,0.8 0 1,1 -1.6,0Z" +
                    "M11.2,15.2a0.8,0.8 0 1,1 1.6,0a0.8,0.8 0 1,1 -1.6,0Z" +
                    "M10.4,12.6a0.8,0.8 0 1,1 1.6,0a0.8,0.8 0 1,1 -1.6,0Z" +
                    "M12,10.4a0.8,0.8 0 1,1 1.6,0a0.8,0.8 0 1,1 -1.6,0Z" +
                    "M10.8,7.6a0.8,0.8 0 1,1 1.6,0a0.8,0.8 0 1,1 -1.6,0Z",
            )
            clearGroup()
        }
    }

    /** A zip: closed teeth above the slider, open below, with the pull tab. */
    val Zip: ImageVector by lazy {
        icon("Zip") {
            fill(
                "M9,1.8h3v1.25h-3ZM12,3.55h3v1.25h-3ZM9,5.3h3v1.25h-3ZM12,7.05h3v1.25h-3ZM9,8.8h3v1.25h-3Z" +
                    "M8.65,16.2a0.95,0.95 0 1,1 1.9,0a0.95,0.95 0 1,1 -1.9,0Z" +
                    "M13.45,16.2a0.95,0.95 0 1,1 1.9,0a0.95,0.95 0 1,1 -1.9,0Z" +
                    "M6.85,17.95a0.95,0.95 0 1,1 1.9,0a0.95,0.95 0 1,1 -1.9,0Z" +
                    "M15.25,17.95a0.95,0.95 0 1,1 1.9,0a0.95,0.95 0 1,1 -1.9,0Z" +
                    "M5.05,19.7a0.95,0.95 0 1,1 1.9,0a0.95,0.95 0 1,1 -1.9,0Z" +
                    "M17.05,19.7a0.95,0.95 0 1,1 1.9,0a0.95,0.95 0 1,1 -1.9,0Z" +
                    "M3.25,21.45a0.95,0.95 0 1,1 1.9,0a0.95,0.95 0 1,1 -1.9,0Z" +
                    "M18.85,21.45a0.95,0.95 0 1,1 1.9,0a0.95,0.95 0 1,1 -1.9,0ZM7.8,10.6H16.2L14.6,15H9.4Z",
            )
            stroke("M12,15V21", 3f)
        }
    }
}

private val ink = SolidColor(Color.Black)

private inline fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

private fun ImageVector.Builder.fill(d: String) {
    addPath(addPathNodes(d), fill = ink)
}

private fun ImageVector.Builder.stroke(d: String, width: Float) {
    addPath(addPathNodes(d), stroke = ink, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
}
