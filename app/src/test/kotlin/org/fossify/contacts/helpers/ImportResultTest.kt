package org.fossify.contacts.helpers

import org.fossify.contacts.helpers.VcfImporter.ImportResult.IMPORT_FAIL
import org.fossify.contacts.helpers.VcfImporter.ImportResult.IMPORT_OK
import org.fossify.contacts.helpers.VcfImporter.ImportResult.IMPORT_PARTIAL
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportResultTest {
    @Test
    fun everyImportedCardWithNoFailureIsOk() {
        assertEquals(IMPORT_OK, importResult(2, 0))
    }

    @Test
    fun someFailedCardsArePartial() {
        assertEquals(IMPORT_PARTIAL, importResult(2, 1))
    }

    @Test
    fun noCardsImportedIsAFailure() {
        assertEquals(IMPORT_FAIL, importResult(0, 0))
    }

    @Test
    fun onlyFailedCardsIsAFailure() {
        assertEquals(IMPORT_FAIL, importResult(0, 3))
    }
}
