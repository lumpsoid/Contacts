package org.fossify.contacts.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RunEachTest {
    @Test
    fun everyItemThatSucceedsIsDone() {
        assertEquals(RunCounts(3, 0), runEach(listOf(1, 2, 3)) { true })
    }

    @Test
    fun aThrowingItemFailsAndTheRestStillRun() {
        val visited = mutableListOf<Int>()
        val counts = runEach(listOf(1, 2, 3)) { item ->
            visited.add(item)
            if (item == 2) throw IllegalStateException("bad")
            true
        }

        assertEquals(RunCounts(2, 1), counts)
        assertEquals(listOf(1, 2, 3), visited)
    }

    @Test
    fun falseCountsAsFailed() {
        assertEquals(RunCounts(1, 1), runEach(listOf(1, 2)) { it == 1 })
    }

    @Test
    fun onFailureGetsTheException() {
        val failures = mutableListOf<Exception>()
        val thrown = IllegalStateException("bad")

        runEach(listOf(1), onFailure = { failures.add(it) }) { throw thrown }

        assertEquals(1, failures.size)
        assertSame(thrown, failures[0])
    }
}
