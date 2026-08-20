package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.IntelligenceRepository
import com.example.data.intelligence.IntelligenceSnapshotReport
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * State for the user-facing Intelligence Dashboard.
 */
data class IntelligenceDashboardState(
    val report: IntelligenceSnapshotReport? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel for Aura's Phase 4 Intelligence Dashboard.
 * 
 * DESIGN PRINCIPLE:
 * - Local data only.
 * - Traceable metric provenance.
 * - Framed as "Personalization Confidence".
 */
class IntelligenceDashboardViewModel(
    private val repository: IntelligenceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntelligenceDashboardState())
    val uiState: StateFlow<IntelligenceDashboardState> = _uiState.asStateFlow()

    init {
        observeIntelligenceSnapshots()
    }

    private fun observeIntelligenceSnapshots() {
        viewModelScope.launch {
            repository.snapshotReport.collect { report ->
                if (report != null) {
                    _uiState.update { it.copy(report = report, isLoading = false) }
                }
            }
        }
    }

    /**
     * Manually triggers a refresh of the intelligence snapshots.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.refreshIntelligenceSnapshot()
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
