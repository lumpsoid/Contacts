package org.fossify.contacts.models

/** A person whose numbers and emails are spread over several accounts and held whole by no writable record. */
data class SplitDetail(val person: ScannedPerson) {
    val key: String get() = person.key
}
