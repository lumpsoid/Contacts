package org.fossify.contacts.models

/** Why a group was not merged. WRITE_FAILED leaves the group untouched; later ones happen after the write. */
enum class MergeFailure { SIM_LIMIT, WRITE_FAILED, DELETE_FAILED, LINK_FAILED }
