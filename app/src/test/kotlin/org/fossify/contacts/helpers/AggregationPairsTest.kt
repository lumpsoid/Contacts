package org.fossify.contacts.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregationPairsTest {
    @Test
    fun linkingPairsEveryIdWithTheFirst() {
        // Duplicate 5 is dropped, so 5 pairs with 3 and 9 only.
        assertEquals(listOf(5 to 3, 5 to 9), linkPairs(listOf(5, 3, 5, 9)))
    }

    @Test
    fun oneIdLinksNothing() {
        assertTrue(linkPairs(listOf(4)).isEmpty())
    }

    @Test
    fun unlinkingPairsEveryTwoIds() {
        assertEquals(listOf(1 to 2, 1 to 3, 2 to 3), unlinkPairs(listOf(1, 2, 3)))
    }

    @Test
    fun keepingApartSkipsTheSameId() {
        // Id 2 is in both lists, so the pair 2 with 2 is not made.
        assertEquals(listOf(1 to 2, 1 to 3, 2 to 3), keepApartPairs(listOf(1, 2), listOf(2, 3)))
    }
}
