package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.ConflictField
import org.fossify.contacts.models.DuplicateGroup
import org.fossify.contacts.models.MatchConfidence
import org.fossify.contacts.models.MergeChoices
import org.fossify.contacts.models.MergePlan
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.ScanResult
import org.fossify.contacts.models.ScannedPerson
import org.fossify.contacts.models.SplitDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupeSelectionTest {
    private fun person(id: Int): ScannedPerson {
        val contact = Contact(id = id, contactId = id, firstName = "Ann", source = "acc")
        return ScannedPerson("contact:$id", listOf(MergeRecord(contact, "com.google", AccountAccess.WRITABLE)))
    }

    private fun group(key: String, confidence: MatchConfidence, vararg people: ScannedPerson) =
        DuplicateGroup(key, confidence, people.toList())

    private val high = group("ann|high", MatchConfidence.HIGH, person(1), person(2), person(3))
    private val low = group("bob|low", MatchConfidence.LOW, person(4), person(5))
    private val split = SplitDetail(person(6))
    private val result = ScanResult(listOf(high, low), listOf(split))

    private fun keysOf(selection: DedupeSelection) = selection.plans().map { it.key }

    private fun summaryOf(plans: List<MergePlan>): List<Triple<String, List<String>, MergeChoices>> {
        return plans.map { Triple(it.key, it.records.map { record -> record.key }, it.choices) }
    }

    @Test
    fun defaultsSelectHighGroupsAndSplitDetailsOnly() {
        val selection = DedupeSelection(result)
        assertTrue(selection.isSelected(high.key))
        assertFalse(selection.isSelected(low.key))
        assertTrue(selection.isSelected(split.key))
        assertEquals(listOf(high.key, split.key), keysOf(selection))
    }

    @Test
    fun excludingOnePersonOfTwoDropsTheGroupUntilReincluded() {
        val selection = DedupeSelection(result)
        selection.setSelected(low.key, true)
        selection.setIncluded(low.key, "contact:5", false)
        assertEquals(listOf(high.key, split.key), keysOf(selection))
        selection.setIncluded(low.key, "contact:5", true)
        assertEquals(listOf(high.key, low.key, split.key), keysOf(selection))
    }

    @Test
    fun excludedPersonsRecordsLeaveTheMerge() {
        val selection = DedupeSelection(result)
        selection.setIncluded(high.key, "contact:2", false)
        val plan = selection.plans().first { it.key == high.key }
        assertEquals(listOf("acc:1", "acc:3"), plan.records.map { it.key })
    }

    @Test
    fun aPickLandsInThePlanChoices() {
        val selection = DedupeSelection(result)
        selection.setChoices(high.key, selection.choicesOf(high.key).pick(ConflictField.NAME, "acc:3"))
        val plan = selection.plans().first { it.key == high.key }
        assertEquals("acc:3", plan.choices.picks[ConflictField.NAME])
    }

    @Test
    fun savedStateRestoresTheSameSelection() {
        val selection = DedupeSelection(result)
        selection.setSelected(low.key, true)
        selection.setIncluded(high.key, "contact:2", false)
        val choices = selection.choicesOf(high.key).pick(ConflictField.NAME, "acc:3").withKeepBothNotes(true)
        selection.setChoices(high.key, choices)
        val restored = DedupeSelection(result)
        restored.restoreState(selection.saveState())
        val expected = summaryOf(selection.plans())
        assertEquals(listOf(high.key, low.key, split.key), expected.map { it.first })
        assertEquals(expected, summaryOf(restored.plans()))
    }

    private fun line(vararg parts: String) = parts.joinToString("\u001F")

    @Test
    fun aLineWithAnUnknownConflictFieldPicksNothing() {
        val selection = DedupeSelection(result)
        selection.restoreState(listOf(line("C", high.key, "NOT_A_FIELD", "acc:1")))
        assertEquals(MergeChoices(), selection.choicesOf(high.key))

        // Positive control: a known field is picked.
        selection.restoreState(listOf(line("C", high.key, ConflictField.NAME.name, "acc:1")))
        assertEquals("acc:1", selection.choicesOf(high.key).picks[ConflictField.NAME])
    }

    @Test
    fun aLineWithAnUnknownTagIsIgnored() {
        val selection = DedupeSelection(result)
        selection.restoreState(listOf(line("Z", high.key), line("Z", split.key, "acc:1")))
        assertTrue(selection.plans().isEmpty())

        // Positive control: a known tag selects the group.
        selection.restoreState(listOf(line("S", high.key)))
        assertEquals(listOf(high.key), keysOf(selection))
    }

    @Test
    fun aSinglePartLineIsIgnoredWithoutCrashing() {
        val selection = DedupeSelection(result)
        selection.restoreState(listOf("S", ""))
        assertTrue(selection.plans().isEmpty())

        // Positive control: the same tag with its key selects the group.
        selection.restoreState(listOf(line("S", high.key)))
        assertEquals(listOf(high.key), keysOf(selection))
    }

    @Test
    fun restoringReplacesTheSelectionItFollows() {
        val selection = DedupeSelection(result)
        selection.setIncluded(high.key, "contact:2", false)
        selection.setChoices(high.key, MergeChoices().pick(ConflictField.NAME, "acc:3"))
        selection.restoreState(listOf(line("S", low.key)))
        assertEquals(listOf(low.key), keysOf(selection))
        assertTrue(selection.isIncluded(high.key, "contact:2"))
        assertEquals(MergeChoices(), selection.choicesOf(high.key))
    }
}
