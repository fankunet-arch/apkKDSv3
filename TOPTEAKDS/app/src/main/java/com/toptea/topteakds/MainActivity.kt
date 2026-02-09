package com.toptea.topteakds

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.toptea.topteakds.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity(), WebAppInterface.BridgeListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var printerConfigManager: PrinterConfigManager
    private lateinit var appConfigManager: AppConfigManager

    // Callbacks for JS
    private var scanSuccessCallback: String? = null
    private var scanErrorCallback: String? = null
    private var photoSuccessCallback: String? = null
    private var photoErrorCallback: String? = null

    // For File Chooser
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        printerConfigManager = PrinterConfigManager(this)
        appConfigManager = AppConfigManager(this)

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        // Clear Cache logic
        webView.clearCache(true)
        webView.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                if (fileChooserParams?.isCaptureEnabled == true) {
                    val intent = Intent(this@MainActivity, EvidencePhotoActivity::class.java)
                    fileChooserLauncher.launch(intent)
                } else {
                    val intent = fileChooserParams?.createIntent()
                    try {
                         fileChooserSystemLauncher.launch(intent)
                    } catch (e: Exception) {
                        this@MainActivity.filePathCallback = null
                        return false
                    }
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val errorHtml = """
                        <html><body>
                        <h1>System Unavailable</h1>
                        <p>Error Code: ${error?.errorCode}</p>
                        <button onclick="location.reload()">Reload</button>
                        </body></html>
                    """.trimIndent()
                    view?.loadData(errorHtml, "text/html", "UTF-8")
                }
            }
        }

        // Load URL
        val url = appConfigManager.getKdsUrl()
        webView.loadUrl(url)
    }

    // Launchers
    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val code = result.data?.getStringExtra("scan_result")
            evaluateJsCallback(scanSuccessCallback, code)
        } else {
            evaluateJsCallback(scanErrorCallback, "Cancelled")
        }
        scanSuccessCallback = null
        scanErrorCallback = null
    }

    private val photoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra("photo_path")
            if (path != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val bytes = File(path).readBytes()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        withContext(Dispatchers.Main) {
                            evaluateJsCallback(photoSuccessCallback, base64)
                        }
                        File(path).delete()
                    } catch (e: Exception) {
                         withContext(Dispatchers.Main) {
                             evaluateJsCallback(photoErrorCallback, "Error reading photo: ${e.message}")
                         }
                    }
                }
            } else {
                 evaluateJsCallback(photoErrorCallback, "No photo path returned")
            }
        } else {
            evaluateJsCallback(photoErrorCallback, "Cancelled")
        }
        photoSuccessCallback = null
        photoErrorCallback = null
    }

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                filePathCallback?.onReceiveValue(arrayOf(uri))
            } else {
                filePathCallback?.onReceiveValue(null)
            }
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private val fileChooserSystemLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
             val uri = result.data?.data
             if (uri != null) {
                 filePathCallback?.onReceiveValue(arrayOf(uri))
             } else {
                 val clip = result.data?.clipData
                 if (clip != null) {
                     val uris = (0 until clip.itemCount).map { clip.getItemAt(it).uri }.toTypedArray()
                     filePathCallback?.onReceiveValue(uris)
                 } else {
                     filePathCallback?.onReceiveValue(null)
                 }
             }
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // Bridge Implementation
    override fun onPrintJob(payload: String, successCallback: String, errorCallback: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = printerConfigManager.loadConfig()
                PrinterService.printJob(config, payload)
                withContext(Dispatchers.Main) {
                    evaluateJsCallback(successCallback, null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    evaluateJsCallback(errorCallback, e.message)
                }
            }
        }
    }

    override fun onStartScan(successCallback: String, errorCallback: String) {
        runOnUiThread {
            this.scanSuccessCallback = successCallback
            this.scanErrorCallback = errorCallback
            val intent = Intent(this, ScanActivity::class.java)
            scanLauncher.launch(intent)
        }
    }

    override fun onSavePrinterConfig(type: String, ip: String, port: Int, macAddress: String, successCallback: String, errorCallback: String) {
        lifecycleScope.launch(Dispatchers.IO) {
             try {
                 printerConfigManager.saveConfig(type, ip, port, macAddress)
                 withContext(Dispatchers.Main) {
                     evaluateJsCallback(successCallback, null)
                 }
             } catch (e: Exception) {
                 withContext(Dispatchers.Main) {
                     evaluateJsCallback(errorCallback, e.message)
                 }
             }
        }
    }

    override fun onTakeEvidencePhoto(successCallback: String, errorCallback: String) {
        runOnUiThread {
            this.photoSuccessCallback = successCallback
            this.photoErrorCallback = errorCallback
            val intent = Intent(this, EvidencePhotoActivity::class.java)
            photoLauncher.launch(intent)
        }
    }

    override fun onSaveKdsUrl(url: String) {
        appConfigManager.saveKdsUrl(url)
        runOnUiThread {
            binding.webView.loadUrl(url)
        }
    }

    private fun evaluateJsCallback(functionName: String?, arg: String?) {
        if (functionName.isNullOrEmpty()) return

        val safeArg = if (arg == null) "null" else JSONObject.quote(arg)
        val js = "javascript:try { window.$functionName($safeArg); } catch(e) { console.error(e); }"
        binding.webView.evaluateJavascript(js, null)
    }
}
