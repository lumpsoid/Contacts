package org.fossify.contacts.helpers

import org.fossify.contacts.models.DuplicateGroup
import org.fossify.contacts.models.MatchConfidence
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.ScanResult
import org.fossify.contacts.models.ScannedPerson
import org.fossify.contacts.models.SplitDetail
import java.text.Normalizer
import java.util.Locale

// Latin, Greek and Cyrillic accents only: kana voicing marks (U+3099, U+309A) change the name.
private val COMBINING_MARKS = Regex("[\\u0300-\\u036F]+")
private val WHITESPACE = Regex("\\s+")

/** Lower-case, accent-free, word-order-free form of a name: "Petrová Anna" -> "anna petrova". */
fun normalizeName(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
        .sorted()
        .joinToString(" ")

/** Records grouped into people as the system aggregates them, ordered by person key. */
fun peopleOf(records: List<MergeRecord>): List<ScannedPerson> =
    records.groupBy { ScannedPerson.keyOf(it.contact) }
        .map { (key, list) -> ScannedPerson(key, list) }
        .sortedBy { it.key }

/**
 * Duplicate groups and split-detail people among the records of [visibleSources]. People the system keeps
 * apart ([keptSeparate], device raw id pairs, smaller first) never share a group.
 */
fun scanDuplicates(
    records: List<MergeRecord>,
    keptSeparate: Set<Pair<Int, Int>>,
    visibleSources: Collection<String>
): ScanResult {
    val people = peopleOf(records.filter { visibleSources.contains(it.contact.source) })
    val duplicates = people
        .groupBy { normalizeName(it.displayName()) }
        .filterKeys { it.isNotEmpty() }
        .values
        .flatMap { clusterKeptApart(it, keptSeparate) }
        .filter { it.size >= 2 }
        .map { cluster -> DuplicateGroup(cluster.joinToString("|") { it.key }, confidenceOf(cluster), cluster) }
    val grouped = duplicates.flatMap { group -> group.people.map { it.key } }.toSet()
    val splitDetails = people.filter { it.key !in grouped && isSplitDetail(it) }.map { SplitDetail(it) }
    return ScanResult(duplicates, splitDetails)
}

/** First fit: each person joins the first cluster that holds no person the system keeps apart from it. */
private fun clusterKeptApart(
    people: List<ScannedPerson>,
    keptSeparate: Set<Pair<Int, Int>>
): List<List<ScannedPerson>> {
    val clusters = ArrayList<MutableList<ScannedPerson>>()
    people.forEach { person ->
        val fit = clusters.firstOrNull { cluster -> cluster.none { isKeptApart(it, person, keptSeparate) } }
        if (fit != null) fit.add(person) else clusters.add(mutableListOf(person))
    }
    return clusters
}

private fun isKeptApart(first: ScannedPerson, second: ScannedPerson, keptSeparate: Set<Pair<Int, Int>>) =
    first.deviceRawIds().any { a ->
        second.deviceRawIds().any { b -> keptSeparate.contains(keptSeparatePair(a, b)) }
    }

/** HIGH when two people of the group share a normalized phone number or email. */
private fun confidenceOf(people: List<ScannedPerson>): MatchConfidence {
    val sharedKey = people.flatMap { it.detailKeys() }.groupingBy { it }.eachCount().any { it.value >= 2 }
    return if (sharedKey) MatchConfidence.HIGH else MatchConfidence.LOW
}

/**
 * A person spread over 2+ accounts with a writable record and details, where no single writable record
 * holds all of those details.
 */
private fun isSplitDetail(person: ScannedPerson): Boolean {
    val keys = person.detailKeys()
    val accounts = person.records.map { Pair(it.contact.source, it.accountType) }.toSet()
    return accounts.size >= 2 && keys.isNotEmpty() && person.records.any { it.isWritable() } &&
        person.records.none { it.isWritable() && it.detailKeys().containsAll(keys) }
}
