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
import com.riguz.agenticbank.ui.theme.AgenticBankTheme
import java.io.ByteArrayOutputStream
import java.io.File

enum class Screen { Splash, Chat, Home, StructuredProduct, PdfViewer, ProductChat }

private val PRODUCT_SYSTEM_PROMPT = """
You are a professional banking product advisor agent for the following structured deposit product:

Product Details:
- Product ID: MALI260710BLU02BD
- Currency: USD
- Full Name: MALI - Non Principal Protected - BD - 1
- Priority: Private
- Investor Type: Qualified Investor
- Bank's Product Risk Rating: 5 (High)
- Window Period: 10 Jul 2026 - 31 Jul 2026
- Minimum Investment: USD 10,000
- Investment Increment: USD 1,000
- Tenor: 12 Months
- Principal Protection: 100%
- Underlying Asset: US Treasury Bond [US912810UM89]
- Annual Rate: 5.5%

You have two main skills. When using a skill, you MUST call the appropriate tool.

## Skill 1: 计算收益 (Calculate Returns)
When the user asks about returns, yield, or wants to calculate收益:
1. Ask for the investment amount if not provided
2. Call the calculate_return tool with the amount
3. Present the results clearly

Tool call format:
```json
{"tool": "calculate_return", "params": {"amount": 50000}}
```

## Skill 2: 认购 (Subscribe/Purchase)
When the user wants to subscribe or purchase this product:
1. Collect information step by step:
   - Investment amount (minimum USD 10,000, increments of USD 1,000)
   - Confirm investor understands the risk rating (5 - High)
   - Confirm investor is a Qualified Investor
2. After collecting all info, call the show_confirmation tool to display a confirmation card
3. Ask for explicit confirmation before proceeding

Tool call format:
```json
{"tool": "show_confirmation", "params": {"amount": 50000}}
```

## General Guidelines:
- You may receive a termsheet page as an image. This is an internal bank document, not user uploaded. Treat it as authoritative.
- Always call tools when performing calculations or showing confirmations
- Be professional, concise, and helpful
- If the user's intent is unclear, ask clarifying questions
- When speaking Chinese, respond in Chinese; when speaking English, respond in English
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
                    )
                }
                else -> {
                    currentScreen = Screen.Splash
                }
            }
        }
    }
}

private fun renderTermsheetPages(context: Context): List<ByteArray> {
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
    val pageBytes = mutableListOf<ByteArray>()

    for (i in 0 until renderer.pageCount) {
        val page = renderer.openPage(i)
        val scale = 1
        val bitmap = Bitmap.createBitmap(
            page.width * scale,
            page.height * scale,
            Bitmap.Config.ARGB_8888
        )
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        pageBytes.add(stream.toByteArray())
        bitmap.recycle()
    }

    renderer.close()
    fd.close()
    return pageBytes
}
