package org.fossify.contacts.screens.editcontact

import org.fossify.commons.models.contacts.Contact

/** How the user asked to leave the edit screen; each way leaves on its own path. */
enum class LeaveVia { BACK, TOOLBAR }

/**
 * What a leave request needs to know: whether a save is running, the screen's [saved] contact, the contact
 * the form would save ([form], null when [saved] is), and the ringtone the contact was loaded with.
 */
data class BackFacts(val saving: Boolean, val saved: Contact?, val form: Contact?, val originalRingtone: String?) {
    /**
     * Unsaved changes: the form differs from the contact, or the ringtone changed. With no contact yet,
     * only the ringtone counts.
     */
    val changed: Boolean get() = saved != null && saved != form || originalRingtone != saved?.ringtone
}

/** The edit screen's state: when the save prompt was last shown, so a quick second back leaves. */
data class EditContactState(val lastPromptAt: Long = 0L)
