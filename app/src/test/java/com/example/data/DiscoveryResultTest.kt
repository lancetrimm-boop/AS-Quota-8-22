package com.example.data

import com.example.data.db.MediaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryResultTest {

    @Test
    fun `test DiscoveryResult Complete properties`() {
        val entities = listOf(MediaEntity(id = "1", title = "Test", mediaType = "PHOTO"))
        val discoveredIds = setOf("1")
        val scannedVolumes = setOf("external")
        val scannedMediaTypes = setOf("PHOTO")
        
        val result = DiscoveryResult.Complete(entities, discoveredIds, scannedVolumes, scannedMediaTypes)
        
        assertEquals(entities, result.entities)
        assertEquals(discoveredIds, result.discoveredIds)
        assertEquals(scannedVolumes, result.scannedVolumes)
        assertEquals(scannedMediaTypes, result.scannedMediaTypes)
    }

    @Test
    fun `test DiscoveryResult Incomplete properties`() {
        val reason = "Failed"
        val errorCode = ScanError.QUERY_FAILURE
        val cause = Exception("fail")
        
        val result = DiscoveryResult.Incomplete(reason, errorCode, cause)
        
        assertEquals(reason, result.reason)
        assertEquals(errorCode, result.errorCode)
        assertEquals(cause, result.cause)
    }
}
