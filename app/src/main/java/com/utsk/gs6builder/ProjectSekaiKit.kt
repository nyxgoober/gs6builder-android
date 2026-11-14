package com.utsk.gs6builder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Ok
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Project SEKAI: this app's second UI design. Teal/mint/sky cards and pill buttons, flat and
// playful, where the Material design is Material 3.
// Lives entirely alongside MaterialKit.kt / MaterialShell.kt; nothing here touches AppController
// or the Python bridge. Picked by the "UI design" setting, same shape as themeMode/seedColor.
//
// The Project SEKAI language: flat cards, pill buttons, no ripples (a press swaps the fill to the
// sky blue and glows), selectable cards that tint mint. Everything below follows it.
// ─────────────────────────────────────────────────────────────────────────────────────────────

/** The Project SEKAI brand tokens. */
internal object ProjectSekaiColors {
    val Teal = Color(0xFF39C5BB)          // the accent: header bar, icons, active labels
    val Teal2 = Color(0xFF2EB8AE)
    val TealGradientEnd = Color(0xFF2EC4B6) // the second stop of the header gradient
    val Pink = Color(0xFFFF6EB4)
    val Pink2 = Color(0xFFFF4DA6)
    val Navy = Color(0xFF2D3561)
    val Mint = Color(0xFF7EF0DC)           // the primary button fill and the selected wash
    val Sky = Color(0xFF79D8FF)            // the press highlight
    val Card = Color(0xFFEEEEF5)           // the sheet/popup background
    val Ink = Color(0xFF2F3358)            // the primary text colour
    val MutedText = Color(0xFF7484A6)
    val Scrim = Color(0x4D3C3C6E)          // rgba(60,60,110,.45)
}

/** The colours the Project SEKAI widgets actually paint with. Two of them: the light palette and a
 *  dark one that keeps the same teal/mint accent so the app still reads as Project SEKAI at night. */
internal data class ProjectSekaiPalette(
    val page: Color,        // the app background behind the cards
    val surface: Color,     // a card
    val sheet: Color,       // a sheet/popup background
    val ink: Color,         // primary text
    val inkMuted: Color,    // secondary text
    val line: Color,        // card borders
    val teal: Color,        // accent: icons, active labels, gradient start
    val tealDeep: Color,    // gradient end
    val mint: Color,        // the "ok" fill and the selected wash
    val sky: Color,         // the press highlight
    val onTeal: Color,      // text on top of teal/mint
    val isDark: Boolean,
) {
    companion object {
        val Light = ProjectSekaiPalette(
            page = Color(0xFFF3F4FB),
            surface = Color(0xFFFFFFFF),
            sheet = ProjectSekaiColors.Card,
            ink = ProjectSekaiColors.Ink,
            inkMuted = Color(0xFF6B7396),
            line = Color(0xFFDDE1EE),
            teal = Color(0xFF17A79E),
            tealDeep = ProjectSekaiColors.TealGradientEnd,
            mint = ProjectSekaiColors.Mint,
            sky = ProjectSekaiColors.Sky,
            onTeal = Color(0xFF12312F),
            isDark = false,
        )
        val Dark = ProjectSekaiPalette(
            page = Color(0xFF12152B),
            surface = Color(0xFF1E2340),
            sheet = Color(0xFF1A1F39),
            ink = Color(0xFFEDEFFA),
            inkMuted = Color(0xFF98A1C6),
            line = Color(0xFF2C3357),
            teal = Color(0xFF4AD6CC),
            tealDeep = ProjectSekaiColors.TealGradientEnd,
            mint = Color(0xFF6EE7D2),
            sky = ProjectSekaiColors.Sky,
            onTeal = Color(0xFF07221F),
            isDark = true,
        )
    }
}

/** The palette of the Project SEKAI UI. Provided once by the shell; every widget reads it from here. */
internal val LocalProjectSekai = staticCompositionLocalOf { ProjectSekaiPalette.Light }

internal val ProjectSekaiCardShape = RoundedCornerShape(16.dp)
internal val ProjectSekaiSheetShape = RoundedCornerShape(20.dp)
internal val ProjectSekaiPill = CircleShape // border-radius:999px

// ---- press feedback --------------------------------------------------------------------------
// The site has no ripple: the rest state is flat and :active/:hover flips the fill to --sky with
// a glow. `collectIsPressedAsState`-driven background swaps are the Compose equivalent of that.

@Composable
private fun rememberProjectSekaiPressed(source: MutableInteractionSource): Boolean {
    val pressed by source.collectIsPressedAsState()
    return pressed
}

/** Pill action button. Two looks sharing one call, like MButton: [primary] = mint fill (.btn-ok),
 *  otherwise white/card fill with a border (.btn-c). Both flip to --sky while pressed. */
@Composable
internal fun PButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val p = LocalProjectSekai.current
    val haptic = LocalMHaptics.current
    val source = remember { MutableInteractionSource() }
    val pressed = rememberProjectSekaiPressed(source)
    val bg = when {
        !enabled -> (if (primary) p.mint else p.surface).copy(alpha = 0.4f)
        pressed -> p.sky
        primary -> p.mint
        else -> p.surface
    }
    val fg = when {
        !enabled -> p.inkMuted
        pressed -> Color.White
        else -> p.ink
    }
    val border = if (!primary && !pressed) BorderStroke(2.dp, p.line) else null
    androidx.compose.material3.Surface(
        onClick = { haptic(); onClick() }, enabled = enabled, shape = ProjectSekaiPill, color = bg,
        contentColor = fg, border = border,
        modifier = modifier,
        interactionSource = source,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 13.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically, content = content,
        )
    }
}

/** Selectable card: platform grid (.plat-btn), method list (.mbtn), menu cards (.mcard) all share
 *  this shape — flat card, tinted mint border + wash when [selected], sky glow while pressed. */
@Composable
internal fun PChoiceCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val p = LocalProjectSekai.current
    val haptic = LocalMHaptics.current
    val source = remember { MutableInteractionSource() }
    val pressed = rememberProjectSekaiPressed(source)
    val (bg, borderColor) = when {
        pressed -> p.sky.copy(alpha = 0.16f) to p.sky
        selected -> p.mint.copy(alpha = 0.22f) to p.mint
        else -> p.surface to p.line
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(ProjectSekaiCardShape)
            .background(bg)
            .border(2.dp, borderColor, ProjectSekaiCardShape)
            .clickable(interactionSource = source, indication = null) { haptic(); onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

// ---- cards and rows --------------------------------------------------------------------------

/** The little rounded square the Project SEKAI icon badges became. */
@Composable
internal fun PIconBubble(icon: ImageVector, size: Dp = 34.dp, tint: Color? = null) {
    val p = LocalProjectSekai.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(p.mint.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: p.teal, modifier = Modifier.size(size * 0.55f))
    }
}

/** A section title in the Project SEKAI small-caps style, with the rows under it. */
@Composable
internal fun PSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalProjectSekai.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title, color = p.teal, fontSize = 12.sp, fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp, modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** A card row: icon bubble, title, optional summary and something on the right. */
@Composable
internal fun PRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val p = LocalProjectSekai.current
    val haptic = LocalMHaptics.current
    val source = remember { MutableInteractionSource() }
    val pressed = rememberProjectSekaiPressed(source)
    val bg = when {
        pressed -> p.sky.copy(alpha = 0.16f)
        selected -> p.mint.copy(alpha = 0.22f)
        else -> p.surface
    }
    val border = when {
        pressed -> p.sky
        selected -> p.mint
        else -> p.line
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(ProjectSekaiCardShape)
            .background(bg)
            .border(2.dp, border, ProjectSekaiCardShape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = source, indication = null) { haptic(); onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) PIconBubble(icon)
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(summary, color = p.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (trailing != null) trailing()
    }
}

/** A card that holds a title and something bigger under it (a segmented control, swatches…). */
@Composable
internal fun PTitledBlock(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalProjectSekai.current
    Column(
        modifier.fillMaxWidth().clip(ProjectSekaiCardShape).background(p.surface)
            .border(2.dp, p.line, ProjectSekaiCardShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) PIconBubble(icon)
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

/** A row that goes somewhere: a Project SEKAI card with a trailing chevron. */
@Composable
internal fun PArrowRow(
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val p = LocalProjectSekai.current
    PRow(title, summary, icon, modifier, onClick = onClick) {
        Icon(MiuixIcons.ChevronForward, contentDescription = null, tint = p.inkMuted, modifier = Modifier.size(20.dp))
    }
}

/** The site's on/off switch: a mint track with a white knob. */
@Composable
internal fun PSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalProjectSekai.current
    val haptic = LocalMHaptics.current
    val anim by animateFloatAsState(if (checked) 1f else 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f), label = "switch")
    val track = androidx.compose.ui.graphics.lerp(p.line, p.mint, anim)
    Box(
        modifier
            .width(46.dp).height(26.dp)
            .clip(CircleShape).background(track)
            .clickable { haptic(); onChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(start = 3.dp)
                .offset(x = 20.dp * anim)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) p.teal else Color.White),
        )
    }
}

/** A row with an on/off switch on the right. */
@Composable
internal fun PSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    PRow(title, summary, icon, onClick = { onChange(!checked) }) {
        PSwitch(checked, onChange)
    }
}

/**
 * A row that picks one of several values. The options open inside the card itself (the site
 * keeps everything in cards; a floating menu would look out of place).
 */
@Composable
internal fun PDropdownRow(
    title: String,
    options: List<String>,
    selected: Int,
    icon: ImageVector? = null,
    onSelect: (Int) -> Unit,
) {
    val p = LocalProjectSekai.current
    val haptic = LocalMHaptics.current
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(ProjectSekaiCardShape).background(p.surface)
            .border(2.dp, if (open) p.teal else p.line, ProjectSekaiCardShape),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { haptic(); open = !open }.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) PIconBubble(icon)
            Column(Modifier.weight(1f)) {
                Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(options.getOrElse(selected) { "" }, color = p.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(MIcons.ExpandMore, contentDescription = null, tint = p.inkMuted, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEachIndexed { i, o ->
                    val on = i == selected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (on) p.mint.copy(alpha = 0.3f) else Color.Transparent)
                            .clickable { haptic(); onSelect(i); open = false }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(MiuixIcons.Ok, contentDescription = null, tint = if (on) p.teal else Color.Transparent,
                            modifier = Modifier.size(16.dp))
                        Text(
                            o, color = p.ink, fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.Black else FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** A row of pill buttons, one selected. */
@Composable
internal fun PSegmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = LocalProjectSekai.current
    val haptic = LocalMHaptics.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(ProjectSekaiPill)
                    .background(if (on) p.mint else p.surface)
                    .border(2.dp, if (on) p.teal else p.line, ProjectSekaiPill)
                    .clickable { haptic(); onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = p.ink, fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Black else FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---- inputs ------------------------------------------------------------------------------------

/** A text box in the Project SEKAI flat card style: white rounded box, teal border when focused. */
@Composable
internal fun PTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    hint: String? = null,
    number: Boolean = false,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val p = LocalProjectSekai.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, color = p.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.Black)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            cursorBrush = SolidColor(p.teal),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box(
                    Modifier.fillMaxWidth().clip(ProjectSekaiCardShape).background(p.surface)
                        .border(2.dp, p.line, ProjectSekaiCardShape).padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text("—", color = p.inkMuted.copy(alpha = 0.55f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    innerTextField()
                }
            },
        )
        if (hint != null) Text(hint, color = p.inkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** A text box bound to a project field. */
@Composable
internal fun PField(
    label: String, key: String, st: Store, hint: String? = null, number: Boolean = false,
    modifier: Modifier = Modifier,
) {
    PTextField(st.str(key), { st.set(key, it) }, label = label, hint = hint, number = number, modifier = modifier)
}

/** "Pick a file" row: the file name on the left, a pill button on the right. */
@Composable
internal fun PFileRow(
    label: String,
    path: String,
    onPick: () -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val p = LocalProjectSekai.current
    val haptic = LocalMHaptics.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) Text(label, color = p.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Row(
            Modifier.fillMaxWidth().clip(ProjectSekaiCardShape).background(p.surface).border(2.dp, p.line, ProjectSekaiCardShape)
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (path.isBlank()) "No file chosen" else File(path).name,
                color = if (path.isBlank()) p.inkMuted else p.ink,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PButton(onClick = onPick) { Text("Choose", fontSize = 13.sp, fontWeight = FontWeight.Black) }
            if (onClear != null) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).clickable { haptic(); onClear() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MiuixIcons.Clear, contentDescription = "Clear", tint = p.inkMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ---- sheet shell ------------------------------------------------------------------------------
// Mirrors .ssheet + .sh: gradient header bar (teal -> teal2), optional back button, optional step
// badge, card body. This is what the Import and Compile flows below are built from.

@Composable
internal fun ProjectSekaiSheet(
    title: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalProjectSekai.current
    val haptic = LocalMHaptics.current
    Box(
        // The sheet slides up from the bottom of the screen, so only the top corners are rounded.
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(p.sheet),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(ProjectSekaiColors.Teal, ProjectSekaiColors.TealGradientEnd)))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onBack != null) {
                    IconButton(onClick = { haptic(); onBack() }, modifier = Modifier.size(30.dp)) {
                        Icon(MiuixIcons.Back, contentDescription = "Back", tint = Color.White)
                    }
                }
                Text(
                    title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp,
                    letterSpacing = 1.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                if (badge != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) { Text(badge, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp) }
                }
            }
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
internal fun PHint(text: String, modifier: Modifier = Modifier) {
    val p = LocalProjectSekai.current
    Text(text, color = p.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

// ---- dialogs ----------------------------------------------------------------------------------

/** The site's popup: a card in the middle of a scrim, with pill buttons along the bottom. */
@Composable
internal fun PDialog(
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
    val p = LocalProjectSekai.current
    val haptic = LocalMHaptics.current
    Dialog(onDismissRequest = { if (cancellable) onDismiss?.invoke() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 500f)) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = appear.value }
                    .background(ProjectSekaiColors.Scrim)
                    .clickable(interactionSource = null, indication = null) { if (cancellable) onDismiss?.invoke() },
            )
            Column(
                Modifier
                    .padding(24.dp)
                    .graphicsLayer {
                        val v = appear.value
                        scaleX = 0.88f + 0.12f * v
                        scaleY = 0.88f + 0.12f * v
                        alpha = (v * 4f).coerceAtMost(1f)
                    }
                    .clip(ProjectSekaiCardShape)
                    .background(p.surface)
                    .border(2.dp, p.line, ProjectSekaiCardShape)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (title != null) {
                    Text(title, color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                Column(content = text)
                if (confirmText != null || dismissText != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (dismissText != null) {
                            PButton(
                                modifier = Modifier.weight(1f), onClick = { haptic(); onDismiss?.invoke() },
                            ) { Text(dismissText, fontWeight = FontWeight.Black) }
                        }
                        if (confirmText != null) {
                            PButton(
                                modifier = Modifier.weight(1f), primary = true, enabled = confirmEnabled,
                                onClick = { haptic(); onConfirm?.invoke() },
                            ) { Text(confirmText, fontWeight = FontWeight.Black) }
                        }
                    }
                }
            }
        }
    }
}

/** The site's progress bar: a mint bar sliding in a light track. */
@Composable
internal fun PProgress(modifier: Modifier = Modifier) {
    val p = LocalProjectSekai.current
    val transition = rememberInfiniteTransition(label = "progress")
    val x by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "progressX",
    )
    var trackW by remember { mutableIntStateOf(0) }
    val barW = 44.dp
    Box(
        modifier.fillMaxWidth().height(8.dp).clip(ProjectSekaiPill).background(p.line)
            .onSizeChanged { trackW = it.width },
    ) {
        Box(
            Modifier.width(barW).fillMaxHeight()
                .graphicsLayer { translationX = (trackW + barW.toPx()) * x - barW.toPx() }
                .clip(ProjectSekaiPill).background(p.teal),
        )
    }
}

// ---- app chrome --------------------------------------------------------------------------------

/** The page header: the Project SEKAI teal gradient bar with the page name in it. */
@Composable
internal fun PTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    val p = LocalProjectSekai.current
    val haptic = LocalMHaptics.current
    Box(
        Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(ProjectSekaiColors.Teal, ProjectSekaiColors.TealGradientEnd)))
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color.White)
                        .clickable { haptic(); onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MiuixIcons.Back, contentDescription = "Back", tint = p.ink, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** The bottom bar: four cards in a row, the selected one tinted mint. */
@Composable
internal fun PTabBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    tabs: List<Pair<ImageVector, String>>,
    modifier: Modifier = Modifier,
) {
    val p = LocalProjectSekai.current
    val haptic = LocalMHaptics.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface)
            .border(2.dp, p.line, RoundedCornerShape(22.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { i, (icon, label) ->
            val on = i == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (on) p.mint.copy(alpha = 0.35f) else Color.Transparent)
                    .clickable { haptic(); onSelect(i) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, contentDescription = label, tint = if (on) p.teal else p.inkMuted, modifier = Modifier.size(21.dp))
                Spacer(Modifier.height(2.dp))
                Text(
                    label, color = if (on) p.ink else p.inkMuted,
                    fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1,
                )
            }
        }
    }
}
