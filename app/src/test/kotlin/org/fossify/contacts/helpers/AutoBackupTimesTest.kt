package org.fossify.contacts.helpers

import org.joda.time.DateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupTimesTest {
    @Test
    fun beforeSixTheNextBackupIsToday() {
        val expected = DateTime(2026, 3, 4, 6, 10, 0)
        val actual = nextAutoBackupTime(DateTime(2026, 3, 4, 5, 10, 0))
        assertEquals(expected.millis, actual.millis)
    }

    @Test
    fun afterSixTheNextBackupIsTomorrow() {
        val expected = DateTime(2026, 3, 5, 6, 10, 0)
        val actual = nextAutoBackupTime(DateTime(2026, 3, 4, 7, 10, 0))
        assertEquals(expected.millis, actual.millis)
    }

    @Test
    fun thePreviousBackupIsOneIntervalBeforeTheNext() {
        val expected = DateTime(2026, 3, 4, 6, 10, 0)
        val actual = previousAutoBackupTime(DateTime(2026, 3, 4, 7, 10, 0))
        assertEquals(expected.millis, actual.millis)
    }

    @Test
    fun aBackupBeforeThePreviousSlotIsMissed() {
        assertTrue(missedAutoBackup(1, 2))
        assertFalse(missedAutoBackup(2, 2))
        assertFalse(missedAutoBackup(3, 2))
    }

    @Test
    fun tokensAreReplacedByTheGivenTime() {
        val name = autoBackupFileName("c_%Y-%M-%D_%h-%m-%s", DateTime(2026, 3, 4, 5, 6, 7))
        assertEquals("c_2026-03-04_05-06-07", name)
    }

    @Test
    fun aFreeNameIsUsedAsIs() {
        assertEquals("b.vcf", freeBackupFileName("b") { false })
    }

    @Test
    fun takenNamesGetTheFirstFreeNumber() {
        val taken = setOf("b.vcf", "b_1.vcf")
        assertEquals("b_2.vcf", freeBackupFileName("b") { it in taken })
    }
}
