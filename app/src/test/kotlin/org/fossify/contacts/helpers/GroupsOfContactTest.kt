package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.Group
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class GroupsOfContactTest {
    private val work = Group(1L, "Work")
    private val home = Group(2L, "Home")
    private val draft = Group(null, "Draft")
    private val stored = listOf(work, home, draft)

    @Test
    fun aContactGetsTheGroupsItIsAMemberOf() {
        val titles = groupsOfContact(setOf(2L), stored).map { it.title }
        assertEquals(listOf("Home"), titles)
    }

    @Test
    fun groupsWithoutAnIdNeverMatch() {
        val titles = groupsOfContact(setOf(1L, 2L), stored).map { it.title }
        assertEquals(listOf("Work", "Home"), titles)
    }

    @Test
    fun noMembershipsGiveNoGroups() {
        assertEquals(emptyList<Group>(), groupsOfContact(emptySet(), stored))
    }

    @Test
    fun copiesAreReturned() {
        val result = groupsOfContact(setOf(1L), stored)
        assertNotSame(work, result.first())
    }
}
