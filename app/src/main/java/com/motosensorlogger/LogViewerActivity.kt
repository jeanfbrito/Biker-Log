package com.motosensorlogger

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.setPadding
import java.io.File

class LogViewerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_FILE_PATH = "file_path"
    }

    private var currentFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create layout programmatically to avoid needing XML
        val scrollView =
            ScrollView(this).apply {
                setPadding(16)
                setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
            }

        val textView =
            TextView(this).apply {
                setPadding(16)
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(android.graphics.Color.BLACK)
                setHorizontallyScrolling(true)
            }

        scrollView.addView(textView)
        setContentView(scrollView)

        // Set up action bar with back button
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Log Viewer"
        }

        // Load and display file content
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        if (filePath != null) {
            loadFileContent(filePath, textView)
        } else {
            textView.text = "Error: No file specified"
        }
    }

    private fun loadFileContent(
        filePath: String,
        textView: TextView,
    ) {
        try {
            val file = File(filePath)
            currentFile = file
            supportActionBar?.subtitle = file.name

            // Read file content
            val content = file.readText()

            // Display with some basic stats
            val lines = content.lines()
            val dataLines =
                lines.filter {
                    it.isNotEmpty() && !it.startsWith("#") && it.contains(",")
                }

            val stats =
                buildString {
                    appendLine("=== FILE STATS ===")
                    appendLine("File: ${file.name}")
                    appendLine("Size: ${formatFileSize(file.length())}")
                    appendLine("Total lines: ${lines.size}")
                    appendLine("Data rows: ${dataLines.size}")
                    appendLine("=".repeat(50))
                    appendLine()
                }

            textView.text = stats + content
        } catch (e: Exception) {
            textView.text = "Error reading file: ${e.message}"
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Share").setIcon(android.R.drawable.ic_menu_share)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, 2, 0, "Analyze").setIcon(android.R.drawable.ic_menu_info_details)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> {
                shareFile()
                true
            }
            2 -> {
                analyzeFile()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareFile() {
        currentFile?.let { file ->
            try {
                val uri =
                    FileProvider.getUriForFile(
                        this,
                        "$packageName.fileprovider",
                        file,
                    )

                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
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
    }

    private fun analyzeFile() {
        currentFile?.let { file ->
            val intent = Intent(this, RideAnalysisActivity::class.java).apply {
                putExtra(RideAnalysisActivity.EXTRA_FILE_PATH, file.absolutePath)
            }
            startActivity(intent)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
