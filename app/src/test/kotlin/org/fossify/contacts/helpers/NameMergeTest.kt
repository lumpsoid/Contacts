package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Group
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMergeTest {
    companion object {
        private const val ID_A = 10
        private const val ID_B = 11
        private const val ID_C = 12
        private const val ID_D = 13
        private const val CONTACT_A = 20
        private const val CONTACT_B = 30
        private const val CONTACT_C = 31
        private const val CONTACT_D = 32
        private const val LONG_MIMETYPE = "vnd.long.mimetype"
        private val VISIBLE = setOf("acc", "smt_private")
    }

    private fun device(id: Int, contactId: Int, name: String = "Ann", mimetype: String = "") =
        Contact(id = id, contactId = contactId, firstName = name, source = "acc", mimetype = mimetype)

    private fun private(id: Int, name: String = "Ann") =
        Contact(id = id, contactId = id, firstName = name, source = "smt_private")

    private fun ids(contacts: List<Contact>) = contacts.map { it.id }

    @Test
    fun sameNameContactsMergeWithoutPairs() {
        val result = mergeDuplicatesByName(
            contacts = listOf(device(ID_A, CONTACT_A), device(ID_B, CONTACT_B, "ann")),
            keptSeparate = emptySet()
        )
        assertEquals(1, result.size)
    }

    @Test
    fun keptSeparateSameNameContactsStayApart() {
        val result = mergeDuplicatesByName(
            contacts = listOf(device(ID_A, CONTACT_A), device(ID_B, CONTACT_B)),
            keptSeparate = setOf(keptSeparatePair(ID_A, ID_B))
        )
        assertEquals(listOf(ID_A, ID_B), ids(result).sorted())
    }

    @Test
    fun pairWithReversedOrderIsHonoured() {
        // The set holds (min, max) built by hand, while the devices are passed with the larger id first.
        val result = mergeDuplicatesByName(
            contacts = listOf(device(ID_B, CONTACT_B), device(ID_A, CONTACT_A)),
            keptSeparate = setOf(Pair(ID_A, ID_B))
        )
        assertEquals(listOf(ID_A, ID_B), ids(result).sorted())
    }

    @Test
    fun otherSameNameContactsStillMerge() {
        val result = mergeDuplicatesByName(
            contacts = listOf(
                device(ID_A, CONTACT_A),
                device(ID_B, CONTACT_B),
                device(ID_C, CONTACT_C),
                device(ID_D, CONTACT_D)
            ),
            keptSeparate = setOf(keptSeparatePair(ID_A, ID_B))
        )
        val resultIds = ids(result).sorted()
        assertEquals(3, resultIds.size)
        assertEquals(listOf(ID_A, ID_B), resultIds.take(2))
        assertTrue(resultIds.last() == ID_C || resultIds.last() == ID_D)
    }

    @Test
    fun pairAcrossDifferentNamesChangesNothing() {
        val result = mergeDuplicatesByName(
            contacts = listOf(
                device(ID_A, CONTACT_A, "Ann"),
                device(ID_B, CONTACT_B, "Bob"),
                device(ID_C, CONTACT_C, "Ann")
            ),
            keptSeparate = setOf(keptSeparatePair(ID_A, ID_B))
        )
        assertEquals(2, result.size)
        assertEquals(listOf(ID_A, ID_B), ids(result).sorted())
    }

    @Test
    fun privateContactIsNeverKeptSeparate() {
        val result = mergeDuplicatesByName(
            contacts = listOf(
                device(ID_A, CONTACT_A),
                private(ID_B),
                device(ID_C, CONTACT_C)
            ),
            keptSeparate = setOf(keptSeparatePair(ID_A, ID_B))
        )
        assertEquals(1, result.size)
    }

    @Test
    fun longestCompareStringIsKept() {
        val result = mergeDuplicatesByName(
            contacts = listOf(
                device(ID_A, CONTACT_A),
                device(ID_B, CONTACT_B, mimetype = LONG_MIMETYPE)
            ),
            keptSeparate = emptySet()
        )
        assertEquals(listOf(ID_B), ids(result))
    }

    @Test
    fun keptContactTakesGroupsOfItsSystemContact() {
        val withGroups = device(ID_A, CONTACT_A).apply {
            groups = arrayListOf(Group(1L, "Family"))
        }
        val result = mergeDuplicatesByName(
            contacts = listOf(withGroups, device(ID_B, CONTACT_A, mimetype = LONG_MIMETYPE)),
            keptSeparate = emptySet()
        )
        val kept = result.single()
        assertEquals(ID_B, kept.id)
        assertEquals(listOf("Family"), kept.groups.map { it.title })
    }

    @Test
    fun linkedRowsWithDifferentNamesCarryGroupsOnce() {
        val withGroups = device(ID_A, CONTACT_A, "Ann").apply {
            groups = arrayListOf(Group(1L, "Family"))
        }
        val result = mergeDuplicatesByName(
            contacts = listOf(withGroups, device(ID_B, CONTACT_A, "Bob")),
            keptSeparate = emptySet()
        )
        assertEquals(2, result.size)
        assertEquals(listOf(ID_A), ids(result.filter { it.groups.isNotEmpty() }))
        assertTrue(result.single { it.id == ID_B }.groups.isEmpty())
    }

    @Test
    fun withoutKeptSeparateDropsSeparatedDuplicates() {
        val a = device(ID_A, CONTACT_A)
        val b = device(ID_B, CONTACT_B)
        val c = device(ID_C, CONTACT_C)
        val d = device(ID_D, CONTACT_D)
        val withPair = withoutKeptSeparate(a, listOf(b, c, d), setOf(keptSeparatePair(ID_B, ID_C)), VISIBLE)
        assertEquals(listOf(ID_D), ids(withPair))

        val withoutPair = withoutKeptSeparate(a, listOf(b, c, d), emptySet(), VISIBLE)
        assertEquals(listOf(ID_B, ID_C, ID_D), ids(withoutPair))
    }

    @Test
    fun withoutKeptSeparateIsEmptyForSeparatedContact() {
        val a = device(ID_A, CONTACT_A)
        val b = device(ID_B, CONTACT_B)
        val c = device(ID_C, CONTACT_C)
        val result = withoutKeptSeparate(a, listOf(b, c), setOf(keptSeparatePair(ID_A, ID_B)), VISIBLE)
        assertEquals(emptyList<Int>(), ids(result))
    }

    @Test
    fun withoutKeptSeparateKeepsPrivateDuplicates() {
        val a = device(ID_A, CONTACT_A)
        val result = withoutKeptSeparate(
            a,
            listOf(private(ID_B), device(ID_C, CONTACT_C)),
            setOf(keptSeparatePair(ID_A, ID_B)),
            VISIBLE
        )
        assertEquals(listOf(ID_B, ID_C), ids(result))
    }

    @Test
    fun hiddenSourcePairDoesNotSplitVisibleDuplicates() {
        val a = device(ID_A, CONTACT_A)
        val b = device(ID_B, CONTACT_B)
        val hidden = device(ID_C, CONTACT_C).apply { source = "hidden" }
        val pairs = setOf(keptSeparatePair(ID_A, ID_C))
        val result = withoutKeptSeparate(a, listOf(b, hidden), pairs, setOf("acc"))
        assertEquals(listOf(ID_B), ids(result))

        // Positive control: with the hidden source visible, the pair isolates A and nothing is merged.
        val allVisible = withoutKeptSeparate(a, listOf(b, hidden), pairs, setOf("acc", "hidden"))
        assertEquals(emptyList<Int>(), ids(allVisible))
    }
}
