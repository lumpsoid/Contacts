package org.fossify.contacts.screens.main

/** The stored tab settings the start page is picked from. */
data class TabSettings(val defaultTab: Int, val showTabs: Int, val lastUsedPage: Int)

/** Which import-capable action the launch intent carries. */
enum class IntentKind { VIEW, SEND, OTHER }

/** A uri from the launch intent: its string form and the scheme the platform parsed. */
data class IntentUri(val value: String, val scheme: String?)

/** The launch intent as plain values. [stream] is EXTRA_STREAM, read by the shell only for ACTION_SEND. */
data class ImportFacts(val kind: IntentKind, val data: IntentUri?, val stream: IntentUri?)

/**
 * The main screen's state: the start page chosen when the pager was created, null until then.
 * [searchText] is the search box text while search is open, null while closed.
 */
data class MainState(val startPage: Int? = null, val searchText: String? = null)
