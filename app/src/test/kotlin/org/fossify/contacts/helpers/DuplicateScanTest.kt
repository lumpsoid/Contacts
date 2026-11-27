package org.fossify.contacts.helpers

import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Email
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.MatchConfidence
import org.fossify.contacts.models.MergeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateScanTest {
    companion object {
        private const val GOOGLE = "me@gmail.com"
        private const val WHATSAPP = "WhatsApp"
        private const val HIDDEN = "hidden@x.com"
        private val VISIBLE = setOf(GOOGLE, WHATSAPP)
    }

    private fun record(
        id: Int,
        contactId: Int,
        name: String = "Ann Lee",
        source: String = GOOGLE,
        access: AccountAccess = AccountAccess.WRITABLE,
        numbers: List<String> = emptyList(),
        emails: List<String> = emptyList()
    ) = MergeRecord(
        Contact(
            id = id,
            contactId = contactId,
            firstName = name,
            source = source,
            phoneNumbers = ArrayList(numbers.map { PhoneNumber(it, 0, "", it) }),
            emails = ArrayList(emails.map { Email(it, 0, "") })
        ),
        if (source == WHATSAPP) "com.whatsapp" else "com.google",
        access
    )

    @Test
    fun sameNameAndSharedNumberIsOneHighGroup() {
        val result = scanDuplicates(
            listOf(record(1, 1, numbers = listOf("555")), record(2, 2, numbers = listOf("555"))),
            emptySet(),
            VISIBLE
        )
        assertEquals(1, result.duplicates.size)
        assertEquals(MatchConfidence.HIGH, result.duplicates.single().confidence)
    }

    @Test
    fun sameNameWithoutSharedDetailIsOneLowGroup() {
        val result = scanDuplicates(
            listOf(record(1, 1, numbers = listOf("555")), record(2, 2, numbers = listOf("777"))),
            emptySet(),
            VISIBLE
        )
        assertEquals(1, result.duplicates.size)
        assertEquals(MatchConfidence.LOW, result.duplicates.single().confidence)
    }

    @Test
    fun sharedEmailMakesASameNameGroupHigh() {
        val shared = scanDuplicates(
            listOf(record(1, 1, emails = listOf("ann@x.com")), record(2, 2, emails = listOf("ann@x.com"))),
            emptySet(),
            VISIBLE
        )
        assertEquals(MatchConfidence.HIGH, shared.duplicates.single().confidence)

        // Positive control: different emails leave only the name in common.
        val different = scanDuplicates(
            listOf(record(1, 1, emails = listOf("ann@x.com")), record(2, 2, emails = listOf("bob@x.com"))),
            emptySet(),
            VISIBLE
        )
        assertEquals(MatchConfidence.LOW, different.duplicates.single().confidence)
    }

    @Test
    fun differentNamesAreNotGrouped() {
        val result = scanDuplicates(
            listOf(record(1, 1, name = "Ann Lee"), record(2, 2, name = "Bob Ray")),
            emptySet(),
            VISIBLE
        )
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun namesMatchAcrossAccentCaseAndWordOrder() {
        assertEquals("anna petrova", normalizeName("Petrová Anna"))
        val result = scanDuplicates(
            listOf(record(1, 1, name = "Anna Petrová"), record(2, 2, name = "petrova  anna")),
            emptySet(),
            VISIBLE
        )
        assertEquals(1, result.duplicates.size)
    }

    @Test
    fun kanaVoicingMarksAreKept() {
        // U+304C is が; its NFD form is か followed by the voicing mark U+3099.
        assertNotEquals(normalizeName("か"), normalizeName("が"))
        assertEquals(normalizeName("が"), normalizeName("が"))
    }

    @Test
    fun recordsOfOneContactAreOnePersonNotADuplicate() {
        val linked = scanDuplicates(listOf(record(1, 7), record(2, 7)), emptySet(), VISIBLE)
        assertTrue(linked.duplicates.isEmpty())

        // Positive control: the same two records under different contacts are a duplicate pair.
        val apart = scanDuplicates(listOf(record(1, 7), record(2, 8)), emptySet(), VISIBLE)
        assertEquals(1, apart.duplicates.size)
    }

    @Test
    fun keptApartPairIsNeverGroupedTogether() {
        val pair = scanDuplicates(listOf(record(1, 1), record(2, 2)), setOf(Pair(1, 2)), VISIBLE)
        assertTrue(pair.duplicates.isEmpty())

        val withThird = scanDuplicates(
            listOf(record(1, 1), record(2, 2), record(3, 3)),
            setOf(Pair(1, 2)),
            VISIBLE
        )
        assertEquals(1, withThird.duplicates.size)
        val ids = withThird.duplicates.single().records().map { it.contact.id }
        assertEquals(2, ids.size)
        assertTrue(ids.contains(3))
        assertFalse(ids.containsAll(listOf(1, 2)))
    }

    @Test
    fun hiddenAccountIsIgnored() {
        val records = listOf(record(1, 1), record(2, 2, source = HIDDEN))
        val hidden = scanDuplicates(records, emptySet(), VISIBLE)
        assertTrue(hidden.duplicates.isEmpty())

        // Positive control: with the hidden account visible, the same pair is a duplicate.
        val visible = scanDuplicates(records, emptySet(), VISIBLE + HIDDEN)
        assertEquals(1, visible.duplicates.size)
    }

    @Test
    fun messengerRecordOfOtherwiseOneContactIsASplitDetail() {
        val split = scanDuplicates(
            listOf(
                record(1, 7, numbers = listOf("111")),
                record(2, 7, source = WHATSAPP, access = AccountAccess.READ_ONLY, numbers = listOf("222"))
            ),
            emptySet(),
            VISIBLE
        )
        assertTrue(split.duplicates.isEmpty())
        assertEquals(listOf("contact:7"), split.splitDetails.map { it.key })

        // Positive control: the messenger record holds the same number, so no writable record is missing one.
        val whole = scanDuplicates(
            listOf(
                record(1, 7, numbers = listOf("111")),
                record(2, 7, source = WHATSAPP, access = AccountAccess.READ_ONLY, numbers = listOf("111"))
            ),
            emptySet(),
            VISIBLE
        )
        assertTrue(whole.splitDetails.isEmpty())
    }

    @Test
    fun splitAcrossTwoWritableAccountsIsASplitDetail() {
        val visibleWithPhone = VISIBLE + "phone"
        val split = scanDuplicates(
            listOf(
                record(1, 7, numbers = listOf("111")),
                record(2, 7, source = "phone", access = AccountAccess.WRITABLE, numbers = listOf("222"))
            ),
            emptySet(),
            visibleWithPhone
        )
        assertEquals(listOf("contact:7"), split.splitDetails.map { it.key })

        // Positive control: record 1 holds both numbers, so no writable record is missing one.
        val whole = scanDuplicates(
            listOf(
                record(1, 7, numbers = listOf("111", "222")),
                record(2, 7, source = "phone", access = AccountAccess.WRITABLE, numbers = listOf("222"))
            ),
            emptySet(),
            visibleWithPhone
        )
        assertTrue(whole.splitDetails.isEmpty())
    }

    @Test
    fun blankNamesAreNeverGrouped() {
        val result = scanDuplicates(
            listOf(record(1, 1, name = ""), record(2, 2, name = "")),
            emptySet(),
            VISIBLE
        )
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun aSharedContactIdIsOnePersonAcrossDeviceRecords() {
        val people = peopleOf(listOf(record(1, 7), record(2, 7)))
        assertEquals(listOf("contact:7"), people.map { it.key })
        assertEquals(listOf(1, 2), people.single().records.map { it.contact.id })
    }

    @Test
    fun aDeviceRecordWithoutContactIdAndAPrivateRecordAreTwoPeople() {
        val people = peopleOf(listOf(record(1, 0), record(2, 2, source = SMT_PRIVATE)))
        assertEquals(listOf("raw:$GOOGLE:1", "raw:$SMT_PRIVATE:2"), people.map { it.key })
    }

    @Test
    fun privateRecordsSharingAContactIdAreSeparatePeople() {
        val people = peopleOf(listOf(record(1, 7, source = SMT_PRIVATE), record(2, 7, source = SMT_PRIVATE)))
        assertEquals(listOf("raw:$SMT_PRIVATE:1", "raw:$SMT_PRIVATE:2"), people.map { it.key })
    }

    @Test
    fun aPersonInOneAccountIsNotASplitDetail() {
        val split = scanDuplicates(
            listOf(record(1, 7, numbers = listOf("111")), record(2, 7, numbers = listOf("222"))),
            emptySet(),
            VISIBLE
        )
        assertTrue(split.splitDetails.isEmpty())
    }

    @Test
    fun aPersonWithoutAWritableRecordIsNotASplitDetail() {
        val readOnly = scanDuplicates(
            listOf(
                record(1, 7, numbers = listOf("111"), access = AccountAccess.READ_ONLY),
                record(2, 7, source = WHATSAPP, access = AccountAccess.READ_ONLY, numbers = listOf("222"))
            ),
            emptySet(),
            VISIBLE
        )
        assertTrue(readOnly.splitDetails.isEmpty())

        // Positive control: a writable first record makes the same person a split detail.
        val writable = scanDuplicates(
            listOf(
                record(1, 7, numbers = listOf("111")),
                record(2, 7, source = WHATSAPP, access = AccountAccess.READ_ONLY, numbers = listOf("222"))
            ),
            emptySet(),
            VISIBLE
        )
        assertEquals(listOf("contact:7"), writable.splitDetails.map { it.key })
    }

    @Test
    fun twoPeopleSharingANumberAndAnEmailAreHigh() {
        val result = scanDuplicates(
            listOf(
                record(1, 1, numbers = listOf("555"), emails = listOf("ann@x.com")),
                record(2, 2, numbers = listOf("555"), emails = listOf("ann@x.com"))
            ),
            emptySet(),
            VISIBLE
        )
        assertEquals(MatchConfidence.HIGH, result.duplicates.single().confidence)
    }

    @Test
    fun aKeyHeldTwiceByOnePersonIsNotSharedWithAnother() {
        val lone = scanDuplicates(
            listOf(
                record(1, 1, numbers = listOf("555")),
                record(2, 1, numbers = listOf("555")),
                record(3, 2, numbers = listOf("777"))
            ),
            emptySet(),
            VISIBLE
        )
        assertEquals(MatchConfidence.LOW, lone.duplicates.single().confidence)

        // Positive control: the other person holding 555 too makes the group HIGH.
        val shared = scanDuplicates(
            listOf(
                record(1, 1, numbers = listOf("555")),
                record(2, 1, numbers = listOf("555")),
                record(3, 2, numbers = listOf("555"))
            ),
            emptySet(),
            VISIBLE
        )
        assertEquals(MatchConfidence.HIGH, shared.duplicates.single().confidence)
    }
}
