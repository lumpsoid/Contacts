package org.fossify.contacts.models

import android.net.Uri

/** The end of a run: the backup failed and nothing was merged, or the plans ran (maybe cut short by cancel). */
sealed class MergeRunResult {
    data object BackupFailed : MergeRunResult()

    data class Finished(
        val outcomes: List<MergeOutcome>,
        val backupUri: Uri?,
        val cancelled: Boolean
    ) : MergeRunResult()
}
