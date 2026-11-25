package org.fossify.contacts.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedCallersTest {
    private val own = "0123456789abcdef".repeat(4)
    private val other = "fedcba9876543210".repeat(4)

    @Test
    fun fossifyReleaseCertIsTrusted() {
        assertTrue(isTrustedCaller(listOf(FOSSIFY_RELEASE_CERT_SHA256), emptyList()))
        assertTrue(isTrustedCaller(listOf(FOSSIFY_RELEASE_CERT_SHA256.uppercase()), emptyList()))
    }

    @Test
    fun ownCertIsTrusted() {
        assertTrue(isTrustedCaller(listOf(own), listOf(own)))
        assertTrue(isTrustedCaller(listOf(own.uppercase()), listOf(own)))
    }

    @Test
    fun anyOtherCertIsRejected() {
        assertFalse(isTrustedCaller(listOf(other), listOf(own)))
    }

    @Test
    fun aCallerWithNoCertIsRejected() {
        assertFalse(isTrustedCaller(emptyList(), listOf(own)))
    }

    @Test
    fun oneTrustedCertAmongSeveralIsEnough() {
        assertTrue(isTrustedCaller(listOf(other, FOSSIFY_RELEASE_CERT_SHA256), emptyList()))
    }

    @Test
    fun sha256HexMatchesTheStandardVector() {
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(expected, sha256Hex("abc".toByteArray()))
    }
}
