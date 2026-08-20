package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.MediaEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaStoreDeletionSafetyGateTest {

    private lateinit var context: Context
    private lateinit var database: AuraDatabase
    private lateinit var repository: MediaRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        repository = MediaRepository()
        
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

    private suspend fun invokeReconcileDeletions(result: DiscoveryResult) {
        repository.reconcileDeletions(result)
    }

    @Test
    fun `test reconcileDeletions processes complete scan with legitimate deletions`() = runBlocking {
        // Setup: DB has 3 items
        val item1 = MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1")
        val item2 = MediaEntity(id = "local_img_2", title = "I2", mediaType = "PHOTO", uriPath = "content://media/external/images/media/2")
        val item3 = MediaEntity(id = "local_vid_3", title = "V3", mediaType = "VIDEO", uriPath = "content://media/external/video/media/3")
        database.mediaDao().insertAll(listOf(item1, item2, item3))

        // Scan: Only 1 and 2 discovered. 3 is missing.
        val result = DiscoveryResult.Complete(
            entities = emptyList(),
            discoveredIds = setOf("local_vid_1", "local_img_2"),
            scannedVolumes = setOf("external"),
            scannedMediaTypes = setOf("VIDEO", "PHOTO")
        )

        invokeReconcileDeletions(result)

        // Verify: item3 is deleted, 1 and 2 remain
        assertNotNull(database.mediaDao().getMediaById("local_vid_1"))
        assertNotNull(database.mediaDao().getMediaById("local_img_2"))
        assertNull(database.mediaDao().getMediaById("local_vid_3"))
        assertEquals(2, database.mediaDao().getCount())
    }

    @Test
    fun `test reconcileDeletions supports 90 percent mass deletion if complete`() = runBlocking {
        // Setup: DB has 10 items
        val items = (1..10).map { i ->
            MediaEntity(id = "local_vid_$i", title = "V$i", mediaType = "VIDEO", uriPath = "content://media/external/video/media/$i")
        }
        database.mediaDao().insertAll(items)

        // Scan: Only 1 item discovered (90% deletion)
        val result = DiscoveryResult.Complete(
            entities = emptyList(),
            discoveredIds = setOf("local_vid_1"),
            scannedVolumes = setOf("external"),
            scannedMediaTypes = setOf("VIDEO", "PHOTO")
        )

        invokeReconcileDeletions(result)

        // Verify: Only item1 remains
        assertEquals(1, database.mediaDao().getCount())
        assertNotNull(database.mediaDao().getMediaById("local_vid_1"))
    }

    @Test
    fun `test reconcileDeletions processes legitimate empty scan (everything deleted)`() = runBlocking {
        // Setup: DB has 5 items
        val items = (1..5).map { i ->
            MediaEntity(id = "local_vid_$i", title = "V$i", mediaType = "VIDEO", uriPath = "content://media/external/video/media/$i")
        }
        database.mediaDao().insertAll(items)

        // Scan: Authoritative scan finds NOTHING (e.g. user wiped their media)
        val result = DiscoveryResult.Complete(
            entities = emptyList(),
            discoveredIds = emptySet(),
            scannedVolumes = setOf("external"),
            scannedMediaTypes = setOf("VIDEO", "PHOTO")
        )

        invokeReconcileDeletions(result)

        // Verify: ALL local items deleted
        assertEquals(0, database.mediaDao().getCount())
    }

    @Test
    fun `test reconcileDeletions aborts on incomplete scan`() = runBlocking {
        // Setup: DB has 1 item
        val item1 = MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1")
        database.mediaDao().insertAll(listOf(item1))

        // Scan: Incomplete
        val result = DiscoveryResult.Incomplete("Query failed", ScanError.QUERY_FAILURE)

        invokeReconcileDeletions(result)

        // Verify: item1 remains despite being "missing" from discovery result (which is ignored)
        assertNotNull(database.mediaDao().getMediaById("local_vid_1"))
        assertEquals(1, database.mediaDao().getCount())
    }

    @Test
    fun `test reconcileDeletions handles cancellation state correctly`() = runBlocking {
        val item1 = MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1")
        database.mediaDao().insertAll(listOf(item1))

        val result = DiscoveryResult.Incomplete("Canceled", ScanError.CANCELED)
        invokeReconcileDeletions(result)

        assertEquals(1, database.mediaDao().getCount())
    }

    @Test
    fun `test reconcileDeletions preserves items from unmounted volumes`() = runBlocking {
        // Setup: DB has items on internal and external SD
        val internalItem = MediaEntity(id = "local_vid_1", title = "Internal", mediaType = "VIDEO", uriPath = "content://media/external_primary/video/media/1")
        val sdItem = MediaEntity(id = "local_vid_2", title = "SD", mediaType = "VIDEO", uriPath = "content://media/sd_card/video/media/2")
        database.mediaDao().insertAll(listOf(internalItem, sdItem))

        // Scan: SD card is unmounted, so only external_primary is scanned.
        // internalItem is discovered. sdItem is NOT in discovery but its volume was not scanned.
        val result = DiscoveryResult.Complete(
            entities = emptyList(),
            discoveredIds = setOf("local_vid_1"),
            scannedVolumes = setOf("external_primary"),
            scannedMediaTypes = setOf("VIDEO", "PHOTO")
        )

        invokeReconcileDeletions(result)

        // Verify: Both remain. SD item is NOT deleted because its volume was not verified.
        assertNotNull(database.mediaDao().getMediaById("local_vid_1"))
        assertNotNull(database.mediaDao().getMediaById("local_vid_2"))
        assertEquals(2, database.mediaDao().getCount())
    }

    @Test
    fun `test reconcileDeletions preserves items if media type not scanned`() = runBlocking {
        // Setup: DB has video and photo
        val video = MediaEntity(id = "local_vid_1", title = "V1", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1")
        val photo = MediaEntity(id = "local_img_1", title = "I1", mediaType = "PHOTO", uriPath = "content://media/external/images/media/1")
        database.mediaDao().insertAll(listOf(video, photo))

        // Scan: Only PHOTO was successfully scanned (e.g. video query failed or was skipped)
        val result = DiscoveryResult.Complete(
            entities = emptyList(),
            discoveredIds = setOf("local_img_1"), // video missing
            scannedVolumes = setOf("external"),
            scannedMediaTypes = setOf("PHOTO") // VIDEO missing from scanned types
        )

        invokeReconcileDeletions(result)

        // Verify: Video remains because its type was not authoritatively verified this session
        assertNotNull(database.mediaDao().getMediaById("local_vid_1"))
        assertNotNull(database.mediaDao().getMediaById("local_img_1"))
        assertEquals(2, database.mediaDao().getCount())
    }

    @Test
    fun `test reconcileDeletions does not delete non-local items`() = runBlocking {
        // Setup: DB has local item and imported item
        val local = MediaEntity(id = "local_vid_1", title = "Local", mediaType = "VIDEO", uriPath = "content://media/external/video/media/1")
        val imported = MediaEntity(id = "import_123", title = "Imported", mediaType = "VIDEO", uriPath = "content://external_provider/123")
        database.mediaDao().insertAll(listOf(local, imported))

        // Scan: complete but empty
        val result = DiscoveryResult.Complete(
            entities = emptyList(),
            discoveredIds = emptySet(),
            scannedVolumes = setOf("external"),
            scannedMediaTypes = setOf("VIDEO", "PHOTO")
        )

        invokeReconcileDeletions(result)

        // Verify: Local is deleted, Imported remains (Provenance safety)
        assertNull(database.mediaDao().getMediaById("local_vid_1"))
        assertNotNull(database.mediaDao().getMediaById("import_123"))
        assertEquals(1, database.mediaDao().getCount())
    }
}
