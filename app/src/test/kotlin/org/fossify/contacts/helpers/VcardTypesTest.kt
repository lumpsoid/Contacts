package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VcardTypesTest {
    @Test
    fun aMobileEmailTypeIsMobile() {
        assertEquals(Email.TYPE_MOBILE, emailTypeId("mobile"))
    }

    @Test
    fun anUnknownEmailTypeIsCustom() {
        assertEquals(Email.TYPE_CUSTOM, emailTypeId("x"))
    }

    @Test
    fun aWorkAddressTypeIsWorkWhateverTheCase() {
        assertEquals(StructuredPostal.TYPE_WORK, addressTypeId("Work"))
    }

    @Test
    fun flagTypesAreLeftOutOfTheKinds() {
        assertEquals(listOf("HOME"), meaningfulTypes(listOf("pref", "HOME", "pref=1", "voice")))
    }

    @Test
    fun onlyPrefOneMarksAPreferredNumber() {
        assertFalse(isPreferred(listOf("PREF=2"), null))
        assertTrue(isPreferred(emptyList(), 1))
        assertTrue(isPreferred(listOf("pref"), null))
    }

    @Test
    fun aCustomPhoneTypeWithNoLabelHasNoName() {
        assertEquals(emptyList<String>(), phoneTypeNames(Phone.TYPE_CUSTOM, ""))
        assertEquals(listOf("Gym"), phoneTypeNames(Phone.TYPE_CUSTOM, "Gym"))
    }

    @Test
    fun aBuiltInEmailTypeIsItsNameAndACustomOneIsItsLabel() {
        assertEquals("WORK", emailTypeName(Email.TYPE_WORK, ""))
        assertEquals("Gym", emailTypeName(Email.TYPE_CUSTOM, "Gym"))
    }

    @Test
    fun aBuiltInAddressTypeIsItsNameAndACustomOneIsItsLabel() {
        assertEquals("HOME", addressTypeName(StructuredPostal.TYPE_HOME, ""))
        assertEquals("Gym", addressTypeName(StructuredPostal.TYPE_CUSTOM, "Gym"))
    }
}
