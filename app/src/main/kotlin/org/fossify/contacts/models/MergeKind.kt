package org.fossify.contacts.models

/**
 * DUPLICATES deletes the redundant writable copies; SPLIT_DETAILS only copies data in; COPY writes a record into
 * another account and leaves it there, unlinked.
 */
enum class MergeKind { DUPLICATES, SPLIT_DETAILS, COPY }
