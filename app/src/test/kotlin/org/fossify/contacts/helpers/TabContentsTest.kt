package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class TabContentsTest {
    private fun contact(id: Int, source: String = "acc", starred: Int = 0, contactId: Int = id) =
        Contact(id = id, contactId = contactId, source = source, starred = starred)

    private fun ids(list: List<Contact>) = list.map { it.id }

    private fun filter(
        visible: Set<String> = setOf("acc"),
        merge: Boolean = false,
        order: List<String>? = null
    ) = TabFilter(visible, merge, order)

    @Test
    fun contactsTabListsOnlyVisibleSources() {
        val contacts = listOf(contact(1), contact(2, source = "hidden"), contact(3))
        assertEquals(listOf(1, 3), ids(tabContents(ListTab.CONTACTS, contacts, filter())))
    }

    @Test
    fun groupsTabListsOnlyVisibleSourcesEvenWhenMerging() {
        val contacts = listOf(contact(1, contactId = 7), contact(2, contactId = 7))
        assertEquals(listOf(1, 2), ids(tabContents(ListTab.GROUPS, contacts, filter(merge = true))))
    }

    @Test
    fun contactsTabMergesLinkedDuplicatesOnlyWhenMergingIsOn() {
        val contacts = listOf(contact(1, contactId = 7), contact(2, contactId = 7))
        assertEquals(2, tabContents(ListTab.CONTACTS, contacts, filter(merge = false)).size)
        assertEquals(1, tabContents(ListTab.CONTACTS, contacts, filter(merge = true)).size)
    }

    @Test
    fun favouritesTabListsOnlyStarredFromVisibleSources() {
        val contacts = listOf(
            contact(1, starred = 1),
            contact(2, starred = 0),
            contact(3, source = "hidden", starred = 1),
            contact(4, starred = 1)
        )
        assertEquals(listOf(1, 4), ids(tabContents(ListTab.FAVORITES, contacts, filter())))
    }

    @Test
    fun favouritesKeepTheirLoadOrderWithoutCustomOrder() {
        val contacts = listOf(contact(3, starred = 1), contact(1, starred = 1), contact(2, starred = 1))
        assertEquals(listOf(3, 1, 2), ids(tabContents(ListTab.FAVORITES, contacts, filter(order = null))))
    }

    @Test
    fun favouritesFollowTheStoredOrder() {
        val contacts = listOf(contact(1, starred = 1), contact(2, starred = 1), contact(3, starred = 1))
        val ordered = filter(order = listOf("3", "1", "2"))
        assertEquals(listOf(3, 1, 2), ids(tabContents(ListTab.FAVORITES, contacts, ordered)))
    }

    @Test
    fun favouritesMissingFromTheStoredOrderComeFirst() {
        // Current behaviour, kept: a starred contact the stored order doesn't hold sorts first.
        val contacts = listOf(contact(1, starred = 1), contact(2, starred = 1), contact(3, starred = 1))
        val partial = filter(order = listOf("3", "1"))
        assertEquals(listOf(2, 3, 1), ids(tabContents(ListTab.FAVORITES, contacts, partial)))
    }

    @Test
    fun customOrderAppliesOnlyToFavourites() {
        val contacts = listOf(contact(1), contact(2))
        val reversed = filter(order = listOf("2", "1"))
        assertEquals(listOf(1, 2), ids(tabContents(ListTab.CONTACTS, contacts, reversed)))
    }
}
