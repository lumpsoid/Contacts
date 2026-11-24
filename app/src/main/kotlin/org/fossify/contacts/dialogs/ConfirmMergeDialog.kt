package org.fossify.contacts.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.contacts.R
import org.fossify.contacts.databinding.DialogConfirmMergeBinding
import org.fossify.contacts.models.MergeSummary

/** Asks before a merge run: what gets merged and deleted where, and whether to back up first. */
class ConfirmMergeDialog(
    activity: BaseSimpleActivity,
    summary: MergeSummary,
    destinationName: String,
    accountNames: Map<String, String>,
    callback: (backUp: Boolean) -> Unit
) {
    init {
        val binding = DialogConfirmMergeBinding.inflate(activity.layoutInflater)
        val lines = arrayListOf(activity.getString(R.string.dedupe_confirm_merge, summary.groups, destinationName))
        summary.deletedPerAccount.forEach { (account, count) ->
            val name = accountNames[account]
                ?: account.ifEmpty { activity.getString(org.fossify.commons.R.string.phone_storage) }
            lines.add(activity.getString(R.string.dedupe_confirm_deletes, name, count))
        }
        if (summary.deletedPerAccount.isNotEmpty()) {
            lines.add(activity.getString(R.string.dedupe_confirm_synced))
        }
        binding.confirmMergeMessage.text = lines.joinToString("\n\n")
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.merge) { _, _ ->
                callback(binding.confirmMergeBackup.isChecked)
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.find_and_merge_duplicates) { dialog ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(activity.getColor(org.fossify.commons.R.color.md_red_700))
                }
            }
    }
}
