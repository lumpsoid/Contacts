package org.fossify.contacts.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getFileOutputStream
import org.fossify.commons.extensions.getPublicContactSourceSync
import org.fossify.commons.extensions.getTempFile
import org.fossify.commons.extensions.getVisibleContactSources
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.sharePathIntent
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toFileDirItem
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.tryInitiateCall
import org.fossify.commons.helpers.CONTACT_ID
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.IS_PRIVATE
import org.fossify.commons.helpers.ON_CLICK_CALL_CONTACT
import org.fossify.commons.helpers.ON_CLICK_EDIT_CONTACT
import org.fossify.commons.helpers.ON_CLICK_VIEW_CONTACT
import org.fossify.commons.helpers.PERMISSION_CALL_PHONE
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.BuildConfig
import org.fossify.contacts.R
import org.fossify.contacts.activities.EditContactActivity
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.activities.ViewContactActivity
import org.fossify.contacts.dialogs.ImportContactsDialog
import org.fossify.contacts.helpers.ContactLinkHelper
import org.fossify.contacts.helpers.DEFAULT_FILE_NAME
import org.fossify.contacts.helpers.IMPORT_FILE_PREFIX
import org.fossify.contacts.helpers.VcfExporter
import java.io.IOException

fun SimpleActivity.startCallIntent(recipient: String) {
    handlePermission(PERMISSION_CALL_PHONE) {
        val action = if (it) Intent.ACTION_CALL else Intent.ACTION_DIAL
        Intent(action).apply {
            data = Uri.fromParts("tel", recipient, null)
            launchActivityIntent(this)
        }
    }
}

fun SimpleActivity.showContactSourcePicker(currentSource: String, callback: (newSource: String) -> Unit) {
    ContactsHelper(this).getSaveableContactSources { sources ->
        val items = ArrayList<RadioItem>()
        var sourceNames = sources.map { it.name }
        var currentSourceIndex = sourceNames.indexOfFirst { it == currentSource }
        sourceNames = sources.map { it.publicName }

        sourceNames.forEachIndexed { index, account ->
            items.add(RadioItem(index, account))
            if (currentSource == SMT_PRIVATE && account == getString(R.string.phone_storage_hidden)) {
                currentSourceIndex = index
            }
        }

        runOnUiThread {
            RadioGroupDialog(this, items, currentSourceIndex) {
                callback(sources[it as Int].name)
            }
        }
    }
}

fun BaseSimpleActivity.shareContacts(contacts: ArrayList<Contact>) {
    val filename = if (contacts.size == 1) {
        "${contacts.first().getNameToDisplay()}.vcf"
    } else {
        DEFAULT_FILE_NAME
    }

    val file = getTempFile(filename)
    if (file == null) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        return
    }

    getFileOutputStream(file.toFileDirItem(this), true) { outputStream ->
        var result = VcfExporter.ExportResult.EXPORT_FAIL
        try {
            outputStream.use { stream ->
                VcfExporter().exportContacts(
                    context = this,
                    outputStream = stream,
                    contacts = contacts,
                    showExportingToast = false
                ) { result = it }
            }
        } catch (e: IOException) {
            showErrorToast(e)
            result = VcfExporter.ExportResult.EXPORT_FAIL
        }

        if (result == VcfExporter.ExportResult.EXPORT_OK) {
            sharePathIntent(file.absolutePath, BuildConfig.APPLICATION_ID)
        } else {
            // the stream is closed by the use block above, so a partial export can be removed
            runCatching { file.delete() }
            showErrorToast("$result")
        }
    }
}

fun SimpleActivity.handleGenericContactClick(contact: Contact) {
    when (config.onContactClick) {
        ON_CLICK_CALL_CONTACT -> callContact(contact)
        ON_CLICK_VIEW_CONTACT -> viewContact(contact)
        ON_CLICK_EDIT_CONTACT -> editContact(contact, config.mergeDuplicateContacts)
    }
}

fun SimpleActivity.callContact(contact: Contact) {
    hideKeyboard()
    if (contact.phoneNumbers.isNotEmpty()) {
        tryInitiateCall(contact) { startCallIntent(it) }
    } else {
        toast(org.fossify.commons.R.string.no_phone_number_found)
    }
}

fun Activity.viewContact(contact: Contact) {
    hideKeyboard()
    Intent(applicationContext, ViewContactActivity::class.java).apply {
        putExtra(CONTACT_ID, contact.id)
        putExtra(IS_PRIVATE, contact.isPrivate())
        startActivity(this)
    }
}

fun Activity.editContact(contact: Contact, isMergedDuplicate: Boolean) {
    if (!isMergedDuplicate) {
        editContact(contact)
    } else {
        ContactsHelper(this).getContactSources { contactSources ->
            getDuplicateContacts(contact, true) { contacts ->
                if (contacts.size == 1) {
                    runOnUiThread {
                        editContact(contacts.first())
                    }
                } else {
                    val items = ArrayList(contacts.mapIndexed { index, contact ->
                        var source = getPublicContactSourceSync(contact.source, contactSources)
                        if (source == "") {
                            source = getString(R.string.phone_storage)
                        }
                        RadioItem(index, source, contact)
                    }.sortedBy { it.title })

                    runOnUiThread {
                        RadioGroupDialog(
                            activity = this,
                            items = items,
                            titleId = R.string.select_account,
                        ) {
                            editContact(it as Contact)
                        }
                    }
                }
            }
        }
    }
}

fun Activity.getDuplicateContacts(contact: Contact, includeCurrent: Boolean, callback: (duplicateContacts: ArrayList<Contact>) -> Unit) {
    val duplicateContacts = ArrayList<Contact>()
    if (includeCurrent) {
        duplicateContacts.add(contact)
    }
    getDuplicatesKeptTogether(contact, false) { contacts ->
        ensureBackgroundThread {
            val displayContactSources = getVisibleContactSources()
            contacts.filter { displayContactSources.contains(it.source) }.forEach {
                val duplicate = ContactsHelper(this).getContactWithId(it.id, it.isPrivate())
                if (duplicate != null) {
                    duplicateContacts.add(duplicate)
                }
            }
            if (config.mergeDuplicateContacts && !contact.isPrivate() && contact.contactId != 0) {
                val knownIds = duplicateContacts.map { it.id }.toMutableSet()
                knownIds.add(contact.id)
                ContactLinkHelper(this).getLinkedRawContactIds(contact.contactId)
                    .filter { !knownIds.contains(it) }
                    .forEach { rawId ->
                        val linked = getContactWithIdOrRaw(rawId, false)
                        if (linked != null && displayContactSources.contains(linked.source)) {
                            duplicateContacts.add(linked)
                        }
                    }
            }
            callback(duplicateContacts)
        }
    }
}

fun Activity.editContact(contact: Contact) {
    hideKeyboard()
    Intent(applicationContext, EditContactActivity::class.java).apply {
        putExtra(CONTACT_ID, contact.id)
        putExtra(IS_PRIVATE, contact.isPrivate())
        startActivity(this)
    }
}

fun SimpleActivity.tryImportContactsFromFile(uri: Uri, callback: (Boolean) -> Unit) {
    when (uri.scheme) {
        "file" -> showImportContactsDialog(uri.path!!, onDismiss = {}, callback = callback)
        "content" -> {
            try {
                // Sweep copies left behind by a cancelled dialog, which never reaches the callback.
                getTempFile(DEFAULT_FILE_NAME)?.parentFile?.listFiles { file ->
                    file.name.startsWith(IMPORT_FILE_PREFIX)
                }?.forEach { it.delete() }

                val tempName = "$IMPORT_FILE_PREFIX${System.currentTimeMillis()}-$DEFAULT_FILE_NAME"
                val tempFile = copyUriToTempFile(uri, tempName)
                if (tempFile == null) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                    return
                }

                showImportContactsDialog(tempFile.absolutePath, onDismiss = { tempFile.delete() }) { success ->
                    callback(success)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

        else -> toast(org.fossify.commons.R.string.invalid_file_format)
    }
}

fun SimpleActivity.showImportContactsDialog(path: String, onDismiss: () -> Unit, callback: (Boolean) -> Unit) {
    ImportContactsDialog(this, path, onDismiss, callback)
}
