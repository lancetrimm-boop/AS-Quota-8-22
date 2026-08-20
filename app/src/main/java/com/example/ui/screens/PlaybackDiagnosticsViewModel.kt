package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compatibility.AuraMediaTranscoder
import com.example.data.*
import com.example.data.db.ConversionJobEntity
import com.example.data.db.PlaybackErrorLogEntity
import com.example.data.intelligence.AuraConversionAdvisor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlaybackDiagnosticsViewModel(
    private val repository: PlaybackErrorLogRepository,
    private val queueRepository: ConversionQueueRepository? = null
) : ViewModel() {

    // Conversion state for the active single-file prototype
    private val _conversionStage = mutableStateOf(ConversionStage.IDLE)
    val conversionStage: State<ConversionStage> = _conversionStage

    private val _conversionProgress = mutableStateOf(0)
    val conversionProgress: State<Int> = _conversionProgress

    private val _lastResult = mutableStateOf<SingleFileConversionResult?>(null)
    val lastResult: State<SingleFileConversionResult?> = _lastResult

    // Selection state for batch conversion candidates
    private val _selectedCandidateIds = mutableStateOf(setOf<String>())
    val selectedCandidateIds: State<Set<String>> = _selectedCandidateIds

    val errorLogs: StateFlow<List<PlaybackErrorLogEntity>> = repository.observeRecentErrors()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Observe the persistent conversion queue.
     */
    val conversionQueue: StateFlow<List<ConversionJobEntity>> = queueRepository?.observeAllJobs()
        ?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        ) ?: MutableStateFlow(emptyList())

    /**
     * Derived summary of conversion eligibility across all recorded errors.
     */
    val eligibilitySummary: StateFlow<ConversionEligibilitySummary> = errorLogs
        .map { logs ->
            analyzeEligibility(logs)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConversionEligibilitySummary(0, 0, 0, 0, 0, emptyList())
        )

    private fun analyzeEligibility(logs: List<PlaybackErrorLogEntity>): ConversionEligibilitySummary {
        // Group errors by mediaItemId (falling back to mediaUri if ID is null)
        val groups = logs.groupBy { it.mediaItemId ?: it.mediaUri ?: "unknown" }
        
        val candidates = groups.mapNotNull { (id, entries) ->
            if (id == "unknown") return@mapNotNull null
            
            // Take the most recent entry for detailed analysis
            val latestEntry = entries.first()
            val uri = latestEntry.mediaUri ?: return@mapNotNull null
            
            val recommendation = AuraConversionAdvisor.createRecommendation(latestEntry)
            
            ConversionCandidate(
                mediaId = id,
                sourceUri = Uri.parse(uri),
                fileName = latestEntry.fileName ?: "Unknown",
                mediaTitle = latestEntry.mediaTitle,
                recommendation = recommendation,
                failureCount = entries.sumOf { it.occurrenceCount },
                lastFailureTimestamp = entries.maxOf { it.lastOccurrenceTimestamp }
            )
        }

        return ConversionEligibilitySummary(
            totalErrors = logs.sumOf { it.occurrenceCount },
            uniqueFiles = candidates.size,
            convertibleCount = candidates.count { it.recommendation.eligibility == ConversionEligibility.CONVERTIBLE },
            notRecommendedCount = candidates.count { it.recommendation.eligibility == ConversionEligibility.NOT_RECOMMENDED },
            unavailableCount = candidates.count { it.recommendation.eligibility == ConversionEligibility.UNAVAILABLE },
            candidates = candidates.sortedByDescending { it.lastFailureTimestamp }
        )
    }

    fun toggleCandidateSelection(mediaId: String) {
        val current = _selectedCandidateIds.value
        _selectedCandidateIds.value = if (current.contains(mediaId)) {
            current - mediaId
        } else {
            current + mediaId
        }
    }

    fun selectAllEligible() {
        val convertible = eligibilitySummary.value.candidates
            .filter { it.recommendation.eligibility == ConversionEligibility.CONVERTIBLE }
            .map { it.mediaId }
            .toSet()
        _selectedCandidateIds.value = convertible
    }

    fun clearSelection() {
        _selectedCandidateIds.value = emptySet()
    }

    fun startBatchConversion() {
        val candidates = eligibilitySummary.value.candidates
            .filter { _selectedCandidateIds.value.contains(it.mediaId) }
        
        if (candidates.isNotEmpty()) {
            viewModelScope.launch {
                queueRepository?.enqueueConversions(candidates)
                _selectedCandidateIds.value = emptySet()
            }
        }
    }

    fun cancelJob(jobId: Long) {
        viewModelScope.launch {
            queueRepository?.cancelJob(jobId)
        }
    }

    fun retryJob(jobId: Long) {
        viewModelScope.launch {
            queueRepository?.retryJob(jobId)
        }
    }

    fun clearCompletedJobs() {
        viewModelScope.launch {
            queueRepository?.clearCompleted()
        }
    }

    fun replaceOriginal(context: Context, jobId: Long) {
        viewModelScope.launch {
            queueRepository?.replaceOriginal(context, jobId)
        }
    }

    fun cleanupOriginalNow(jobId: Long) {
        viewModelScope.launch {
            queueRepository?.cleanupOriginalNow(jobId)
        }
    }

    /**
     * Attempts to perform the cleanup directly from the UI context.
     * This allows handling of SecurityExceptions that require user interaction.
     */
    suspend fun performDirectCleanup(context: Context, jobId: Long): Result<Unit> {
        return queueRepository?.performDirectCleanup(context, jobId) 
            ?: Result.failure(Exception("Queue repository not available"))
    }

    fun startConversion(context: Context, error: PlaybackErrorLogEntity) {
        val uriStr = error.mediaUri ?: return
        val uri = Uri.parse(uriStr)
        
        _lastResult.value = null
        
        viewModelScope.launch {
            val result = AuraMediaTranscoder.transcodeAndValidate(context, uri) { stage, progress ->
                _conversionStage.value = stage
                _conversionProgress.value = progress
            }
            _lastResult.value = result
        }
    }

    fun resetConversion() {
        _conversionStage.value = ConversionStage.IDLE
        _conversionProgress.value = 0
        _lastResult.value = null
    }

    private val _isAutoCleanupEnabled = mutableStateOf(true)
    val isAutoCleanupEnabled: State<Boolean> = _isAutoCleanupEnabled

    init {
        loadAutoCleanupPref()
    }

    private fun loadAutoCleanupPref() {
        viewModelScope.launch {
            _isAutoCleanupEnabled.value = queueRepository?.isAutoCleanupEnabled() ?: true
        }
    }

    fun toggleAutoCleanup() {
        val newValue = !_isAutoCleanupEnabled.value
        _isAutoCleanupEnabled.value = newValue
        viewModelScope.launch {
            queueRepository?.setAutoCleanupEnabled(newValue)
        }
    }

    fun deleteError(errorId: Long) {
        viewModelScope.launch {
            repository.deleteError(errorId)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
        }
    }
}
