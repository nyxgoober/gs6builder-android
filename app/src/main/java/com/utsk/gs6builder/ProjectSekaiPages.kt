package com.utsk.gs6builder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Update
import org.json.JSONObject
import java.io.File

// Project SEKAI pages. Same data and callbacks as the Material pages in MaterialPages.kt; only the
// widgets differ. They are placed inside PPage's scrolling column in ProjectSekaiShell.kt.

private val PFonts = listOf("Intern", "Inter", "System")
private val PUpdateChannels = listOf("Release (stable only)", "Preview (includes betas)")
private val PUiModes = listOf("Material UI", "Project SEKAI UI")

// ---- Files -----------------------------------------------------------------------------------

@Composable
internal fun PFilesPage(st: Store, pickFile: PickFile, check: (JSONObject, Boolean) -> Unit) {
    PSection("Song files") {
        PFileRow("Jacket (image)*", st.str("jacket"), onPick = { pickFile("jacket", IMAGE) { st.set("jacket", it) } })
        PFileRow("Audio track*", st.str("track"), onPick = { pickFile("track", AUDIO) { st.set("track", it) } })
        PFileRow("Preview audio (≤ 45 s)*", st.str("track_pre"), onPick = { pickFile("track_pre", AUDIO) { st.set("track_pre", it) } })
    }

    PSection("Music videos (optional, up to 2)") {
        val mvs = st.arr("mvs")
        for (i in 0 until mvs.length()) {
            val mv = mvs.getJSONObject(i)
            PDropdownRow("Type", MV_TYPES, MV_TYPES.indexOf(mv.getString("kind")).coerceAtLeast(0)) {
                mv.put("kind", it); st.touch()
            }
            PFileRow("", mv.optString("path"),
                onPick = { pickFile("mv$i", VIDEO) { mv.put("path", it); st.touch() } },
                onClear = { st.set("mvs", mvs.without(i)) })
        }
        if (mvs.length() < 2) {
            PButton(modifier = Modifier.fillMaxWidth(), onClick = {
                mvs.put(JSONObject().put("kind", MV_TYPES[mvs.length() % 2]).put("path", "")); st.touch()
            }) { Text("+ Add music video", fontWeight = FontWeight.Black) }
        }
    }

    PSection("Charts") {
        val rows = st.arr("charts")
        PHintCard("Formats: .sus, .usc, Next SEKAI / Chart Cyanvas LevelData (.json, .json.gz, .nsld). Combo is automatic.")
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            PDropdownRow("Difficulty", DIFFICULTIES, DIFFICULTIES.indexOf(r.getString("difficulty")).coerceAtLeast(0)) {
                r.put("difficulty", it); st.touch()
            }
            PTextField(
                r.optString("level", "1"), { r.put("level", it); st.touch() },
                label = "Level 1–99", hint = "combo ${r.optString("combo", "-")}", number = true,
            )
            PFileRow("Chart file", r.optString("path"),
                onPick = {
                    pickFile("chart$i", ANY) {
                        r.put("path", it)
                        inferDifficulty(it)?.let { d -> r.put("difficulty", d) }
                        r.put("lenient", false); st.touch(); check(r, false)
                    }
                },
                onClear = { st.set("charts", rows.without(i)) })
        }
        if (rows.length() < 6) {
            PButton(modifier = Modifier.fillMaxWidth(), onClick = {
                val used = (0 until rows.length()).map { rows.getJSONObject(it).getString("difficulty") }.toSet()
                rows.put(JSONObject().put("difficulty", DIFFICULTIES.firstOrNull { it !in used } ?: "master")
                    .put("level", "1").put("path", "").put("lenient", false)); st.touch()
            }) { Text("+ Add chart", fontWeight = FontWeight.Black) }
        }
    }

    PSection("Vocals") {
        PHintCard("Alt vocals (covers). Leave empty for a single-vocal song. If you add alt vocals, name the DEFAULT vocal first (it uses the main track + jacket).")
        PField("Default vocal name", "default_vocal_name", st)
        PDropdownRow("Default vocal type", VOCAL_TYPES, VOCAL_TYPES.indexOf(st.str("default_vocal_type")).coerceAtLeast(0)) {
            st.set("default_vocal_type", it)
        }
        val alts = st.arr("alt_vocals")
        for (i in 0 until alts.length()) {
            val v = alts.getJSONObject(i)
            PTextField(v.optString("name"), { v.put("name", it); st.touch() }, label = "Alt vocal ${i + 1} — name")
            PTextField(v.optString("vocals"), { v.put("vocals", it); st.touch() }, label = "Vocals")
            PDropdownRow("Vocal type", VOCAL_TYPES, VOCAL_TYPES.indexOf(v.optString("vocal_type")).coerceAtLeast(0)) {
                v.put("vocal_type", it); st.touch()
            }
            PFileRow("Audio", v.optString("audio"), onPick = { pickFile("alt${i}_audio", AUDIO) { v.put("audio", it); st.touch() } })
            PFileRow("Preview", v.optString("preview"), onPick = { pickFile("alt${i}_pre", AUDIO) { v.put("preview", it); st.touch() } })
            PFileRow("Jacket (optional)", v.optString("jacket"),
                onPick = { pickFile("alt${i}_jacket", IMAGE) { v.put("jacket", it); st.touch() } },
                onClear = { v.put("jacket", ""); st.touch() })
            PButton(modifier = Modifier.fillMaxWidth(), onClick = { st.set("alt_vocals", alts.without(i)) }) {
                Text("Remove alt vocal", fontWeight = FontWeight.Black)
            }
        }
        PButton(modifier = Modifier.fillMaxWidth(), onClick = {
            alts.put(JSONObject().put("name", "").put("vocals", "").put("vocal_type", "original_song")
                .put("audio", "").put("preview", "").put("jacket", "")); st.touch()
        }) { Text("+ Add alt vocal", fontWeight = FontWeight.Black) }
    }
}

/** A note inside the page: a soft card with muted text. */
@Composable
private fun PHintCard(text: String) {
    val p = LocalProjectSekai.current
    Box(
        Modifier.fillMaxWidth().clip(ProjectSekaiCardShape).background(p.mint.copy(alpha = 0.16f))
            .border(2.dp, p.mint.copy(alpha = 0.5f), ProjectSekaiCardShape).padding(14.dp),
    ) {
        Text(text, color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ---- Metadata --------------------------------------------------------------------------------

@Composable
internal fun PMetadataPage(st: Store, onRegen: () -> Unit, onCopy: () -> Unit) {
    PSection("Credits") {
        PField("Title*", "title", st)
        PField("Charter*", "charter", st)
        PField("Lyricist*", "lyricist", st)
        PField("Composer*", "composer", st)
        PField("Arranger*", "arranger", st)
        PField("Artist*", "artist", st)
        PField("Vocals*", "vocals", st)
        PSwitchRow("Full-length", "Not a game-size cut", st.bool("is_full")) { st.set("is_full", it) }
    }
    PSection("Timing & links") {
        PField("Global offset (ms)", "offset_ms", st, "Global chart timing offset (applies to all difficulties)", number = true)
        PField("Global MV offset (ms)", "mv_offset_ms", st, "Global extra MV offset", number = true)
        PField("Original (URL)", "original", st, "Optional")
        PField("Collab (optional)", "collab", st)
    }
    PSection("Identity") {
        PHintCard("This identifier uniquely names the chart. Re-exporting with the same identifier updates that chart in the game instead of adding a duplicate. " +
            "Keep it after your first export. You may paste your own: two valid v4 UUIDs joined by '_'.")
        PTextField(st.str("identifier"), { st.set("identifier", it) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(modifier = Modifier.weight(1f), onClick = onRegen) { Text("Regenerate", fontWeight = FontWeight.Black) }
            PButton(modifier = Modifier.weight(1f), onClick = onCopy) { Text("Copy", fontWeight = FontWeight.Black) }
        }
    }
}

// ---- Settings --------------------------------------------------------------------------------

@Composable
internal fun PSettingsPage(
    s: AppSettings, onNewProject: () -> Unit,
    version: String, checkingUpdate: Boolean,     onCheckUpdate: () -> Unit,
    onOpenLicenses: () -> Unit, onOpenAppInfo: () -> Unit,
    onPickOutputFolder: () -> Unit, onResetOutputFolder: () -> Unit,
    onUnlockDev: () -> Unit, onOpenDevFiles: () -> Unit, onDisableDev: () -> Unit,
) {
    PSection("Appearance") {
        PDropdownRow("UI design", PUiModes, s.uiMode, icon = MIcons.Palette) { s.uiMode = it }
        // Project SEKAI is light-first: its own light/dark choice, independent of the Material theme.
        PTitledBlock("Theme", MiuixIcons.Theme) {
            PSegmented(listOf("Light", "Dark"), s.sekaiTheme) { s.sekaiTheme = it }
        }
        PDropdownRow("Font", PFonts, s.fontChoice, icon = MIcons.TextFields) { s.fontChoice = it }
        PSwitchRow("Haptics", "Vibrate on taps, switches and tab changes", s.haptics, icon = MIcons.Vibration) { s.haptics = it }
    }
    PSection("Project") {
        PSwitchRow("Copy identifier after compiling", "Puts the identifier on your clipboard when a compile succeeds", s.autoCopyId, icon = MiuixIcons.Copy) { s.autoCopyId = it }
        PArrowRow("Output folder", describeOutputFolder(s.outputDir) + (if (s.outputDir.isBlank()) " (default)" else ""),
            icon = MiuixIcons.Folder, onClick = onPickOutputFolder)
        if (s.outputDir.isNotBlank()) {
            PArrowRow("Reset output folder", "Go back to Downloads/GS6Charts", icon = MiuixIcons.Reset, onClick = onResetOutputFolder)
        }
        PArrowRow("New project", "Clear every field and start over", icon = MiuixIcons.Add, onClick = onNewProject)
    }
    PSection("Updates") {
        PSwitchRow("Check for updates on launch", "Looks up the newest GitHub release each time the app starts", s.updateCheck, icon = MiuixIcons.Update) { s.updateCheck = it }
        PDropdownRow("Update channel", PUpdateChannels, s.updateChannel, icon = MiuixIcons.Download) { s.updateChannel = it }
        PArrowRow("Check for updates", if (checkingUpdate) "Checking…" else "Current version $version",
            modifier = Modifier.holdToTrigger(onHold = onUnlockDev),
            icon = MiuixIcons.Refresh,
            onClick = { if (!checkingUpdate) onCheckUpdate() })
    }
    // About and Developer Mode share one column so the page's section spacing isn't applied to a
    // hidden dev section; its own gap lives inside the animation and expands/collapses with it.
    Column {
        PSection("About") {
            PArrowRow("App info", "Version, release and build attestation", icon = MiuixIcons.Info, onClick = onOpenAppInfo)
            PArrowRow("Open-source licenses", "Libraries, fonts and bundled code this app uses", icon = MiuixIcons.Info, onClick = onOpenLicenses)
        }
        AnimatedVisibility(
            visible = s.devMode,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Box(Modifier.padding(top = 20.dp)) {
                PSection("Developer Mode") {
                    PArrowRow("Private app data", "Browse the app's internal files", icon = MiuixIcons.Folder, onClick = onOpenDevFiles)
                    PArrowRow("Disable developer mode", "Hide this section again", icon = MiuixIcons.Lock, onClick = onDisableDev)
                }
            }
        }
    }
}

// ---- Licenses + dev file explorer ------------------------------------------------------------

@Composable
internal fun PLicensesContent() {
    val p = LocalProjectSekai.current
    for (entry in LICENSE_ENTRIES) {
        Column(
            Modifier.fillMaxWidth().clip(ProjectSekaiCardShape).background(p.surface)
                .border(2.dp, p.line, ProjectSekaiCardShape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(entry.title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(entry.subtitle, color = p.teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(entry.body, color = p.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun PDevFileExplorer(root: File, onOpenFile: (File) -> Unit, onExportFile: (File) -> Unit) {
    val p = LocalProjectSekai.current
    var cwd by remember { mutableStateOf(root) }
    fun entryLabel(f: File): String = if (f.isDirectory) "folder" else {
        if (f.length() > 1024 * 1024) "> 1 MB" else "%.1f KB".format(f.length() / 1024.0)
    }
    Column(
        Modifier.fillMaxWidth().clip(ProjectSekaiCardShape).background(p.surface)
            .border(2.dp, p.line, ProjectSekaiCardShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Private app data", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
        PHint(cwd.absolutePath)
        PHint("Tap a file to view it (binary files open in a hex viewer). Hold a file to export it.")
        val entries = cwd.listFiles()?.sortedWith(compareBy({ it.isFile }, { it.name.lowercase() }))
        if (cwd != root) {
            PArrowRow("Up one level", icon = MiuixIcons.ExpandLess, onClick = { cwd = cwd.parentFile ?: root })
        }
        for (f in entries.orEmpty()) {
            Row(
                Modifier.fillMaxWidth()
                    .then(if (f.isFile) Modifier.holdToTrigger(millis = 700) { onExportFile(f) } else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { if (f.isDirectory) cwd = f else onOpenFile(f) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PHint(entryLabel(f))
                }
                if (f.isFile) PHint("view")
            }
        }
        if (entries.isNullOrEmpty()) PHint("(empty folder)")
    }
}
