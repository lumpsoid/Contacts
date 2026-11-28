package org.fossify.contacts.screens.main

import org.fossify.commons.helpers.TAB_CALL_HISTORY
import org.fossify.commons.helpers.TAB_CONTACTS
import org.fossify.commons.helpers.TAB_FAVORITES
import org.fossify.commons.helpers.TAB_GROUPS
import org.fossify.commons.helpers.TAB_LAST_USED
import org.fossify.contacts.screens.Next
import org.fossify.contacts.screens.main.MainAction as A
import org.fossify.contacts.screens.main.MainEffect as E
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUpdateTest {
    private val all = TAB_CONTACTS or TAB_FAVORITES or TAB_GROUPS
    private val noFavourites = TAB_CONTACTS or TAB_GROUPS

    private fun page(defaultTab: Int, showTabs: Int = all, lastUsed: Int = 0) =
        update(MainState(), A.PagerCreated(TabSettings(defaultTab, showTabs, lastUsed)))

    private fun content(path: String) = IntentUri("content://$path", "content")

    private fun import(kind: IntentKind, data: IntentUri? = null, stream: IntentUri? = null) =
        update(MainState(), A.IntentReceived(ImportFacts(kind, data, stream))).effects

    /** Feeds the actions through the update in order and returns the last step's result. */
    private fun run(vararg actions: A, from: MainState = MainState()) =
        actions.fold(Next<MainState, E>(from)) { next, action -> update(next.state, action) }

    @Test
    fun lastUsedReopensTheStoredPage() {
        val next = page(TAB_LAST_USED, lastUsed = 2)
        assertEquals(listOf(E.ShowPage(2)), next.effects)
        assertEquals(2, next.state.startPage)

        assertEquals(listOf(E.ShowPage(1)), page(TAB_LAST_USED, showTabs = noFavourites, lastUsed = 1).effects)
    }

    @Test
    fun lastUsedPastTheShownTabsOpensTheFirstPage() {
        assertEquals(listOf(E.ShowPage(0)), page(TAB_LAST_USED, showTabs = TAB_CONTACTS, lastUsed = 5).effects)
        assertEquals(listOf(E.ShowPage(0)), page(TAB_LAST_USED, showTabs = noFavourites, lastUsed = 2).effects)
        assertEquals(listOf(E.ShowPage(0)), page(TAB_LAST_USED, lastUsed = -1).effects)
    }

    @Test
    fun contactsOpensTheFirstPage() {
        assertEquals(listOf(E.ShowPage(0)), page(TAB_CONTACTS, lastUsed = 2).effects)
    }

    @Test
    fun favouritesOpensSecondWhenContactsIsShown() {
        assertEquals(listOf(E.ShowPage(1)), page(TAB_FAVORITES).effects)
    }

    @Test
    fun hiddenDefaultTabOpensTheFirstPage() {
        assertEquals(listOf(E.ShowPage(0)), page(TAB_FAVORITES, showTabs = noFavourites).effects)
        assertEquals(listOf(E.ShowPage(0)), page(TAB_CONTACTS, showTabs = TAB_FAVORITES or TAB_GROUPS).effects)
    }

    @Test
    fun favouritesOpensFirstWhenContactsIsHidden() {
        assertEquals(listOf(E.ShowPage(0)), page(TAB_FAVORITES, showTabs = TAB_FAVORITES or TAB_GROUPS).effects)
    }

    @Test
    fun groupsOpensAtItsIndexAmongShownTabs() {
        assertEquals(listOf(E.ShowPage(2)), page(TAB_GROUPS, showTabs = all).effects)
        assertEquals(listOf(E.ShowPage(1)), page(TAB_GROUPS, showTabs = TAB_CONTACTS or TAB_GROUPS).effects)
        assertEquals(listOf(E.ShowPage(1)), page(TAB_GROUPS, showTabs = TAB_FAVORITES or TAB_GROUPS).effects)
        assertEquals(listOf(E.ShowPage(0)), page(TAB_GROUPS, showTabs = TAB_GROUPS).effects)
    }

    @Test
    fun groupsHiddenOpensTheFirstPage() {
        assertEquals(listOf(E.ShowPage(0)), page(TAB_GROUPS, showTabs = TAB_CONTACTS or TAB_FAVORITES).effects)
    }

    @Test
    fun unknownDefaultTabActsAsGroups() {
        assertEquals(listOf(E.ShowPage(2)), page(TAB_CALL_HISTORY).effects)
    }

    @Test
    fun viewIntentImportsItsContentData() {
        assertEquals(
            listOf(E.ImportFile("content://x/1"), E.ConsumeIntent),
            import(IntentKind.VIEW, data = content("x/1")),
        )
    }

    @Test
    fun sendIntentImportsItsContentStream() {
        assertEquals(
            listOf(E.ImportFile("content://x/2"), E.ConsumeIntent),
            import(IntentKind.SEND, stream = content("x/2")),
        )
    }

    @Test
    fun fileUriIsRefusedAndConsumed() {
        val refused = listOf(E.Notify(MainNotice.INVALID_FILE_FORMAT), E.ConsumeIntent)
        assertEquals(refused, import(IntentKind.VIEW, data = IntentUri("file:///sdcard/a.vcf", "file")))
        assertEquals(refused, import(IntentKind.SEND, stream = IntentUri("a.vcf", null)))
    }

    @Test
    fun viewIgnoresTheStreamAndSendIgnoresTheData() {
        assertEquals(emptyList<E>(), import(IntentKind.VIEW, stream = content("x/3")))
        assertEquals(emptyList<E>(), import(IntentKind.SEND, data = content("x/4")))
    }

    @Test
    fun otherIntentDoesNothing() {
        assertEquals(emptyList<E>(), import(IntentKind.OTHER, data = content("x/5"), stream = content("x/6")))
    }

    @Test
    fun importSuccessRefreshesAllTabs() {
        assertEquals(listOf(E.RefreshAll), update(MainState(), A.ImportFinished(true)).effects)
        assertEquals(emptyList<E>(), update(MainState(), A.ImportFinished(false)).effects)
    }

    @Test
    fun searchStartsClosed() {
        assertFalse(MainState().searchOpen)
        assertEquals(emptyList<E>(), update(MainState(), A.PageSelected).effects)
        assertEquals(emptyList<E>(), update(MainState(), A.ListsRefreshed).effects)
    }

    @Test
    fun openingSearchWithNoTextRefiltersWithEmptyText() {
        assertEquals(listOf(E.FilterPage("")), run(A.SearchOpened, A.ListsRefreshed).effects)
    }

    @Test
    fun typingFiltersTheCurrentPage() {
        val next = run(A.SearchOpened, A.SearchTextChanged("ann"))
        assertEquals(listOf(E.FilterPage("ann")), next.effects)
        assertEquals("ann", next.state.searchText)
        assertTrue(next.state.searchOpen)
    }

    @Test
    fun textRestoredBeforeOpenIsUsedOnceOpen() {
        val next = run(A.SearchTextChanged("ann"), A.SearchOpened, A.PageSelected)
        assertEquals(listOf(E.FilterPage("ann")), next.effects)
    }

    @Test
    fun switchingPageRefiltersWithTheCurrentText() {
        val typed = arrayOf(A.SearchOpened, A.SearchTextChanged("ann"))
        assertEquals(listOf(E.FilterPage("ann")), run(*typed, A.PageSelected).effects)
        assertEquals(listOf(E.FilterPage("ann")), run(*typed, A.ListsRefreshed).effects)
    }

    @Test
    fun reopeningKeepsTheText() {
        val next = run(A.SearchOpened, A.SearchTextChanged("ann"), A.SearchOpened)
        assertEquals("ann", next.state.searchText)
        assertTrue(next.state.searchOpen)
    }

    @Test
    fun closingClearsAllPages() {
        val next = run(A.SearchOpened, A.SearchTextChanged("ann"), A.SearchClosed)
        assertEquals(listOf(E.ClearSearch), next.effects)
        assertFalse(next.state.searchOpen)
    }

    @Test
    fun textClearedAfterCloseStillFiltersButLeavesSearchClosed() {
        val next = run(A.SearchOpened, A.SearchClosed, A.SearchTextChanged(""))
        assertEquals(listOf(E.FilterPage("")), next.effects)
        assertEquals("", next.state.searchText)
        assertFalse(next.state.searchOpen)
        assertEquals(emptyList<E>(), update(next.state, A.PageSelected).effects)
    }
}
