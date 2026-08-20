package com.example.ui

import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.ui.screens.TraitContrastHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraitContrastTest {

    private fun createItem(id: String, moodTags: List<String>): MediaItem {
        return MediaItem(
            id = id,
            title = "Test Item $id",
            mediaType = "PHOTO",
            year = 2024,
            duration = "0:30",
            genre = "Visual",
            moodTags = moodTags,
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )
    }

    @Test
    fun testTraitContrast_vividVsMuted() {
        val itemA = createItem("a", listOf("vibrant"))
        val itemB = createItem("b", listOf("muted"))

        val contrast = TraitContrastHelper.deriveContrast(itemA, itemB)

        assertNotNull(contrast)
        assertEquals("Vibrant", contrast?.badgeA)
        assertEquals("Muted", contrast?.badgeB)
        assertEquals("Vibrancy", contrast?.contrastLabel)
    }

    @Test
    fun testTraitContrast_warmVsCool() {
        val itemA = createItem("a", listOf("warm"))
        val itemB = createItem("b", listOf("cool"))

        val contrast = TraitContrastHelper.deriveContrast(itemA, itemB)

        assertNotNull(contrast)
        assertEquals("Warm", contrast?.badgeA)
        assertEquals("Cool", contrast?.badgeB)
        assertEquals("Color Temperature", contrast?.contrastLabel)
    }

    @Test
    fun testTraitContrast_minimalVsDetailed() {
        val itemA = createItem("a", listOf("minimalist"))
        val itemB = createItem("b", listOf("complex"))

        val contrast = TraitContrastHelper.deriveContrast(itemA, itemB)

        assertNotNull(contrast)
        // minimalism: poles Busy (-) vs Minimal (+)
        // itemA has minimalist (+1.0 minimalism)
        // itemB has complex (no minimalism, +1.0 complexity)
        // minimalism delta = 1.0. complexity delta = -1.0 - 1.0 = 2.0 (but capped at 1.0/-1.0? No, adjustments are coerced)
        // Actually bestDim should be complexity or density/minimalism
        
        assertTrue(contrast?.dimensionName == "complexity" || contrast?.dimensionName == "minimalism" || contrast?.dimensionName == "density")
    }

    @Test
    fun testTraitContrast_emptyItems() {
        val itemA = MediaItem(id = "", title = "", mediaType = "PHOTO")
        val itemB = MediaItem(id = "", title = "", mediaType = "PHOTO")

        val contrast = TraitContrastHelper.deriveContrast(itemA, itemB)

        assertNull(contrast)
    }
}
