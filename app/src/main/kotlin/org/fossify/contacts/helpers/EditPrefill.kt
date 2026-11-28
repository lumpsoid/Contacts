package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import org.fossify.commons.helpers.DEFAULT_ADDRESS_TYPE
import org.fossify.commons.helpers.DEFAULT_EMAIL_TYPE
import org.fossify.commons.helpers.DEFAULT_EVENT_TYPE
import org.fossify.commons.helpers.DEFAULT_PHONE_NUMBER_TYPE
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Address
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Email
import org.fossify.commons.models.contacts.Event
import org.fossify.commons.models.contacts.Organization

/** One ContentValues of a third-party intent's "data" list, as plain values keyed by column name. */
typealias IntentRow = Map<String, Any?>

/**
 * What a third-party intent asks the edit screen to prefill, read from its extras: the KEY_PHONE extra,
 * KEY_EMAIL, KEY_NAME as text, and the "data" list's [rows].
 */
data class Prefill(
    val phoneExtra: String? = null,
    val email: String? = null,
    val name: String? = null,
    val rows: List<IntentRow> = emptyList(),
)

/** The prefilled contact, and whether the form highlights its last number and its last email. */
data class Prefilled(val contact: Contact, val highlightNumber: Boolean, val highlightEmail: Boolean)

/**
 * Adds what the intent carries to a copy of [contact]: the phones, the email, the name as first name, then each
 * data row by mimetype. A non-empty phone is highlighted only when [addingNumber] (ADD_NEW_CONTACT_NUMBER).
 */
fun Prefill.applyTo(contact: Contact, addingNumber: Boolean, normalize: (String) -> String): Prefilled {
    val filled = contact.copy(
        phoneNumbers = ArrayList(contact.phoneNumbers),
        emails = ArrayList(contact.emails),
        addresses = ArrayList(contact.addresses),
        events = ArrayList(contact.events),
        websites = ArrayList(contact.websites),
    )
    val phones = phones()
    phones.forEach { filled.phoneNumbers.add(PhoneNumber(it, DEFAULT_PHONE_NUMBER_TYPE, "", normalize(it))) }
    if (email != null) {
        filled.emails.add(Email(email, DEFAULT_EMAIL_TYPE, ""))
    }
    if (name != null) {
        filled.firstName = name
    }
    rows.forEach { filled.addRow(it) }
    return Prefilled(
        filled,
        highlightNumber = addingNumber && phones.any { it.isNotEmpty() },
        highlightEmail = email != null,
    )
}

/** The KEY_PHONE extra, then every phone row's number, each once. */
private fun Prefill.phones(): List<String> =
    (listOfNotNull(phoneExtra) + rows
        .filter { it[CommonDataKinds.StructuredName.MIMETYPE] == CommonDataKinds.Phone.CONTENT_ITEM_TYPE }
        .mapNotNull { stringOf(it, CommonDataKinds.Phone.DATA1) })
        .distinct()

/** Applies one data row as its mimetype's parse rule does; a row whose value is missing adds nothing. */
private fun Contact.addRow(row: IntentRow) {
    when (row[CommonDataKinds.StructuredName.MIMETYPE]) {
        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
            stringOf(row, CommonDataKinds.Email.DATA1)?.let {
                emails.add(Email(it, intOf(row, CommonDataKinds.Email.DATA2) ?: DEFAULT_EMAIL_TYPE, ""))
            }
        }
        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
            addressOf(row)?.let { addresses.add(it) }
        }
        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
            organization = Organization(
                stringOf(row, CommonDataKinds.Organization.DATA1) ?: "",
                stringOf(row, CommonDataKinds.Organization.DATA4) ?: "",
            )
        }
        CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
            stringOf(row, CommonDataKinds.Event.DATA1)?.let {
                events.add(Event(it, intOf(row, CommonDataKinds.Event.DATA2) ?: DEFAULT_EVENT_TYPE))
            }
        }
        CommonDataKinds.Website.CONTENT_ITEM_TYPE -> {
            stringOf(row, CommonDataKinds.Website.DATA1)?.let { websites.add(it) }
        }
        CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
            stringOf(row, CommonDataKinds.Note.DATA1)?.let { notes = it }
        }
        else -> Unit
    }
}

/** The address row, or null when it has neither a formatted nor a plain value. */
private fun addressOf(row: IntentRow): Address? {
    val value = stringOf(row, CommonDataKinds.StructuredPostal.DATA4)
        ?: stringOf(row, CommonDataKinds.StructuredPostal.DATA1)
        ?: return null
    return Address(
        value,
        intOf(row, CommonDataKinds.StructuredPostal.DATA2) ?: DEFAULT_ADDRESS_TYPE,
        "",
        stringOf(row, CommonDataKinds.StructuredPostal.COUNTRY).orEmpty(),
        stringOf(row, CommonDataKinds.StructuredPostal.REGION).orEmpty(),
        stringOf(row, CommonDataKinds.StructuredPostal.CITY).orEmpty(),
        stringOf(row, CommonDataKinds.StructuredPostal.POSTCODE).orEmpty(),
        stringOf(row, CommonDataKinds.StructuredPostal.POBOX).orEmpty(),
        stringOf(row, CommonDataKinds.StructuredPostal.STREET).orEmpty(),
        stringOf(row, CommonDataKinds.StructuredPostal.NEIGHBORHOOD).orEmpty(),
    )
}

private fun stringOf(row: IntentRow, column: String): String? = row[column]?.toString()

private fun intOf(row: IntentRow, column: String): Int? = when (val value = row[column]) {
    is Number -> value.toInt()
    is String -> value.toIntOrNull()
    else -> null
}
