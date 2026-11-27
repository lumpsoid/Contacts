package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import org.fossify.contacts.models.ExtraDetailRow
import org.fossify.contacts.models.LabeledValue
import org.fossify.contacts.models.PhoneticName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraDetailsRowsTest {
    private fun row(
        rawId: Int,
        mimetype: String,
        data1: String? = null,
        data2: Int = 0,
        data3: String? = null,
        given: String? = null,
        middle: String? = null,
        family: String? = null
    ) = ExtraDetailRow(rawId, mimetype, data1, data2, data3, given, middle, family)

    @Test
    fun sipAndRelationRowsGroupByRawContact() {
        val result = extraDetailsOf(
            listOf(
                row(1, SipAddress.CONTENT_ITEM_TYPE, data1 = "a@sip", data2 = 1),
                row(1, Relation.CONTENT_ITEM_TYPE, data1 = "Bob", data2 = 2, data3 = ""),
                row(2, SipAddress.CONTENT_ITEM_TYPE, data1 = "c@sip", data2 = 1)
            )
        )
        assertEquals(setOf(1, 2), result.keys)
        assertEquals(listOf(LabeledValue("a@sip", 1, "")), result.getValue(1).sipAddresses)
        assertEquals(listOf(LabeledValue("Bob", 2, "")), result.getValue(1).relations)
    }

    @Test
    fun blankValuesAreDropped() {
        val result = extraDetailsOf(
            listOf(
                row(1, SipAddress.CONTENT_ITEM_TYPE, data1 = "  ", data2 = 1),
                row(1, SipAddress.CONTENT_ITEM_TYPE, data1 = null, data2 = 1)
            )
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun theFirstNonEmptyPhoneticNameWins() {
        val result = extraDetailsOf(
            listOf(
                row(1, StructuredName.CONTENT_ITEM_TYPE),
                row(1, StructuredName.CONTENT_ITEM_TYPE, given = "Ann"),
                row(1, StructuredName.CONTENT_ITEM_TYPE, given = "Zed")
            )
        )
        assertEquals(PhoneticName("Ann"), result.getValue(1).phoneticName)
    }

    @Test
    fun otherMimetypesAreIgnored() {
        val result = extraDetailsOf(
            listOf(row(1, "vnd.android.cursor.item/email_v2", data1 = "x"))
        )
        assertTrue(result.isEmpty())
    }
}
