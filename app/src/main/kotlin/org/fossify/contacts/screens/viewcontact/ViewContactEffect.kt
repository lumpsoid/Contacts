package org.fossify.contacts.screens.viewcontact

import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.models.TransferMode

/** A toast the shell maps to a string resource. */
enum class ViewContactNotice { UNKNOWN_ERROR, NO_CONTACTS_PERMISSION, CONTACTS_UNLINKED }

/**
 * What the view-contact shell runs. [Ui] runs on the main thread, [Load] and [Write] on the shell's
 * background executor; loads answer with an Action.
 */
sealed interface ViewContactEffect {
    sealed interface Ui : ViewContactEffect
    data object RequestReadPermission : Ui
    data object ShowContact : Ui
    data class Notify(val notice: ViewContactNotice) : Ui
    data object Finish : Ui
    data class ConfirmDelete(val fromAllSources: Boolean) : Ui
    data class Share(val contact: Contact) : Ui
    data class Edit(val contact: Contact, val isMergedDuplicate: Boolean) : Ui
    data class EditExact(val contact: Contact) : Ui
    data class OpenWith(val contact: Contact) : Ui
    data class Transfer(val contact: Contact, val mode: TransferMode) : Ui

    sealed interface Load : ViewContactEffect
    data object ResolveLookupUri : Load
    data object ResolveContactUri : Load
    data class LoadContact(val id: Int, val isPrivate: Boolean) : Load
    data class LoadLinkedRawIds(val contactId: Int) : Load
    data class LoadMergedDeleteScope(val contact: Contact) : Load

    sealed interface Write : ViewContactEffect
    data class DeleteMerged(val contacts: List<Contact>, val linkedRawIds: List<Int>) : Write
    data class DeleteSingle(val contact: Contact) : Write
    data class DeleteRawContacts(val rawIds: List<Int>) : Write
    data class Unlink(val rawIds: List<Int>) : Write
    data class AddFavorite(val contact: Contact) : Write
    data class RemoveFavorite(val contact: Contact) : Write
    data class SaveLocalRingtone(val contactId: Int, val path: String) : Write
    data class SaveProviderRingtone(val contactId: Int, val path: String) : Write
}
