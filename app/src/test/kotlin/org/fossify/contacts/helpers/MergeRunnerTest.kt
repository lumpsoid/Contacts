package org.fossify.contacts.helpers

import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Email
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.MergeChoices
import org.fossify.contacts.models.MergeFailure
import org.fossify.contacts.models.MergeKind
import org.fossify.contacts.models.MergeOutcome
import org.fossify.contacts.models.MergePlan
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.MergeRunResult
import org.joda.time.DateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeRunnerTest {
    private val accountA = ContactSource("A", "com.google", "A")
    private val store = FakeMergeStore()
    private val errors = ArrayList<Exception>()
    private val runner = MergeRunner(store) { errors.add(it) }

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

    private fun plan(key: String, vararg records: MergeRecord) =
        MergePlan(key, MergeKind.DUPLICATES, records.toList(), MergeChoices())

    private fun run(
        plans: List<MergePlan>,
        backUp: ((ArrayList<Contact>) -> Boolean)? = null,
        isCancelled: () -> Boolean = { false },
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) = runner.run(plans, accountA, AccountAccess.WRITABLE, backUp, isCancelled, onProgress)

    @Test
    fun aFailedBackupMergesNothing() {
        val plans = listOf(plan("p1", record(1, "A"), record(2, "A")))
        val result = run(plans, backUp = { false })
        assertEquals(MergeRunResult.BackupFailed, result)
        assertTrue(store.calls.isEmpty())
    }

    @Test
    fun theBackupGetsEveryRecordOnce() {
        var backedUpIds = emptyList<Int>()
        val backUp = { contacts: ArrayList<Contact> ->
            backedUpIds = contacts.map { it.id }
            true
        }
        val plans = listOf(
            plan("p1", record(1, "A", numbers = listOf("111")), record(2, "A", numbers = listOf("222"))),
            plan(
                "p2",
                record(1, "A", numbers = listOf("111")),
                record(3, "WhatsApp", type = "com.whatsapp", access = AccountAccess.READ_ONLY)
            )
        )
        val result = run(plans, backUp = backUp)
        assertEquals(listOf(1, 2, 3), backedUpIds)
        assertTrue(result is MergeRunResult.Finished && result.backedUp)
    }

    @Test
    fun noBackupRunsWithoutOne() {
        val result = run(listOf(plan("p1", record(1, "A"))))
        assertTrue(result is MergeRunResult.Finished && !result.backedUp)
    }

    @Test
    fun cancelStopsBeforeTheNextPlan() {
        var cancelled = false
        val plans = listOf(plan("p1", record(1, "A")), plan("p2", record(5, "A")))
        val result = run(
            plans,
            isCancelled = { cancelled },
            onProgress = { _, _ -> cancelled = true }
        ) as MergeRunResult.Finished
        assertEquals(1, result.outcomes.size)
        assertTrue(result.cancelled)
    }

    @Test
    fun anErrorFailsOnlyItsPlan() {
        store.failUpdateWith = IllegalStateException("boom")
        val plans = listOf(plan("p1", record(1, "A"), record(2, "A")), plan("p2", record(5, "A")))
        val result = run(plans, onProgress = { _, _ -> store.failUpdateWith = null }) as MergeRunResult.Finished
        assertEquals(
            listOf(MergeOutcome.Failed("p1", MergeFailure.ERROR), MergeOutcome.Merged("p2")),
            result.outcomes
        )
        assertEquals(1, errors.size)
        assertEquals("boom", errors.single().message)
    }

    @Test
    fun theBackupFileNameCarriesTheMinuteOfTheRun() {
        assertEquals("merge-2026-11-24-1830.vcf", mergeBackupFileName(DateTime(2026, 11, 24, 18, 30)))
    }
}
