package org.fossify.contacts.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.R
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.databinding.ItemDedupeCardBinding
import org.fossify.contacts.databinding.ItemDedupeConflictBinding
import org.fossify.contacts.databinding.ItemDedupeHeaderBinding
import org.fossify.contacts.databinding.ItemDedupeMemberBinding
import org.fossify.contacts.extensions.conflictOption
import org.fossify.contacts.extensions.conflictText
import org.fossify.contacts.extensions.memberDetails
import org.fossify.contacts.extensions.resultPreview
import org.fossify.contacts.helpers.ContactLinkHelper
import org.fossify.contacts.helpers.DedupeSelection
import org.fossify.contacts.helpers.buildMergedContact
import org.fossify.contacts.helpers.findConflicts
import org.fossify.contacts.helpers.holderOf
import org.fossify.contacts.models.ConflictField
import org.fossify.contacts.models.DedupeListItem
import org.fossify.contacts.models.MatchConfidence
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.ScannedPerson

/** The duplicates review list: section headers and one card per duplicate group or split detail. */
class DedupeAdapter(
    private val activity: SimpleActivity,
    private val selection: DedupeSelection,
    private val accountNames: Map<String, String>,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items: List<DedupeListItem> = buildList {
        if (selection.result.duplicates.isNotEmpty()) {
            add(DedupeListItem.Header(R.string.dedupe_section_duplicates))
            addAll(selection.result.duplicates.map { DedupeListItem.Duplicates(it) })
        }
        if (selection.result.splitDetails.isNotEmpty()) {
            add(DedupeListItem.Header(R.string.dedupe_section_split))
            addAll(selection.result.splitDetails.map { DedupeListItem.Split(it) })
        }
    }

    private class Holder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) =
        if (items[position] is DedupeListItem.Header) TYPE_HEADER else TYPE_CARD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = activity.layoutInflater
        val view = if (viewType == TYPE_HEADER) {
            ItemDedupeHeaderBinding.inflate(inflater, parent, false).root
        } else {
            ItemDedupeCardBinding.inflate(inflater, parent, false).root
        }
        return Holder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DedupeListItem.Header -> {
                val header = ItemDedupeHeaderBinding.bind(holder.itemView).dedupeHeader
                header.text = activity.getString(item.titleId)
                header.setTextColor(activity.getProperPrimaryColor())
            }

            is DedupeListItem.Duplicates -> {
                val badgeId = if (item.group.confidence == MatchConfidence.HIGH) {
                    R.string.dedupe_same_name_and_detail
                } else {
                    R.string.dedupe_same_name_only
                }
                bindCard(holder.itemView, item.group.key, item.group.people, activity.getString(badgeId))
            }

            is DedupeListItem.Split -> bindCard(holder.itemView, item.detail.key, listOf(item.detail.person), null)
        }
    }

    private fun bindCard(view: View, key: String, people: List<ScannedPerson>, badge: String?) {
        val binding = ItemDedupeCardBinding.bind(view)
        val members = binding.dedupeCardMembers
        binding.dedupeCardCheckbox.text = people.first().displayName()
        binding.dedupeCardCheckbox.setOnCheckedChangeListener(null)
        binding.dedupeCardCheckbox.isChecked = selection.isSelected(key)
        binding.dedupeCardCheckbox.setOnCheckedChangeListener { _, checked ->
            selection.setSelected(key, checked)
            onChanged()
        }
        binding.dedupeCardBadge.beVisibleIf(badge != null)
        binding.dedupeCardBadge.text = badge.orEmpty()
        members.removeAllViews()
        if (badge != null) {
            people.forEach { addMemberRow(members, key, it.records, it.key) }
        } else {
            people.flatMap { it.records }.forEach { addMemberRow(members, key, listOf(it), null) }
        }
        val records = selection.recordsOf(key, people)
        val merged = buildMergedContact(Contact(id = 0, contactId = 0), records, selection.choicesOf(key))
        binding.dedupeCardResult.text = if (records.isEmpty()) "" else activity.resultPreview(merged)
        addConflictRows(binding.dedupeCardConflicts, key, records)
        activity.updateTextColors(binding.root)
    }

    /** A person's accounts and details, or one record's for a split; a null [personKey] hides the checkbox. */
    private fun addMemberRow(container: LinearLayout, key: String, records: List<MergeRecord>, personKey: String?) {
        val row = ItemDedupeMemberBinding.inflate(activity.layoutInflater, container, false)
        row.dedupeMemberAccount.text = records.map { accountNames[it.contact.source] ?: it.contact.source }
            .distinct().joinToString(", ")
        row.dedupeMemberDetails.text = activity.memberDetails(records)
        row.dedupeMemberNote.beVisibleIf(records.any { !it.isWritable() })
        row.dedupeMemberCheckbox.beVisibleIf(personKey != null)
        if (personKey != null) {
            row.dedupeMemberCheckbox.isChecked = selection.isIncluded(key, personKey)
            row.dedupeMemberCheckbox.setOnClickListener {
                val checked = row.dedupeMemberCheckbox.isChecked
                selection.setIncluded(key, personKey, checked)
                if (!checked) askKeepApart(key, personKey)
                notifyDataSetChanged()
                onChanged()
            }
        }
        container.addView(row.root)
    }

    /** Offers to keep an unchecked person's device contacts apart from the other people in the group. */
    private fun askKeepApart(key: String, personKey: String) {
        val group = selection.result.duplicates.firstOrNull { it.key == key } ?: return
        val person = group.people.firstOrNull { it.key == personKey } ?: return
        val mine = person.deviceRawIds()
        val others = group.people.filter { it.key != personKey }.flatMap { it.deviceRawIds() }
        if (mine.isEmpty() || others.isEmpty()) return
        ConfirmationDialog(activity, messageId = R.string.dedupe_keep_apart) {
            ensureBackgroundThread { ContactLinkHelper(activity).keepApart(mine, others) }
        }
    }

    private fun addConflictRows(container: LinearLayout, key: String, records: List<MergeRecord>) {
        container.removeAllViews()
        val choices = selection.choicesOf(key)
        for ((field, holders) in findConflicts(records)) {
            val row = ItemDedupeConflictBinding.inflate(activity.layoutInflater, container, false)
            val keepBoth = field == ConflictField.NOTES && choices.keepBothNotes
            val chosen = if (keepBoth) null else holderOf(field, records, choices)
            row.dedupeConflict.text = activity.conflictText(field, chosen, keepBoth, accountNames)
            row.dedupeConflict.setOnClickListener { pickConflict(key, field, holders) }
            container.addView(row.root)
        }
    }

    private fun pickConflict(key: String, field: ConflictField, holders: List<MergeRecord>) {
        val items = ArrayList(holders.mapIndexed { index, record ->
            RadioItem(index, activity.conflictOption(field, record, accountNames))
        })
        if (field == ConflictField.NOTES) {
            items.add(RadioItem(holders.size, activity.getString(org.fossify.commons.R.string.keep_both)))
        }
        RadioGroupDialog(activity, items) { picked ->
            val index = picked as Int
            val choices = selection.choicesOf(key)
            selection.setChoices(
                key,
                when {
                    field == ConflictField.NOTES && index == holders.size -> choices.withKeepBothNotes(true)
                    field == ConflictField.NOTES -> choices.withKeepBothNotes(false).pick(field, holders[index].key)
                    else -> choices.pick(field, holders[index].key)
                }
            )
            notifyDataSetChanged()
            onChanged()
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_CARD = 1
    }
}
