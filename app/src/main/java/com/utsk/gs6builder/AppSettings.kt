package com.utsk.gs6builder

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlin.reflect.KProperty

private class BoolSetting(private val sp: SharedPreferences, private val key: String, default: Boolean) {
    private val state = mutableStateOf(sp.getBoolean(key, default))
    operator fun getValue(thisRef: Any?, p: KProperty<*>): Boolean = state.value
    operator fun setValue(thisRef: Any?, p: KProperty<*>, v: Boolean) {
        state.value = v
        sp.edit().putBoolean(key, v).apply()
    }
}

private class IntSetting(private val sp: SharedPreferences, private val key: String, default: Int) {
    private val state = mutableIntStateOf(sp.getInt(key, default))
    operator fun getValue(thisRef: Any?, p: KProperty<*>): Int = state.intValue
    operator fun setValue(thisRef: Any?, p: KProperty<*>, v: Int) {
        state.intValue = v
        sp.edit().putInt(key, v).apply()
    }
}

private class StringSetting(private val sp: SharedPreferences, private val key: String, default: String) {
    private val state = mutableStateOf(sp.getString(key, default) ?: default)
    operator fun getValue(thisRef: Any?, p: KProperty<*>): String = state.value
    operator fun setValue(thisRef: Any?, p: KProperty<*>, v: String) {
        state.value = v
        sp.edit().putString(key, v).apply()
    }
}

/** App preferences, persisted in SharedPreferences and observable from Compose. */
class AppSettings(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 0 = follow system, 1 = light, 2 = dark. */
    var themeMode by IntSetting(sp, "theme_mode", 0)
    var monet by BoolSetting(sp, "monet", false)
    /** 0 = Intern (default), 1 = Inter, 2 = System. */
    var fontChoice by IntSetting(sp, "font_choice", 0)
    var glassBar by BoolSetting(sp, "glass_bar", true)

    /** Pure black surfaces in dark theme. */
    var amoled by BoolSetting(sp, "amoled", false)
    /** 0 = default scheme, otherwise the ARGB seed the Material scheme is generated from. */
    var seedColor by IntSetting(sp, "seed_color", 0)
    /** Vibrate on taps, switches and tab changes. */
    var haptics by BoolSetting(sp, "haptics", true)
    var autoCopyId by BoolSetting(sp, "auto_copy_id", true)

    /** SAF tree URI of the folder compiled charts/zips are saved to; empty = Downloads/GS6Charts. */
    var outputDir by StringSetting(sp, "output_dir", "")

    /** Unlocked by holding the "Check for updates" row for ten seconds; shows the Developer Mode section in Settings. */
    var devMode by BoolSetting(sp, "dev_mode", false)
    var updateCheck by BoolSetting(sp, "update_check", true)
    /** 0 = Release (stable builds only), 1 = Preview (also alpha/beta/pre/rc builds). */
    var updateChannel by IntSetting(sp, "update_channel", 0)
}
