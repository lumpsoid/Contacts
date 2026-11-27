package org.fossify.contacts.models

/** One group row of the device, as read from the cursor. */
data class GroupRow(val id: Long, val title: String?, val accountName: String?, val accountType: String?)
