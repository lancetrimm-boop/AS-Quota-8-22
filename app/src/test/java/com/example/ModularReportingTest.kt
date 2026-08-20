package com.example

import com.example.data.*
import com.example.util.IntelligenceExporter
import org.junit.Assert.assertTrue
import org.junit.Test

class ModularReportingTest {

    @Test
    fun testExportToMarkdown_IncludesAllTiers() {
        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 10, score = 55.0, quality = 1.0),
            EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 20, score = 60.0, quality = 1.0),
            EvidenceRecord(tier = EvidenceTier.SIMULATION, sampleCount = 30, score = 65.0, quality = 1.0)
        )
        val report = ClosedLoopEngine.evaluate(50.0, 55.0, 70.0, evidence)
        val md = IntelligenceExporter.exportToMarkdown(report)

        assertTrue(md.contains("Production"))
        assertTrue(md.contains("Experimental"))
        assertTrue(md.contains("Simulation"))
        assertTrue(md.contains("10"))
        assertTrue(md.contains("20"))
        assertTrue(md.contains("30"))
    }
}
