package com.utsk.gs6builder

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
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
internal fun SettingsPage(s: AppSettings, onNewProject: () -> Unit) {
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
}
