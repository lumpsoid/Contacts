package org.fossify.contacts.helpers

import org.fossify.contacts.models.MergeFailure
import org.fossify.contacts.models.MergeOutcome
import org.fossify.contacts.models.MergeRunResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeRunResultTest {
    private val outcomes = listOf(
        MergeOutcome.Merged("a"),
        MergeOutcome.Failed("b", MergeFailure.DELETE_FAILED),
        MergeOutcome.Merged("c")
    )

    @Test
    fun countsMergedAndFailedOutcomes() {
        val result = MergeRunResult.Finished(outcomes, backedUp = false, total = 3)

        assertEquals(2, result.mergedCount())
        assertEquals(listOf("b"), result.failures().map { it.key })
    }

    @Test
    fun aRunThatRanEveryPlanIsNotCancelled() {
        val result = MergeRunResult.Finished(outcomes, backedUp = false, total = 3)

        assertFalse(result.cancelled)
        assertEquals(0, result.skippedCount())
    }

    @Test
    fun plansThatNeverRanMakeTheRunCancelled() {
        val result = MergeRunResult.Finished(outcomes, backedUp = false, total = 5)

        assertTrue(result.cancelled)
        assertEquals(2, result.skippedCount())
    }
}
