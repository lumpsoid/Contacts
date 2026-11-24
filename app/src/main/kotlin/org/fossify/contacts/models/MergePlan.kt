package org.fossify.contacts.models

/** One merge to run: the records that become one, and the user's choices. [key] is the group's key. */
data class MergePlan(val key: String, val kind: MergeKind, val records: List<MergeRecord>, val choices: MergeChoices)
