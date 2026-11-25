package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.Contact

/** The contacts whose source is one of [sources] (see Context.getVisibleContactSources). */
fun List<Contact>.inSources(sources: Collection<String>): ArrayList<Contact> =
    filterTo(ArrayList()) { sources.contains(it.source) }
