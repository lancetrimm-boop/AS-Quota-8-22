package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaThumbnailFetcherTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MediaThumbnailFetcher.clearDiskCache(context)
    }

    @Test
    fun `test getThumbnail with blank uri returns null`() = runTest {
        val result = MediaThumbnailFetcher.getThumbnail(context, "")
        assertNull(result)
    }

    @Test
    fun `test disk cache clearing`() {
        val cacheDir = File(context.cacheDir, "thumbnails")
        cacheDir.mkdirs()
        File(cacheDir, "test.jpg").createNewFile()
        assertTrue(cacheDir.exists())
        
        MediaThumbnailFetcher.clearDiskCache(context)
        assertFalse(cacheDir.exists())
    }

    @Test
    fun `test concurrent requests for same URI do not crash`() = runTest {
        val uri = "content://test/1"
        val jobs = List(10) {
            launch {
                MediaThumbnailFetcher.getThumbnail(context, uri)
            }
        }
        jobs.joinAll()
    }
}
