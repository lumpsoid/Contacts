package org.fossify.contacts.screens

/** What a screen's update returns: the new state and the effects the shell runs, in order. */
data class Next<S, E>(val state: S, val effects: List<E> = emptyList())
