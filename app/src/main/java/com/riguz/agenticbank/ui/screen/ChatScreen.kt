package com.riguz.agenticbank.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val hasImage: Boolean = false,
    val imageUri: Uri? = null,
    val hasAudio: Boolean = false,
    val audioBytes: ByteArray? = null,
    val speedInfo: String? = null,
)

@Composable
fun ChatScreen(
    engine: Engine,
    backendName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var attachedAudioBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isVoiceMode by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    val conversation = remember {
        engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    "You are a professional banking assistant. Answer concisely and helpfully."
                ),
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose { conversation.close() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) attachedImageUri = uri
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording(context) { bytes ->
            attachedAudioBytes = bytes
            isRecording = false
            recordingSeconds = 0
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        val hasAttach = attachedImageUri != null || attachedAudioBytes != null
        if (text.isEmpty() && !hasAttach) return
        if (isLoading) return

        val msgText = text.ifEmpty { "" }
        messages.add(ChatMessage(
            text = msgText,
            isUser = true,
            hasImage = attachedImageUri != null,
            imageUri = attachedImageUri,
            hasAudio = attachedAudioBytes != null,
            audioBytes = attachedAudioBytes,
        ))
        val savedImageUri = attachedImageUri
        val savedAudioBytes = attachedAudioBytes
        inputText = ""
        attachedImageUri = null
        attachedAudioBytes = null
        isLoading = true

        val pendingIndex = messages.size
        messages.add(ChatMessage("", isUser = false))

        generationJob = scope.launch {
            val startTime = System.currentTimeMillis()
            var tokenCount = 0
            try {
                val hasAttach = savedImageUri != null || savedAudioBytes != null
                if (hasAttach) {
                    val contents = buildContents(context, text, savedImageUri, savedAudioBytes)
                    conversation.sendMessageAsync(contents).collect { chunk ->
                        tokenCount++
                        val existing = messages[pendingIndex]
                        messages[pendingIndex] = existing.copy(text = existing.text + chunk.toString())
                    }
                } else {
                    conversation.sendMessageAsync(text).collect { chunk ->
                        tokenCount++
                        val existing = messages[pendingIndex]
                        messages[pendingIndex] = existing.copy(text = existing.text + chunk.toString())
                    }
                }
                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                val tps = tokenCount * 1000f / elapsed
                messages[pendingIndex] = messages[pendingIndex].copy(
                    speedInfo = "%.1f tokens/s  %d tokens  %.1fs".format(tps, tokenCount, elapsed / 1000.0)
                )
            } catch (e: Exception) {
                messages[pendingIndex] = ChatMessage("Error: ${e.message}", isUser = false)
            } finally {
                isLoading = false
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        isLoading = false
    }

    fun toggleRecording() {
        if (isRecording) {
            isRecording = false
            recordingSeconds = 0
            stopRecording()
            scope.launch {
                delay(100)
                sendMessage()
            }
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            isRecording = true
            recordingSeconds = 0
            startRecording(context) { bytes ->
                attachedAudioBytes = bytes
                isRecording = false
                recordingSeconds = 0
            }
            scope.launch {
                while (isRecording) {
                    delay(1000)
                    recordingSeconds++
                }
            }
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Text("\u2190", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gemma 4 E2B",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (backendName.contains("GPU"))
                        Color(0xFF34A853).copy(alpha = 0.15f) else Color(0xFFFBBC04).copy(alpha = 0.15f),
                ) {
                    Text(
                        text = backendName.take(3),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (backendName.contains("GPU"))
                            Color(0xFF34A853) else Color(0xFFB8860B),
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("\uD83C\uDFE6", fontSize = 28.sp)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Banking AI Assistant",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Powered by Gemma 4 E2B on $backendName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                items(messages) { message ->
                    MessageItem(
                        message = message,
                        isStreaming = isLoading && !message.isUser && message == messages.lastOrNull() && message.text.isEmpty(),
                        backendName = backendName,
                    )
                }

                if (isLoading && messages.lastOrNull()?.text?.isEmpty() == true && !messages.lastOrNull()!!.isUser) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                text = "Model on $backendName",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "\u25CF  \u25CF  \u25CF",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    letterSpacing = 4.sp,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (!isLoading) {
                if (attachedImageUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("\uD83D\uDDBC\uFE0F Photo attached", style = MaterialTheme.typography.labelSmall)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "\u2715",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable { attachedImageUri = null },
                                )
                            }
                        }
                    }
                }

                if (isVoiceMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { isVoiceMode = false },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Keyboard",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed = interactionSource.collectIsPressedAsState()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isPressed.value) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(interactionSource = interactionSource, indication = null) {},
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isRecording) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEA4335)),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Recording ${recordingSeconds}s  Release to send",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFEA4335),
                                    )
                                }
                            } else {
                                Text(
                                    "Hold to Talk",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        LaunchedEffect(isPressed.value) {
                            if (isPressed.value && !isRecording) {
                                toggleRecording()
                            } else if (!isPressed.value && isRecording) {
                                toggleRecording()
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        IconButton(
                            onClick = { isVoiceMode = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardVoice,
                                contentDescription = "Voice",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Type a message...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            minLines = 1,
                            maxLines = 5,
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Attach",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Photo") },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        imagePicker.launch("image/*")
                                    },
                                )
                            }
                        }

                        val canSend = inputText.isNotBlank() || attachedImageUri != null || attachedAudioBytes != null
                        IconButton(
                            onClick = { if (canSend) sendMessage() },
                            modifier = Modifier.size(40.dp),
                            enabled = canSend,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            } else {
                Surface(
                    onClick = { stopGeneration() },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("\u25A0", fontSize = 14.sp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop generating", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage, isStreaming: Boolean, backendName: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (message.isUser) "YOU" else "Model on $backendName",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Row(
                horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
                    modifier = Modifier.widthIn(max = 300.dp),
                ) {
                    if (message.hasAudio && message.audioBytes != null) {
                        VoiceMessagePlayer(
                            audioBytes = message.audioBytes,
                            isUser = message.isUser,
                        )
                    }

                    if (message.hasImage && message.imageUri != null) {
                        val context = LocalContext.current
                        val bitmap = remember(message.imageUri) {
                            try {
                                val input = context.contentResolver.openInputStream(message.imageUri)
                                val options = BitmapFactory.Options().apply {
                                    inSampleSize = 4
                                }
                                input?.use { BitmapFactory.decodeStream(it, null, options) }
                            } catch (_: Exception) { null }
                        }
                        if (bitmap != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (message.isUser) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Image",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .widthIn(max = 260.dp)
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            }
                        }
                    }

                    val showTextBubble = message.text.isNotEmpty() && !(message.hasAudio && message.text.isEmpty()) && !(message.hasImage && message.text == "Describe this image")
                    if (showTextBubble || isStreaming) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (message.isUser) 16.dp else 4.dp,
                                bottomEnd = if (message.isUser) 4.dp else 16.dp,
                            ),
                            color = if (message.isUser) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            val displayText = when {
                                isStreaming -> "\u200B"
                                message.text.isEmpty() -> "\u200B"
                                else -> message.text
                            }
                            Text(
                                text = displayText,
                                color = if (message.isUser) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }

                    if (message.speedInfo != null) {
                        Text(
                            text = message.speedInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceMessagePlayer(audioBytes: ByteArray, isUser: Boolean) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var durationMs by remember { mutableIntStateOf(0) }
    val mediaPlayer = remember { mutableStateOf<MediaPlayer?>(null) }

    val waveformData = remember(audioBytes) {
        val sampleCount = 28
        val step = audioBytes.size / sampleCount
        (0 until sampleCount).map { i ->
            val idx = (i * step).coerceIn(0, audioBytes.size - 1)
            val v = (audioBytes[idx].toInt() and 0xFF) - 128
            (abs(v) / 128f).coerceIn(0.15f, 1f)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveAnim",
    )

    DisposableEffect(audioBytes) {
        onDispose {
            try {
                mediaPlayer.value?.release()
            } catch (_: Exception) {}
            mediaPlayer.value = null
        }
    }

    fun startPlayback() {
        try {
            mediaPlayer.value?.release()
            val tmpFile = File(context.cacheDir, "playback_${System.nanoTime()}.m4a")
            tmpFile.writeBytes(audioBytes)
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(tmpFile.absolutePath)
                prepare()
                durationMs = duration
                start()
            }
            mediaPlayer.value = mp
            isPlaying = true
            progress = 0f

            val poll = object : java.util.TimerTask() {
                override fun run() {
                    try {
                        if (mp.isPlaying) {
                            progress = mp.currentPosition.toFloat() / mp.duration.coerceAtLeast(1)
                        }
                    } catch (_: Exception) {}
                }
            }
            val timer = java.util.Timer()
            timer.scheduleAtFixedRate(poll, 50, 50)

            mp.setOnCompletionListener {
                isPlaying = false
                progress = 0f
                timer.cancel()
                try { tmpFile.delete() } catch (_: Exception) {}
                try { mp.release() } catch (_: Exception) {}
                mediaPlayer.value = null
            }
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Playback failed", e)
            isPlaying = false
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable {
            if (isPlaying) {
                try {
                    mediaPlayer.value?.pause()
                    isPlaying = false
                } catch (_: Exception) {}
            } else {
                startPlayback()
            }
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isPlaying) "\u23F8" else "\u25B6",
                fontSize = 16.sp,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(8.dp))
            val activeBarColor = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary
            val inactiveBarColor = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
            ) {
                val barCount = waveformData.size
                val barWidth = size.width / (barCount * 1.8f)
                val gap = barWidth * 0.8f
                val centerY = size.height / 2f
                val progressIdx = (progress * barCount).toInt()

                for (i in 0 until barCount) {
                    val x = i * (barWidth + gap)
                    val amplitude = if (isPlaying && i <= progressIdx) {
                        waveformData[i] * (0.6f + 0.4f * animatedProgress)
                    } else {
                        waveformData[i] * 0.5f
                    }
                    val barHeight = amplitude * size.height
                    val barColor = if (isPlaying && i <= progressIdx) activeBarColor
                    else inactiveBarColor
                    drawLine(
                        color = barColor,
                        start = Offset(x + barWidth / 2f, centerY - barHeight / 2f),
                        end = Offset(x + barWidth / 2f, centerY + barHeight / 2f),
                        strokeWidth = barWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
            if (durationMs > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "%d:%02d".format(durationMs / 1000 / 60, durationMs / 1000 % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun buildContents(
    context: android.content.Context,
    text: String,
    imageUri: Uri?,
    audioBytes: ByteArray?,
): Contents {
    val contents = mutableListOf<Content>()

    if (imageUri != null) {
        val imageBytes = context.contentResolver.openInputStream(imageUri)?.readBytes()
        if (imageBytes != null) {
            contents.add(Content.ImageBytes(imageBytes))
        }
    }

    if (audioBytes != null) {
        contents.add(Content.AudioBytes(audioBytes))
    }

    val effectiveText = text.ifBlank {
        when {
            imageUri != null -> "Describe this image"
            audioBytes != null -> "Respond to this voice message"
            else -> ""
        }
    }
    if (effectiveText.isNotBlank()) {
        contents.add(Content.Text(effectiveText))
    }

    return Contents.of(contents.toList())
}

private var audioRecord: AudioRecord? = null
private var isRecordingActive = false
private var pendingAudioCallback: ((ByteArray) -> Unit)? = null

private const val SAMPLE_RATE = 16000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

private fun startRecording(context: android.content.Context, onDone: (ByteArray) -> Unit) {
    val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    val record = AudioRecord(
        android.media.MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        bufferSize * 2,
    )
    if (record.state != AudioRecord.STATE_INITIALIZED) {
        record.release()
        return
    }
    audioRecord = record
    isRecordingActive = true
    pendingAudioCallback = onDone

    Thread {
        val pcmData = mutableListOf<Byte>()
        val buffer = ByteArray(bufferSize)
        record.startRecording()
        while (isRecordingActive) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
                pcmData.addAll(buffer.take(read))
            }
        }
        record.stop()
        record.release()
        audioRecord = null

        val pcmBytes = pcmData.toByteArray()
        val wavBytes = encodeToWav(pcmBytes, SAMPLE_RATE, 1, 16)
        pendingAudioCallback?.invoke(wavBytes)
        pendingAudioCallback = null
    }.start()
}

private fun stopRecording() {
    isRecordingActive = false
}

private fun encodeToWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val dataSize = pcmData.size
    val totalSize = 36 + dataSize

    val header = ByteArray(44)
    // RIFF header
    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    writeInt(header, 4, totalSize)
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    // fmt subchunk
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    writeInt(header, 16, 16) // subchunk1 size
    writeShort(header, 20, 1) // PCM format
    writeShort(header, 22, channels)
    writeInt(header, 24, sampleRate)
    writeInt(header, 28, byteRate)
    writeShort(header, 32, blockAlign)
    writeShort(header, 34, bitsPerSample)
    // data subchunk
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    writeInt(header, 40, dataSize)

    return header + pcmData
}

private fun writeInt(buffer: ByteArray, offset: Int, value: Int) {
    buffer[offset] = (value and 0xFF).toByte()
    buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
    buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

private fun writeShort(buffer: ByteArray, offset: Int, value: Int) {
    buffer[offset] = (value and 0xFF).toByte()
    buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
}
