package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.DiscoveryPolicy
import com.example.data.IntelligenceRepository
import com.example.data.TasteDNA
import com.example.data.social.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private enum class DiscoveryViewMode(val label: String) {
    SIDE_BY_SIDE("Side-by-Side Comparison"),
    AURA_TASTE("Aura Taste Order"),
    PLATFORM("Original Platform Order")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalSocialDiscoveryScreen(
    repository: IntelligenceRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tasteDNA by repository.mediaRepository.tasteDNA.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("cyberpunk lighting") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var benchmarkResult by remember { mutableStateOf<SocialDiscoveryBenchmarkResult?>(null) }
    var selectedViewMode by remember { mutableStateOf(DiscoveryViewMode.SIDE_BY_SIDE) }
    var useLiveApi by remember { mutableStateOf(false) }
    var customApiKey by remember { mutableStateOf("") }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    // Quick suggestion presets for instant aesthetic testing
    val suggestedQueries = listOf(
        "cyberpunk lighting",
        "minimal ambient modular synth",
        "warm analog film photography",
        "vibrant anime aesthetic",
        "dark noir detective moody",
        "macro nature 4k",
        "hyper dense megacity architecture"
    )

    fun executeBenchmark(query: String) {
        if (query.isBlank()) {
            errorMessage = "Please enter a search query."
            return
        }

        errorMessage = null
        isLoading = true

        coroutineScope.launch {
            try {
                val candidates: List<SocialCandidateDto> = if (useLiveApi) {
                    val keyToUse = customApiKey.ifBlank {
                        // Attempt fallback to system env if present
                        System.getenv("YOUTUBE_API_KEY") ?: ""
                    }
                    if (keyToUse.isBlank()) {
                        isLoading = false
                        errorMessage = "Live API Key is required. Provide a YouTube Data API v3 key or switch to Benchmark Dataset mode."
                        return@launch
                    }
                    val result = YouTubeSearchClient.searchVideos(query, keyToUse, 25)
                    if (result.isFailure) {
                        isLoading = false
                        val ex = result.exceptionOrNull()
                        errorMessage = "YouTube API Error: ${ex?.message ?: "Unknown error"}"
                        return@launch
                    }
                    result.getOrDefault(emptyList())
                } else {
                    // Fast, reliable, deterministic developer sample candidates
                    YouTubeSearchClient.getSampleCandidates(query)
                }

                if (candidates.isEmpty()) {
                    errorMessage = "Zero candidates retrieved for query: '$query'"
                    isLoading = false
                    return@launch
                }

                // Run on-device Taste Discovery Benchmark
                val result = SocialDiscoveryEngine.runBenchmark(
                    candidates = candidates,
                    tasteDNA = tasteDNA,
                    policy = DiscoveryPolicy(),
                    query = query
                )

                benchmarkResult = result
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Benchmark Execution Failed: ${e.localizedMessage}"
                isLoading = false
            }
        }
    }

    // Auto-run initial benchmark on first load with default query
    LaunchedEffect(Unit) {
        if (benchmarkResult == null) {
            executeBenchmark("cyberpunk lighting")
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AuraCrispWhite)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Universal Social Discovery",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AuraMidnight
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = DiscoveryViolet.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "EXPERIMENTAL POC",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DiscoveryViolet,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Zero-Persistence On-Device Taste Intelligence",
                            fontSize = 12.sp,
                            color = AuraMutedSlate
                        )
                    }

                    IconButton(
                        onClick = { showApiKeyDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (useLiveApi) Icons.Default.Cloud else Icons.Outlined.CloudOff,
                            contentDescription = "API Settings",
                            tint = if (useLiveApi) DiscoveryViolet else AuraMutedSlate
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar & Execute Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        placeholder = { Text("Enter discovery query...", fontSize = 13.sp) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = DiscoveryViolet,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { executeBenchmark(searchQuery) }),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DiscoveryViolet,
                            unfocusedBorderColor = AuraSubtleBorder,
                            focusedContainerColor = AuraCrispWhite,
                            unfocusedContainerColor = AuraCrispWhite
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { executeBenchmark(searchQuery) },
                        enabled = !isLoading && searchQuery.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = DiscoveryViolet),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(52.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AuraCrispWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Benchmark", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Query Suggestion Chips
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(suggestedQueries) { query ->
                        val isSelected = searchQuery.equals(query, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) DiscoveryViolet.copy(alpha = 0.15f) else AuraSubtleSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) DiscoveryViolet else AuraSubtleBorder
                            ),
                            modifier = Modifier.clickable {
                                searchQuery = query
                                executeBenchmark(query)
                            }
                        ) {
                            Text(
                                text = query,
                                fontSize = 11.sp,
                                color = if (isSelected) DiscoveryViolet else AuraSlate,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // View Mode Tabs
                Spacer(modifier = Modifier.height(8.dp))
                TabRow(
                    selectedTabIndex = selectedViewMode.ordinal,
                    containerColor = AuraCrispWhite,
                    contentColor = DiscoveryViolet,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedViewMode.ordinal]),
                            color = DiscoveryViolet
                        )
                    }
                ) {
                    DiscoveryViewMode.entries.forEach { mode ->
                        Tab(
                            selected = selectedViewMode == mode,
                            onClick = { selectedViewMode = mode },
                            text = {
                                Text(
                                    text = mode.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedViewMode == mode) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }
        },
        containerColor = AuraSubtleSurface
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (errorMessage != null) {
                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFDC2626)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFF991B1B),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            benchmarkResult?.let { result ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Diagnostic Summary Metrics Card
                    item {
                        BenchmarkMetricsCard(result = result, tasteDNA = tasteDNA)
                    }

                    // 2. Candidate Items based on selected view mode
                    when (selectedViewMode) {
                        DiscoveryViewMode.SIDE_BY_SIDE -> {
                            item {
                                Text(
                                    text = "AURA PERSONALIZED TASTE ORDER (WITH PLATFORM DELTA)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DiscoveryViolet,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            itemsIndexed(result.auraRankedCandidates) { index, candidate ->
                                AuraSocialCandidateCard(
                                    candidate = candidate,
                                    onLaunch = { url ->
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot launch: $url", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }

                        DiscoveryViewMode.AURA_TASTE -> {
                            item {
                                Text(
                                    text = "AURA TASTE RANKING (SORTED BY 24-D ALIGNMENT)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DiscoveryViolet,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            itemsIndexed(result.auraRankedCandidates) { index, candidate ->
                                AuraSocialCandidateCard(
                                    candidate = candidate,
                                    onLaunch = { url ->
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot launch: $url", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }

                        DiscoveryViewMode.PLATFORM -> {
                            item {
                                Text(
                                    text = "ORIGINAL PLATFORM SEARCH ORDER (YOUTUBE DATA API)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AuraSlate,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            itemsIndexed(result.originalPlatformCandidates) { index, candidate ->
                                AuraSocialCandidateCard(
                                    candidate = candidate,
                                    onLaunch = { url ->
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot launch: $url", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Developer API Config Dialog
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Developer API Connector Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Toggle between local high-fidelity benchmark datasets or live YouTube Data API v3 search.",
                        fontSize = 12.sp,
                        color = AuraMutedSlate
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Use Live YouTube API v3", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = useLiveApi,
                            onCheckedChange = { useLiveApi = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = DiscoveryViolet)
                        )
                    }

                    if (useLiveApi) {
                        OutlinedTextField(
                            value = customApiKey,
                            onValueChange = { customApiKey = it },
                            label = { Text("YouTube API Key") },
                            placeholder = { Text("AIzaSy...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Note: API key is held in memory for this session only and never committed.",
                            fontSize = 10.sp,
                            color = AuraMutedSlate
                        )
                    } else {
                        Surface(
                            color = DiscoveryViolet.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✓ Benchmark Dataset Mode Active: Instant, quota-free offline evaluation of 25 diverse aesthetic video candidates.",
                                fontSize = 11.sp,
                                color = DiscoveryViolet,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Apply Settings", color = DiscoveryViolet, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun BenchmarkMetricsCard(
    result: SocialDiscoveryBenchmarkResult,
    tasteDNA: TasteDNA
) {
    Surface(
        color = AuraCrispWhite,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AURA BENCHMARK DIAGNOSTICS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DiscoveryViolet,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Query: '${result.query}'",
                    fontSize = 11.sp,
                    color = AuraMutedSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Grid KPI metrics
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricKpiBox(
                    title = "Rank Displaced",
                    value = "${result.rankChangedPercentage.toInt()}%",
                    subtitle = "${result.rankChangedCount}/${result.totalScored} candidates",
                    modifier = Modifier.weight(1f)
                )
                MetricKpiBox(
                    title = "Avg Movement",
                    value = "±${"%.1f".format(result.avgAbsoluteRankMovement)}",
                    subtitle = "positions delta",
                    modifier = Modifier.weight(1f)
                )
                MetricKpiBox(
                    title = "Top-3 Overlap",
                    value = "${result.top3OverlapCount} / 3",
                    subtitle = "platform vs taste",
                    modifier = Modifier.weight(1f)
                )
                MetricKpiBox(
                    title = "Spearman (rs)",
                    value = result.rankCorrelation?.let { "%.2f".format(it) } ?: "N/A",
                    subtitle = "rank correlation",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Peak movements row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AuraSubtleSurface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "▲ Peak Promotion: ${result.largestPositiveCandidate?.let { "#${it.originalRank} → #${it.auraRank} (+${result.largestPositiveDelta})" } ?: "None"}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF059669)
                )
                Text(
                    text = "▼ Peak Demotion: ${result.largestNegativeCandidate?.let { "#${it.originalRank} → #${it.auraRank} (${result.largestNegativeDelta})" } ?: "None"}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFDC2626)
                )
            }
        }
    }
}

@Composable
private fun MetricKpiBox(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AuraSubtleSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 9.sp, color = AuraMutedSlate, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AuraMidnight)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 8.sp, color = AuraMutedSlate)
        }
    }
}

@Composable
private fun AuraSocialCandidateCard(
    candidate: AuraSocialCandidate,
    onLaunch: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val deltaColor = when {
        candidate.rankDelta > 0 -> Color(0xFF059669) // Green promotion
        candidate.rankDelta < 0 -> Color(0xFFDC2626) // Red demotion
        else -> AuraMutedSlate // Neutral
    }

    val deltaText = when {
        candidate.rankDelta > 0 -> "▲ +${candidate.rankDelta}"
        candidate.rankDelta < 0 -> "▼ ${candidate.rankDelta}"
        else -> "— 0"
    }

    Surface(
        color = AuraCrispWhite,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (candidate.auraRank <= 3) DiscoveryViolet.copy(alpha = 0.4f) else AuraSubtleBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Rank Badges Column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(54.dp)
                ) {
                    // Aura Rank Circle
                    Surface(
                        shape = CircleShape,
                        color = if (candidate.auraRank <= 3) DiscoveryViolet else AuraSubtleSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (candidate.auraRank <= 3) DiscoveryViolet else AuraSubtleBorder
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "#${candidate.auraRank}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (candidate.auraRank <= 3) AuraCrispWhite else AuraMidnight
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Rank Delta Chip
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = deltaColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = deltaText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = deltaColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Orig #${candidate.originalRank}",
                        fontSize = 9.sp,
                        color = AuraMutedSlate
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Video Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 84.dp, height = 58.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AuraSlate)
                ) {
                    if (candidate.candidate.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = candidate.candidate.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Surface(
                        color = AuraMidnight.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = "YOUTUBE",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuraCrispWhite,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title & Channel & Alignment
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.candidate.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AuraMidnight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = candidate.candidate.channelName.ifBlank { "YouTube Creator" },
                        fontSize = 11.sp,
                        color = AuraMutedSlate,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Taste Alignment Score Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DiscoveryViolet.copy(alpha = 0.12f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = DiscoveryViolet,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${candidate.tasteAlignmentPercent}% Taste Alignment",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DiscoveryViolet
                                )
                            }
                        }

                        // Launch button
                        IconButton(
                            onClick = { onLaunch(candidate.candidate.externalUrl) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open in YouTube",
                                tint = AuraSlate,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Top Matched Traits Row
            if (candidate.matchedTraits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    candidate.matchedTraits.take(4).forEach { trait ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AuraSubtleSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                        ) {
                            Text(
                                text = "• ${trait.displayName} (${(trait.alignment * 100).toInt()}%)",
                                fontSize = 10.sp,
                                color = AuraSlate,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Expandable Developer Inspector
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(AuraSubtleSurface, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "DEVELOPER INTELLIGENCE INSPECTOR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DiscoveryViolet,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Exploitation Score: ${"%.3f".format(candidate.exploitationScore)}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AuraSlate
                        )
                        Text(
                            text = "Policy Score: ${"%.3f".format(candidate.policyScore)}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AuraSlate
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Normalized Tokens: ${candidate.normalizedTokens.joinToString(", ")}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AuraMutedSlate
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Mapped Trait Weights: ${candidate.traitAdjustments.entries.joinToString { "${it.key}: ${"%.2f".format(it.value)}" }}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AuraMutedSlate
                    )
                }
            }
        }
    }
}
