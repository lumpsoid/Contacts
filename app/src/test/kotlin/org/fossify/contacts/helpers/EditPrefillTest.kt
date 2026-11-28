package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import org.fossify.commons.helpers.DEFAULT_EMAIL_TYPE
import org.fossify.commons.helpers.DEFAULT_EVENT_TYPE
import org.fossify.commons.helpers.DEFAULT_PHONE_NUMBER_TYPE
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Address
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Email
import org.fossify.commons.models.contacts.Event
import org.fossify.commons.models.contacts.Organization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditPrefillTest {
    private val normalize: (String) -> String = { it.filter { c -> c.isDigit() || c == '+' } }
    private val empty = Contact(id = 0, contactId = 0)
    private fun row(mimetype: String, vararg values: Pair<String, Any?>): IntentRow =
        mapOf(CommonDataKinds.StructuredName.MIMETYPE to mimetype, *values)

    @Test
    fun phoneExtraIsAddedNormalised() {
        val result = Prefill(hasPhoneExtra = true, phoneExtra = "0903 123").applyTo(empty, false, normalize)
        assertEquals(
            listOf(PhoneNumber("0903 123", DEFAULT_PHONE_NUMBER_TYPE, "", "0903123")),
            result.contact.phoneNumbers,
        )
    }

    @Test
    fun withoutExtraTheFirstRowData1IsThePhone() {
        val prefill = Prefill(
            rows = listOf(row(CommonDataKinds.Phone.CONTENT_ITEM_TYPE, CommonDataKinds.Phone.DATA1 to "+123 456")),
        )
        val result = prefill.applyTo(empty, false, normalize)
        assertEquals(
            listOf(PhoneNumber("+123 456", DEFAULT_PHONE_NUMBER_TYPE, "", "+123456")),
            result.contact.phoneNumbers,
        )
    }

    @Test
    fun phoneExtraKeyWithNullValueAddsNoPhone() {
        val prefill = Prefill(
            hasPhoneExtra = true,
            phoneExtra = null,
            rows = listOf(row(CommonDataKinds.Phone.CONTENT_ITEM_TYPE, CommonDataKinds.Phone.DATA1 to "1")),
        )
        val result = prefill.applyTo(empty, false, normalize)
        assertTrue(result.contact.phoneNumbers.isEmpty())
    }

    @Test
    fun addingNumberHighlightsANonEmptyPhone() {
        assertTrue(Prefill(hasPhoneExtra = true, phoneExtra = "1").applyTo(empty, true, normalize).highlightNumber)
        assertFalse(Prefill(hasPhoneExtra = true, phoneExtra = "").applyTo(empty, true, normalize).highlightNumber)
        assertFalse(Prefill(hasPhoneExtra = true, phoneExtra = "1").applyTo(empty, false, normalize).highlightNumber)
    }

    @Test
    fun emailExtraIsAddedAndHighlighted() {
        val result = Prefill(email = "a@b").applyTo(empty, false, normalize)
        assertEquals(listOf(Email("a@b", DEFAULT_EMAIL_TYPE, "")), result.contact.emails)
        assertTrue(result.highlightEmail)
        assertFalse(Prefill().applyTo(empty, false, normalize).highlightEmail)
    }

    @Test
    fun nameBecomesTheFirstName() {
        val result = Prefill(name = "Ann Lee").applyTo(empty, false, normalize)
        assertEquals("Ann Lee", result.contact.firstName)
    }

    @Test
    fun emailRowKeepsItsType() {
        val work = Prefill(
            rows = listOf(
                row(
                    CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    CommonDataKinds.Email.DATA1 to "x@y",
                    CommonDataKinds.Email.DATA2 to CommonDataKinds.Email.TYPE_WORK,
                ),
            ),
        ).applyTo(empty, false, normalize)
        assertEquals(listOf(Email("x@y", CommonDataKinds.Email.TYPE_WORK, "")), work.contact.emails)

        val stringType = Prefill(
            rows = listOf(
                row(
                    CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    CommonDataKinds.Email.DATA1 to "x@y",
                    CommonDataKinds.Email.DATA2 to "2",
                ),
            ),
        ).applyTo(empty, false, normalize)
        assertEquals(listOf(Email("x@y", CommonDataKinds.Email.TYPE_WORK, "")), stringType.contact.emails)

        val noType = Prefill(
            rows = listOf(row(CommonDataKinds.Email.CONTENT_ITEM_TYPE, CommonDataKinds.Email.DATA1 to "x@y")),
        ).applyTo(empty, false, normalize)
        assertEquals(listOf(Email("x@y", DEFAULT_EMAIL_TYPE, "")), noType.contact.emails)
    }

    @Test
    fun rowWithoutValueIsSkipped() {
        val result = Prefill(
            rows = listOf(
                row(CommonDataKinds.Email.CONTENT_ITEM_TYPE),
                row(CommonDataKinds.Event.CONTENT_ITEM_TYPE),
                row(CommonDataKinds.Website.CONTENT_ITEM_TYPE),
            ),
        ).applyTo(empty, false, normalize)
        assertEquals(empty, result.contact)
    }

    @Test
    fun addressRowPrefersFormattedAddressData4() {
        val prefill = Prefill(
            rows = listOf(
                row(
                    CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                    CommonDataKinds.StructuredPostal.DATA1 to "one",
                    CommonDataKinds.StructuredPostal.DATA4 to "four",
                    CommonDataKinds.StructuredPostal.DATA2 to CommonDataKinds.StructuredPostal.TYPE_WORK,
                    CommonDataKinds.StructuredPostal.COUNTRY to "c",
                    CommonDataKinds.StructuredPostal.REGION to "r",
                    CommonDataKinds.StructuredPostal.CITY to "ci",
                    CommonDataKinds.StructuredPostal.POSTCODE to "pc",
                    CommonDataKinds.StructuredPostal.POBOX to "pb",
                    CommonDataKinds.StructuredPostal.STREET to "st",
                    CommonDataKinds.StructuredPostal.NEIGHBORHOOD to "nb",
                ),
            ),
        )
        val result = prefill.applyTo(empty, false, normalize)
        assertEquals(
            listOf(
                Address("four", CommonDataKinds.StructuredPostal.TYPE_WORK, "", "c", "r", "ci", "pc", "pb", "st", "nb"),
            ),
            result.contact.addresses,
        )
    }

    @Test
    fun organizationRowReplacesTheOrganization() {
        val base = empty.copy(organization = Organization("Old", "Job"))
        val full = Prefill(
            rows = listOf(
                row(
                    CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                    CommonDataKinds.Organization.DATA1 to "Co",
                    CommonDataKinds.Organization.DATA4 to "Dev",
                ),
            ),
        ).applyTo(base, false, normalize)
        assertEquals(Organization("Co", "Dev"), full.contact.organization)

        val onlyCompany = Prefill(
            rows = listOf(
                row(CommonDataKinds.Organization.CONTENT_ITEM_TYPE, CommonDataKinds.Organization.DATA1 to "Co"),
            ),
        ).applyTo(base, false, normalize)
        assertEquals(Organization("Co", ""), onlyCompany.contact.organization)
    }

    @Test
    fun eventWebsiteAndNoteRows() {
        val result = Prefill(
            rows = listOf(
                row(CommonDataKinds.Event.CONTENT_ITEM_TYPE, CommonDataKinds.Event.DATA1 to "--05-01"),
                row(CommonDataKinds.Website.CONTENT_ITEM_TYPE, CommonDataKinds.Website.DATA1 to "x.org"),
                row(CommonDataKinds.Note.CONTENT_ITEM_TYPE, CommonDataKinds.Note.DATA1 to "hi"),
            ),
        ).applyTo(empty, false, normalize).contact
        assertEquals(listOf(Event("--05-01", DEFAULT_EVENT_TYPE)), result.events)
        assertEquals(listOf("x.org"), result.websites)
        assertEquals("hi", result.notes)
    }

    @Test
    fun unknownMimetypeIsIgnored() {
        val prefill = Prefill(
            rows = listOf(row(CommonDataKinds.Im.CONTENT_ITEM_TYPE, "data1" to "handle")),
        )
        val result = prefill.applyTo(empty, false, normalize)
        assertEquals(empty, result.contact)
    }

    @Test
    fun theInputContactIsUntouched() {
        val base = Contact(
            id = 3,
            contactId = 3,
            emails = arrayListOf(Email("o@p", DEFAULT_EMAIL_TYPE, "")),
        )
        val result = Prefill(email = "a@b", hasPhoneExtra = true, phoneExtra = "1").applyTo(base, false, normalize)
        assertEquals(1, base.emails.size)
        assertTrue(base.phoneNumbers.isEmpty())
        assertEquals(2, result.contact.emails.size)
    }
}
