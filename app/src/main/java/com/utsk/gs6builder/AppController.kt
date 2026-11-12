package com.utsk.gs6builder

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.chaquo.python.PyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.function.Consumer

/** Tab order: Import is an action (opens a sheet), the other three are real pages. */
internal val TAB_LABELS = listOf("Import", "Files", "Metadata", "Settings")
internal val IMPORT_KINDS = listOf("UntitledCharts link", "Chart Cyanvas (CC Archive) link", ".zip project")

/** Full-screen pages that slide over the tab pages (opened from Settings / dev mode). */
internal enum class SubPage { Licenses, DevFiles, AppInfo }

/**
 * Everything the app *does*, with no UI in it: the project [Store], dialog/sheet flags, Python calls,
 * imports, compiles, update checks and the dev-mode file viewer. Both UI modes (Miuix and Material)
 * render this same object, so neither has to know about the other and switching style keeps your work.
 */
internal class AppController(
    val b: Backend,
    val s: AppSettings,
    val scope: CoroutineScope,
    val context: Context,
    private val haptic: HapticFeedback,
    val pagerState: PagerState,
) {
    val st = Store().also { it.loadFresh(freshProjectJson()) }

    var status by mutableStateOf("Starting…")
    var busy by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var errors by mutableStateOf<List<String>?>(null)
    var unsupported by mutableStateOf<Pair<JSONObject, String>?>(null)

    var showImport by mutableStateOf(false)
    var importKind by mutableIntStateOf(0)
    var url by mutableStateOf("")
    var pendingOverwrite by mutableStateOf<(() -> Unit)?>(null)
    var confirmReset by mutableStateOf(false)

    // Per-run switch, chosen in the sheet that opens when Compile is pressed.
    var showCompile by mutableStateOf(false)
    var compileZip by mutableStateOf(false)

    // Licenses page + dev-mode private-data explorer. [lastSubPage] outlives [subPage] so the page keeps
    // its content while it slides out.
    var subPage by mutableStateOf<SubPage?>(null)
    var lastSubPage by mutableStateOf(SubPage.Licenses)
    fun openSubPage(p: SubPage) { lastSubPage = p; subPage = p }
    var devFileText by mutableStateOf<Pair<String, String>?>(null)
    var devHex by mutableStateOf<HexFile?>(null)
    private var pendingExport: File? = null

    // Update check: runs once per launch (if enabled in settings) and on demand from Settings.
    val currentVersion = UpdateChecker.currentVersion(context)
    var updateTag by mutableStateOf<String?>(null)
    var unstableTag by mutableStateOf<String?>(null)   // pre-release warning, shown after "Update"
    var checkingUpdate by mutableStateOf(false)

    // Page scroll positions live here (not in the shells) so they survive opening sub pages and switching style.
    val filesScroll = ScrollState(0)
    val metaScroll = ScrollState(0)
    val settingsScroll = ScrollState(0)

    /** 0 = Import (while its sheet is open), 1..3 = Files, Metadata, Settings. The pager is the source of truth. */
    val currentTabIndex: Int get() = if (showImport) 0 else pagerState.targetPage + 1

    /**
     * Fresh empty project. Normally comes from Python; if Python isn't available (e.g. a UI-preview
     * build without the bundled requirements) fall back to the same schema in Kotlin so the UI
     * still opens instead of crashing.
     */
    fun freshProjectJson(): String =
        runCatching { b.call("new_project") }.getOrElse {
            """
            {"jacket":"","track":"","track_pre":"","mvs":[],
             "charts":[{"difficulty":"master","level":1,"path":"","lenient":false}],
             "title":"","charter":"","lyricist":"","composer":"","arranger":"","artist":"","vocals":"",
             "collab":"","original":"","is_full":true,"offset_ms":0,"mv_offset_ms":0,
             "default_vocal_name":"","default_vocal_type":"original_song","alt_vocals":[],
             "identifier":"${java.util.UUID.randomUUID()}_${java.util.UUID.randomUUID()}"}
            """.trimIndent()
        }

    // Set by [rememberAppController]: they wrap the activity-result launchers.
    internal var openDocument: (Array<String>) -> Unit = {}
    internal var openTree: () -> Unit = {}
    internal var createDocument: (String) -> Unit = {}
    private var pendingPick: ((Uri) -> Unit)? = null

    /** Short tick for taps, switches and tab changes; silenced by the haptics setting.
     *  Debounced, because a tap reaches [onTab] both directly and through the glass bar's
     *  selector-arrival callback. */
    private var lastTickAt = 0L
    fun tick() {
        val now = android.os.SystemClock.uptimeMillis()
        if (s.haptics && now - lastTickAt > 80) {
            lastTickAt = now
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /** Stronger pulse for long-press actions; silenced by the haptics setting. */
    fun longPress() {
        if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun checkForUpdate(manual: Boolean) {
        if (checkingUpdate) return
        checkingUpdate = true
        scope.launch {
            val r = UpdateChecker.fetchLatestTag(includeUnstable = s.updateChannel == 1)
            checkingUpdate = false
            r.onSuccess { tag ->
                if (UpdateChecker.isNewer(tag, currentVersion)) updateTag = tag
                else if (manual) message = "You're up to date ($currentVersion)."
            }.onFailure { e ->
                if (manual) message = "Couldn't check for updates: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

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

    // ---- file pickers ------------------------------------------------------------------------
    fun pick(mimes: Array<String>, cb: (Uri) -> Unit) { pendingPick = cb; openDocument(mimes) }
    internal fun onDocumentPicked(uri: Uri?) {
        val cb = pendingPick; pendingPick = null
        if (uri != null && cb != null) cb(uri)
    }

    /** Pick → copy into app storage → hand the real path over. */
    val pickFile: PickFile = { slot, mimes, onPath ->
        pick(mimes) { uri -> scope.launch { onPath(withContext(Dispatchers.IO) { b.copyIn(uri, slot) }) } }
    }

    internal fun onTreePicked(uri: Uri?) {
        if (uri != null) {
            try {
                b.grantTree(uri, s.outputDir)
                s.outputDir = uri.toString()
                message = "Charts and zips will be saved to ${describeOutputFolder(s.outputDir)}."
            } catch (e: Exception) {
                message = "Couldn't use that folder: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    internal fun onExportTarget(uri: Uri?) {
        val f = pendingExport; pendingExport = null
        if (uri != null && f != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { b.exportTo(f, uri) }
                message = "Exported ${f.name}."
            } catch (e: Exception) {
                message = "Couldn't export ${f.name}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    suspend fun startBackend() {
        try { withContext(Dispatchers.IO) { b.init() }; ready = true; status = "" }
        catch (e: Exception) { message = e.message ?: e.toString(); status = "Setup failed" }
    }

    // ---- build / compile ---------------------------------------------------------------------
    private fun errorList(res: JSONObject): List<String> {
        val e = res.getJSONArray("errors")
        return (0 until e.length()).map { e.getString(it) }
    }

    private fun savedText(res: JSONObject, pathsKey: String?): String {
        val files = if (pathsKey == null) listOf(res.getString("path"))
        else res.getJSONArray(pathsKey).let { a -> (0 until a.length()).map { a.getString(it) } }
        val saved = files.map { b.saveOutput(File(it), s.outputDir) }
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

    fun compile(alsoZip: Boolean) = runPy("Compiling…") {
        val (text, errs) = withContext(Dispatchers.IO) {
            val r = JSONObject(b.call("compile_chart", st.p.toString(), b.outDir.absolutePath, true, log))
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

    /** The Compile button: explains what's missing, or opens the compile sheet. */
    fun onCompilePressed() {
        if (!ready) { message = "Still starting up, give it a few seconds."; return }
        val why = missingFields()
        if (why.isNotEmpty()) errors = why else showCompile = true
    }

    // ---- import ------------------------------------------------------------------------------
    private fun importProject(json: String, extra: String? = null) {
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
    fun closeImportSheet() { showImport = false }

    private fun doLinkImport() {
        val site = if (importKind == 0) "untitledcharts" else "ccarchive"
        val link = url.trim()
        closeImportSheet()
        runPy("Importing…") {
            val json = withContext(Dispatchers.IO) { b.call("import_level", site, link, b.projectDir.absolutePath, log) }
            importProject(json, "Imported from $site.")
            url = ""
        }
    }

    private fun doZipImport() {
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

    // ---- dev mode ----------------------------------------------------------------------------
    fun unlockDevTab() {
        longPress()
        message = if (s.devMode) "Developer mode is already on." else "Developer mode enabled. Look for it at the bottom of Settings."
        s.devMode = true
    }

    /** Open a private-data file for the dev-mode viewer: text as text, anything binary in the hex viewer. */
    fun openDevFile(f: File) {
        scope.launch {
            val head = withContext(Dispatchers.IO) { readHead(f, DEV_VIEW_LIMIT) }
            when {
                looksBinary(head) -> devHex = HexFile(f.name, head, f.length())
                f.length() > DEV_VIEW_LIMIT ->
                    devFileText = f.name to "(file too large to show in-app; hold it in the list to export it)"
                else -> devFileText = f.name to String(head, Charsets.UTF_8).ifBlank { "(empty file)" }
            }
        }
    }

    /** Hold a file in the explorer: pick somewhere to write a copy of it. */
    fun exportDevFile(f: File) {
        longPress()
        pendingExport = f
        createDocument(f.name)
    }

    // ---- navigation --------------------------------------------------------------------------
    fun onTab(i: Int) {
        if (i == 0) {
            if (!showImport) { tick(); showImport = true }
        } else if (pagerState.targetPage != i - 1) {
            // Own job per tap: a newer tap or a swipe cancels the running scroll without touching anything else.
            tick()
            scope.launch { pagerState.animateScrollToPage(i - 1) }
        }
    }

    // ---- callbacks handed to the pages. They are fields (not recreated), so the pages that stay
    // composed side by side don't recompose just because the shell did. ---------------------------
    val onPickFile: PickFile get() = pickFile
    val onCheckRow: (JSONObject, Boolean) -> Unit = { row, lenient -> checkRow(row, lenient) }
    val onRegen: () -> Unit = {
        runCatching { b.call("new_uuid") }.getOrElse {
            java.util.UUID.randomUUID().toString() + "_" + java.util.UUID.randomUUID().toString()
        }.let { st.set("identifier", it) }
    }
    val onCopy: () -> Unit = { b.copyText(st.str("identifier")); status = "Identifier copied." }
    val onNewProject: () -> Unit = { confirmReset = true }
    val onCheckUpdate: () -> Unit = { checkForUpdate(true) }
    val onOpenLicenses: () -> Unit = { openSubPage(SubPage.Licenses) }
    val onOpenAppInfo: () -> Unit = { openSubPage(SubPage.AppInfo) }
    val onPickFolder: () -> Unit = { openTree() }
    val onResetFolder: () -> Unit = { b.releaseTree(s.outputDir); s.outputDir = "" }
    val onUnlockDev: () -> Unit = { unlockDevTab() }
    val onOpenDevFiles: () -> Unit = { openSubPage(SubPage.DevFiles) }
    val onDisableDev: () -> Unit = { s.devMode = false }

    /** Dialog confirm actions shared by both UIs. */
    fun confirmOverwrite() { val a = pendingOverwrite; pendingOverwrite = null; a?.invoke() }
    fun confirmNewProject() { confirmReset = false; st.loadFresh(freshProjectJson()); status = "New project." }
    fun confirmUnsupported() { val row = unsupported?.first; unsupported = null; if (row != null) checkRow(row, true) }
    fun confirmUpdate() {
        val t = updateTag; updateTag = null
        if (t != null) { if (UpdateChecker.isUnstable(t)) unstableTag = t else UpdateChecker.openRelease(context, t) }
    }
    fun confirmUnstable() { val t = unstableTag; unstableTag = null; if (t != null) UpdateChecker.openRelease(context, t) }
}

/** Creates the [AppController] once (above both UI modes) and wires up its activity-result launchers and startup work. */
@Composable
internal fun rememberAppController(b: Backend, s: AppSettings): AppController {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val c = remember { AppController(b, s, scope, context, haptic, pagerState) }

    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { c.onDocumentPicked(it) }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { c.onTreePicked(it) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { c.onExportTarget(it) }
    c.openDocument = { mimes -> docLauncher.launch(mimes) }
    c.openTree = { treeLauncher.launch(null) }
    c.createDocument = { name -> exportLauncher.launch(name) }

    LaunchedEffect(Unit) { c.startBackend() }

    var bootChecked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!bootChecked) {
            bootChecked = true
            if (s.updateCheck) c.checkForUpdate(false)
        }
    }
    return c
}
