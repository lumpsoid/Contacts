package org.fossify.contacts.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportResultTest {
    @Test
    fun aFullWriteOfEveryCardIsOk() {
        assertEquals(VcfExporter.ExportResult.EXPORT_OK, exportResult(3, 0, true))
    }

    @Test
    fun aFailedWriteIsAFailureEvenWithCardsBuilt() {
        assertEquals(VcfExporter.ExportResult.EXPORT_FAIL, exportResult(3, 0, false))
    }

    @Test
    fun noCardsIsAFailure() {
        assertEquals(VcfExporter.ExportResult.EXPORT_FAIL, exportResult(0, 0, true))
    }

    @Test
    fun someFailedCardsArePartial() {
        assertEquals(VcfExporter.ExportResult.EXPORT_PARTIAL, exportResult(3, 1, true))
    }

    @Test
    fun aFailedWriteWinsOverPartial() {
        assertEquals(VcfExporter.ExportResult.EXPORT_FAIL, exportResult(3, 1, false))
    }
}
