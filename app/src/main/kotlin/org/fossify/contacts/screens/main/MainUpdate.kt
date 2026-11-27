package org.fossify.contacts.screens.main

import org.fossify.commons.helpers.TAB_CONTACTS
import org.fossify.commons.helpers.TAB_FAVORITES
import org.fossify.commons.helpers.TAB_GROUPS
import org.fossify.commons.helpers.TAB_LAST_USED
import org.fossify.contacts.screens.Next
import org.fossify.contacts.screens.main.MainAction as A
import org.fossify.contacts.screens.main.MainEffect as E

typealias MainNext = Next<MainState, E>

// Other apps may only hand us content uris; a file path would let them read any file we can reach.
private const val SCHEME_CONTENT = "content"

/** Every decision of the main screen. Pure: the shell feeds facts in and runs the effects out. */
fun update(state: MainState, action: A): MainNext = when (action) {
    is A.Tabs -> onTabs(state, action)
    is A.Import -> onImport(state, action)
}

private fun onTabs(state: MainState, action: A.Tabs): MainNext = when (action) {
    is A.PagerCreated -> {
        val page = startPage(action.tabs)
        Next(state.copy(startPage = page), listOf(E.ShowPage(page)))
    }
}

private fun onImport(state: MainState, action: A.Import): MainNext = when (action) {
    is A.IntentReceived -> Next(state, importEffects(action.facts))
    is A.ImportFinished -> Next(state, if (action.success) listOf(E.RefreshAll) else emptyList())
}

/**
 * The pager index to open on. TAB_LAST_USED reopens the stored page unchecked; a fixed tab opens at its
 * index among the shown tabs, and any value settings doesn't offer acts as groups.
 */
private fun startPage(tabs: TabSettings): Int {
    fun shown(tab: Int) = (tabs.showTabs and tab) > 0
    return when (tabs.defaultTab) {
        TAB_LAST_USED -> tabs.lastUsedPage
        TAB_CONTACTS -> 0
        // Index 1 whenever contacts is shown, even with favourites hidden, as upstream does.
        TAB_FAVORITES -> if (shown(TAB_CONTACTS)) 1 else 0
        else -> if (shown(TAB_GROUPS)) listOf(TAB_CONTACTS, TAB_FAVORITES).count { shown(it) } else 0
    }
}

/** VIEW imports its data, SEND its stream; only a content uri is read, anything else is refused. */
private fun importEffects(facts: ImportFacts): List<E> {
    val uri = when (facts.kind) {
        IntentKind.VIEW -> facts.data
        IntentKind.SEND -> facts.stream
        IntentKind.OTHER -> null
    } ?: return emptyList()
    val handle = if (uri.scheme == SCHEME_CONTENT) E.ImportFile(uri.value) else E.Notify(MainNotice.INVALID_FILE_FORMAT)
    return listOf(handle, E.ConsumeIntent)
}
