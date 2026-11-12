package com.utsk.gs6builder

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Ok
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
        // A seed picked in Settings: generate the whole Material scheme from it (AMOLED handled below).
        s.seedColor != 0 -> dynamicColorScheme(
            seedColor = Color(s.seedColor), isDark = dark,
            isAmoled = dark && s.amoled, style = PaletteStyle.TonalSpot,
        )
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = if (s.seedColor == 0 && dark && s.amoled)
        base.copy(background = Color.Black, surface = Color.Black) else base
    val typography = remember(font) { Typography().withFont(font) }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

// ---- haptics --------------------------------------------------------------------------------------

/** Fired by every Material row, button, switch and dropdown; backed by the controller's debounced tick,
 *  which the haptics setting gates. The Miuix UI uses its own widgets' feedback instead. */
internal val LocalMHaptics = staticCompositionLocalOf { {} }

// ---- expressive buttons ---------------------------------------------------------------------------
// Material 3 "expressive" feel: the corner radius collapses while you press (or hover with a mouse).

private const val BUTTON_RADIUS_REST = 26
private const val BUTTON_RADIUS_PRESSED = 6
/** A tap is over in well under 100 ms; keep the pressed shape at least this long so it is always seen. */
private const val MIN_PRESSED_MS = 170L

/** Interaction source + corner radius animated from rest to pressed; share it between the button's
 *  [shape] and [interactionSource] so the press is what drives the animation. */
@Composable
internal fun rememberExpressiveShape(source: MutableInteractionSource): Shape {
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    // Latch the press: a quick tap releases before the spring has moved, so without this the squish
    // is only visible if you hold the button down.
    var latched by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) latched = true
        else if (latched) { delay(MIN_PRESSED_MS); latched = false }
    }
    val squished = pressed || latched || hovered
    val radius by animateDpAsState(
        targetValue = if (squished) BUTTON_RADIUS_PRESSED.dp else BUTTON_RADIUS_REST.dp,
        // Snappy on the way in so it reads immediately; softer, slightly springy on the way out.
        animationSpec = if (squished) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1800f)
                        else spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "buttonRadius",
    )
    return RoundedCornerShape(radius.coerceAtLeast(0.dp))
}

/** A Material button with the expressive corner-radius press. [primary] = filled, otherwise tonal.
 *  One composable call for both looks, so flipping [primary] (e.g. Compile becoming ready) keeps the
 *  button's state and its running animation instead of recreating it. */
@Composable
internal fun MButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val haptic = LocalMHaptics.current
    val source = remember { MutableInteractionSource() }
    val shape = rememberExpressiveShape(source)
    Button(
        onClick = { haptic(); onClick() }, modifier = modifier, enabled = enabled, shape = shape,
        interactionSource = source,
        colors = if (primary) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        elevation = if (primary) ButtonDefaults.buttonElevation() else ButtonDefaults.filledTonalButtonElevation(),
        content = content,
    )
}

// ---- animated dialogs -----------------------------------------------------------------------------
// The stock M3 AlertDialog pops in with no transition, which cuts short whatever button animation
// opened it. This replacement fades the scrim and grows the pane (scale + expanding corner radius)
// with a spring, so the press keeps flowing into the reveal.

@Composable
internal fun MDialog(
    show: Boolean,
    title: String? = null,
    text: @Composable ColumnScope.() -> Unit = {},
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    cancellable: Boolean = true,
) {
    if (!show) return
    Dialog(onDismissRequest = { if (cancellable) onDismiss?.invoke() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Composed at show=true, so the entrance is animated from 0 every time.
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            appear.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 500f))
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = appear.value }
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = null, indication = null,
                    ) { if (cancellable) onDismiss?.invoke() },
            )
            val haptic = LocalMHaptics.current
            Surface(
                shape = RoundedCornerShape(8.dp + 20.dp * appear.value),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(24.dp)
                    .graphicsLayer {
                        val v = appear.value
                        scaleX = 0.88f + 0.12f * v
                        scaleY = 0.88f + 0.12f * v
                        alpha = (v * 4f).coerceAtMost(1f)   // visible almost immediately, opaque by a quarter in
                    },
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.verticalScroll(rememberScrollState()), content = text)
                    if (confirmText != null || dismissText != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (dismissText != null) {
                                TextButton(onClick = { haptic(); onDismiss?.invoke() }, modifier = Modifier.weight(1f)) { Text(dismissText) }
                            }
                            if (confirmText != null) {
                                TextButton(
                                    onClick = { haptic(); onConfirm?.invoke() },
                                    enabled = confirmEnabled, modifier = Modifier.weight(1f),
                                ) { Text(confirmText) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- grouped sections ----------------------------------------------------------------------------

internal class GroupScope {
    class Entry(val key: Any?, val visible: Boolean, val content: @Composable () -> Unit)
    val entries = mutableListOf<Entry>()

    /** [key] must be stable and unique for rows that come and go (list items, add buttons), otherwise
     *  Compose matches rows by position and a row inserted above one hands it the wrong state, which
     *  restarts its animations. Rows without a key are numbered in order, so they only need one if a
     *  keyed row can sit before them. [visible] = false keeps the row but collapses it with an animation. */
    fun row(key: Any? = null, visible: Boolean = true, content: @Composable () -> Unit) {
        entries += Entry(key, visible, content)
    }
}

private val OUTER = 28.dp
private val INNER = 6.dp
private val ROW_GAP = 2.dp

/** What a section remembers between compositions so rows that left the list can finish animating out. */
private class SectionMemory {
    var settled = false
    var order: List<Any> = emptyList()                       // keys as last displayed, ghosts included
    val entries = HashMap<Any, GroupScope.Entry>()           // last displayed entry per key
    val ghosts = LinkedHashMap<Any, GroupScope.Entry>()      // removed rows still collapsing
}

/** A small coloured title over a group of rows that are separate cards with tiny gaps and big outer corners.
 *  Rows added after the section first appears (a new chart, a revealed option) grow in. Rows hidden with
 *  `visible = false` or dropped from the list (a removed chart) shrink out together with their gap, so
 *  nothing is left behind and nothing vanishes abruptly. */
@Composable
internal fun MSection(title: String, build: GroupScope.() -> Unit) {
    val g = GroupScope().apply(build)
    val mem = remember { SectionMemory() }
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") tick   // read, so a finished exit recomposes and drops its ghost
    // What is on screen at the first composition just appears; anything that shows up later animates.
    val animateNew = mem.settled
    SideEffect { mem.settled = true }

    var unkeyed = 0
    val current = g.entries.map { (it.key ?: "row#${unkeyed++}") to it }
    val currentKeys = current.mapTo(HashSet()) { it.first }

    // A row that was shown last time but is gone now keeps composing as a hidden "ghost" until its exit ends.
    for (k in mem.order) {
        if (k !in currentKeys && k !in mem.ghosts) mem.entries[k]?.let { mem.ghosts[k] = GroupScope.Entry(it.key, false, it.content) }
    }
    mem.ghosts.keys.removeAll(currentKeys)   // a key that came back is a normal row again
    val shown = current.toMutableList()
    for ((idx, k) in mem.order.withIndex()) {
        val ghost = mem.ghosts[k] ?: continue
        // Re-insert it right after the nearest earlier row that is still displayed.
        val anchor = (idx - 1 downTo 0).firstNotNullOfOrNull { j -> shown.indexOfFirst { it.first == mem.order[j] }.takeIf { it >= 0 } }
        shown.add((anchor ?: -1) + 1, k to ghost)
    }
    mem.order = shown.map { it.first }
    mem.entries.clear(); shown.forEach { mem.entries[it.first] = it.second }

    val first = shown.indexOfFirst { it.second.visible }
    val last = shown.indexOfLast { it.second.visible }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp),
        )
        Column {
            shown.forEachIndexed { i, (k, e) ->
                key(k) {
                    val state = remember { MutableTransitionState(if (animateNew) false else e.visible) }
                    state.targetState = e.visible
                    if (k in mem.ghosts) {
                        LaunchedEffect(Unit) {
                            snapshotFlow { state.isIdle && !state.currentState }.first { it }
                            mem.ghosts.remove(k); tick++
                        }
                    }
                    AnimatedVisibility(
                        visibleState = state,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        val top = if (i == first) OUTER else INNER
                        val bottom = if (i == last) OUTER else INNER
                        Surface(
                            shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            // The gap lives inside the animated row so it collapses with it.
                            modifier = Modifier.fillMaxWidth().padding(top = if (i == first) 0.dp else ROW_GAP),
                        ) { e.content() }
                    }
                }
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
internal fun MSwitchRow(
    title: String, summary: String? = null, checked: Boolean,
    icon: ImageVector? = null, onChange: (Boolean) -> Unit,
) {
    val haptic = LocalMHaptics.current
    Row(
        Modifier.fillMaxWidth().clickable { haptic(); onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        RowTexts(title, summary, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A settings row with an optional leading icon and the Material chevron at the end. */
@Composable
internal fun MArrowRow(
    title: String, summary: String? = null, modifier: Modifier = Modifier,
    icon: ImageVector? = null, onClick: () -> Unit,
) {
    val haptic = LocalMHaptics.current
    Row(
        modifier.fillMaxWidth().clickable { haptic(); onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        RowTexts(title, summary, Modifier.weight(1f))
        Icon(MiuixIcons.ChevronForward, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun MDropdownRow(
    title: String, options: List<String>, selected: Int,
    icon: ImageVector? = null, onSelect: (Int) -> Unit,
) {
    val haptic = LocalMHaptics.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { haptic(); open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            RowTexts(title, options.getOrElse(selected) { "" }, Modifier.weight(1f))
            Icon(MIcons.ExpandMore, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(
                    text = { Text(o) },
                    leadingIcon = {
                        // Only the selected entry carries a check mark.
                        if (i == selected) Icon(MiuixIcons.Ok, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    },
                    onClick = { haptic(); open = false; onSelect(i) },
                )
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
    val haptic = LocalMHaptics.current
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = selected == i,
                onClick = { haptic(); onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

// ---- inputs --------------------------------------------------------------------------------------

@Composable
internal fun MFileRow(label: String, path: String, onPick: () -> Unit, onClear: (() -> Unit)? = null) {
    val haptic = LocalMHaptics.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            if (label.isNotEmpty()) MHint(label)
            Text(
                if (path.isBlank()) "-" else File(path).name, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        MButton(onClick = onPick) { Text("Choose") }
        if (onClear != null) {
            TextButton(onClick = { haptic(); onClear() }) {
                Icon(MiuixIcons.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
            }
        }
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
