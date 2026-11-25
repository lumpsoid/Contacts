package org.fossify.contacts.views

import android.net.Uri
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.contacts.R
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.databinding.ItemDedupeFailureBinding
import org.fossify.contacts.databinding.ItemDedupeStatBinding
import org.fossify.contacts.databinding.ViewDedupeResultBinding
import org.fossify.contacts.extensions.failureText
import org.fossify.contacts.models.MergeOutcome
import org.fossify.contacts.models.MergeRunResult

/** Fills the result page of a merge run: its status, the counts, the groups not merged and the backup. */
class MergeResultPage(
    private val activity: SimpleActivity,
    private val binding: ViewDedupeResultBinding,
    private val onOpenBackup: (Uri) -> Unit
) {
    private val textColor = activity.getProperTextColor()
    private val primaryColor = activity.getProperPrimaryColor()
    private val warningColor = activity.getColor(org.fossify.commons.R.color.md_orange_700)
    private val errorColor = activity.getColor(org.fossify.commons.R.color.md_red_700)

    /**
     * [names]: display name by plan key; [destinationName]: the account the run saved into.
     * [backupUri]: where the backup was written, shown when the run backed up.
     */
    fun show(result: MergeRunResult, names: Map<String, String>, destinationName: String, backupUri: Uri?) {
        activity.updateTextColors(binding.root)
        when (result) {
            is MergeRunResult.BackupFailed -> {
                val reason = activity.getString(R.string.dedupe_backup_failed)
                showStatus(CROSS, errorColor, R.string.dedupe_result_none, reason)
                binding.dedupeResultStats.beGone()
            }

            is MergeRunResult.Finished -> showFinished(result, names, destinationName, backupUri)
        }
    }

    private fun showFinished(
        result: MergeRunResult.Finished,
        names: Map<String, String>,
        destinationName: String,
        backupUri: Uri?
    ) {
        val merged = result.mergedCount()
        val failures = result.failures()
        val subtitle = if (merged > 0) activity.getString(R.string.dedupe_result_saved_to, destinationName) else ""
        when {
            result.cancelled -> showStatus(CROSS, textColor, R.string.dedupe_result_cancelled, subtitle)
            failures.isEmpty() -> showStatus(CHECK, primaryColor, R.string.dedupe_result_complete, subtitle)
            merged > 0 -> showStatus(CHECK, warningColor, R.string.dedupe_result_partial, subtitle)
            else -> showStatus(CROSS, errorColor, R.string.dedupe_result_none, subtitle)
        }

        setStat(binding.dedupeResultMerged, merged, R.string.dedupe_result_merged, primaryColor)
        setStat(binding.dedupeResultFailed, failures.size, R.string.dedupe_result_not_merged, errorColor)
        setStat(binding.dedupeResultSkipped, result.skippedCount(), R.string.dedupe_result_not_started, textColor)
        binding.dedupeResultSkipped.root.beVisibleIf(result.cancelled)
        binding.dedupeResultNote.beVisibleIf(result.cancelled)

        showFailures(failures, names)
        showBackup(if (result.backedUp) backupUri else null)
    }

    private fun showStatus(iconId: Int, color: Int, titleId: Int, subtitle: String) {
        binding.dedupeResultIcon.setImageResource(iconId)
        binding.dedupeResultIcon.applyColorFilter(color)
        binding.dedupeResultTitle.text = activity.getString(titleId)
        binding.dedupeResultSubtitle.text = subtitle
        binding.dedupeResultSubtitle.beVisibleIf(subtitle.isNotEmpty())
    }

    /** A count tile; the number takes [color] only when it is not zero, so an empty tile stays quiet. */
    private fun setStat(stat: ItemDedupeStatBinding, value: Int, labelId: Int, color: Int) {
        stat.dedupeStatValue.text = value.toString()
        stat.dedupeStatValue.setTextColor(if (value > 0) color else textColor)
        stat.dedupeStatLabel.text = activity.getString(labelId)
    }

    private fun showFailures(failures: List<MergeOutcome.Failed>, names: Map<String, String>) {
        binding.dedupeResultFailuresLabel.beVisibleIf(failures.isNotEmpty())
        binding.dedupeResultFailures.beVisibleIf(failures.isNotEmpty())
        binding.dedupeResultFailures.removeAllViews()
        failures.forEach { failure ->
            val row = ItemDedupeFailureBinding.inflate(activity.layoutInflater, binding.dedupeResultFailures, false)
            row.dedupeFailureName.text = names[failure.key] ?: failure.key
            row.dedupeFailureReason.text = activity.failureText(failure.reason)
            activity.updateTextColors(row.root)
            binding.dedupeResultFailures.addView(row.root)
        }
    }

    private fun showBackup(backupUri: Uri?) {
        binding.dedupeResultBackupLabel.beVisibleIf(backupUri != null)
        binding.dedupeResultBackup.beVisibleIf(backupUri != null)
        binding.dedupeResultBackupIcon.applyColorFilter(textColor)
        if (backupUri != null) {
            binding.dedupeOpenBackup.setOnClickListener { onOpenBackup(backupUri) }
        }
    }

    companion object {
        private val CHECK = org.fossify.commons.R.drawable.ic_check_circle_vector
        private val CROSS = org.fossify.commons.R.drawable.ic_cross_vector
    }
}
