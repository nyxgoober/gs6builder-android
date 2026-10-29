package com.utsk.gs6builder

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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

/** Tab order: Import is an action (opens a sheet), the other three are real pages. */
private val TAB_LABELS = listOf("Import", "Files", "Metadata", "Settings")
private val IMPORT_KINDS = listOf("UntitledCharts link", "Chart Cyanvas (CC Archive) link", ".zip project")

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

    // Inter, applied to every Miuix text style.
    val inter = remember { FontFamily(Font(R.font.inter_variable)) }
    val interStyles = remember(inter) {
        val d = defaultTextStyles()
        d.copy(
            main = d.main.copy(fontFamily = inter), paragraph = d.paragraph.copy(fontFamily = inter),
            body1 = d.body1.copy(fontFamily = inter), body2 = d.body2.copy(fontFamily = inter),
            button = d.button.copy(fontFamily = inter), footnote1 = d.footnote1.copy(fontFamily = inter),
            footnote2 = d.footnote2.copy(fontFamily = inter), headline1 = d.headline1.copy(fontFamily = inter),
            headline2 = d.headline2.copy(fontFamily = inter), subtitle = d.subtitle.copy(fontFamily = inter),
            title1 = d.title1.copy(fontFamily = inter), title2 = d.title2.copy(fontFamily = inter),
            title3 = d.title3.copy(fontFamily = inter), title4 = d.title4.copy(fontFamily = inter),
        )
    }
    MiuixTheme(controller = controller, textStyles = interStyles) { App(b, s, dark) }
}


@Composable
fun App(b: Backend, s: AppSettings, dark: Boolean) {
    val st = remember { Store().also { it.loadFresh(b.call("new_project")) } }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var page by remember { mutableIntStateOf(1) }       // 1 Files, 2 Metadata, 3 Settings
    var barIndex by remember { mutableIntStateOf(1) }   // which tab the bar highlights (0 while the import sheet is open)
    var status by remember { mutableStateOf("Starting…") }
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableStateOf<List<String>?>(null) }
    var unsupported by remember { mutableStateOf<Pair<JSONObject, String>?>(null) }

    var showImport by remember { mutableStateOf(false) }
    var importKind by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf("") }
    var pendingOverwrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    // Per-run switches, chosen in the sheet that opens when Compile is pressed.
    var showCompile by remember { mutableStateOf(false) }
    var compileIos by remember { mutableStateOf(false) }
    var compileZip by remember { mutableStateOf(false) }

    var bottomPx by remember { mutableIntStateOf(0) }
    val filesScroll = rememberScrollState()
    val metaScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()
    val backdrop = rememberLayerBackdrop()
    val useGlass = s.glassBar && isRuntimeShaderSupported()

    // ---- helpers -------------------------------------------------------------------------
    /** Everything required before a compile is meaningful, human-readable for the "why not" dialog. */
    fun missingFields(): List<String> {
        val missing = mutableListOf<String>()
        fun need(ok: Boolean, label: String) { if (!ok) missing.add(label) }
        need(st.str("jacket").isNotBlank(), "Jacket (image)")
        need(st.str("track").isNotBlank(), "Audio track")
        need(st.str("track_pre").isNotBlank(), "Preview audio")
        var hasChart = false
        val charts = st.arr("charts")
        for (i in 0 until charts.length()) if (charts.getJSONObject(i).optString("path").isNotBlank()) hasChart = true
        need(hasChart, "At least one chart file")
        for ((key, label) in listOf(
            "title" to "Title", "charter" to "Charter", "lyricist" to "Lyricist", "composer" to "Composer",
            "arranger" to "Arranger", "artist" to "Artist", "vocals" to "Vocals",
        )) need(st.str(key).isNotBlank(), label)
        if (st.arr("alt_vocals").length() > 0 && st.str("default_vocal_name").isBlank())
            missing.add("Default vocal name")
        return missing
    }

    fun runPy(label: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true; status = label
        scope.launch {
            try { block() }
            catch (e: PyException) { message = e.message ?: "Python error" }
            catch (e: Exception) { message = e.message ?: e.toString() }
            finally { busy = false; status = "" }
        }
    }
    /** Python progress lines, translated to something a chart maker cares about. */
    val log = Consumer<String> { raw ->
        status = when {
            raw.startsWith("ffmpeg wav:") || raw.startsWith("ACB:") -> "Packing ACB audio…"
            raw.startsWith("ffmpeg m1v:") || raw.startsWith("USM:") || raw.contains("live/2dmode") -> "Processing video…"
            raw.contains("] jacket") -> "Processing jacket…"
            raw.contains("music_score") -> "Preparing music scores…"
            raw.contains("music/") -> "Packing music…"
            raw.startsWith("packing ") -> "Packing chart bundle…"
            raw.startsWith("preparing charts") -> "Preparing charts…"
            else -> raw
        }
    }

    fun checkRow(row: JSONObject, lenient: Boolean) = runPy("Reading chart…") {
        val r = JSONObject(withContext(Dispatchers.IO) { b.call("check_chart", row.getString("path"), lenient) })
        when (r.getString("status")) {
            "ok" -> { row.put("combo", r.getInt("combo").toString()); row.put("lenient", lenient) }
            "unsupported" -> {
                val f = r.getJSONArray("features")
                unsupported = row to (0 until minOf(f.length(), 20)).joinToString("\n") { "• " + f.getString(it) }
                row.put("combo", "?")
            }
            else -> { row.put("combo", "err"); message = r.optString("message") }
        }
        st.touch()
    }

    var pendingPick by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cb = pendingPick; pendingPick = null
        if (uri != null && cb != null) cb(uri)
    }
    fun pick(mimes: Array<String>, cb: (Uri) -> Unit) { pendingPick = cb; launcher.launch(mimes) }
    /** Pick → copy into app storage → hand the real path over. */
    val pickFile: PickFile = { slot, mimes, onPath ->
        pick(mimes) { uri -> scope.launch { onPath(withContext(Dispatchers.IO) { b.copyIn(uri, slot) }) } }
    }

    LaunchedEffect(Unit) {
        try { withContext(Dispatchers.IO) { b.init() }; ready = true; status = "" }
        catch (e: Exception) { message = e.message ?: e.toString(); status = "Setup failed" }
    }

    fun errorList(res: JSONObject): List<String> {
        val e = res.getJSONArray("errors")
        return (0 until e.length()).map { e.getString(it) }
    }
    fun savedText(res: JSONObject, pathsKey: String?): String {
        val files = if (pathsKey == null) listOf(res.getString("path"))
        else res.getJSONArray(pathsKey).let { a -> (0 until a.length()).map { a.getString(it) } }
        val saved = files.map { b.saveToDownloads(File(it)) }
        return "Saved:\n" + saved.joinToString("\n") +
            (if (pathsKey != null) "\n\nIdentifier (keep it to update this chart later):\n" + res.getString("identifier") else "")
    }

    fun buildZip() = runPy("Building project zip…") {
        val (text, errs) = withContext(Dispatchers.IO) {
            val r = JSONObject(b.call("build_zip", st.p.toString(), b.outDir.absolutePath))
            val e = errorList(r)
            if (e.isNotEmpty()) "" to e else savedText(r, null) to emptyList<String>()
        }
        if (errs.isNotEmpty()) errors = errs else message = text
    }

    fun compile(includeIos: Boolean, alsoZip: Boolean) = runPy("Compiling…") {
        val (text, errs) = withContext(Dispatchers.IO) {
            val r = JSONObject(b.call("compile_chart", st.p.toString(), b.outDir.absolutePath, includeIos, log))
            val e = errorList(r)
            if (e.isNotEmpty()) return@withContext "" to e
            val parts = mutableListOf(savedText(r, "outputs"))
            if (alsoZip) {
                val z = JSONObject(b.call("build_zip", st.p.toString(), b.outDir.absolutePath))
                val ze = errorList(z)
                if (ze.isNotEmpty()) return@withContext "" to ze
                parts += savedText(z, null)
            }
            parts.joinToString("\n\n") to emptyList<String>()
        }
        if (errs.isNotEmpty()) errors = errs else message = text
        if (errs.isEmpty() && s.autoCopyId) b.copyText(st.str("identifier"))
    }

    fun importProject(json: String, extra: String? = null) {
        val o = JSONObject(json)
        val notes = o.optJSONObject("_notes"); o.remove("_notes")
        st.load(o.toString())
        for (i in 0 until st.arr("charts").length()) {
            val row = st.arr("charts").getJSONObject(i)
            if (row.optString("path").isNotBlank()) checkRowQuiet(scope, b, row, st)
        }
        message = (extra ?: "Imported.") +
            (if (notes?.optBoolean("preview_cut") == true) "\n\nPreview cut to the first 45 s; replace it if you like." else "") +
            "\n\nCheck difficulty/level, then fill in the remaining required metadata (Lyricist, Composer, Arranger, Vocals)."
    }

    fun closeCompileSheet() { showCompile = false }

    fun closeImportSheet() { showImport = false; barIndex = page }

    fun doLinkImport() {
        val site = if (importKind == 0) "untitledcharts" else "ccarchive"
        val link = url.trim()
        closeImportSheet()
        runPy("Importing…") {
            val json = withContext(Dispatchers.IO) { b.call("import_level", site, link, b.projectDir.absolutePath, log) }
            importProject(json, "Imported from $site.")
            url = ""
        }
    }

    fun doZipImport() {
        closeImportSheet()
        pick(ANY) { uri ->
            runPy("Importing zip…") {
                val zip = withContext(Dispatchers.IO) { b.copyIn(uri, "import_zip") }
                val json = withContext(Dispatchers.IO) { b.call("import_zip", zip, File(b.projectDir, "imported").absolutePath) }
                importProject(json, "Imported the project zip.")
            }
        }
    }

    /** Anything filled in? Ask before an import replaces it. */
    fun requestImport() {
        val action: () -> Unit = if (importKind == 2) { { doZipImport() } } else { { doLinkImport() } }
        if (st.isDirty()) pendingOverwrite = action else action()
    }

    fun onTab(i: Int) {
        if (i == 0) { showImport = true; barIndex = 0 } else { page = i; barIndex = i }
    }

    // ---- layout --------------------------------------------------------------------------
    val tabIcons = listOf(MiuixIcons.Import, MiuixIcons.Folder, MiuixIcons.Edit, MiuixIcons.Settings)

    Scaffold(topBar = { SmallTopAppBar(title = TAB_LABELS[page]) }) { pad ->
        Box(Modifier.fillMaxSize()) {
            // Page content. It is also the backdrop the glass bar refracts.
            // A real pager: pages slide along the bar axis and never overlap.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MiuixTheme.colorScheme.surface)) {
                val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.collect { p ->
                        val np = p + 1
                        if (page != np) { page = np; barIndex = np }
                    }
                }
                LaunchedEffect(page) {
                    if (pagerState.settledPage != page - 1) pagerState.animateScrollToPage(page - 1)
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { p ->
                    val scroll = when (p) { 0 -> filesScroll; 1 -> metaScroll; else -> settingsScroll }
                    Column(
                        Modifier.fillMaxSize().verticalScroll(scroll)
                            .padding(top = pad.calculateTopPadding(),
                                bottom = with(density) { bottomPx.toDp() } + pad.calculateBottomPadding())
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (p) {
                            0 -> FilesPage(st, pickFile, ::checkRow)
                            1 -> MetadataPage(st,
                                onRegen = { st.set("identifier", b.call("new_uuid")) },
                                onCopy = { b.copyText(st.str("identifier")); status = "Identifier copied." })
                            else -> SettingsPage(s, onNewProject = { confirmReset = true })
                        }
                    }
                }
            }

            // Status, actions and the tab bar float over the bottom of the page.
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(bottom = pad.calculateBottomPadding())
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
                        onClick = {
                            when {
                                !ready -> message = "Still starting up, give it a few seconds."
                                else -> {
                                    val why = missingFields()
                                    if (why.isNotEmpty()) errors = why else showCompile = true
                                }
                            }
                        },
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
                    SwitchPreference(checked = compileIos, onCheckedChange = { compileIos = it },
                        title = "Also compile iOS version")
                    SwitchPreference(checked = compileZip, onCheckedChange = { compileZip = it },
                        title = "Also build project ZIP")
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        onClick = { closeCompileSheet(); compile(compileIos, compileZip) },
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
                onConfirm = { val a = pendingOverwrite; pendingOverwrite = null; a?.invoke() },
                onDismiss = { pendingOverwrite = null },
            )
            ConfirmDialog(
                show = confirmReset, title = "Start a new project?",
                body = "This clears every field, file and chart.", confirmText = "Clear",
                onConfirm = {
                    confirmReset = false
                    st.loadFresh(b.call("new_project")); status = "New project."
                },
                onDismiss = { confirmReset = false },
            )
            ConfirmDialog(
                show = unsupported != null, title = "Unsupported chart features",
                body = "This chart uses features the converter does not support:\n\n${unsupported?.second ?: ""}\n\n" +
                    "Continue anyway? Unsupported parts are skipped or replaced with the closest equivalent.",
                confirmText = "Continue",
                onConfirm = { val row = unsupported?.first; unsupported = null; if (row != null) checkRow(row, true) },
                onDismiss = { unsupported = null },
            )
            InfoDialog(show = message != null, title = null, body = message ?: "", onDismiss = { message = null })
            InfoDialog(show = errors != null, title = "Cannot build",
                body = errors?.joinToString("\n") { "• $it" } ?: "", onDismiss = { errors = null })
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
private fun checkRowQuiet(scope: CoroutineScope, b: Backend, row: JSONObject, st: Store) {
    scope.launch {
        val r = JSONObject(withContext(Dispatchers.IO) { b.call("check_chart", row.getString("path"), true) })
        row.put("combo", if (r.getString("status") == "ok") r.getInt("combo").toString() else "err")
        st.touch()
    }
}
