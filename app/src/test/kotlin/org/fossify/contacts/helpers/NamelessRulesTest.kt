package org.fossify.contacts.helpers

import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class NamelessRulesTest {
    companion object {
        private const val ID_SELF = 1
        private const val ID_SAME_NUMBER = 2
        private const val ID_OTHER_NUMBER = 3
        private const val NUMBER_A = "555"
        private const val NUMBER_B = "777"
    }

    private fun phoneOnly(id: Int, number: String) = Contact(
        id = id,
        contactId = id,
        source = "acc",
        phoneNumbers = arrayListOf(PhoneNumber(number, 2, "", number))
    )

    private fun ids(contacts: List<Contact>) = contacts.map { it.id }

    @Test
    fun listFollowsSettings() {
        val merged = namelessRules(
            getAll = false,
            ignoredContactSources = emptySet(),
            mergeDuplicates = true,
            showOnlyWithNumbers = true
        )
        assertEquals(NamelessRules(true, true), merged)

        val plain = namelessRules(
            getAll = false,
            ignoredContactSources = emptySet(),
            mergeDuplicates = false,
            showOnlyWithNumbers = false
        )
        assertEquals(NamelessRules(false, false), plain)
    }

    @Test
    fun getAllNeverMerges() {
        val rules = namelessRules(
            getAll = true,
            ignoredContactSources = emptySet(),
            mergeDuplicates = true,
            showOnlyWithNumbers = true
        )
        assertEquals(NamelessRules(mergeLinked = false, onlyWithNumbers = true), rules)
    }

    @Test
    fun ignoredSourcesDisableMergeAndNumberFilter() {
        val rules = namelessRules(
            getAll = true,
            ignoredContactSources = setOf("acc:type"),
            mergeDuplicates = true,
            showOnlyWithNumbers = true
        )
        assertEquals(NamelessRules(false, false), rules)
    }

    @Test
    fun namelessWithSameNumberIsDuplicate() {
        val contact = phoneOnly(ID_SELF, NUMBER_A)
        val nameless = listOf(
            phoneOnly(ID_SELF, NUMBER_A),
            phoneOnly(ID_SAME_NUMBER, NUMBER_A),
            phoneOnly(ID_OTHER_NUMBER, NUMBER_B)
        )
        val result = namelessDuplicatesOf(contact, nameless)
        assertEquals(listOf(ID_SAME_NUMBER), ids(result))
    }

    @Test
    fun namedContactHasNoNamelessDuplicates() {
        val contact = Contact(
            id = ID_SELF,
            contactId = ID_SELF,
            firstName = "Ann",
            source = "acc",
            phoneNumbers = arrayListOf(PhoneNumber(NUMBER_A, 2, "", NUMBER_A))
        )
        val nameless = listOf(phoneOnly(ID_SAME_NUMBER, NUMBER_A))
        val result = namelessDuplicatesOf(contact, nameless)
        assertEquals(emptyList<Int>(), ids(result))
    }

    @Test
    fun privateContactMatchesNamelessWithSameId() {
        val contact = Contact(
            id = ID_SELF,
            contactId = 0,
            source = "smt_private",
            phoneNumbers = arrayListOf(PhoneNumber(NUMBER_A, 2, "", NUMBER_A))
        )
        val nameless = listOf(phoneOnly(ID_SELF, NUMBER_A))
        val result = namelessDuplicatesOf(contact, nameless)
        assertEquals(listOf(ID_SELF), ids(result))
    }
}
