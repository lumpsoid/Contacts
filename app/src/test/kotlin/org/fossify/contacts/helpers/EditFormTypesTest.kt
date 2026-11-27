package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import org.junit.Assert.assertEquals
import org.junit.Test

class EditFormTypesTest {
    private val label: (Int) -> String = { "s$it" }

    @Test
    fun knownTextMapsToItsType() {
        listOf(PHONE_TYPE_LABELS, EMAIL_TYPE_LABELS, EVENT_TYPE_LABELS, ADDRESS_TYPE_LABELS, IM_TYPE_LABELS)
            .forEach { table ->
                table.forEach { (res, id) ->
                    assertEquals(id, typeIdOf(label(res), table, -99, label))
                }
            }
    }

    @Test
    fun unknownTextFallsBack() {
        assertEquals(
            CommonDataKinds.Phone.TYPE_CUSTOM,
            typeIdOf("Boat", PHONE_TYPE_LABELS, CommonDataKinds.Phone.TYPE_CUSTOM, label)
        )
        assertEquals(
            CommonDataKinds.Event.TYPE_OTHER,
            typeIdOf("Boat", EVENT_TYPE_LABELS, CommonDataKinds.Event.TYPE_OTHER, label)
        )
        assertEquals(
            CommonDataKinds.Email.TYPE_CUSTOM,
            typeIdOf("Boat", EMAIL_TYPE_LABELS, CommonDataKinds.Email.TYPE_CUSTOM, label)
        )
        assertEquals(
            CommonDataKinds.StructuredPostal.TYPE_CUSTOM,
            typeIdOf("Boat", ADDRESS_TYPE_LABELS, CommonDataKinds.StructuredPostal.TYPE_CUSTOM, label)
        )
        assertEquals(
            CommonDataKinds.Im.PROTOCOL_CUSTOM,
            typeIdOf("Boat", IM_TYPE_LABELS, CommonDataKinds.Im.PROTOCOL_CUSTOM, label)
        )
    }

    @Test
    fun tablesMatchEditFormOrder() {
        assertEquals(
            listOf(
                org.fossify.commons.R.string.home to CommonDataKinds.Email.TYPE_HOME,
                org.fossify.commons.R.string.work to CommonDataKinds.Email.TYPE_WORK,
                org.fossify.commons.R.string.mobile to CommonDataKinds.Email.TYPE_MOBILE,
                org.fossify.commons.R.string.other to CommonDataKinds.Email.TYPE_OTHER,
            ),
            EMAIL_TYPE_LABELS
        )
        assertEquals(
            listOf(
                org.fossify.commons.R.string.anniversary to CommonDataKinds.Event.TYPE_ANNIVERSARY,
                org.fossify.commons.R.string.birthday to CommonDataKinds.Event.TYPE_BIRTHDAY,
            ),
            EVENT_TYPE_LABELS
        )
        assertEquals(
            listOf(
                org.fossify.commons.R.string.home to CommonDataKinds.StructuredPostal.TYPE_HOME,
                org.fossify.commons.R.string.work to CommonDataKinds.StructuredPostal.TYPE_WORK,
                org.fossify.commons.R.string.other to CommonDataKinds.StructuredPostal.TYPE_OTHER,
            ),
            ADDRESS_TYPE_LABELS
        )
    }

    @Test
    fun phoneTableMatchesEditFormOrder() {
        assertEquals(
            listOf(
                CommonDataKinds.Phone.TYPE_MOBILE,
                CommonDataKinds.Phone.TYPE_HOME,
                CommonDataKinds.Phone.TYPE_WORK,
                CommonDataKinds.Phone.TYPE_MAIN,
                CommonDataKinds.Phone.TYPE_FAX_WORK,
                CommonDataKinds.Phone.TYPE_FAX_HOME,
                CommonDataKinds.Phone.TYPE_PAGER,
                CommonDataKinds.Phone.TYPE_OTHER,
            ),
            PHONE_TYPE_LABELS.map { it.second }
        )
        assertEquals(
            listOf(
                CommonDataKinds.Im.PROTOCOL_AIM,
                CommonDataKinds.Im.PROTOCOL_MSN,
                CommonDataKinds.Im.PROTOCOL_YAHOO,
                CommonDataKinds.Im.PROTOCOL_SKYPE,
                CommonDataKinds.Im.PROTOCOL_QQ,
                CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK,
                CommonDataKinds.Im.PROTOCOL_ICQ,
                CommonDataKinds.Im.PROTOCOL_JABBER,
            ),
            IM_TYPE_LABELS.map { it.second }
        )
    }

    @Test
    fun firstMatchWins() {
        assertEquals(10, typeIdOf("x", listOf(1 to 10, 2 to 20), -1) { "x" })
    }
}
