package org.fossify.contacts.screens.viewcontact

import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.models.TransferMode

/** User events and load results of the view-contact screen. Config values arrive as payloads. */
sealed interface ViewContactAction {
    sealed interface Load : ViewContactAction
    data class Resumed(val intent: IntentFacts) : Load
    data class PermissionResult(val granted: Boolean) : Load
    data object VisibleFieldsChanged : Load
    data class LookupResolved(val contact: Contact?, val rawId: Int) : Load
    data class UriResolved(val rawId: Int) : Load
    data class ContactLoaded(val contact: Contact?) : Load

    sealed interface Delete : ViewContactAction
    data class DeleteClicked(val shownSourceCount: Int, val mergeDuplicates: Boolean) : Delete
    data class DeleteConfirmed(val mergeDuplicates: Boolean) : Delete
    data class MergedDeleteScopeLoaded(val contacts: List<Contact>, val linkedRawIds: List<Int>) : Delete
    data class MergedDeleted(val success: Boolean, val linkedRawIds: List<Int>) : Delete
    data object SingleDeleted : Delete

    sealed interface Link : ViewContactAction
    data class LinkedRawIdsLoaded(val ids: List<Int>) : Link
    data object UnlinkClicked : Link
    data class Unlinked(val success: Boolean) : Link

    sealed interface Menu : ViewContactAction
    data object ShareClicked : Menu
    data class EditClicked(val mergeDuplicates: Boolean) : Menu
    data class EditSourceClicked(val source: Contact) : Menu
    data object OpenWithClicked : Menu
    data class TransferClicked(val mode: TransferMode) : Menu
    data class Transferred(val mode: TransferMode, val done: Int) : Menu

    sealed interface Detail : ViewContactAction
    data object FavoriteToggled : Detail
    data class RingtonePicked(val path: String?) : Detail
}
