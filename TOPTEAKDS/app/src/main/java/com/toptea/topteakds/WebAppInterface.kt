package com.toptea.topteakds

import android.webkit.JavascriptInterface

class WebAppInterface(private val listener: BridgeListener) {

    interface BridgeListener {
        fun onPrintJob(payload: String, successCallback: String, errorCallback: String)
        fun onStartScan(successCallback: String, errorCallback: String)
        fun onSavePrinterConfig(type: String, ip: String, port: Int, macAddress: String, successCallback: String, errorCallback: String)
        fun onTakeEvidencePhoto(successCallback: String, errorCallback: String)
        fun onSaveKdsUrl(url: String)
    }

    @JavascriptInterface
    fun saveKdsUrl(url: String) {
        listener.onSaveKdsUrl(url)
    }

    @JavascriptInterface
    fun printJob(payload: String, successCallback: String, errorCallback: String) {
        listener.onPrintJob(payload, successCallback, errorCallback)
    }

    @JavascriptInterface
    fun startScan(successCallback: String, errorCallback: String) {
        listener.onStartScan(successCallback, errorCallback)
    }

    @JavascriptInterface
    fun savePrinterConfig(type: String, ip: String, port: Int, macAddress: String, successCallback: String, errorCallback: String) {
        listener.onSavePrinterConfig(type, ip, port, macAddress, successCallback, errorCallback)
    }

    @JavascriptInterface
    fun takeEvidencePhoto(successCallback: String, errorCallback: String) {
        listener.onTakeEvidencePhoto(successCallback, errorCallback)
    }
}
