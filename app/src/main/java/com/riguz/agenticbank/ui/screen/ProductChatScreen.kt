package com.riguz.agenticbank.ui.screen

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

private val PRODUCT_CONTEXT = """
Product Details:
- Product ID: MALI260710BLU02BD
- Currency: USD
- Full Name: MALI - Non Principal Protected - BD - 1
- Priority: Private
- Investor Type: Qualified Investor
- Bank's Product Risk Rating: 5 (High)
- Window Period: 10 Jul 2026 - 31 Jul 2026
- Minimum Investment: USD 10,000
- Tenor: 12 Months
- Principal Protection: 100%
- Underlying Asset: US Treasury Bond [US912810UM89]

You are a banking product advisor for this structured deposit product. Answer user questions about this product concisely and accurately based on the product details and any termsheet images provided. If you don't know the answer, say so honestly.
""".trimIndent()

data class ProductChatMessage(
    val text: String,
    val isUser: Boolean,
    val imageBitmaps: List<Bitmap> = emptyList(),
    val speedInfo: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductChatScreen(
    engine: Engine,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<ProductChatMessage>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }

    val conversation = remember {
        engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(PRODUCT_CONTEXT),
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose { conversation.close() }
    }

    val lastMessageTextLen = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastMessageTextLen) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage(text: String, images: List<Bitmap> = emptyList()) {
        if (text.isBlank() && images.isEmpty()) return
        if (isLoading) return

        messages.add(ProductChatMessage(
            text = text.ifBlank { "Describe this termsheet" },
            isUser = true,
            imageBitmaps = images,
        ))
        inputText = ""
        isLoading = true

        val pendingIndex = messages.size
        messages.add(ProductChatMessage(text = "", isUser = false))

        generationJob = scope.launch {
            val startTime = System.currentTimeMillis()
            var tokenCount = 0
            try {
                val contents = mutableListOf<Content>()
                for (img in images) {
                    val stream = ByteArrayOutputStream()
                    img.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    contents.add(Content.ImageBytes(stream.toByteArray()))
                }
                val msgText = text.ifBlank { "Describe this termsheet page" }
                contents.add(Content.Text(msgText))
                val msgContents = Contents.of(contents)

                withContext(Dispatchers.IO) {
                    conversation.sendMessageAsync(msgContents)
                }.collect { chunk ->
                    tokenCount++
                    val existing = messages[pendingIndex]
                    messages[pendingIndex] = existing.copy(text = existing.text + chunk.toString())
                }

                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                val tps = tokenCount * 1000f / elapsed
                messages[pendingIndex] = messages[pendingIndex].copy(
                    speedInfo = "%.1f tokens/s  %d tokens  %.1fs".format(tps, tokenCount, elapsed / 1000.0)
                )
            } catch (e: Exception) {
                messages[pendingIndex] = ProductChatMessage("Error: ${e.message}", isUser = false)
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Product Advisor")
                        Text(
                            "MALI260710BLU02BD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "\uD83D\uDCAC",
                                    style = MaterialTheme.typography.displayMedium,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Ask about this product",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Try: \"What is the risk rating?\" or attach a termsheet page",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                items(messages) { message ->
                    ProductMessageItem(message = message)
                }
            }

            ProductChatInput(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = { sendMessage(inputText) },
                isLoading = isLoading,
                onAttachTermsheet = {
                    scope.launch {
                        val bitmaps = withContext(Dispatchers.IO) {
                            renderTermsheetPages(context)
                        }
                        if (bitmaps.isNotEmpty()) {
                            sendMessage(inputText, bitmaps)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ProductMessageItem(message: ProductChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = if (message.isUser) "YOU" else "Product Advisor",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Row(
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                if (message.imageBitmaps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (bitmap in message.imageBitmaps) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (message.isUser) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Termsheet page",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .widthIn(max = 260.dp)
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            }
                        }
                    }
                    if (message.text.isNotBlank() && message.text != "Describe this termsheet") {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                val showText = message.text.isNotBlank() &&
                    (message.imageBitmaps.isEmpty() || message.text != "Describe this termsheet")
                if (showText) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (message.isUser) 16.dp else 4.dp,
                            bottomEnd = if (message.isUser) 4.dp else 16.dp,
                        ),
                        color = if (message.isUser) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = message.text,
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

@Composable
private fun ProductChatInput(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    onAttachTermsheet: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onAttachTermsheet, enabled = !isLoading) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Attach termsheet page",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = { Text("Ask about this product...") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                maxLines = 4,
            )
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank() && !isLoading)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

private fun renderTermsheetPages(context: android.content.Context): List<Bitmap> {
    val cacheFile = File(context.cacheDir, "termsheet.pdf")
    if (!cacheFile.exists()) {
        context.resources.openRawResource(
            context.resources.getIdentifier("termsheet", "raw", context.packageName)
        ).use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    val fd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(fd)
    val bitmaps = mutableListOf<Bitmap>()

    for (i in 0 until renderer.pageCount) {
        val page = renderer.openPage(i)
        val scale = 2
        val bitmap = Bitmap.createBitmap(
            page.width * scale,
            page.height * scale,
            Bitmap.Config.ARGB_8888
        )
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        bitmaps.add(bitmap)
    }

    renderer.close()
    fd.close()
    return bitmaps
}
