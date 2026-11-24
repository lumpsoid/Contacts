package org.fossify.contacts.helpers

import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.models.AccountAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountAccessTest {
    private fun contactWithNumbers(count: Int) = Contact(
        id = 1,
        contactId = 1,
        phoneNumbers = ArrayList((1..count).map { PhoneNumber("$it", 0, "", "$it") })
    )

    @Test
    fun messengerAndReadOnlyAdapterAccountsAreReadOnly() {
        assertEquals(AccountAccess.READ_ONLY, AccountAccess.of("com.whatsapp", emptySet()))
        assertEquals(AccountAccess.READ_ONLY, AccountAccess.of("com.example.chat", setOf("com.example.chat")))
    }

    @Test
    fun simAndPlainAccountsAreClassifiedByType() {
        assertEquals(AccountAccess.SIM, AccountAccess.of("com.android.sim", emptySet()))
        assertEquals(AccountAccess.WRITABLE, AccountAccess.of("com.google", emptySet()))
        assertEquals(AccountAccess.WRITABLE, AccountAccess.of("", emptySet()))
    }

    @Test
    fun simHoldsOneNumberOnly() {
        assertTrue(AccountAccess.SIM.fits(contactWithNumbers(1)))
        assertFalse(AccountAccess.SIM.fits(contactWithNumbers(2)))
        assertTrue(AccountAccess.WRITABLE.fits(contactWithNumbers(2)))
    }
}
