package org.fossify.contacts.helpers

import org.fossify.commons.helpers.PHOTO_ADDED
import org.fossify.commons.helpers.PHOTO_CHANGED
import org.fossify.commons.helpers.PHOTO_UNCHANGED
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Email
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
        val secret = record(4, SMT_PRIVATE, access = AccountAccess.READ_ONLY)
        val plan = mergePlan(MergeKind.DUPLICATES, target, whatsApp, secret)
        assertEquals(listOf(3), rawIdsToLink(plan, target))
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
}
