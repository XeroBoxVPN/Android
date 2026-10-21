package io.nekohasekai.sfa.compose.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import io.nekohasekai.sfa.compose.theme.XeroCyan
import io.nekohasekai.sfa.compose.theme.XeroMint
import io.nekohasekai.sfa.compose.theme.XeroViolet

// The XeroBox X: four blades locking around a diamond core. Same geometry as
// windows/assets/mark.svg (viewBox 256), so the mark is identical on every platform.
private val blades = arrayOf(
    floatArrayOf(128.7f, 99f, 72.1f, 42.4f, 42.4f, 72.1f, 99f, 128.7f), // top-left
    floatArrayOf(127.3f, 99f, 183.9f, 42.4f, 213.6f, 72.1f, 157f, 128.7f), // top-right
    floatArrayOf(157f, 127.3f, 213.6f, 183.9f, 183.9f, 213.6f, 127.3f, 157f), // bottom-right
    floatArrayOf(99f, 127.3f, 42.4f, 183.9f, 72.1f, 213.6f, 128.7f, 157f), // bottom-left
)
private val outward = arrayOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)
private val core = floatArrayOf(128f, 114f, 142f, 128f, 128f, 142f, 114f, 128f)

/**
 * The X mark.
 *
 * @param spread 0 = blades locked together (protected); 1 = blades pulled apart (open).
 * @param tint a flat colour, or null for the brand gradient (mint → cyan → violet).
 * @param coreAlpha opacity of the diamond core; 0 hides it.
 */
@Composable
fun XMark(
    modifier: Modifier = Modifier,
    spread: Float = 0f,
    tint: Color? = null,
    coreColor: Color = XeroMint,
    coreAlpha: Float = 0.9f,
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 256f
        val ox = (size.width - 256f * s) / 2f
        val oy = (size.height - 256f * s) / 2f
        val brush = if (tint == null) {
            Brush.linearGradient(
                colors = listOf(XeroMint, XeroCyan, XeroViolet),
                start = Offset(ox + 40f * s, oy + 40f * s),
                end = Offset(ox + 216f * s, oy + 216f * s),
            )
        } else {
            null
        }
        val shift = spread.coerceIn(0f, 1.5f) * 13f // viewBox units along each diagonal
        val stroke = Stroke(width = 6f * s, join = StrokeJoin.Round)
        blades.forEachIndexed { i, p ->
            val (dx, dy) = outward[i]
            val path = Path().apply {
                for (k in 0 until 4) {
                    val x = ox + (p[k * 2] + dx * shift) * s
                    val y = oy + (p[k * 2 + 1] + dy * shift) * s
                    if (k == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            // Fill plus a round-joined stroke softens the corners, as in the SVG.
            if (brush != null) {
                drawPath(path, brush, style = Fill)
                drawPath(path, brush, style = stroke)
            } else {
                drawPath(path, tint!!, style = Fill)
                drawPath(path, tint, style = stroke)
            }
        }
        if (coreAlpha > 0f) {
            val path = Path().apply {
                moveTo(ox + core[0] * s, oy + core[1] * s)
                for (k in 1 until 4) lineTo(ox + core[k * 2] * s, oy + core[k * 2 + 1] * s)
                close()
            }
            drawPath(path, coreColor.copy(alpha = coreAlpha.coerceIn(0f, 1f)))
        }
    }
}
