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

    /** Artist's palette with dabs of paint: the UI design row. */
    val Palette: ImageVector by lazy {
        glyph(
            "Palette",
            "M12,2 C6.49,2 2,6.49 2,12 C2,17.51 6.49,22 12,22 C13.1,22 14,21.1 14,20 C14,19.46 13.79,18.97 13.45,18.61 " +
                "C13.12,18.26 12.91,17.78 12.91,17.25 C12.91,16.15 13.81,15.25 14.91,15.25 L17,15.25 C19.76,15.25 22,13.01 22,10.25 " +
                "C22,5.7 17.52,2 12,2 Z M6.5,13 C5.67,13 5,12.33 5,11.5 C5,10.67 5.67,10 6.5,10 C7.33,10 8,10.67 8,11.5 " +
                "C8,12.33 7.33,13 6.5,13 Z M9.5,8.5 C8.67,8.5 8,7.83 8,7 C8,6.17 8.67,5.5 9.5,5.5 C10.33,5.5 11,6.17 11,7 " +
                "C11,7.83 10.33,8.5 9.5,8.5 Z M14.5,8.5 C13.67,8.5 13,7.83 13,7 C13,6.17 13.67,5.5 14.5,5.5 C15.33,5.5 16,6.17 16,7 " +
                "C16,7.83 15.33,8.5 14.5,8.5 Z M17.5,13 C16.67,13 16,12.33 16,11.5 C16,10.67 16.67,10 17.5,10 C18.33,10 19,10.67 19,11.5 " +
                "C19,12.33 18.33,13 17.5,13 Z",
        )
    }
}
