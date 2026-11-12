package com.utsk.gs6builder

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import org.json.JSONArray
import java.io.File

internal val DIFFICULTIES = listOf("easy", "normal", "hard", "expert", "master", "append")
internal val MV_TYPES = listOf("ogmv", "2dmv")
internal val VOCAL_TYPES = listOf("sekai", "original_song", "another_vocal")
internal val IMAGE = arrayOf("image/*")
internal val AUDIO = arrayOf("audio/*", "video/*", "application/ogg")
internal val VIDEO = arrayOf("video/*")
internal val ANY = arrayOf("*/*")
internal typealias PickFile = (String, Array<String>, (String) -> Unit) -> Unit

/**
 * Fires [onHold] once the pointer has been held down on this spot for [millis] without lifting or
 * dragging away. Watches in the Initial pass so it works on top of clickable children, and eats the
 * rest of the gesture after firing so the release isn't also treated as a tap.
 */
internal fun Modifier.holdToTrigger(millis: Long = 10_000, onHold: () -> Unit): Modifier =
    pointerInput(millis, onHold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            val letGo = withTimeoutOrNull(millis) {
                var holding = true
                while (holding) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    holding = change != null && change.pressed && (change.position - down.position).getDistance() <= slop
                }
                true
            }
            if (letGo == null) {
                onHold()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }

internal fun JSONArray.without(i: Int): JSONArray {
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

// ---- Open-source licenses ---------------------------------------------------------------------

internal data class LicenseEntry(val title: String, val subtitle: String, val body: String)

internal val LICENSE_ENTRIES: List<LicenseEntry> = listOf(
    LicenseEntry("GS6 Builder", "GNU AGPL-3.0", "This program is free software: you can redistribute it and/or modify it " +
        "under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either " +
        "version 3 of the License, or (at your option) any later version.\n\n" +
        "This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied " +
        "warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more " +
        "details. The complete text ships with the repository's LICENSE file and at https://www.gnu.org/licenses/.\n\n" +
        "It is an Android port of the GridlessSekai6 custom-chart builder."),
    LicenseEntry("Apache License 2.0", "Miuix · Kyant0 backdrop & shapes · AndroidLiquidGlass (catalog) · Jetpack Compose & androidx.activity · Compose Multiplatform · kotlinx-coroutines",
        "Copyrights: Google LLC (Jetpack Compose, androidx.activity); JetBrains (Compose Multiplatform, " +
        "kotlinx-coroutines); compose-miuix-ui contributors (Miuix); Kyant0 (backdrop, shapes, AndroidLiquidGlass " +
        "catalog).\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use these files except " +
        "in compliance with the License. You may obtain a copy of the License at\n\n" +
        "http://www.apache.org/licenses/LICENSE-2.0\n\n" +
        "Unless required by applicable law or agreed to in writing, software distributed under the License is distributed " +
        "on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for " +
        "the specific language governing permissions and limitations under the License.\n\n" +
        "The full license text is included in each source file header where the code was copied into this repository and " +
        "at the URL above."),
    LicenseEntry("MIT License", "Chaquopy · UnityPy · texture2ddecoder · etcpak · astc-encoder-py · brotli",
        "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated " +
        "documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation " +
        "the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and " +
        "to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\n" +
        "The above copyright notice and this permission notice shall be included in all copies or substantial portions of " +
        "the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING " +
        "BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN " +
        "NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN " +
        "AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR " +
        "OTHER DEALINGS IN THE SOFTWARE."),
    LicenseEntry("MIT-CMU (HPND)", "Pillow",
        "By obtaining, using, and/or copying this software and/or its associated software, you agree that you have read, " +
        "understood, and will comply with the following terms and conditions:\n\n" +
        "Permission to use, copy, modify, and distribute this software and its documentation for any purpose and without " +
        "fee is hereby granted, provided that the above copyright notice appears in all copies, and that both that " +
        "copyright notice appears in supporting documentation, and that the name of CMU not be used in advertising or " +
        "publicity pertaining to distribution of the software without specific, written prior permission."),
    LicenseEntry("BSD-2-Clause (Simplified)", "lz4",
        "Redistribution and use in source and binary forms, with or without modification, are permitted provided that the " +
        "following conditions are met:\n\n1. Redistributions of source code must retain the above copyright notice, this " +
        "list of conditions and the following disclaimer.\n\n2. Redistributions in binary form must reproduce the above " +
        "copyright notice, this list of conditions and the following disclaimer.\n\nTHIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS " +
        "\"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF " +
        "MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR " +
        "CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES " +
        "(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR " +
        "BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR " +
        "TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF " +
        "THE POSSIBILITY OF SUCH DAMAGE."),
    LicenseEntry("GNU GPL-3.0", "rg-converters",
        "PyPI package built and bundled from source as required by its GPL-3.0 license. The corresponding source is " +
        "available at its PyPI page (https://pypi.org/project/rg-converters/); the Android-compatible wheel build scripts " +
        "live in this repository's GitHub workflow."),
    LicenseEntry("GNU GPL-2.0-or-later (ffmpeg components)", "ffmpeg · x264 · x265",
        "This app bundles prebuilt native libraries (libffmpeg_bin.so and friends) that link against GPL components " +
        "(libx264, libx265). The corresponding source for these libraries is obtainable from their upstream projects " +
        "(https://ffmpeg.org, https://x264.org, https://x265.org) and from the build instructions of the repository these " +
        "binaries ship with."),
    LicenseEntry("SIL Open Font License 1.1", "Inter",
        "Copyright (c) 2016-2020 The Inter Project Authors (https://github.com/rsms/inter)\n\n" +
        "This Font Software is licensed under the SIL Open Font License, Version 1.1. This license is copied below, and " +
        "is also available with a FAQ at https://scripts.sil.org/OFL\n\n" +
        "This license allows the font to be used, studied, modified and redistributed freely, subject to the conditions " +
        "that derivative fonts licensed under OFL carry the Reserved Font Name clause and are not sold by themselves. " +
        "See the full text at https://openfontlicense.org."),
    LicenseEntry("SIL Open Font License 1.1", "Intern",
        "Copyright 2016 The Inter Project Authors (https://github.com/rsms/inter)\nCopyright 2026 Luke (https://github.com/bvvst/intern)\n\n" +
        "Intern is a fully rounded, variable-weight derivative of Inter. This Font Software is licensed under the SIL " +
        "Open Font License, Version 1.1. This license is copied below, and is also available with a FAQ at " +
        "https://scripts.sil.org/OFL\n\n" +
        "This license allows the font to be used, studied, modified and redistributed freely, subject to the conditions " +
        "that derivative fonts licensed under OFL carry the Reserved Font Name clause and are not sold by themselves. " +
        "See the full text at https://openfontlicense.org."),
)
