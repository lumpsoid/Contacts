package org.fossify.contacts.helpers

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract.Groups
import org.fossify.commons.extensions.groupsDB
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Group

private const val ID_COLUMN = 0
private const val TITLE_COLUMN = 1
private const val ACCOUNT_NAME_COLUMN = 2
private const val ACCOUNT_TYPE_COLUMN = 3

/** The groups of one account, by title, creating the missing ones. */
class DestinationGroups(private val context: Context) {
    /** Groups of [destination] titled [titles], created where missing; null when one could not be created. Blocking. */
    fun groupsFor(destination: ContactSource, titles: List<String>): ArrayList<Group>? {
        if (titles.isEmpty()) {
            return ArrayList()
        }
        val existing = groupsOf(destination)
        val helper = ContactsHelper(context)
        val groups = titles.distinct().map { title ->
            existing[title] ?: helper.createNewGroup(title, destination.name, destination.type)
        }
        return if (groups.any { it == null }) null else ArrayList(groups.filterNotNull())
    }

    private fun groupsOf(destination: ContactSource): Map<String, Group> {
        if (destination.type == SMT_PRIVATE) {
            return context.groupsDB.getGroups().associateBy { it.title }
        }
        val groups = HashMap<String, Group>()
        val projection = arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_NAME, Groups.ACCOUNT_TYPE)
        val selection = "${Groups.DELETED} = 0"
        try {
            context.contentResolver.query(Groups.CONTENT_URI, projection, selection, null, null)?.use {
                addGroupsOf(it, destination, groups)
            }
        } catch (e: SecurityException) {
            context.showErrorToast(e)
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
        }
        return groups
    }

    /** Adds the groups of [destination] found in [cursor] to [groups], keeping the first of each title. */
    private fun addGroupsOf(cursor: Cursor, destination: ContactSource, groups: MutableMap<String, Group>) {
        while (cursor.moveToNext()) {
            val accountName = cursor.getString(ACCOUNT_NAME_COLUMN) ?: ""
            val accountType = cursor.getString(ACCOUNT_TYPE_COLUMN) ?: ""
            if (accountName == destination.name && accountType == destination.type) {
                val title = cursor.getString(TITLE_COLUMN) ?: ""
                groups.putIfAbsent(title, Group(cursor.getLong(ID_COLUMN), title))
            }
        }
    }
}
