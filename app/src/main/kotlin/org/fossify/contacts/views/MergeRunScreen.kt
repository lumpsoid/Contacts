package org.fossify.contacts.views

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.WindowManager
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.showErrorToast
import org.fossify.contacts.R
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.databinding.ActivityDedupeBinding
import org.fossify.contacts.helpers.MergeSession
import org.fossify.contacts.models.MergeRunResult

/** Shows a merge session on the dedupe screen: progress while it runs, the result page when it is done. */
class MergeRunScreen(private val activity: SimpleActivity, private val binding: ActivityDedupeBinding) {
    fun show(state: MergeSession.State) {
        when (state) {
            is MergeSession.State.Running -> showRunning(state.done, state.total)
            is MergeSession.State.Done -> showResult(state.result, state.labels)
        }
    }

    private fun showRunning(done: Int, total: Int) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.dedupeList.beGone()
        binding.dedupeBottomBar.beGone()
        binding.dedupeEmptyHolder.beGone()
        binding.dedupeProgressHolder.beVisible()
        binding.dedupeCancel.beVisible()
        binding.dedupeProgressLabel.text = activity.getString(R.string.dedupe_merging, done, total)
    }

    /** Shows the outcome of a run on the result page. */
    private fun showResult(result: MergeRunResult, labels: MergeSession.Labels) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.dedupeList.beGone()
        binding.dedupeBottomBar.beGone()
        binding.dedupeProgressHolder.beGone()
        binding.dedupeCancel.beGone()
        binding.dedupeResult.root.beVisible()
        MergeResultPage(activity, binding.dedupeResult) { backupUri ->
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(backupUri, "text/x-vcard")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                activity.showErrorToast(e)
            }
        }.show(result, labels.names, labels.destinationName, labels.backupUri)
    }
}
