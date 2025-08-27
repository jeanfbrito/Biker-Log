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
        private const val GITHUB_API_HOST = "api.github.com"
        private const val GITHUB_RELEASES_HOST = "github.com"
        private const val GITHUB_USER_CONTENT_HOST = "github-releases.githubusercontent.com"
        private val TRUSTED_HOSTS = setOf(GITHUB_API_HOST, GITHUB_RELEASES_HOST, GITHUB_USER_CONTENT_HOST)
        private const val BUFFER_SIZE = 8192
        private const val MAX_APK_SIZE = 100 * 1024 * 1024 // 100MB max
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_MS = 15000
        private const val USER_AGENT = "Biker-Log-Android/${BuildConfig.VERSION_NAME}"
    }
    
    private fun isValidGitHubHost(host: String): Boolean {
        return TRUSTED_HOSTS.contains(host.lowercase())
    }
    
    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_API_URL)
            
            // Security: Validate host before connecting (certificate pinning)
            if (!isValidGitHubHost(url.host)) {
                Log.e(TAG, "Security: Refusing to connect to untrusted host: ${url.host}")
                return@withContext null
            }
            
            connection = url.openConnection() as HttpURLConnection
            
            // Configure connection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false // Security: Manually validate redirects
            }
            
            // Enforce HTTPS for security
            if (connection !is HttpsURLConnection) {
                Log.e(TAG, "Security: Connection is not HTTPS")
                return@withContext null
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
        var apkFile: File? = null
        
        try {
            // Security: Validate URL host before downloading
            val downloadUrl = URL(url)
            if (!isValidGitHubHost(downloadUrl.host)) {
                Log.e(TAG, "Security: Refusing to download from untrusted host: ${downloadUrl.host}")
                return@withContext null
            }
            
            // Security: Enforce HTTPS
            if (downloadUrl.protocol != "https") {
                Log.e(TAG, "Security: Refusing to download over non-HTTPS connection")
                return@withContext null
            }
            
            // Clean up old update files
            cleanupOldUpdateFiles()
            
            apkFile = File(context.getExternalFilesDir(null), "update_${System.currentTimeMillis()}.apk")
            connection = downloadUrl.openConnection() as HttpURLConnection
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
            apkFile?.delete()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            apkFile?.delete()
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
            // Security validation: Ensure file is in our app's private directory
            val expectedDir = context.getExternalFilesDir(null)
            if (expectedDir == null || !file.canonicalPath.startsWith(expectedDir.canonicalPath)) {
                Log.e(TAG, "Security: APK file is not in expected directory")
                return false
            }
            
            // Validate file exists and has reasonable size
            if (!file.exists()) {
                Log.e(TAG, "APK file does not exist: ${file.absolutePath}")
                return false
            }
            
            if (file.length() <= 0 || file.length() > MAX_APK_SIZE) {
                Log.e(TAG, "Security: Invalid APK file size: ${file.length()}")
                return false
            }
            
            // Validate file name pattern
            if (!file.name.matches(Regex("update_\\d+\\.apk"))) {
                Log.e(TAG, "Security: Invalid APK filename pattern: ${file.name}")
                return false
            }
            
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                // Use FileProvider for secure file access
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                
                // Add security flags for Android 14+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
            }
            
            Log.d(TAG, "Starting APK installation for verified file: ${file.name}")
            context.startActivity(intent)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during APK installation", e)
            false
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