package com.motosensorlogger

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.motosensorlogger.adapters.LogFileAdapter
import com.motosensorlogger.databinding.ActivityMainBinding
import com.motosensorlogger.services.SensorLoggerService
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var sensorService: SensorLoggerService? = null
    private var isServiceBound = false
    private lateinit var logFileAdapter: LogFileAdapter
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.HIGH_SAMPLING_RATE_SENSORS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.HIGH_SAMPLING_RATE_SENSORS
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SensorLoggerService.LocalBinder
            sensorService = binder.getService()
            isServiceBound = true
            updateUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            sensorService = null
            isServiceBound = false
            updateUI()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissions()
        setupRecyclerView()
    }
    
    private fun setupUI() {
        // Start/Stop button
        binding.btnStartStop.setOnClickListener {
            if (sensorService?.isCurrentlyLogging() == true) {
                stopLogging()
            } else {
                startLogging()
            }
        }
        
        // Pause/Resume button
        binding.btnPauseResume.setOnClickListener {
            if (sensorService?.isCurrentlyPaused() == true) {
                resumeLogging()
            } else {
                pauseLogging()
            }
        }
        
        // Refresh logs button
        binding.btnRefreshLogs.setOnClickListener {
            refreshLogsList()
        }
        
        updateUI()
    }
    
    private fun setupRecyclerView() {
        logFileAdapter = LogFileAdapter(
            onItemClick = { file -> viewLogFile(file) },
            onDeleteClick = { file -> confirmDeleteFile(file) }
        )
        
        binding.recyclerViewLogs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logFileAdapter
        }
        
        refreshLogsList()
    }
    
    private fun checkPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
        
        // Check for background location permission separately (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Background Location Required")
                    .setMessage("This app needs background location access to log GPS data while riding. Please grant 'Allow all the time' permission.")
                    .setPositiveButton("Grant") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                            PERMISSION_REQUEST_CODE + 1
                        )
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
    
    private fun startLogging() {
        if (!checkAllPermissions()) {
            Toast.makeText(this, "Please grant all required permissions", Toast.LENGTH_LONG).show()
            checkPermissions()
            return
        }
        
        val serviceIntent = Intent(this, SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_START_LOGGING
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        bindToService()
        updateUI()
        
        Toast.makeText(this, "Logging started", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopLogging() {
        val serviceIntent = Intent(this, SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_STOP_LOGGING
        }
        startService(serviceIntent)
        
        unbindFromService()
        updateUI()
        refreshLogsList()
        
        Toast.makeText(this, "Logging stopped", Toast.LENGTH_SHORT).show()
    }
    
    private fun pauseLogging() {
        val serviceIntent = Intent(this, SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_PAUSE_LOGGING
        }
        startService(serviceIntent)
        updateUI()
        
        Toast.makeText(this, "Logging paused", Toast.LENGTH_SHORT).show()
    }
    
    private fun resumeLogging() {
        val serviceIntent = Intent(this, SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_RESUME_LOGGING
        }
        startService(serviceIntent)
        updateUI()
        
        Toast.makeText(this, "Logging resumed", Toast.LENGTH_SHORT).show()
    }
    
    private fun bindToService() {
        if (!isServiceBound) {
            val intent = Intent(this, SensorLoggerService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    private fun unbindFromService() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
            sensorService = null
        }
    }
    
    private fun updateUI() {
        runOnUiThread {
            val isLogging = sensorService?.isCurrentlyLogging() == true
            val isPaused = sensorService?.isCurrentlyPaused() == true
            
            binding.btnStartStop.text = if (isLogging) "Stop Recording" else "Start Recording"
            binding.btnStartStop.setBackgroundColor(
                ContextCompat.getColor(this, if (isLogging) android.R.color.holo_red_dark else android.R.color.holo_green_dark)
            )
            
            binding.btnPauseResume.isEnabled = isLogging
            binding.btnPauseResume.text = if (isPaused) "Resume" else "Pause"
            
            binding.tvStatus.text = when {
                isLogging && isPaused -> "Status: Paused"
                isLogging -> "Status: Recording"
                else -> "Status: Idle"
            }
        }
    }
    
    private fun refreshLogsList() {
        val logFiles = getLogFiles()
        logFileAdapter.updateFiles(logFiles)
        
        binding.tvLogsCount.text = "Log Files: ${logFiles.size}"
    }
    
    private fun getLogFiles(): List<File> {
        val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val logDir = File(documentsDir, "MotoSensorLogs")
        
        return logDir.listFiles { file -> file.extension == "csv" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
    
    private fun viewLogFile(file: File) {
        val intent = Intent(this, LogViewerActivity::class.java).apply {
            putExtra(LogViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
        }
        startActivity(intent)
    }
    
    private fun shareLogFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Moto Sensor Log: ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Share log file"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun confirmDeleteFile(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Log File")
            .setMessage("Are you sure you want to delete ${file.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteLogFile(file)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteLogFile(file: File) {
        if (file.delete()) {
            Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show()
            refreshLogsList()
        } else {
            Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        bindToService()
    }
    
    override fun onStop() {
        super.onStop()
        unbindFromService()
    }
}