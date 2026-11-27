package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Address
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Email
import org.fossify.commons.models.contacts.Event
import org.fossify.commons.models.contacts.IM
import org.fossify.commons.models.contacts.Organization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditFormTest {
    private val normalize: (String) -> String = { it.filter { c -> c.isDigit() || c == '+' } }
    private val same: (String, String) -> Boolean = { a, b -> a.isNotEmpty() && a.takeLast(9) == b.takeLast(9) }
    private val base = Contact(id = 1, contactId = 1, source = "acc")

    @Test
    fun blankFormIsBlank() {
        assertTrue(FormSnapshot().isBlank("Unknown"))
    }

    @Test
    fun anyTextFieldMakesFormNotBlank() {
        assertFalse(FormSnapshot(jobPosition = "Dev").isBlank("Unknown"))
        assertFalse(FormSnapshot(photoPath = "p").isBlank("Unknown"))
    }

    @Test
    fun emptyRowsKeepFormBlank() {
        val form = FormSnapshot(
            numbers = listOf(NumberRow()),
            emails = listOf(TypedRow()),
            addresses = listOf(PlainAddress(), StructuredAddress()),
            events = listOf(EventRow(shown = "Unknown", date = "x")),
            websites = listOf(""),
        )
        assertTrue(form.isBlank("Unknown"))
        assertFalse(FormSnapshot(events = listOf(EventRow(shown = "1990-01-01", date = "1990-01-01")))
            .isBlank("Unknown"))
    }

    @Test
    fun customLabelOnlyForCustomPhoneType() {
        val custom = NumberRow("555", CommonDataKinds.Phone.TYPE_CUSTOM, "Boat")
        val home = NumberRow("555", CommonDataKinds.Phone.TYPE_HOME, "Home")
        assertEquals("Boat", custom.toPhoneNumber(normalize, same).label)
        assertEquals("", home.toPhoneNumber(normalize, same).label)
    }

    @Test
    fun fetchedNormalizedNumberKeptWhenSame() {
        val kept = NumberRow("0903 123456", fetchedNormalized = "+421903123456")
        assertEquals("+421903123456", kept.toPhoneNumber(normalize, same).normalizedNumber)
        val other = NumberRow("0903 123456", fetchedNormalized = "+421111111111")
        assertEquals("0903123456", other.toPhoneNumber(normalize, same).normalizedNumber)
        assertEquals(
            "0903123456",
            NumberRow("0903 123456", fetchedNormalized = "").toPhoneNumber(normalize, same).normalizedNumber
        )
    }

    @Test
    fun primaryAndStarredCarried() {
        val starred = FormSnapshot(
            starred = true,
            numbers = listOf(NumberRow("555", isPrimary = true)),
        ).toContact(base, "Unknown", normalize, same)
        assertEquals(1, starred.starred)
        assertTrue(starred.phoneNumbers[0].isPrimary)

        val notStarred = FormSnapshot(starred = false).toContact(base, "Unknown", normalize, same)
        assertEquals(0, notStarred.starred)
    }

    @Test
    fun structuredAddressFormatting() {
        val address = StructuredAddress(
            street = "Main St 1", pobox = "PO 5", neighborhood = "Old Town", postcode = "12345",
            city = "Town", region = "Region", country = "de",
        )
        assertEquals("Main St 1 PO 5 Old Town\n12345 Town\nRegion\nDE", address.formatted)
        val built = address.toAddress()
        assertEquals("de", built.country)
        assertEquals("Main St 1", built.street)
        assertEquals("Town", StructuredAddress(city = "Town").formatted)
        assertFalse(StructuredAddress().isFilled)
    }

    @Test
    fun plainAddressHasNoParts() {
        assertEquals(
            Address("Somewhere 1", CommonDataKinds.StructuredPostal.TYPE_CUSTOM, "Cabin", "", "", "", "", "", "", ""),
            PlainAddress("Somewhere 1", CommonDataKinds.StructuredPostal.TYPE_CUSTOM, "Cabin").toAddress()
        )
        val work = PlainAddress("Somewhere 1", CommonDataKinds.StructuredPostal.TYPE_WORK, "Cabin")
        assertEquals("", work.toAddress().label)
    }

    @Test
    fun toContactDropsEmptyRowsAndKeepsOrder() {
        val form = FormSnapshot(
            prefix = "Mr", firstName = "Ann", middleName = "M", surname = "Lee", suffix = "Jr", nickname = "A",
            notes = "n", company = "Co", jobPosition = "Dev", photoPath = "ph", starred = true,
            numbers = listOf(NumberRow("555", CommonDataKinds.Phone.TYPE_HOME, "Home"), NumberRow()),
            emails = listOf(
                TypedRow("a@b", CommonDataKinds.Email.TYPE_CUSTOM, "Mine"),
                TypedRow(),
            ),
            ims = listOf(TypedRow("me", CommonDataKinds.Im.PROTOCOL_CUSTOM, "Chat")),
            events = listOf(
                EventRow(shown = "May 1", date = "--05-01", type = CommonDataKinds.Event.TYPE_ANNIVERSARY),
                EventRow(shown = "Unknown", date = "--01-01"),
            ),
            websites = listOf("", "x.org"),
        )
        val contact = form.toContact(base, "Unknown", normalize, same)

        assertEquals(1, contact.id)
        assertEquals("acc", contact.source)
        assertEquals("Mr", contact.prefix)
        assertEquals("Ann", contact.firstName)
        assertEquals("M", contact.middleName)
        assertEquals("Lee", contact.surname)
        assertEquals("Jr", contact.suffix)
        assertEquals("A", contact.nickname)
        assertEquals("n", contact.notes)
        assertEquals("ph", contact.photoUri)
        assertEquals(1, contact.starred)
        assertEquals(arrayListOf(PhoneNumber("555", CommonDataKinds.Phone.TYPE_HOME, "", "555")), contact.phoneNumbers)
        assertEquals(1, contact.emails.size)
        assertEquals(Email("a@b", CommonDataKinds.Email.TYPE_CUSTOM, "Mine"), contact.emails[0])
        assertEquals(1, contact.IMs.size)
        assertEquals("Chat", contact.IMs[0].label)
        assertEquals(IM("me", CommonDataKinds.Im.PROTOCOL_CUSTOM, "Chat"), contact.IMs[0])
        assertEquals(
            arrayListOf(Event("--05-01", CommonDataKinds.Event.TYPE_ANNIVERSARY)),
            contact.events
        )
        assertEquals(arrayListOf("x.org"), contact.websites)
        assertEquals(Organization("Co", "Dev"), contact.organization)
    }

    @Test
    fun loadedValuesRoundTripUnchanged() {
        val loaded = base.copy(
            firstName = "Ann",
            phoneNumbers = arrayListOf(
                PhoneNumber("0903 123456", CommonDataKinds.Phone.TYPE_MOBILE, "", "+421903123456"),
            ),
        )
        val snapshot = FormSnapshot(
            firstName = "Ann",
            numbers = listOf(NumberRow("0903 123456", CommonDataKinds.Phone.TYPE_MOBILE, "Mobile", "+421903123456")),
        )
        assertEquals(loaded, snapshot.toContact(base, "Unknown", normalize, same))
    }
}
