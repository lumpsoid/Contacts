package org.fossify.contacts.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getPublicContactSourceSync
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.contacts.R
import org.fossify.contacts.adapters.DedupeAdapter
import org.fossify.contacts.databinding.ActivityDedupeBinding
import org.fossify.contacts.dialogs.ConfirmMergeDialog
import org.fossify.contacts.extensions.config
import org.fossify.contacts.helpers.DedupeSelection
import org.fossify.contacts.helpers.DuplicateScanner
import org.fossify.contacts.helpers.MergeRunner
import org.fossify.contacts.helpers.mergeBackupFileName
import org.fossify.contacts.helpers.preferredDestination
import org.fossify.contacts.helpers.summarize
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.MergeRunResult
import org.fossify.contacts.models.ScanResult
import org.fossify.contacts.views.MergeResultPage
import org.joda.time.DateTime
import java.util.concurrent.atomic.AtomicBoolean

/** Lists the duplicate contacts found on the device, so the user can pick which ones to merge. */
class DedupeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDedupeBinding::inflate)
    private var selection: DedupeSelection? = null
    private var accountNames: Map<String, String> = emptyMap()
    private var restoredState: ArrayList<String>? = null
    private val cancelled = AtomicBoolean(false)
    private var pendingDestination: Pair<ContactSource, AccountAccess>? = null
    private var destinationName = ""
    private val backupPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-vcard")
    ) { uri ->
        val destination = pendingDestination
        if (uri != null && destination != null) {
            startRun(destination, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.dedupeHolder))
        setupMaterialScrollListener(binding.dedupeList, binding.dedupeAppbar)
        updateTextColors(binding.dedupeCoordinator)
        binding.dedupeClose.setOnClickListener { finish() }
        binding.dedupeMerge.setOnClickListener { pickDestination() }
        binding.dedupeCancel.setOnClickListener { cancelled.set(true) }
        binding.dedupeResult.dedupeDone.setOnClickListener { finish() }
        restoredState = savedInstanceState?.getStringArrayList(SELECTION_STATE)
        startScan()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.dedupeAppbar, NavigationIcon.Arrow)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        (selection?.saveState() ?: restoredState)?.let { outState.putStringArrayList(SELECTION_STATE, it) }
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

    /** Lists the writable accounts to save the merged contacts into, the preferred one selected. */
    private fun pickDestination() {
        val plans = selection?.plans().orEmpty()
        if (plans.isEmpty()) {
            return
        }
        ensureBackgroundThread {
            val sources = DuplicateScanner(this).writableSources()
            runOnUiThread {
                if (isDestroyed || sources.isEmpty()) {
                    return@runOnUiThread
                }
                val writable = sources.map { it.first }
                val preferred = preferredDestination(plans, writable, config.lastUsedContactSource)
                val items = ArrayList(sources.mapIndexed { i, (source, access) ->
                    val title = if (access == AccountAccess.SIM) {
                        getString(R.string.dedupe_sim_limit, source.publicName)
                    } else {
                        source.publicName
                    }
                    RadioItem(i, title)
                })
                RadioGroupDialog(
                    this, items, sources.indexOfFirst { it.first == preferred }, R.string.dedupe_choose_destination
                ) { picked ->
                    confirmMerge(sources[picked as Int])
                }
            }
        }
    }

    /** Shows what the merge will do; a yes starts the run, after the backup picker when a backup is wanted. */
    private fun confirmMerge(destination: Pair<ContactSource, AccountAccess>) {
        val plans = selection?.plans().orEmpty()
        ConfirmMergeDialog(
            this, summarize(plans, destination.first), destination.first.publicName, accountNames
        ) { backUp ->
            if (backUp) {
                pendingDestination = destination
                backupPicker.launch(mergeBackupFileName(DateTime.now()))
            } else {
                startRun(destination, null)
            }
        }
    }

    /** Merges on a background thread; the screen stays on, and leaving it does not stop the run. */
    private fun startRun(destination: Pair<ContactSource, AccountAccess>, backupUri: Uri?) {
        val plans = selection?.plans().orEmpty()
        destinationName = destination.first.publicName
        cancelled.set(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.dedupeList.beGone()
        binding.dedupeBottomBar.beGone()
        binding.dedupeProgressHolder.beVisible()
        binding.dedupeCancel.beVisible()
        binding.dedupeProgressLabel.text = getString(R.string.dedupe_merging, 0, plans.size)
        val runner = MergeRunner(applicationContext)
        val onProgress = { done: Int, total: Int ->
            runOnUiThread {
                if (!isDestroyed) {
                    binding.dedupeProgressLabel.text = getString(R.string.dedupe_merging, done, total)
                }
            }
        }
        ensureBackgroundThread {
            val result = runner.run(
                plans, destination.first, destination.second, backupUri, { cancelled.get() }, onProgress
            )
            runOnUiThread {
                if (!isDestroyed) {
                    showResult(result)
                }
            }
        }
    }

    /** Shows the outcome of a run on the result page. */
    private fun showResult(result: MergeRunResult) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.dedupeProgressHolder.beGone()
        binding.dedupeCancel.beGone()
        binding.dedupeResult.root.beVisible()
        val names = selection?.result?.let { scan ->
            scan.duplicates.associate { it.key to it.people.first().displayName() } +
                scan.splitDetails.associate { it.key to it.person.displayName() }
        }.orEmpty()
        MergeResultPage(this, binding.dedupeResult) { backupUri ->
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(backupUri, "text/x-vcard")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                showErrorToast(e)
            }
        }.show(result, names, destinationName)
    }

    companion object {
        private const val SELECTION_STATE = "dedupe_selection_state"
    }
}
