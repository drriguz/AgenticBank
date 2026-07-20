package com.riguz.agenticbank.model

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ModelManager(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        private val SOURCE_PATHS = listOf(
            "/sdcard/Download/$MODEL_FILE_NAME",
            "/storage/emulated/0/Download/$MODEL_FILE_NAME",
        )
    }

    sealed class State {
        data object Idle : State()
        data class Loading(val progress: Float, val message: String) : State()
        data class Ready(val engine: Engine, val backendName: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var engine: Engine? = null

    fun loadModel() {
        if (_state.value is State.Loading || _state.value is State.Ready) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()

                _state.value = State.Loading(0f, "Looking for model file...")

                val modelPath = prepareModelFile(context)

                _state.value = State.Loading(0.5f, "Loading Gemma 4 E2B model...")

                val (eng, backendName) = tryInitializeEngine(modelPath, context)

                engine = eng
                _state.value = State.Ready(eng, backendName)

            } catch (e: Exception) {
                _state.value = State.Error(
                    "Failed to load model: ${e.message}"
                )
            }
        }
    }

    private fun prepareModelFile(context: Application): String {
        val internalPath = File(context.filesDir, MODEL_FILE_NAME)

        if (internalPath.exists()) {
            _state.value = State.Loading(0.3f, "Found cached model file")
            return internalPath.path
        }

        val sourcePath = findModelFile(context)

        if (sourcePath == null) {
            val hints = mutableListOf("Place the file in one of these locations:")
            hints.add("  1. /sdcard/Download/")
            context.getExternalFilesDir(null)?.let {
                hints.add("  2. ${it.path}")
            }
            throw IllegalStateException(
                "Model file not found: $MODEL_FILE_NAME\n${hints.joinToString("\n")}"
            )
        }

        _state.value = State.Loading(0.05f, "Copying model file...")

        val sourceFile = File(sourcePath)
        val totalSize = sourceFile.length()

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(internalPath).use { output ->
                val buffer = ByteArray(65536)
                var copiedSize = 0L
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    copiedSize += bytesRead
                    val progress = if (totalSize > 0)
                        0.05f + (0.40f * (copiedSize.toFloat() / totalSize.toFloat()))
                    else
                        0.05f + (0.40f * (copiedSize.toFloat() / 5_000_000_000f).coerceAtMost(1f))
                    _state.value = State.Loading(
                        progress,
                        "Copying model file... ${copiedSize / (1024 * 1024)} MB"
                    )
                }
            }
        }

        return internalPath.path
    }

    private fun findModelFile(context: Application): String? {
        val searchPaths = mutableListOf<String>()

        context.getExternalFilesDir(null)?.let {
            searchPaths.add("${it.path}/$MODEL_FILE_NAME")
        }
        searchPaths.addAll(SOURCE_PATHS)

        for (path in searchPaths) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return file.path
            }
        }

        val uri = findModelInDownloads(context.contentResolver)
        if (uri != null) {
            val tempFile = File(context.cacheDir, "$MODEL_FILE_NAME.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            if (tempFile.exists()) {
                return tempFile.path
            }
        }

        return null
    }

    private fun findModelInDownloads(resolver: ContentResolver): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val args = arrayOf(MODEL_FILE_NAME)

            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    return Uri.withAppendedPath(collection, id.toString())
                }
            }
        }
        return null
    }

    fun loadModelFromUri(uri: Uri) {
        if (_state.value is State.Loading) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val internalPath = File(context.filesDir, MODEL_FILE_NAME)

                _state.value = State.Loading(0.05f, "Copying model file...")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(internalPath).use { output ->
                        val buffer = ByteArray(65536)
                        var copiedSize = 0L
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            copiedSize += bytesRead
                            _state.value = State.Loading(
                                0.05f + (0.40f * (copiedSize.toFloat() / 5_000_000_000f).coerceAtMost(1f)),
                                "Copying model file... ${copiedSize / (1024 * 1024)} MB"
                            )
                        }
                    }
                } ?: throw IllegalStateException("Cannot read selected file")

                _state.value = State.Loading(0.5f, "Loading Gemma 4 E2B model...\nThis takes about 10 seconds")

                val (eng, backendName) = tryInitializeEngine(internalPath.path, context)

                engine = eng
                _state.value = State.Ready(eng, backendName)

            } catch (e: Exception) {
                _state.value = State.Error(
                    "Failed to load model: ${e.message}"
                )
            }
        }
    }

    private fun tryInitializeEngine(modelPath: String, context: Application): Pair<Engine, String> {
        return try {
            val gpuConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                cacheDir = context.cacheDir.path,
            )
            val eng = Engine(gpuConfig)
            eng.initialize()
            eng to "GPU"
        } catch (e: Exception) {
            val cpuConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path,
            )
            val eng = Engine(cpuConfig)
            eng.initialize()
            eng to "CPU (GPU init failed: ${e.message})"
        }
    }

    fun retry() {
        _state.value = State.Idle
        loadModel()
    }

    override fun onCleared() {
        super.onCleared()
        engine?.close()
        engine = null
    }
}

