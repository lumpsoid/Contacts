package org.fossify.contacts.screens.main

/** A toast the main shell maps to a string resource. */
enum class MainNotice { INVALID_FILE_FORMAT }

/** What the main shell runs. Every effect is [Ui] for now; the shell runs them inline on the main thread, in order. */
sealed interface MainEffect {
    sealed interface Ui : MainEffect
    data class ShowPage(val index: Int) : Ui
    data class ImportFile(val uri: String) : Ui
    data object ConsumeIntent : Ui
    data class Notify(val notice: MainNotice) : Ui
    data object RefreshAll : Ui
}
