package org.fossify.contacts.helpers

import org.fossify.commons.helpers.PHOTO_ADDED
import org.fossify.commons.helpers.PHOTO_CHANGED
import org.fossify.commons.helpers.PHOTO_UNCHANGED
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Address
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Email
import org.fossify.commons.models.contacts.Event
import org.fossify.commons.models.contacts.Group
import org.fossify.commons.models.contacts.IM
import org.fossify.commons.models.contacts.Organization
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.MergeChoices
import org.fossify.contacts.models.MergeKind
import org.fossify.contacts.models.MergePlan
import org.fossify.contacts.models.MergeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTargetsTest {
    private val google = ContactSource("Google", "com.google", "Google")
    private val accountA = ContactSource("A", "com.google", "A")
    private val accountB = ContactSource("B", "com.google", "B")

    private fun record(
        id: Int,
        source: String,
        type: String = "com.google",
        access: AccountAccess = AccountAccess.WRITABLE,
        numbers: List<String> = emptyList(),
        emails: List<String> = emptyList()
    ) = MergeRecord(
        Contact(
            id = id,
            contactId = id,
            source = source,
            phoneNumbers = ArrayList(numbers.map { PhoneNumber(it, 0, "", it) }),
            emails = ArrayList(emails.map { Email(it, 0, "") })
        ),
        type,
        access
    )

    private fun mergePlan(kind: MergeKind, vararg records: MergeRecord): MergePlan =
        MergePlan("key", kind, records.toList(), MergeChoices())

    @Test
    fun pickTargetPrefersTheRecordWithMoreDetails() {
        val fewer = record(1, "Google", numbers = listOf("111"))
        val more = record(2, "Google", numbers = listOf("111", "222"))
        assertEquals(more, pickTarget(listOf(fewer, more), google))
    }

    @Test
    fun pickTargetTakesTheLowerIdOnATie() {
        val later = record(5, "Google", numbers = listOf("111"))
        val earlier = record(3, "Google", numbers = listOf("222"))
        assertEquals(earlier, pickTarget(listOf(later, earlier), google))
    }

    @Test
    fun pickTargetIsNullWithoutADestinationRecord() {
        val other = record(1, "Phone", type = "local")
        assertNull(pickTarget(listOf(other), google))
    }

    @Test
    fun pickTargetNeverPicksAReadOnlyRecord() {
        val readOnly = record(1, "Google", access = AccountAccess.READ_ONLY, numbers = listOf("1", "2", "3"))
        val writable = record(2, "Google")
        assertEquals(writable, pickTarget(listOf(readOnly, writable), google))
        assertNull(pickTarget(listOf(readOnly), google))
    }

    @Test
    fun duplicatesDeleteTheOtherWritableRecordsOnly() {
        val target = record(1, "Google")
        val phoneCopy = record(2, "Phone", type = "local")
        val whatsApp = record(3, "WhatsApp", type = "com.whatsapp", access = AccountAccess.READ_ONLY)
        val plan = mergePlan(MergeKind.DUPLICATES, target, phoneCopy, whatsApp)
        assertEquals(listOf(phoneCopy), recordsToDelete(plan, target))
    }

    @Test
    fun splitDetailsDeletesNothing() {
        val target = record(1, "Google")
        val other = record(2, "Phone", type = "local")
        val plan = mergePlan(MergeKind.SPLIT_DETAILS, target, other)
        assertTrue(recordsToDelete(plan, target).isEmpty())
    }

    @Test
    fun linksOnlyTheReadOnlyDeviceRecords() {
        val target = record(1, "Google")
        val phoneCopy = record(2, "Phone", type = "local")
        val whatsApp = record(3, "WhatsApp", type = "com.whatsapp", access = AccountAccess.READ_ONLY)
        val plan = mergePlan(MergeKind.DUPLICATES, target, phoneCopy, whatsApp)
        assertEquals(listOf(3), rawIdsToLink(plan, target))
    }

    @Test
    fun aPrivateRecordIsNeverLinked() {
        val target = record(1, "Google")
        val whatsApp = record(3, "WhatsApp", type = "com.whatsapp", access = AccountAccess.READ_ONLY)
        val secret = record(4, SMT_PRIVATE)
        val plan = mergePlan(MergeKind.SPLIT_DETAILS, target, whatsApp, secret)
        assertEquals(listOf(3), rawIdsToLink(plan, target))

        val device = record(5, "B")
        val withDevice = mergePlan(MergeKind.SPLIT_DETAILS, target, whatsApp, secret, device)
        assertEquals(listOf(3, 5), rawIdsToLink(withDevice, target))
    }

    @Test
    fun summaryCountsDeletionsPerAccountLikeRecordsToDelete() {
        val first = mergePlan(
            MergeKind.DUPLICATES,
            record(1, "Google"),
            record(2, "Phone", type = "local"),
            record(3, "Phone", type = "local"),
            record(4, "WhatsApp", type = "com.whatsapp", access = AccountAccess.READ_ONLY)
        )
        val second = mergePlan(
            MergeKind.DUPLICATES,
            record(5, "Google"),
            record(6, "Phone", type = "local")
        )
        val summary = summarize(listOf(first, second), google)
        assertEquals(2, summary.groups)
        assertEquals(6, summary.records)
        assertEquals(mapOf("Phone" to 3), summary.deletedPerAccount)
    }

    @Test
    fun verifyComparesNumbersByDigitsAndPlus() {
        val expected = record(1, "Google", numbers = listOf("+1 555-0100")).contact
        val actual = record(2, "Google", numbers = listOf("+15550100")).contact
        assertTrue(verifyMerged(expected, actual))
    }

    @Test
    fun verifyFailsOnAMissingNumber() {
        val expected = record(1, "Google", numbers = listOf("+15550100")).contact
        val actual = record(2, "Google").contact
        assertFalse(verifyMerged(expected, actual))
    }

    @Test
    fun verifyFailsOnAMissingEmail() {
        val expected = record(1, "Google", emails = listOf("ann@example.com")).contact
        val actual = record(2, "Google").contact
        assertFalse(verifyMerged(expected, actual))
    }

    @Test
    fun verifyComparesEmailsTrimmedAndIgnoringCase() {
        val expected = record(1, "Google", emails = listOf("Ann@X.com ")).contact
        val actual = record(2, "Google", emails = listOf("ann@x.com")).contact
        assertTrue(verifyMerged(expected, actual))

        val other = record(3, "Google", emails = listOf("bob@x.com")).contact
        assertFalse(verifyMerged(expected, other))
    }

    @Test
    fun verifyFailsOnAMissingAddressNoteOrGroup() {
        val withAddress = Contact(id = 1, contactId = 1, addresses = arrayListOf(Address("1 Main St", 0, "")))
        assertFalse(verifyMerged(withAddress, Contact(id = 2, contactId = 2)))

        val withNote = Contact(id = 1, contactId = 1, notes = "met at work")
        assertFalse(verifyMerged(withNote, Contact(id = 2, contactId = 2, notes = "")))

        val withGroup = Contact(id = 1, contactId = 1, groups = arrayListOf(Group(1L, "Family")))
        assertFalse(verifyMerged(withGroup, Contact(id = 2, contactId = 2)))
    }

    @Test
    fun verifyFailsOnAMissingPhoto() {
        val expected = Contact(id = 1, contactId = 1, photoUri = "content://a")
        assertFalse(verifyMerged(expected, Contact(id = 2, contactId = 2)))
        assertTrue(verifyMerged(expected, Contact(id = 2, contactId = 2, photoUri = "content://b")))
    }

    @Test
    fun verifyFailsOnADifferentName() {
        val expected = Contact(id = 1, contactId = 1, firstName = "Anna")
        assertFalse(verifyMerged(expected, Contact(id = 2, contactId = 2, firstName = "Hanna")))
        assertTrue(verifyMerged(expected, Contact(id = 2, contactId = 2, firstName = " anna ")))
    }

    @Test
    fun verifyIgnoresBlankExpectedFields() {
        val expected = Contact(id = 1, contactId = 1)
        val actual = Contact(id = 2, contactId = 2, firstName = "Anna", notes = "x")
        assertTrue(verifyMerged(expected, actual))
    }

    @Test
    fun verifyPassesWhenEveryFieldLanded() {
        val expected = Contact(
            id = 1,
            contactId = 1,
            firstName = "Anna",
            surname = "Petrova",
            nickname = "Ann",
            organization = Organization("Acme", "CTO"),
            addresses = arrayListOf(Address("1 Main St", 0, "")),
            IMs = arrayListOf(IM("ann@jabber", 0, "")),
            events = arrayListOf(Event("--05-01", 3)),
            websites = arrayListOf("https://a.example"),
            groups = arrayListOf(Group(5L, "Family"))
        )
        val actual = expected.copy(
            id = 2,
            groups = arrayListOf(Group(9L, "family"), Group(10L, "Work"))
        )
        assertTrue(verifyMerged(expected, actual))
    }

    @Test
    fun verifyFailsOnALostStar() {
        val expected = Contact(id = 1, contactId = 1, starred = 1)
        assertFalse(verifyMerged(expected, Contact(id = 2, contactId = 2, starred = 0)))
        assertTrue(verifyMerged(expected, Contact(id = 2, contactId = 2, starred = 1)))
    }

    @Test
    fun verifyIgnoresAStarTheExpectedLacks() {
        val expected = Contact(id = 1, contactId = 1, starred = 0)
        assertTrue(verifyMerged(expected, Contact(id = 2, contactId = 2, starred = 1)))
    }

    @Test
    fun verifyFailsOnADifferentRingtone() {
        val expected = Contact(id = 1, contactId = 1, ringtone = "content://media/1")
        assertFalse(verifyMerged(expected, Contact(id = 2, contactId = 2, ringtone = "content://media/2")))
        assertFalse(verifyMerged(expected, Contact(id = 2, contactId = 2, ringtone = null)))
        assertTrue(verifyMerged(expected, Contact(id = 2, contactId = 2, ringtone = " content://media/1")))
    }

    @Test
    fun verifyIgnoresABlankExpectedRingtone() {
        val actual = Contact(id = 2, contactId = 2, ringtone = "content://media/2")
        assertTrue(verifyMerged(Contact(id = 1, contactId = 1, ringtone = ""), actual))
        assertTrue(verifyMerged(Contact(id = 1, contactId = 1, ringtone = null), actual))
    }

    @Test
    fun photoStatusFollowsTheUri() {
        assertEquals(PHOTO_ADDED, photoUpdateStatus("", "a"))
        assertEquals(PHOTO_CHANGED, photoUpdateStatus("a", "b"))
        assertEquals(PHOTO_UNCHANGED, photoUpdateStatus("a", "a"))
        assertEquals(PHOTO_UNCHANGED, photoUpdateStatus("a", ""))
    }

    @Test
    fun destinationIsTheSourceHoldingEverything() {
        val plans = listOf(mergePlan(MergeKind.DUPLICATES, record(1, "B"), record(2, "B")))
        assertEquals(accountB, preferredDestination(plans, listOf(accountA, accountB), "A"))
    }

    @Test
    fun destinationFallsBackToTheLastUsedName() {
        val plans = listOf(mergePlan(MergeKind.DUPLICATES, record(1, "A"), record(2, "B")))
        assertEquals(accountB, preferredDestination(plans, listOf(accountA, accountB), "B"))
    }

    @Test
    fun destinationFallsBackToTheFirstSource() {
        val plans = listOf(mergePlan(MergeKind.DUPLICATES, record(1, "A"), record(2, "B")))
        assertEquals(accountA, preferredDestination(plans, listOf(accountA, accountB), "Nope"))
    }

    @Test
    fun copyDeletesNothing() {
        val plan = mergePlan(MergeKind.COPY, record(5, "Other"))
        assertTrue(recordsToDelete(plan, null).isEmpty())
    }

    @Test
    fun copyLinksNothing() {
        val plan = mergePlan(MergeKind.COPY, record(5, "Other"))
        assertTrue(rawIdsToLink(plan, null).isEmpty())
    }

    @Test
    fun aOneRecordDuplicatesPlanDeletesTheRecord() {
        val only = record(5, "Other")
        val plan = mergePlan(MergeKind.DUPLICATES, only)
        assertEquals(listOf(only), recordsToDelete(plan, null))
    }
}
