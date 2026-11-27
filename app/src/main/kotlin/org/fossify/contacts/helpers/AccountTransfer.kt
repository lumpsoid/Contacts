package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.ContactSource
import org.fossify.contacts.models.MergeChoices
import org.fossify.contacts.models.MergeKind
import org.fossify.contacts.models.MergePlan
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.TransferMode

/**
 * One plan per record of [records] to write into [destination]. A move is a one-record DUPLICATES plan:
 * no record is in [destination], so the engine inserts a new one and deletes the record once it verifies.
 * Left out: records already in [destination] and, for a move, records that cannot be deleted.
 */
fun transferPlans(records: List<MergeRecord>, destination: ContactSource, mode: TransferMode): List<MergePlan> {
    val kind = if (mode == TransferMode.MOVE) MergeKind.DUPLICATES else MergeKind.COPY
    return records
        .filter { !it.isIn(destination) && (mode == TransferMode.COPY || it.isWritable()) }
        .map { MergePlan(it.key, kind, listOf(it), MergeChoices()) }
}
