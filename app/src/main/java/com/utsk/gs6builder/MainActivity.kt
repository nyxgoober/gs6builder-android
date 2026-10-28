package com.utsk.gs6builder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.function.Consumer

private val DIFFICULTIES = listOf("easy", "normal", "hard", "expert", "master", "append")
private val MV_TYPES = listOf("ogmv", "2dmv")
private val VOCAL_TYPES = listOf("sekai", "original_song", "another_vocal")
private val IMAGE = arrayOf("image/*")
private val AUDIO = arrayOf("audio/*", "video/*", "application/ogg")
private val VIDEO = arrayOf("video/*")
private val ANY = arrayOf("*/*")

/** Project form state: one JSONObject (same schema gs6_api.py expects) + a recomposition counter. */
class Store {
    private var rev by mutableIntStateOf(0)
    var p = JSONObject()
        private set

    fun str(k: String): String { rev; return p.optString(k, "") }
    fun bool(k: String): Boolean { rev; return p.optBoolean(k, true) }
    fun arr(k: String): JSONArray { rev; return p.getJSONArray(k) }
    fun set(k: String, v: Any) { p.put(k, v); rev++ }
    fun touch() { rev++ }
    fun load(json: String) { p = JSONObject(json); rev++ }
}

/** Everything that touches Python, the file system and MediaStore. */
class Backend(private val ctx: Context) {
    private val py by lazy { Python.getInstance().getModule("bridge") }
    val projectDir = File(ctx.filesDir, "project").apply { mkdirs() }
    val outDir = File(ctx.cacheDir, "out").apply { mkdirs() }

    fun call(name: String, vararg args: Any?): String = py.callAttr(name, *args)?.toString() ?: ""

    /** Extract bases from assets once, then tell Python where ffmpeg and the bases live. */
    fun init() {
        val bases = File(ctx.filesDir, "bases")
        val stamp = File(bases, ".complete")
        if (!stamp.exists()) {
            bases.mkdirs()
            for (name in ctx.assets.list("bases").orEmpty()) {
                ctx.assets.open("bases/$name").use { i -> File(bases, name).outputStream().use { o -> i.copyTo(o) } }
            }
            stamp.writeText("ok")
        }
        val layout = com.utsk.gs6builder.ffmpeg.FfmpegBinaries.ensureInstalled(ctx)
        check(File(layout.ffmpegPath).exists()) { "libffmpeg_bin.so missing from the APK (see README)" }
        // Must be set before Python starts so ffmpeg subprocesses inherit it.
        android.system.Os.setenv("LD_LIBRARY_PATH", layout.libDir, true)
        call("init", layout.ffmpegPath, bases.absolutePath)
    }

    private fun displayName(uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "file"

    /** Copy a picked document into the app dir (Python needs real paths; the name keeps its extension). */
    fun copyIn(uri: Uri, slot: String): String {
        val dir = File(projectDir, slot).apply { deleteRecursively(); mkdirs() }
        val dst = File(dir, displayName(uri).replace(Regex("[/\\\\]"), "_"))
        ctx.contentResolver.openInputStream(uri)!!.use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
        return dst.absolutePath
    }

    /** Save into the public Downloads folder; returns a human-readable location. */
    fun saveToDownloads(src: File): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, src.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GS6Charts")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        ctx.contentResolver.openOutputStream(uri)!!.use { o -> src.inputStream().use { it.copyTo(o) } }
        return "Downloads/GS6Charts/${src.name}"
    }

    fun copyText(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("identifier", text))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        val backend = Backend(this)
        setContent { MaterialTheme { App(backend) } }
    }
}

@Composable
fun App(b: Backend) {
    val st = remember { Store().also { it.load(b.call("new_project")) } }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Starting…") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableStateOf<List<String>?>(null) }
    var linkSite by remember { mutableStateOf<String?>(null) }
    var unsupported by remember { mutableStateOf<Pair<JSONObject, String>?>(null) }
    var includeIos by remember { mutableStateOf(false) }
    var importMenu by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }

    // ---- helpers -------------------------------------------------------------------------
    fun runPy(label: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true; status = label
        scope.launch {
            try { block() }
            catch (e: PyException) { message = e.message ?: "Python error" }
            catch (e: Exception) { message = e.message ?: e.toString() }
            finally { busy = false }
        }
    }
    val log = Consumer<String> { status = it }

    fun checkRow(row: JSONObject, lenient: Boolean) = runPy("Reading chart…") {
        val r = JSONObject(withContext(Dispatchers.IO) { b.call("check_chart", row.getString("path"), lenient) })
        when (r.getString("status")) {
            "ok" -> { row.put("combo", r.getInt("combo").toString()); row.put("lenient", lenient); status = "Chart OK" }
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
    fun pickFile(slot: String, mimes: Array<String>, onPath: (String) -> Unit) = pick(mimes) { uri ->
        scope.launch { onPath(withContext(Dispatchers.IO) { b.copyIn(uri, slot) }) }
    }

    LaunchedEffectOnce {
        try { withContext(Dispatchers.IO) { b.init() }; ready = true; status = "Ready." }
        catch (e: Exception) { message = e.message ?: e.toString(); status = "Setup failed" }
    }

    fun finishOutputs(res: JSONObject, pathsKey: String?) {
        val errs = res.getJSONArray("errors")
        if (errs.length() > 0) { errors = (0 until errs.length()).map { errs.getString(it) }; return }
        val files = if (pathsKey == null) listOf(res.getString("path"))
        else res.getJSONArray(pathsKey).let { a -> (0 until a.length()).map { a.getString(it) } }
        val saved = files.map { b.saveToDownloads(File(it)) }
        message = "Saved:\n" + saved.joinToString("\n") +
            (if (pathsKey != null) "\n\nIdentifier (keep it to update this chart later):\n" + res.getString("identifier") else "")
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

    // ---- layout --------------------------------------------------------------------------
    Scaffold(
        bottomBar = {
            Column(Modifier.padding(8.dp)) {
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        OutlinedButton(onClick = { importMenu = true }, enabled = ready && !busy) { Text("Import…") }
                        DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                            DropdownMenuItem(text = { Text(".zip project") }, onClick = {
                                importMenu = false
                                pick(ANY) { uri -> runPy("Importing zip…") {
                                    val zip = withContext(Dispatchers.IO) { b.copyIn(uri, "import_zip") }
                                    val json = withContext(Dispatchers.IO) {
                                        b.call("import_zip", zip, File(b.projectDir, "imported").absolutePath) }
                                    importProject(json, "Imported the project zip.")
                                } }
                            })
                            DropdownMenuItem(text = { Text("UntitledCharts link") }, onClick = { importMenu = false; linkSite = "untitledcharts" })
                            DropdownMenuItem(text = { Text("CC Archive link") }, onClick = { importMenu = false; linkSite = "ccarchive" })
                        }
                    }
                    OutlinedButton(enabled = ready && !busy, onClick = {
                        runPy("Building zip…") {
                            val r = withContext(Dispatchers.IO) { JSONObject(b.call("build_zip", st.p.toString(), b.outDir.absolutePath)) }
                            withContext(Dispatchers.IO) { finishOutputs(r, null) }
                        }
                    }) { Text("Build zip") }
                    Button(enabled = ready && !busy, onClick = {
                        runPy("Compiling… this can take a while") {
                            val r = withContext(Dispatchers.IO) {
                                JSONObject(b.call("compile_chart", st.p.toString(), b.outDir.absolutePath, includeIos, log)).also {
                                    if (it.getJSONArray("errors").length() == 0) finishOutputs(it, "outputs")
                                }
                            }
                            val errs = r.getJSONArray("errors")
                            if (errs.length() > 0) errors = (0 until errs.length()).map { errs.getString(it) }
                            status = "Done."
                        }
                    }) { Text("Compile") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeIos, { includeIos = it }); Text("Also compile iOS version", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val titles = listOf("Files", "Charts", "Metadata", "Vocals", "Identity")
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                titles.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    0 -> FilesTab(st, ::pickFile)
                    1 -> ChartsTab(st, ::pickFile, ::checkRow)
                    2 -> MetadataTab(st)
                    3 -> VocalsTab(st, ::pickFile)
                    4 -> IdentityTab(st, onRegen = { st.set("identifier", b.call("new_uuid")) }, onCopy = { b.copyText(st.str("identifier")); status = "Identifier copied." })
                }
            }
        }
    }

    // ---- dialogs -------------------------------------------------------------------------
    message?.let { m -> AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton({ message = null }) { Text("OK") } }, text = { Text(m) }) }
    errors?.let { e -> AlertDialog(onDismissRequest = { errors = null }, confirmButton = { TextButton({ errors = null }) { Text("OK") } },
        title = { Text("Cannot build") }, text = { Text(e.joinToString("\n") { "• $it" }) }) }
    unsupported?.let { (row, feats) -> AlertDialog(onDismissRequest = { unsupported = null },
        title = { Text("Unsupported chart features") },
        text = { Text("This chart uses features the converter does not support:\n\n$feats\n\nContinue anyway? Unsupported parts are skipped or replaced with the closest equivalent.") },
        confirmButton = { TextButton({ unsupported = null; checkRow(row, true) }) { Text("Continue") } },
        dismissButton = { TextButton({ unsupported = null }) { Text("Cancel") } }) }
    linkSite?.let { site ->
        var url by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { linkSite = null }, title = { Text("Import from " + if (site == "ccarchive") "CC Archive" else "UntitledCharts") },
            text = { OutlinedTextField(url, { url = it }, label = { Text("Level link") }, singleLine = true) },
            confirmButton = { TextButton({
                linkSite = null
                runPy("Importing…") {
                    val json = withContext(Dispatchers.IO) { b.call("import_level", site, url, b.projectDir.absolutePath, log) }
                    importProject(json, "Imported from $site.")
                }
            }) { Text("Import") } },
            dismissButton = { TextButton({ linkSite = null }) { Text("Cancel") } })
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

@Composable
private fun LaunchedEffectOnce(block: suspend () -> Unit) =
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }

// ---- tabs ------------------------------------------------------------------------------------
private typealias PickFile = (String, Array<String>, (String) -> Unit) -> Unit

@Composable
private fun FileField(label: String, path: String, onPick: () -> Unit, onClear: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(if (path.isBlank()) "— none —" else File(path).name, maxLines = 1)
        }
        OutlinedButton(onPick) { Text("Choose") }
        if (onClear != null) TextButton(onClear) { Text("✕") }
    }
}

@Composable
private fun Dropdown(value: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton({ open = true }) { Text(value) }
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(o) }) }
        }
    }
}

@Composable
private fun Field(label: String, key: String, st: Store, hint: String? = null, number: Boolean = false) {
    OutlinedTextField(
        value = st.str(key), onValueChange = { st.set(key, it) }, label = { Text(label) },
        supportingText = if (hint != null) { { Text(hint) } } else null, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
    )
}

@Composable
private fun FilesTab(st: Store, pickFile: PickFile) {
    FileField("Jacket (image)*", st.str("jacket"), { pickFile("jacket", IMAGE) { st.set("jacket", it) } })
    FileField("Audio track*", st.str("track"), { pickFile("track", AUDIO) { st.set("track", it) } })
    FileField("Preview audio (≤ 45 s)*", st.str("track_pre"), { pickFile("track_pre", AUDIO) { st.set("track_pre", it) } })
    Text("Music videos (optional, up to 2)", style = MaterialTheme.typography.titleSmall)
    val mvs = st.arr("mvs")
    for (i in 0 until mvs.length()) {
        val mv = mvs.getJSONObject(i)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dropdown(mv.getString("kind"), MV_TYPES) { mv.put("kind", it); st.touch() }
            Column(Modifier.weight(1f)) {
                FileField("", mv.optString("path"), { pickFile("mv$i", VIDEO) { mv.put("path", it); st.touch() } },
                    { val a = JSONArray(); for (j in 0 until mvs.length()) if (j != i) a.put(mvs.get(j)); st.set("mvs", a) })
            }
        }
    }
    if (mvs.length() < 2) OutlinedButton({
        mvs.put(JSONObject().put("kind", MV_TYPES[mvs.length() % 2]).put("path", "")); st.touch()
    }) { Text("+ Add music video") }
}

@Composable
private fun ChartsTab(st: Store, pickFile: PickFile, check: (JSONObject, Boolean) -> Unit) {
    Text("1–6 charts: .sus, .usc or Next SEKAI / Chart Cyanvas LevelData (.json, .json.gz, .nsld). Combo is derived automatically.",
        style = MaterialTheme.typography.bodySmall)
    val rows = st.arr("charts")
    for (i in 0 until rows.length()) {
        val r = rows.getJSONObject(i)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown(r.getString("difficulty"), DIFFICULTIES) { r.put("difficulty", it); st.touch() }
                OutlinedTextField(r.optString("level", "1"), { r.put("level", it); st.touch() }, label = { Text("Level 1–99") },
                    singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("combo ${r.optString("combo", "-")}", style = MaterialTheme.typography.bodySmall)
            }
            FileField("Chart file", r.optString("path"),
                { pickFile("chart$i", ANY) { r.put("path", it); r.put("lenient", false); st.touch(); check(r, false) } },
                { val a = JSONArray(); for (j in 0 until rows.length()) if (j != i) a.put(rows.get(j)); st.set("charts", a) })
        }
    }
    if (rows.length() < 6) OutlinedButton({
        val used = (0 until rows.length()).map { rows.getJSONObject(it).getString("difficulty") }.toSet()
        rows.put(JSONObject().put("difficulty", DIFFICULTIES.firstOrNull { it !in used } ?: "master")
            .put("level", "1").put("path", "").put("lenient", false)); st.touch()
    }) { Text("+ Add chart") }
}

@Composable
private fun MetadataTab(st: Store) {
    Field("Title*", "title", st); Field("Charter*", "charter", st); Field("Lyricist*", "lyricist", st)
    Field("Composer*", "composer", st); Field("Arranger*", "arranger", st); Field("Artist*", "artist", st)
    Field("Vocals*", "vocals", st)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(st.bool("is_full"), { st.set("is_full", it) }); Text("Full-length (not a game-size cut)")
    }
    Field("offset_ms", "offset_ms", st, "global chart timing offset (applies to all difficulties)", number = true)
    Field("additional_mv_offset_ms", "mv_offset_ms", st, "global extra MV offset", number = true)
    Field("original (URL)", "original", st, "optional"); Field("Collab (optional)", "collab", st)
}

@Composable
private fun VocalsTab(st: Store, pickFile: PickFile) {
    Text("Alt vocals (covers). Leave empty for a single-vocal song. If you add alt vocals, name the DEFAULT vocal first (it uses the main track + jacket).",
        style = MaterialTheme.typography.bodySmall)
    Field("Default vocal name", "default_vocal_name", st)
    Dropdown(st.str("default_vocal_type"), VOCAL_TYPES) { st.set("default_vocal_type", it) }
    val alts = st.arr("alt_vocals")
    for (i in 0 until alts.length()) {
        val v = alts.getJSONObject(i)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Alt vocal ${i + 1}", style = MaterialTheme.typography.titleSmall)
            for ((key, label) in listOf("name" to "Name", "vocals" to "Vocals")) {
                OutlinedTextField(v.optString(key), { v.put(key, it); st.touch() }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Dropdown(v.optString("vocal_type"), VOCAL_TYPES) { v.put("vocal_type", it); st.touch() }
            FileField("Audio", v.optString("audio"), { pickFile("alt${i}_audio", AUDIO) { v.put("audio", it); st.touch() } })
            FileField("Preview", v.optString("preview"), { pickFile("alt${i}_pre", AUDIO) { v.put("preview", it); st.touch() } })
            FileField("Jacket (optional)", v.optString("jacket"), { pickFile("alt${i}_jacket", IMAGE) { v.put("jacket", it); st.touch() } },
                { v.put("jacket", ""); st.touch() })
            TextButton({ val a = JSONArray(); for (j in 0 until alts.length()) if (j != i) a.put(alts.get(j)); st.set("alt_vocals", a) }) { Text("Remove") }
        }
    }
    OutlinedButton({
        alts.put(JSONObject().put("name", "").put("vocals", "").put("vocal_type", "original_song")
            .put("audio", "").put("preview", "").put("jacket", "")); st.touch()
    }) { Text("+ Add alt vocal") }
}

@Composable
private fun IdentityTab(st: Store, onRegen: () -> Unit, onCopy: () -> Unit) {
    Text("This identifier uniquely names the chart. Re-exporting with the same identifier updates that chart in the game instead of adding a duplicate. " +
        "Keep it after your first export. You may paste your own: two valid v4 UUIDs joined by '_'.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(st.str("identifier"), { st.set("identifier", it) }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onRegen) { Text("Regenerate") }
        OutlinedButton(onCopy) { Text("Copy") }
    }
}