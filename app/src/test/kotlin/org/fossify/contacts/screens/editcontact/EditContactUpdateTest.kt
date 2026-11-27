package org.fossify.contacts.screens.editcontact

import android.provider.ContactsContract.CommonDataKinds
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Organization
import org.fossify.contacts.helpers.FormSnapshot
import org.fossify.contacts.helpers.NumberRow
import org.fossify.contacts.screens.editcontact.EditContactAction as A
import org.fossify.contacts.screens.editcontact.EditContactEffect as E
import org.junit.Assert.assertEquals
import org.junit.Test

class EditContactUpdateTest {
    private val normalize: (String) -> String = { it.filter { c -> c.isDigit() || c == '+' } }
    private val same: (String, String) -> Boolean = { a, b -> a.isNotEmpty() && a.takeLast(9) == b.takeLast(9) }

    private val saved = Contact(
        id = 1, contactId = 1, source = "acc", firstName = "Ann", surname = "Lee", notes = "n",
        organization = Organization("Co", "Dev"),
        phoneNumbers = arrayListOf(PhoneNumber("0903123456", CommonDataKinds.Phone.TYPE_HOME, "", "+421903123456")),
        websites = arrayListOf("x.org"),
        ringtone = "r",
    )

    private val sameForm = FormSnapshot(
        firstName = "Ann", surname = "Lee", notes = "n", company = "Co", jobPosition = "Dev",
        numbers = listOf(
            NumberRow("0903123456", CommonDataKinds.Phone.TYPE_HOME, "Home", fetchedNormalized = "+421903123456"),
            NumberRow(),
        ),
        websites = listOf("x.org", ""),
    )

    private fun formOf(snapshot: FormSnapshot) = snapshot.toContact(saved, "Unknown", normalize, same)

    private fun facts(
        form: Contact? = formOf(sameForm),
        saving: Boolean = false,
        saved: Contact? = this.saved,
        ringtone: String? = "r",
    ) = BackFacts(saving, saved, form, ringtone)

    private val dirty = facts(form = formOf(sameForm.copy(firstName = "Anne")))

    private fun leave(
        facts: BackFacts,
        now: Long = 5000L,
        via: LeaveVia = LeaveVia.BACK,
        from: EditContactState = EditContactState(),
    ) = update(from, A.LeaveRequested(via, facts, now))

    @Test
    fun unchangedFormLeavesWithoutPrompt() {
        assertEquals(saved, formOf(sameForm))
        val next = leave(facts())
        assertEquals(listOf(E.Exit(LeaveVia.BACK)), next.effects)
        assertEquals(EditContactState(), next.state)
    }

    @Test
    fun editedFieldPrompts() {
        val next = leave(dirty)
        assertEquals(listOf(E.AskToSave(LeaveVia.BACK)), next.effects)
        assertEquals(5000L, next.state.lastPromptAt)
    }

    @Test
    fun editedNumberPrompts() {
        val numbers = listOf(
            NumberRow("0903123457", CommonDataKinds.Phone.TYPE_HOME, fetchedNormalized = "+421903123456"),
        )
        val form = formOf(sameForm.copy(numbers = numbers))
        assertEquals(listOf(E.AskToSave(LeaveVia.BACK)), leave(facts(form = form)).effects)
    }

    @Test
    fun changedRingtonePrompts() {
        assertEquals(listOf(E.AskToSave(LeaveVia.BACK)), leave(facts(ringtone = "old")).effects)
    }

    @Test
    fun noContactPromptsOnlyForARingtone() {
        val noRingtone = facts(saved = null, form = null, ringtone = null)
        val withRingtone = facts(saved = null, form = null, ringtone = "r")
        assertEquals(listOf(E.Exit(LeaveVia.BACK)), leave(noRingtone).effects)
        assertEquals(listOf(E.AskToSave(LeaveVia.BACK)), leave(withRingtone).effects)
    }

    @Test
    fun savingSwallowsTheRequest() {
        val from = EditContactState(lastPromptAt = 7L)
        val next = leave(dirty.copy(saving = true), from = from)
        assertEquals(emptyList<E>(), next.effects)
        assertEquals(from, next.state)
    }

    @Test
    fun secondBackWithinTheIntervalLeaves() {
        val first = leave(dirty, now = 5000L).state
        val within = leave(dirty, now = 5999L, from = first)
        assertEquals(listOf(E.Exit(LeaveVia.BACK)), within.effects)
        assertEquals(5000L, within.state.lastPromptAt)
        assertEquals(listOf(E.Exit(LeaveVia.BACK)), leave(dirty, now = 6000L, from = first).effects)
        val after = leave(dirty, now = 6001L, from = first)
        assertEquals(listOf(E.AskToSave(LeaveVia.BACK)), after.effects)
        assertEquals(6001L, after.state.lastPromptAt)
    }

    @Test
    fun leavingWithoutPromptKeepsTheTimestamp() {
        val next = leave(facts(), now = 9000L, from = EditContactState(lastPromptAt = 3000L))
        assertEquals(EditContactState(lastPromptAt = 3000L), next.state)
    }

    @Test
    fun toolbarWayIsCarriedThroughThePrompt() {
        assertEquals(listOf(E.AskToSave(LeaveVia.TOOLBAR)), leave(dirty, via = LeaveVia.TOOLBAR).effects)
        val answered = update(EditContactState(), A.PromptAnswered(LeaveVia.TOOLBAR, save = false))
        assertEquals(listOf(E.Exit(LeaveVia.TOOLBAR)), answered.effects)
        assertEquals(listOf(E.Exit(LeaveVia.TOOLBAR)), leave(facts(), via = LeaveVia.TOOLBAR).effects)
    }

    @Test
    fun answeringSaveSaves() {
        val from = EditContactState(lastPromptAt = 5000L)
        val next = update(from, A.PromptAnswered(LeaveVia.BACK, save = true))
        assertEquals(listOf(E.Save), next.effects)
        assertEquals(from, next.state)
    }
}
