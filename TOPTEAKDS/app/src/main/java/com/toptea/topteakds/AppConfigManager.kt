package com.toptea.topteakds

import android.content.Context
import android.content.SharedPreferences

class AppConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)

    fun getKdsUrl(): String {
        return prefs.getString("kds_url", "file:///android_asset/setup.html") ?: "file:///android_asset/setup.html"
    }

    fun saveKdsUrl(url: String) {
        prefs.edit().putString("kds_url", url).apply()
    }
}
