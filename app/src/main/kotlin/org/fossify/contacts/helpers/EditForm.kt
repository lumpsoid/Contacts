package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Address
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Email
import org.fossify.commons.models.contacts.Event
import org.fossify.commons.models.contacts.IM
import org.fossify.commons.models.contacts.Organization
import java.util.Locale

/** The edit form's values as read from its views on save or back: type ids, not label text. */
data class FormSnapshot(
    val prefix: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val surname: String = "",
    val suffix: String = "",
    val nickname: String = "",
    val notes: String = "",
    val company: String = "",
    val jobPosition: String = "",
    val photoPath: String = "",
    val starred: Boolean = false,
    val numbers: List<NumberRow> = emptyList(),
    val emails: List<TypedRow> = emptyList(),
    val addresses: List<AddressRow> = emptyList(),
    val ims: List<TypedRow> = emptyList(),
    val events: List<EventRow> = emptyList(),
    val websites: List<String> = emptyList(),
) {
    /** True when nothing was entered: the save is refused. */
    fun isBlank(unknownEvent: String): Boolean {
        val textEmpty = listOf(prefix, firstName, middleName, surname, suffix, nickname, notes, company, jobPosition)
            .all { it.isEmpty() }
        val rowsEmpty = numbers.none { it.isFilled } && emails.none { it.isFilled } &&
            addresses.none { it.isFilled } && ims.none { it.isFilled } &&
            events.none { it.isFilled(unknownEvent) } && websites.none { it.isNotEmpty() }
        return textEmpty && photoPath.isEmpty() && rowsEmpty
    }

    /** [base] with the form's values; empty rows are dropped. */
    fun toContact(
        base: Contact,
        unknownEvent: String,
        normalize: (String) -> String,
        sameNumber: (String, String) -> Boolean,
    ): Contact = base.copy(
        prefix = prefix, firstName = firstName, middleName = middleName, surname = surname,
        suffix = suffix, nickname = nickname,
        photoUri = photoPath,
        phoneNumbers = numbers.filter { it.isFilled }.mapTo(ArrayList()) { it.toPhoneNumber(normalize, sameNumber) },
        emails = emails.filter { it.isFilled }.mapTo(ArrayList()) { it.toEmail() },
        addresses = addresses.filter { it.isFilled }.mapTo(ArrayList()) { it.toAddress() },
        IMs = ims.filter { it.isFilled }.mapTo(ArrayList()) { it.toIM() },
        events = events.filter { it.isFilled(unknownEvent) }.mapTo(ArrayList()) { it.toEvent() },
        starred = if (starred) 1 else 0,
        notes = notes,
        websites = websites.filterTo(ArrayList()) { it.isNotEmpty() },
        organization = Organization(company, jobPosition),
    )
}

/** A phone row: [fetchedNormalized] is the normalised number the row was loaded with, "" for a new row. */
data class NumberRow(
    val number: String = "",
    val type: Int = CommonDataKinds.Phone.TYPE_MOBILE,
    val label: String = "",
    val fetchedNormalized: String = "",
    val isPrimary: Boolean = false,
) {
    val isFilled: Boolean get() = number.isNotEmpty()

    /** Keeps the fetched normalised number when it is the same number, so loading and saving is no change. */
    fun toPhoneNumber(normalize: (String) -> String, sameNumber: (String, String) -> Boolean): PhoneNumber {
        val normalized = normalize(number)
        val kept = if (sameNumber(normalized, fetchedNormalized)) fetchedNormalized else normalized
        val customLabel = if (type == CommonDataKinds.Phone.TYPE_CUSTOM) label else ""
        return PhoneNumber(number, type, customLabel, kept, isPrimary)
    }
}

/** An email or IM row: [value] is the address or handle, [label] is kept only for the custom type. */
data class TypedRow(val value: String = "", val type: Int = 0, val label: String = "") {
    val isFilled: Boolean get() = value.isNotEmpty()

    fun toEmail() = Email(value, type, if (type == CommonDataKinds.Email.TYPE_CUSTOM) label else "")

    fun toIM() = IM(value, type, if (type == CommonDataKinds.Im.PROTOCOL_CUSTOM) label else "")
}

/** An address row, typed as one line or as structured parts. */
sealed interface AddressRow {
    val type: Int
    val label: String
    val isFilled: Boolean get() = toAddress().value.isNotEmpty()

    fun toAddress(): Address
}

/** An address typed as one line of text. */
data class PlainAddress(
    val address: String = "",
    override val type: Int = CommonDataKinds.StructuredPostal.TYPE_HOME,
    override val label: String = "",
) : AddressRow {
    override fun toAddress() = Address(address, type, customLabel(type, label), "", "", "", "", "", "", "")
}

/** An address typed as structured parts; its value is the one-line text DAVdroid formats from them. */
data class StructuredAddress(
    val street: String = "",
    val neighborhood: String = "",
    val city: String = "",
    val postcode: String = "",
    val pobox: String = "",
    val region: String = "",
    val country: String = "",
    override val type: Int = CommonDataKinds.StructuredPostal.TYPE_HOME,
    override val label: String = "",
) : AddressRow {
    /** The one-line text, as DAVdroid formats it. */
    val formatted: String
        get() {
            val lineStreet = listOf(street, pobox, neighborhood).filter { it.isNotEmpty() }.joinToString(" ")
            val lineLocality = listOf(postcode, city).filter { it.isNotEmpty() }.joinToString(" ")
            val lines = mutableListOf<String>()
            if (lineStreet.isNotEmpty()) lines += lineStreet
            if (lineLocality.isNotEmpty()) lines += lineLocality
            if (region.isNotEmpty()) lines += region
            if (country.isNotEmpty()) lines += country.uppercase(Locale.getDefault())
            return lines.joinToString("\n")
        }

    override fun toAddress() = Address(
        formatted, type, customLabel(type, label), country, region, city, postcode, pobox, street, neighborhood
    )
}

private fun customLabel(type: Int, label: String) =
    if (type == CommonDataKinds.StructuredPostal.TYPE_CUSTOM) label else ""

/** An event row: [shown] is the field's text, [date] the stored value behind it. */
data class EventRow(
    val shown: String = "",
    val date: String = "",
    val type: Int = CommonDataKinds.Event.TYPE_BIRTHDAY,
) {
    fun isFilled(unknownEvent: String) = shown.isNotEmpty() && shown != unknownEvent

    fun toEvent() = Event(date, type)
}
