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

/** The pager's tabs, in page order; settings hide some. */
private val TAB_ORDER = listOf(TAB_CONTACTS, TAB_FAVORITES, TAB_GROUPS)

/** Every decision of the main screen. Pure: the shell feeds facts in and runs the effects out. */
fun update(state: MainState, action: A): MainNext = when (action) {
    is A.Tabs -> onTabs(state, action)
    is A.Import -> onImport(state, action)
    is A.Search -> onSearch(state, action)
}

private fun onTabs(state: MainState, action: A.Tabs): MainNext = when (action) {
    is A.PagerCreated -> {
        val page = startPage(action.tabs)
        Next(state.copy(startPage = page), listOf(E.ShowPage(page)))
    }
    A.PageSelected, A.ListsRefreshed -> Next(state, refilter(state))
}

private fun onImport(state: MainState, action: A.Import): MainNext = when (action) {
    is A.IntentReceived -> Next(state, importEffects(action.facts))
    is A.ImportFinished -> Next(state, if (action.success) listOf(E.RefreshAll) else emptyList())
}

private fun onSearch(state: MainState, action: A.Search): MainNext = when (action) {
    A.SearchOpened -> Next(state.copy(searchOpen = true))
    // Every change filters, even closed: commons clears the box just after closing, as upstream forwarded.
    is A.SearchTextChanged -> Next(state.copy(searchText = action.text), listOf(E.FilterPage(action.text)))
    A.SearchClosed -> Next(state.copy(searchOpen = false), listOf(E.ClearSearch))
}

/** An open search filters the page shown and every refreshed list again, with the box's text. */
private fun refilter(state: MainState): List<E> =
    if (state.searchOpen) listOf(E.FilterPage(state.searchText)) else emptyList()

/**
 * The pager index to open on. TAB_LAST_USED reopens the stored page, or the first when it is past the shown
 * tabs; a fixed tab opens at its index among the shown tabs, and any value settings doesn't offer acts as groups.
 */
private fun startPage(tabs: TabSettings): Int {
    fun shown(tab: Int) = (tabs.showTabs and tab) > 0
    val shownCount = TAB_ORDER.count { shown(it) }
    return when (tabs.defaultTab) {
        TAB_LAST_USED -> tabs.lastUsedPage.takeIf { it in 0 until shownCount } ?: 0
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
