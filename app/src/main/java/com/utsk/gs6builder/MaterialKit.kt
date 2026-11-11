package com.utsk.gs6builder

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

// ---- theme -------------------------------------------------------------------------------------
// Everything in this file and in MaterialPages.kt / MaterialShell.kt belongs to the Material UI mode.
// Nothing here is used by the Miuix mode, and the Miuix files don't reference anything in here
// (except Root, which picks one of the two).

private fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

@Composable
internal fun MaterialAppTheme(s: AppSettings, dark: Boolean, font: FontFamily, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dynamic = s.monet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        dynamic && dark -> dynamicDarkColorScheme(ctx)
        dynamic -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = if (dark && s.amoled) base.copy(background = Color.Black, surface = Color.Black) else base
    val typography = remember(font) { Typography().withFont(font) }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

// ---- grouped sections ----------------------------------------------------------------------------

internal class GroupScope {
    val rows = mutableListOf<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) { rows += content }
}

private val OUTER = 28.dp
private val INNER = 6.dp

/** A small coloured title over a group of rows that are separate cards with tiny gaps and big outer corners. */
@Composable
internal fun MSection(title: String, build: GroupScope.() -> Unit) {
    val g = GroupScope().apply(build)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            g.rows.forEachIndexed { i, rowContent ->
                val top = if (i == 0) OUTER else INNER
                val bottom = if (i == g.rows.lastIndex) OUTER else INNER
                Surface(
                    shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) { rowContent() }
            }
        }
    }
}

@Composable
internal fun MPad(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
internal fun MHint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

// ---- rows ----------------------------------------------------------------------------------------

@Composable
private fun RowTexts(title: String, summary: String?, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun MSwitchRow(title: String, summary: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowTexts(title, summary, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun MArrowRow(title: String, summary: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowTexts(title, summary, Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun MDropdownRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RowTexts(title, options.getOrElse(selected) { "" }, Modifier.weight(1f))
            Text("▾", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(i) })
            }
        }
    }
}

@Composable
internal fun MChoice(title: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    MDropdownRow(title, options, options.indexOf(value).coerceAtLeast(0)) { onSelect(options[it]) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = selected == i, onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

// ---- inputs --------------------------------------------------------------------------------------

@Composable
internal fun MFileRow(label: String, path: String, onPick: () -> Unit, onClear: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            if (label.isNotEmpty()) MHint(label)
            Text(
                if (path.isBlank()) "-" else File(path).name, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalButton(onClick = onPick) { Text("Choose") }
        if (onClear != null) TextButton(onClick = onClear) { Text("✕") }
    }
}

@Composable
internal fun MTextField(
    value: String, onValueChange: (String) -> Unit, label: String? = null, hint: String? = null,
    number: Boolean = false, singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, singleLine = singleLine, modifier = Modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        supportingText = hint?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
    )
}

@Composable
internal fun MField(label: String, key: String, st: Store, hint: String? = null, number: Boolean = false) {
    MTextField(st.str(key), { st.set(key, it) }, label = label, hint = hint, number = number)
}
