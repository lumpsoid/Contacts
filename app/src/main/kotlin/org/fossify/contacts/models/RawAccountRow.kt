package org.fossify.contacts.models

/** A raw contact row read from the provider: its id and the account it belongs to. */
data class RawAccountRow(val id: Int, val accountName: String, val accountType: String)
