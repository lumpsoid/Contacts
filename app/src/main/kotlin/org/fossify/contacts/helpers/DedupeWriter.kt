package org.fossify.contacts.helpers

import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.MergeFailure
import org.fossify.contacts.models.MergeOutcome
import org.fossify.contacts.models.MergePlan
import org.fossify.contacts.models.MergeRecord

/**
 * Merges one plan into the destination: write, verify, then delete the old copies and link the rest.
 * Owns that order; [store] does the reading and writing. Blocking; call off the main thread.
 */
class DedupeWriter(private val store: MergeStore) {
    /** Writes [plan] into [destination]; a failure leaves the group as far as it got. */
    fun merge(plan: MergePlan, destination: ContactSource, access: AccountAccess): MergeOutcome {
        val target = pickTarget(plan.records, destination)
        val base = target?.contact ?: Contact(id = 0, contactId = 0, source = destination.name)
        val merged = buildMergedContact(base, plan.records, plan.choices)
        val isPrivate = destination.type == SMT_PRIVATE
        val sendToVoicemail = !isPrivate && store.sendsToVoicemail(deviceRawIds(plan))
        val failure = when {
            !access.fits(merged) -> MergeFailure.SIM_LIMIT
            !placePhoto(merged, isPrivate) -> MergeFailure.WRITE_FAILED
            else -> writeAndClean(plan, merged, target, destination, isPrivate, sendToVoicemail)
        }
        return if (failure == null) MergeOutcome.Merged(plan.key) else MergeOutcome.Failed(plan.key, failure)
    }

    /** Writes [merged], then deletes the old copies and links the rest; null when all of it succeeded. */
    private fun writeAndClean(
        plan: MergePlan,
        merged: Contact,
        target: MergeRecord?,
        destination: ContactSource,
        isPrivate: Boolean,
        sendToVoicemail: Boolean
    ): MergeFailure? {
        merged.groups = store.groupsFor(destination, merged.groups.map { it.title })
        val resultId = writeVerified(merged, target, destination)
        return when {
            resultId == null -> MergeFailure.WRITE_FAILED
            !store.delete(recordsToDelete(plan, target).map { it.contact }) -> MergeFailure.DELETE_FAILED
            !link(resultId, isPrivate, rawIdsToLink(plan, target)) -> MergeFailure.LINK_FAILED
            else -> {
                if (sendToVoicemail) store.setSendToVoicemail(resultId)
                null
            }
        }
    }

    /** Gives [merged] a photo uri when it is a bitmap for a device account; false when saving failed. */
    private fun placePhoto(merged: Contact, isPrivate: Boolean): Boolean {
        val photo = merged.photo ?: return true
        if (!needsPhotoFile(merged.photoUri, hasBitmap = true, toPrivate = isPrivate)) return true
        val uri = store.savePhoto(photo) ?: return false
        merged.photoUri = uri
        merged.thumbnailUri = uri
        return true
    }

    /** Updates [target] or inserts [merged] new, and returns its id only when reading it back verifies. */
    private fun writeVerified(merged: Contact, target: MergeRecord?, destination: ContactSource): Int? {
        val id = if (target != null) {
            if (store.update(merged, target)) target.contact.id else null
        } else {
            store.insert(merged, destination)
        }
        if (id == null) return null
        val written = store.readBack(id, destination)
        if (written != null && verifyMerged(merged, written)) return id
        if (target == null) store.delete(listOf(merged.copy(id = id)))
        return null
    }

    /** Private contacts are not in the system provider, so they cannot be linked. */
    private fun link(resultId: Int, isPrivate: Boolean, rawIds: List<Int>): Boolean =
        isPrivate || rawIds.isEmpty() || store.link(listOf(resultId) + rawIds)

    /** Raw ids of the device records of [plan]. */
    private fun deviceRawIds(plan: MergePlan): Set<Int> =
        plan.records.filter { !it.contact.isPrivate() }.map { it.contact.id }.toSet()
}
