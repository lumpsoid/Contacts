package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.Contact

/** The list tabs of the contacts pager. */
enum class ListTab { CONTACTS, FAVORITES, GROUPS }

/**
 * The settings that shape a tab's list. [favouritesOrder] is the stored drag order as contact ids in string form,
 * null unless the custom order is selected and one is stored.
 */
data class TabFilter(
    val visibleSources: Collection<String>,
    val mergeDuplicates: Boolean,
    val favouritesOrder: List<String>?
)

/**
 * What [tab] lists out of [contacts]: only contacts from visible sources. Contacts and favourites drop linked
 * duplicates when merging is on; favourites keep only starred contacts, in the custom order when there is one.
 */
fun tabContents(tab: ListTab, contacts: List<Contact>, filter: TabFilter): List<Contact> = when (tab) {
    ListTab.GROUPS -> contacts.inSources(filter.visibleSources)
    ListTab.FAVORITES -> {
        val starred = contacts
            .filter { it.starred == 1 && filter.visibleSources.contains(it.source) }
            .mergedIf(filter.mergeDuplicates)
        filter.favouritesOrder?.let { starred.inFavouritesOrder(it) } ?: starred
    }
    ListTab.CONTACTS -> contacts
        .filter { filter.visibleSources.contains(it.source) }
        .mergedIf(filter.mergeDuplicates)
}

/** Favourites in the stored drag [order]; contacts the order doesn't hold come first, in their current order. */
fun List<Contact>.inFavouritesOrder(order: List<String>): List<Contact> {
    val position = order.withIndex().associate { it.value to it.index }
    return sortedBy { position[it.id.toString()] }
}

private fun List<Contact>.mergedIf(merge: Boolean): List<Contact> = if (merge) distinctByLinkedContact() else this
