package org.fossify.contacts.helpers

import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.MergeChoices
import org.fossify.contacts.models.MergeKind
import org.fossify.contacts.models.MergePlan
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.TransferMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountTransferTest {
    private val accountA = ContactSource("A", "com.google", "A")

    private fun record(
        id: Int,
        source: String,
        type: String = "com.google",
        access: AccountAccess = AccountAccess.WRITABLE
    ) = MergeRecord(Contact(id = id, contactId = id, source = source), type, access)

    @Test
    fun aMoveIsAOneRecordDuplicatesPlan() {
        val rec = record(1, "B")
        assertEquals(
            listOf(MergePlan(rec.key, MergeKind.DUPLICATES, listOf(rec), MergeChoices())),
            transferPlans(listOf(rec), accountA, TransferMode.MOVE)
        )
    }

    @Test
    fun aCopyIsACopyPlan() {
        val rec = record(1, "B")
        assertEquals(
            listOf(MergePlan(rec.key, MergeKind.COPY, listOf(rec), MergeChoices())),
            transferPlans(listOf(rec), accountA, TransferMode.COPY)
        )
    }

    @Test
    fun aRecordAlreadyThereIsLeftOut() {
        val rec = record(1, "A")
        assertEquals(emptyList<MergePlan>(), transferPlans(listOf(rec), accountA, TransferMode.MOVE))
        assertEquals(emptyList<MergePlan>(), transferPlans(listOf(rec), accountA, TransferMode.COPY))
    }

    @Test
    fun aMoveLeavesOutWhatCannotBeDeleted() {
        val rec = record(3, "WhatsApp", type = "com.whatsapp", access = AccountAccess.READ_ONLY)
        assertEquals(emptyList<MergePlan>(), transferPlans(listOf(rec), accountA, TransferMode.MOVE))
        assertEquals(
            listOf(MergePlan(rec.key, MergeKind.COPY, listOf(rec), MergeChoices())),
            transferPlans(listOf(rec), accountA, TransferMode.COPY)
        )
    }

    @Test
    fun aRecordInASameNamedAccountOfAnotherTypeIsMoved() {
        val rec = record(1, "A", type = "com.other")
        assertEquals(
            listOf(MergePlan(rec.key, MergeKind.DUPLICATES, listOf(rec), MergeChoices())),
            transferPlans(listOf(rec), accountA, TransferMode.MOVE)
        )
    }

    @Test
    fun aPrivateRecordMovesToADeviceAccount() {
        val rec = record(2, SMT_PRIVATE, type = SMT_PRIVATE)
        assertEquals(
            listOf(MergePlan(rec.key, MergeKind.DUPLICATES, listOf(rec), MergeChoices())),
            transferPlans(listOf(rec), accountA, TransferMode.MOVE)
        )
        val privateDestination = ContactSource(SMT_PRIVATE, SMT_PRIVATE, "Phone storage")
        assertEquals(emptyList<MergePlan>(), transferPlans(listOf(rec), privateDestination, TransferMode.MOVE))
    }

    @Test
    fun aMixedSelectionKeepsOrderAndDropsOnlyWhatItMust() {
        val r1 = record(1, "B")
        val r2 = record(2, "A")
        val r3 = record(3, "WhatsApp", type = "com.whatsapp", access = AccountAccess.READ_ONLY)
        val r4 = record(4, "C")
        assertEquals(
            listOf(r1.key, r4.key),
            transferPlans(listOf(r1, r2, r3, r4), accountA, TransferMode.MOVE).map { it.key }
        )
    }
}
