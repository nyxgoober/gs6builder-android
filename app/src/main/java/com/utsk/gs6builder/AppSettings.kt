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

/** App preferences, persisted in SharedPreferences and observable from Compose. */
class AppSettings(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 0 = follow system, 1 = light, 2 = dark. */
    var themeMode by IntSetting(sp, "theme_mode", 0)
    var monet by BoolSetting(sp, "monet", false)
    var glassBar by BoolSetting(sp, "glass_bar", true)
    var compileIosByDefault by BoolSetting(sp, "ios_default", false)
    var compileZipByDefault by BoolSetting(sp, "zip_default", false)
}
