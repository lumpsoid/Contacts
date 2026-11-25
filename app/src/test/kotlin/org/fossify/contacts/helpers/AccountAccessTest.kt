package org.fossify.contacts.helpers

import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.contacts.models.AccountAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun everyCommonsMessengerTypeIsReadOnly() {
        listOf("org.thoughtcrime.securesms", "org.telegram.messenger", "ch.threema.app", "com.whatsapp")
            .forEach { assertEquals(it, AccountAccess.READ_ONLY, AccountAccess.of(it, emptySet())) }
    }

    @Test
    fun simAndPlainAccountsAreClassifiedByType() {
        assertEquals(AccountAccess.SIM, AccountAccess.of("com.android.sim", emptySet()))
        assertEquals(AccountAccess.WRITABLE, AccountAccess.of("com.google", emptySet()))
        assertEquals(AccountAccess.WRITABLE, AccountAccess.of("", emptySet()))
    }

    @Test
    fun anUnknownAccountIsReadOnly() {
        assertEquals(AccountAccess.READ_ONLY, AccountAccess.of(null, emptySet()))
    }

    @Test
    fun simIsMatchedByASegmentOnly() {
        listOf("vnd.sec.contact.sim", "vnd.sec.contact.sim2", "com.example.usim")
            .forEach { assertEquals(it, AccountAccess.SIM, AccountAccess.of(it, emptySet())) }
        listOf("org.simplemobiletools.contacts", "com.simple.sync")
            .forEach { assertEquals(it, AccountAccess.WRITABLE, AccountAccess.of(it, emptySet())) }
    }

    @Test
    fun accountTypeIsResolvedByNameAndFailsClosed() {
        val google = ContactSource("me@x.com", "com.google", "me")
        val signal = ContactSource("me@x.com", "org.thoughtcrime.securesms", "me")
        val phone = ContactSource("Phone", "", "Phone")
        assertEquals("com.google", accountTypeOf("me@x.com", listOf(google)))
        assertNull(accountTypeOf("me@x.com", listOf(google, signal)))
        assertNull(accountTypeOf("other", listOf(google)))
        assertEquals("", accountTypeOf("Phone", listOf(phone)))
    }

    @Test
    fun simHoldsOneNumberOnly() {
        assertTrue(AccountAccess.SIM.fits(contactWithNumbers(1)))
        assertFalse(AccountAccess.SIM.fits(contactWithNumbers(2)))
        assertTrue(AccountAccess.WRITABLE.fits(contactWithNumbers(2)))
    }
}
