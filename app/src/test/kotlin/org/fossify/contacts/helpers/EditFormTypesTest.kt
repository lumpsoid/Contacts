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
