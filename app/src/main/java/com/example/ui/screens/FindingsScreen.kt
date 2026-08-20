package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.IntelligenceRepository
import com.example.ui.components.FindingCard

@Composable
fun FindingsScreen(
    repository: IntelligenceRepository,
    onNavigateToFinding: (String) -> Unit
) {
    val viewModel: IntelligenceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return IntelligenceViewModel(repository) as T
            }
        }
    )

    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.findings.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        com.example.ui.components.AuraLogoIcon(size = 64.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Aura hasn't identified anything requiring attention.",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = com.example.ui.theme.AuraMutedSlate
                        )
                    }
                }
            }
        } else {
            items(state.findings) { finding ->
                FindingCard(
                    finding = finding,
                    onClick = { onNavigateToFinding(finding.id) }
                )
            }
        }
    }
}
