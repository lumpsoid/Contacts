package org.fossify.contacts.screens.viewcontact

import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.models.TransferMode
import org.fossify.contacts.screens.Next
import org.fossify.contacts.screens.viewcontact.ViewContactAction as A
import org.fossify.contacts.screens.viewcontact.ViewContactEffect as E

typealias ViewContactNext = Next<ViewContactState, E>

private const val NO_RAW_ID = -1

/** No effects; the explicit type lets Kotlin infer Next's type arguments where nothing else gives them. */
private fun stay(state: ViewContactState): ViewContactNext = Next(state)

/** Every decision of the view-contact screen. Pure: the shell feeds facts in and runs the effects out. */
fun update(state: ViewContactState, action: A): ViewContactNext = when (action) {
    is A.Load -> onLoad(state, action)
    is A.Delete -> onDelete(state, action)
    is A.Link -> onLink(state, action)
    is A.Menu -> onMenu(state, action)
    is A.Detail -> onDetail(state, action)
}

private fun onLoad(state: ViewContactState, action: A.Load): ViewContactNext = when (action) {
    is A.Resumed -> {
        val resumed = state.copy(intent = action.intent)
        if (action.intent.isViewIntent) Next(resumed, listOf(E.RequestReadPermission)) else start(resumed)
    }

    is A.PermissionResult -> if (action.granted) {
        start(state)
    } else {
        Next(state, listOf(E.Notify(ViewContactNotice.NO_CONTACTS_PERMISSION), E.Finish))
    }

    A.VisibleFieldsChanged -> start(state)
    is A.LookupResolved -> {
        val looked = state.copy(contact = action.contact)
        if (action.contact != null) {
            loaded(looked, action.contact)
        } else {
            target(looked, action.rawId.takeIf { it != NO_RAW_ID } ?: 0)
        }
    }

    is A.UriResolved -> target(state, action.rawId.takeIf { it != NO_RAW_ID } ?: 0)
    is A.ContactLoaded -> if (action.contact != null) {
        loaded(state.copy(contact = action.contact), action.contact)
    } else {
        val notify: List<E> =
            if (state.wasEditLaunched) emptyList() else listOf(E.Notify(ViewContactNotice.UNKNOWN_ERROR))
        Next(state.copy(contact = null), notify + E.Finish)
    }
}

/** Picks what to load from the intent: an id, or the data uri of a view intent when there is no id. */
private fun start(state: ViewContactState): ViewContactNext {
    val intent = state.intent ?: return stay(state)
    val contactId = intent.contactId ?: return stay(state)
    if (contactId == 0 && intent.isViewIntent) {
        when (intent.data) {
            IntentData.LOOKUP -> return Next(state, listOf(E.ResolveLookupUri))
            IntentData.CONTACT -> return Next(state, listOf(E.ResolveContactUri))
            IntentData.NONE -> Unit
        }
    }
    return target(state, contactId)
}

/** Loads [id]; without one, shows the contact already held or leaves. */
private fun target(state: ViewContactState, id: Int): ViewContactNext {
    if (id != 0) {
        return Next(state, listOf(E.LoadContact(id, state.intent?.isPrivate ?: false)))
    }
    val held = state.contact ?: return Next(state, listOf(E.Finish))
    return loaded(state, held)
}

/** Shows [contact]; unlink is only offered for a non-private contact with a contact id. */
private fun loaded(state: ViewContactState, contact: Contact): ViewContactNext =
    if (contact.isPrivate() || contact.contactId == 0) {
        Next(state.copy(linkedRawIds = emptyList()), listOf(E.ShowContact))
    } else {
        Next(state, listOf(E.ShowContact, E.LoadLinkedRawIds(contact.contactId)))
    }

private fun onDelete(state: ViewContactState, action: A.Delete): ViewContactNext = when (action) {
    is A.DeleteClicked ->
        Next(state, listOf(E.ConfirmDelete(fromAllSources = action.shownSourceCount > 1 && action.mergeDuplicates)))

    is A.DeleteConfirmed -> {
        val contact = state.contact
        when {
            contact == null -> stay(state)
            action.mergeDuplicates -> Next(state, listOf(E.LoadMergedDeleteScope(contact)))
            else -> Next(state, listOf(E.DeleteSingle(contact)))
        }
    }

    is A.MergedDeleteScopeLoaded -> Next(state, listOf(E.DeleteMerged(action.contacts, action.linkedRawIds)))
    is A.MergedDeleted ->
        if (action.success) Next(state, listOf(E.DeleteRawContacts(action.linkedRawIds), E.Finish)) else stay(state)

    A.SingleDeleted -> Next(state, listOf(E.Finish))
}

private fun onLink(state: ViewContactState, action: A.Link): ViewContactNext = when (action) {
    is A.LinkedRawIdsLoaded -> stay(state.copy(linkedRawIds = action.ids))
    A.UnlinkClicked -> if (state.linkedRawIds.size < 2) {
        stay(state)
    } else {
        Next(state, listOf(E.Unlink(state.linkedRawIds)))
    }

    is A.Unlinked -> if (action.success) {
        Next(state, listOf(E.Notify(ViewContactNotice.CONTACTS_UNLINKED), E.Finish))
    } else {
        stay(state)
    }
}

private fun onMenu(state: ViewContactState, action: A.Menu): ViewContactNext {
    val contact = state.contact
    return when (action) {
        A.ShareClicked -> Next(state, listOfNotNull(contact?.let { E.Share(it) }))
        is A.EditClicked -> if (contact == null) {
            stay(state)
        } else {
            Next(state.copy(wasEditLaunched = true), listOf(E.Edit(contact, action.mergeDuplicates)))
        }

        is A.EditSourceClicked -> Next(state.copy(wasEditLaunched = true), listOf(E.EditExact(action.source)))
        A.OpenWithClicked -> Next(state, listOfNotNull(contact?.let { E.OpenWith(it) }))
        is A.TransferClicked -> Next(state, listOfNotNull(contact?.let { E.Transfer(it, action.mode) }))
        is A.Transferred ->
            if (action.mode == TransferMode.MOVE && action.done > 0) Next(state, listOf(E.Finish)) else stay(state)
    }
}

private fun onDetail(state: ViewContactState, action: A.Detail): ViewContactNext {
    val contact = state.contact ?: return stay(state)
    return when (action) {
        A.FavoriteToggled -> {
            val starred = contact.copy(starred = if (contact.starred == 1) 0 else 1)
            val write = if (starred.starred == 1) E.AddFavorite(starred) else E.RemoveFavorite(starred)
            Next(state.copy(contact = starred), listOf(write))
        }

        is A.RingtonePicked -> {
            val path = action.path ?: ""
            val write = if (contact.isPrivate()) {
                E.SaveLocalRingtone(contact.contactId, path)
            } else {
                E.SaveProviderRingtone(contact.contactId, path)
            }
            Next(state.copy(contact = contact.copy(ringtone = action.path)), listOf(write))
        }
    }
}
