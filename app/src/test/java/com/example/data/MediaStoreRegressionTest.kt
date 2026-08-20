package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.MediaEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaStoreRegressionTest {

    private lateinit var context: Context
    private lateinit var database: AuraDatabase
    private lateinit var repository: MediaRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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

    /**
     * STEP 5: TEST PERMISSION CHANGES
     */

    @Test
    fun `test permission denied before scan prevents reconciliation`() = runTest {
        // Setup: DB has items
        database.mediaDao().insert(MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1"))
        
        // Deny permissions
        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.denyPermissions(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_IMAGES)

        val result = repository.scanLocalMedia(context)
        
        assertFalse("Scan should return false (no changes) if permissions denied", result)
        assertEquals(ScanError.PERMISSION_DENIED, repository.scanProgress.value.errorCode)
        assertNotNull("Item should remain in DB", database.mediaDao().getMediaById("local_vid_1"))
    }

    /**
     * STEP 6 & 10: TEST QUERY FAILURE MODES & TRANSIENT NULL
     */

    @Test
    fun `test null image query invalidates scan and prevents deletion`() = runTest {
        // This test simulates the case where video discovery succeeds but image discovery returns null (simulated via Incomplete result)
        database.mediaDao().insert(MediaEntity(id = "local_img_1", title = "I1", mediaType = "PHOTO", uriPath = "content://media/external/images/media/1"))
        database.mediaDao().insert(MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1"))

        // Discovery Result: Incomplete due to image query failure
        val result = DiscoveryResult.Incomplete("Image query returned null", ScanError.QUERY_FAILURE)
        
        repository.reconcileDeletions(result)
        
        assertEquals("No items should be deleted on incomplete scan", 2, database.mediaDao().getCount())
    }

    /**
     * STEP 11: TEST GENUINE MASS DELETION
     */

    @Test
    fun `test genuine 99 percent deletion is supported`() = runTest {
        // Setup: DB has 1000 items
        val items = (1..1000).map { i ->
            MediaEntity(id = "local_vid_$i", title = "V$i", mediaType = "VIDEO", uriPath = "content://media/external/video/media/$i")
        }
        database.mediaDao().insertAll(items)
        assertEquals(1000, database.mediaDao().getCount())

        // Scan: Authoritative COMPLETE scan finds only 10 items (99% deletion)
        val discoveredIds = (1..10).map { "local_vid_$it" }.toSet()
        val result = DiscoveryResult.Complete(
            entities = emptyList(),
            discoveredIds = discoveredIds,
            scannedVolumes = setOf("external"),
            scannedMediaTypes = setOf("VIDEO", "PHOTO")
        )

        repository.reconcileDeletions(result)

        assertEquals("Exactly 10 items should remain", 10, database.mediaDao().getCount())
        assertTrue("Item 1 should remain", database.mediaDao().getMediaById("local_vid_1") != null)
        assertNull("Item 1000 should be deleted", database.mediaDao().getMediaById("local_vid_1000"))
    }

    /**
     * STEP 12: TEST DATABASE METADATA PRESERVATION
     */

    @Test
    fun `test metadata preservation on incomplete scan`() = runTest {
        // Setup: Item with user metadata
        val item = MediaEntity(
            id = "local_vid_1", 
            title = "V1", 
            mediaType = "VIDEO", 
            uriPath = "content://media/external/video/media/1",
            rating = 5f,
            playCount = 10,
            isFavorite = true
        )
        database.mediaDao().insert(item)

        // Scan fails
        val result = DiscoveryResult.Incomplete("Query failed", ScanError.QUERY_FAILURE)
        repository.reconcileDeletions(result)

        // Verify metadata is intact
        val preserved = database.mediaDao().getMediaById("local_vid_1")
        assertNotNull(preserved)
        assertEquals(5f, preserved!!.rating)
        assertEquals(10, preserved.playCount)
        assertTrue(preserved.isFavorite)
    }

    /**
     * STEP 9: TEST UNMOUNTED VOLUMES (STRICT)
     */
    @Test
    fun `test volume unmount recovery cycle`() = runTest {
        // 1. Initial State: Volume A and B present
        val itemA = MediaEntity(id = "local_vid_A", title = "A", mediaType = "VIDEO", uriPath = "content://media/volume_a/video/media/1")
        val itemB = MediaEntity(id = "local_vid_B", title = "B", mediaType = "VIDEO", uriPath = "content://media/volume_b/video/media/2")
        database.mediaDao().insertAll(listOf(itemA, itemB))

        // 2. Scan: Volume B is unmounted (not in scannedVolumes)
        val result1 = DiscoveryResult.Complete(
            entities = emptyList(),
            discoveredIds = setOf("local_vid_A"),
            scannedVolumes = setOf("volume_a"),
            scannedMediaTypes = setOf("VIDEO", "PHOTO")
        )
        repository.reconcileDeletions(result1)
        
        // Both must remain
        assertNotNull(database.mediaDao().getMediaById("local_vid_A"))
        assertNotNull(database.mediaDao().getMediaById("local_vid_B"))

        // 3. Volume B returns, but user deleted it
        val result2 = DiscoveryResult.Complete(
            entities = emptyList(),
            discoveredIds = setOf("local_vid_A"), // B is still missing but now B is in scannedVolumes
            scannedVolumes = setOf("volume_a", "volume_b"),
            scannedMediaTypes = setOf("VIDEO", "PHOTO")
        )
        repository.reconcileDeletions(result2)

        // Now B should be deleted
        assertNotNull(database.mediaDao().getMediaById("local_vid_A"))
        assertNull(database.mediaDao().getMediaById("local_vid_B"))
    }

    /**
     * STEP 7: TEST CANCELLATION (DETERMINISTIC)
     */
    @Test
    fun `test cancellation before reconciliation loop`() = runTest {
        database.mediaDao().insert(MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1"))
        
        val result = DiscoveryResult.Complete(
            entities = emptyList(),
            discoveredIds = emptySet(),
            scannedVolumes = setOf("external"),
            scannedMediaTypes = setOf("VIDEO", "PHOTO")
        )

        // Simulate cancellation by using a cancelled context
        kotlinx.coroutines.withContext(kotlinx.coroutines.Job().apply { cancel() }) {
            repository.reconcileDeletions(result)
        }

        assertEquals("No items should be deleted if coroutine is cancelled", 1, database.mediaDao().getCount())
    }

    /**
     * STEP 6: TEST QUERY FAILURE MODES
     */

    @Test
    fun `test SecurityException during query invalidates scan`() = runTest {
        // Setup: DB has item
        database.mediaDao().insert(MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1"))

        // Simulate discovery result with SecurityException (passed as Incomplete)
        val result = DiscoveryResult.Incomplete("SecurityException", ScanError.STORAGE_ACCESS_FAILED, SecurityException("Denied"))
        
        repository.reconcileDeletions(result)
        
        assertEquals("Data must be preserved on SecurityException", 1, database.mediaDao().getCount())
    }

    @Test
    fun `test query failure on one volume preserves other volume items`() = runTest {
        // Setup: DB has items on two volumes
        val item1 = MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/vol_a/video/media/1")
        val item2 = MediaEntity(id = "local_vid_2", title = "V2", mediaType = "VIDEO", uriPath = "content://media/vol_b/video/media/2")
        database.mediaDao().insertAll(listOf(item1, item2))

        // Scan: vol_a succeeded, vol_b failed.
        // Even if we have partial data, we must return Incomplete for the whole session.
        val result = DiscoveryResult.Incomplete("vol_b failed", ScanError.QUERY_FAILURE)
        
        repository.reconcileDeletions(result)
        
        assertEquals("Everything preserved on partial failure", 2, database.mediaDao().getCount())
    }

    @Test
    fun `test permission state changes between scans`() = runTest {
        val shadowApp = shadowOf(context as android.app.Application)
        database.mediaDao().insert(MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1"))

        // 1. Scan with permission -> OK
        shadowApp.grantPermissions(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_IMAGES)
        // (Mocking successful scan result)
        repository.reconcileDeletions(DiscoveryResult.Complete(emptyList(), setOf("local_vid_1"), setOf("external"), setOf("VIDEO")))
        assertEquals(1, database.mediaDao().getCount())

        // 2. Permission revoked
        shadowApp.denyPermissions(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_IMAGES)
        
        // 3. New scan session
        val scanResult = repository.scanLocalMedia(context)
        assertFalse(scanResult)
        assertEquals(ScanError.PERMISSION_DENIED, repository.scanProgress.value.errorCode)
        
        // 4. Verification: Authorization from previous scan did not leak/allow deletion
        assertEquals(1, database.mediaDao().getCount())
    }

    /**
     * FORENSIC: TEST PROCESS PENDING MEDIA SAFETY
     */
    @Test
    fun `test processPendingMedia preserves unreadable item if volume missing from scanned set`() = runTest {
        // Setup: DB has a pending item on vol_b
        val item = MediaEntity(
            id = "local_vid_B", 
            title = "B", 
            mediaType = "VIDEO", 
            uriPath = "content://media/vol_b/video/media/1",
            compatibilityStatus = CompatibilityStatus.ANALYSIS_PENDING.name
        )
        database.mediaDao().insert(item)

        // Mock processPendingMedia call (using reflection to access private method)
        val method = MediaRepository::class.java.getDeclaredMethod(
            "processPendingMedia", 
            Context::class.java, 
            Long::class.java, 
            Set::class.java
        )
        method.isAccessible = true
        
        // Volume B is NOT in scannedVolumes
        method.invoke(repository, context, 123L, setOf("vol_a"))

        // Verify: Item preserved in DB despite being (mock) unreadable during analysis 
        // (AuraMediaCompatibilityEngine.analyzeMedia will return UNREADABLE because file doesn't exist in Robolectric)
        assertNotNull(database.mediaDao().getMediaById("local_vid_B"))
    }

    /**
     * FORENSIC: TEST RECONCILE EXISTING MEDIA SAFETY
     */
    @Test
    fun `test reconcileExistingMedia preserves unreadable item if volume unmounted`() = runTest {
        // Setup: DB has item on vol_b that is ANALYSIS_PENDING
        val item = MediaEntity(
            id = "local_vid_B", 
            title = "B", 
            mediaType = "VIDEO", 
            uriPath = "content://media/vol_b/video/media/1",
            compatibilityStatus = CompatibilityStatus.ANALYSIS_PENDING.name
        )
        database.mediaDao().insert(item)

        // Mock reconcileExistingMedia call
        val method = MediaRepository::class.java.getDeclaredMethod("reconcileExistingMedia", Context::class.java)
        method.isAccessible = true
        
        // Note: Robolectric MediaStore.getExternalVolumeNames will return empty or default.
        // Since vol_b is not a standard Robolectric volume, it won't be in mountedVolumes.
        
        method.invoke(repository, context)

        // Verify: Item preserved
        assertNotNull(database.mediaDao().getMediaById("local_vid_B"))
    }
}
