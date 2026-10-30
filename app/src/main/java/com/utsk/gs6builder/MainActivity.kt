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
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

    private var baseline = ""
    /** Load a pristine project and remember it, so [isDirty] can tell whether anything was filled in. */
    fun loadFresh(json: String) { load(json); baseline = p.toString() }
    fun isDirty(): Boolean = p.toString() != baseline
}

/** Everything that touches Python, the file system and MediaStore. */
class Backend(private val ctx: Context) {
    private val py by lazy { Python.getInstance().getModule("bridge") }
    val projectDir = File(ctx.filesDir, "project").apply { mkdirs() }
    val appDataDir = ctx.filesDir
    /** versionName from the manifest (the launcher label users see). */
    val appVersion: String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "dev"
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
        val settings = AppSettings(this)
        setContent { Root(this@MainActivity, backend, settings) }
    }
}
