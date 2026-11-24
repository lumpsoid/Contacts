package org.fossify.contacts.models

/** One row of the duplicates review list. */
sealed class DedupeListItem {
    data class Header(val titleId: Int) : DedupeListItem()
    data class Duplicates(val group: DuplicateGroup) : DedupeListItem()
    data class Split(val detail: SplitDetail) : DedupeListItem()
}
