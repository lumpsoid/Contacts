package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.ContactSource
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnIdsTest {
    private val destination = ContactSource("A", "com.google", "A")

    @Test
    fun onlyRowsOfTheDestinationAccountAreOwn() {
        // Row 6 is in another account and row 7 in another type, so only 5 and 8 are kept.
        val rows = listOf(
            RawAccountRow(5, "A", "com.google"),
            RawAccountRow(6, "B", "com.google"),
            RawAccountRow(7, "A", "other"),
            RawAccountRow(8, "A", "com.google")
        )
        assertEquals(listOf(5, 8), ownIds(rows, destination))
    }

    @Test
    fun aPhoneLocalDestinationMatchesBlankAccounts() {
        val phone = ContactSource("", "", "Phone")
        val rows = listOf(
            RawAccountRow(1, "", ""),
            RawAccountRow(2, "A", "com.google")
        )
        assertEquals(listOf(1), ownIds(rows, phone))
    }
}
