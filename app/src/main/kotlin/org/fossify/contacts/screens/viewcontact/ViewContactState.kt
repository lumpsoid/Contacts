package org.fossify.contacts.screens.viewcontact

import org.fossify.commons.models.contacts.Contact

/** What the intent's data uri points at, read by the shell. */
enum class IntentData { NONE, LOOKUP, CONTACT }

/**
 * The launch intent as plain values. [contactId] is null when the CONTACT_ID extra can't be read;
 * [isViewIntent] is ACTION_VIEW or QuickContact.ACTION_QUICK_CONTACT.
 */
data class IntentFacts(
    val contactId: Int?,
    val isPrivate: Boolean,
    val isViewIntent: Boolean,
    val data: IntentData,
)

data class ViewContactState(
    val intent: IntentFacts? = null,
    val contact: Contact? = null,
    val linkedRawIds: List<Int> = emptyList(),
    val wasEditLaunched: Boolean = false,
) {
    val showOpenWith: Boolean get() = contact?.isPrivate() == false
    val showUnlink: Boolean get() = linkedRawIds.size > 1
}
