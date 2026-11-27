package org.fossify.contacts.screens.viewcontact

import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.models.TransferMode
import org.fossify.contacts.screens.viewcontact.ViewContactAction as A
import org.fossify.contacts.screens.viewcontact.ViewContactEffect as E
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewContactUpdateTest {
    private fun device(contactId: Int = 7) = Contact(id = 70, contactId = contactId, firstName = "Ann", source = "acc")

    private fun privateContact() = Contact(id = 5, contactId = 5, firstName = "Ann", source = "smt_private")

    private fun intent(
        contactId: Int? = 7,
        isView: Boolean = false,
        data: IntentData = IntentData.NONE,
        isPrivate: Boolean = false,
    ) = IntentFacts(contactId, isPrivate, isView, data)

    @Test
    fun nonViewIntentLoadsItsIdWithThePrivateFlag() {
        val next = update(ViewContactState(), A.Resumed(intent(isPrivate = true)))
        assertEquals(listOf(E.LoadContact(7, true)), next.effects)
        assertEquals(intent(isPrivate = true), next.state.intent)
    }

    @Test
    fun viewIntentAsksForReadPermissionThenLoadsItsId() {
        val asked = update(ViewContactState(), A.Resumed(intent(isView = true)))
        assertEquals(listOf(E.RequestReadPermission), asked.effects)

        val next = update(asked.state, A.PermissionResult(true))
        assertEquals(listOf(E.LoadContact(7, false)), next.effects)
    }

    @Test
    fun deniedPermissionNotifiesAndLeaves() {
        val asked = update(ViewContactState(), A.Resumed(intent(isView = true)))
        val next = update(asked.state, A.PermissionResult(false))
        assertEquals(listOf(E.Notify(ViewContactNotice.NO_CONTACTS_PERMISSION), E.Finish), next.effects)
    }

    @Test
    fun viewIntentWithoutIdResolvesItsDataUri() {
        val lookup = intent(contactId = 0, isView = true, data = IntentData.LOOKUP)
        val lookupAsked = update(ViewContactState(), A.Resumed(lookup))
        val lookupNext = update(lookupAsked.state, A.PermissionResult(true))
        assertEquals(listOf(E.ResolveLookupUri), lookupNext.effects)

        val contact = intent(contactId = 0, isView = true, data = IntentData.CONTACT)
        val contactAsked = update(ViewContactState(), A.Resumed(contact))
        val contactNext = update(contactAsked.state, A.PermissionResult(true))
        assertEquals(listOf(E.ResolveContactUri), contactNext.effects)
    }

    @Test
    fun nonViewIntentIgnoresItsDataUriAndLeaves() {
        val next = update(ViewContactState(), A.Resumed(intent(contactId = 0, data = IntentData.LOOKUP)))
        assertEquals(listOf(E.Finish), next.effects)
    }

    @Test
    fun unreadableContactIdStartsNothing() {
        val next = update(ViewContactState(), A.Resumed(intent(contactId = null)))
        assertEquals(emptyList<E>(), next.effects)
    }

    @Test
    fun lookupHitWinsOverRawId() {
        val next = update(ViewContactState(), A.LookupResolved(device(), 99))
        assertEquals(device(), next.state.contact)
        assertEquals(listOf(E.ShowContact, E.LoadLinkedRawIds(7)), next.effects)
    }

    @Test
    fun lookupMissLoadsRawIdOrLeavesWhenNoneIsLeft() {
        val held = ViewContactState(contact = device())

        val missed = update(held, A.LookupResolved(null, 42))
        assertNull(missed.state.contact)
        assertEquals(listOf(E.LoadContact(42, false)), missed.effects)

        val none = update(held, A.LookupResolved(null, -1))
        assertEquals(listOf(E.Finish), none.effects)
    }

    @Test
    fun uriResolvedLoadsRawIdOrFallsBackToTheHeldContact() {
        val loaded = update(ViewContactState(), A.UriResolved(42))
        assertEquals(listOf(E.LoadContact(42, false)), loaded.effects)

        val none = update(ViewContactState(), A.UriResolved(-1))
        assertEquals(listOf(E.Finish), none.effects)

        val held = update(ViewContactState(contact = device()), A.UriResolved(-1))
        assertEquals(listOf(E.ShowContact, E.LoadLinkedRawIds(7)), held.effects)
    }

    @Test
    fun unknownContactToastsUnlessAnEditWasLaunched() {
        val loaded = update(ViewContactState(), A.ContactLoaded(null))
        assertEquals(listOf(E.Notify(ViewContactNotice.UNKNOWN_ERROR), E.Finish), loaded.effects)

        val edited = update(ViewContactState(contact = device()), A.EditClicked(false))
        assertEquals(listOf(E.Edit(device(), false)), edited.effects)
        assertTrue(edited.state.wasEditLaunched)
        val afterEdit = update(edited.state, A.ContactLoaded(null))
        assertEquals(listOf(E.Finish), afterEdit.effects)
    }

    @Test
    fun privateContactDropsLinkedRawIdsAndOffersNoUnlink() {
        val held = ViewContactState(linkedRawIds = listOf(1, 2))
        val next = update(held, A.ContactLoaded(privateContact()))
        assertEquals(emptyList<Int>(), next.state.linkedRawIds)
        assertFalse(next.state.showUnlink)
        assertEquals(listOf(E.ShowContact), next.effects)
    }

    @Test
    fun contactWithoutContactIdDropsLinkedRawIds() {
        val held = ViewContactState(linkedRawIds = listOf(1, 2))
        val next = update(held, A.ContactLoaded(device(contactId = 0)))
        assertEquals(emptyList<Int>(), next.state.linkedRawIds)
        assertEquals(listOf(E.ShowContact), next.effects)
    }

    @Test
    fun unlinkNeedsMoreThanOneLinkedRawId() {
        val loaded = update(ViewContactState(), A.ContactLoaded(device()))
        assertEquals(listOf(E.ShowContact, E.LoadLinkedRawIds(7)), loaded.effects)

        val one = update(loaded.state, A.LinkedRawIdsLoaded(listOf(1)))
        assertFalse(one.state.showUnlink)
        assertEquals(emptyList<E>(), one.effects)

        val two = update(one.state, A.LinkedRawIdsLoaded(listOf(1, 2)))
        assertTrue(two.state.showUnlink)
    }

    @Test
    fun unlinkClickedWithOneLinkedRawIdDoesNothing() {
        val one = update(ViewContactState(linkedRawIds = listOf(1)), A.UnlinkClicked)
        assertEquals(emptyList<E>(), one.effects)

        val two = update(ViewContactState(linkedRawIds = listOf(1, 2)), A.UnlinkClicked)
        assertEquals(listOf(E.Unlink(listOf(1, 2))), two.effects)
    }

    @Test
    fun unlinkedLeavesOnlyOnSuccess() {
        val none = ViewContactState()
        val success = update(none, A.Unlinked(true))
        assertEquals(listOf(E.Notify(ViewContactNotice.CONTACTS_UNLINKED), E.Finish), success.effects)

        val failure = update(none, A.Unlinked(false))
        assertEquals(emptyList<E>(), failure.effects)
    }

    @Test
    fun deleteClickedAsksForAllSourcesOnlyWithSeveralSourcesAndMerge() {
        val state = ViewContactState()
        assertEquals(listOf(E.ConfirmDelete(true)), update(state, A.DeleteClicked(2, true)).effects)
        assertEquals(listOf(E.ConfirmDelete(false)), update(state, A.DeleteClicked(2, false)).effects)
        assertEquals(listOf(E.ConfirmDelete(false)), update(state, A.DeleteClicked(1, true)).effects)
    }

    @Test
    fun deleteConfirmedUsesTheHeldContact() {
        val held = ViewContactState(contact = device())
        val merged = update(held, A.DeleteConfirmed(true))
        assertEquals(listOf(E.LoadMergedDeleteScope(device())), merged.effects)

        val single = update(held, A.DeleteConfirmed(false))
        assertEquals(listOf(E.DeleteSingle(device())), single.effects)

        val none = update(ViewContactState(), A.DeleteConfirmed(true))
        assertEquals(emptyList<E>(), none.effects)
    }

    @Test
    fun mergedDeleteScopeLoadedDeletesMergedContacts() {
        val a = device(contactId = 3)
        val b = device(contactId = 4)
        val next = update(ViewContactState(), A.MergedDeleteScopeLoaded(listOf(a, b), listOf(3, 4)))
        assertEquals(listOf(E.DeleteMerged(listOf(a, b), listOf(3, 4))), next.effects)
    }

    @Test
    fun mergedDeletedRemovesLinkedRawsOnlyOnSuccess() {
        val none = ViewContactState()
        val success = update(none, A.MergedDeleted(true, listOf(3, 4)))
        assertEquals(listOf(E.DeleteRawContacts(listOf(3, 4)), E.Finish), success.effects)

        val failure = update(none, A.MergedDeleted(false, listOf(3, 4)))
        assertEquals(emptyList<E>(), failure.effects)
    }

    @Test
    fun singleDeletedLeaves() {
        val next = update(ViewContactState(), A.SingleDeleted)
        assertEquals(listOf(E.Finish), next.effects)
    }

    @Test
    fun menuActionsNeedAHeldContact() {
        val empty = ViewContactState()
        assertEquals(emptyList<E>(), update(empty, A.ShareClicked).effects)
        assertEquals(emptyList<E>(), update(empty, A.OpenWithClicked).effects)
        assertEquals(emptyList<E>(), update(empty, A.TransferClicked(TransferMode.COPY)).effects)

        val c = device()
        val held = ViewContactState(contact = c)
        assertEquals(listOf(E.Share(c)), update(held, A.ShareClicked).effects)
        assertEquals(listOf(E.OpenWith(c)), update(held, A.OpenWithClicked).effects)
        val copy = update(held, A.TransferClicked(TransferMode.COPY))
        assertEquals(listOf(E.Transfer(c, TransferMode.COPY)), copy.effects)
    }

    @Test
    fun editLaunchesWithTheMergeFlagAndSourceEditsExactly() {
        val c = device()
        val edit = update(ViewContactState(contact = c), A.EditClicked(true))
        assertEquals(listOf(E.Edit(c, true)), edit.effects)
        assertTrue(edit.state.wasEditLaunched)

        val other = device(contactId = 8)
        val source = update(ViewContactState(), A.EditSourceClicked(other))
        assertEquals(listOf(E.EditExact(other)), source.effects)
    }

    @Test
    fun onlyAMoveThatMovedRowsFinishes() {
        val none = ViewContactState()
        assertEquals(listOf(E.Finish), update(none, A.Transferred(TransferMode.MOVE, 1)).effects)
        assertEquals(emptyList<E>(), update(none, A.Transferred(TransferMode.MOVE, 0)).effects)
        assertEquals(emptyList<E>(), update(none, A.Transferred(TransferMode.COPY, 1)).effects)
    }

    @Test
    fun openWithNeedsANonPrivateContact() {
        assertFalse(ViewContactState().showOpenWith)
        assertFalse(ViewContactState(contact = privateContact()).showOpenWith)
        assertTrue(ViewContactState(contact = device()).showOpenWith)
    }

    @Test
    fun favoriteToggleStarsThenUnstarsWithoutTouchingTheOriginal() {
        val original = device()
        val starred = update(ViewContactState(contact = original), A.FavoriteToggled)
        assertEquals(1, starred.state.contact?.starred)
        assertEquals(listOf(E.AddFavorite(device().copy(starred = 1))), starred.effects)

        val unstarred = update(starred.state, A.FavoriteToggled)
        assertEquals(0, unstarred.state.contact?.starred)
        assertEquals(listOf(E.RemoveFavorite(device())), unstarred.effects)

        assertEquals(0, original.starred)
    }

    @Test
    fun ringtonePickedSavesToTheProviderOrLocallyForPrivateContacts() {
        val onDevice = update(ViewContactState(contact = device()), A.RingtonePicked("a.ogg"))
        assertEquals(listOf(E.SaveProviderRingtone(7, "a.ogg")), onDevice.effects)
        assertEquals("a.ogg", onDevice.state.contact?.ringtone)

        val onPrivate = update(ViewContactState(contact = privateContact()), A.RingtonePicked("a.ogg"))
        assertEquals(listOf(E.SaveLocalRingtone(5, "a.ogg")), onPrivate.effects)
    }

    @Test
    fun ringtonePickedNullClearsTheRingtoneAsAnEmptyPath() {
        val next = update(ViewContactState(contact = device()), A.RingtonePicked(null))
        assertEquals(listOf(E.SaveProviderRingtone(7, "")), next.effects)
        assertNull(next.state.contact?.ringtone)
    }
}
