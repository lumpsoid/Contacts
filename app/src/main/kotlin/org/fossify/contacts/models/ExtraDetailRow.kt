package org.fossify.contacts.models

/** The cursor values of one row of the [ExtraDetailsStore] query, as plain values. */
data class ExtraDetailRow(
    val rawId: Int,
    val mimetype: String?,
    val data1: String?,
    val data2: Int,
    val data3: String?,
    val phoneticGiven: String?,
    val phoneticMiddle: String?,
    val phoneticFamily: String?
)
