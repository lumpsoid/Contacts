package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.Contact

/** An action the contact list's selection bar can offer. */
enum class CabAction {
    EDIT, REMOVE, ADD_TO_FAVORITES, ADD_TO_GROUP, SEND_SMS, SEND_EMAIL, DELETE, CREATE_SHORTCUT, LINK,
    MOVE_TO_ACCOUNT, COPY_TO_ACCOUNT
}

private val actionsByLocation = mapOf(
    LOCATION_CONTACTS_TAB to setOf(
        CabAction.ADD_TO_FAVORITES, CabAction.ADD_TO_GROUP, CabAction.SEND_SMS, CabAction.SEND_EMAIL,
        CabAction.DELETE, CabAction.MOVE_TO_ACCOUNT, CabAction.COPY_TO_ACCOUNT
    ),
    LOCATION_FAVORITES_TAB to setOf(
        CabAction.REMOVE, CabAction.ADD_TO_GROUP, CabAction.SEND_SMS, CabAction.SEND_EMAIL,
        CabAction.MOVE_TO_ACCOUNT, CabAction.COPY_TO_ACCOUNT
    ),
    LOCATION_GROUP_CONTACTS to setOf(
        CabAction.REMOVE, CabAction.ADD_TO_FAVORITES, CabAction.SEND_SMS, CabAction.SEND_EMAIL, CabAction.DELETE
    )
)

/** The actions the selection bar shows for [selectedCount] contacts at [location]; [oreoPlus] is Android 8+. */
fun cabActions(location: Int, selectedCount: Int, oreoPlus: Boolean): Set<CabAction> {
    val onTab = location == LOCATION_CONTACTS_TAB || location == LOCATION_FAVORITES_TAB
    val one = selectedCount == 1
    return buildSet {
        addAll(actionsByLocation[location].orEmpty())
        if (one) add(CabAction.EDIT)
        if (one && oreoPlus && onTab) add(CabAction.CREATE_SHORTCUT)
        if (selectedCount > 1 && onTab) add(CabAction.LINK)
    }
}

/** How linking the selected contacts runs. */
enum class LinkMode {
    /** Fewer than two selected: nothing happens. */
    NONE,

    /** No private contact: link the raw contacts in the provider. */
    RAW,

    /** All private: merge them, after the user confirms. */
    PRIVATE,

    /** Private mixed with device contacts: refused. */
    REFUSE
}

/** How linking [selected] runs: fewer than two does nothing, private contacts mixed with device ones are refused. */
fun linkMode(selected: List<Contact>): LinkMode {
    val privateCount = selected.count { it.isPrivate() }
    return when {
        selected.size < 2 -> LinkMode.NONE
        privateCount == 0 -> LinkMode.RAW
        privateCount == selected.size -> LinkMode.PRIVATE
        else -> LinkMode.REFUSE
    }
}

/**
 * What deleting [selected] removes: each selected contact, plus, when [mergeDuplicates] is on, its same-name
 * duplicates in [allContacts] that are not kept apart. Nameless contacts are never in [allContacts], so the
 * selected ones are always included. A private and a device contact with the same id are both kept.
 */
fun deleteTargets(
    selected: List<Contact>,
    allContacts: List<Contact>,
    mergeDuplicates: Boolean,
    keptSeparate: Set<Pair<Int, Int>>,
    visibleSources: Collection<String>
): List<Contact> {
    val matches = selected.flatMap { contact ->
        val sameName = allContacts.filter {
            (it.id == contact.id || (mergeDuplicates && canHaveDuplicates(contact))) &&
                it.getHashToCompare() == contact.getHashToCompare()
        }
        withoutKeptSeparate(contact, sameName, keptSeparate, visibleSources)
    }
    return (selected + matches).distinctBy { "${it.isPrivate()}:${it.id}" }
}
