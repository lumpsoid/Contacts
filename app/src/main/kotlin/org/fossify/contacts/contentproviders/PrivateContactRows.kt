package org.fossify.contacts.contentproviders

import com.google.gson.Gson
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.models.SimpleContact
import org.fossify.contacts.models.PrivateContactsQuery

fun privateContactsQueryOf(selectionArgs: Array<out String>?) = PrivateContactsQuery(
    favoritesOnly = selectionArgs?.getOrNull(0)?.equals("1") ?: false,
    withPhoneNumbersOnly = selectionArgs?.getOrNull(1)?.equals("1") ?: true,
)

val PRIVATE_CONTACT_COLUMNS = arrayOf(
    MyContactsContentProvider.COL_RAW_ID,
    MyContactsContentProvider.COL_CONTACT_ID,
    MyContactsContentProvider.COL_NAME,
    MyContactsContentProvider.COL_PHOTO_URI,
    MyContactsContentProvider.COL_PHONE_NUMBERS,
    MyContactsContentProvider.COL_BIRTHDAYS,
    MyContactsContentProvider.COL_ANNIVERSARIES,
)

/** One cursor row, values in [PRIVATE_CONTACT_COLUMNS] order. */
fun privateContactRow(contact: SimpleContact): List<Any?> = listOf(
    contact.rawId,
    contact.contactId,
    contact.name,
    contact.photoUri,
    Gson().toJson(contact.phoneNumbers),
    Gson().toJson(contact.birthdays),
    Gson().toJson(contact.anniversaries),
)
