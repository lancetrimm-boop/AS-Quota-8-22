package com.example.data.cleanup

import android.app.Activity
import com.example.data.MediaItem
import com.example.data.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SafeDeleteManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: MediaRepository
    private lateinit var deleteManager: SafeDeleteManager

    @Before
    fun setup() {
        repository = mock()
        deleteManager = SafeDeleteManager(repository)
    }

    @Test
    fun testRequestDeletion_NonMediaStoreUris_ConfirmsImmediately() = runTest {
        // Items with non-media authority
        val item = MediaItem(id = "test-1", title = "Test", mediaType = "PHOTO", uriPath = "file:///sdcard/test.jpg")
        
        deleteManager.requestDeletion(mock(), listOf(item), emptyList(), mock())
        
        assertEquals(DeletionState.CONFIRMED, deleteManager.deletionState.value)
        verify(repository).deleteMediaItem("test-1")
    }

    @Test
    fun testHandleDeletionResult_Cancelled_ClearsState() = runTest {
        val item = MediaItem(id = "test-1", title = "Test", mediaType = "PHOTO", uriPath = "content://media/1")
        
        // We simulate PENDING state by calling handleDeletionResult(RESULT_CANCELED)
        // In a real scenario it would be set by requestDeletion
        deleteManager.handleDeletionResult(Activity.RESULT_CANCELED)
        
        assertEquals(DeletionState.CANCELLED, deleteManager.deletionState.value)
        verify(repository, never()).deleteMediaItem(any())
    }
}
