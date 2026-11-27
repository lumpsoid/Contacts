package org.fossify.contacts.screens.editcontact

/** What the edit shell runs. Every effect is [Ui] for now; the shell runs them inline on the main thread, in order. */
sealed interface EditContactEffect {
    sealed interface Ui : EditContactEffect
    /** Ask whether to save before closing; the answer comes back as PromptAnswered with the same [via]. */
    data class AskToSave(val via: LeaveVia) : Ui
    data class Exit(val via: LeaveVia) : Ui
    data object Save : Ui
}
