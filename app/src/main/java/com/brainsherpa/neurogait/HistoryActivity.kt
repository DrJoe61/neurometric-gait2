package com.brainsherpa.neurogait

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class HistoryActivity : AppCompatActivity() {

    private lateinit var adapter: HistoryAdapter
    private val filesList = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view_history)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = HistoryAdapter(filesList, 
            onShare = { shareFile(it) }, 
            onDelete = { deleteFile(it) }
        )
        recyclerView.adapter = adapter

        loadFiles()
    }

    private fun loadFiles() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        filesList.clear()
        dir?.listFiles()?.filter { it.extension == "csv" }?.let {
            filesList.addAll(it.sortedByDescending { f -> f.lastModified() })
        }
        adapter.notifyDataSetChanged()
    }

    private fun shareFile(file: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Session CSV"))
    }

    private fun deleteFile(file: File) {
        if (file.delete()) {
            loadFiles()
        }
    }

    class HistoryAdapter(
        private val files: List<File>,
        private val onShare: (File) -> Unit,
        private val onDelete: (File) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val fileName: TextView = view.findViewById(R.id.tv_file_name)
            val btnShare: ImageButton = view.findViewById(R.id.btn_share)
            val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.fileName.text = file.name
            holder.btnShare.setOnClickListener { onShare(file) }
            holder.btnDelete.setOnClickListener { onDelete(file) }
        }

        override fun getItemCount() = files.size
    }
}
