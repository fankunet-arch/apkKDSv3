package com.toptea.topteakds

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

data class PrinterConfig(
    val type: String = "WIFI", // "WIFI", "BLUETOOTH", "USB"
    val ip: String = "",
    val port: Int = 9100,
    val macAddress: String = ""
)

class PrinterConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveConfig(type: String, ip: String, port: Int, macAddress: String) {
        val config = PrinterConfig(type, ip, port, macAddress)
        val json = gson.toJson(config)
        prefs.edit().putString("config", json).apply()
    }

    fun loadConfig(): PrinterConfig {
        val json = prefs.getString("config", null)
        return if (json != null) {
            try {
                gson.fromJson(json, PrinterConfig::class.java)
            } catch (e: Exception) {
                PrinterConfig()
            }
        } else {
            PrinterConfig()
        }
    }
}
