package com.utsk.gs6builder

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Update
import org.json.JSONObject
import java.io.File

// Material-mode pages. Same data and callbacks as the Miuix pages in Screens.kt; only the widgets differ.
// They are placed inside MPage's scrolling column in MaterialShell.kt.

// ---- Files -----------------------------------------------------------------------------------

@Composable
internal fun MFilesPage(st: Store, pickFile: PickFile, check: (JSONObject, Boolean) -> Unit) {
    MSection("Song files") {
        row {
            MPad {
                MFileRow("Jacket (image)*", st.str("jacket"), onPick = { pickFile("jacket", IMAGE) { st.set("jacket", it) } })
                MFileRow("Audio track*", st.str("track"), onPick = { pickFile("track", AUDIO) { st.set("track", it) } })
                MFileRow("Preview audio (≤ 45 s)*", st.str("track_pre"), onPick = { pickFile("track_pre", AUDIO) { st.set("track_pre", it) } })
            }
        }
    }

    MSection("Music videos (optional, up to 2)") {
        val mvs = st.arr("mvs")
        for (i in 0 until mvs.length()) {
            val mv = mvs.getJSONObject(i)
            val id = System.identityHashCode(mv)
            row("mv$id-kind") { MChoice("Type", mv.getString("kind"), MV_TYPES) { mv.put("kind", it); st.touch() } }
            row("mv$id-file") {
                MPad {
                    MFileRow("", mv.optString("path"),
                        onPick = { pickFile("mv$i", VIDEO) { mv.put("path", it); st.touch() } },
                        onClear = { st.set("mvs", mvs.without(i)) })
                }
            }
        }
        row("addMv", visible = mvs.length() < 2) {
            MPad {
                MButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    mvs.put(JSONObject().put("kind", MV_TYPES[mvs.length() % 2]).put("path", "")); st.touch()
                }) { Text("+ Add music video") }
            }
        }
    }

    MSection("Charts") {
        val rows = st.arr("charts")
        row("chartHint") { MPad { MHint("Formats: .sus, .usc, Next SEKAI / Chart Cyanvas LevelData (.json, .json.gz, .nsld). Combo is automatic.") } }
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val id = System.identityHashCode(r)
            row("ch$id-diff") { MChoice("Difficulty", r.getString("difficulty"), DIFFICULTIES) { r.put("difficulty", it); st.touch() } }
            row("ch$id-file") {
                MPad {
                    MTextField(r.optString("level", "1"), { r.put("level", it); st.touch() }, label = "Level 1–99", number = true)
                    MHint("combo ${r.optString("combo", "-")}")
                    MFileRow("Chart file", r.optString("path"),
                        onPick = {
                            pickFile("chart$i", ANY) {
                                r.put("path", it)
                                inferDifficulty(it)?.let { d -> r.put("difficulty", d) }
                                r.put("lenient", false); st.touch(); check(r, false)
                            }
                        },
                        onClear = { st.set("charts", rows.without(i)) })
                }
            }
        }
        row("addChart", visible = rows.length() < 6) {
            MPad {
                MButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    val used = (0 until rows.length()).map { rows.getJSONObject(it).getString("difficulty") }.toSet()
                    rows.put(JSONObject().put("difficulty", DIFFICULTIES.firstOrNull { it !in used } ?: "master")
                        .put("level", "1").put("path", "").put("lenient", false)); st.touch()
                }) { Text("+ Add chart") }
            }
        }
    }

    MSection("Vocals") {
        row {
            MPad {
                MHint("Alt vocals (covers). Leave empty for a single-vocal song. If you add alt vocals, name the DEFAULT vocal first (it uses the main track + jacket).")
                MField("Default vocal name", "default_vocal_name", st)
            }
        }
        row { MChoice("Default vocal type", st.str("default_vocal_type"), VOCAL_TYPES) { st.set("default_vocal_type", it) } }
        val alts = st.arr("alt_vocals")
        for (i in 0 until alts.length()) {
            val v = alts.getJSONObject(i)
            val id = System.identityHashCode(v)
            row("alt$id-head") {
                MPad {
                    Text("Alt vocal ${i + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    for ((key, label) in listOf("name" to "Name", "vocals" to "Vocals")) {
                        MTextField(v.optString(key), { v.put(key, it); st.touch() }, label = label)
                    }
                }
            }
            row("alt$id-type") { MChoice("Vocal type", v.optString("vocal_type"), VOCAL_TYPES) { v.put("vocal_type", it); st.touch() } }
            row("alt$id-files") {
                MPad {
                    MFileRow("Audio", v.optString("audio"), onPick = { pickFile("alt${i}_audio", AUDIO) { v.put("audio", it); st.touch() } })
                    MFileRow("Preview", v.optString("preview"), onPick = { pickFile("alt${i}_pre", AUDIO) { v.put("preview", it); st.touch() } })
                    MFileRow("Jacket (optional)", v.optString("jacket"),
                        onPick = { pickFile("alt${i}_jacket", IMAGE) { v.put("jacket", it); st.touch() } },
                        onClear = { v.put("jacket", ""); st.touch() })
                    TextButton(onClick = { st.set("alt_vocals", alts.without(i)) }) { Text("Remove alt vocal") }
                }
            }
        }
        row("addAlt") {
            MPad {
                MButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    alts.put(JSONObject().put("name", "").put("vocals", "").put("vocal_type", "original_song")
                        .put("audio", "").put("preview", "").put("jacket", "")); st.touch()
                }) { Text("+ Add alt vocal") }
            }
        }
    }
}

// ---- Metadata --------------------------------------------------------------------------------

@Composable
internal fun MMetadataPage(st: Store, onRegen: () -> Unit, onCopy: () -> Unit) {
    MSection("Credits") {
        row {
            MPad {
                MField("Title*", "title", st); MField("Charter*", "charter", st); MField("Lyricist*", "lyricist", st)
                MField("Composer*", "composer", st); MField("Arranger*", "arranger", st); MField("Artist*", "artist", st)
                MField("Vocals*", "vocals", st)
            }
        }
        row { MSwitchRow("Full-length", "Not a game-size cut", st.bool("is_full")) { st.set("is_full", it) } }
    }
    MSection("Timing & links") {
        row {
            MPad {
                MField("offset_ms", "offset_ms", st, "Global chart timing offset (applies to all difficulties)", number = true)
                MField("additional_mv_offset_ms", "mv_offset_ms", st, "Global extra MV offset", number = true)
                MField("original (URL)", "original", st, "Optional")
                MField("Collab (optional)", "collab", st)
            }
        }
    }
    MSection("Identity") {
        row {
            MPad {
                MHint("This identifier uniquely names the chart. Re-exporting with the same identifier updates that chart in the game instead of adding a duplicate. " +
                    "Keep it after your first export. You may paste your own: two valid v4 UUIDs joined by '_'.")
                MTextField(st.str("identifier"), { st.set("identifier", it) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MButton(modifier = Modifier.weight(1f), onClick = onRegen) { Text("Regenerate") }
                    MButton(modifier = Modifier.weight(1f), onClick = onCopy) { Text("Copy") }
                }
            }
        }
    }
}

// ---- Settings --------------------------------------------------------------------------------

@Composable
internal fun MSettingsPage(
    s: AppSettings, onNewProject: () -> Unit,
    version: String, checkingUpdate: Boolean, onCheckUpdate: () -> Unit,
    onOpenLicenses: () -> Unit,
    onPickOutputFolder: () -> Unit, onResetOutputFolder: () -> Unit,
    onUnlockDev: () -> Unit, onOpenDevFiles: () -> Unit, onDisableDev: () -> Unit,
) {
    val glassSupported = isRuntimeShaderSupported()
    MSection("Appearance") {
        row { MDropdownRow("App style", listOf("Miuix", "Material"), s.uiStyle, icon = MiuixIcons.Tune) { s.uiStyle = it } }
        row {
            MPad {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(MiuixIcons.Theme, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                }
                MSegmented(listOf("System", "Light", "Dark"), s.themeMode) { s.themeMode = it }
            }
        }
        val canDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (canDynamic) {
            row("dynamicColor") { MSwitchRow("Dynamic color", "Use colors from your wallpaper", s.monet, icon = MiuixIcons.Background) { s.monet = it } }
        }
        // The seed swatches only make sense while the wallpaper isn't driving the scheme. A real row
        // (not an empty one wrapping AnimatedVisibility) so it, and its gap, collapse completely.
        row("themeColor", visible = !(canDynamic && s.monet)) {
            Column {
                Row(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(MiuixIcons.Favorites, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text("Theme color", style = MaterialTheme.typography.bodyLarge)
                }
                MSeedSwatchRow(s)
            }
        }
        row { MSwitchRow("AMOLED dark", "Pure black background in the dark theme", s.amoled, icon = MiuixIcons.Hide) { s.amoled = it } }
        row { MDropdownRow("Font", listOf("Intern", "Inter", "System"), s.fontChoice, icon = MIcons.TextFields) { s.fontChoice = it } }
        if (glassSupported) {
            row { MSwitchRow("Liquid glass tab bar", "Turn off for the plain Material bar", s.glassBar, icon = MiuixIcons.GridView) { s.glassBar = it } }
        }
        row { MSwitchRow("Haptics", "Vibrate on taps, switches and tab changes", s.haptics, icon = MIcons.Vibration) { s.haptics = it } }
    }
    MSection("Project") {
        row { MSwitchRow("Copy identifier after compiling", "Puts the identifier on your clipboard when a compile succeeds", s.autoCopyId, icon = MiuixIcons.Copy) { s.autoCopyId = it } }
        row {
            MArrowRow("Output folder", describeOutputFolder(s.outputDir) + (if (s.outputDir.isBlank()) " (default)" else ""),
                icon = MiuixIcons.Folder, onClick = onPickOutputFolder)
        }
        row("resetOutput", visible = s.outputDir.isNotBlank()) {
            MArrowRow("Reset output folder", "Go back to Downloads/GS6Charts", icon = MiuixIcons.Reset, onClick = onResetOutputFolder)
        }
        row { MArrowRow("New project", "Clear every field and start over", icon = MiuixIcons.Add, onClick = onNewProject) }
    }
    MSection("Updates") {
        row { MSwitchRow("Check for updates on launch", "Looks up the newest GitHub release each time the app starts", s.updateCheck, icon = MiuixIcons.Update) { s.updateCheck = it } }
        row {
            MDropdownRow("Update channel", listOf("Release (stable only)", "Preview (includes betas)"), s.updateChannel, icon = MiuixIcons.Download) { s.updateChannel = it }
        }
        row {
            MArrowRow("Check for updates", if (checkingUpdate) "Checking…" else "Current version $version",
                modifier = Modifier.holdToTrigger(onHold = onUnlockDev),
                icon = MiuixIcons.Refresh,
                onClick = { if (!checkingUpdate) onCheckUpdate() })
        }
    }
    // About and Developer Mode share one column so the page's 20 dp section spacing isn't applied to a
    // hidden dev section; its own gap lives inside the animation and expands/collapses with it.
    Column {
        MSection("About") {
            row { MArrowRow("Open-source licenses", "Libraries, fonts and bundled code this app uses", icon = MiuixIcons.Info, onClick = onOpenLicenses) }
        }
        AnimatedVisibility(
            visible = s.devMode,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Box(Modifier.padding(top = 20.dp)) {
                MSection("Developer Mode") {
                    row { MArrowRow("Private app data", "Browse the app's internal files", icon = MiuixIcons.Folder, onClick = onOpenDevFiles) }
                    row { MArrowRow("Disable developer mode", "Hide this section again", icon = MiuixIcons.Lock, onClick = onDisableDev) }
                }
            }
        }
    }
}

// ---- Material theme colors ------------------------------------------------------------------------

/** Preset seed colors for the Material scheme; the first entry restores the default scheme. */
private val SEED_PRESETS = listOf(
    0 to "Default",
    0xFF9C27B0.toInt() to "Purple",
    0xFFE91E63.toInt() to "Pink",
    0xFFF44336.toInt() to "Red",
    0xFFFF9800.toInt() to "Orange",
    0xFF4CAF50.toInt() to "Green",
    0xFF009688.toInt() to "Teal",
    0xFF2196F3.toInt() to "Blue",
    0xFF3F51B5.toInt() to "Indigo",
    0xFF607D8B.toInt() to "Slate",
)

/** Editable Material colors: a row of seed swatches (plus a custom hex picker) that regenerates the
 *  whole scheme live. Shown below the Dynamic color switch while wallpaper colors are off. */
@Composable
private fun MSeedSwatchRow(s: AppSettings) {
    val haptic = LocalMHaptics.current
    var customOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SEED_PRESETS.forEach { (argb, label) ->
            val selected = s.seedColor == argb
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (argb == 0) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        else Modifier.background(Color(argb))
                    )
                    .clickable { haptic(); s.seedColor = argb },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(MiuixIcons.Ok, contentDescription = label,
                    tint = if (argb == 0) MaterialTheme.colorScheme.onSurface else Color.White)
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { haptic(); customOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MiuixIcons.Add, contentDescription = "Custom color",
                tint = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (customOpen) {
        MCustomSeedDialog(
            initial = s.seedColor,
            onDismiss = { customOpen = false },
            onPick = { argb -> s.seedColor = argb; customOpen = false },
        )
    }
}

@Composable
private fun MCustomSeedDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (initial == 0) "" else "#%06X".format(initial and 0xFFFFFF)) }
    val parsed = text.removePrefix("#").let {
        if (it.length == 6) it.toLongOrNull(16)?.toInt()?.or(0xFF000000.toInt()) else null
    }
    MDialog(
        show = true, title = "Custom color",
        onDismiss = onDismiss, confirmText = "Use", onConfirm = { if (parsed != null) onPick(parsed) },
        confirmEnabled = parsed != null, dismissText = "Cancel",
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Hex (e.g. 546E7A)") }, singleLine = true)
                if (parsed != null) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color(parsed))) {}
                }
            }
        },
    )
}

// ---- Licenses + dev file explorer ------------------------------------------------------------

@Composable
internal fun MLicensesContent() {
    for (entry in LICENSE_ENTRIES) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                MHint(entry.subtitle)
                Text(entry.body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun MDevFileExplorer(root: File, onOpenFile: (File) -> Unit, onExportFile: (File) -> Unit) {
    var cwd by remember { mutableStateOf(root) }
    fun entryLabel(f: File): String = if (f.isDirectory) "folder" else {
        if (f.length() > 1024 * 1024) "> 1 MB" else "%.1f KB".format(f.length() / 1024.0)
    }
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Private app data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                MHint(cwd.absolutePath)
                MHint("Tap a file to view it (binary files open in a hex viewer). Hold a file to export it.")
            }
            val entries = cwd.listFiles()?.sortedWith(compareBy({ it.isFile }, { it.name.lowercase() }))
            if (cwd != root) {
                Row(Modifier.fillMaxWidth().clickable { cwd = cwd.parentFile ?: root }
                    .padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(MiuixIcons.ExpandLess, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Up one level", color = MaterialTheme.colorScheme.primary)
                }
            }
            for (f in entries.orEmpty()) {
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (f.isFile) Modifier.holdToTrigger(millis = 700) { onExportFile(f) } else Modifier)
                        .clickable { if (f.isDirectory) cwd = f else onOpenFile(f) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MHint(entryLabel(f))
                    }
                    if (f.isFile) MHint("view")
                }
            }
            if (entries.isNullOrEmpty()) {
                MHint("(empty folder)", Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
        }
    }
}
