package com.utsk.gs6builder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The Project SEKAI sheets: the Import flow (the Project SEKAI method list) and the Compile options.
// They are hosted by the ModalBottomSheet in ProjectSekaiShell.kt, which only provides the slide-up
// motion; everything visible is drawn by these two.

/** The site's method-list icon + name + description row (.mi / .mn / .md). */
@Composable
private fun RowScope.PImportRow(emoji: String, name: String, desc: String) {
    val p = LocalProjectSekai.current
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(p.mint.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 17.sp)
    }
    Column(Modifier.weight(1f)) {
        Text(name, color = p.ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(desc, color = p.inkMuted, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

private val IMPORT_EMOJI = listOf("🔗", "🔗", "📁")
private val IMPORT_DESC = listOf(
    "Paste an UntitledCharts level link",
    "Paste a Chart Cyanvas / CC Archive level link",
    "Pick a project .zip this app or the desktop builder made",
)

/** Project SEKAI replacement for the plain ModalBottomSheet import flow in MaterialShell.
 *  Same state as before (c.importKind / c.url / c.requestImport / c.closeImportSheet / c.ready /
 *  c.busy) — only the chrome changes, so this drops straight into the existing `with(c) { ... }`
 *  block in ProjectSekaiShell. */
@Composable
internal fun ProjectSekaiImportSheet(c: AppController) {
    with(c) {
        val p = LocalProjectSekai.current
        ProjectSekaiSheet(title = "IMPORT", onDismiss = { closeImportSheet() }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IMPORT_KINDS.forEachIndexed { i, label ->
                    PChoiceCard(selected = importKind == i, onClick = { importKind = i }) {
                        PImportRow(IMPORT_EMOJI[i], label, IMPORT_DESC[i])
                    }
                }
            }

            if (importKind < 2) {
                PTextField(
                    url, { url = it }, label = "Level link",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PHint("Pick a project .zip exported by this app or the desktop builder.")
            }

            PButton(
                modifier = Modifier.fillMaxWidth(), primary = true,
                enabled = ready && !busy && (importKind == 2 || url.isNotBlank()),
                onClick = { requestImport() },
            ) {
                Text(
                    if (importKind == 2) "Choose .zip…" else "Import",
                    color = p.ink, fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

/** The per-run options the Compile button opens: also build the project zip. */
@Composable
internal fun ProjectSekaiCompileSheet(c: AppController) {
    with(c) {
        ProjectSekaiSheet(title = "COMPILE", onDismiss = { closeCompileSheet() }) {
            PSwitchRow("Also build project ZIP", "Saves the source project next to the compiled chart", checked = compileZip) {
                compileZip = it
            }
            PButton(
                modifier = Modifier.fillMaxWidth(), primary = true, enabled = !busy,
                onClick = { closeCompileSheet(); compile(compileZip) },
            ) {
                Text("Compile", fontWeight = FontWeight.Black)
            }
        }
    }
}
