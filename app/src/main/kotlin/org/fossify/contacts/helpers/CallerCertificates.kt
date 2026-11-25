package org.fossify.contacts.helpers

import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build

/** SHA-256 hex digests of the signing certificates of every package running as [uid]; empty when unknown. */
fun PackageManager.certDigestsOfUid(uid: Int): List<String> =
    getPackagesForUid(uid).orEmpty().flatMap { certDigestsOf(it) }

/**
 * SHA-256 hex digests of [packageName]'s signing certificates; empty when they can't be read,
 * so callers fail closed.
 */
fun PackageManager.certDigestsOf(packageName: String): List<String> =
    runCatching { signaturesOf(packageName).map { sha256Hex(it.toByteArray()) } }.getOrDefault(emptyList())

@Suppress("DEPRECATION")
private fun PackageManager.signaturesOf(packageName: String): List<Signature> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
        when {
            info == null -> emptyList()
            info.hasMultipleSigners() -> info.apkContentsSigners.orEmpty().toList()
            else -> info.signingCertificateHistory.orEmpty().toList()
        }
    } else {
        getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty().toList()
    }
