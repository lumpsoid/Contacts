package org.fossify.contacts.screens.editcontact

import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.helpers.Prefill

/** How the user asked to leave the edit screen; each way leaves on its own path. */
enum class LeaveVia { BACK, TOOLBAR }

/**
 * What a leave request needs to know: whether a save is running, the screen's [saved] contact, the contact
 * the form would save ([form], null when [saved] is), and the ringtone the contact was loaded with.
 */
data class BackFacts(val saving: Boolean, val saved: Contact?, val form: Contact?, val originalRingtone: String?) {
    /** Unsaved changes: the form differs from the contact, or the ringtone changed. No contact, nothing to save. */
    val changed: Boolean get() = saved != null && (saved != form || originalRingtone != saved.ringtone)
}

/** How the edit screen was launched: ACTION_EDIT, ACTION_INSERT, ADD_NEW_CONTACT_NUMBER, or anything else. */
enum class OpenKind { EDIT, INSERT, ADD_NUMBER, OTHER }

/** The launch intent's data uri as plain values: its path and its last path segment ("" when it has none). */
data class OpenUri(val path: String, val lastSegment: String)

/**
 * The launch intent as plain values. [contactId] is the CONTACT_ID extra (0 when absent), [data] is null when the
 * intent has no data uri or the uri has no path, [prefill] is null when the intent has no extras.
 */
data class OpenFacts(
    val kind: OpenKind,
    val fromSimpleContacts: Boolean = false,
    val contactId: Int = 0,
    val isPrivate: Boolean = false,
    val data: OpenUri? = null,
    val prefill: Prefill? = null,
)

/**
 * The edit screen's state: how it was launched, and when the save prompt was last shown, so a quick second back
 * leaves.
 */
data class EditContactState(val lastPromptAt: Long = 0L, val opened: OpenFacts? = null)
