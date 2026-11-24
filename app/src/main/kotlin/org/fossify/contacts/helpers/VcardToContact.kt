package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import ezvcard.VCard
import ezvcard.property.Address as VcfAddress
import ezvcard.property.DateOrTimeProperty
import ezvcard.property.Impp
import org.fossify.commons.helpers.DEFAULT_MIMETYPE
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Address
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Email
import org.fossify.commons.models.contacts.Event
import org.fossify.commons.models.contacts.IM
import org.fossify.commons.models.contacts.Organization
import java.time.LocalDate

private const val NO_YEAR = 1900

/** What one vCard maps to: the contact, plus the parts only an Android context can store. */
class ParsedCard(val contact: Contact, val groupNames: List<String>, val photo: ByteArray?)

/** Maps this vCard to a contact in [source]; [normalize] gives a phone number's normalized form. */
fun VCard.toParsedCard(source: String, normalize: (String) -> String): ParsedCard {
    val name = structuredName
    val contact = Contact(
        id = 0,
        prefix = name?.prefixes?.firstOrNull() ?: "",
        firstName = name?.given ?: "",
        middleName = name?.additionalNames?.firstOrNull() ?: "",
        surname = name?.family ?: "",
        suffix = name?.suffixes?.firstOrNull() ?: "",
        nickname = nicknames.flatMap { it.values }.filter { it.isNotBlank() }.joinToString(", "),
        photoUri = "",
        phoneNumbers = readPhones(normalize),
        emails = readEmails(),
        addresses = ArrayList(addresses.mapNotNull { readAddress(it) }),
        events = readEvents(),
        source = source,
        starred = 0,
        contactId = 0,
        thumbnailUri = "",
        photo = null,
        notes = notes.mapNotNull { it.value }.filter { it.isNotBlank() }.joinToString("\n"),
        groups = arrayListOf(),
        organization = Organization(
            organizations.flatMap { it.values }.filter { it.isNotBlank() }.joinToString(", "),
            titles.mapNotNull { it.value }.filter { it.isNotBlank() }.joinToString(", ")
        ),
        websites = ArrayList(urls.mapNotNull { it.value }),
        IMs = readIms(),
        mimetype = DEFAULT_MIMETYPE,
        ringtone = null
    )

    // if there is no N and ORG fields at the given contact, only FN, treat it as an organization
    val fullName = formattedName?.value
    if (contact.getNameToDisplay().isEmpty() && contact.organization.isEmpty() && !fullName.isNullOrEmpty()) {
        contact.organization.company = fullName
        contact.mimetype = CommonDataKinds.Organization.CONTENT_ITEM_TYPE
    }

    if (contact.isABusinessContact()) {
        contact.mimetype = CommonDataKinds.Organization.CONTENT_ITEM_TYPE
    }

    val groupNames = categoriesList.flatMap { it.values }.filter { it.isNotBlank() }.distinct()
    return ParsedCard(contact, groupNames, photos.firstOrNull()?.data)
}

private fun VCard.readPhones(normalize: (String) -> String): ArrayList<PhoneNumber> = ArrayList(
    telephoneNumbers.mapNotNull { phone ->
        val number = phone.text ?: return@mapNotNull null
        val values = phone.types.map { it.value }
        val types = meaningfulTypes(values)
        val type = phoneTypeId(types.firstOrNull() ?: MOBILE, types.getOrNull(1))
        PhoneNumber(
            value = number,
            type = type,
            label = if (type == Phone.TYPE_CUSTOM) types.firstOrNull().orEmpty() else "",
            normalizedNumber = normalize(number),
            isPrimary = isPreferred(values, runCatching { phone.pref }.getOrNull())
        )
    }
)

private fun VCard.readEmails(): ArrayList<Email> = ArrayList(
    emails.mapNotNull { mail ->
        val value = mail.value.orEmpty()
        if (value.isEmpty()) return@mapNotNull null
        val types = meaningfulTypes(mail.types.map { it.value })
        val type = emailTypeId(types.firstOrNull() ?: HOME)
        val label = if (type == CommonDataKinds.Email.TYPE_CUSTOM) types.firstOrNull().orEmpty() else ""
        Email(value, type, label)
    }
)

/** Null when the address has no text to show, so there is nothing to store. */
private fun readAddress(address: VcfAddress): Address? {
    val street = address.streetAddress.orEmpty()
    val city = address.locality.orEmpty()
    val region = address.region.orEmpty()
    val postcode = address.postalCode.orEmpty()
    val country = address.country.orEmpty()
    val pobox = address.poBox.orEmpty()
    val neighborhood = address.extendedAddress.orEmpty()
    val formatted = address.label.orEmpty().trim()

    var composed = street
    if (city.isNotEmpty()) {
        composed += " $city "
    }
    if (region.isNotEmpty()) {
        if (composed.isNotEmpty()) {
            composed = "${composed.trim()}, "
        }
        composed += "$region "
    }
    if (postcode.isNotEmpty()) {
        composed += "$postcode "
    }
    if (country.isNotEmpty()) {
        composed += "$country "
    }
    composed = composed.trim()

    val poBoxOrNeighborhood = listOf(pobox, neighborhood).filter { it.isNotEmpty() }.joinToString(", ")
    val value = formatted.ifEmpty { composed.ifEmpty { poBoxOrNeighborhood } }
    if (value.isEmpty()) return null

    // the exporter writes a formatted-only address as street = label = value
    val formattedOnly = formatted.isNotEmpty() && street == formatted &&
        listOf(country, region, city, postcode, pobox, neighborhood).all { it.isEmpty() }
    val typeNames = meaningfulTypes(address.types.map { it.value })
    val type = addressTypeId(typeNames.firstOrNull() ?: HOME)
    return Address(
        value = value,
        type = type,
        label = if (type == StructuredPostal.TYPE_CUSTOM) typeNames.firstOrNull().orEmpty() else "",
        country = country,
        region = region,
        city = city,
        postcode = postcode,
        pobox = pobox,
        street = if (formattedOnly) "" else street,
        neighborhood = neighborhood
    )
}

private fun VCard.readEvents(): ArrayList<Event> {
    val events = ArrayList<Event>()
    anniversaries.forEach { property ->
        eventValue(property)?.let { events.add(Event(it, CommonDataKinds.Event.TYPE_ANNIVERSARY)) }
    }
    birthdays.forEach { property ->
        eventValue(property)?.let { events.add(Event(it, CommonDataKinds.Event.TYPE_BIRTHDAY)) }
    }
    getExtendedProperties(X_EVENT).forEach { raw ->
        val value = raw.value.orEmpty()
        if (value.isNotBlank()) {
            val type = raw.getParameter(X_EVENT_TYPE)?.toIntOrNull() ?: CommonDataKinds.Event.TYPE_OTHER
            events.add(Event(value, type))
        }
    }
    return events
}

/** The day code of a date (`--MM-dd` when it has no year), else the partial date, else the text. */
private fun eventValue(property: DateOrTimeProperty): String? {
    val date = property.date
    val partial = property.partialDate
    return when {
        date != null -> runCatching { LocalDate.from(date) }.getOrNull()?.let { day ->
            if (day.year == NO_YEAR) {
                // for backward compatibility with old exports
                "--%02d-%02d".format(day.monthValue, day.dayOfMonth)
            } else {
                "%04d-%02d-%02d".format(day.year, day.monthValue, day.dayOfMonth)
            }
        } ?: date.toString()

        partial != null -> {
            val month = partial.month
            val day = partial.date
            if (month != null && day != null) {
                "--%02d-%02d".format(month, day)
            } else {
                partial.toISO8601(true)
            }
        }

        else -> property.text
    }
}

private fun VCard.readIms(): ArrayList<IM> {
    val ims = ArrayList(impps.mapNotNull { imFrom(it) })
    // an IMPP that is not a valid URI is kept by ezvcard as a raw property
    getExtendedProperties("IMPP").forEach { raw ->
        val handle = raw.value.orEmpty()
        if (handle.isNotBlank()) {
            ims.add(IM(handle, Im.PROTOCOL_CUSTOM, ""))
        }
    }
    return ims
}

private fun imFrom(impp: Impp): IM? {
    val handle = impp.handle.orEmpty()
    if (impp.uri == null || handle.isBlank()) return null
    val type = imType(impp)
    val protocol = impp.protocol.orEmpty()
    val label = when {
        type != Im.PROTOCOL_CUSTOM -> ""
        protocol.isEmpty() || protocol.equals(CUSTOM_IM_SCHEME, ignoreCase = true) ->
            impp.getParameter(X_IM_LABEL).orEmpty()
        else -> protocol
    }
    return IM(handle, type, label)
}

private fun imType(impp: Impp): Int {
    val protocol = impp.protocol.orEmpty()
    return when {
        impp.isAim -> Im.PROTOCOL_AIM
        impp.isYahoo -> Im.PROTOCOL_YAHOO
        impp.isMsn -> Im.PROTOCOL_MSN
        impp.isIcq -> Im.PROTOCOL_ICQ
        impp.isSkype -> Im.PROTOCOL_SKYPE
        protocol.equals(HANGOUTS, ignoreCase = true) -> Im.PROTOCOL_GOOGLE_TALK
        protocol.equals(QQ, ignoreCase = true) -> Im.PROTOCOL_QQ
        protocol.equals(JABBER, ignoreCase = true) -> Im.PROTOCOL_JABBER
        else -> Im.PROTOCOL_CUSTOM
    }
}
