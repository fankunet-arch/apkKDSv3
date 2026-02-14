package com.toptea.topteakds

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.toptea.topteakds.databinding.ActivityPhotoBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EvidencePhotoActivity : AppCompatActivity(), LocationListener {

    private lateinit var binding: ActivityPhotoBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var locationManager: LocationManager? = null
    private var currentLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        binding.btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        if (allPermissionsGranted()) {
            startCamera()
            startLocationUpdates()
        } else {
            requestPermissions.launch(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCamera()
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Permissions required.", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun startLocationUpdates() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
                try {
                    locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, this)
                } catch (e: Exception) {
                    // Network provider might fail if no sim
                }

                val lastKnown = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                if (lastKnown != null) {
                    currentLocation = lastKnown
                    updateGpsStatus("GPS: Last Known", android.graphics.Color.YELLOW)
                }
            }
        } catch (e: Exception) {
            Log.e("GPS", "Error starting location updates", e)
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = location
        updateGpsStatus("GPS: Locked", android.graphics.Color.GREEN)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun updateGpsStatus(msg: String, color: Int) {
        runOnUiThread {
            binding.tvGpsStatus.text = msg
            binding.tvGpsStatus.setTextColor(color)
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        if (currentLocation == null) {
             Toast.makeText(this, "Waiting for GPS...", Toast.LENGTH_SHORT).show()
             return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnCapture.visibility = View.INVISIBLE
        binding.btnCancel.isEnabled = false

        val photoFile = File(externalCacheDir, SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + ".jpg")

        val metadata = ImageCapture.Metadata().apply {
            location = currentLocation
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .setMetadata(metadata)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("Photo", "Photo capture failed: ${exc.message}", exc)
                    binding.progressBar.visibility = View.GONE
                    binding.btnCapture.visibility = View.VISIBLE
                    binding.btnCancel.isEnabled = true
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        val exif = ExifInterface(photoFile.absolutePath)
                        if (currentLocation != null) {
                            exif.setGpsInfo(currentLocation!!)
                            exif.saveAttributes()
                        }
                    } catch (e: Exception) {
                        Log.e("Exif", "Failed to write manual EXIF", e)
                    }

                    val savedUri = FileProvider.getUriForFile(
                        this@EvidencePhotoActivity,
                        "${applicationContext.packageName}.fileprovider",
                        photoFile
                    )

                    val resultIntent = Intent().apply {
                        data = savedUri
                        putExtra("photo_path", photoFile.absolutePath)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch(exc: Exception) {
                 Log.e("Photo", "Use case binding failed", exc)
                 Toast.makeText(this, "Camera init failed", Toast.LENGTH_SHORT).show()
                 setResult(RESULT_CANCELED)
                 finish()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(baseContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(baseContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        locationManager?.removeUpdates(this)
    }
}
