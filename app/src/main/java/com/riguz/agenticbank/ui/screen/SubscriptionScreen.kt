package com.riguz.agenticbank.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    amount: Double,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val annualRate = 0.055
    val tenorMonths = 12
    val expectedReturn = amount * annualRate * tenorMonths / 12
    val totalPayout = amount + expectedReturn
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscription") },
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
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {
            // Header banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "MALI260710BLU02BD",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Structured Deposit Subscription",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Investment summary card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Investment Summary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SummaryRow("Product ID", "MALI260710BLU02BD")
                    SummaryRow("Product Name", "Non Principal Protected - BD - 1")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SummaryRow("Investment Amount", "USD %,.0f".format(amount), isHighlight = true)
                    SummaryRow("Currency", "USD")
                    SummaryRow("Tenor", "$tenorMonths Months")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SummaryRow("Expected Return", "USD %,.2f".format(expectedReturn), isHighlight = true)
                    SummaryRow("Maturity Date", "Jul 2027")
                    SummaryRow("Total Payout", "USD %,.2f".format(totalPayout), isHighlight = true)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Risk disclosure card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Risk Disclosure",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Risk Rating: 5 (High)\n• Principal Protection: 100%\n• This product is suitable for Qualified Investors only\n• Past performance is not indicative of future results",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Investor confirmation
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Investor Confirmation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ConfirmationCheckItem("I am a Qualified Investor")
                    ConfirmationCheckItem("I understand the risk rating is 5 (High)")
                    ConfirmationCheckItem("I have read and agree to the termsheet")
                    ConfirmationCheckItem("I confirm the investment amount of USD %,.0f".format(amount))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Confirm Subscription",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    isHighlight: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ConfirmationCheckItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
