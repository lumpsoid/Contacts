package org.fossify.contacts.screens.editcontact

import org.fossify.commons.models.contacts.Contact

/** What reaches the edit screen's update, grouped by concern. */
sealed interface EditContactAction {
    /** Leaving the screen: the request, with the time it was made, and the save prompt's answer. */
    sealed interface Leave : EditContactAction
    data class LeaveRequested(val via: LeaveVia, val facts: BackFacts, val now: Long) : Leave
    data class PromptAnswered(val via: LeaveVia, val save: Boolean) : Leave

    /** Opening the screen: the launch intent, the contacts permission answer, and the load results. */
    sealed interface Open : EditContactAction
    data class Opened(val facts: OpenFacts) : Open
    data class PermissionAnswered(val granted: Boolean) : Open
    data class UriResolved(val rawId: Int) : Open
    data class ContactLoaded(val contact: Contact?) : Open
}
