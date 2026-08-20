package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.app.RecoverableSecurityException
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.data.db.PlaybackErrorLogEntity
import com.example.data.intelligence.AuraConversionAdvisor
import com.example.ui.components.AuraTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private enum class ViewMode {
    LOGS,
    CONVERSION,
    QUEUE
}

@SuppressLint("NewApi")
@Composable
fun PlaybackDiagnosticsScreen(
    viewModel: PlaybackDiagnosticsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorLogs by viewModel.errorLogs.collectAsStateWithLifecycle()
    var selectedError by remember { mutableStateOf<PlaybackErrorLogEntity?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val conversionStage = viewModel.conversionStage.value
    val conversionProgress = viewModel.conversionProgress.value
    val lastResult = viewModel.lastResult.value
    
    val eligibilitySummary by viewModel.eligibilitySummary.collectAsStateWithLifecycle()
    val selectedCandidateIds by viewModel.selectedCandidateIds
    val conversionQueue by viewModel.conversionQueue.collectAsStateWithLifecycle()
    val isAutoCleanupEnabled by viewModel.isAutoCleanupEnabled
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var viewMode by remember { mutableStateOf<ViewMode>(ViewMode.LOGS) }

    // Launcher for OS-level deletion authorization (Android 10+)
    var pendingAuthJobId by remember { mutableStateOf<Long?>(null) }
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingAuthJobId?.let { jobId ->
                coroutineScope.launch {
                    viewModel.performDirectCleanup(context, jobId)
                    Toast.makeText(context, "Original deleted successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }
        pendingAuthJobId = null
    }

    Box(modifier = modifier.fillMaxSize().background(AuraCrispWhite)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AuraTopBar(
                title = "Playback Diagnostics",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AuraMidnight
                        )
                    }
                },
                actions = {
                    if (errorLogs.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = Color(0xFFEF4444))
                        }
                    }
                }
            )

            if (errorLogs.isEmpty()) {
                EmptyDiagnosticsState()
            } else {
                if (conversionStage != ConversionStage.IDLE && conversionStage != ConversionStage.COMPLETE && conversionStage != ConversionStage.FAILED) {
                    ConversionProgressCard(
                        stage = conversionStage,
                        progress = conversionProgress,
                        onCancel = { viewModel.resetConversion() }
                    )
                } else if (lastResult != null) {
                    ConversionResultCard(
                        result = lastResult,
                        onDismiss = { viewModel.resetConversion() }
                    )
                }

                Text(
                    text = "Playback errors recorded locally on this device.",
                    fontSize = 13.sp,
                    color = AuraMutedSlate,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ConversionSummaryCard(
                    summary = eligibilitySummary,
                    queueCount = conversionQueue.size,
                    queue = conversionQueue,
                    isAutoCleanupEnabled = isAutoCleanupEnabled,
                    onToggleAutoCleanup = { viewModel.toggleAutoCleanup() },
                    viewMode = viewMode,
                    onViewModeChange = { viewMode = it }
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (viewMode == ViewMode.LOGS) {
                        items(errorLogs, key = { it.id }) { error ->
                            val eligibility = remember(error) { 
                                AuraConversionAdvisor.evaluateEligibility(error) 
                            }
                            
                            PlaybackErrorCard(
                                error = error,
                                onClick = { selectedError = error },
                                isConvertible = eligibility == ConversionEligibility.CONVERTIBLE,
                                onConvert = { context -> 
                                    viewModel.startConversion(context, error)
                                }
                            )
                        }
                    } else if (viewMode == ViewMode.CONVERSION) {
                        val convertibleCandidates = eligibilitySummary.candidates.filter { 
                            it.recommendation.eligibility == ConversionEligibility.CONVERTIBLE 
                        }
                        
                        if (convertibleCandidates.isEmpty()) {
                            item {
                                Text(
                                    "No convertible files identified.",
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    textAlign = TextAlign.Center,
                                    color = AuraMutedSlate
                                )
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${selectedCandidateIds.size} files selected",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DiscoveryViolet
                                    )
                                    TextButton(
                                        onClick = { viewModel.selectAllEligible() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Select All Eligible", fontSize = 12.sp)
                                    }
                                }
                            }
                            
                            items(convertibleCandidates, key = { it.mediaId }) { candidate ->
                                ConversionCandidateCard(
                                    candidate = candidate,
                                    isSelected = selectedCandidateIds.contains(candidate.mediaId),
                                    onToggleSelection = { viewModel.toggleCandidateSelection(candidate.mediaId) }
                                )
                            }
                            
                            if (selectedCandidateIds.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { 
                                                viewModel.startBatchConversion()
                                                viewMode = ViewMode.QUEUE
                                            },
                                        color = DiscoveryViolet,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Start Batch Conversion (${selectedCandidateIds.size})",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (viewMode == ViewMode.QUEUE) {
                        if (conversionQueue.isEmpty()) {
                            item {
                                Text(
                                    "No conversion jobs in queue.",
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    textAlign = TextAlign.Center,
                                    color = AuraMutedSlate
                                )
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { viewModel.clearCompletedJobs() }) {
                                        Text("Clear Completed", fontSize = 12.sp)
                                    }
                                }
                            }
                            
                            items(conversionQueue, key = { it.id }) { job ->
                                ConversionJobCard(
                                    job = job,
                                    onCancel = { viewModel.cancelJob(job.id) },
                                    onRetry = { viewModel.retryJob(job.id) },
                                    onReplace = { viewModel.replaceOriginal(context, job.id) },
                                    onCleanupNow = { 
                                        coroutineScope.launch {
                                            val res = viewModel.performDirectCleanup(context, job.id)
                                            if (res.isFailure) {
                                                val error = res.exceptionOrNull()
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
                                                    error is RecoverableSecurityException) {
                                                    pendingAuthJobId = job.id
                                                    // Scoped storage deletion requires user consent on Android 10+
                                                    val intentSender = error.userAction.actionIntent.intentSender
                                                    authLauncher.launch(
                                                        IntentSenderRequest.Builder(intentSender).build()
                                                    )
                                                } else {
                                                    Toast.makeText(context, "Cleanup failed: ${error?.message}", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Original deleted", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // Detail View Overlay
        AnimatedVisibility(
            visible = selectedError != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            selectedError?.let { error ->
                PlaybackErrorDetailView(
                    error = error,
                    onBack = { selectedError = null },
                    onDelete = {
                        viewModel.deleteError(error.id)
                        selectedError = null
                    }
                )
            }
        }

        if (showDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDialog = false },
                title = { Text("Clear All Playback Diagnostics?", fontWeight = FontWeight.Bold) },
                text = { Text("All locally stored playback error records will be permanently deleted.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllLogs()
                            showDeleteAllDialog = false
                        }
                    ) {
                        Text("Clear All", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllDialog = false }) {
                        Text("Cancel", color = AuraMidnight)
                    }
                },
                containerColor = AuraCrispWhite,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
private fun ConversionSummaryCard(
    summary: com.example.data.ConversionEligibilitySummary,
    queueCount: Int,
    queue: List<com.example.data.db.ConversionJobEntity>,
    isAutoCleanupEnabled: Boolean,
    onToggleAutoCleanup: () -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = AuraSubtleSurface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONVERSION OPPORTUNITIES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = DiscoveryViolet,
                    letterSpacing = 1.2.sp
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-Cleanup", fontSize = 10.sp, color = AuraMutedSlate)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isAutoCleanupEnabled,
                        onCheckedChange = { onToggleAutoCleanup() },
                        modifier = Modifier.scale(0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryStat(label = "Convertible", value = summary.convertibleCount.toString(), color = DiscoveryViolet)
                SummaryStat(label = "In Queue", value = queueCount.toString(), color = AuraPurple)
                
                val cleanupCount = queue.count { it.cleanupStatus == com.example.data.OriginalCleanupStatus.CLEANUP_COMPLETED.name }
                SummaryStat(label = "Cleaned", value = cleanupCount.toString(), color = Color(0xFF10B981))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AuraSubtleBorder.copy(alpha = 0.3f))
                    .padding(4.dp)
            ) {
                ViewModeButton(
                    label = "Errors",
                    isSelected = viewMode == ViewMode.LOGS,
                    onClick = { onViewModeChange(ViewMode.LOGS) },
                    modifier = Modifier.weight(1f)
                )
                ViewModeButton(
                    label = "Convertible",
                    isSelected = viewMode == ViewMode.CONVERSION,
                    onClick = { onViewModeChange(ViewMode.CONVERSION) },
                    modifier = Modifier.weight(1f)
                )
                ViewModeButton(
                    label = "Queue",
                    isSelected = viewMode == ViewMode.QUEUE,
                    onClick = { onViewModeChange(ViewMode.QUEUE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 10.sp, color = AuraMutedSlate)
    }
}

@Composable
private fun ViewModeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AuraCrispWhite else Color.Transparent)
            .clickable { onClick() }
            .then(if (isSelected) Modifier.border(1.dp, AuraSubtleBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) AuraMidnight else AuraMutedSlate
        )
    }
}

@Composable
private fun ConversionCandidateCard(
    candidate: com.example.data.ConversionCandidate,
    isSelected: Boolean,
    onToggleSelection: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggleSelection() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) DiscoveryViolet else AuraSubtleBorder,
                shape = RoundedCornerShape(16.dp)
            ),
        color = if (isSelected) DiscoveryViolet.copy(alpha = 0.02f) else AuraSubtleSurface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                colors = CheckboxDefaults.colors(checkedColor = DiscoveryViolet)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.mediaTitle ?: candidate.fileName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuraMidnight
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${candidate.recommendation.sourceVideoCodec ?: "Unknown"} → ${candidate.recommendation.targetVideoCodec}",
                    fontSize = 12.sp,
                    color = AuraMutedSlate
                )
                Text(
                    text = "${candidate.recommendation.sourceContainer ?: "Unknown"} → ${candidate.recommendation.targetContainer}",
                    fontSize = 12.sp,
                    color = AuraMutedSlate
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = candidate.recommendation.explanation,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = AuraMidnight.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(12.dp), tint = AuraMutedSlate)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${candidate.failureCount} playback failures",
                        fontSize = 11.sp,
                        color = AuraMutedSlate
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversionJobCard(
    job: com.example.data.db.ConversionJobEntity,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onReplace: () -> Unit,
    onCleanupNow: () -> Unit
) {
    val status = remember(job.status) { com.example.data.ConversionJobStatus.valueOf(job.status) }
    val cleanupStatus = remember(job.cleanupStatus) { com.example.data.OriginalCleanupStatus.valueOf(job.cleanupStatus) }
    
    val color = when {
        status == com.example.data.ConversionJobStatus.CLEANUP_COMPLETED -> DiscoveryViolet
        status == com.example.data.ConversionJobStatus.READY_FOR_ORIGINAL_CLEANUP -> Color(0xFF10B981) // Green
        status == com.example.data.ConversionJobStatus.FAILED -> Color(0xFFEF4444)
        status == com.example.data.ConversionJobStatus.CANCELLED -> AuraMutedSlate
        else -> AuraPurple
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, AuraSubtleBorder, RoundedCornerShape(16.dp)),
        color = AuraSubtleSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.mediaTitle ?: job.fileName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AuraMidnight
                    )
                    Text(
                        text = "${job.targetVideoCodec} MP4",
                        fontSize = 11.sp,
                        color = AuraMutedSlate
                    )
                }
                
                Surface(
                    color = color.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    val statusText = when (status) {
                        com.example.data.ConversionJobStatus.REPLACING -> "REPLACING..."
                        com.example.data.ConversionJobStatus.READY_FOR_ORIGINAL_CLEANUP -> "REPLACED"
                        com.example.data.ConversionJobStatus.COMPLETED -> "READY"
                        else -> job.status
                    }
                    Text(
                        text = statusText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            if (status == com.example.data.ConversionJobStatus.REPLACING) {
                Spacer(modifier = Modifier.height(12.dp))
                val replacementStage = try { 
                    com.example.data.ReplacementStage.valueOf(job.replacementStage ?: "") 
                } catch (_: Exception) { com.example.data.ReplacementStage.NOT_STARTED }
                
                val stageText = when (replacementStage) {
                    com.example.data.ReplacementStage.PREPARING -> "Preparing..."
                    com.example.data.ReplacementStage.HANDOFF_IN_PROGRESS -> "Transferring bytes..."
                    com.example.data.ReplacementStage.VERIFYING -> "Verifying..."
                    com.example.data.ReplacementStage.RECONCILING_LIBRARY -> "Finalizing..."
                    else -> "Installing compatible version..."
                }
                
                Text(text = stageText, fontSize = 11.sp, color = DiscoveryViolet, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = DiscoveryViolet,
                    trackColor = DiscoveryViolet.copy(alpha = 0.1f)
                )
            } else if (status == com.example.data.ConversionJobStatus.READY_FOR_ORIGINAL_CLEANUP || 
                       status == com.example.data.ConversionJobStatus.CLEANUP_COMPLETED) {
                Spacer(modifier = Modifier.height(12.dp))
                val cleanupText = when (cleanupStatus) {
                    com.example.data.OriginalCleanupStatus.WAITING_FOR_STABILITY -> {
                        val remainingMs = (job.cleanupEligibilityTimestamp ?: 0L) - System.currentTimeMillis()
                        val remainingDays = remainingMs / (1000 * 60 * 60 * 24)
                        if (remainingDays > 0) {
                            "Original protected for $remainingDays more days"
                        } else {
                            val remainingHours = remainingMs / (1000 * 60 * 60)
                            if (remainingHours > 0) {
                                "Original protected for $remainingHours more hours"
                            } else {
                                "Original cleanup eligible"
                            }
                        }
                    }
                    com.example.data.OriginalCleanupStatus.CLEANUP_ELIGIBLE -> "Original ready for cleanup"
                    com.example.data.OriginalCleanupStatus.CLEANUP_IN_PROGRESS -> "Removing original..."
                    com.example.data.OriginalCleanupStatus.CLEANUP_COMPLETED -> "Original removed safely"
                    com.example.data.OriginalCleanupStatus.CLEANUP_FAILED -> "Cleanup failed: ${job.lastCleanupError}"
                    com.example.data.OriginalCleanupStatus.CLEANUP_BLOCKED -> "Cleanup blocked: ${job.lastCleanupError}"
                    else -> "Replacement verified"
                }
                Text(text = cleanupText, fontSize = 11.sp, color = if (cleanupStatus == com.example.data.OriginalCleanupStatus.CLEANUP_FAILED) Color(0xFFEF4444) else AuraMutedSlate)
            } else if (status != com.example.data.ConversionJobStatus.COMPLETED &&
                status != com.example.data.ConversionJobStatus.FAILED && 
                status != com.example.data.ConversionJobStatus.CANCELLED) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { job.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = AuraPurple,
                    trackColor = AuraPurple.copy(alpha = 0.1f)
                )
            }
            
            if (status == com.example.data.ConversionJobStatus.FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = job.errorMessage ?: "Unknown error",
                    fontSize = 11.sp,
                    color = Color(0xFFEF4444),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status == com.example.data.ConversionJobStatus.COMPLETED || 
                    (status == com.example.data.ConversionJobStatus.FAILED && 
                     job.failureStage == com.example.data.ConversionStage.COMPLETE.name)) {
                    Button(
                        onClick = onReplace,
                        colors = ButtonDefaults.buttonColors(containerColor = DiscoveryViolet),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text("Finish Replacement", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (status == com.example.data.ConversionJobStatus.READY_FOR_ORIGINAL_CLEANUP ||
                    cleanupStatus == com.example.data.OriginalCleanupStatus.WAITING_FOR_STABILITY ||
                    cleanupStatus == com.example.data.OriginalCleanupStatus.CLEANUP_ELIGIBLE ||
                    cleanupStatus == com.example.data.OriginalCleanupStatus.CLEANUP_FAILED ||
                    cleanupStatus == com.example.data.OriginalCleanupStatus.CLEANUP_BLOCKED) {
                    
                    var showDeleteConfirm by remember { mutableStateOf(false) }
                    
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text("Delete Original", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Delete Original File?", fontWeight = FontWeight.Bold) },
                            text = { Text("The converted replacement is active and verified. Deleting the original permanently removes the old version from your device.") },
                            confirmButton = {
                                TextButton(onClick = { 
                                    onCleanupNow()
                                    showDeleteConfirm = false
                                }) {
                                    Text("Delete Original", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text("Cancel", color = AuraMidnight)
                                }
                            },
                            containerColor = AuraCrispWhite,
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                }
                
                if (status == com.example.data.ConversionJobStatus.FAILED || status == com.example.data.ConversionJobStatus.CANCELLED) {
                    TextButton(onClick = onRetry) {
                        Text("Retry", fontSize = 12.sp, color = DiscoveryViolet)
                    }
                }
                if (status == com.example.data.ConversionJobStatus.QUEUED || 
                    status == com.example.data.ConversionJobStatus.PREPARING || 
                    status == com.example.data.ConversionJobStatus.CONVERTING ||
                    status == com.example.data.ConversionJobStatus.REPLACING) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", fontSize = 12.sp, color = AuraMutedSlate)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDiagnosticsState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            color = AuraSubtleSurface,
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CheckCircleOutline,
                    contentDescription = null,
                    tint = DiscoveryViolet,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Playback Errors",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = AuraMidnight
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Aura hasn't recorded any playback failures on this device.",
            fontSize = 14.sp,
            color = AuraMutedSlate,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlaybackErrorCard(
    error: PlaybackErrorLogEntity,
    isConvertible: Boolean = false,
    onConvert: (Context) -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(1.dp, AuraSubtleBorder, RoundedCornerShape(16.dp)),
        color = AuraSubtleSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = error.diagnosticSummary ?: "Playback Error",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AuraMidnight
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error.mediaTitle ?: error.fileName ?: "Unknown Media",
                        fontSize = 13.sp,
                        color = AuraMidnight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (error.occurrenceCount > 1) {
                    Surface(
                        color = DiscoveryViolet.copy(alpha = 0.1f),
                        shape = CircleShape,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "x${error.occurrenceCount}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DiscoveryViolet,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = AuraMutedSlate,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimestamp(error.timestamp),
                    fontSize = 11.sp,
                    color = AuraMutedSlate
                )
                if (error.errorCodeName != null) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = error.errorCodeName,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (isConvertible) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onConvert(context) },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AuraPurple),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Convert to Compatible Format", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ConversionProgressCard(
    stage: ConversionStage,
    progress: Int,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = AuraPurple.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraPurple.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (stage) {
                        ConversionStage.PREPARING -> "Preparing source..."
                        ConversionStage.CONVERTING -> "Converting to H.264..."
                        ConversionStage.VALIDATING -> "Validating output..."
                        ConversionStage.TESTING_PLAYBACK -> "Testing playback..."
                        else -> "Processing..."
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuraPurple
                )
                Text(
                    text = "$progress%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = AuraPurple
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = AuraPurple,
                trackColor = AuraPurple.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Cancel", color = AuraMutedSlate, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ConversionResultCard(
    result: SingleFileConversionResult,
    onDismiss: () -> Unit
) {
    val isSuccess = result.status == ConversionStatus.CONVERTED
    val color = if (isSuccess) DiscoveryViolet else Color(0xFFEF4444)

    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = color.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isSuccess) "Conversion Complete" else "Conversion Failed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            
            if (isSuccess) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "A compatible replacement candidate has been created successfully.",
                    fontSize = 13.sp,
                    color = AuraMidnight
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ResultRow("Target", "H.264 / AAC MP4")
                    ResultRow("Playback Validation", "Passed")
                    ResultRow("Size Change", "${(result.compressionRatio * 100).toInt()}% of original")
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = result.errorMessage ?: "An unexpected error occurred during transcoding.",
                    fontSize = 13.sp,
                    color = Color(0xFFEF4444)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Dismiss", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row {
        Text(text = "• $label: ", fontSize = 12.sp, color = AuraMutedSlate)
        Text(text = value, fontSize = 12.sp, color = AuraMidnight, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PlaybackErrorDetailView(
    error: PlaybackErrorLogEntity,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AuraCrispWhite
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AuraTopBar(
                title = "Error Details",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AuraMidnight)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        copyToClipboard(context, formatDiagnosticForCopy(error))
                        Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Details", tint = DiscoveryViolet)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Playback diagnostics are stored locally on this device.",
                    fontSize = 12.sp,
                    color = AuraMutedSlate,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                DiagnosticSection("Overview") {
                    DiagnosticRow("Summary", error.diagnosticSummary)
                    DiagnosticRow("First Occurrence", formatTimestampFull(error.timestamp))
                    if (error.occurrenceCount > 1) {
                        DiagnosticRow("Last Occurrence", formatTimestampFull(error.lastOccurrenceTimestamp))
                        DiagnosticRow("Total Count", error.occurrenceCount.toString())
                    }
                    DiagnosticRow("Session ID", error.sessionId)
                    DiagnosticRow("Recovery Attempted", error.recoveryAttempted.toString())
                    DiagnosticRow("Recovery Successful", error.recoverySuccessful?.toString() ?: "N/A")
                }

                DiagnosticSection("Media") {
                    DiagnosticRow("Title", error.mediaTitle)
                    DiagnosticRow("Filename", error.fileName)
                    DiagnosticRow("Media ID", error.mediaItemId)
                    DiagnosticRow("URI", error.mediaUri)
                    DiagnosticRow("MIME Type", error.mimeType)
                    DiagnosticRow("Duration", error.durationMs?.let { "${it}ms" })
                    DiagnosticRow("Playback Position", error.playbackPositionMs?.let { "${it}ms" })
                    DiagnosticRow("Local File", error.isLocalFile.toString())
                }

                DiagnosticSection("Playback") {
                    DiagnosticRow("State", error.playbackState)
                    DiagnosticRow("Play When Ready", error.playWhenReady?.toString())
                    DiagnosticRow("Renderer Index", error.rendererIndex?.toString())
                    DiagnosticRow("Renderer Name", error.rendererName)
                }

                if (error.codecName != null || error.codecMimeType != null) {
                    DiagnosticSection("Decoder / Codec") {
                        DiagnosticRow("Codec Name", error.codecName)
                        DiagnosticRow("Codec MIME", error.codecMimeType)
                    }
                }

                DiagnosticSection("Error") {
                    DiagnosticRow("Code", error.errorCode?.toString())
                    DiagnosticRow("Code Name", error.errorCodeName)
                    DiagnosticRow("Message", error.errorMessage)
                    DiagnosticRow("Exception Class", error.exceptionClass)
                }

                if (error.causeChain != null) {
                    DiagnosticSection("Cause Chain") {
                        val chains = error.causeChain.split(" -> ")
                        chains.forEachIndexed { index, chain ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (index > 0) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = AuraMutedSlate,
                                        modifier = Modifier.size(14.dp).padding(end = 8.dp)
                                    )
                                }
                                Text(
                                    text = chain,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AuraMidnight
                                )
                            }
                        }
                    }
                }

                if (error.stackTrace != null) {
                    var expanded by remember { mutableStateOf(false) }
                    DiagnosticSection("Stack Trace") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .border(1.dp, AuraSubtleBorder, RoundedCornerShape(8.dp)),
                            color = AuraSubtleSurface,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (expanded) "Hide Stack Trace" else "Show Stack Trace",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DiscoveryViolet
                                    )
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = DiscoveryViolet,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (expanded) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = error.stackTrace,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = AuraMidnight,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())
                                    )
                                }
                            }
                        }
                    }
                }

                DiagnosticSection("Device") {
                    DiagnosticRow("Manufacturer", error.deviceManufacturer)
                    DiagnosticRow("Model", error.deviceModel)
                    DiagnosticRow("Android Version", error.androidVersion)
                    DiagnosticRow("SDK Int", error.sdkInt?.toString())
                    DiagnosticRow("App Version", error.appVersion)
                    DiagnosticRow("Media3 Version", error.media3Version)
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Playback Error?", fontWeight = FontWeight.Bold) },
                text = { Text("This diagnostic record will be permanently removed from this device.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDelete()
                            showDeleteConfirm = false
                        }
                    ) {
                        Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel", color = AuraMidnight)
                    }
                },
                containerColor = AuraCrispWhite,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
private fun DiagnosticSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = AuraMutedSlate,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AuraMutedSlate
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "Unavailable",
            fontSize = 13.sp,
            color = AuraMidnight,
            lineHeight = 18.sp
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    
    return if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && 
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
    }
}

private fun formatTimestampFull(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Playback Diagnostic", text)
    clipboard.setPrimaryClip(clip)
}

private fun formatDiagnosticForCopy(error: PlaybackErrorLogEntity): String {
    val sb = StringBuilder()
    sb.append("Aura Media Player\n")
    sb.append("Playback Diagnostic\n\n")
    
    sb.append("Summary: ${error.diagnosticSummary ?: "Unavailable"}\n")
    sb.append("First Occurrence: ${formatTimestampFull(error.timestamp)}\n")
    if (error.occurrenceCount > 1) {
        sb.append("Last Occurrence: ${formatTimestampFull(error.lastOccurrenceTimestamp)}\n")
        sb.append("Occurrence Count: ${error.occurrenceCount}\n")
    }
    sb.append("Media: ${error.mediaTitle ?: "Unavailable"}\n")
    sb.append("Filename: ${error.fileName ?: "Unavailable"}\n")
    sb.append("Media ID: ${error.mediaItemId ?: "Unavailable"}\n")
    sb.append("URI: ${error.mediaUri ?: "Unavailable"}\n")
    sb.append("MIME Type: ${error.mimeType ?: "Unavailable"}\n")
    sb.append("Playback Position: ${error.playbackPositionMs?.let { "${it}ms" } ?: "Unavailable"}\n")
    sb.append("Playback State: ${error.playbackState ?: "Unavailable"}\n\n")
    
    sb.append("Error Code: ${error.errorCode ?: "Unavailable"}\n")
    sb.append("Error Code Name: ${error.errorCodeName ?: "Unavailable"}\n")
    sb.append("Error Message: ${error.errorMessage ?: "Unavailable"}\n")
    sb.append("Exception: ${error.exceptionClass ?: "Unavailable"}\n\n")
    
    sb.append("Codec: ${error.codecName ?: "Unavailable"}\n")
    sb.append("Renderer: ${error.rendererName ?: "Unavailable"}\n\n")
    
    sb.append("Device: ${error.deviceManufacturer} ${error.deviceModel}\n")
    sb.append("Android: ${error.androidVersion}\n")
    sb.append("SDK: ${error.sdkInt}\n")
    sb.append("App Version: ${error.appVersion}\n")
    sb.append("Media3 Version: ${error.media3Version}\n\n")
    
    sb.append("Cause Chain:\n${error.causeChain ?: "Unavailable"}\n\n")
    sb.append("Stack Trace:\n${error.stackTrace ?: "Unavailable"}\n")
    
    return sb.toString()
}
