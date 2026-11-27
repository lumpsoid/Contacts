package org.fossify.contacts.helpers

import java.security.MessageDigest

/** SHA-256 of the certificate Fossify signs its released apps with (F-Droid and Google Play). */
const val FOSSIFY_RELEASE_CERT_SHA256 = "affdb124d3f4720c2f98dbca9eacba0514fba4306e20a2786c861c3c0d6ff292"

/**
 * Whether an app signed with [callerCertDigests] may read this app's private contacts: one of its
 * certificates is Fossify's release certificate or one of [ownCertDigests]. Digests are SHA-256 hex.
 */
fun isTrustedCaller(callerCertDigests: Collection<String>, ownCertDigests: Collection<String>): Boolean {
    val trusted = (ownCertDigests + FOSSIFY_RELEASE_CERT_SHA256).map { it.lowercase() }.toSet()
    return callerCertDigests.any { it.lowercase() in trusted }
}

/** Whether a caller may read private contacts; digests are only computed when needed. */
fun canReadPrivateContacts(
    showPrivateContacts: Boolean,
    callingUid: Int,
    myUid: Int,
    callerCertDigests: () -> Collection<String>,
    ownCertDigests: () -> Collection<String>,
): Boolean = showPrivateContacts &&
    (callingUid == myUid || isTrustedCaller(callerCertDigests(), ownCertDigests()))

/** Lower-case hex SHA-256 of [bytes], the form [isTrustedCaller] compares. */
fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
