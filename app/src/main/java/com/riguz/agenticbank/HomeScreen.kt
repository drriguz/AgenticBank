package com.riguz.agenticbank

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riguz.agenticbank.ui.theme.AgenticBankTheme

data class DemoCase(
    val title: String,
    val emoji: String,
    val onClick: (() -> Unit)? = null,
)

val demoCases = listOf(
    DemoCase("Chat\nGemma 4 E2B", "\uD83E\uDD16"),
    DemoCase("Structured Deposit\nTermsheet Explainer", "\uD83D\uDCC4"),
    DemoCase("Deposit Yield Scenario\nConversational Calculator", "\uD83E\uDDEE"),
    DemoCase("Smart Search", "\uD83D\uDD0D"),
    DemoCase("Data Anonymization", "\uD83D\uDD12"),
    DemoCase("More to Come", "\u2795"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onChatClick: () -> Unit = {},
    onStructuredProductClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cases = demoCases.mapIndexed { index, demo ->
        when (index) {
            0 -> demo.copy(onClick = onChatClick)
            1 -> demo.copy(onClick = onStructuredProductClick)
            else -> demo
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AgenticBank Demo") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(cases) { demo ->
                DemoCaseCard(demo)
            }
        }
    }
}

@Composable
fun DemoCaseCard(demo: DemoCase, modifier: Modifier = Modifier) {
    Card(
        onClick = { demo.onClick?.invoke() },
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = demo.emoji,
                    fontSize = 28.sp,
                )
            }
            Text(
                text = demo.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    AgenticBankTheme {
        HomeScreen()
    }
}
