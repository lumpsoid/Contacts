package org.fossify.contacts.models

import org.fossify.commons.helpers.SIGNAL_PACKAGE
import org.fossify.commons.helpers.TELEGRAM_PACKAGE
import org.fossify.commons.helpers.THREEMA_PACKAGE
import org.fossify.commons.helpers.WHATSAPP_PACKAGE
import org.fossify.commons.models.contacts.Contact

/** Whether this app may write contacts into an account, decided from the account type. */
enum class AccountAccess {
    WRITABLE, SIM, READ_ONLY;

    fun isWritable() = this != READ_ONLY

    /** A SIM holds one number per contact. */
    fun fits(contact: Contact) = this != SIM || contact.phoneNumbers.size <= 1

    companion object {
        private val MESSENGER_TYPES = setOf(SIGNAL_PACKAGE, TELEGRAM_PACKAGE, WHATSAPP_PACKAGE, THREEMA_PACKAGE)

        /** [readOnlyAdapterTypes]: account types whose contacts sync adapter does not support uploading. */
        fun of(accountType: String, readOnlyAdapterTypes: Set<String>) = when {
            accountType in MESSENGER_TYPES || accountType in readOnlyAdapterTypes -> READ_ONLY
            accountType.contains(".sim") -> SIM
            else -> WRITABLE
        }
    }
}
