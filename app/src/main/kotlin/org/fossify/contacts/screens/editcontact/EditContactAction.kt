package org.fossify.contacts.screens.editcontact

/** What reaches the edit screen's update, grouped by concern. */
sealed interface EditContactAction {
    /** Leaving the screen: the request, with the time it was made, and the save prompt's answer. */
    sealed interface Leave : EditContactAction
    data class LeaveRequested(val via: LeaveVia, val facts: BackFacts, val now: Long) : Leave
    data class PromptAnswered(val via: LeaveVia, val save: Boolean) : Leave
}
