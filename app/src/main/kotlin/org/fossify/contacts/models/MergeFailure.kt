package org.fossify.contacts.models

/**
 * Why a group was not merged. WRITE_FAILED leaves the group untouched; later ones happen after the write.
 * ERROR is an unexpected error at any point of the merge.
 */
enum class MergeFailure { SIM_LIMIT, WRITE_FAILED, DELETE_FAILED, LINK_FAILED, ERROR }
