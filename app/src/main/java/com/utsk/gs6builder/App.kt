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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import java.io.File
import java.util.function.Consumer

@Composable
fun Root(activity: ComponentActivity, b: Backend, s: AppSettings) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (s.themeMode) { 1 -> false; 2 -> true; else -> systemDark }
    val monet = s.monet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val mode = when (s.themeMode) {
        1 -> if (monet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        2 -> if (monet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
        else -> if (monet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    }
    val controller = remember(mode) { ThemeController(colorSchemeMode = mode) }

    DisposableEffect(dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
        )
        activity.window.isNavigationBarContrastEnforced = false
        onDispose {}
    }

    // App font, applied to every Miuix text style. 0 = Intern, 1 = Inter, 2 = System default.
    val appFont = remember(s.fontChoice) {
        when (s.fontChoice) {
            1 -> FontFamily(Font(R.font.inter_variable))
            2 -> FontFamily.Default
            else -> FontFamily(Font(R.font.intern_variable))
        }
    }
    val fontStyles = remember(appFont) {
        val d = defaultTextStyles()
        d.copy(
            main = d.main.copy(fontFamily = appFont), paragraph = d.paragraph.copy(fontFamily = appFont),
            body1 = d.body1.copy(fontFamily = appFont), body2 = d.body2.copy(fontFamily = appFont),
            button = d.button.copy(fontFamily = appFont), footnote1 = d.footnote1.copy(fontFamily = appFont),
            footnote2 = d.footnote2.copy(fontFamily = appFont), headline1 = d.headline1.copy(fontFamily = appFont),
            headline2 = d.headline2.copy(fontFamily = appFont), subtitle = d.subtitle.copy(fontFamily = appFont),
            title1 = d.title1.copy(fontFamily = appFont), title2 = d.title2.copy(fontFamily = appFont),
            title3 = d.title3.copy(fontFamily = appFont), title4 = d.title4.copy(fontFamily = appFont),
        )
    }
    // Hoisted above the style switch so the project and every dialog survive changing style.
    val c = rememberAppController(b, s)
    if (s.uiStyle == 1) {
        MaterialAppTheme(s, dark, appFont) { MaterialShell(c, dark) }
    } else {
        MiuixTheme(controller = controller, textStyles = fontStyles) { MiuixShell(c, dark) }
    }
}


/** The Miuix UI: unchanged look and behaviour, now driven by the shared [AppController]. */
@Composable
internal fun MiuixShell(c: AppController, dark: Boolean) {
  with(c) {
    val density = LocalDensity.current
    var bottomPx by remember { mutableIntStateOf(0) }
    val backdrop = rememberLayerBackdrop()
    val useGlass = s.glassBar && isRuntimeShaderSupported()
    val barIndex by remember { derivedStateOf { currentTabIndex } }

        // ---- layout --------------------------------------------------------------------------
        val tabIcons = listOf(MiuixIcons.Import, MiuixIcons.Folder, MiuixIcons.Edit, MiuixIcons.Settings)

        Scaffold(topBar = {
            SmallTopAppBar(title = when (subPage) {
                SubPage.Licenses -> "Open-source licenses"
                SubPage.DevFiles -> "Private app data"
                null -> TAB_LABELS[pagerState.targetPage + 1]
            })
        }) { pad ->
            BackHandler(enabled = subPage != null) { subPage = null }
            // The Miuix NavigationBar pads for the system bars itself; adding the scaffold's inset on top of it
            // left a gap under the plain bar. Only the floating glass bar needs it.
            val barInset = if (useGlass) pad.calculateBottomPadding() else 0.dp
            Box(Modifier.fillMaxSize()) {
                // Page content. It is also the backdrop the glass bar refracts.
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MiuixTheme.colorScheme.surface)) {
                    // All three pages stay composed, so switching tabs only scrolls the pager instead of
                    // composing the target page from scratch.
                    HorizontalPager(
                        state = pagerState, modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 2,
                    ) { p ->
                        val scroll = when (p) { 0 -> filesScroll; 1 -> metaScroll; else -> settingsScroll }
                        Column(
                            Modifier.fillMaxSize().verticalScroll(scroll)
                                .padding(top = pad.calculateTopPadding(),
                                    bottom = with(density) { bottomPx.toDp() } + barInset)
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when (p) {
                                0 -> FilesPage(st, onPickFile, onCheckRow)
                                1 -> MetadataPage(st, onRegen = onRegen, onCopy = onCopy)
                                else -> SettingsPage(s, onNewProject = onNewProject,
                                    version = currentVersion, checkingUpdate = checkingUpdate,
                                    onCheckUpdate = onCheckUpdate, onOpenLicenses = onOpenLicenses,
                                    onPickOutputFolder = onPickFolder, onResetOutputFolder = onResetFolder,
                                    onUnlockDev = onUnlockDev, onOpenDevFiles = onOpenDevFiles, onDisableDev = onDisableDev)
                            }
                        }
                    }

                    // Licenses / file browser slide over the pages (which stay composed underneath).
                    AnimatedVisibility(
                        visible = subPage != null,
                        enter = slideInHorizontally(initialOffsetX = { it / 3 }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it / 3 }) + fadeOut(),
                    ) {
                        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)
                            .pointerInput(Unit) { detectTapGestures { } }) {   // don't let touches reach the pages below
                            val insets = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
                            when (lastSubPage) {
                                SubPage.Licenses -> LicensesPage(insets)
                                SubPage.DevFiles -> DevFileExplorer(root = b.appDataDir, insets = insets, onOpenFile = { f -> openDevFile(f) }, onExportFile = { f -> exportDevFile(f) })
                            }
                        }
                    }
                }

                // Status, actions and the tab bar float over the bottom of the page; they slide away while a
                // sub page is open.
                AnimatedVisibility(
                    visible = subPage == null,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                ) {
                  Column(
                    Modifier.fillMaxWidth()
                        .padding(bottom = barInset)
                        .onSizeChanged { bottomPx = it.height },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(modifier = Modifier.weight(1f), enabled = ready && !busy, onClick = { buildZip() }) { Text("Build zip") }
                        val missing = if (ready) missingFields() else emptyList()
                        val canCompile = ready && missing.isEmpty()
                        Button(
                            modifier = Modifier.weight(1f), enabled = !busy,
                            colors = if (canCompile) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
                            onClick = { onCompilePressed() },
                        ) { Text("Compile") }
                    }
                    if (useGlass) {
                        val painters = tabIcons.map { rememberVectorPainter(it) }
                        val contentColor = if (dark) Color.White else Color.Black
                        LiquidBottomTabs(
                            selectedTabIndex = { barIndex },
                            onTabSelected = { onTab(it) },
                            backdrop = backdrop,
                            tabsCount = TAB_LABELS.size,
                            dark = dark,
                            accentColor = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
                        ) {
                            TAB_LABELS.forEachIndexed { i, label ->
                                LiquidBottomTab({ onTab(i) }) {
                                    Box(Modifier.size(28.dp).paint(painters[i], colorFilter = ColorFilter.tint(contentColor)))
                                    BasicText(label, style = TextStyle(contentColor, 12.sp))
                                }
                            }
                        }
                    } else {
                        NavigationBar {
                            TAB_LABELS.forEachIndexed { i, label ->
                                NavigationBarItem(selected = barIndex == i, onClick = { onTab(i) }, icon = tabIcons[i], label = label)
                            }
                        }
                    }
                  }
                }

                // ---- sheet + dialogs (inside the Scaffold so Miuix can host them) ------------------
                OverlayBottomSheet(show = showImport, title = "Import", onDismissRequest = { closeImportSheet() }) {
                    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OverlayDropdownPreference(
                            items = IMPORT_KINDS, selectedIndex = importKind, title = "Source",
                            onSelectedIndexChange = { importKind = it },
                        )
                        if (importKind < 2) {
                            TextField(value = url, onValueChange = { url = it }, label = "Level link",
                                singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                        } else {
                            Text("Pick a project .zip exported by this app or the desktop builder.",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            enabled = ready && !busy && (importKind == 2 || url.isNotBlank()),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            onClick = { requestImport() },
                        ) { Text(if (importKind == 2) "Choose .zip…" else "Import") }
                    }
                }

                // The per-run compile choices, mirroring the Import sheet.
                OverlayBottomSheet(show = showCompile, title = "Compile", onDismissRequest = { closeCompileSheet() }) {
                    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SwitchPreference(checked = compileZip, onCheckedChange = { compileZip = it },
                            title = "Also build project ZIP")
                        Button(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            onClick = { closeCompileSheet(); compile(compileZip) },
                        ) { Text("Compile") }
                    }
                }

                // While Python works, lock the whole app behind an unclosable progress dialog.
                OverlayDialog(show = busy, title = "Working…", onDismissRequest = {}) {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(status, style = MiuixTheme.textStyles.footnote1, modifier = Modifier.fillMaxWidth())
                    }
                }

                ConfirmDialog(
                    show = pendingOverwrite != null, title = "Overwrite everything?",
                    body = "Importing replaces every field, file and chart you have filled in. This can't be undone.",
                    confirmText = "Overwrite",
                    onConfirm = { confirmOverwrite() },
                    onDismiss = { pendingOverwrite = null },
                )
                ConfirmDialog(
                    show = confirmReset, title = "Start a new project?",
                    body = "This clears every field, file and chart.", confirmText = "Clear",
                    onConfirm = { confirmNewProject() },
                    onDismiss = { confirmReset = false },
                )
                ConfirmDialog(
                    show = unsupported != null, title = "Unsupported chart features",
                    body = "This chart uses features the converter does not support:\n\n${unsupported?.second ?: ""}\n\n" +
                        "Continue anyway? Unsupported parts are skipped or replaced with the closest equivalent.",
                    confirmText = "Continue",
                    onConfirm = { confirmUnsupported() },
                    onDismiss = { unsupported = null },
                )
                ConfirmDialog(
                    show = updateTag != null, title = "Update available",
                    body = "Version ${updateTag ?: ""} is out" +
                        (if (updateTag?.let { UpdateChecker.isUnstable(it) } == true) " (pre-release)" else "") +
                        " (you have $currentVersion).\n\nOpen the release page in your browser to download it?",
                    confirmText = "Update",
                    onConfirm = { confirmUpdate() },
                    onDismiss = { updateTag = null },
                )
                ConfirmDialog(
                    show = unstableTag != null, title = "Unstable build",
                    body = "${unstableTag ?: ""} is a pre-release (alpha, beta, preview or release candidate). " +
                        "It may not be stable, and things are subject to change.\n\nOpen it anyway?",
                    confirmText = "Open anyway",
                    onConfirm = { confirmUnstable() },
                    onDismiss = { unstableTag = null },
                )
                HexDialog(devHex, onDismiss = { devHex = null })
                InfoDialog(show = devFileText != null, title = devFileText?.first, body = devFileText?.second ?: "",
                    onDismiss = { devFileText = null })
                InfoDialog(show = message != null, title = null, body = message ?: "", onDismiss = { message = null })
                InfoDialog(show = errors != null, title = "Cannot build",
                    body = errors?.joinToString("\n") { "• $it" } ?: "", onDismiss = { errors = null })
            }
        }
  }
}

@Composable
private fun DialogBody(text: String) {
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
        Text(text, color = MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
private fun InfoDialog(show: Boolean, title: String?, body: String, onDismiss: () -> Unit) {
    OverlayDialog(show = show, title = title, onDismissRequest = onDismiss) {
        Column {
            DialogBody(body)
            TextButton(
                text = "OK", onClick = onDismiss, colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    show: Boolean, title: String, body: String, confirmText: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    OverlayDialog(show = show, title = title, onDismissRequest = onDismiss) {
        Column {
            DialogBody(body)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(text = confirmText, onClick = onConfirm, colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.weight(1f))
            }
        }
    }
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

@Composable
private fun HexDialog(file: HexFile?, onDismiss: () -> Unit) {
    OverlayDialog(show = file != null, title = file?.name, onDismissRequest = onDismiss) {
        Column {
            if (file != null) {
                val shown = if (file.bytes.size.toLong() < file.totalSize)
                    "First ${file.bytes.size} of ${file.totalSize} bytes. Hold the file in the list to export all of it."
                else "${file.totalSize} bytes. Hold the file in the list to export it."
                Text(shown, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 8.dp))
                val mono = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items((file.bytes.size + HEX_BYTES_PER_LINE - 1) / HEX_BYTES_PER_LINE) { line ->
                            Text(hexLine(file.bytes, line), style = mono, maxLines = 1, color = MiuixTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            TextButton(
                text = "OK", onClick = onDismiss, colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}
