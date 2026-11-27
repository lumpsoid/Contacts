package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactSelectionTest {
    private val visible = setOf("acc", "smt_private")

    private fun device(id: Int, name: String = "Ann") =
        Contact(id = id, contactId = id, firstName = name, source = "acc")

    private fun privateContact(id: Int, name: String = "Ann") =
        Contact(id = id, contactId = id, firstName = name, source = "smt_private")

    private fun nameless(id: Int) = Contact(id = id, contactId = id, source = "acc")

    private fun keys(list: List<Contact>) = list.map { it.isPrivate() to it.id }

    @Test
    fun contactsTabOneSelected() {
        assertEquals(
            setOf(
                CabAction.EDIT, CabAction.ADD_TO_FAVORITES, CabAction.ADD_TO_GROUP, CabAction.SEND_SMS,
                CabAction.SEND_EMAIL, CabAction.DELETE, CabAction.CREATE_SHORTCUT, CabAction.MOVE_TO_ACCOUNT,
                CabAction.COPY_TO_ACCOUNT
            ),
            cabActions(LOCATION_CONTACTS_TAB, 1, true)
        )
        assertEquals(
            setOf(
                CabAction.EDIT, CabAction.ADD_TO_FAVORITES, CabAction.ADD_TO_GROUP, CabAction.SEND_SMS,
                CabAction.SEND_EMAIL, CabAction.DELETE, CabAction.MOVE_TO_ACCOUNT, CabAction.COPY_TO_ACCOUNT
            ),
            cabActions(LOCATION_CONTACTS_TAB, 1, false)
        )
    }

    @Test
    fun contactsTabManySelected() {
        assertEquals(
            setOf(
                CabAction.ADD_TO_FAVORITES, CabAction.ADD_TO_GROUP, CabAction.SEND_SMS, CabAction.SEND_EMAIL,
                CabAction.DELETE, CabAction.LINK, CabAction.MOVE_TO_ACCOUNT, CabAction.COPY_TO_ACCOUNT
            ),
            cabActions(LOCATION_CONTACTS_TAB, 2, true)
        )
    }

    @Test
    fun favoritesTabOneAndMany() {
        assertEquals(
            setOf(
                CabAction.EDIT, CabAction.REMOVE, CabAction.ADD_TO_GROUP, CabAction.SEND_SMS, CabAction.SEND_EMAIL,
                CabAction.CREATE_SHORTCUT, CabAction.MOVE_TO_ACCOUNT, CabAction.COPY_TO_ACCOUNT
            ),
            cabActions(LOCATION_FAVORITES_TAB, 1, true)
        )
        assertEquals(
            setOf(
                CabAction.REMOVE, CabAction.ADD_TO_GROUP, CabAction.SEND_SMS, CabAction.SEND_EMAIL,
                CabAction.LINK, CabAction.MOVE_TO_ACCOUNT, CabAction.COPY_TO_ACCOUNT
            ),
            cabActions(LOCATION_FAVORITES_TAB, 3, true)
        )
    }

    @Test
    fun groupContactsNeverLinksTransfersOrShortcuts() {
        assertEquals(
            setOf(
                CabAction.EDIT, CabAction.REMOVE, CabAction.ADD_TO_FAVORITES, CabAction.SEND_SMS,
                CabAction.SEND_EMAIL, CabAction.DELETE
            ),
            cabActions(LOCATION_GROUP_CONTACTS, 1, true)
        )
        assertEquals(
            setOf(
                CabAction.REMOVE, CabAction.ADD_TO_FAVORITES, CabAction.SEND_SMS, CabAction.SEND_EMAIL,
                CabAction.DELETE
            ),
            cabActions(LOCATION_GROUP_CONTACTS, 2, true)
        )
    }

    @Test
    fun insertOrEditOffersOnlyEdit() {
        assertEquals(setOf(CabAction.EDIT), cabActions(LOCATION_INSERT_OR_EDIT, 1, true))
        assertEquals(emptySet<CabAction>(), cabActions(LOCATION_INSERT_OR_EDIT, 2, true))
    }

    @Test
    fun linkModeNeedsTwo() {
        assertEquals(LinkMode.NONE, linkMode(emptyList()))
        assertEquals(LinkMode.NONE, linkMode(listOf(device(1))))
        // Positive control: two device contacts link.
        assertEquals(LinkMode.RAW, linkMode(listOf(device(1), device(2))))
    }

    @Test
    fun linkModeAllPrivateMerges() {
        assertEquals(LinkMode.PRIVATE, linkMode(listOf(privateContact(1), privateContact(2))))
    }

    @Test
    fun linkModeMixedIsRefused() {
        assertEquals(LinkMode.REFUSE, linkMode(listOf(privateContact(1), device(2))))
        assertEquals(LinkMode.REFUSE, linkMode(listOf(device(1), device(2), privateContact(3))))
    }

    @Test
    fun deleteWithoutMergeTakesOnlySelected() {
        assertEquals(
            listOf(false to 1),
            keys(deleteTargets(listOf(device(1)), listOf(device(1), device(2)), false, emptySet(), visible))
        )
        // Positive control: with merging on, the same-name duplicate is taken too.
        assertEquals(
            listOf(false to 1, false to 2),
            keys(deleteTargets(listOf(device(1)), listOf(device(1), device(2)), true, emptySet(), visible))
        )
    }

    @Test
    fun deleteSkipsOtherNames() {
        assertEquals(
            listOf(false to 1),
            keys(deleteTargets(listOf(device(1)), listOf(device(1), device(3, "Bob")), true, emptySet(), visible))
        )
    }

    @Test
    fun deleteSkipsKeptApartDuplicate() {
        assertEquals(
            listOf(false to 1),
            keys(
                deleteTargets(
                    listOf(device(1)),
                    listOf(device(1), device(2)),
                    true,
                    setOf(keptSeparatePair(1, 2)),
                    visible
                )
            )
        )
    }

    @Test
    fun deleteIncludesSelectedNamelessButNotItsLookalikes() {
        assertEquals(
            listOf(false to 5),
            keys(deleteTargets(listOf(nameless(5)), listOf(nameless(6), device(1)), true, emptySet(), visible))
        )
    }

    @Test
    fun deleteKeepsPrivateAndDeviceWithSameId() {
        assertEquals(
            listOf(false to 7, true to 7),
            keys(deleteTargets(listOf(device(7)), listOf(device(7), privateContact(7)), true, emptySet(), visible))
        )
    }

    @Test
    fun deleteListsEachTargetOnce() {
        assertEquals(
            listOf(false to 1, false to 2),
            keys(
                deleteTargets(
                    listOf(device(1), device(2)),
                    listOf(device(1), device(2)),
                    true,
                    emptySet(),
                    visible
                )
            )
        )
    }
}
