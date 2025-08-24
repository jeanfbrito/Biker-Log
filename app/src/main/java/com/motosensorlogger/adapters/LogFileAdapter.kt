package com.motosensorlogger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.motosensorlogger.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LogFileAdapter(
    private val onItemClick: (File) -> Unit,
    private val onDeleteClick: (File) -> Unit,
) : RecyclerView.Adapter<LogFileAdapter.ViewHolder>() {
    private var files = listOf<File>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        val tvFileDate: TextView = view.findViewById(R.id.tvFileDate)
        val tvFileSize: TextView = view.findViewById(R.id.tvFileSize)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_log_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val file = files[position]

        holder.tvFileName.text = file.name
        holder.tvFileDate.text = dateFormat.format(Date(file.lastModified()))
        holder.tvFileSize.text = formatFileSize(file.length())

        holder.itemView.setOnClickListener {
            onItemClick(file)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(file)
        }
    }

    override fun getItemCount() = files.size

    fun updateFiles(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged()
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }
}
