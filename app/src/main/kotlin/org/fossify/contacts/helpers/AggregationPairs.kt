package org.fossify.contacts.helpers

/** Links every raw contact id with the first one, so the first id leads the link. */
fun linkPairs(rawIds: List<Int>): List<Pair<Int, Int>> {
    val ids = rawIds.distinct()
    return ids.drop(1).map { Pair(ids.first(), it) }
}

/** Pairs every two raw contact ids, in list order, for keeping them apart. */
fun unlinkPairs(rawIds: List<Int>): List<Pair<Int, Int>> {
    val ids = rawIds.distinct()
    return ids.flatMapIndexed { index, first -> ids.drop(index + 1).map { Pair(first, it) } }
}

/** Keeps each raw contact id of [first] apart from each other raw contact id of [second]. */
fun keepApartPairs(first: List<Int>, second: List<Int>): List<Pair<Int, Int>> {
    return first.distinct().flatMap { a -> second.distinct().filter { it != a }.map { b -> Pair(a, b) } }
}
