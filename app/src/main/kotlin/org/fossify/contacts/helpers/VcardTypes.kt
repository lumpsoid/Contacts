package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import java.util.Locale

private val IGNORED_TYPES = setOf(PREF, "VOICE", "INTERNET")
private const val PREF_PREFIX = "PREF="

/** The TYPE values that name a kind (home, work, ...), leaving out the ones that only flag something. */
internal fun meaningfulTypes(values: List<String>): List<String> = values.filterNot { value ->
    val upper = value.uppercase(Locale.ROOT)
    upper in IGNORED_TYPES || upper.startsWith(PREF_PREFIX)
}

/** Whether the card marks this number or address as preferred: PREF=1, a PREF type, or PREF=1 as a type. */
internal fun isPreferred(values: List<String>, pref: Int?): Boolean {
    if (pref == 1) return true
    return values.any { value ->
        val upper = value.uppercase(Locale.ROOT)
        upper == PREF ||
            (upper.startsWith(PREF_PREFIX) && upper.removePrefix(PREF_PREFIX).toIntOrNull() == 1)
    }
}

internal fun phoneTypeId(type: String, subtype: String?): Int =
    when (type.uppercase(Locale.ROOT)) {
        CELL -> Phone.TYPE_MOBILE
        HOME -> {
            if (subtype?.uppercase(Locale.ROOT) == FAX) {
                Phone.TYPE_FAX_HOME
            } else {
                Phone.TYPE_HOME
            }
        }

        WORK -> {
            if (subtype?.uppercase(Locale.ROOT) == FAX) {
                Phone.TYPE_FAX_WORK
            } else {
                Phone.TYPE_WORK
            }
        }

        MAIN -> Phone.TYPE_MAIN
        WORK_FAX -> Phone.TYPE_FAX_WORK
        HOME_FAX -> Phone.TYPE_FAX_HOME
        FAX -> if (subtype?.uppercase(Locale.ROOT) == HOME) Phone.TYPE_FAX_HOME else Phone.TYPE_FAX_WORK
        PAGER -> Phone.TYPE_PAGER
        OTHER -> Phone.TYPE_OTHER
        else -> Phone.TYPE_CUSTOM
    }

internal fun emailTypeId(type: String): Int = when (type.uppercase(Locale.ROOT)) {
    HOME -> Email.TYPE_HOME
    WORK -> Email.TYPE_WORK
    MOBILE -> Email.TYPE_MOBILE
    OTHER -> Email.TYPE_OTHER
    else -> Email.TYPE_CUSTOM
}

internal fun addressTypeId(type: String): Int = when (type.uppercase(Locale.ROOT)) {
    HOME -> StructuredPostal.TYPE_HOME
    WORK -> StructuredPostal.TYPE_WORK
    OTHER -> StructuredPostal.TYPE_OTHER
    else -> StructuredPostal.TYPE_CUSTOM
}

/** The TYPE names a phone number is written with: a fax is its kind plus FAX, an unnamed kind is its label. */
internal fun phoneTypeNames(type: Int, label: String): List<String> = when (type) {
    Phone.TYPE_MOBILE -> listOf(CELL)
    Phone.TYPE_HOME -> listOf(HOME)
    Phone.TYPE_WORK -> listOf(WORK)
    Phone.TYPE_MAIN -> listOf(MAIN)
    Phone.TYPE_FAX_WORK -> listOf(WORK, FAX)
    Phone.TYPE_FAX_HOME -> listOf(HOME, FAX)
    Phone.TYPE_PAGER -> listOf(PAGER)
    Phone.TYPE_OTHER -> listOf(OTHER)
    else -> listOfNotNull(label.takeIf { it.isNotBlank() })
}

internal fun emailTypeName(type: Int, label: String): String = when (type) {
    Email.TYPE_HOME -> HOME
    Email.TYPE_WORK -> WORK
    Email.TYPE_MOBILE -> MOBILE
    Email.TYPE_OTHER -> OTHER
    else -> label
}

internal fun addressTypeName(type: Int, label: String): String = when (type) {
    StructuredPostal.TYPE_HOME -> HOME
    StructuredPostal.TYPE_WORK -> WORK
    StructuredPostal.TYPE_OTHER -> OTHER
    else -> label
}
