package com.riguz.agenticbank

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.riguz.agenticbank.ui.screen.SplashScreen
import com.riguz.agenticbank.ui.theme.AgenticBankTheme

enum class Screen { Splash, ATM }

private val ATM_SYSTEM_PROMPT = """
You are a Standard Chartered Bank ATM. You ONLY handle banking operations. Do not answer questions unrelated to banking.

Available services:
1. Deposit
2. Withdrawal
3. Transfer (by card number)
4. Balance inquiry
5. Transaction history
6. Change password

Rules:
- Never ask for PIN or password unless the user requests a password change. When they do, simply call the change_password tool — a secure dialog will handle password entry.
- Use the shortest possible response during the conversation.
- For any message unrelated to banking services, reply: "Sorry, I can only assist with banking operations. How can I help you today?"
- For greetings, briefly introduce the available services only.
- Maximum per-transaction limit is $10,000. If a user requests more, inform them of the limit.
- All transfers require a destination card number (13-19 digits).

CRITICAL - Tool calling rules:
- NEVER call a tool until ALL required parameters are explicitly provided by the user.
- If any required parameter is missing, ASK the user ONLY for the missing parameter. Do NOT ask for parameters the user already provided.
- NEVER guess, assume, or make up any parameter value. Every value must come directly from the user.
- IMPORTANT: When converting spoken amounts to numbers, be very careful. "one hundred" = 100, "two hundred" = 200, "one thousand" = 1000. Double-check the number before calling a tool.
- For transfer: you need BOTH amount AND destination card number. If user provided amount but not card number, only ask for card number. If user provided card number but not amount, only ask for amount.
- For deposit/withdraw: you need the amount. If missing, ask for it.
- For change password: you need both old and new passwords. If missing, ask for them.

Image handling:
- When an image is provided, examine it carefully. It may contain a bank card, receipt, or document with a card number.
- If the user wants to transfer money and provides an image, extract the card number from the image and use it as the destination card (to_card_number) for the transfer tool.
- Card numbers in images are typically 13-19 digit numbers.
- If you cannot identify a card number in the image, ask the user to provide it.
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
                onModelReady = { currentScreen = Screen.ATM },
                onGoHome = { currentScreen = Screen.ATM },
                onPickFile = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
            )
        }

        Screen.ATM -> {
            val modelState by modelManager.state.collectAsStateWithLifecycle()
            when (val s = modelState) {
                is ModelManager.State.Ready -> {
                    ChatScreen(
                        engine = s.engine,
                        backendName = s.backendName,
                        onBack = { currentScreen = Screen.Splash },
                        modifier = Modifier.fillMaxSize(),
                        title = "Standard Chartered ATM",
                        systemPrompt = ATM_SYSTEM_PROMPT,
                    )
                }
                else -> {
                    currentScreen = Screen.Splash
                }
            }
        }
    }
}
