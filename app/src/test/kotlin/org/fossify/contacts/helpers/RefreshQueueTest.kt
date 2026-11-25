package org.fossify.contacts.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshQueueTest {
    companion object {
        private const val TAB_A = 1
        private const val TAB_B = 2
        private const val TAB_C = 4
    }

    @Test
    fun aFirstRequestStartsALoad() {
        assertTrue(RefreshQueue().request(TAB_A))
    }

    @Test
    fun aRequestDuringALoadWaitsAndComesBackOnFinish() {
        val queue = RefreshQueue()
        queue.request(TAB_A)
        assertFalse(queue.request(TAB_B))
        assertFalse(queue.request(TAB_C))
        assertEquals(TAB_B or TAB_C, queue.finish())
    }

    @Test
    fun finishWithNothingWaitingReturnsZeroAndFreesTheQueue() {
        val queue = RefreshQueue()
        queue.request(TAB_A)
        assertEquals(0, queue.finish())
        assertTrue(queue.request(TAB_B))
    }

    @Test
    fun aRequestAfterFinishStartsANewLoad() {
        val queue = RefreshQueue()
        queue.request(TAB_A)
        queue.request(TAB_B)
        queue.finish()
        assertTrue(queue.request(TAB_B))
        assertEquals(0, queue.finish())
    }
}
