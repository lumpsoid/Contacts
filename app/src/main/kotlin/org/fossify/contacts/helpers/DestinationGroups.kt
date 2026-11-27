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

/** One group row of the device, as read from the cursor. */
data class GroupRow(val id: Long, val title: String?, val accountName: String?, val accountType: String?)

/** The groups of [destination] in [rows] by title, keeping the first of each title. */
fun groupsByTitle(rows: List<GroupRow>, destination: ContactSource): Map<String, Group> {
    val groups = LinkedHashMap<String, Group>()
    rows.forEach { row ->
        val accountName = row.accountName ?: ""
        val accountType = row.accountType ?: ""
        if (accountName == destination.name && accountType == destination.type) {
            val title = row.title ?: ""
            groups.putIfAbsent(title, Group(row.id, title))
        }
    }
    return groups
}

/**
 * The groups to use for [titles]: the [existing] one of each title, else one made by [create].
 * Null when any of them could not be created.
 */
fun pickGroups(existing: Map<String, Group>, titles: List<String>, create: (String) -> Group?): ArrayList<Group>? {
    if (titles.isEmpty()) {
        return ArrayList()
    }
    val groups = titles.distinct().map { title ->
        existing[title] ?: create(title)
    }
    return if (groups.any { it == null }) null else ArrayList(groups.filterNotNull())
}

/** The groups of one account, by title, creating the missing ones. */
class DestinationGroups(private val context: Context) {
    /** Groups of [destination] titled [titles], created where missing; null when one could not be created. Blocking. */
    fun groupsFor(destination: ContactSource, titles: List<String>): ArrayList<Group>? {
        if (titles.isEmpty()) {
            return ArrayList()
        }
        val existing = groupsOf(destination)
        val helper = ContactsHelper(context)
        return pickGroups(existing, titles) { helper.createNewGroup(it, destination.name, destination.type) }
    }

    private fun groupsOf(destination: ContactSource): Map<String, Group> {
        if (destination.type == SMT_PRIVATE) {
            return context.groupsDB.getGroups().associateBy { it.title }
        }
        val projection = arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_NAME, Groups.ACCOUNT_TYPE)
        val selection = "${Groups.DELETED} = 0"
        val rows = try {
            context.contentResolver.query(Groups.CONTENT_URI, projection, selection, null, null)?.use {
                groupRowsOf(it)
            }
        } catch (e: SecurityException) {
            context.showErrorToast(e)
            null
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
            null
        }
        return groupsByTitle(rows.orEmpty(), destination)
    }

    /** Reads every group row found in [cursor]. */
    private fun groupRowsOf(cursor: Cursor): List<GroupRow> {
        val rows = ArrayList<GroupRow>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(ID_COLUMN)
            val title = cursor.getString(TITLE_COLUMN)
            val accountName = cursor.getString(ACCOUNT_NAME_COLUMN)
            val accountType = cursor.getString(ACCOUNT_TYPE_COLUMN)
            rows.add(GroupRow(id, title, accountName, accountType))
        }
        return rows
    }
}
