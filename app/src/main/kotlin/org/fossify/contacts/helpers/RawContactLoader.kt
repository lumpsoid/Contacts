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
import org.fossify.contacts.models.DataRow

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
    fun getNamelessContacts(
        ignoredContactSources: Set<String> = context.baseConfig.ignoredContactSources
    ): List<Contact> {
        if (!context.hasPermission(PERMISSION_READ_CONTACTS)) {
            return emptyList()
        }
        return loadContacts(getNamelessRawIds(ignoredContactSources)).values.toList()
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

    /** Copies the stored name parts of raw contact [contact].id into [contact]; getContact leaves them blank. */
    fun fillName(contact: Contact) {
        val projection = arrayOf(
            CommonDataKinds.StructuredName.PREFIX,
            CommonDataKinds.StructuredName.GIVEN_NAME,
            CommonDataKinds.StructuredName.MIDDLE_NAME,
            CommonDataKinds.StructuredName.FAMILY_NAME,
            CommonDataKinds.StructuredName.SUFFIX
        )
        val selection = "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(contact.id.toString(), CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        context.queryCursor(Data.CONTENT_URI, projection, selection, selectionArgs, showErrors = true) { cursor ->
            contact.prefix = cursor.getStringValue(CommonDataKinds.StructuredName.PREFIX) ?: ""
            contact.firstName = cursor.getStringValue(CommonDataKinds.StructuredName.GIVEN_NAME) ?: ""
            contact.middleName = cursor.getStringValue(CommonDataKinds.StructuredName.MIDDLE_NAME) ?: ""
            contact.surname = cursor.getStringValue(CommonDataKinds.StructuredName.FAMILY_NAME) ?: ""
            contact.suffix = cursor.getStringValue(CommonDataKinds.StructuredName.SUFFIX) ?: ""
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

    private fun getNamelessRawIds(ignoredSources: Set<String>): List<Int> {
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
        if (rawIds.isEmpty()) {
            return emptyMap()
        }

        val storedGroups = ContactsHelper(context).getStoredGroupsSync()
        val rows = ArrayList<DataRow>()
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
                rows.add(dataRowOf(cursor))
            }
        }
        val contacts = contactsOf(rows) { it.normalizePhoneNumber() }
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
            contact.groups.addAll(groupsOfContact(memberships[contact.contactId].orEmpty(), storedGroups))
        }
    }
}

/** The copies of the [storedGroups] whose id is in [groupIds], in stored order; a group without an id never matches. */
fun groupsOfContact(groupIds: Set<Long>, storedGroups: List<Group>): List<Group> {
    return storedGroups
        .filter { group -> group.id?.let { groupIds.contains(it) } == true }
        .map { Group(it.id, it.title) }
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

/**
 * How nameless contacts join a list; mirrors commons ContactsHelper.getContacts for named ones.
 * [mergeLinked] also decides whether named contacts are merged by name (getContactsWithNameless).
 */
data class NamelessRules(val mergeLinked: Boolean, val onlyWithNumbers: Boolean)

fun namelessRules(
    getAll: Boolean,
    ignoredContactSources: Set<String>,
    mergeDuplicates: Boolean,
    showOnlyWithNumbers: Boolean
) = NamelessRules(
    mergeLinked = mergeDuplicates && ignoredContactSources.isEmpty() && !getAll,
    onlyWithNumbers = showOnlyWithNumbers && ignoredContactSources.isEmpty()
)

/** A contact with nothing to show as a name matches every other such contact by hash, so it has no duplicates. */
fun canHaveDuplicates(contact: Contact) = contact.getNameToDisplay().isNotBlank()

/**
 * The nameless contacts commons getDuplicatesOfContact would call duplicates of [contact], if it could load them.
 * Private contacts have their own id space, so only a device contact is excluded by id.
 * A contact with no display name has none (see [canHaveDuplicates]).
 */
fun namelessDuplicatesOf(contact: Contact, nameless: List<Contact>): List<Contact> {
    if (!canHaveDuplicates(contact)) {
        return emptyList()
    }
    val hash = contact.getHashToCompare()
    return nameless.filter {
        canHaveDuplicates(it) && (contact.isPrivate() || it.id != contact.id) && it.getHashToCompare() == hash
    }
}

/** Builds the contacts of [rows] by raw id, in first-row order; [normalize] fills a missing normalized number. */
fun contactsOf(rows: List<DataRow>, normalize: (String) -> String): Map<Int, Contact> {
    val contacts = LinkedHashMap<Int, Contact>()
    rows.forEach { row ->
        val contact = contacts.getOrPut(row.rawId) { createContact(row.rawId, row) }
        DATA_READERS[row.mimetype]?.invoke(contact, row, normalize)
    }
    return contacts
}

/**
 * Reads one data row. Data columns are read only for mimetypes a reader maps, since other rows may hold
 * blobs that a string or int read throws on; the other rows keep null / 0 for them.
 */
private fun dataRowOf(cursor: Cursor): DataRow {
    val mimetype = cursor.getStringValue(Data.MIMETYPE)
    val mapped = DATA_READERS.containsKey(mimetype)
    return DataRow(
        rawId = cursor.getIntValue(Data.RAW_CONTACT_ID),
        contactId = cursor.getIntValue(Data.CONTACT_ID),
        mimetype = mimetype,
        isPrimary = if (mapped) cursor.getIntValue(Data.IS_PRIMARY) else 0,
        data1 = if (mapped) cursor.getStringValue(Data.DATA1) else null,
        data2Int = if (mapped) cursor.getIntValue(Data.DATA2) else 0,
        data3 = if (mapped) cursor.getStringValue(Data.DATA3) else null,
        data4 = if (mapped) cursor.getStringValue(Data.DATA4) else null,
        data5 = if (mapped) cursor.getStringValue(Data.DATA5) else null,
        data5Int = if (mapped) cursor.getIntValue(Data.DATA5) else 0,
        data6 = if (mapped) cursor.getStringValue(Data.DATA6) else null,
        data7 = if (mapped) cursor.getStringValue(Data.DATA7) else null,
        data8 = if (mapped) cursor.getStringValue(Data.DATA8) else null,
        data9 = if (mapped) cursor.getStringValue(Data.DATA9) else null,
        data10 = if (mapped) cursor.getStringValue(Data.DATA10) else null,
        accountName = cursor.getStringValue(RawContacts.ACCOUNT_NAME),
        starred = cursor.getIntValue(CommonDataKinds.StructuredName.STARRED),
        photoUri = cursor.getStringValue(CommonDataKinds.StructuredName.PHOTO_URI),
        photoThumbnailUri = cursor.getStringValue(CommonDataKinds.StructuredName.PHOTO_THUMBNAIL_URI),
        customRingtone = cursor.getStringValue(CommonDataKinds.StructuredName.CUSTOM_RINGTONE)
    )
}

private typealias DataReader = (contact: Contact, row: DataRow, normalize: (String) -> String) -> Unit

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

private fun createContact(rawId: Int, row: DataRow) = Contact(
    id = rawId,
    contactId = row.contactId,
    source = row.accountName ?: "",
    starred = row.starred,
    photoUri = row.photoUri ?: "",
    thumbnailUri = row.photoThumbnailUri ?: "",
    ringtone = row.customRingtone,
    mimetype = CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
)

// Column per field: Phone/Email/Postal/Event TYPE = DATA2, LABEL = DATA3, Phone NORMALIZED_NUMBER = DATA4,
// Postal STREET..COUNTRY = DATA4..DATA10, Im PROTOCOL = DATA5, CUSTOM_PROTOCOL = DATA6, Organization TITLE = DATA4.
private val DATA_READERS = mapOf<String, DataReader>(
    CommonDataKinds.Phone.CONTENT_ITEM_TYPE to { contact, row, normalize ->
        val number = row.data1
        if (number != null) {
            val normalized = row.data4 ?: normalize(number)
            val type = row.data2Int
            val label = row.data3 ?: ""
            val isPrimary = row.isPrimary != 0
            contact.phoneNumbers.add(PhoneNumber(number, type, label, normalized, isPrimary))
        }
    },
    CommonDataKinds.Email.CONTENT_ITEM_TYPE to { contact, row, _ ->
        val email = row.data1
        if (email != null) {
            val type = row.data2Int
            val label = row.data3 ?: ""
            contact.emails.add(Email(email, type, label))
        }
    },
    CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE to { contact, row, _ ->
        val address = row.data1
        if (address != null) {
            contact.addresses.add(
                Address(
                    value = address,
                    type = row.data2Int,
                    label = row.data3 ?: "",
                    country = row.data10 ?: "",
                    region = row.data8 ?: "",
                    city = row.data7 ?: "",
                    postcode = row.data9 ?: "",
                    pobox = row.data5 ?: "",
                    street = row.data4 ?: "",
                    neighborhood = row.data6 ?: ""
                )
            )
        }
    },
    CommonDataKinds.Im.CONTENT_ITEM_TYPE to { contact, row, _ ->
        val im = row.data1
        if (im != null) {
            val type = row.data5Int
            val label = row.data6 ?: ""
            contact.IMs.add(IM(im, type, label))
        }
    },
    CommonDataKinds.Event.CONTENT_ITEM_TYPE to { contact, row, _ ->
        val startDate = row.data1
        if (startDate != null) {
            contact.events.add(Event(startDate, row.data2Int))
        }
    },
    CommonDataKinds.Note.CONTENT_ITEM_TYPE to { contact, row, _ ->
        val note = row.data1
        if (note != null) {
            contact.notes = note
        }
    },
    CommonDataKinds.Nickname.CONTENT_ITEM_TYPE to { contact, row, _ ->
        val nickname = row.data1
        if (nickname != null) {
            contact.nickname = nickname
        }
    },
    CommonDataKinds.Organization.CONTENT_ITEM_TYPE to { contact, row, _ ->
        val company = row.data1 ?: ""
        val title = row.data4 ?: ""
        if (company.isNotEmpty() || title.isNotEmpty()) {
            contact.organization = Organization(company, title)
        }
    },
    CommonDataKinds.Website.CONTENT_ITEM_TYPE to { contact, row, _ ->
        val url = row.data1
        if (url != null) {
            contact.websites.add(url)
        }
    }
)
