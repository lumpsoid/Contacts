package org.fossify.contacts.models

/** The result of merging one plan, by the plan's key. */
sealed class MergeOutcome {
    abstract val key: String

    data class Merged(override val key: String) : MergeOutcome()

    data class Failed(override val key: String, val reason: MergeFailure) : MergeOutcome()
}
