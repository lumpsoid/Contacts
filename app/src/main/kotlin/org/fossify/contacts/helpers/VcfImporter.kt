package org.fossify.contacts.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import ezvcard.Ezvcard
import ezvcard.VCard
import org.fossify.commons.extensions.getCachePhoto
import org.fossify.commons.extensions.groupsDB
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.models.contacts.Group
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.extensions.getCachePhotoUri
import org.fossify.contacts.helpers.VcfImporter.ImportResult.IMPORT_FAIL
import org.fossify.contacts.helpers.VcfImporter.ImportResult.IMPORT_OK
import org.fossify.contacts.helpers.VcfImporter.ImportResult.IMPORT_PARTIAL
import java.io.File
import java.io.FileOutputStream

class VcfImporter(val activity: SimpleActivity) {
    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL
    }

    fun importContacts(path: String, targetContactSource: String): ImportResult {
        try {
            val inputStream = if (path.contains("/")) {
                File(path).inputStream()
            } else {
                activity.assets.open(path)
            }

            val ezContacts = Ezvcard.parse(inputStream).all()
            val counts = runEach(
                ezContacts,
                onFailure = { e -> Log.w("VcfImporter", "skipped a vCard", e) }
            ) { ezContact -> importCard(ezContact, targetContactSource) }
            return importResult(counts.done, counts.failed)
        } catch (e: Exception) {
            activity.showErrorToast(e, Toast.LENGTH_LONG)
            return IMPORT_FAIL
        }
    }

    private fun importCard(card: VCard, source: String): Boolean {
        val parsed = card.toParsedCard(source) { it.normalizePhoneNumber() }
        val contact = parsed.contact
        val bitmap = parsed.photo?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bitmap != null) {
            val uri = savePhoto(bitmap)
            contact.photo = bitmap
            contact.photoUri = uri
            contact.thumbnailUri = uri
        }
        contact.groups = getContactGroups(parsed.groupNames)
        return ContactsHelper(activity).insertContact(contact)
    }

    private fun getContactGroups(names: List<String>): ArrayList<Group> {
        if (names.isEmpty()) return arrayListOf()

        val storedGroups = ContactsHelper(activity).getStoredGroupsSync()
        return ArrayList(names.map { name ->
            storedGroups.firstOrNull { it.title == name } ?: run {
                val newGroup = Group(null, name)
                newGroup.id = activity.groupsDB.insertOrUpdate(newGroup)
                newGroup
            }
        })
    }

    private fun savePhoto(bitmap: Bitmap): String {
        val file = activity.getCachePhoto()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        return activity.getCachePhotoUri(file).toString()
    }
}

/** The result of an import that stored [imported] cards and failed on [failed] of them. */
fun importResult(imported: Int, failed: Int): VcfImporter.ImportResult = when {
    imported == 0 -> IMPORT_FAIL
    failed > 0 -> IMPORT_PARTIAL
    else -> IMPORT_OK
}
