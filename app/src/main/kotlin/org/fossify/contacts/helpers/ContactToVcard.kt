package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Im
import ezvcard.VCard
import ezvcard.parameter.ImageType
import ezvcard.property.Address
import ezvcard.property.Anniversary
import ezvcard.property.Birthday
import ezvcard.property.Categories
import ezvcard.property.Email
import ezvcard.property.FormattedName
import ezvcard.property.Impp
import ezvcard.property.Organization
import ezvcard.property.Photo
import ezvcard.property.StructuredName
import ezvcard.property.Telephone
import ezvcard.property.Title
import ezvcard.util.PartialDate
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.IM
import java.time.LocalDate

private const val PNG_SIGNATURE = "\u0089PNG"
private const val GIF_SIGNATURE = "GIF8"
private const val SIGNATURE_LENGTH = 4
private val CUSTOM_LABEL_SCHEME = Regex("[A-Za-z][A-Za-z0-9+.-]*")
private val BUILT_IN_IM_SCHEMES = listOf("aim", "ymsgr", "msnim", "icq", "skype", HANGOUTS, QQ, JABBER)
    .map { it.lowercase() }
    .toSet()
private val PARTIAL_DATE = Regex("--(\\d{2})-(\\d{2})")
private val FULL_DATE = Regex("(\\d{4})-(\\d{2})-(\\d{2})")

/** Builds the vCard for this contact; [photo] is the photo file's bytes, if any. */
fun Contact.toVCard(photo: ByteArray?): VCard {
    val card = VCard()

    val formattedName = arrayOf(prefix, firstName, middleName, surname, suffix)
        .filter { it.isNotEmpty() }
        .joinToString(separator = " ")
    card.formattedName = FormattedName(formattedName)

    val structuredName = StructuredName()
    structuredName.prefixes.add(prefix)
    structuredName.given = firstName
    structuredName.additionalNames.add(middleName)
    structuredName.family = surname
    structuredName.suffixes.add(suffix)
    card.structuredName = structuredName

    if (nickname.isNotEmpty()) {
        card.setNickname(nickname)
    }

    card.addPhones(this)
    card.addEmails(this)
    card.addAddresses(this)
    card.addEvents(this)
    IMs.filter { it.value.isNotBlank() }.forEach { card.addImpp(toImpp(it)) }

    if (notes.isNotEmpty()) {
        card.addNote(notes)
    }

    if (organization.company.isNotEmpty()) {
        val org = Organization()
        org.values.add(organization.company)
        card.organization = org
    }
    if (organization.jobPosition.isNotEmpty()) {
        card.addTitle(Title(organization.jobPosition))
    }

    websites.forEach { card.addUrl(it) }

    if (photo != null) {
        card.addPhoto(Photo(photo, imageType(photo)))
    }

    if (groups.isNotEmpty()) {
        val groupList = Categories()
        groups.forEach { groupList.values.add(it.title) }
        card.categories = groupList
    }

    return card
}

private fun VCard.addPhones(contact: Contact) {
    contact.phoneNumbers.forEach {
        val telephone = Telephone(it.value)
        phoneTypeNames(it.type, it.label).forEach { name -> telephone.parameters.addType(name) }
        if (it.isPrimary) {
            telephone.pref = 1
        }
        addTelephoneNumber(telephone)
    }
}

private fun VCard.addEmails(contact: Contact) {
    contact.emails.forEach {
        val email = Email(it.value)
        val typeName = emailTypeName(it.type, it.label)
        if (typeName.isNotEmpty()) {
            email.parameters.addType(typeName)
        }
        addEmail(email)
    }
}

/**
 * An address with parts is written as its parts, with the formatted value as its LABEL. One with only a
 * formatted value is written as its street and no LABEL, so that it reads back as formatted-only.
 */
private fun VCard.addAddresses(contact: Contact) {
    contact.addresses.forEach {
        val hasParts = listOf(it.country, it.region, it.city, it.postcode, it.pobox, it.street, it.neighborhood)
            .any { part -> part.isNotEmpty() }
        if (hasParts || it.value.isNotEmpty()) {
            val address = Address()
            if (hasParts) {
                address.country = it.country
                address.region = it.region
                address.locality = it.city
                address.postalCode = it.postcode
                address.poBox = it.pobox
                address.streetAddress = it.street
                address.extendedAddress = it.neighborhood
                if (it.value.isNotEmpty()) {
                    address.label = it.value
                }
            } else {
                address.streetAddress = it.value
            }
            val typeName = addressTypeName(it.type, it.label)
            if (typeName.isNotEmpty()) {
                address.parameters.addType(typeName)
            }
            addAddress(address)
        }
    }
}

private fun VCard.addEvents(contact: Contact) {
    contact.events.filter { it.value.isNotBlank() }.forEach { event ->
        val value = event.value
        when (event.type) {
            Event.TYPE_BIRTHDAY -> birthdays.add(
                dated(value, { Birthday(it) }, { Birthday(it) }, { Birthday(it) })
            )

            Event.TYPE_ANNIVERSARY -> anniversaries.add(
                dated(value, { Anniversary(it) }, { Anniversary(it) }, { Anniversary(it) })
            )

            else -> addExtendedProperty(X_EVENT, value).addParameter(X_EVENT_TYPE, event.type.toString())
        }
    }
}

/** Builds the date of an event value: `--MM-dd`, then `yyyy-MM-dd`, else the text as it is. */
private fun <T> dated(
    value: String,
    ofDate: (LocalDate) -> T,
    ofPartial: (PartialDate) -> T,
    ofText: (String) -> T,
): T {
    val partial = PARTIAL_DATE.matchEntire(value)?.let { match ->
        val (month, day) = match.destructured
        runCatching { PartialDate.builder().month(month.toInt()).date(day.toInt()).build() }.getOrNull()
    }
    val date = FULL_DATE.matchEntire(value)?.let { match ->
        val (year, month, day) = match.destructured
        runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
    }
    return when {
        partial != null -> ofPartial(partial)
        date != null -> ofDate(date)
        else -> ofText(value)
    }
}

private fun toImpp(im: IM): Impp {
    val value = im.value
    return when (im.type) {
        Im.PROTOCOL_AIM -> Impp.aim(value)
        Im.PROTOCOL_YAHOO -> Impp.yahoo(value)
        Im.PROTOCOL_MSN -> Impp.msn(value)
        Im.PROTOCOL_ICQ -> Impp.icq(value)
        Im.PROTOCOL_SKYPE -> Impp.skype(value)
        Im.PROTOCOL_GOOGLE_TALK -> Impp(HANGOUTS, value)
        Im.PROTOCOL_QQ -> Impp(QQ, value)
        Im.PROTOCOL_JABBER -> Impp(JABBER, value)
        else -> customImpp(im.label, value)
    }
}

/**
 * An IM on a protocol this app has no name for. A label that is a valid URI scheme becomes the scheme,
 * unless it names a built-in protocol: that would re-import as the built-in protocol and lose the label.
 * Any other label is kept as a parameter on the x-im scheme, since Impp would reject it.
 */
private fun customImpp(label: String, value: String): Impp {
    if (CUSTOM_LABEL_SCHEME.matches(label) && label.lowercase() !in BUILT_IN_IM_SCHEMES) {
        return Impp(label, value)
    }
    val impp = Impp(CUSTOM_IM_SCHEME, value)
    if (label.isNotEmpty()) {
        impp.addParameter(X_IM_LABEL, label)
    }
    return impp
}

private fun imageType(bytes: ByteArray): ImageType {
    val head = String(bytes.copyOf(minOf(bytes.size, SIGNATURE_LENGTH)), Charsets.ISO_8859_1)
    return when {
        head.startsWith(PNG_SIGNATURE) -> ImageType.PNG
        head.startsWith(GIF_SIGNATURE) -> ImageType.GIF
        else -> ImageType.JPEG
    }
}
