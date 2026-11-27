package org.fossify.contacts.contentproviders

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.contacts.extensions.config
import org.fossify.contacts.helpers.canReadPrivateContacts
import org.fossify.contacts.helpers.certDigestsOf
import org.fossify.contacts.helpers.certDigestsOfUid

class MyContactsContentProvider : ContentProvider() {
    override fun insert(uri: Uri, contentValues: ContentValues?) = null

    /** Only this app and apps signed with Fossify's or this app's certificate may read private contacts. */
    private fun canCallerRead(): Boolean {
        val context = context ?: return false
        val uid = Binder.getCallingUid()
        val packageManager = context.packageManager
        return canReadPrivateContacts(
            context.config.showPrivateContacts,
            uid,
            Process.myUid(),
            { packageManager.certDigestsOfUid(uid) },
            { packageManager.certDigestsOf(context.packageName) },
        )
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (context == null || !canCallerRead()) {
            return null
        } else {
            val matrixCursor = MatrixCursor(PRIVATE_CONTACT_COLUMNS)
            val filters = privateContactsQueryOf(selectionArgs)

            LocalContactsHelper(context!!)
                .getPrivateSimpleContactsSync(filters.favoritesOnly, filters.withPhoneNumbersOnly)
                .forEach {
                    matrixCursor.addRow(privateContactRow(it))
                }

            return matrixCursor
        }
    }

    override fun onCreate() = true

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri) = ""
}
