package com.utsk.gs6builder

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * "App info" sub page for the About section of Settings: version/versionCode, the GitHub release
 * matching the installed version (published time + pre-release/beta flag), and the build
 * attestation the release workflow generated for this exact APK, matched by SHA-256 digest.
 */

private const val REPO = "nyxgoober/gs6builder-android"
private const val API = "https://api.github.com/repos/$REPO"

internal class AppInfoBundle(
    val versionName: String,
    val versionCode: Long,
    val sha256: String,
    val releaseTag: String?,
    val releaseTime: String?,
    val isPrerelease: Boolean,
    val hasRelease: Boolean,
    val attestation: Attestation?,
    val error: String? = null,
)

internal class Attestation(
    val predicateType: String,
    val workflowName: String?,
    val actor: String?,
    val invocationUrl: String?,
    val finishedAt: String?,
    val commit: String?,
)

internal object AppInfoFetcher {
    suspend fun fetch(context: Context): AppInfoBundle = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        val versionName = info.versionName ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val apk = pm.getApplicationInfo(context.packageName, 0).sourceDir
        val sha256 = runCatching { sha256Of(apk) }.getOrDefault("")

        runCatching {
            val release = fetchReleaseFor(versionName)
            val attestation = if (sha256.isNotBlank()) fetchAttestation(sha256) else null
            AppInfoBundle(
                versionName = versionName, versionCode = versionCode, sha256 = sha256,
                releaseTag = release?.optString("tag_name")?.takeIf { it.isNotEmpty() },
                releaseTime = release?.optString("published_at")?.takeIf { it.isNotEmpty() }?.let { formatIso(it) },
                isPrerelease = release?.optBoolean("prerelease") ?: false,
                hasRelease = release != null,
                attestation = attestation,
            )
        }.getOrElse {
            AppInfoBundle(
                versionName = versionName, versionCode = versionCode, sha256 = sha256,
                releaseTag = null, releaseTime = null, isPrerelease = false, hasRelease = false,
                attestation = null, error = it.message ?: it.javaClass.simpleName,
            )
        }
    }

    /** The release whose tag matches the installed versionName (e.g. 2.1.0 → "v2.1.0"), newest first. */
    private fun fetchReleaseFor(versionName: String): JSONObject? {
        val body = get("$API/releases?per_page=100") ?: return null
        val arr = JSONArray(body)
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val tag = r.optString("tag_name", "")
            // Compare only the dotted core: "v2.1.0", "v2.1.0-beta.2" and "2.1.0" all match 2.1.0.
            if (coreOf(tag) == versionName) return r
        }
        return null
    }

    private fun coreOf(tag: String): String = tag.trim()
        .removePrefix("v").removePrefix("V").substringBefore('+').substringBefore('-')

    /**
     * GitHub artifact attestation lookup by the APK's SHA-256 digest. The workflow signs every
     * release APK with actions/attest-build-provenance; a hit proves this binary came from it.
     */
    private fun fetchAttestation(sha256: String): Attestation? {
        val url = "$API/attestations?digest_type=sha256&digest=sha256:$sha256"
        val body = get(url) ?: return null
        val arr = JSONObject(body).optJSONArray("attestations") ?: return null
        if (arr.length() == 0) return null
        // The payload is the base64 DSSE envelope body: the raw in-toto statement JSON.
        for (i in 0 until arr.length()) {
            val payload = arr.getJSONObject(i).optJSONObject("bundle")
                ?.optJSONObject("dsse_envelope")?.optString("payload") ?: continue
            val st = runCatching {
                JSONObject(String(android.util.Base64.decode(payload, android.util.Base64.DEFAULT)))
            }.getOrNull() ?: continue
            val p = st.optJSONObject("predicate") ?: continue
            val run = p.optJSONObject("run_details")
            val build = p.optJSONObject("buildDefinition")
            val meta = run?.optJSONObject("metadata")
            val invocation = run?.optString("invocationId")?.takeIf { it.isNotEmpty() }
                ?: build?.optJSONObject("invocation")?.optJSONObject("configSource")
                    ?.optString("uri")?.takeIf { it.isNotEmpty() }
            // Commit id: the source dependency carries the git digest.
            var commit: String? = null
            val deps = build?.optJSONArray("resolvedDependencies") ?: p.optJSONArray("resolvedDependencies")
            if (deps != null) for (j in 0 until deps.length()) {
                val d = deps.getJSONObject(j)
                commit = d.optJSONObject("digest")?.optString("gitCommit")?.takeIf { it.isNotEmpty() } ?: continue
            }
            return Attestation(
                predicateType = st.optString("predicateType", "unknown"),
                workflowName = run?.optString("displayName")?.takeIf { it.isNotEmpty() }
                    ?: build?.optString("externalIdentifier")?.takeIf { it.isNotEmpty() },
                actor = run?.optString("actor")?.takeIf { it.isNotEmpty() },
                invocationUrl = invocation,
                finishedAt = meta?.optString("finishedOn")?.takeIf { it.isNotEmpty() }?.let { formatIso(it) },
                commit = commit,
            )
        }
        return null
    }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "GS6Builder-Android")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return null
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun sha256Of(path: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        java.io.FileInputStream(path).use { f ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = f.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun formatIso(iso: String): String = runCatching {
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(iso)) + " (${ZoneId.systemDefault().id})"
    }.getOrDefault(iso)
}

// ---- page ------------------------------------------------------------------------------------

internal class AppInfoColors(val on: Color, val muted: Color, val card: Color)

private val CONFETTI_COLORS = listOf(
    Color(0xFF5DA971), Color(0xFF4FC3C7), Color(0xFFF2B94B), Color(0xFFE8636F),
    Color(0xFF8E7BE8), Color(0xFFE98BC4),
)

@Composable
internal fun AppInfoContent(context: Context, backend: Backend?, colors: AppInfoColors, textStyle: TextStyle, ownScroll: Boolean = true) {
    val muted = textStyle.copy(color = colors.muted)
    val text = textStyle.copy(color = colors.on)
    var info by remember { mutableStateOf<AppInfoBundle?>(null) }
    var pyInfo by remember { mutableStateOf<JSONObject?>(null) }
    var pyError by remember { mutableStateOf<String?>(null) }
    var taps by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableStateOf(0L) }
    var thump by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }
    val iconCenter = remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) {
        info = AppInfoFetcher.fetch(context)
        if (backend != null) runCatching { backend.call("versions") }
            .onSuccess { pyInfo = JSONObject(it) }
            .onFailure { pyError = it.message ?: it.javaClass.simpleName }
    }

    // [ownScroll] must be off when the host page already scrolls (Material's MPage), because a
    // verticalScroll inside another verticalScroll measures with infinite height and crashes.
    Box(Modifier.then(if (ownScroll) Modifier.fillMaxSize() else Modifier)) {
        Column(
            Modifier.then(if (ownScroll) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Easter egg: the icon plays the splash hammer thump on every tap; ten taps in rapid
            // succession burst confetti out of the icon. (Deliberately unlabelled.)
            Box(
                Modifier.size(120.dp)
                    .clip(CircleShape)
                    .onGloballyPositioned { iconCenter.value = it.boundsInRoot().center }
                    .clickable {
                        val now = android.os.SystemClock.uptimeMillis()
                        taps = if (now - lastTapAt > 1200L) 1 else taps + 1
                        lastTapAt = now
                        thump += 1
                        if (taps >= 10) { taps = 0; confetti += 1 }
                    },
                contentAlignment = Alignment.Center,
            ) { ThumpingIcon(trigger = thump) }
            Spacer(Modifier.height(10.dp))
            Text("GS6 Builder", style = text.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = text.fontSize * 1.5f))
            Spacer(Modifier.height(24.dp))

            val i = info
            InfoCard(colors) {
                if (i == null) {
                    LoadingLine(text, muted)
                } else {
                    InfoRow("Version", i.versionName, text, muted)
                    InfoRow("Version code", i.versionCode.toString(), text, muted)
                    InfoRow(
                        "Build type",
                        when {
                            !i.hasRelease -> "Unknown (no matching GitHub release)"
                            i.isPrerelease -> "Beta (pre-release)"
                            else -> "Stable release"
                        },
                        text, muted,
                    )
                    InfoRow("Released", i.releaseTime ?: "Unknown", text, muted)
                    InfoRow("Release tag", i.releaseTag ?: "—", text, muted)
                    InfoRow("Build attestation", if (i.attestation != null) "Signed by the release workflow" else
                        (if (i.error != null) "Unavailable: ${i.error}" else "None found for this APK"), text, muted)
                }
            }
            if (i?.attestation != null) {
                Spacer(Modifier.height(12.dp))
                InfoCard(colors) {
                    val a = i.attestation
                    InfoRow("Predicate", a.predicateType, text, muted)
                    InfoRow("Workflow", a.workflowName ?: "—", text, muted)
                    InfoRow("Built by", a.actor ?: "—", text, muted)
                    InfoRow("Finished", a.finishedAt ?: "—", text, muted)
                    InfoRow("Commit", a.commit?.takeIf { it.length > 12 }?.let { it.take(12) + "…" } ?: (a.commit ?: "—"), text, muted)
                    a.invocationUrl?.let {
                        InfoRow("Run", it.substringAfterLast("/").let { id -> "run $id" }, text, muted)
                    }
                }
            }
            if (i != null && i.sha256.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                InfoCard(colors) {
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        Text("APK SHA-256", style = muted)
                        Text(i.sha256, style = text.copy(fontFamily = FontFamily.Monospace, fontSize = text.fontSize * 0.8f))
                    }
                }
            }

            // Bundled runtime: the embedded Python and the versions of everything that ships with it.
            Spacer(Modifier.height(12.dp))
            InfoCard(colors) {
                InfoRow("Chaquopy", BuildConfig.CHAQUOPY_VERSION, text, muted)
                val py = pyInfo?.optString("python")
                InfoRow("Python (bundled)", if (py != null) py else "Unavailable" + (pyError?.let { ": $it" } ?: ""), text, muted)
            }
            val comps = pyInfo?.optJSONObject("components")
            if (comps != null) {
                Spacer(Modifier.height(12.dp))
                InfoCard(colors) {
                    val keys = comps.names().let { n -> (0 until n.length()).map { n.getString(it) } }.sorted()
                    for (k in keys) InfoRow(k, comps.optString(k), text, muted)
                }
            }
        }
        ConfettiOverlay(
            trigger = confetti,
            origin = iconCenter.value,
            color = colors.on,
            modifier = Modifier.then(if (ownScroll) Modifier.fillMaxSize() else Modifier),
        )
    }
}

@Composable
private fun InfoCard(colors: AppInfoColors, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.card).padding(16.dp),
    ) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
}

@Composable
private fun InfoRow(label: String, value: String, style: TextStyle, muted: TextStyle) {
    Column { Text(label, style = muted); Text(value, style = style) }
}

@Composable
private fun LoadingLine(style: TextStyle, muted: TextStyle) {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(700L); on = !on } }
    Text("Loading…", style = style, modifier = Modifier.graphicsLayer { alpha = if (on) 1f else 0.35f })
}

/** The splash icon artwork, clipped to the launcher circle, with the hammer thump on [trigger]. */
@Composable
private fun ThumpingIcon(trigger: Int, iconSize: Dp = 120.dp) {
    val art = ImageBitmap.imageResource(R.drawable.splash_art)
    val hammer = ImageBitmap.imageResource(R.drawable.splash_hammer)
    val angle = remember { Animatable(0f) }
    val squash = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        coroutineScope {
            launch {
                angle.animateTo(APP_ICON_WINDUP, tween(110, easing = LinearOutSlowInEasing))          // wind up
                angle.animateTo(APP_ICON_STRIKE, tween(90, easing = CubicBezierEasing(0.6f, 0f, 1f, 0.5f)))  // drop
            }
            launch { squash.animateTo(0.95f, tween(45, easing = LinearOutSlowInEasing)) }
            delay(60)
        }
        coroutineScope {
            launch { angle.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 380f)) }   // bounce to rest
            launch { squash.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f)) }
        }
    }

    Canvas(Modifier.size(iconSize)) {
        val k = this.size.width / APP_ICON_LAYER_PX
        val dst = IntSize(this.size.width.toInt(), this.size.height.toInt())
        val circle = Path().apply { addOval(Rect(center, radius = size.width / 2f)) }
        scale(squash.value, squash.value, pivot = center) {
            clipPath(circle) { drawImage(art, dstSize = dst, filterQuality = FilterQuality.High) }
            rotate(angle.value, pivot = APP_ICON_HAMMER_PIVOT * k) {
                drawImage(hammer, dstSize = dst, filterQuality = FilterQuality.High)
            }
        }
    }
}

private operator fun Offset.times(k: Float) = Offset(x * k, y * k)

/** Confetti burst radiating out of the icon; replays every time [trigger] changes. */
@Composable
private fun ConfettiOverlay(trigger: Int, origin: Offset, color: Color, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    var particles by remember(trigger) {
        mutableStateOf(Array(320) {
            val r = Random(trigger * 977 + it)
            val a = (r.nextFloat() * 2f - 1f) * Math.PI.toFloat()     // radiate out in every direction
            val speed = 500f + r.nextFloat() * 1400f
            Particle(
                v0 = Offset(cos(a) * speed, sin(a) * speed),
                spin = (r.nextFloat() * 2f - 1f) * 720f,
                sizePx = 8f + r.nextFloat() * 10f,
                color = CONFETTI_COLORS[r.nextInt(CONFETTI_COLORS.size)],
                isCircle = r.nextInt(3) == 0,
            )
        })
    }
    val anim = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { anim.snapTo(0f); anim.animateTo(1f, tween(1700, easing = LinearOutSlowInEasing)) }
    Canvas(modifier) {
        val t = anim.value
        val start = if (origin == Offset.Zero) Offset(this.size.width / 2f, this.size.height * 0.3f) else origin
        val elapsed = t * 1.7f
        for (p in particles) {
            val g = 1400f
            val pos = start + p.v0 * elapsed + Offset(0f, 0.5f * g * elapsed * elapsed)
            if (pos.y > this.size.height + 40f) continue
            val fade = ((1f - t) / 0.35f).coerceIn(0f, 1f)
            rotate(p.spin * t, pivot = pos) {
                if (p.isCircle) drawCircle(p.color, radius = p.sizePx / 2f, center = pos, alpha = fade)
                else drawRect(p.color, topLeft = pos - Offset(p.sizePx / 2f, p.sizePx / 4f),
                    size = androidx.compose.ui.geometry.Size(p.sizePx, p.sizePx / 2f), alpha = fade)
            }
        }
    }
}

private class Particle(val v0: Offset, val spin: Float, val sizePx: Float, val color: Color, val isCircle: Boolean)
