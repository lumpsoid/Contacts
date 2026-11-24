package org.fossify.contacts.models

data class ScanResult(val duplicates: List<DuplicateGroup>, val splitDetails: List<SplitDetail>) {
    fun isEmpty() = duplicates.isEmpty() && splitDetails.isEmpty()
}
