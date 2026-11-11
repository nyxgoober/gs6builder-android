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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.isRuntimeShaderSupported
import com.utsk.gs6builder.glass.LiquidBottomTab
import com.utsk.gs6builder.glass.LiquidBottomTabs
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Settings

// Tune the big headings here. The title shrinks smoothly from the expanded to the collapsed size as you scroll.
private val HEADING_EXPANDED_SIZE = 46.sp
private val HEADING_EXPANDED_LINE = 52.sp
private val HEADING_COLLAPSED_SIZE = 22.sp
private val HEADING_COLLAPSED_LINE = 28.sp
private val HEADING_WEIGHT = FontWeight.ExtraBold
private val HEADING_BAR_HEIGHT = 172.dp

/** One page with an M3 large top app bar that collapses as the page scrolls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MPage(
    title: String, scroll: ScrollState, bottomPad: Dp, onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
        MLargeTitle(title, behavior, onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MLargeTitle(title: String, behavior: TopAppBarScrollBehavior, onBack: (() -> Unit)?) {
    LargeTopAppBar(
        title = {
            val f = behavior.state.collapsedFraction
            Text(
                title,
                fontSize = lerp(HEADING_EXPANDED_SIZE, HEADING_COLLAPSED_SIZE, f),
                lineHeight = lerp(HEADING_EXPANDED_LINE, HEADING_COLLAPSED_LINE, f),
                fontWeight = HEADING_WEIGHT, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(MiuixIcons.Back, contentDescription = "Back") } },
        expandedHeight = HEADING_BAR_HEIGHT,
        scrollBehavior = behavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/** The Material UI. Same controller, pages-by-pager and glass tab bar as the Miuix UI, with Material 3 widgets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaterialShell(c: AppController, dark: Boolean) {
  with(c) {
    val density = LocalDensity.current
    var bottomPx by remember { mutableIntStateOf(0) }
    val backdrop = rememberLayerBackdrop()
    val useGlass = s.glassBar && isRuntimeShaderSupported()
    val barIndex by remember { derivedStateOf { currentTabIndex } }
    val tabIcons = listOf(MiuixIcons.Import, MiuixIcons.Folder, MiuixIcons.Edit, MiuixIcons.Settings)
    val painters = tabIcons.map { rememberVectorPainter(it) }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The M3 NavigationBar pads for the system bars itself; only the floating glass bar needs the inset.
    val barInset = if (useGlass) navInset else 0.dp
    val bg = MaterialTheme.colorScheme.background

    BackHandler(enabled = subPage != null) { subPage = null }

    // Every Material row/button below fires the controller's debounced tick, which the haptics setting gates.
    CompositionLocalProvider(LocalMHaptics provides { c.tick() }) {
    Box(Modifier.fillMaxSize().background(bg)) {
        // Page content. It is also the backdrop the glass bar refracts.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 2) { p ->
                val scroll = when (p) { 0 -> filesScroll; 1 -> metaScroll; else -> settingsScroll }
                MPage(TAB_LABELS[p + 1], scroll, with(density) { bottomPx.toDp() } + barInset) {
                    when (p) {
                        0 -> MFilesPage(st, onPickFile, onCheckRow)
                        1 -> MMetadataPage(st, onRegen = onRegen, onCopy = onCopy)
                        else -> MSettingsPage(
                            s, onNewProject = onNewProject,
                            version = currentVersion, checkingUpdate = checkingUpdate,
                            onCheckUpdate = onCheckUpdate, onOpenLicenses = onOpenLicenses,
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
                    MPage(
                        title = when (lastSubPage) { SubPage.Licenses -> "Licenses"; SubPage.DevFiles -> "App data" },
                        scroll = rememberScrollState(), bottomPad = navInset + 16.dp, onBack = { subPage = null },
                    ) {
                        when (lastSubPage) {
                            SubPage.Licenses -> MLicensesContent()
                            SubPage.DevFiles -> MDevFileExplorer(
                                root = b.appDataDir,
                                onOpenFile = { f -> openDevFile(f) }, onExportFile = { f -> exportDevFile(f) },
                            )
                        }
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
                    // Pad before the background: nothing may be painted behind the system navigation
                    // pill, or it reads as a second bar when the pill is picked up.
                    .padding(bottom = barInset)
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to bg))
                    .onSizeChanged { bottomPx = it.height },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MButton(modifier = Modifier.weight(1f), enabled = ready && !busy, onClick = { buildZip() }) { Text("Build zip") }
                    val canCompile = ready && missingFields().isEmpty()
                    val compileClick = { onCompilePressed() }
                    if (canCompile) {
                        MButton(modifier = Modifier.weight(1f), primary = true, enabled = !busy, onClick = compileClick) { Text("Compile") }
                    } else {
                        MButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = compileClick) { Text("Compile") }
                    }
                }
                if (useGlass) {
                    val contentColor = MaterialTheme.colorScheme.onSurface
                    LiquidBottomTabs(
                        selectedTabIndex = { barIndex },
                        onTabSelected = { onTab(it) },
                        backdrop = backdrop,
                        tabsCount = TAB_LABELS.size,
                        dark = dark,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
                    ) {
                        TAB_LABELS.forEachIndexed { i, label ->
                            LiquidBottomTab({ onTab(i) }) {
                                Box(Modifier.size(28.dp).paint(painters[i], colorFilter = ColorFilter.tint(contentColor)))
                                BasicText(label, style = TextStyle(contentColor, 12.sp))
                            }
                        }
                    }
                } else {
                    NavigationBar {
                        TAB_LABELS.forEachIndexed { i, label ->
                            NavigationBarItem(
                                selected = barIndex == i, onClick = { onTab(i) },
                                icon = { Icon(painters[i], contentDescription = label) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }

        // ---- sheets + dialogs ------------------------------------------------------------------
        if (showImport) {
            ModalBottomSheet(onDismissRequest = { closeImportSheet() }) {
                Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Import", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        MDropdownRow("Source", IMPORT_KINDS, importKind) { importKind = it }
                    }
                    if (importKind < 2) {
                        OutlinedTextField(
                            value = url, onValueChange = { url = it }, label = { Text("Level link") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        MHint("Pick a project .zip exported by this app or the desktop builder.")
                    }
                    MButton(
                        modifier = Modifier.fillMaxWidth(), primary = true,
                        enabled = ready && !busy && (importKind == 2 || url.isNotBlank()),
                        onClick = { requestImport() },
                    ) { Text(if (importKind == 2) "Choose .zip…" else "Import") }                }
            }
        }

        if (showCompile) {
            ModalBottomSheet(onDismissRequest = { closeCompileSheet() }) {
                Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Compile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        MSwitchRow("Also build project ZIP", null, compileZip) { compileZip = it }
                    }
                    MButton(modifier = Modifier.fillMaxWidth(), primary = true, onClick = { closeCompileSheet(); compile(compileZip) }) { Text("Compile") }
                }
            }
        }

        // While Python works, lock the whole app behind an unclosable progress dialog.
        if (busy) {
            AlertDialog(
                onDismissRequest = {}, title = { Text("Working…") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            )
        }

        MConfirmDialog(
            show = pendingOverwrite != null, title = "Overwrite everything?",
            body = "Importing replaces every field, file and chart you have filled in. This can't be undone.",
            confirmText = "Overwrite", onConfirm = { confirmOverwrite() }, onDismiss = { pendingOverwrite = null },
        )
        MConfirmDialog(
            show = confirmReset, title = "Start a new project?",
            body = "This clears every field, file and chart.", confirmText = "Clear",
            onConfirm = { confirmNewProject() }, onDismiss = { confirmReset = false },
        )
        MConfirmDialog(
            show = unsupported != null, title = "Unsupported chart features",
            body = "This chart uses features the converter does not support:\n\n${unsupported?.second ?: ""}\n\n" +
                "Continue anyway? Unsupported parts are skipped or replaced with the closest equivalent.",
            confirmText = "Continue", onConfirm = { confirmUnsupported() }, onDismiss = { unsupported = null },
        )
        MConfirmDialog(
            show = updateTag != null, title = "Update available",
            body = "Version ${updateTag ?: ""} is out" +
                (if (updateTag?.let { UpdateChecker.isUnstable(it) } == true) " (pre-release)" else "") +
                " (you have $currentVersion).\n\nOpen the release page in your browser to download it?",
            confirmText = "Update", onConfirm = { confirmUpdate() }, onDismiss = { updateTag = null },
        )
        MConfirmDialog(
            show = unstableTag != null, title = "Unstable build",
            body = "${unstableTag ?: ""} is a pre-release (alpha, beta, preview or release candidate). " +
                "It may not be stable, and things are subject to change.\n\nOpen it anyway?",
            confirmText = "Open anyway", onConfirm = { confirmUnstable() }, onDismiss = { unstableTag = null },
        )
        MHexDialog(devHex, onDismiss = { devHex = null })
        MInfoDialog(show = devFileText != null, title = devFileText?.first, body = devFileText?.second ?: "", onDismiss = { devFileText = null })
        MInfoDialog(show = message != null, title = null, body = message ?: "", onDismiss = { message = null })
        MInfoDialog(show = errors != null, title = "Cannot build", body = errors?.joinToString("\n") { "• $it" } ?: "", onDismiss = { errors = null })
    }
    }
  }
}

// ---- Material dialogs ----------------------------------------------------------------------------

@Composable
private fun DialogScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), content = content)
}

@Composable
private fun MInfoDialog(show: Boolean, title: String?, body: String, onDismiss: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { DialogScroll { Text(body) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun MConfirmDialog(
    show: Boolean, title: String, body: String, confirmText: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { DialogScroll { Text(body) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MHexDialog(file: HexFile?, onDismiss: () -> Unit) {
    if (file == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name) },
        text = {
            Column {
                val shown = if (file.bytes.size.toLong() < file.totalSize)
                    "First ${file.bytes.size} of ${file.totalSize} bytes. Hold the file in the list to export all of it."
                else "${file.totalSize} bytes. Hold the file in the list to export it."
                MHint(shown, Modifier.padding(bottom = 8.dp))
                val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items((file.bytes.size + HEX_BYTES_PER_LINE - 1) / HEX_BYTES_PER_LINE) { line ->
                            Text(hexLine(file.bytes, line), style = mono, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
