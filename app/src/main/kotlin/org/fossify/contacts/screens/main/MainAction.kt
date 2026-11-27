package org.fossify.contacts.screens.main

/** What reaches the main screen's update: user and lifecycle events and results, grouped by concern. */
sealed interface MainAction {
    sealed interface Tabs : MainAction
    data class PagerCreated(val tabs: TabSettings) : Tabs

    sealed interface Import : MainAction
    data class IntentReceived(val facts: ImportFacts) : Import
    data class ImportFinished(val success: Boolean) : Import
}
