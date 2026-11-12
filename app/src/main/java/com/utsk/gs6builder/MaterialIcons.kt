package com.utsk.gs6builder

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Material Symbols glyphs for the few things Miuix's icon set has no matching picture for.
// Tinted by the caller, so the fill colour here is irrelevant.

private fun glyph(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
    }.build()

internal object MIcons {
    /** Phone with vibration waves: the Haptics row. */
    val Vibration: ImageVector by lazy {
        glyph("Vibration", "M0,15h2V9H0V15zM3,17h2V7H3V17zM22,9v6h2V9H22zM19,17h2V7h-2V17zM16.5,3h-9C6.67,3,6,3.67,6,4.5v15C6,20.33,6.67,21,7.5,21h9c0.83,0,1.5-0.67,1.5-1.5v-15C18,3.67,17.33,3,16.5,3zM16,19H8V5h8V19z")
    }

    /** Large and small "T": the Font row. */
    val TextFields: ImageVector by lazy {
        glyph("TextFields", "M2.5,4v3h5v12h3V7h5V4H2.5zM21.5,9h-9v3h3v7h3v-7h3V9z")
    }

    /** Plain downward chevron: the trailing mark on dropdown rows. */
    val ExpandMore: ImageVector by lazy {
        glyph("ExpandMore", "M16.59,8.59L12,13.17L7.41,8.59L6,10l6,6l6,-6z")
    }
}
