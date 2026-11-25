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
        private val SIM_SEGMENT = Regex("u?sim\\d*")

        /**
         * [accountType] null: an account this app could not identify, so it is never written.
         * [readOnlyAdapterTypes]: account types whose contacts sync adapter does not support uploading.
         */
        fun of(accountType: String?, readOnlyAdapterTypes: Set<String>) = when {
            accountType == null -> READ_ONLY
            accountType in MESSENGER_TYPES || accountType in readOnlyAdapterTypes -> READ_ONLY
            isSimType(accountType) -> SIM
            else -> WRITABLE
        }

        /** A SIM account type has a segment like sim, sim2 or usim: com.android.sim, vnd.sec.contact.sim2. */
        private fun isSimType(accountType: String) = accountType.lowercase().split('.').any { SIM_SEGMENT.matches(it) }
    }
}
