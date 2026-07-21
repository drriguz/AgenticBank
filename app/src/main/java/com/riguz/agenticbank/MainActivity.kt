package com.riguz.agenticbank

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riguz.agenticbank.model.ModelManager
import com.riguz.agenticbank.ui.screen.ChatScreen
import com.riguz.agenticbank.ui.screen.PdfViewerScreen
import com.riguz.agenticbank.ui.screen.SplashScreen
import com.riguz.agenticbank.ui.screen.StructuredProductScreen
import com.riguz.agenticbank.ui.screen.SubscriptionScreen
import com.riguz.agenticbank.ui.theme.AgenticBankTheme
import java.io.ByteArrayOutputStream
import java.io.File

enum class Screen { Splash, Chat, Home, StructuredProduct, PdfViewer, ProductChat, Subscription }

private val PRODUCT_SYSTEM_PROMPT = """
You are a professional banking product advisor agent for structured deposit products.

You have access to the following tools:

1. calculate_return: Use this tool when the user asks about returns, yield, or wants to calculate returns. The parameter is "amount" (investment amount in USD).

2. show_confirmation: Use this tool when the user wants to subscribe or purchase this product. The parameter is "amount" (investment amount in USD).

When the user asks to calculate returns or subscribe, you MUST use the appropriate tool. Do not calculate manually.

## General Guidelines:
- You will receive termsheet pages as images. These are internal bank documents, not user uploaded. Treat them as authoritative.
- Answer the best you can based on available information
- For product-specific questions, ONLY provide information you are certain about from the termsheet. If something is not clearly stated in the termsheet, say "This information is not available in the provided termsheet."
- Do NOT make assumptions or fabricate details about the product
- Always use tools for calculations and confirmations
- Be professional, concise, and helpful
- If the user's intent is unclear, ask clarifying questions
- ALWAYS respond in English, regardless of the language the user uses
- Don't ask user to provide termsheets or any other product related info
""".trimIndent()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgenticBankTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val modelManager: ModelManager = viewModel()
    var currentScreen by remember { mutableStateOf(Screen.Splash) }
    var subscriptionAmount by remember { mutableStateOf(0.0) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            modelManager.loadModelFromUri(uri)
        }
    }

    when (currentScreen) {
        Screen.Splash -> {
            SplashScreen(
                modelManager = modelManager,
                onModelReady = { currentScreen = Screen.Home },
                onGoHome = { currentScreen = Screen.Home },
                onPickFile = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
            )
        }

        Screen.Chat -> {
            BackHandler { currentScreen = Screen.Home }
            val modelState by modelManager.state.collectAsStateWithLifecycle()
            when (val s = modelState) {
                is ModelManager.State.Ready -> {
                    ChatScreen(
                        engine = s.engine,
                        backendName = s.backendName,
                        onBack = { currentScreen = Screen.Home },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    currentScreen = Screen.Splash
                }
            }
        }

        Screen.Home -> {
            HomeScreen(
                onChatClick = { currentScreen = Screen.Chat },
                onStructuredProductClick = { currentScreen = Screen.StructuredProduct },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Screen.StructuredProduct -> {
            BackHandler { currentScreen = Screen.Home }
            StructuredProductScreen(
                onBack = { currentScreen = Screen.Home },
                onViewTermsheet = { currentScreen = Screen.PdfViewer },
                onChatClick = { currentScreen = Screen.ProductChat },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Screen.PdfViewer -> {
            BackHandler { currentScreen = Screen.StructuredProduct }
            PdfViewerScreen(
                onBack = { currentScreen = Screen.StructuredProduct },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Screen.ProductChat -> {
            BackHandler { currentScreen = Screen.StructuredProduct }
            val modelState by modelManager.state.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current
            val termsheetPages = remember { renderTermsheetPages(context) }
            when (val s = modelState) {
                is ModelManager.State.Ready -> {
                    ChatScreen(
                        engine = s.engine,
                        backendName = s.backendName,
                        onBack = { currentScreen = Screen.StructuredProduct },
                        modifier = Modifier.fillMaxSize(),
                        title = "Product Advisor",
                        systemPrompt = PRODUCT_SYSTEM_PROMPT,
                        termsheetPages = termsheetPages,
                        onSubscribe = { amount ->
                            subscriptionAmount = amount
                            currentScreen = Screen.Subscription
                        },
                    )
                }
                else -> {
                    currentScreen = Screen.Splash
                }
            }
        }

        Screen.Subscription -> {
            BackHandler { currentScreen = Screen.ProductChat }
            SubscriptionScreen(
                amount = subscriptionAmount,
                onBack = { currentScreen = Screen.ProductChat },
                onConfirm = {
                    currentScreen = Screen.Home
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun renderTermsheetPages(context: Context): List<ByteArray> {
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
    val pageBytes = mutableListOf<ByteArray>()

    for (i in 0 until renderer.pageCount) {
        val page = renderer.openPage(i)
        val scale = 3
        val bitmap = Bitmap.createBitmap(
            page.width * scale,
            page.height * scale,
            Bitmap.Config.ARGB_8888
        )
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        pageBytes.add(stream.toByteArray())
        bitmap.recycle()
    }

    renderer.close()
    fd.close()
    return pageBytes
}
