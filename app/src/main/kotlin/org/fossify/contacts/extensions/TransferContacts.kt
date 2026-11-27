package org.fossify.contacts.extensions

import android.content.Context
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Group
import org.fossify.contacts.R
import org.fossify.contacts.helpers.DuplicateScanner
import org.fossify.contacts.helpers.GroupMemberships
import org.fossify.contacts.helpers.MergeRunner
import org.fossify.contacts.helpers.ProviderMergeStore
import org.fossify.contacts.helpers.transferPlans
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.MergeRunResult
import org.fossify.contacts.models.TransferMode
import java.util.concurrent.atomic.AtomicBoolean

/** True while a run writes; a second run started meanwhile would copy the same contacts again. */
private val transferRunning = AtomicBoolean(false)

/**
 * Asks for the account to move or copy [contacts] into, writes them there through the merge engine
 * and toasts how many made it. [onDone] runs on the UI thread after the run with the number moved or copied,
 * unless the screen is gone.
 */
fun BaseSimpleActivity.transferContacts(contacts: List<Contact>, mode: TransferMode, onDone: (done: Int) -> Unit) {
    if (contacts.isEmpty()) return
    ensureBackgroundThread {
        val scanner = DuplicateScanner(this)
        val sources = scanner.writableSources()
        val stored = ContactsHelper(this).getStoredGroupsSync()
        val records = scanner.toRecords(contacts.map { withOwnGroups(it, stored) })
        runOnUiThread {
            if (!isDestroyed) pickAccount(sources, mode) { runTransfer(records, it, mode, onDone) }
        }
    }
}

/** [contact] with the groups of its own raw contact; the list holds the groups of its whole aggregate. */
private fun Context.withOwnGroups(contact: Contact, stored: List<Group>): Contact {
    if (contact.isPrivate()) return contact
    val ids = GroupMemberships(this).groupIdsOf(contact.id) ?: return contact
    return contact.copy(groups = ArrayList(stored.filter { it.id in ids }))
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
    onDone: (done: Int) -> Unit
) {
    if (!transferRunning.compareAndSet(false, true)) return
    val plans = transferPlans(records, destination.first, mode)
    val context = applicationContext
    ensureBackgroundThread {
        val runner = MergeRunner(ProviderMergeStore(context)) { context.showErrorToast(it) }
        val result = try {
            runner.run(plans, destination.first, destination.second, null, { false }) { _, _ -> }
        } finally {
            transferRunning.set(false)
        }
        val finished = result as? MergeRunResult.Finished
        val done = finished?.mergedCount() ?: 0
        val textId = if (mode == TransferMode.MOVE) R.string.transfer_moved else R.string.transfer_copied
        val summary = context.getString(textId, done, records.size)
        val failure = finished?.failures()?.firstOrNull()
        val text = if (failure == null) summary else summary + "\n" + context.failureText(failure.reason)
        runOnUiThread {
            context.toast(text)
            if (!isDestroyed) onDone(done)
        }
    }
}
