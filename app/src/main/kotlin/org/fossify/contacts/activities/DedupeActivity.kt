package org.fossify.contacts.activities

import android.os.Bundle
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getPublicContactSourceSync
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.contacts.R
import org.fossify.contacts.adapters.DedupeAdapter
import org.fossify.contacts.databinding.ActivityDedupeBinding
import org.fossify.contacts.helpers.DedupeSelection
import org.fossify.contacts.helpers.DuplicateScanner
import org.fossify.contacts.models.ScanResult

/** Lists the duplicate contacts found on the device, so the user can pick which ones to merge. */
class DedupeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDedupeBinding::inflate)
    private var selection: DedupeSelection? = null
    private var accountNames: Map<String, String> = emptyMap()
    private var restoredState: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.dedupeHolder))
        setupMaterialScrollListener(binding.dedupeList, binding.dedupeAppbar)
        updateTextColors(binding.dedupeCoordinator)
        binding.dedupeClose.setOnClickListener { finish() }
        restoredState = savedInstanceState?.getStringArrayList(SELECTION_STATE)
        startScan()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.dedupeAppbar, NavigationIcon.Arrow)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selection?.let { outState.putStringArrayList(SELECTION_STATE, it.saveState()) }
    }

    /** Scans on a background thread and then shows the review on the UI thread. */
    private fun startScan() {
        binding.dedupeProgressHolder.beVisible()
        binding.dedupeProgressLabel.text = getString(R.string.dedupe_scanning)
        DuplicateScanner(this).scan { result ->
            val sources = ArrayList(ContactsHelper(this).getDeviceContactSources())
            val names = (result.duplicates.flatMap { it.records() } + result.splitDetails.flatMap { it.person.records })
                .map { it.contact.source }
                .distinct()
                .associateWith { getPublicContactSourceSync(it, sources) }
            runOnUiThread {
                if (!isDestroyed) {
                    accountNames = names
                    showReview(result)
                }
            }
        }
    }

    private fun showReview(result: ScanResult) {
        binding.dedupeProgressHolder.beGone()
        if (result.isEmpty()) {
            binding.dedupeEmptyHolder.beVisible()
            return
        }
        val newSelection = DedupeSelection(result)
        restoredState?.let { newSelection.restoreState(it) }
        selection = newSelection
        binding.dedupeList.adapter = DedupeAdapter(this, newSelection, accountNames) { updateSummary() }
        binding.dedupeList.beVisible()
        binding.dedupeBottomBar.beVisible()
        updateSummary()
    }

    private fun updateSummary() {
        val plans = selection?.plans().orEmpty()
        binding.dedupeSummary.text = getString(
            R.string.dedupe_summary, plans.size, plans.sumOf { it.records.size }, plans.size
        )
        binding.dedupeMerge.isEnabled = plans.isNotEmpty()
    }

    companion object {
        private const val SELECTION_STATE = "dedupe_selection_state"
    }
}
