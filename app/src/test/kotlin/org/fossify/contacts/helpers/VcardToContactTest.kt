package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import ezvcard.Ezvcard
import org.fossify.commons.models.contacts.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VcardToContactTest {
    private fun parse(text: String): ParsedCard =
        Ezvcard.parse(text.trimIndent()).first().toParsedCard("src") { it }

    @Test
    fun voiceIsNotTakenAsACustomPhoneLabel() {
        val phone = parse(
            """
            BEGIN:VCARD
            VERSION:3.0
            TEL;TYPE=VOICE,CELL:+15550001
            END:VCARD
            """
        ).contact.phoneNumbers.single()
        assertEquals(CommonDataKinds.Phone.TYPE_MOBILE, phone.type)
        assertEquals("", phone.label)
    }

    @Test
    fun faxSubtypeIsRead() {
        val phone = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            TEL;TYPE=work,fax:+15550002
            END:VCARD
            """
        ).contact.phoneNumbers.single()
        assertEquals(CommonDataKinds.Phone.TYPE_FAX_WORK, phone.type)
    }

    @Test
    fun prefMarksThePrimaryNumber() {
        val v3Pref = parse(
            """
            BEGIN:VCARD
            VERSION:3.0
            TEL;TYPE=CELL,pref:+1
            END:VCARD
            """
        ).contact.phoneNumbers.single()
        assertTrue(v3Pref.isPrimary)

        val v4Pref = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            TEL;TYPE=cell;PREF=1:+2
            END:VCARD
            """
        ).contact.phoneNumbers.single()
        assertTrue(v4Pref.isPrimary)

        val legacyPref = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            TEL;TYPE=CELL,PREF=1:+3
            END:VCARD
            """
        ).contact.phoneNumbers.single()
        assertTrue(legacyPref.isPrimary)

        val plain = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            TEL;TYPE=CELL:+4
            END:VCARD
            """
        ).contact.phoneNumbers.single()
        assertFalse(plain.isPrimary)
    }

    @Test
    fun malformedPrefDoesNotThrow() {
        val phone = parse(
            """
            BEGIN:VCARD
            VERSION:3.0
            TEL;TYPE=CELL;TYPE=pref=x:+15550003
            END:VCARD
            """
        ).contact.phoneNumbers.single()
        assertFalse(phone.isPrimary)
    }

    @Test
    fun internetIsNotTakenAsACustomEmailLabel() {
        val email = parse(
            """
            BEGIN:VCARD
            VERSION:3.0
            EMAIL;TYPE=INTERNET,WORK:a@b.c
            END:VCARD
            """
        ).contact.emails.single()
        assertEquals(CommonDataKinds.Email.TYPE_WORK, email.type)
        assertEquals("", email.label)
    }

    @Test
    fun imHandleKeepsPlusAndPercent() {
        val skype = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            IMPP:skype:john+doe
            END:VCARD
            """
        ).contact.IMs.single()
        assertEquals("john+doe", skype.value)
        assertEquals(CommonDataKinds.Im.PROTOCOL_SKYPE, skype.type)

        val sip = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            IMPP:sip:a%25b
            END:VCARD
            """
        ).contact.IMs.single()
        assertEquals("a%b", sip.value)
    }

    @Test
    fun imWithoutSchemeIsCustom() {
        val im = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            IMPP:john@example.com
            END:VCARD
            """
        ).contact.IMs.single()
        assertEquals("john@example.com", im.value)
        assertEquals(CommonDataKinds.Im.PROTOCOL_CUSTOM, im.type)
        assertEquals("", im.label)
    }

    @Test
    fun unparseableImIsKeptAsText() {
        val im = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            IMPP:foo bar
            END:VCARD
            """
        ).contact.IMs.single()
        assertEquals("foo bar", im.value)
        assertEquals(CommonDataKinds.Im.PROTOCOL_CUSTOM, im.type)
    }

    @Test
    fun customImLabelIsRead() {
        val labelled = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            IMPP;X-LABEL=My IM:x-im:handle1
            END:VCARD
            """
        ).contact.IMs.single()
        assertEquals("My IM", labelled.label)
        assertEquals("handle1", labelled.value)
        assertEquals(CommonDataKinds.Im.PROTOCOL_CUSTOM, labelled.type)

        val scheme = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            IMPP:matrix:@me:server
            END:VCARD
            """
        ).contact.IMs.single()
        assertEquals("matrix", scheme.label)
        assertEquals("@me:server", scheme.value)
    }

    @Test
    fun textBirthdayIsKept() {
        val events = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            BDAY:circa 1800
            END:VCARD
            """
        ).contact.events
        assertEquals(listOf(Event("circa 1800", CommonDataKinds.Event.TYPE_BIRTHDAY)), events)
    }

    @Test
    fun datesKeepTheirForm() {
        val birthday = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            BDAY:19900215
            END:VCARD
            """
        ).contact.events
        assertEquals(listOf(Event("1990-02-15", CommonDataKinds.Event.TYPE_BIRTHDAY)), birthday)

        val anniversary = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            ANNIVERSARY:--0315
            END:VCARD
            """
        ).contact.events
        assertEquals(listOf(Event("--03-15", CommonDataKinds.Event.TYPE_ANNIVERSARY)), anniversary)
    }

    @Test
    fun otherEventsAreReadBack() {
        val events = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            X-FOSSIFY-EVENT;X-TYPE=2:2001-05-06
            END:VCARD
            """
        ).contact.events
        assertEquals(listOf(Event("2001-05-06", CommonDataKinds.Event.TYPE_OTHER)), events)
    }

    @Test
    fun everyNoteTitleOrgAndNicknameIsKept() {
        val contact = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            NOTE:one
            NOTE:two
            TITLE:Boss
            TITLE:Chair
            ORG:Acme;Sales
            NICKNAME:Jo,Jojo
            END:VCARD
            """
        ).contact
        assertEquals("one\ntwo", contact.notes)
        assertEquals("Boss, Chair", contact.organization.jobPosition)
        assertEquals("Acme, Sales", contact.organization.company)
        assertEquals("Jo, Jojo", contact.nickname)
    }

    @Test
    fun poBoxOnlyAddressIsKept() {
        val address = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            ADR;TYPE=home:PO 5;;;;;;
            END:VCARD
            """
        ).contact.addresses.single()
        assertEquals("PO 5", address.value)
        assertEquals("PO 5", address.pobox)
    }

    @Test
    fun labelIsTheFormattedAddress() {
        val address = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            ADR;LABEL="1 Main St, Springfield":;;1 Main St;Springfield;;;
            END:VCARD
            """
        ).contact.addresses.single()
        assertEquals("1 Main St, Springfield", address.value)
        assertEquals("1 Main St", address.street)
        assertEquals("Springfield", address.city)
    }

    @Test
    fun labelEqualToStreetKeepsTheStreet() {
        val address = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            ADR;LABEL="Somewhere 1":;;Somewhere 1;;;;
            END:VCARD
            """
        ).contact.addresses.single()
        assertEquals("Somewhere 1", address.value)
        assertEquals("Somewhere 1", address.street)
    }

    @Test
    fun badCardsInOneFileAllMap() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            FN:A
            N:A;;;;
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            N:B;;;;
            BDAY:circa 1800
            IMPP:foo%zz
            TEL;TYPE=pref=x:1
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            N:C;;;;
            END:VCARD
        """.trimIndent()
        val contacts = Ezvcard.parse(text).all().map { card ->
            card.toParsedCard("src") { it }.contact
        }
        assertEquals(listOf("A", "B", "C"), contacts.map { it.surname })
        assertTrue(contacts[1].events.contains(Event("circa 1800", CommonDataKinds.Event.TYPE_BIRTHDAY)))
    }

    @Test
    fun categoriesBecomeGroupNames() {
        val groupNames = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            CATEGORIES:Friends,Work
            END:VCARD
            """
        ).groupNames
        assertEquals(listOf("Friends", "Work"), groupNames)
    }

    @Test
    fun customPhoneLabelKeepsItsCase() {
        val phone = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            TEL;TYPE=Boat:+3
            END:VCARD
            """
        ).contact.phoneNumbers.single()
        assertEquals(CommonDataKinds.Phone.TYPE_CUSTOM, phone.type)
        assertEquals("Boat", phone.label)
    }

    @Test
    fun faxHomeInEitherOrder() {
        val fax = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            TEL;TYPE=fax,home:+5
            END:VCARD
            """
        ).contact.phoneNumbers.single()
        assertEquals(CommonDataKinds.Phone.TYPE_FAX_HOME, fax.type)

        val reversed = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            TEL;TYPE=home,fax:+7
            END:VCARD
            """
        ).contact.phoneNumbers.single()
        assertEquals(CommonDataKinds.Phone.TYPE_FAX_HOME, reversed.type)
    }

    @Test
    fun nonIntegerPrefParamDoesNotThrow() {
        val phone = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            TEL;TYPE=cell;PREF=x:+6
            END:VCARD
            """
        ).contact.phoneNumbers.single()
        assertFalse(phone.isPrimary)
    }

    @Test
    fun utcBirthdayIsADate() {
        val events = parse(
            """
            BEGIN:VCARD
            VERSION:4.0
            BDAY:19900101T000000Z
            END:VCARD
            """
        ).contact.events
        assertEquals(listOf(Event("1990-01-01", CommonDataKinds.Event.TYPE_BIRTHDAY)), events)
    }

    @Test
    fun textBirthdayIn30IsKept() {
        val events = parse(
            """
            BEGIN:VCARD
            VERSION:3.0
            BDAY:circa 1800
            END:VCARD
            """
        ).contact.events
        assertEquals(listOf(Event("circa 1800", CommonDataKinds.Event.TYPE_BIRTHDAY)), events)
    }
}
