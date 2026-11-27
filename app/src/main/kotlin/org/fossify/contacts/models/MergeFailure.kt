package org.fossify.contacts.models

/**
 * Why a group was not merged. WRITE_FAILED leaves the group untouched; later ones happen after the write.
 * NO_ROOM: a record the merge would delete has details (a SIP address, relation or phonetic name) the destination
 * cannot keep; nothing is written.
 * ERROR is an unexpected error at any point of the merge.
 */
enum class MergeFailure { SIM_LIMIT, NO_ROOM, WRITE_FAILED, DELETE_FAILED, LINK_FAILED, ERROR }
