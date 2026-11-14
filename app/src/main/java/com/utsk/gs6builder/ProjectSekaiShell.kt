package com.utsk.gs6builder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Settings

// The Project SEKAI UI: teal/mint/sky cards and pill buttons, applied to
// the whole app. Same controller and same data as the Material UI in MaterialShell.kt; only the
// widgets differ, so switching design keeps your work.

/** One page: the teal header bar plus a scrolling column of cards. */
@Composable
private fun PPage(
    title: String, scroll: ScrollState, bottomPad: Dp, onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PTopBar(title, onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                .padding(horizontal = 16.dp).padding(top = 16.dp, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

/** The Project SEKAI UI. Same controller, pages-by-pager and tab bar as the Material UI, with Project SEKAI widgets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectSekaiShell(c: AppController, dark: Boolean) {
  with(c) {
    val palette = if (dark) ProjectSekaiPalette.Dark else ProjectSekaiPalette.Light
    val density = LocalDensity.current
    var bottomPx by remember { mutableIntStateOf(0) }
    val barIndex by remember { derivedStateOf { currentTabIndex } }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bg = palette.page
    val tabIcons = listOf(MiuixIcons.Import, MiuixIcons.Folder, MiuixIcons.Edit, MiuixIcons.Settings)
    val tabs = tabIcons.zip(TAB_LABELS) { icon, label -> icon to label }

    BackHandler(enabled = subPage != null) { subPage = null }

    // Every Project SEKAI row/button fires the controller's debounced tick, which the haptics setting gates.
    CompositionLocalProvider(LocalProjectSekai provides palette, LocalMHaptics provides { c.tick() }) {
    Box(Modifier.fillMaxSize().background(bg)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 2) { p ->
            val scroll = when (p) { 0 -> filesScroll; 1 -> metaScroll; else -> settingsScroll }
            PPage(TAB_LABELS[p + 1], scroll, with(density) { bottomPx.toDp() } + navInset) {
                when (p) {
                    0 -> PFilesPage(st, onPickFile, onCheckRow)
                    1 -> PMetadataPage(st, onRegen = onRegen, onCopy = onCopy)
                    else -> PSettingsPage(
                        s, onNewProject = onNewProject,
                        version = currentVersion, checkingUpdate = checkingUpdate,
                        onCheckUpdate = onCheckUpdate, onOpenLicenses = onOpenLicenses,
                        onOpenAppInfo = onOpenAppInfo,
                        onPickOutputFolder = onPickFolder, onResetOutputFolder = onResetFolder,
                        onUnlockDev = onUnlockDev, onOpenDevFiles = onOpenDevFiles, onDisableDev = onDisableDev,
                    )
                }
            }
        }

        // Licenses / file browser slide over the pages (which stay composed underneath).
        AnimatedVisibility(
            visible = subPage != null,
            enter = slideInHorizontally(initialOffsetX = { it / 3 }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it / 3 }) + fadeOut(),
        ) {
            Box(Modifier.fillMaxSize().background(bg).pointerInput(Unit) { detectTapGestures { } }) {
                PPage(
                    title = when (lastSubPage) {
                        SubPage.Licenses -> "Licenses"; SubPage.DevFiles -> "App data"; SubPage.AppInfo -> "App info"
                    },
                    scroll = rememberScrollState(), bottomPad = navInset + 16.dp, onBack = { subPage = null },
                ) {
                    when (lastSubPage) {
                        SubPage.Licenses -> PLicensesContent()
                        SubPage.DevFiles -> PDevFileExplorer(
                            root = b.appDataDir,
                            onOpenFile = { f -> openDevFile(f) }, onExportFile = { f -> exportDevFile(f) },
                        )
                        SubPage.AppInfo -> AppInfoContent(
                            context = context, backend = b, ownScroll = false,
                            colors = AppInfoColors(
                                on = palette.ink,
                                muted = palette.inkMuted,
                                card = palette.surface,
                            ),
                            textStyle = TextStyle(
                                color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }
        }

        // Actions and the tab bar float over the bottom of the page; they slide away while a sub page is open.
        AnimatedVisibility(
            visible = subPage == null,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    // Fade the page out above the controls, then stay fully opaque through the system
                    // navigation inset so scrolled content can't show beneath the gesture pill.
                    .background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.3f to bg))
                    .padding(bottom = navInset)
                    .onSizeChanged { bottomPx = it.height },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PButton(modifier = Modifier.weight(1f), enabled = ready && !busy, onClick = { buildZip() }) {
                        Text("Build zip", fontWeight = FontWeight.Black)
                    }
                    val canCompile = ready && missingFields().isEmpty()
                    PButton(modifier = Modifier.weight(1f), primary = canCompile, enabled = !busy, onClick = { onCompilePressed() }) {
                        Text("Compile", fontWeight = FontWeight.Black)
                    }
                }
                PTabBar(
                    selected = barIndex, onSelect = { onTab(it) }, tabs = tabs,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
                )
            }
        }

        // ---- sheets + dialogs ------------------------------------------------------------------
        if (showImport) {
            ModalBottomSheet(
                onDismissRequest = { closeImportSheet() },
                // Project SEKAI mode draws its own header/card chrome; strip M3's default scrim container
                // padding/surface so the Sekai card is the only thing visible.
                containerColor = Color.Transparent,
                dragHandle = null,
            ) {
                ProjectSekaiImportSheet(c)
            }
        }

        if (showCompile) {
            ModalBottomSheet(
                onDismissRequest = { closeCompileSheet() },
                containerColor = Color.Transparent,
                dragHandle = null,
            ) {
                ProjectSekaiCompileSheet(c)
            }
        }

        // While Python works, lock the whole app behind an unclosable progress dialog.
        PDialog(
            show = busy, title = "Working…", cancellable = false,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PProgress()
                    Text(status, color = palette.ink, fontWeight = FontWeight.Bold)
                }
            },
        )

        PConfirmDialog(
            show = pendingOverwrite != null, title = "Overwrite everything?",
            body = "Importing replaces every field, file and chart you have filled in. This can't be undone.",
            confirmText = "Overwrite", onConfirm = { confirmOverwrite() }, onDismiss = { pendingOverwrite = null },
        )
        PConfirmDialog(
            show = confirmReset, title = "Start a new project?",
            body = "This clears every field, file and chart.", confirmText = "Clear",
            onConfirm = { confirmNewProject() }, onDismiss = { confirmReset = false },
        )
        PConfirmDialog(
            show = unsupported != null, title = "Unsupported chart features",
            body = "This chart uses features the converter does not support:\n\n${unsupported?.second ?: ""}\n\n" +
                "Continue anyway? Unsupported parts are skipped or replaced with the closest equivalent.",
            confirmText = "Continue", onConfirm = { confirmUnsupported() }, onDismiss = { unsupported = null },
        )
        PConfirmDialog(
            show = updateTag != null, title = "Update available",
            body = "Version ${updateTag ?: ""} is out" +
                (if (updateTag?.let { UpdateChecker.isUnstable(it) } == true) " (pre-release)" else "") +
                " (you have $currentVersion).\n\nOpen the release page in your browser to download it?",
            confirmText = "Update", onConfirm = { confirmUpdate() }, onDismiss = { updateTag = null },
        )
        PConfirmDialog(
            show = unstableTag != null, title = "Unstable build",
            body = "${unstableTag ?: ""} is a pre-release (alpha, beta, preview or release candidate). " +
                "It may not be stable, and things are subject to change.\n\nOpen it anyway?",
            confirmText = "Open anyway", onConfirm = { confirmUnstable() }, onDismiss = { unstableTag = null },
        )
        PHexDialog(devHex, onDismiss = { devHex = null })
        PInfoDialog(show = devFileText != null, title = devFileText?.first, body = devFileText?.second ?: "", onDismiss = { devFileText = null })
        PInfoDialog(show = message != null, title = null, body = message ?: "", onDismiss = { message = null })
        PInfoDialog(show = errors != null, title = "Cannot build", body = errors?.joinToString("\n") { "• $it" } ?: "", onDismiss = { errors = null })
    }
    }
  }
}

// ---- Project SEKAI dialogs (animated reveal) -----------------------------------------------------------

@Composable
private fun PInfoDialog(show: Boolean, title: String?, body: String, onDismiss: () -> Unit) {
    PDialog(
        show = show, title = title,
        text = { Text(body, color = LocalProjectSekai.current.ink) },
        confirmText = "OK", onConfirm = onDismiss, onDismiss = onDismiss,
    )
}

@Composable
private fun PConfirmDialog(
    show: Boolean, title: String, body: String, confirmText: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    PDialog(
        show = show, title = title,
        text = { Text(body, color = LocalProjectSekai.current.ink) },
        confirmText = confirmText, onConfirm = onConfirm,
        dismissText = "Cancel", onDismiss = onDismiss,
    )
}

@Composable
private fun PHexDialog(file: HexFile?, onDismiss: () -> Unit) {
    if (file == null) return
    val p = LocalProjectSekai.current
    PDialog(
        show = true, title = file.name, onDismiss = onDismiss, confirmText = "OK", onConfirm = onDismiss,
        text = {
            val shown = if (file.bytes.size.toLong() < file.totalSize)
                "First ${file.bytes.size} of ${file.totalSize} bytes. Hold the file in the list to export all of it."
            else "${file.totalSize} bytes. Hold the file in the list to export it."
            PHint(shown, Modifier.padding(bottom = 8.dp))
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items((file.bytes.size + HEX_BYTES_PER_LINE - 1) / HEX_BYTES_PER_LINE) { line ->
                        Text(
                            hexLine(file.bytes, line),
                            color = p.inkMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1,
                        )
                    }
                }
            }
        },
    )
}
