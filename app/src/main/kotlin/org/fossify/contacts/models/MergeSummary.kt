package org.fossify.contacts.models

/** What a run will do: plans, records involved, and records deleted per account name. */
data class MergeSummary(val groups: Int, val records: Int, val deletedPerAccount: Map<String, Int>)
