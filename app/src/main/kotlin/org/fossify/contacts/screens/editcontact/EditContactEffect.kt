package org.fossify.contacts.screens.editcontact

import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.helpers.Prefill

/** A toast the shell maps to a string resource. */
enum class EditContactNotice { UNKNOWN_ERROR, NO_CONTACTS_PERMISSION }

/**
 * What the edit shell runs. [Ui] runs on the main thread, [Load] on the shell's background executor; loads answer
 * with an Action.
 */
sealed interface EditContactEffect {
    sealed interface Ui : EditContactEffect
    /** Ask whether to save before closing; the answer comes back as PromptAnswered with the same [via]. */
    data class AskToSave(val via: LeaveVia) : Ui
    data class Exit(val via: LeaveVia) : Ui
    data object Save : Ui

    /** Ask for read, then write contacts permission; the answer comes back as PermissionAnswered. */
    data object AskContactsPermission : Ui
    data class Notify(val notice: EditContactNotice) : Ui
    /** Hide the keyboard and finish. */
    data object Close : Ui
    /** Show [contact] (null: a new, empty one), then apply [prefill] to it when set. */
    data class ShowContact(val contact: Contact?, val prefill: Prefill?, val addingNumber: Boolean) : Ui

    sealed interface Load : EditContactEffect
    data object ResolveLookupUri : Load
    data object ResolveContactUri : Load
    data class LoadContact(val id: Int, val isPrivate: Boolean) : Load
}
