package com.utsk.gs6builder

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Compares the installed version against the newest GitHub release.
 *
 * Reads the public `releases.atom` feed instead of the REST API (60 unauthenticated requests/hour
 * per IP). The feed has no rate limit and, unlike `/releases/latest`, includes pre-releases.
 */
object UpdateChecker {
    private const val REPO = "nyxgoober/gs6builder-android"
    private const val FEED_URL = "https://github.com/$REPO/releases.atom"
    private const val TAG_MARKER = "/releases/tag/"

    fun releaseUrl(tag: String): String = "https://github.com/$REPO$TAG_MARKER$tag"

    /**
     * Highest release tag in the feed (e.g. "v2.0.0" or "v2.1.0-beta.1"), or a failure.
     * Unstable tags are only considered when [includeUnstable] is set (the Preview channel).
     */
    suspend fun fetchLatestTag(includeUnstable: Boolean): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(FEED_URL).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "GS6Builder-Android")
                if (c.responseCode != 200) error("GitHub returned HTTP ${c.responseCode}")
                val body = c.inputStream.bufferedReader().use { it.readText() }
                Regex("href=\"[^\"]*" + Regex.escape(TAG_MARKER) + "([^\"?#]+)\"")
                    .findAll(body)
                    .map { URLDecoder.decode(it.groupValues[1], "UTF-8") }
                    .filter { parse(it).nums.isNotEmpty() && (includeUnstable || !isUnstable(it)) }
                    .distinct()
                    .maxWithOrNull { a, b -> compare(a, b) }
                    ?: error(if (includeUnstable) "No releases published yet" else "No stable releases published yet")
            } finally {
                c.disconnect()
            }
        }
    }

    // ---- versions ------------------------------------------------------------------------

    /** Suffix labels that mark a build as unstable, in ascending order of maturity. */
    private val UNSTABLE_LABELS = listOf("alpha", "beta", "pre", "rc")  // "preview" matches "pre"
    private const val STABLE_RANK = 100

    private class Ver(val nums: List<Int>, val rank: Int, val preNum: Int)

    private fun suffix(version: String): String =
        version.trim().substringBefore('+').substringAfter('-', "").lowercase()

    private fun rankOf(suffix: String): Int {
        val i = UNSTABLE_LABELS.indexOfFirst { suffix.startsWith(it) }
        return if (i >= 0) i else STABLE_RANK
    }

    /** "v2.0.0-beta.2+3" -> nums [2,0,0], rank of "beta", preNum 2. */
    private fun parse(version: String): Ver {
        val core = version.trim().removePrefix("v").removePrefix("V").substringBefore('+').substringBefore('-')
        val nums = Regex("\\d+").findAll(core).mapNotNull { it.value.toIntOrNull() }.toList()
        val suf = suffix(version)
        val preNum = Regex("\\d+").find(suf)?.value?.toIntOrNull() ?: 0
        return Ver(nums, rankOf(suf), preNum)
    }

    private fun compare(a: String, b: String): Int {
        val x = parse(a)
        val y = parse(b)
        for (i in 0 until maxOf(x.nums.size, y.nums.size)) {
            val d = x.nums.getOrElse(i) { 0 }.compareTo(y.nums.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        // Same numbers: 2.1.0 > 2.1.0-rc.1 > 2.1.0-beta.2 > 2.1.0-beta.1 > 2.1.0-alpha
        val r = x.rank.compareTo(y.rank)
        return if (r != 0) r else x.preNum.compareTo(y.preNum)
    }

    /** True only when [latest] is strictly newer than [current]; unparsable input is never "newer". */
    fun isNewer(latest: String, current: String): Boolean {
        if (parse(latest).nums.isEmpty() || parse(current).nums.isEmpty()) return false
        return compare(latest, current) > 0
    }

    /** -alpha*, -beta*, -pre*, -preview* and -rc* tags are unstable builds. */
    fun isUnstable(tag: String): Boolean = rankOf(suffix(tag)) != STABLE_RANK

    // ---- android glue --------------------------------------------------------------------

    fun currentVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "unknown"

    /** Opens the release page in the user's browser. */
    fun openRelease(ctx: Context, tag: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl(tag))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
