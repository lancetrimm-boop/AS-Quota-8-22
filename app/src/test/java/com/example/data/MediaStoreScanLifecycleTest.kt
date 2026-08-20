package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.MediaEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaStoreScanLifecycleTest {

    private lateinit var context: Context
    private lateinit var database: AuraDatabase
    private lateinit var repository: MediaRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Grant permissions for Robolectric
        val app = context as android.app.Application
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            shadowApp.grantPermissions(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            shadowApp.grantPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        repository = MediaRepository(dispatcher = testDispatcher)
        
        // Inject database via reflection
        val dbField = MediaRepository::class.java.getDeclaredField("database")
        dbField.isAccessible = true
        dbField.set(repository, database)
        
        // Set database state to READY
        val stateField = MediaRepository::class.java.getDeclaredField("_databaseState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (stateField.get(repository) as MutableStateFlow<DatabaseState>).value = DatabaseState.READY
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `test scan session ID is generated and unique`() = runTest {
        repository.scanLocalMedia(context)
        val session1 = repository.scanProgress.value.scanSessionId
        assertTrue("Session ID should be positive", session1 > 0)

        // Force a delay to ensure timestamp would be different if not immediate
        kotlinx.coroutines.delay(kotlin.time.Duration.parse("10ms"))
        
        repository.scanLocalMedia(context)
        val session2 = repository.scanProgress.value.scanSessionId
        assertNotEquals("Subsequent scans should have unique session IDs", session1, session2)
    }

    @Test
    fun `test cancellation before reconciliation prevents deletion`() = runTest {
        // Setup: DB has 1 item
        database.mediaDao().insert(MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1"))
        
        // Start scan
        val job = launch {
            repository.scanLocalMedia(context)
        }
        
        // Use repository's own cancel mechanism
        repository.cancelScan()
        job.join()
        
        // Verify: Item remains
        assertNotNull("Record should not be deleted if scan was cancelled", database.mediaDao().getMediaById("local_vid_1"))
        assertEquals(1, database.mediaDao().getCount())
        
        val progress = repository.scanProgress.value
        assertFalse("Scan should not be marked complete after cancellation", progress.isComplete)
        assertEquals("Scan cancelled", progress.statusText)
    }

    @Test
    fun `test reconcileDeletions respects cancellation during loop`() = runTest {
        // Setup: DB has items
        val item1 = MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1")
        database.mediaDao().insert(item1)

        // Mock a result that would trigger deletions
        val result = DiscoveryResult.Complete(
            entities = emptyList(),
            discoveredIds = emptySet(), // Everything "deleted"
            scannedVolumes = setOf("external"),
            scannedMediaTypes = setOf("VIDEO", "PHOTO")
        )

        // Start reconciliation in a job and cancel it immediately
        val job = launch {
            repository.reconcileDeletions(result)
        }
        job.cancelAndJoin()

        // Verify: Item remains because loop check !isActive should have triggered
        assertNotNull("Reconciliation loop should abort on cancellation", database.mediaDao().getMediaById("local_vid_1"))
    }

    @Test
    fun `test authoritative scan result cannot authorize deletion if incomplete`() = runTest {
        // Setup: DB has 1 item
        database.mediaDao().insert(MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1"))
        
        // Mock Incomplete result
        val result = DiscoveryResult.Incomplete("Failed", ScanError.QUERY_FAILURE)
        
        repository.reconcileDeletions(result)
        
        // Verify: No deletion
        assertNotNull(database.mediaDao().getMediaById("local_vid_1"))
        assertEquals(1, database.mediaDao().getCount())
    }
}
