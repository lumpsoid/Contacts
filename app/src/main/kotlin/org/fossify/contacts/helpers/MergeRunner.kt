package org.fossify.contacts.helpers

import android.content.Context
import android.database.SQLException
import android.net.Uri
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.MergeFailure
import org.fossify.contacts.models.MergeOutcome
import org.fossify.contacts.models.MergePlan
import org.fossify.contacts.models.MergeRunResult
import org.joda.time.DateTime
import java.io.IOException

/** Owns the order of a merge run: backup first, then one plan at a time until done or cancelled. */
class MergeRunner(private val store: MergeStore, private val onError: (Exception) -> Unit) {
    /**
     * Blocking. [onProgress] gets (done, total) after each plan.
     * [backUp] gets every record of the run once and says whether the backup was written;
     * null runs without a backup.
     */
    fun run(
        plans: List<MergePlan>,
        destination: ContactSource,
        access: AccountAccess,
        backUp: ((ArrayList<Contact>) -> Boolean)?,
        isCancelled: () -> Boolean,
        onProgress: (done: Int, total: Int) -> Unit
    ): MergeRunResult {
        if (backUp != null && !backUp(backupContacts(plans))) {
            return MergeRunResult.BackupFailed
        }
        val writer = DedupeWriter(store)
        val outcomes = ArrayList<MergeOutcome>()
        for (plan in plans) {
            if (isCancelled()) {
                break
            }
            outcomes.add(mergeSafely(writer, plan, destination, access))
            onProgress(outcomes.size, plans.size)
        }
        return MergeRunResult.Finished(outcomes, backedUp = backUp != null, total = plans.size)
    }

    /** Merges [plan]; an unexpected error fails only this plan, so the rest of the run goes on. */
    private fun mergeSafely(
        writer: DedupeWriter,
        plan: MergePlan,
        destination: ContactSource,
        access: AccountAccess
    ): MergeOutcome {
        return try {
            writer.merge(plan, destination, access)
        } catch (e: SQLException) {
            onError(e)
            MergeOutcome.Failed(plan.key, MergeFailure.ERROR)
        } catch (e: IllegalStateException) {
            onError(e)
            MergeOutcome.Failed(plan.key, MergeFailure.ERROR)
        } catch (e: IllegalArgumentException) {
            onError(e)
            MergeOutcome.Failed(plan.key, MergeFailure.ERROR)
        } catch (e: SecurityException) {
            onError(e)
            MergeOutcome.Failed(plan.key, MergeFailure.ERROR)
        }
    }

    private fun backupContacts(plans: List<MergePlan>): ArrayList<Contact> =
        ArrayList(plans.flatMap { it.records }.distinctBy { it.key }.map { it.contact })
}

/** Writes [contacts] as a vCard backup to [uri]; true only when every contact was exported. Blocking. */
fun Context.exportMergeBackup(contacts: ArrayList<Contact>, uri: Uri): Boolean {
    var exported = false
    try {
        contentResolver.openOutputStream(uri)?.use { stream ->
            VcfExporter().exportContacts(this, stream, contacts, showExportingToast = false) {
                exported = it == VcfExporter.ExportResult.EXPORT_OK
            }
        }
    } catch (e: IOException) {
        exported = false
        showErrorToast(e)
    } catch (e: SecurityException) {
        exported = false
        showErrorToast(e)
    }
    return exported
}

/** File name the backup picker suggests: merge-2026-11-24-1830.vcf. */
fun mergeBackupFileName(now: DateTime): String = "merge-${now.toString("yyyy-MM-dd-HHmm")}.vcf"
