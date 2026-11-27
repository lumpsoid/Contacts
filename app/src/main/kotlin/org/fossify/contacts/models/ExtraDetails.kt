package org.fossify.contacts.models

/** A SIP address or a relation: the value, its ContactsContract type and its custom label. */
data class LabeledValue(val value: String, val type: Int, val label: String) {
    /** Two values with the same key are the same detail. */
    val key: String get() = "$type:${value.trim().lowercase()}"
}

/** The phonetic name: StructuredName PHONETIC_GIVEN_NAME, PHONETIC_MIDDLE_NAME and PHONETIC_FAMILY_NAME. */
data class PhoneticName(val given: String = "", val middle: String = "", val family: String = "") {
    fun isEmpty() = given.isBlank() && middle.isBlank() && family.isBlank()

    /** Whether every non-blank part of this equals that of [stored], trimmed and ignoring case. */
    fun isHeldBy(stored: PhoneticName): Boolean =
        listOf(given to stored.given, middle to stored.middle, family to stored.family).all { (own, held) ->
            own.isBlank() || own.trim().equals(held.trim(), ignoreCase = true)
        }
}

/**
 * Details commons' Contact has no field for, so commons neither reads nor writes them: SIP addresses,
 * relations and the phonetic name. A merge carries them so deleting the old copies does not lose them.
 */
data class ExtraDetails(
    val sipAddresses: List<LabeledValue> = emptyList(),
    val relations: List<LabeledValue> = emptyList(),
    val phoneticName: PhoneticName = PhoneticName()
) {
    fun isEmpty() = sipAddresses.isEmpty() && relations.isEmpty() && phoneticName.isEmpty()

    /** Whether [stored] holds every detail of this; it may hold more. */
    fun isHeldBy(stored: ExtraDetails) = missingFrom(stored).isEmpty()

    /** The details of this that [stored] lacks; the phonetic name whole when a part of it differs. */
    fun missingFrom(stored: ExtraDetails): ExtraDetails {
        val sipKeys = stored.sipAddresses.map { it.key }.toSet()
        val relationKeys = stored.relations.map { it.key }.toSet()
        return ExtraDetails(
            sipAddresses.filter { it.key !in sipKeys },
            relations.filter { it.key !in relationKeys },
            if (phoneticName.isHeldBy(stored.phoneticName)) PhoneticName() else phoneticName
        )
    }
}
