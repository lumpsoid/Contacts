package org.fossify.contacts.helpers

import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Email
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.ExtraDetails
import org.fossify.contacts.models.LabeledValue
import org.fossify.contacts.models.MergeChoices
import org.fossify.contacts.models.MergeFailure
import org.fossify.contacts.models.MergeKind
import org.fossify.contacts.models.MergeOutcome
import org.fossify.contacts.models.MergePlan
import org.fossify.contacts.models.MergeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupeWriterTest {
    private val accountA = ContactSource("A", "com.google", "A")
    private val whatsApp = record(3, "WhatsApp", type = "com.whatsapp", access = AccountAccess.READ_ONLY)
    private val store = FakeMergeStore()
    private val writer = DedupeWriter(store)
    private val sip = ExtraDetails(sipAddresses = listOf(LabeledValue("ann@sip.example", 1, "")))
    private val privateStorage = ContactSource(SMT_PRIVATE, SMT_PRIVATE, "Phone storage")

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

    /** Two device records in A with a number each, and the read-only messenger record. */
    private fun duplicates(): MergePlan = mergePlan(
        MergeKind.DUPLICATES,
        record(1, "A", numbers = listOf("111")),
        record(2, "A", numbers = listOf("222")),
        whatsApp
    )

    @Test
    fun aMergeDeletesThenLinks() {
        val plan = duplicates()
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Merged(plan.key), outcome)
        assertEquals(listOf("groups", "update:1", "readBack:1", "delete:2", "link:1,3"), store.calls)
    }

    @Test
    fun aFailedUpdateDeletesAndLinksNothing() {
        store.updateWorks = false
        val plan = duplicates()
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Failed(plan.key, MergeFailure.WRITE_FAILED), outcome)
        assertTrue(store.deletes().isEmpty())
        assertTrue(store.links().isEmpty())
    }

    @Test
    fun aReadBackMissingDataDeletesNothing() {
        store.storedById[1] = Contact(id = 1, contactId = 1, source = "A")
        val plan = duplicates()
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Failed(plan.key, MergeFailure.WRITE_FAILED), outcome)
        assertTrue(store.deletes().isEmpty())
    }

    @Test
    fun aFailedVerifyAfterInsertDeletesOnlyTheNewRecord() {
        store.storedById[FakeMergeStore.NEW_ID] = Contact(
            id = FakeMergeStore.NEW_ID,
            contactId = FakeMergeStore.NEW_ID,
            source = "A"
        )
        val plan = mergePlan(
            MergeKind.DUPLICATES,
            record(1, "B", numbers = listOf("111")),
            record(2, "B", numbers = listOf("222"))
        )
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Failed(plan.key, MergeFailure.WRITE_FAILED), outcome)
        assertEquals(listOf("delete:${FakeMergeStore.NEW_ID}"), store.deletes())
    }

    @Test
    fun twoNewRecordsThatFailVerifyAreBothKept() {
        val other = FakeMergeStore.NEW_ID + 1
        store.newIds = listOf(FakeMergeStore.NEW_ID, other)
        store.storedById[FakeMergeStore.NEW_ID] = Contact(
            id = FakeMergeStore.NEW_ID,
            contactId = FakeMergeStore.NEW_ID,
            source = "A"
        )
        store.storedById[other] = Contact(id = other, contactId = other, source = "A")
        val plan = mergePlan(
            MergeKind.DUPLICATES,
            record(1, "B", numbers = listOf("111")),
            record(2, "B", numbers = listOf("222"))
        )
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Failed(plan.key, MergeFailure.WRITE_FAILED), outcome)
        assertTrue(store.deletes().isEmpty())
    }

    @Test
    fun theNewRecordThatVerifiesIsTheResult() {
        val other = FakeMergeStore.NEW_ID + 1
        store.newIds = listOf(FakeMergeStore.NEW_ID, other)
        store.storedById[FakeMergeStore.NEW_ID] = Contact(
            id = FakeMergeStore.NEW_ID,
            contactId = FakeMergeStore.NEW_ID,
            source = "A"
        )
        val plan = mergePlan(
            MergeKind.DUPLICATES,
            record(1, "B", numbers = listOf("111")),
            record(2, "B", numbers = listOf("222"))
        )
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Merged(plan.key), outcome)
        assertTrue(store.calls.contains("readBack:101"))
        assertTrue(store.deletes().none { it.split(':', ',').contains("100") })
        assertEquals(listOf("delete:1,2"), store.deletes())
    }

    @Test
    fun aFailedDeleteLinksNothing() {
        store.deleteWorks = false
        val plan = duplicates()
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Failed(plan.key, MergeFailure.DELETE_FAILED), outcome)
        assertTrue(store.links().isEmpty())
    }

    @Test
    fun aFailedLinkIsReported() {
        store.linkWorks = false
        val plan = duplicates()
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Failed(plan.key, MergeFailure.LINK_FAILED), outcome)
        assertEquals(listOf("link:1,3"), store.links())
    }

    @Test
    fun aGroupThatCannotBeCreatedWritesNothing() {
        store.groups = null
        val plan = duplicates()
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Failed(plan.key, MergeFailure.WRITE_FAILED), outcome)
        assertEquals(listOf("groups"), store.calls)
    }

    @Test
    fun tooManyNumbersForASimWritesNothing() {
        val plan = mergePlan(
            MergeKind.DUPLICATES,
            record(1, "A", numbers = listOf("111", "222"))
        )
        val outcome = writer.merge(plan, accountA, AccountAccess.SIM)
        assertEquals(MergeOutcome.Failed(plan.key, MergeFailure.SIM_LIMIT), outcome)
        assertTrue(store.calls.isEmpty())
    }

    @Test
    fun splitDetailsDeletesNothingAndLinksTheRest() {
        val plan = mergePlan(
            MergeKind.SPLIT_DETAILS,
            record(1, "A", numbers = listOf("111")),
            record(2, "B", emails = listOf("a@x.com"))
        )
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Merged(plan.key), outcome)
        // The writer asks the store to delete an empty list, so no record id may be among the deletes.
        assertTrue(store.deletes().all { it == "delete:" })
        assertEquals(listOf("link:1,2"), store.links())
    }

    @Test
    fun aCopyWritesAndDeletesNothing() {
        val plan = mergePlan(MergeKind.COPY, record(5, "B", numbers = listOf("555")))
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Merged(plan.key), outcome)
        assertTrue(store.calls.contains("insert"))
        // The writer asks the store to delete an empty list, so no record id may be among the deletes.
        assertTrue(store.deletes().all { it == "delete:" })
        assertTrue(store.links().isEmpty())
    }

    @Test
    fun aMoveDeletesTheRecordAfterTheWrite() {
        val plan = mergePlan(MergeKind.DUPLICATES, record(5, "B", numbers = listOf("555")))
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Merged(plan.key), outcome)
        val insertAt = store.calls.indexOf("insert")
        val deleteAt = store.calls.indexOfFirst { it.startsWith("delete") }
        assertTrue(insertAt >= 0 && deleteAt > insertAt)
        assertEquals(listOf("delete:5"), store.deletes())
    }

    /** A record in account B with a number and the SIP address, to move into A. */
    private fun movedWithSip() = record(1, "B", numbers = listOf("111")).copy(extras = sip)

    @Test
    fun aMoveAddsExtrasBeforeDeletingTheSource() {
        val plan = mergePlan(MergeKind.DUPLICATES, movedWithSip())
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Merged(plan.key), outcome)
        assertEquals(listOf("groups", "insert", "readBack:100", "extras:100", "delete:1"), store.calls)
        assertEquals(sip, store.addedExtras)
    }

    @Test
    fun extrasThatDoNotLandDeleteOnlyTheNewRecord() {
        store.extrasWork = false
        val plan = mergePlan(MergeKind.DUPLICATES, movedWithSip())
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Failed(plan.key, MergeFailure.WRITE_FAILED), outcome)
        assertEquals(listOf("delete:${FakeMergeStore.NEW_ID}"), store.deletes())
    }

    @Test
    fun aMergeAddsExtrasToTheKeptTarget() {
        val plan = mergePlan(
            MergeKind.DUPLICATES,
            record(1, "A", numbers = listOf("111")),
            record(2, "A", numbers = listOf("222")).copy(extras = sip)
        )
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Merged(plan.key), outcome)
        assertEquals(listOf("groups", "update:1", "readBack:1", "extras:1", "delete:2"), store.calls)
    }

    @Test
    fun aMoveIntoPrivateStorageThatWouldLoseExtrasWritesNothing() {
        val plan = mergePlan(MergeKind.DUPLICATES, movedWithSip())
        val outcome = writer.merge(plan, privateStorage, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Failed(plan.key, MergeFailure.NO_ROOM), outcome)
        assertTrue(store.calls.isEmpty())
    }

    @Test
    fun aMoveToASimThatWouldLoseExtrasWritesNothing() {
        val plan = mergePlan(MergeKind.DUPLICATES, movedWithSip())
        val outcome = writer.merge(plan, accountA, AccountAccess.SIM)
        assertEquals(MergeOutcome.Failed(plan.key, MergeFailure.NO_ROOM), outcome)
        assertTrue(store.calls.isEmpty())
    }

    @Test
    fun aCopyIntoPrivateStorageRunsWithoutExtras() {
        val plan = mergePlan(MergeKind.COPY, movedWithSip())
        val outcome = writer.merge(plan, privateStorage, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Merged(plan.key), outcome)
        assertTrue(store.calls.none { it.startsWith("extras") })
        // The writer asks the store to delete an empty list, which records a bare "delete:".
        assertEquals(listOf("delete:"), store.deletes())
    }

    @Test
    fun unreadableExtrasWriteNothing() {
        val plan = mergePlan(MergeKind.DUPLICATES, record(1, "B", numbers = listOf("111")).copy(extras = null))
        val outcome = writer.merge(plan, accountA, AccountAccess.WRITABLE)
        assertEquals(MergeOutcome.Failed(plan.key, MergeFailure.WRITE_FAILED), outcome)
        assertTrue(store.calls.isEmpty())
    }
}
