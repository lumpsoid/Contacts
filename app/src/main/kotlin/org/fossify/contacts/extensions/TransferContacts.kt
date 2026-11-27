package org.fossify.contacts.extensions

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.contacts.R
import org.fossify.contacts.helpers.DuplicateScanner
import org.fossify.contacts.helpers.MergeRunner
import org.fossify.contacts.helpers.ProviderMergeStore
import org.fossify.contacts.helpers.transferPlans
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.MergeRunResult
import org.fossify.contacts.models.TransferMode

/**
 * Asks for the account to move or copy [contacts] into, writes them there through the merge engine
 * and toasts how many made it. [onDone] runs on the UI thread after the run, unless the screen is gone.
 */
fun BaseSimpleActivity.transferContacts(contacts: List<Contact>, mode: TransferMode, onDone: () -> Unit) {
    if (contacts.isEmpty()) return
    ensureBackgroundThread {
        val scanner = DuplicateScanner(this)
        val sources = scanner.writableSources()
        val records = scanner.toRecords(contacts)
        runOnUiThread {
            if (!isDestroyed) pickAccount(sources, mode) { runTransfer(records, it, mode, onDone) }
        }
    }
}

private fun BaseSimpleActivity.pickAccount(
    sources: List<Pair<ContactSource, AccountAccess>>,
    mode: TransferMode,
    callback: (Pair<ContactSource, AccountAccess>) -> Unit
) {
    val items = ArrayList(sources.mapIndexed { i, (source, access) ->
        val title = if (access == AccountAccess.SIM) {
            getString(R.string.dedupe_sim_limit, source.publicName)
        } else {
            source.publicName
        }
        RadioItem(i, title)
    })
    RadioGroupDialog(
        this, items, -1, if (mode == TransferMode.MOVE) R.string.transfer_move else R.string.transfer_copy
    ) { callback(sources[it as Int]) }
}

private fun BaseSimpleActivity.runTransfer(
    records: List<MergeRecord>,
    destination: Pair<ContactSource, AccountAccess>,
    mode: TransferMode,
    onDone: () -> Unit
) {
    val plans = transferPlans(records, destination.first, mode)
    val context = applicationContext
    ensureBackgroundThread {
        val runner = MergeRunner(ProviderMergeStore(context)) { context.showErrorToast(it) }
        val result = runner.run(plans, destination.first, destination.second, null, { false }) { _, _ -> }
        val done = (result as? MergeRunResult.Finished)?.mergedCount() ?: 0
        val textId = if (mode == TransferMode.MOVE) R.string.transfer_moved else R.string.transfer_copied
        runOnUiThread {
            context.toast(context.getString(textId, done, records.size))
            if (!isDestroyed) onDone()
        }
    }
}
