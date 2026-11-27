package org.fossify.contacts.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraDetailsTest {
    @Test
    fun anEmptyDetailIsHeldByAnything() {
        assertTrue(ExtraDetails().isEmpty())
        assertTrue(ExtraDetails(phoneticName = PhoneticName(" ", "", "")).isEmpty())
        assertTrue(ExtraDetails().isHeldBy(ExtraDetails()))
    }

    @Test
    fun missingFromKeepsOnlyWhatStoredLacks() {
        val own = ExtraDetails(
            sipAddresses = listOf(LabeledValue("ann@sip.example", 1, "")),
            relations = listOf(LabeledValue("Bob", 1, ""), LabeledValue("Eve", 2, ""))
        )
        val stored = ExtraDetails(
            sipAddresses = listOf(LabeledValue(" ANN@sip.example", 1, "")),
            relations = listOf(LabeledValue("bob", 1, ""), LabeledValue("Eve", 3, ""))
        )
        assertEquals(
            ExtraDetails(relations = listOf(LabeledValue("Eve", 2, ""))),
            own.missingFrom(stored)
        )
    }

    @Test
    fun aPhoneticNameIsMissingWholeWhenAPartDiffers() {
        val own = ExtraDetails(phoneticName = PhoneticName("Ana", "", "Petrova"))
        val same = ExtraDetails(phoneticName = PhoneticName("ana ", "X", "petrova"))
        assertTrue(own.isHeldBy(same))
        assertTrue(own.missingFrom(same).phoneticName.isEmpty())

        val differs = ExtraDetails(phoneticName = PhoneticName("Ana", "", "Ivanova"))
        assertEquals(own.phoneticName, own.missingFrom(differs).phoneticName)
    }

    @Test
    fun isHeldByFailsOnAMissingSipAddress() {
        val sip = LabeledValue("ann@sip.example", 1, "")
        val own = ExtraDetails(sipAddresses = listOf(sip))
        assertFalse(own.isHeldBy(ExtraDetails()))
        val other = LabeledValue("bob@sip.example", 1, "")
        assertTrue(own.isHeldBy(ExtraDetails(sipAddresses = listOf(sip, other))))
    }

    @Test
    fun relationsWithDifferentLabelsAreDifferentDetails() {
        val own = ExtraDetails(relations = listOf(LabeledValue("Bob", 0, "Boss")))
        val otherLabel = ExtraDetails(relations = listOf(LabeledValue("bob", 0, "Mentor")))
        assertFalse(own.isHeldBy(otherLabel))
        val sameLabel = ExtraDetails(relations = listOf(LabeledValue(" Bob", 0, "boss")))
        assertTrue(own.isHeldBy(sameLabel))
    }
}
