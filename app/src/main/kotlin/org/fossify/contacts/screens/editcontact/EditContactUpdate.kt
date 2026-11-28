package org.fossify.contacts.screens.editcontact

import org.fossify.commons.helpers.SAVE_DISCARD_PROMPT_INTERVAL
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.screens.Next
import org.fossify.contacts.screens.editcontact.EditContactAction as A
import org.fossify.contacts.screens.editcontact.EditContactEffect as E

typealias EditContactNext = Next<EditContactState, E>

/** Every decision of the edit screen's loop. Pure: the shell feeds facts in and runs the effects out. */
fun update(state: EditContactState, action: A): EditContactNext = when (action) {
    is A.Leave -> onLeave(state, action)
    is A.Open -> onOpen(state, action)
}

/**
 * A running save swallows the request. Unsaved changes prompt, unless the prompt was shown within the interval:
 * then a second back leaves without asking, as upstream does.
 */
private fun onLeave(state: EditContactState, action: A.Leave): EditContactNext = when (action) {
    is A.LeaveRequested -> when {
        action.facts.saving -> Next(state)
        action.now - state.lastPromptAt > SAVE_DISCARD_PROMPT_INTERVAL && action.facts.changed ->
            Next(state.copy(lastPromptAt = action.now), listOf(E.AskToSave(action.via)))
        else -> Next(state, listOf(E.Exit(action.via)))
    }
    is A.PromptAnswered -> Next(state, listOf(if (action.save) E.Save else E.Exit(action.via)))
}

private const val NO_RAW_ID = -1
private const val LOOKUP = "lookup"
private const val LOCAL_PREFIX = "local_"

/**
 * Edit, insert and add-number launches from other apps need the contacts permission first; a refusal closes.
 * A missing contact closes with an error.
 */
private fun onOpen(state: EditContactState, action: A.Open): EditContactNext = when (action) {
    is A.Opened -> {
        val opened = state.copy(opened = action.facts)
        if (action.facts.kind != OpenKind.OTHER && !action.facts.fromSimpleContacts) {
            Next(opened, listOf(E.AskContactsPermission))
        } else {
            target(opened)
        }
    }
    is A.PermissionAnswered ->
        if (action.granted) target(state) else closing(state, EditContactNotice.NO_CONTACTS_PERMISSION)
    is A.UriResolved -> load(state, rawOrNone(action.rawId))
    is A.ContactLoaded ->
        if (action.contact == null) closing(state, EditContactNotice.UNKNOWN_ERROR) else show(state, action.contact)
}

private fun closing(state: EditContactState, notice: EditContactNotice): EditContactNext =
    Next(state, listOf(E.Notify(notice), E.Close))

/** -1 is "no raw contact": open a new one instead. */
private fun rawOrNone(rawId: Int?): Int = rawId?.takeIf { it != NO_RAW_ID } ?: 0

/**
 * Without a CONTACT_ID, an edit or add-number launch names its contact by the data uri: a lookup uri
 * (local_<raw id> is read here, any other is resolved by the shell) or a contact uri (resolved by the shell).
 */
private fun target(state: EditContactState): EditContactNext {
    val facts = state.opened ?: return Next<EditContactState, E>(state)
    val data = facts.data
    val byUri = facts.contactId == 0 && (facts.kind == OpenKind.EDIT || facts.kind == OpenKind.ADD_NUMBER)
    return when {
        !byUri || data == null -> load(state, facts.contactId)
        !data.path.contains(LOOKUP) -> Next(state, listOf(E.ResolveContactUri))
        data.lastSegment.startsWith(LOCAL_PREFIX) ->
            load(state, rawOrNone(data.path.substringAfter(LOCAL_PREFIX).toIntOrNull()))
        else -> Next(state, listOf(E.ResolveLookupUri))
    }
}

/** A known id loads its contact; none opens a new, empty one. */
private fun load(state: EditContactState, id: Int): EditContactNext =
    if (id != 0) Next(state, listOf(E.LoadContact(id, state.opened?.isPrivate ?: false))) else show(state, null)

/** The intent prefills a new contact on insert, and any contact on add-number; null prefill means no extras. */
private fun show(state: EditContactState, contact: Contact?): EditContactNext {
    val kind = state.opened?.kind
    val isNew = (contact?.id ?: 0) == 0
    val prefills = isNew && kind == OpenKind.INSERT || kind == OpenKind.ADD_NUMBER
    val prefill = state.opened?.prefill?.takeIf { prefills }
    return Next(state, listOf(E.ShowContact(contact, prefill, addingNumber = kind == OpenKind.ADD_NUMBER)))
}
