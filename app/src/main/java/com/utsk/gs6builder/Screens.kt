package com.utsk.gs6builder

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.isRuntimeShaderSupported
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

internal val DIFFICULTIES = listOf("easy", "normal", "hard", "expert", "master", "append")
internal val MV_TYPES = listOf("ogmv", "2dmv")
internal val VOCAL_TYPES = listOf("sekai", "original_song", "another_vocal")
internal val IMAGE = arrayOf("image/*")
internal val AUDIO = arrayOf("audio/*", "video/*", "application/ogg")
internal val VIDEO = arrayOf("video/*")
internal val ANY = arrayOf("*/*")
internal typealias PickFile = (String, Array<String>, (String) -> Unit) -> Unit

// ---- shared building blocks ------------------------------------------------------------------

/** A titled card; children bring their own padding (preference rows are edge to edge). */
@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    SmallTitle(text = title)
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) { content() }
}

@Composable
internal fun Pad(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
private fun HintCardPadded(text: String) {
    Text(text, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
private fun FileRow(label: String, path: String, onPick: () -> Unit, onClear: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            if (label.isNotEmpty()) Hint(label)
            Text(if (path.isBlank()) "-" else File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(onClick = onPick) { Text("Choose") }
        if (onClear != null) TextButton(text = "✕", onClick = onClear)
    }
}

@Composable
private fun Choice(title: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    OverlayDropdownPreference(
        items = options,
        selectedIndex = options.indexOf(value).coerceAtLeast(0),
        title = title,
        onSelectedIndexChange = { onSelect(options[it]) },
    )
}

@Composable
private fun Field(label: String, key: String, st: Store, hint: String? = null, number: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextField(
            value = st.str(key), onValueChange = { st.set(key, it) }, label = label, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        )
        if (hint != null) Hint(hint)
    }
}

private fun JSONArray.without(i: Int): JSONArray {
    val a = JSONArray()
    for (j in 0 until length()) if (j != i) a.put(get(j))
    return a
}

/** Guess the difficulty from a clearly-labelled chart filename (e.g. "master.sus"). */
internal fun inferDifficulty(path: String): String? {
    val stem = File(path).name.lowercase().substringBeforeLast('.').trim()
    return DIFFICULTIES.firstOrNull { d ->
        stem == d || (stem.startsWith(d) && stem.getOrNull(d.length) in listOf('-', '_', '.', ' ', '('))
    }
}

// ---- Files tab: files, charts, vocals --------------------------------------------------------

@Composable
internal fun FilesPage(st: Store, pickFile: PickFile, check: (JSONObject, Boolean) -> Unit) {
    Section("Song files") {
        Pad {
            FileRow("Jacket (image)*", st.str("jacket"), onPick = { pickFile("jacket", IMAGE) { st.set("jacket", it) } })
            FileRow("Audio track*", st.str("track"), onPick = { pickFile("track", AUDIO) { st.set("track", it) } })
            FileRow("Preview audio (≤ 45 s)*", st.str("track_pre"), onPick = { pickFile("track_pre", AUDIO) { st.set("track_pre", it) } })
        }
    }

    Section("Music videos (optional, up to 2)") {
        val mvs = st.arr("mvs")
        for (i in 0 until mvs.length()) {
            val mv = mvs.getJSONObject(i)
            Choice("Type", mv.getString("kind"), MV_TYPES) { mv.put("kind", it); st.touch() }
            Pad {
                FileRow("", mv.optString("path"),
                    onPick = { pickFile("mv$i", VIDEO) { mv.put("path", it); st.touch() } },
                    onClear = { st.set("mvs", mvs.without(i)) })
            }
        }
        if (mvs.length() < 2) Pad {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                mvs.put(JSONObject().put("kind", MV_TYPES[mvs.length() % 2]).put("path", "")); st.touch()
            }) { Text("+ Add music video") }
        }
    }

    Section("Charts") {
        val rows = st.arr("charts")
        Pad { Hint("Formats: .sus, .usc, Next SEKAI / Chart Cyanvas LevelData (.json, .json.gz, .nsld). Combo is automatic.") }
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            Choice("Difficulty", r.getString("difficulty"), DIFFICULTIES) { r.put("difficulty", it); st.touch() }
            Pad {
                TextField(
                    value = r.optString("level", "1"), onValueChange = { r.put("level", it); st.touch() },
                    label = "Level 1–99", singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Hint("combo ${r.optString("combo", "-")}")
                FileRow("Chart file", r.optString("path"),
                    onPick = { pickFile("chart$i", ANY) {
                        r.put("path", it)
                        inferDifficulty(it)?.let { d -> r.put("difficulty", d) }
                        r.put("lenient", false); st.touch(); check(r, false)
                    } },
                    onClear = { st.set("charts", rows.without(i)) })
            }
        }
        if (rows.length() < 6) Pad {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                val used = (0 until rows.length()).map { rows.getJSONObject(it).getString("difficulty") }.toSet()
                rows.put(JSONObject().put("difficulty", DIFFICULTIES.firstOrNull { it !in used } ?: "master")
                    .put("level", "1").put("path", "").put("lenient", false)); st.touch()
            }) { Text("+ Add chart") }
        }
    }

    Section("Vocals") {
        Pad {
            Hint("Alt vocals (covers). Leave empty for a single-vocal song. If you add alt vocals, name the DEFAULT vocal first (it uses the main track + jacket).")
            Field("Default vocal name", "default_vocal_name", st)
        }
        Choice("Default vocal type", st.str("default_vocal_type"), VOCAL_TYPES) { st.set("default_vocal_type", it) }
        val alts = st.arr("alt_vocals")
        for (i in 0 until alts.length()) {
            val v = alts.getJSONObject(i)
            Pad {
                Text("Alt vocal ${i + 1}", style = MiuixTheme.textStyles.subtitle)
                for ((key, label) in listOf("name" to "Name", "vocals" to "Vocals")) {
                    TextField(value = v.optString(key), onValueChange = { v.put(key, it); st.touch() },
                        label = label, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            Choice("Vocal type", v.optString("vocal_type"), VOCAL_TYPES) { v.put("vocal_type", it); st.touch() }
            Pad {
                FileRow("Audio", v.optString("audio"), onPick = { pickFile("alt${i}_audio", AUDIO) { v.put("audio", it); st.touch() } })
                FileRow("Preview", v.optString("preview"), onPick = { pickFile("alt${i}_pre", AUDIO) { v.put("preview", it); st.touch() } })
                FileRow("Jacket (optional)", v.optString("jacket"),
                    onPick = { pickFile("alt${i}_jacket", IMAGE) { v.put("jacket", it); st.touch() } },
                    onClear = { v.put("jacket", ""); st.touch() })
                TextButton(text = "Remove alt vocal", onClick = { st.set("alt_vocals", alts.without(i)) })
            }
        }
        Pad {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                alts.put(JSONObject().put("name", "").put("vocals", "").put("vocal_type", "original_song")
                    .put("audio", "").put("preview", "").put("jacket", "")); st.touch()
            }) { Text("+ Add alt vocal") }
        }
    }
}

// ---- Metadata tab: credits + identity --------------------------------------------------------

@Composable
internal fun MetadataPage(st: Store, onRegen: () -> Unit, onCopy: () -> Unit) {
    Section("Credits") {
        Pad {
            Field("Title*", "title", st); Field("Charter*", "charter", st); Field("Lyricist*", "lyricist", st)
            Field("Composer*", "composer", st); Field("Arranger*", "arranger", st); Field("Artist*", "artist", st)
            Field("Vocals*", "vocals", st)
        }
        SwitchPreference(
            checked = st.bool("is_full"), onCheckedChange = { st.set("is_full", it) },
            title = "Full-length", summary = "Not a game-size cut",
        )
    }
    Section("Timing & links") {
        Pad {
            Field("offset_ms", "offset_ms", st, "Global chart timing offset (applies to all difficulties)", number = true)
            Field("additional_mv_offset_ms", "mv_offset_ms", st, "Global extra MV offset", number = true)
            Field("original (URL)", "original", st, "Optional")
            Field("Collab (optional)", "collab", st)
        }
    }
    Section("Identity") {
        Pad {
            Hint("This identifier uniquely names the chart. Re-exporting with the same identifier updates that chart in the game instead of adding a duplicate. " +
                "Keep it after your first export. You may paste your own: two valid v4 UUIDs joined by '_'.")
            TextField(value = st.str("identifier"), onValueChange = { st.set("identifier", it) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = onRegen) { Text("Regenerate") }
                Button(modifier = Modifier.weight(1f), onClick = onCopy) { Text("Copy") }
            }
        }
    }
}

// ---- Settings tab ----------------------------------------------------------------------------

@Composable
internal fun SettingsPage(
    s: AppSettings, onNewProject: () -> Unit,
    version: String, checkingUpdate: Boolean, onCheckUpdate: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    Section("Appearance") {
        OverlayDropdownPreference(
            items = listOf("Follow system", "Light", "Dark"), selectedIndex = s.themeMode,
            title = "Dark mode", onSelectedIndexChange = { s.themeMode = it },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchPreference(checked = s.monet, onCheckedChange = { s.monet = it },
                title = "Monet colors", summary = "Use your wallpaper colors")
        }
        if (isRuntimeShaderSupported()) {
            SwitchPreference(checked = s.glassBar, onCheckedChange = { s.glassBar = it },
                title = "Liquid glass tab bar", summary = "Turn off for the plain Miuix bar")
        }
    }
    Section("Project") {
        SwitchPreference(checked = s.autoCopyId, onCheckedChange = { s.autoCopyId = it },
            title = "Copy identifier after compiling", summary = "Puts the identifier on your clipboard when a compile succeeds")
        ArrowPreference(title = "New project", summary = "Clear every field and start over", onClick = onNewProject)
    }
    Section("Updates") {
        SwitchPreference(checked = s.updateCheck, onCheckedChange = { s.updateCheck = it },
            title = "Check for updates on launch", summary = "Looks up the newest GitHub release each time the app starts")
        OverlayDropdownPreference(
            items = listOf("Release (stable only)", "Preview (includes betas)"), selectedIndex = s.updateChannel,
            title = "Update channel", onSelectedIndexChange = { s.updateChannel = it },
        )
        ArrowPreference(title = "Check for updates", summary = if (checkingUpdate) "Checking…" else "Current version $version",
            onClick = { if (!checkingUpdate) onCheckUpdate() })
    }
    Section("About") {
        ArrowPreference(title = "Open-source licenses", summary = "Libraries, fonts and bundled code this app uses", onClick = onOpenLicenses)
    }
}

// ---- Open-source licenses + dev file explorer ------------------------------------------------

internal data class LicenseEntry(val title: String, val subtitle: String, val body: String)

internal val LICENSE_ENTRIES: List<LicenseEntry> = listOf(
    LicenseEntry("GS6 Builder", "GNU AGPL-3.0", "This program is free software: you can redistribute it and/or modify it " +
        "under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either " +
        "version 3 of the License, or (at your option) any later version.\n\n" +
        "This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied " +
        "warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more " +
        "details. The complete text ships with the repository's LICENSE file and at https://www.gnu.org/licenses/.\n\n" +
        "It is an Android port of the GridlessSekai6 custom-chart builder."),
    LicenseEntry("Apache License 2.0", "Miuix · Kyant0 backdrop & shapes · AndroidLiquidGlass (catalog) · Jetpack Compose & androidx.activity · Compose Multiplatform · kotlinx-coroutines",
        "Copyrights: Google LLC (Jetpack Compose, androidx.activity); JetBrains (Compose Multiplatform, " +
        "kotlinx-coroutines); compose-miuix-ui contributors (Miuix); Kyant0 (backdrop, shapes, AndroidLiquidGlass " +
        "catalog).\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use these files except " +
        "in compliance with the License. You may obtain a copy of the License at\n\n" +
        "http://www.apache.org/licenses/LICENSE-2.0\n\n" +
        "Unless required by applicable law or agreed to in writing, software distributed under the License is distributed " +
        "on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for " +
        "the specific language governing permissions and limitations under the License.\n\n" +
        "The full license text is included in each source file header where the code was copied into this repository and " +
        "at the URL above."),
    LicenseEntry("MIT License", "Chaquopy · UnityPy · texture2ddecoder · etcpak · astc-encoder-py · brotli",
        "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated " +
        "documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation " +
        "the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and " +
        "to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\n" +
        "The above copyright notice and this permission notice shall be included in all copies or substantial portions of " +
        "the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING " +
        "BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN " +
        "NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN " +
        "AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR " +
        "OTHER DEALINGS IN THE SOFTWARE."),
    LicenseEntry("MIT-CMU (HPND)", "Pillow",
        "By obtaining, using, and/or copying this software and/or its associated software, you agree that you have read, " +
        "understood, and will comply with the following terms and conditions:\n\n" +
        "Permission to use, copy, modify, and distribute this software and its documentation for any purpose and without " +
        "fee is hereby granted, provided that the above copyright notice appears in all copies, and that both that " +
        "copyright notice and this permission notice appear in supporting documentation, and that the name of CMU not be " +
        "used in advertising or publicity pertaining to distribution of the software without specific, written prior " +
        "permission."),
    LicenseEntry("BSD-2-Clause (Simplified)", "lz4",
        "Redistribution and use in source and binary forms, with or without modification, are permitted provided that the " +
        "following conditions are met:\n\n1. Redistributions of source code must retain the above copyright notice, this " +
        "list of conditions and the following disclaimer.\n\n2. Redistributions in binary form must reproduce the above " +
        "copyright notice, this list of conditions and the following disclaimer in the documentation and/or other " +
        "materials provided with the distribution.\n\nTHIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS " +
        "\"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF " +
        "MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR " +
        "CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES " +
        "(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR " +
        "BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR " +
        "TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF " +
        "THE POSSIBILITY OF SUCH DAMAGE."),
    LicenseEntry("GNU GPL-3.0", "rg-converters",
        "PyPI package built and bundled from source as required by its GPL-3.0 license. The corresponding source is " +
        "available at its PyPI page (https://pypi.org/project/rg-converters/); the Android-compatible wheel build scripts " +
        "live in this repository's GitHub workflow."),
    LicenseEntry("GNU GPL-2.0-or-later (ffmpeg components)", "ffmpeg · x264 · x265", 
        "This app bundles prebuilt native libraries (libffmpeg_bin.so and friends) that link against GPL components " +
        "(libx264, libx265). The corresponding source for these libraries is obtainable from their upstream projects " +
        "(https://ffmpeg.org, https://x264.org, https://x265.org) and from the build instructions of the repository these " +
        "binaries ship with."),
    LicenseEntry("SIL Open Font License 1.1", "Inter", 
        "Copyright (c) 2016-2020 The Inter Project Authors (https://github.com/rsms/inter)\n\n" +
        "This Font Software is licensed under the SIL Open Font License, Version 1.1. This license is copied below, and " +
        "is also available with a FAQ at https://scripts.sil.org/OFL\n\n" +
        "This license allows the font to be used, studied, modified and redistributed freely, subject to the conditions " +
        "that derivative fonts licensed under OFL carry the Reserved Font Name clause and are not sold by themselves. " +
        "See the full text at https://openfontlicense.org."),
)

@Composable
internal fun LicensesPage() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (entry in LICENSE_ENTRIES) {
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.title, style = MiuixTheme.textStyles.subtitle)
                    Hint(entry.subtitle)
                    Text(entry.body, style = MiuixTheme.textStyles.footnote1)
                }
            }
        }
    }
}

/** Dev-mode in-app explorer for the app's private data (/data/data/<pkg>/files). */
@Composable
internal fun DevFileExplorer(root: File, onOpenFile: (File) -> Unit) {
    var cwd by remember { mutableStateOf(root) }
    fun entryLabel(f: File): String = if (f.isDirectory) "folder" else {
        val bytes = if (f.length() > 1024 * 1024) "> 1 MB" else "%.1f KB".format(f.length() / 1024.0)
        bytes
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Private app data", style = MiuixTheme.textStyles.subtitle)
                Hint(cwd.absolutePath)
            }
            val entries = cwd.listFiles()?.sortedWith(compareBy({ it.isFile }, { it.name.lowercase() }))
            if (cwd != root) {
                Row(Modifier.fillMaxWidth().clickable { cwd = cwd.parentFile }
                    .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⬆  up one level", color = MiuixTheme.colorScheme.primary)
                }
            }
            for (f in entries.orEmpty()) {
                Row(Modifier.fillMaxWidth().clickable { if (f.isDirectory) cwd = f else onOpenFile(f) }
                    .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Hint(entryLabel(f))
                    }
                    if (f.isFile) Text("view", style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            if (entries.isNullOrEmpty()) {
                HintCardPadded("(empty folder)")
            }
        }
    }
}
