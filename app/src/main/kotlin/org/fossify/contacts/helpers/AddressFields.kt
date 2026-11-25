package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.Address

/**
 * The text for the street field of the structured address editor. An address that has only a formatted value
 * (from another app or a sync) shows that value there, so saving does not drop it.
 */
fun Address.streetForStructuredEdit(): String {
    val parts = listOf(street, neighborhood, city, postcode, pobox, region, country)
    return if (parts.all { it.isBlank() } && value.isNotBlank()) value else street
}
