package com.utsk.gs6builder

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.PyException
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.isRuntimeShaderSupported
import com.utsk.gs6builder.glass.LiquidBottomTab
import com.utsk.gs6builder.glass.LiquidBottomTabs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Settings
import java.io.File
import java.util.function.Consumer

@Composable
fun Root(activity: ComponentActivity, b: Backend, s: AppSettings) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (s.themeMode) { 1 -> false; 2 -> true; else -> systemDark }
    val monet = s.monet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    DisposableEffect(dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
        )
        activity.window.isNavigationBarContrastEnforced = false
        onDispose {}
    }

    // App font, applied to the Material theme's typography. 0 = Intern, 1 = Inter, 2 = System default.
    val appFont = remember(s.fontChoice) {
        when (s.fontChoice) {
            1 -> FontFamily(Font(R.font.inter_variable))
            2 -> FontFamily.Default
            else -> FontFamily(Font(R.font.intern_variable))
        }
    }
    val c = rememberAppController(b, s)
    MaterialAppTheme(s, dark, appFont) { MaterialShell(c, dark) }
}

    /** Re-run the combo check for an imported row without UI prompts (imports are always lenient). */
internal fun checkRowQuiet(scope: CoroutineScope, b: Backend, row: JSONObject, st: Store) {
    scope.launch {
        val r = JSONObject(withContext(Dispatchers.IO) { b.call("check_chart", row.getString("path"), true) })
        row.put("combo", if (r.getString("status") == "ok") r.getInt("combo").toString() else "err")
        st.touch()
    }
}

// ---- dev-mode hex viewer ---------------------------------------------------------------------

internal const val DEV_VIEW_LIMIT = 4 * 1024 * 1024   // bytes shown in-app; export gets the whole file
internal const val HEX_BYTES_PER_LINE = 8

internal class HexFile(val name: String, val bytes: ByteArray, val totalSize: Long)

/** First [limit] bytes of [f]. */
internal fun readHead(f: File, limit: Int): ByteArray {
    val buf = ByteArray(minOf(f.length(), limit.toLong()).toInt())
    var n = 0
    f.inputStream().use { i ->
        while (n < buf.size) {
            val r = i.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
    }
    return if (n == buf.size) buf else buf.copyOf(n)
}

/** Null bytes or stray control characters near the start: not something to show as text. */
internal fun looksBinary(b: ByteArray): Boolean = b.take(1024).any {
    val v = it.toInt() and 0xFF
    v < 9 || (v in 14..31 && v != 27)
}

/** "00000010  48 65 6c 6c 6f 20 77 6f  |Hello wo|" */
internal fun hexLine(b: ByteArray, line: Int): String {
    val start = line * HEX_BYTES_PER_LINE
    val end = minOf(start + HEX_BYTES_PER_LINE, b.size)
    val sb = StringBuilder("%08x  ".format(start))
    for (i in start until start + HEX_BYTES_PER_LINE) {
        if (i < end) sb.append("%02x ".format(b[i].toInt() and 0xFF)) else sb.append("   ")
    }
    sb.append(" |")
    for (i in start until end) {
        val c = b[i].toInt() and 0xFF
        sb.append(if (c in 32..126) c.toChar() else '.')
    }
    return sb.append('|').toString()
}
