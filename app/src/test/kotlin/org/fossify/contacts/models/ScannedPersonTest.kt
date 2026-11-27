package org.fossify.contacts.models

import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class ScannedPersonTest {
    companion object {
        private const val GOOGLE = "me@gmail.com"
    }

    private fun record(
        id: Int,
        name: String,
        source: String = GOOGLE,
        access: AccountAccess = AccountAccess.WRITABLE,
        numbers: List<String> = emptyList()
    ) = MergeRecord(
        Contact(
            id = id,
            contactId = 1,
            firstName = name,
            source = source,
            phoneNumbers = ArrayList(numbers.map { PhoneNumber(it, 0, "", it) })
        ),
        "com.google",
        access
    )

    @Test
    fun aWritableNameBeatsAReadOnlyNameListedFirst() {
        val person = ScannedPerson(
            "contact:1",
            listOf(record(1, "Ann", access = AccountAccess.READ_ONLY), record(2, "Anna"))
        )
        assertEquals("Anna", person.displayName())
    }

    @Test
    fun deviceRawIdsLeavesPrivateRecordsOut() {
        val person = ScannedPerson(
            "contact:1",
            listOf(record(1, "Ann"), record(9, "Ann", source = SMT_PRIVATE))
        )
        assertEquals(listOf(1), person.deviceRawIds())
    }

    @Test
    fun detailKeysAreTheDistinctNumbersOfAllRecords() {
        val person = ScannedPerson(
            "contact:1",
            listOf(record(1, "Ann", numbers = listOf("555")), record(2, "Ann", numbers = listOf("555", "777")))
        )
        assertEquals(setOf("tel:555", "tel:777"), person.detailKeys())
    }
}
