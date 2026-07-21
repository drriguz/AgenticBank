package com.riguz.agenticbank.ui.screen

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    val pages = remember { mutableListOf<Bitmap>() }
    var scale by remember { mutableFloatStateOf(1f) }

    DisposableEffect(Unit) {
        val cacheFile = File(context.cacheDir, "termsheet.pdf")
        // Always copy from resources to ensure latest version
        context.resources.openRawResource(
            context.resources.getIdentifier("termsheet", "raw", context.packageName)
        ).use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val fd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        totalPages = renderer.pageCount

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
            pages.add(bitmap)
        }

        onDispose {
            renderer.close()
            fd.close()
            pages.forEach { it.recycle() }
            pages.clear()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (totalPages > 0) "Term Sheet  ·  Page ${currentPage + 1} / $totalPages"
                        else "Term Sheet"
                    )
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
        if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Loading PDF...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                itemsIndexed(pages) { index, bitmap ->
                    val firstVisible = listState.firstVisibleItemIndex
                    if (firstVisible in pages.indices) {
                        currentPage = firstVisible
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                )
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "End of document",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
