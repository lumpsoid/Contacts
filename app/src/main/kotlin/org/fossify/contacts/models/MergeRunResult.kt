package org.fossify.contacts.models

/** The end of a run: the backup failed and nothing was merged, or the plans ran (maybe cut short by cancel). */
sealed class MergeRunResult {
    data object BackupFailed : MergeRunResult()

    /**
     * [total] plans were asked for; [outcomes] holds one entry per plan that ran, in order.
     * [backedUp]: the run wrote a backup before merging.
     */
    data class Finished(
        val outcomes: List<MergeOutcome>,
        val backedUp: Boolean,
        val total: Int
    ) : MergeRunResult() {
        val cancelled: Boolean get() = outcomes.size < total

        fun mergedCount() = outcomes.count { it is MergeOutcome.Merged }

        fun failures(): List<MergeOutcome.Failed> = outcomes.filterIsInstance<MergeOutcome.Failed>()

        /** Plans that never started because the run was cancelled. */
        fun skippedCount() = total - outcomes.size
    }
}
