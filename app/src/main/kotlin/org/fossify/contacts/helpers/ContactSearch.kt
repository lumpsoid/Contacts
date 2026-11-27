package org.fossify.contacts.helpers

import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.helpers.getProperText
import org.fossify.commons.models.contacts.Contact

private val SEARCH_SPACES = "\\s+".toRegex()

/**
 * The search box text as contact search reads it: [text] trimmed, inner whitespace collapsed; [digits] is [text]
 * as a phone number, normalised by the shell since the platform normaliser can't run in a unit test.
 */
data class SearchQuery(val text: String, val digits: String) {
    // Text without diacritics matches names that have them (c finds č); text with them matches as typed.
    val normalize: Boolean get() = text.normalizeString() == text
}

/** [raw] as a [SearchQuery]; [normalizeNumber] is the platform's phone-number normaliser. */
fun searchQuery(raw: String, normalizeNumber: (String) -> String): SearchQuery {
    val text = raw.trim().replace(SEARCH_SPACES, " ")
    return SearchQuery(text, normalizeNumber(text))
}

/**
 * The contacts [query] finds, ignoring case, in any listed field; phone numbers only when the query is a number.
 * Contacts whose display name starts with or contains the query come first; otherwise the order is kept.
 */
fun List<Contact>.matching(query: SearchQuery): List<Contact> =
    filter { it.matchesNormalized(query) || it.matchesPlain(query) || it.matchesNumber(query) }
        .sortedBy { !it.nameLeads(query) }

private fun Contact.matchesNormalized(query: SearchQuery): Boolean =
    (listOf(getNameToDisplay(), nickname, notes, organization.company, organization.jobPosition) +
        addresses.map { it.value })
        .any { getProperText(it, query.normalize).contains(query.text, true) }

private fun Contact.matchesPlain(query: SearchQuery): Boolean =
    (emails.map { it.value } + IMs.map { it.value } + websites).any { it.contains(query.text, true) }

private fun Contact.matchesNumber(query: SearchQuery): Boolean =
    query.text.toLongOrNull() != null && query.digits.isNotEmpty() &&
        phoneNumbers.any { it.normalizedNumber.contains(query.digits, true) }

private fun Contact.nameLeads(query: SearchQuery): Boolean {
    val name = getNameToDisplay()
    return getProperText(name, query.normalize).startsWith(query.text, true) || name.contains(query.text, true)
}
