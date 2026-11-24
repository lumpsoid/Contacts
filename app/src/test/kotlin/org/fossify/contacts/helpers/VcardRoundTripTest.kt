package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import ezvcard.Ezvcard
import ezvcard.VCardVersion
import ezvcard.parameter.ImageType
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Address
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Event
import org.fossify.commons.models.contacts.IM
import org.fossify.commons.models.contacts.Organization
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VcardRoundTripTest {
    private fun write(contact: Contact, photo: ByteArray? = null): String =
        Ezvcard.write(contact.toVCard(photo)).version(VCardVersion.V4_0).go()

    private fun roundTrip(contact: Contact, photo: ByteArray? = null): ParsedCard =
        Ezvcard.parse(write(contact, photo)).first().toParsedCard("src") { it }

    @Test
    fun structuredAddressKeepsEveryPart() {
        val address = Address(
            value = "1 Main St, 12345 Springfield",
            type = CommonDataKinds.StructuredPostal.TYPE_HOME,
            label = "",
            country = "US",
            region = "IL",
            city = "Springfield",
            postcode = "12345",
            pobox = "PO 9",
            street = "1 Main St",
            neighborhood = "Downtown",
        )
        val contact = Contact(id = 0, contactId = 0, firstName = "Ann", addresses = arrayListOf(address))
        assertEquals(address, roundTrip(contact).contact.addresses.single())
    }

    @Test
    fun formattedOnlyAddressKeepsItsText() {
        val address = Address(
            value = "Somewhere 1, Town",
            type = CommonDataKinds.StructuredPostal.TYPE_HOME,
            label = "",
        )
        val contact = Contact(id = 0, contactId = 0, firstName = "Ann", addresses = arrayListOf(address))
        val parsed = roundTrip(contact).contact.addresses.single()
        assertEquals("Somewhere 1, Town", parsed.value)
        assertEquals("Somewhere 1, Town", parsed.street)
        assertEquals(address.type, parsed.type)
        assertEquals(address.label, parsed.label)
        assertFalse(write(contact).contains("LABEL"))
    }

    @Test
    fun streetOnlyStructuredAddressSurvives() {
        val address = Address(
            value = "1 Main St",
            type = CommonDataKinds.StructuredPostal.TYPE_HOME,
            label = "",
            street = "1 Main St",
        )
        val contact = Contact(id = 0, contactId = 0, firstName = "Ann", addresses = arrayListOf(address))
        assertEquals(address, roundTrip(contact).contact.addresses.single())
    }

    @Test
    fun imHandlesSurvive() {
        val ims = arrayListOf(
            IM("+15551234", CommonDataKinds.Im.PROTOCOL_SKYPE, ""),
            IM("john+doe%1", CommonDataKinds.Im.PROTOCOL_CUSTOM, "My IM"),
            IM("h2", CommonDataKinds.Im.PROTOCOL_CUSTOM, ""),
            IM("h3", CommonDataKinds.Im.PROTOCOL_CUSTOM, "matrix"),
            IM("h4", CommonDataKinds.Im.PROTOCOL_CUSTOM, "Skype"),
        )
        val contact = Contact(id = 0, contactId = 0, firstName = "Ann", IMs = ims)
        assertEquals(ims, roundTrip(contact).contact.IMs)
    }

    @Test
    fun everyEventTypeSurvives() {
        val events = listOf(
            Event("1990-02-15", CommonDataKinds.Event.TYPE_BIRTHDAY),
            Event("--03-15", CommonDataKinds.Event.TYPE_ANNIVERSARY),
            Event("2001-05-06", CommonDataKinds.Event.TYPE_OTHER),
            Event("circa 1800", CommonDataKinds.Event.TYPE_BIRTHDAY),
        )
        val contact = Contact(id = 0, contactId = 0, firstName = "Ann", events = ArrayList(events))
        assertEquals(events.toSet(), roundTrip(contact).contact.events.toSet())
    }

    @Test
    fun primaryAndFaxPhonesSurvive() {
        val phones = arrayListOf(
            PhoneNumber("+1", CommonDataKinds.Phone.TYPE_MOBILE, "", "+1", isPrimary = true),
            PhoneNumber("+2", CommonDataKinds.Phone.TYPE_FAX_WORK, "", "+2"),
            PhoneNumber("+3", CommonDataKinds.Phone.TYPE_CUSTOM, "Boat", "+3"),
        )
        val contact = Contact(id = 0, contactId = 0, firstName = "Ann", phoneNumbers = phones)
        assertEquals(phones, roundTrip(contact).contact.phoneNumbers)
    }

    @Test
    fun notesAndOrganizationSurvive() {
        val contact = Contact(
            id = 0,
            contactId = 0,
            firstName = "Ann",
            nickname = "Jo",
            notes = "line1\nline2",
            organization = Organization("Acme, Inc", "CTO"),
        )
        val parsed = roundTrip(contact).contact
        assertEquals("line1\nline2", parsed.notes)
        assertEquals(Organization("Acme, Inc", "CTO"), parsed.organization)
        assertEquals("Jo", parsed.nickname)
    }

    @Test
    fun emptyJobTitleIsNotWritten() {
        val contact = Contact(id = 0, contactId = 0, firstName = "Ann", organization = Organization("Acme", ""))
        assertTrue(contact.toVCard(null).titles.isEmpty())
        assertFalse(write(contact).contains("TITLE"))
    }

    @Test
    fun pngPhotoIsTaggedPng() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0)
        val contact = Contact(id = 0, contactId = 0, firstName = "Ann")
        assertEquals(ImageType.PNG, contact.toVCard(png).photos.first().contentType)
        assertEquals(ImageType.JPEG, contact.toVCard(byteArrayOf(1, 2, 3)).photos.first().contentType)
        assertArrayEquals(png, roundTrip(contact, png).photo)
    }
}
