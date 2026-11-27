package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Group
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationGroupsTest {
    private val destination = ContactSource("A", "com.google", "A")

    @Test
    fun onlyGroupsOfTheDestinationAccountMatch() {
        // Group 2 is in another account and group 3 in another type, so only "Work" is kept.
        val rows = listOf(
            GroupRow(1, "Work", "A", "com.google"),
            GroupRow(2, "Home", "B", "com.google"),
            GroupRow(3, "Gym", "A", "other")
        )
        assertEquals(setOf("Work"), groupsByTitle(rows, destination).keys)
    }

    @Test
    fun theFirstGroupWithATitleWins() {
        val rows = listOf(
            GroupRow(1, "Work", "A", "com.google"),
            GroupRow(2, "Work", "A", "com.google")
        )
        assertEquals(1L, groupsByTitle(rows, destination)["Work"]?.id)
    }

    @Test
    fun existingGroupsAreReusedAndMissingOnesCreated() {
        val existing = mapOf("Work" to Group(1L, "Work"))
        val created = ArrayList<String>()
        val groups = pickGroups(existing, listOf("Work", "New", "Work")) { title ->
            created.add(title)
            Group(9L, title)
        }
        assertEquals(listOf(1L, 9L), groups?.map { it.id })
        assertEquals(listOf("New"), created)
    }

    @Test
    fun aFailedCreateFailsThePick() {
        assertNull(pickGroups(emptyMap(), listOf("New")) { null })
    }

    @Test
    fun noTitlesCreateNothing() {
        val groups = pickGroups(emptyMap(), emptyList()) { error("nothing should be created") }
        assertEquals(emptyList<Group>(), groups)
    }
}
