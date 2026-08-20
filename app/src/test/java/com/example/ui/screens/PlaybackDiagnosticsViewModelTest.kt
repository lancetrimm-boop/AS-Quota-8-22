package com.example.ui.screens

import com.example.data.ConversionEligibility
import com.example.data.PlaybackErrorLogRepository
import com.example.data.db.PlaybackErrorLogDao
import com.example.data.db.PlaybackErrorLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackDiagnosticsViewModelTest {

    private val dao: PlaybackErrorLogDao = mock()
    private val repository = PlaybackErrorLogRepository(dao)
    private lateinit var viewModel: PlaybackDiagnosticsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val logsFlow = MutableStateFlow<List<PlaybackErrorLogEntity>>(emptyList())
        whenever(dao.observeRecentErrors()).thenReturn(logsFlow)
        viewModel = PlaybackDiagnosticsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testGroupingRepeatedErrors() = runTest {
        val error1 = PlaybackErrorLogEntity(
            id = 1,
            mediaItemId = "media_1",
            mediaUri = "uri_1",
            errorCode = 1001, // ErrorCode doesn't strictly matter for grouping logic but for advisor
            occurrenceCount = 2,
            lastOccurrenceTimestamp = 1000L
        )
        val error2 = PlaybackErrorLogEntity(
            id = 2,
            mediaItemId = "media_1",
            mediaUri = "uri_1",
            errorCode = 1001,
            occurrenceCount = 1,
            lastOccurrenceTimestamp = 2000L
        )
        
        val logsFlow = MutableStateFlow(listOf(error2, error1))
        whenever(dao.observeRecentErrors()).thenReturn(logsFlow)
        
        // Re-init VM to pick up new flow mock
        viewModel = PlaybackDiagnosticsViewModel(repository)
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        val summary = viewModel.eligibilitySummary.first()
        assertEquals(1, summary.uniqueFiles)
        assertEquals(3, summary.totalErrors)
        assertEquals("media_1", summary.candidates[0].mediaId)
    }

    @Test
    fun testSelection() = runTest {
        // Selection is just UI state in VM, no need for complex flows
        viewModel.toggleCandidateSelection("media_1")
        assertEquals(setOf("media_1"), viewModel.selectedCandidateIds.value)
        
        viewModel.toggleCandidateSelection("media_1")
        assertEquals(emptySet<String>(), viewModel.selectedCandidateIds.value)
    }
}
