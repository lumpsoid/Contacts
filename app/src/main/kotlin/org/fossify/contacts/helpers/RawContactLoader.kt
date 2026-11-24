package org.fossify.contacts.helpers

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.queryCursor
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Address
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Email
import org.fossify.commons.models.contacts.Event
import org.fossify.commons.models.contacts.Group
import org.fossify.commons.models.contacts.IM
import org.fossify.commons.models.contacts.Organization

/**
 * Loads device raw contacts from all their data rows. Commons ContactsHelper only sees raw contacts
 * that have a structured name or organization row; this covers the rest, e.g. a raw contact that only
 * has a phone number.
 */
class RawContactLoader(private val context: Context) {
    companion object {
        private const val IDS_PER_QUERY = 500
    }

    /** Builds the raw contact [rawId] from its data rows, or returns null when it has none. */
    fun getContact(rawId: Int): Contact? {
        if (rawId == 0 || !context.hasPermission(PERMISSION_READ_CONTACTS)) {
            return null
        }
        return loadContacts(listOf(rawId))[rawId]
    }

    /** Raw contacts without a name or organization row, from contact sources that are not hidden. */
    fun getNamelessContacts(): List<Contact> {
        if (!context.hasPermission(PERMISSION_READ_CONTACTS)) {
            return emptyList()
        }
        return loadContacts(getNamelessRawIds()).values.toList()
    }

    /**
     * Commons updateContact only updates an existing structured name row, so a name typed into a raw
     * contact that has none would be lost. Adds an empty row for it to update when needed.
     */
    fun ensureNameRow(contact: Contact) {
        if (contact.isPrivate() || contact.id == 0) {
            return
        }

        contact.mimetype = CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
        val names = listOf(contact.prefix, contact.firstName, contact.middleName, contact.surname, contact.suffix)
        if (names.all { it.isEmpty() } || hasNameRow(contact.id)) {
            return
        }

        val values = ContentValues().apply {
            put(Data.RAW_CONTACT_ID, contact.id)
            put(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        }
        try {
            context.contentResolver.insert(Data.CONTENT_URI, values)
        } catch (e: SecurityException) {
            context.showErrorToast(e)
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
        }
    }

    private fun hasNameRow(rawId: Int): Boolean {
        var found = false
        val selection = "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(rawId.toString(), CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        context.queryCursor(Data.CONTENT_URI, arrayOf(Data._ID), selection, selectionArgs) {
            found = true
        }
        return found
    }

    private fun getNamelessRawIds(): List<Int> {
        val ignoredSources = context.baseConfig.ignoredContactSources
        val rawIds = LinkedHashSet<Int>()
        val rawProjection = arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE)
        val rawSelection = "${RawContacts.DELETED} = 0"
        context.queryCursor(RawContacts.CONTENT_URI, rawProjection, rawSelection, null, showErrors = true) { cursor ->
            val accountName = cursor.getStringValue(RawContacts.ACCOUNT_NAME) ?: ""
            val accountType = cursor.getStringValue(RawContacts.ACCOUNT_TYPE) ?: ""
            if (!ignoredSources.contains("$accountName:$accountType")) {
                rawIds.add(cursor.getIntValue(RawContacts._ID))
            }
        }

        val nameSelection = "${Data.MIMETYPE} IN (?, ?)"
        val nameArgs = arrayOf(
            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            CommonDataKinds.Organization.CONTENT_ITEM_TYPE
        )
        val nameProjection = arrayOf(Data.RAW_CONTACT_ID)
        context.queryCursor(Data.CONTENT_URI, nameProjection, nameSelection, nameArgs, showErrors = true) { cursor ->
            rawIds.remove(cursor.getIntValue(Data.RAW_CONTACT_ID))
        }
        return rawIds.toList()
    }

    private fun loadContacts(rawIds: List<Int>): Map<Int, Contact> {
        val contacts = LinkedHashMap<Int, Contact>()
        if (rawIds.isEmpty()) {
            return contacts
        }

        val storedGroups = ContactsHelper(context).getStoredGroupsSync()
        rawIds.chunked(IDS_PER_QUERY).forEach { chunk ->
            val selection = "${Data.RAW_CONTACT_ID} IN (${chunk.joinToString(",") { "?" }})"
            val selectionArgs = chunk.map { it.toString() }.toTypedArray()
            context.queryCursor(
                Data.CONTENT_URI,
                DATA_PROJECTION,
                selection,
                selectionArgs,
                showErrors = true
            ) { cursor ->
                val rawId = cursor.getIntValue(Data.RAW_CONTACT_ID)
                val contact = contacts.getOrPut(rawId) { createContact(rawId, cursor) }
                DATA_READERS[cursor.getStringValue(Data.MIMETYPE)]?.invoke(contact, cursor)
            }
        }
        fillGroups(contacts.values, storedGroups)
        return contacts
    }

    /**
     * Commons reads group membership per system contact and its updateContact rewrites it that way
     * (deleting by CONTACT_ID), so a raw contact has to carry its whole system contact's groups too.
     */
    private fun fillGroups(contacts: Collection<Contact>, storedGroups: List<Group>) {
        val contactIds = contacts.map { it.contactId }.filter { it != 0 }.distinct()
        if (contactIds.isEmpty() || storedGroups.isEmpty()) {
            return
        }

        val memberships = HashMap<Int, LinkedHashSet<Long>>()
        val projection = arrayOf(Data.CONTACT_ID, CommonDataKinds.GroupMembership.GROUP_ROW_ID)
        contactIds.chunked(IDS_PER_QUERY).forEach { chunk ->
            val selection = "${Data.MIMETYPE} = ? AND ${Data.CONTACT_ID} IN (${chunk.joinToString(",") { "?" }})"
            val selectionArgs = arrayOf(CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE) +
                chunk.map { it.toString() }
            context.queryCursor(Data.CONTENT_URI, projection, selection, selectionArgs, showErrors = true) { cursor ->
                memberships.getOrPut(cursor.getIntValue(Data.CONTACT_ID)) { LinkedHashSet() }
                    .add(cursor.getLongValue(CommonDataKinds.GroupMembership.GROUP_ROW_ID))
            }
        }

        contacts.forEach { contact ->
            val groupIds = memberships[contact.contactId].orEmpty()
            storedGroups
                .filter { group -> group.id?.let { groupIds.contains(it) } == true }
                .forEach { contact.groups.add(Group(it.id, it.title)) }
        }
    }
}

/**
 * Adds nameless raw contacts to a contact list loaded by commons. One that would show no name,
 * organization, email or phone number is left out. With [mergeLinked], one whose system contact is
 * already listed is left out too (its data shows on that contact's details), and nameless raw contacts
 * of one system contact are kept once. The result is not sorted.
 */
fun addNamelessContacts(
    listed: List<Contact>,
    nameless: List<Contact>,
    mergeLinked: Boolean,
    onlyWithNumbers: Boolean
): ArrayList<Contact> {
    val listedContactIds = listed.filter { !it.isPrivate() && it.contactId != 0 }.map { it.contactId }.toSet()
    val shown = nameless
        .filter { it.getNameToDisplay().isNotBlank() }
        .filter { !onlyWithNumbers || it.phoneNumbers.isNotEmpty() }
        .filter { !mergeLinked || it.contactId == 0 || !listedContactIds.contains(it.contactId) }
        .let { if (mergeLinked) it.distinctByLinkedContact() else it }
    return ArrayList(listed + shown)
}

private typealias DataReader = (contact: Contact, cursor: Cursor) -> Unit

private val DATA_PROJECTION = arrayOf(
    Data.RAW_CONTACT_ID,
    Data.CONTACT_ID,
    Data.MIMETYPE,
    Data.IS_PRIMARY,
    Data.DATA1,
    Data.DATA2,
    Data.DATA3,
    Data.DATA4,
    Data.DATA5,
    Data.DATA6,
    Data.DATA7,
    Data.DATA8,
    Data.DATA9,
    Data.DATA10,
    RawContacts.ACCOUNT_NAME,
    CommonDataKinds.StructuredName.STARRED,
    CommonDataKinds.StructuredName.PHOTO_URI,
    CommonDataKinds.StructuredName.PHOTO_THUMBNAIL_URI,
    CommonDataKinds.StructuredName.CUSTOM_RINGTONE
)

private fun createContact(rawId: Int, cursor: Cursor) = Contact(
    id = rawId,
    contactId = cursor.getIntValue(Data.CONTACT_ID),
    source = cursor.getStringValue(RawContacts.ACCOUNT_NAME) ?: "",
    starred = cursor.getIntValue(CommonDataKinds.StructuredName.STARRED),
    photoUri = cursor.getStringValue(CommonDataKinds.StructuredName.PHOTO_URI) ?: "",
    thumbnailUri = cursor.getStringValue(CommonDataKinds.StructuredName.PHOTO_THUMBNAIL_URI) ?: "",
    ringtone = cursor.getStringValue(CommonDataKinds.StructuredName.CUSTOM_RINGTONE),
    mimetype = CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
)

private val DATA_READERS = mapOf<String, DataReader>(
    CommonDataKinds.Phone.CONTENT_ITEM_TYPE to { contact, cursor ->
        val number = cursor.getStringValue(CommonDataKinds.Phone.NUMBER)
        if (number != null) {
            val normalized = cursor.getStringValue(CommonDataKinds.Phone.NORMALIZED_NUMBER)
                ?: number.normalizePhoneNumber()
            val type = cursor.getIntValue(CommonDataKinds.Phone.TYPE)
            val label = cursor.getStringValue(CommonDataKinds.Phone.LABEL) ?: ""
            val isPrimary = cursor.getIntValue(CommonDataKinds.Phone.IS_PRIMARY) != 0
            contact.phoneNumbers.add(PhoneNumber(number, type, label, normalized, isPrimary))
        }
    },
    CommonDataKinds.Email.CONTENT_ITEM_TYPE to { contact, cursor ->
        val email = cursor.getStringValue(CommonDataKinds.Email.DATA)
        if (email != null) {
            val type = cursor.getIntValue(CommonDataKinds.Email.TYPE)
            val label = cursor.getStringValue(CommonDataKinds.Email.LABEL) ?: ""
            contact.emails.add(Email(email, type, label))
        }
    },
    CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE to { contact, cursor ->
        val address = cursor.getStringValue(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
        if (address != null) {
            contact.addresses.add(
                Address(
                    value = address,
                    type = cursor.getIntValue(CommonDataKinds.StructuredPostal.TYPE),
                    label = cursor.getStringValue(CommonDataKinds.StructuredPostal.LABEL) ?: "",
                    country = cursor.getStringValue(CommonDataKinds.StructuredPostal.COUNTRY) ?: "",
                    region = cursor.getStringValue(CommonDataKinds.StructuredPostal.REGION) ?: "",
                    city = cursor.getStringValue(CommonDataKinds.StructuredPostal.CITY) ?: "",
                    postcode = cursor.getStringValue(CommonDataKinds.StructuredPostal.POSTCODE) ?: "",
                    pobox = cursor.getStringValue(CommonDataKinds.StructuredPostal.POBOX) ?: "",
                    street = cursor.getStringValue(CommonDataKinds.StructuredPostal.STREET) ?: "",
                    neighborhood = cursor.getStringValue(CommonDataKinds.StructuredPostal.NEIGHBORHOOD) ?: ""
                )
            )
        }
    },
    CommonDataKinds.Im.CONTENT_ITEM_TYPE to { contact, cursor ->
        val im = cursor.getStringValue(CommonDataKinds.Im.DATA)
        if (im != null) {
            val type = cursor.getIntValue(CommonDataKinds.Im.PROTOCOL)
            val label = cursor.getStringValue(CommonDataKinds.Im.CUSTOM_PROTOCOL) ?: ""
            contact.IMs.add(IM(im, type, label))
        }
    },
    CommonDataKinds.Event.CONTENT_ITEM_TYPE to { contact, cursor ->
        val startDate = cursor.getStringValue(CommonDataKinds.Event.START_DATE)
        if (startDate != null) {
            contact.events.add(Event(startDate, cursor.getIntValue(CommonDataKinds.Event.TYPE)))
        }
    },
    CommonDataKinds.Note.CONTENT_ITEM_TYPE to { contact, cursor ->
        val note = cursor.getStringValue(CommonDataKinds.Note.NOTE)
        if (note != null) {
            contact.notes = note
        }
    },
    CommonDataKinds.Nickname.CONTENT_ITEM_TYPE to { contact, cursor ->
        val nickname = cursor.getStringValue(CommonDataKinds.Nickname.NAME)
        if (nickname != null) {
            contact.nickname = nickname
        }
    },
    CommonDataKinds.Organization.CONTENT_ITEM_TYPE to { contact, cursor ->
        val company = cursor.getStringValue(CommonDataKinds.Organization.COMPANY) ?: ""
        val title = cursor.getStringValue(CommonDataKinds.Organization.TITLE) ?: ""
        if (company.isNotEmpty() || title.isNotEmpty()) {
            contact.organization = Organization(company, title)
        }
    },
    CommonDataKinds.Website.CONTENT_ITEM_TYPE to { contact, cursor ->
        val url = cursor.getStringValue(CommonDataKinds.Website.URL)
        if (url != null) {
            contact.websites.add(url)
        }
    }
)
