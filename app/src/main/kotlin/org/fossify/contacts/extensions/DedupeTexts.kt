package org.fossify.contacts.extensions

import android.content.Context
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.R
import org.fossify.contacts.helpers.conflictValue
import org.fossify.contacts.models.ConflictField
import org.fossify.contacts.models.MergeFailure
import org.fossify.contacts.models.MergeOutcome
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.MergeRunResult

/** The phone numbers and emails of [records], each once, comma-separated. */
fun Context.memberDetails(records: List<MergeRecord>): String {
    val phones = records.flatMap { record -> record.contact.phoneNumbers.map { it.value } }
    val emails = records.flatMap { record -> record.contact.emails.map { it.value } }
    return (phones + emails).distinct().joinToString(", ")
}

fun Context.resultPreview(merged: Contact): String {
    val parts = listOf(merged.getNameToDisplay()) + merged.phoneNumbers.map { it.value } +
        merged.emails.map { it.value }
    return getString(R.string.dedupe_result_preview, parts.filter { it.isNotBlank() }.distinct().joinToString(", "))
}

fun Context.fieldLabel(field: ConflictField): String = getString(
    when (field) {
        ConflictField.NAME -> org.fossify.commons.R.string.name
        ConflictField.PHOTO -> R.string.dedupe_photo
        ConflictField.BIRTHDAY -> org.fossify.commons.R.string.birthday
        ConflictField.ORGANIZATION -> R.string.dedupe_organization
        ConflictField.NOTES -> org.fossify.commons.R.string.notes
    }
)

/** How a picker shows [record]'s value for [field]: the value and its account, or "From <account>" for photos. */
fun Context.conflictOption(field: ConflictField, record: MergeRecord, accountNames: Map<String, String>): String {
    val account = accountNames[record.contact.source] ?: record.contact.source
    return if (field == ConflictField.PHOTO) {
        getString(R.string.dedupe_photo_from, account)
    } else {
        "${conflictValue(record, field)} ($account)"
    }
}

/** "Label: chosen value", "Label: Keep both", or "Label: -" when nothing is chosen. */
fun Context.conflictText(
    field: ConflictField,
    chosen: MergeRecord?,
    keepBoth: Boolean,
    accountNames: Map<String, String>
): String {
    val value = when {
        keepBoth -> getString(org.fossify.commons.R.string.keep_both)
        chosen != null -> conflictOption(field, chosen, accountNames)
        else -> "-"
    }
    return "${fieldLabel(field)}: $value"
}

fun Context.failureText(reason: MergeFailure): String = getString(
    when (reason) {
        MergeFailure.SIM_LIMIT -> R.string.dedupe_fail_sim
        MergeFailure.WRITE_FAILED -> R.string.dedupe_fail_write
        MergeFailure.DELETE_FAILED -> R.string.dedupe_fail_delete
        MergeFailure.LINK_FAILED -> R.string.dedupe_fail_link
    }
)

/** The result lines: merged and failed counts, one line per failure, then the cancelled and backup lines. */
fun Context.runResultText(result: MergeRunResult.Finished, names: Map<String, String>): String {
    val merged = result.outcomes.count { it is MergeOutcome.Merged }
    val failures = result.outcomes.filterIsInstance<MergeOutcome.Failed>()
    val lines = arrayListOf(
        getString(R.string.dedupe_merged_count, merged),
        getString(R.string.dedupe_failed_count, failures.size)
    )
    failures.forEach { lines.add("${names[it.key] ?: it.key}: ${failureText(it.reason)}") }
    if (result.cancelled) lines.add(getString(R.string.dedupe_cancelled))
    if (result.backupUri != null) lines.add(getString(R.string.dedupe_backup_saved))
    return lines.joinToString("\n")
}
