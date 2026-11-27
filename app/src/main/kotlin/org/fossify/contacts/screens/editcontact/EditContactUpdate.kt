package org.fossify.contacts.screens.editcontact

import org.fossify.commons.helpers.SAVE_DISCARD_PROMPT_INTERVAL
import org.fossify.contacts.screens.Next
import org.fossify.contacts.screens.editcontact.EditContactAction as A
import org.fossify.contacts.screens.editcontact.EditContactEffect as E

typealias EditContactNext = Next<EditContactState, E>

/** Every decision of the edit screen's loop. Pure: the shell feeds facts in and runs the effects out. */
fun update(state: EditContactState, action: A): EditContactNext = when (action) {
    is A.Leave -> onLeave(state, action)
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
