package com.example.musicadmin

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.musicadmin.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val client = OkHttpClient()
    private var selectedUri: Uri? = null

    private val pickSong =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                selectedUri = uri
                binding.tvFileName.text = displayName(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPickSong.setOnClickListener { pickSong.launch(arrayOf("audio/*")) }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val uri = selectedUri
        if (uri == null) {
            toast("Escolha uma música primeiro")
            return
        }
        val coverUrl = binding.etCoverUrl.text?.toString()?.trim().orEmpty()
        val name = displayName(uri)
        val title = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "mp3")
        val mime = contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "audio/mpeg"
        val path = "${UUID.randomUUID()}.$ext"

        setLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    uploadFile(uri, path, mime)
                    insertSong(title, publicUrl(path), coverUrl.ifEmpty { null })
                }
                toast("Música salva!")
                selectedUri = null
                binding.tvFileName.text = getString(R.string.no_file)
                binding.etCoverUrl.text?.clear()
            } catch (e: Exception) {
                toast("Erro: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun uploadFile(uri: Uri, path: String, mime: String) {
        val body = object : RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun writeTo(sink: BufferedSink) {
                contentResolver.openInputStream(uri)!!.source().use { sink.writeAll(it) }
            }
        }
        val request = Request.Builder()
            .url("${SupabaseConfig.URL}/storage/v1/object/${SupabaseConfig.BUCKET}/$path")
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .post(body)
            .build()
        client.newCall(request).execute().use {
            if (!it.isSuccessful) error("upload ${it.code}: ${it.body?.string()}")
        }
    }

    private fun insertSong(title: String, fileUrl: String, coverUrl: String?) {
        val json = JSONObject()
            .put("title", title)
            .put("file_url", fileUrl)
            .put("cover_url", coverUrl ?: JSONObject.NULL)
        val request = Request.Builder()
            .url("${SupabaseConfig.URL}/rest/v1/songs")
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .header("Prefer", "return=minimal")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use {
            if (!it.isSuccessful) error("banco ${it.code}: ${it.body?.string()}")
        }
    }

    private fun publicUrl(path: String) =
        "${SupabaseConfig.URL}/storage/v1/object/public/${SupabaseConfig.BUCKET}/$path"

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: "musica.mp3"

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnSave.isEnabled = !loading
        binding.btnPickSong.isEnabled = !loading
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
