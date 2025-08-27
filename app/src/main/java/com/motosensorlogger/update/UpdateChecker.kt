package com.motosensorlogger.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.motosensorlogger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val releaseNotes: String = ""
)

class UpdateChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateChecker"
        private const val GITHUB_API_URL = "https://api.github.com/repos/jeanfbrito/Biker-Log/releases/latest"
        private const val BUFFER_SIZE = 8192
        private const val MAX_APK_SIZE = 100 * 1024 * 1024 // 100MB max
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_MS = 15000
        private const val USER_AGENT = "Biker-Log-Android/${BuildConfig.VERSION_NAME}"
    }
    
    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_API_URL)
            connection = url.openConnection() as HttpURLConnection
            
            // Configure connection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }
            
            // Enable SSL certificate validation for HTTPS
            if (connection is HttpsURLConnection) {
                // Default SSL validation is already enabled, but we can add custom checks if needed
            }
            
            Log.d(TAG, "Checking for updates at: $GITHUB_API_URL")
            
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseUpdateResponse(response)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    Log.w(TAG, "No releases found")
                    null
                }
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    Log.e(TAG, "GitHub API rate limit exceeded")
                    null
                }
                else -> {
                    Log.e(TAG, "Unexpected response code: ${connection.responseCode}")
                    null
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error checking for updates", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
    
    private fun parseUpdateResponse(response: String): UpdateInfo? {
        return try {
            val json = JSONObject(response)
            val tagName = json.getString("tag_name").removePrefix("v").removeSuffix("-beta").removeSuffix("-alpha")
            val currentVersion = BuildConfig.VERSION_NAME
            
            Log.d(TAG, "Latest version: $tagName, Current version: $currentVersion")
            
            if (isNewerVersion(tagName, currentVersion)) {
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.getString("name")
                    if (assetName.endsWith(".apk")) {
                        val size = asset.getLong("size")
                        if (size > MAX_APK_SIZE) {
                            Log.w(TAG, "APK size ($size) exceeds maximum allowed size")
                            return null
                        }
                        
                        return UpdateInfo(
                            version = tagName,
                            downloadUrl = asset.getString("browser_download_url"),
                            releaseNotes = json.optString("body", "")
                        )
                    }
                }
                Log.w(TAG, "No APK found in release assets")
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing update response", e)
            null
        }
    }
    
    suspend fun downloadApk(
        url: String,
        progressCallback: suspend (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        val apkFile = File(context.getExternalFilesDir(null), "update_${System.currentTimeMillis()}.apk")
        
        try {
            // Clean up old update files
            cleanupOldUpdateFiles()
            
            connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS * 2 // Longer timeout for downloads
                instanceFollowRedirects = true
            }
            
            val totalSize = connection.contentLength.toLong()
            
            if (totalSize > MAX_APK_SIZE) {
                Log.e(TAG, "APK size ($totalSize) exceeds maximum allowed size")
                return@withContext null
            }
            
            if (totalSize <= 0) {
                Log.w(TAG, "Unknown content length for APK download")
            }
            
            Log.d(TAG, "Downloading APK: $url (${totalSize / 1024 / 1024}MB)")
            
            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastProgressUpdate = 0
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Check if coroutine is still active
                        coroutineContext.ensureActive()
                        
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        if (totalSize > 0) {
                            val progress = (totalBytesRead * 100 / totalSize).toInt()
                            // Only update progress when it changes to avoid excessive updates
                            if (progress != lastProgressUpdate) {
                                lastProgressUpdate = progress
                                withContext(Dispatchers.Main) {
                                    progressCallback(progress)
                                }
                            }
                        }
                    }
                    
                    output.flush()
                }
            }
            
            Log.d(TAG, "APK downloaded successfully: ${apkFile.absolutePath}")
            apkFile
        } catch (e: IOException) {
            Log.e(TAG, "Network error downloading APK", e)
            apkFile.delete()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            apkFile.delete()
            null
        } finally {
            connection?.disconnect()
        }
    }
    
    private fun cleanupOldUpdateFiles() {
        try {
            val dir = context.getExternalFilesDir(null) ?: return
            val oldFiles = dir.listFiles { file -> 
                file.name.startsWith("update_") && file.name.endsWith(".apk")
            }
            oldFiles?.forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "Deleted old update file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up old update files", e)
        }
    }
    
    fun installApk(file: File): Boolean {
        return try {
            if (!file.exists()) {
                Log.e(TAG, "APK file does not exist: ${file.absolutePath}")
                return false
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                // Use FileProvider for all Android versions (minSdk is 26)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            Log.d(TAG, "Starting APK installation for: ${file.absolutePath}")
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            false
        }
    }
    
    internal fun isNewerVersion(remote: String, current: String): Boolean {
        return try {
            // Clean version strings (remove any suffixes like -beta, -alpha)
            val cleanRemote = remote.split("-")[0].trim()
            val cleanCurrent = current.split("-")[0].trim()
            
            val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
            
            // Both versions must have valid parts
            if (remoteParts.isEmpty() || currentParts.isEmpty()) {
                Log.w(TAG, "Invalid version format - remote: $remote, current: $current")
                return false
            }
            
            // Compare version parts
            val maxLength = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLength) {
                val remotePart = remoteParts.getOrNull(i) ?: 0
                val currentPart = currentParts.getOrNull(i) ?: 0
                
                when {
                    remotePart > currentPart -> return true
                    remotePart < currentPart -> return false
                }
            }
            
            // Versions are equal
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: remote=$remote, current=$current", e)
            false
        }
    }
}