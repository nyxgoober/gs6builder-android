package com.utsk.gs6builder.ffmpeg

import android.content.Context
import android.system.Os
import android.system.ErrnoException
import android.util.Log
import java.io.File

object FfmpegBinaries {

    private const val TAG = "FfmpegBinaries"
    private val LIBRARY_MANIFEST: Map<String, String> = mapOf(
        "libavcodec.so.62" to "libavcodec_v62.so",
        "libavdevice.so.62" to "libavdevice_v62.so",
        "libavfilter.so.11" to "libavfilter_v11.so",
        "libavformat.so.62" to "libavformat_v62.so",
        "libavutil.so.60" to "libavutil_v60.so",
        "libbz2.so.1.0" to "libbz2_v1_0.so",
        "libcrypto.so.3" to "libcrypto_v3.so",
        "libexpat.so.1" to "libexpat_v1.so",
        "libglib-2.0.so.0" to "libglib20_v0.so",
        "liblzma.so.5" to "liblzma_v5.so",
        "libsharpyuv.so.0" to "libsharpyuv_v0.so",
        "libssl.so.3" to "libssl_v3.so",
        "libswresample.so.6" to "libswresample_v6.so",
        "libswscale.so.9" to "libswscale_v9.so",
        "libvpx.so.12" to "libvpx_v12.so",
        "libwebp.so.7" to "libwebp_v7.so",
        "libwebpmux.so.3" to "libwebpmux_v3.so",
        "libx264.so.164" to "libx264_v164.so",
        "libxml2.so.16" to "libxml2_v16.so",
        "libz.so.1" to "libz_v1.so",
        "libandroid.so" to "libandroid.so",
        "libandroid-glob.so" to "libandroid-glob.so",
        "libandroid-posix-semaphore.so" to "libandroid-posix-semaphore.so",
        "libandroid-support.so" to "libandroid-support.so",
        "libaom.so" to "libaom.so",
        "libass.so" to "libass.so",
        "libbrotlicommon.so" to "libbrotlicommon.so",
        "libbrotlidec.so" to "libbrotlidec.so",
        "libbrotlienc.so" to "libbrotlienc.so",
        "libc++_shared.so" to "libc++_shared.so",
        "libdav1d.so" to "libdav1d.so",
        "libdrm.so" to "libdrm.so",
        "libfontconfig.so" to "libfontconfig.so",
        "libfreetype.so" to "libfreetype.so",
        "libfribidi.so" to "libfribidi.so",
        "libgraphite2.so" to "libgraphite2.so",
        "libharfbuzz.so" to "libharfbuzz.so",
        "libhwy.so" to "libhwy.so",
        "libiconv.so" to "libiconv.so",
        "libjxl.so" to "libjxl.so",
        "libjxl_cms.so" to "libjxl_cms.so",
        "libjxl_threads.so" to "libjxl_threads.so",
        "libmediandk.so" to "libmediandk.so",
        "libmp3lame.so" to "libmp3lame.so",
        "libmpg123.so" to "libmpg123.so",
        "libogg.so" to "libogg.so",
        "libOpenCL.so" to "libOpenCL.so",
        "libopus.so" to "libopus.so",
        "libpcre2-8.so" to "libpcre2-8.so",
        "libpng16.so" to "libpng16.so",
        "libsoxr.so" to "libsoxr.so",
        "libtermux-platform-ns.so" to "libtermux-platform-ns.so",
        "libvidstab.so" to "libvidstab.so",
        "libvorbis.so" to "libvorbis.so",
        "libvorbisenc.so" to "libvorbisenc.so",
        "libx265.so" to "libx265.so",
        "libzimg.so" to "libzimg.so",
    )

    private const val PACKAGED_FFMPEG = "libffmpeg_bin.so"
    private const val PACKAGED_FFPROBE = "libffprobe_bin.so"

    data class Layout(val ffmpegPath: String, val ffprobePath: String, val libDir: String)

    @Volatile private var cachedLayout: Layout? = null

    fun ensureInstalled(context: Context): Layout {
        cachedLayout?.let { return it }
        synchronized(this) {
            cachedLayout?.let { return it }

            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
            val privateRoot = File(context.filesDir, "ffbin")
            val libDir = File(privateRoot, "lib").apply { mkdirs() }
            val binDir = File(privateRoot, "bin").apply { mkdirs() }

            for ((soname, packagedName) in LIBRARY_MANIFEST) {
                val target = File(nativeLibDir, packagedName)
                if (!target.exists()) {
                    Log.w(TAG, "Missing packaged lib $packagedName for soname $soname")
                    continue
                }
                relink(File(libDir, soname), target)
            }

            val ffmpegTarget = File(nativeLibDir, PACKAGED_FFMPEG)
            val ffprobeTarget = File(nativeLibDir, PACKAGED_FFPROBE)
            val ffmpegLink = File(binDir, "ffmpeg")
            val ffprobeLink = File(binDir, "ffprobe")
            relink(ffmpegLink, ffmpegTarget)
            relink(ffprobeLink, ffprobeTarget)

            val layout = Layout(
                ffmpegPath = ffmpegLink.absolutePath,
                ffprobePath = ffprobeLink.absolutePath,
                libDir = libDir.absolutePath
            )
            cachedLayout = layout
            return layout
        }
    }

    private fun relink(link: File, target: File) {
        val currentLinkDest = readSymlink(link)
        if (currentLinkDest == target.absolutePath) return
        if (link.exists() || currentLinkDest != null) link.delete()

        try {
            Os.symlink(target.absolutePath, link.absolutePath)
        } catch (e: ErrnoException) {
            // Fallback: some filesystems / OEM restrictions may reject symlink() from
            // app-private storage. A plain byte copy still works for the linker's own
            // lookups; the copy inherits the file's read/execute mode bits, and while
            // it lives in a writable dir, it is a straight extraction of an
            // already-signed APK asset rather than app-generated code, which most
            // OEM SELinux policies still permit to be mapped executable.
            Log.w(TAG, "symlink failed for ${link.name}, falling back to copy", e)
            target.copyTo(link, overwrite = true)
            link.setExecutable(true, false)
            link.setReadable(true, false)
        }
    }

    private fun readSymlink(file: File): String? = try {
        Os.readlink(file.absolutePath)
    } catch (e: Exception) { null }
}
