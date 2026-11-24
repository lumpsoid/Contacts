package org.fossify.contacts.helpers

import android.content.Context
import android.database.SQLException
import android.net.Uri
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.MergeFailure
import org.fossify.contacts.models.MergeOutcome
import org.fossify.contacts.models.MergePlan
import org.fossify.contacts.models.MergeRunResult
import org.joda.time.DateTime
import java.io.IOException

/** Owns the order of a merge run: backup first, then one plan at a time until done or cancelled. */
class MergeRunner(private val context: Context) {
    /** Blocking. [onProgress] gets (done, total) after each plan. */
    fun run(
        plans: List<MergePlan>,
        destination: ContactSource,
        access: AccountAccess,
        backupUri: Uri?,
        isCancelled: () -> Boolean,
        onProgress: (done: Int, total: Int) -> Unit
    ): MergeRunResult {
        if (backupUri != null && !backUp(plans, backupUri)) {
            return MergeRunResult.BackupFailed
        }
        val writer = DedupeWriter(context)
        val outcomes = ArrayList<MergeOutcome>()
        for (plan in plans) {
            if (isCancelled()) {
                break
            }
            outcomes.add(mergeSafely(writer, plan, destination, access))
            onProgress(outcomes.size, plans.size)
        }
        return MergeRunResult.Finished(outcomes, backupUri, total = plans.size)
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
            context.showErrorToast(e)
            MergeOutcome.Failed(plan.key, MergeFailure.ERROR)
        } catch (e: IllegalStateException) {
            context.showErrorToast(e)
            MergeOutcome.Failed(plan.key, MergeFailure.ERROR)
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
            MergeOutcome.Failed(plan.key, MergeFailure.ERROR)
        } catch (e: SecurityException) {
            context.showErrorToast(e)
            MergeOutcome.Failed(plan.key, MergeFailure.ERROR)
        }
    }

    private fun backUp(plans: List<MergePlan>, uri: Uri): Boolean {
        val contacts = ArrayList(plans.flatMap { it.records }.distinctBy { it.key }.map { it.contact })
        var exported = false
        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                VcfExporter().exportContacts(context, stream, contacts, showExportingToast = false) {
                    exported = it == VcfExporter.ExportResult.EXPORT_OK
                }
            }
        } catch (e: IOException) {
            context.showErrorToast(e)
        } catch (e: SecurityException) {
            context.showErrorToast(e)
        }
        return exported
    }
}

/** File name the backup picker suggests: merge-2026-11-24-1830.vcf. */
fun mergeBackupFileName(now: DateTime): String = "merge-${now.toString("yyyy-MM-dd-HHmm")}.vcf"
