package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClosedLoopReport
import com.example.data.EngagementMetrics
import com.example.data.MediaRepository
import android.util.Log
import com.example.data.StrategyAction
import com.example.data.StrategyBlueprint
import com.example.data.StrategyBlueprintGenerator
import com.example.ui.components.AuraTopBar
import com.example.ui.components.CompactEngagementDebugger
import com.example.ui.components.DiscoveryPolicyControl
import com.example.ui.components.EditableBlueprintStudioCard
import com.example.ui.theme.*
import com.example.util.IntelligenceExporter
import com.example.util.ModularReportType
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.content.ContentValues
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EngagementDebuggerScreen(
    repository: MediaRepository,
    onBack: () -> Unit,
    onNavigateToCleanupDebug: () -> Unit,
    modifier: Modifier = Modifier
) {
    var metrics by remember { mutableStateOf<EngagementMetrics?>(null) }
    var closedLoopReport by remember { mutableStateOf<ClosedLoopReport?>(null) }
    var closedLoopBlueprint by remember { mutableStateOf<StrategyBlueprint?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showExportMenu by remember { mutableStateOf(false) }
    var showModularPdfDialog by remember { mutableStateOf(false) }
    
    var lastExportedFile by remember { mutableStateOf<File?>(null) }
    var showPostExportDialog by remember { mutableStateOf(false) }

    fun handleExport(format: String, modularType: ModularReportType? = null) {
        val report = closedLoopReport ?: return
        val blueprint = closedLoopBlueprint
        val manifest = repository.blueprintArtifactManager?.currentArtifact?.value?.implementationManifest
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val authority = "${context.packageName}.fileprovider"

        try {
            val file: File
            when (format) {
                "JSON" -> {
                    val artifact = repository.blueprintArtifactManager?.currentArtifact?.value
                    if (artifact != null) {
                        file = com.example.data.blueprint.BlueprintSerializer.exportToFile(context, artifact)
                    } else {
                        val json = IntelligenceExporter.exportToJson(report, blueprint, manifest)
                        file = File(context.filesDir, "Aura_Report_$timestamp.json")
                        file.writeText(json)
                    }
                }
                "Markdown" -> {
                    val md = IntelligenceExporter.exportToMarkdown(report, blueprint, manifest)
                    file = File(context.filesDir, "Aura_Report_$timestamp.md")
                    file.writeText(md)
                }
                "PDF" -> {
                    val type = modularType ?: ModularReportType.COMBINED_PACKAGE
                    val typeSuffix = type.name.lowercase()
                    val safeTitle = blueprint?.title?.replace(Regex("[^a-zA-Z0-9]"), "_")?.take(20) ?: "Report"
                    file = File(context.filesDir, "Aura_${safeTitle}_${typeSuffix}_$timestamp.pdf")
                    IntelligenceExporter.generatePdfReport(context, report, blueprint, file, type)
                }
                else -> return
            }
            
            if (file.exists() && file.length() > 0) {
                lastExportedFile = file
                showPostExportDialog = true
            } else {
                throw Exception("File was not created or is empty.")
            }
            
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun shareFile(file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mimeType = when (file.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "json" -> "application/json"
                "md" -> "text/markdown"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Intelligence Report"))
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Could not share file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun saveToPublicStorage(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, when (file.extension.lowercase()) {
                        "pdf" -> "application/pdf"
                        "json" -> "application/json"
                        else -> "application/octet-stream"
                    })
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Aura/Intelligence")
                }
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        FileInputStream(file).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    android.widget.Toast.makeText(context, "Saved to Documents/Aura/Intelligence", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    throw Exception("Failed to create MediaStore entry")
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to save: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            shareFile(file)
            android.widget.Toast.makeText(context, "Save-to-Documents requires Android 10+. Use 'Share' to save manually.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun openFile(file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mimeType = when (file.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "json" -> "application/json"
                "md" -> "text/markdown"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Could not open file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val storedEvidence by repository.storedEvidence.collectAsStateWithLifecycle()

    LaunchedEffect(storedEvidence) {
        try {
            val fetchedMetrics = repository.getEngagementMetrics()
            metrics = fetchedMetrics
            
            val report = repository.generateClosedLoopReport(baselineScore = 50.0, targetScore = 60.0)
            closedLoopReport = report
            
            val bp = StrategyBlueprintGenerator.generateBlueprint(
                title = "Personalization Engine Optimization",
                description = "Evidence-driven strategy for media recommendation engine.",
                report = report,
                actions = listOf(
                    StrategyAction("Calibrate AISkip", "Adjust skip threshold based on user feedback", "HIGH", "AISkipEngine"),
                    StrategyAction("Expand Pairwise", "Gather more pairwise comparison signal", "MEDIUM", "PairwiseSystem")
                )
            )
            closedLoopBlueprint = bp
            repository.blueprintArtifactManager?.setBlueprint(bp)
        } catch (e: Exception) {
            Log.e("EngagementDebugger", "Failed to load engagement data", e)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuraBackground)
    ) {
        AuraTopBar(
            title = "Engagement Debugger",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AuraOnSurface
                    )
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Intelligence",
                            tint = AuraPurple
                        )
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Intelligence (JSON)") },
                            onClick = {
                                showExportMenu = false
                                handleExport("JSON")
                            },
                            leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Intelligence (Markdown)") },
                            onClick = {
                                showExportMenu = false
                                handleExport("Markdown")
                            },
                            leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Generate Modular PDF...") },
                            onClick = {
                                showExportMenu = false
                                showModularPdfDialog = true
                            },
                            leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) }
                        )
                    }
                }
            },
            showLogo = false
        )

        if (showModularPdfDialog) {
            AlertDialog(
                onDismissRequest = { showModularPdfDialog = false },
                title = { Text("Select Report Module") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModularReportType.entries.forEach { type ->
                            OutlinedButton(
                                onClick = {
                                    showModularPdfDialog = false
                                    handleExport("PDF", type)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(type.displayName, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showModularPdfDialog = false }) { Text("Cancel") }
                },
                containerColor = AuraSurface
            )
        }

        if (showPostExportDialog && lastExportedFile != null) {
            AlertDialog(
                onDismissRequest = { showPostExportDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF4ADE80))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Intelligence Report Ready")
                    }
                },
                text = {
                    Column {
                        Text("The ${lastExportedFile!!.extension.uppercase()} report has been generated successfully.", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("File: ${lastExportedFile!!.name}", fontSize = 11.sp, color = AuraOnSurfaceVariant)
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { 
                            showPostExportDialog = false
                            saveToPublicStorage(lastExportedFile!!)
                        }) {
                            Text("Save As")
                        }
                        Button(onClick = { 
                            showPostExportDialog = false
                            openFile(lastExportedFile!!)
                        }) {
                            Text("Open")
                        }
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { 
                            showPostExportDialog = false
                            shareFile(lastExportedFile!!)
                        }) {
                            Text("Share")
                        }
                        TextButton(onClick = { showPostExportDialog = false }) {
                            Text("Dismiss")
                        }
                    }
                },
                containerColor = AuraSurface
            )
        }

        if (metrics == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuraPurple)
            }
        } else {
            val report = calculateEngagementReport(metrics!!)
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                val policy by repository.discoveryPolicy.collectAsStateWithLifecycle()
                
                DiscoveryPolicyControl(
                    policy = policy,
                    onPolicyChange = { repository.updateDiscoveryPolicy(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                CompactEngagementDebugger(repository = repository)
                
                Spacer(modifier = Modifier.height(24.dp))

                EngagementScoreHeader(report.overallScore, report.status)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                ReportCard(report)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                DataConfidenceCard(report.confidence)
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "INTERNAL SYSTEM METRICS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuraOnSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                
                MetricsGrid(metrics!!)

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNavigateToCleanupDebug,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AuraPurple)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cleanup Intelligence Validation")
                }

                LibraryPerformanceCard(repository)

                SimulationAgentCard(repository)

                AISkipDiagnosticsCard(metrics!!.aiSkipStats)

                PairwiseDiagnosticsCard(metrics!!.pairwiseDiagnostics)

                TopEngagedClipsCard(metrics!!.topEngagedClips)

                if (closedLoopReport != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ClosedLoopDiagnosticsCard(closedLoopReport!!, closedLoopBlueprint)
                }

                repository.blueprintArtifactManager?.let { manager ->
                    Spacer(modifier = Modifier.height(16.dp))
                    EditableBlueprintStudioCard(
                        manager = manager,
                        onExportSuccess = { file ->
                            lastExportedFile = file
                            showPostExportDialog = true
                        },
                        onPdfExportRequested = { type ->
                            handleExport("PDF", type)
                        }
                    )
                }

                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LibraryPerformanceCard(repository: MediaRepository) {
    var expanded by remember { mutableStateOf(false) }
    val latestSortedItems by repository.latestAiSortRecommendation.collectAsStateWithLifecycle()
    val mediaItems by repository.mediaItems.collectAsStateWithLifecycle()

    Spacer(modifier = Modifier.height(16.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuraSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Speed, contentDescription = null, tint = AuraPurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LIBRARY PERFORMANCE DIAGNOSTICS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                }
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                DetailRow("Total Items in DB", "${mediaItems.size}")
                DetailRow("Sorted Items in Cache", "${latestSortedItems.size}")
                DetailRow("UI Model Active", if (latestSortedItems.isNotEmpty()) "YES" else "IDLE (Reclaimed)")
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Caching is currently utilizing 'WhileSubscribed' strategy to release memory when Library is inactive.", fontSize = 10.sp, color = AuraOnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = AuraOnSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
    }
}

@Composable
private fun SimulationAgentCard(repository: MediaRepository) {
    var expanded by remember { mutableStateOf(false) }
    
    Spacer(modifier = Modifier.height(16.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuraSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Science, contentDescription = null, tint = AuraPurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("INTELLIGENCE AGENT SIMULATION", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                }
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Inject evidence from autonomous agents to verify pipeline classification and blueprint generation.", fontSize = 11.sp, color = AuraOnSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { repository.injectEvidence(com.example.data.EvidenceTier.EXPERIMENTAL, 50, 75.0, 0.8) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("Experimental (+Score)", fontSize = 10.sp)
                    }
                    Button(
                        onClick = { repository.injectEvidence(com.example.data.EvidenceTier.SIMULATION, 1000, 85.0, 0.95) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("Simulation (+Score)", fontSize = 10.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { repository.clearEvidence() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) {
                    Text("Clear Injected Evidence", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun EngagementScoreHeader(score: Int, status: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        color = AuraSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Overall Engagement",
                fontSize = 14.sp,
                color = AuraOnSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "$score%",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = when {
                    score >= 80 -> Color(0xFF4ADE80)
                    score >= 50 -> AuraPurple
                    else -> Color(0xFFF87171)
                }
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = status.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = AuraOnSurface
            )
        }
    }
}

@Composable
private fun ReportCard(report: EngagementReport) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InfoCard(
            title = "Weekly Trend",
            content = report.trend,
            icon = Icons.AutoMirrored.Outlined.TrendingUp,
            iconColor = if (report.trend == "Improving") Color(0xFF4ADE80) else AuraPurple
        )
        
        FactorCard(
            title = "Positive Signals",
            factors = report.primaryFactorsIncreasing,
            icon = Icons.Outlined.AddCircleOutline,
            color = Color(0xFF4ADE80)
        )
        
        FactorCard(
            title = "Growth Areas",
            factors = report.primaryFactorsReducing,
            icon = Icons.Outlined.RemoveCircleOutline,
            color = Color(0xFFF87171)
        )
        
        FactorCard(
            title = "Recommendations",
            factors = report.recommendations,
            icon = Icons.Outlined.Lightbulb,
            color = Color(0xFFFFD700)
        )
    }
}

@Composable
private fun InfoCard(title: String, content: String, icon: ImageVector, iconColor: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
        color = AuraSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, fontSize = 12.sp, color = AuraOnSurfaceVariant)
                Text(text = content, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
            }
        }
    }
}

@Composable
private fun FactorCard(title: String, factors: List<String>, icon: ImageVector, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
        color = AuraSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
            }
            Spacer(modifier = Modifier.height(12.dp))
            factors.forEach { factor ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(text = "•", color = color, modifier = Modifier.width(16.dp))
                    Text(text = factor, fontSize = 13.sp, color = AuraOnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DataConfidenceCard(confidence: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
        color = AuraSurface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Analytics,
                contentDescription = null,
                tint = AuraOnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Data Coverage Confidence: $confidence",
                fontSize = 11.sp,
                color = AuraOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricsGrid(metrics: EngagementMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricBox("Total Plays", metrics.totalPlays.toString(), Modifier.weight(1f))
            MetricBox("Favorites", metrics.favoriteCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricBox("Avg Rating", String.format("%.1f", metrics.averageRating), Modifier.weight(1f))
            MetricBox("Comparisons", metrics.totalComparisons.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricBox("Clip Previews", metrics.totalClipPreviews.toString(), Modifier.weight(1f))
            MetricBox("Clip Exports", metrics.totalClipExports.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun TopEngagedClipsCard(clips: List<com.example.data.ClipInteractionSummary>) {
    if (clips.isEmpty()) return

    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = "MOST ENGAGED HIGHLIGHT CLIPS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AuraOnSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
            color = AuraSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                clips.forEach { clip ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = clip.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AuraOnSurface
                            )
                            Text(
                                text = "Previews: ${clip.previewCount}  ·  Selections: ${clip.selectCount}  ·  Exports: ${clip.exportCount}",
                                fontSize = 11.sp,
                                color = AuraOnSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AuraPurple.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${clip.score} pts",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AuraPurple,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        color = Color.Black.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, fontSize = 10.sp, color = AuraOnSurfaceVariant)
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

data class EngagementReport(
    val overallScore: Int,
    val status: String,
    val trend: String,
    val primaryFactorsIncreasing: List<String>,
    val primaryFactorsReducing: List<String>,
    val recommendations: List<String>,
    val confidence: String
)

@Composable
private fun AISkipDiagnosticsCard(stats: com.example.data.AISkipStats) {
    Spacer(modifier = Modifier.height(16.dp))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = AuraSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.FastForward,
                    contentDescription = null,
                    tint = AuraPurple,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AURA AI SKIP INTELLIGENCE DIAGNOSTICS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuraOnSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "AI Skip Forwards:", fontSize = 12.sp, color = AuraOnSurfaceVariant)
                Text(text = "${stats.totalSkipForwards}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "AI Skip Backs:", fontSize = 12.sp, color = AuraOnSurfaceVariant)
                Text(text = "${stats.totalSkipBacks}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "AI Skip Reversals (Returned to Skipped):", fontSize = 12.sp, color = AuraOnSurfaceVariant)
                Text(text = "${stats.totalSkipReversals}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB74D))
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Watched Destination Validations:", fontSize = 12.sp, color = AuraOnSurfaceVariant)
                Text(text = "${stats.totalWatchedDestinations}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
            }
        }
    }
}

@Composable
private fun PairwiseDiagnosticsCard(diag: com.example.data.PairwiseDiagnostics) {
    Spacer(modifier = Modifier.height(16.dp))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = AuraSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                    contentDescription = null,
                    tint = AuraPurple,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PAIRWISE INTELLIGENCE DIAGNOSTICS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuraOnSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Total Eligible Media:", fontSize = 12.sp, color = AuraOnSurfaceVariant)
                Text(text = "${diag.totalEligibleMedia}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Top-100 Candidate Pool Size:", fontSize = 12.sp, color = AuraOnSurfaceVariant)
                Text(text = "${diag.top100CandidatePoolSize}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuraPurple)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Compared Candidates in Pool:", fontSize = 12.sp, color = AuraOnSurfaceVariant)
                Text(text = "${diag.comparedCandidateCount}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AuraOnSurface)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Un-compared / Exploration Candidates:", fontSize = 12.sp, color = AuraOnSurfaceVariant)
                Text(text = "${diag.neverComparedCount}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AuraOnSurface)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Selection Reason: ${diag.lastSelectionReason}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = AuraOnSurfaceVariant
            )
        }
    }
}

private fun calculateEngagementReport(metrics: EngagementMetrics): EngagementReport {

    // 0-100 scale calculation
    var score = 0
    
    // Weighting logic
    score += (metrics.totalPlays * 2).coerceAtMost(30) // Up to 30 pts for usage
    score += (metrics.totalComparisons * 5).coerceAtMost(25) // Up to 25 pts for training
    score += (metrics.favoriteCount * 5).coerceAtMost(20) // Up to 20 pts for explicit preferences
    score += (metrics.averageRating * 5).toInt().coerceAtMost(25) // Up to 25 pts for sentiment
    score += (metrics.totalClipPreviews * 1 + metrics.totalClipSelections * 2 + metrics.totalClipExports * 5).coerceAtMost(20) // Up to 20 pts for clip engagement
    
    val status = when {
        score >= 80 -> "Highly Engaged"
        score >= 50 -> "Healthy"
        score >= 20 -> "Developing"
        else -> "Low"
    }
    
    val trend = if (metrics.totalPlays > 5 || metrics.totalComparisons > 10 || metrics.totalClipPreviews > 5) "Improving" else "Stable"
    
    val increasing = mutableListOf<String>()
    if (metrics.totalPlays > 0) increasing.add("Active media discovery detected")
    if (metrics.totalComparisons > 0) increasing.add("Model training through comparisons")
    if (metrics.favoriteCount > 0) increasing.add("Explicit preference library building")
    if (metrics.totalClipPreviews > 0 || metrics.totalClipExports > 0) {
        increasing.add("Highlight clip engagement logged (${metrics.totalClipPreviews} previews, ${metrics.totalClipExports} exports)")
    }
    if (increasing.isEmpty()) increasing.add("Initial app exploration")

    val reducing = mutableListOf<String>()
    if (metrics.totalPlays < 10) reducing.add("Low content interaction frequency")
    if (metrics.totalComparisons < 20) reducing.add("Limited model training data")
    if (metrics.averageRating == 0f) reducing.add("Absence of explicit rating signals")
    if (reducing.isEmpty()) reducing.add("None identified")

    val recs = mutableListOf<String>()
    if (metrics.totalComparisons < 50) recs.add("Perform more pairwise comparisons")
    if (metrics.favoriteCount < 5) recs.add("Mark more items as favorites")
    if (metrics.itemsDiscovered < 50) recs.add("Import more local media")
    if (metrics.totalClipPreviews == 0) recs.add("Generate and preview dynamic highlight clips on videos")
    if (recs.isEmpty()) recs.add("Explore Discover categories daily")

    val confidence = when {
        metrics.totalPlays > 20 && metrics.totalComparisons > 50 -> "High"
        metrics.totalPlays > 5 || metrics.totalComparisons > 10 || metrics.totalClipPreviews > 5 -> "Medium"
        else -> "Low (Insufficient Data)"
    }

    return EngagementReport(
        overallScore = score,
        status = status,
        trend = trend,
        primaryFactorsIncreasing = increasing,
        primaryFactorsReducing = reducing,
        recommendations = recs,
        confidence = confidence
    )
}

@Composable
private fun ClosedLoopDiagnosticsCard(
    report: ClosedLoopReport,
    blueprint: StrategyBlueprint?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = AuraSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "CLOSED LOOP EVIDENCE INTEGRITY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuraPurple
                )
                Text(
                    text = report.outcomeClassification.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (report.productionSampleCount == 0) Color.Red else AuraOnSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = if (report.productionSampleCount == 0) Color(0xFFFFDAD6) else Color(0xFFE8DEF8),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Production Evidence Samples: ${report.productionSampleCount}",
                        fontWeight = FontWeight.Bold,
                        color = if (report.productionSampleCount == 0) Color(0xFF680008) else Color(0xFF1D192B),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Production Improvement Established: ${if (report.productionImprovementEstablished) "YES" else "NO"}",
                        fontWeight = FontWeight.Bold,
                        color = if (report.productionSampleCount == 0) Color(0xFF680008) else Color(0xFF1D192B),
                        fontSize = 12.sp
                    )
                    Text(
                        text = report.summaryMessage,
                        fontSize = 11.sp,
                        color = if (report.productionSampleCount == 0) Color(0xFF680008) else Color(0xFF1D192B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Baseline Score: ${"%.2f".format(report.baselineScore)} | Measured: ${"%.2f".format(report.measuredScore)} | Target: ${"%.2f".format(report.targetScore)} (${report.targetValidity.name})", fontSize = 11.sp, color = AuraOnSurfaceVariant)
            Text("Prod Confidence: ${"%.1f".format(report.productionConfidence * 100)}% | Overall: ${"%.1f".format(report.overallConfidence * 100)}%", fontSize = 11.sp, color = AuraOnSurfaceVariant)

            if (report.productionEvidence.isNotEmpty() || report.experimentalEvidence.isNotEmpty() || report.simulationEvidence.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("EVIDENCE BY TIER:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AuraOnSurfaceVariant)
                
                (report.productionEvidence + report.experimentalEvidence + report.simulationEvidence).forEach { rec ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${rec.tier.name} (${rec.sampleCount}s):", fontSize = 10.sp, color = AuraOnSurfaceVariant)
                        Text("${"%.2f".format(rec.score)} pts (Q:${"%.2f".format(rec.quality)})", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = AuraOnSurface)
                    }
                }
            }

            if (blueprint != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = AuraOnSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                Text("STRATEGY BLUEPRINT: ${blueprint.title}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
                Text("Notice: ${blueprint.recommendationNotice}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (blueprint.recommendationNotice.startsWith("NO")) Color.Red else AuraPurple)
                Text("Blueprint ID: ${blueprint.identity.blueprintId.take(8)}... | Status: ${blueprint.identity.status.name} | v${blueprint.identity.version}", fontSize = 10.sp, color = AuraOnSurfaceVariant)
                Text("Validation State: ${blueprint.validationState.name}", fontSize = 11.sp, color = AuraPurple)
                Text("Risk: ${blueprint.riskAssessment}", fontSize = 11.sp, color = AuraOnSurfaceVariant)

                if (blueprint.proposedModifications.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Proposed Modifications (${blueprint.proposedModifications.size}):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
                    blueprint.proposedModifications.forEach { mod ->
                        Text("• [${mod.modificationType.name}] ${mod.component}.${mod.parameter}: ${mod.currentValue} -> ${mod.proposedValue} (${mod.delta})", fontSize = 10.sp, color = AuraOnSurfaceVariant)
                    }
                }

                if (blueprint.executionPlan.intendedActions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Execution Plan:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
                    blueprint.executionPlan.intendedActions.forEach { act ->
                        Text("  Step ${act.stepOrder}: ${act.action} (${act.targetComponent})", fontSize = 10.sp, color = AuraOnSurfaceVariant)
                    }
                }
            }
        }
    }
}

