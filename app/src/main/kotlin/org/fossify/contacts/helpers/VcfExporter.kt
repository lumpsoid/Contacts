package org.fossify.contacts.helpers

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.helpers.VcfExporter.ExportResult.EXPORT_FAIL
import java.io.OutputStream

class VcfExporter {
    enum class ExportResult {
        EXPORT_FAIL, EXPORT_OK, EXPORT_PARTIAL
    }

    fun exportContacts(
        context: Context,
        outputStream: OutputStream?,
        contacts: ArrayList<Contact>,
        showExportingToast: Boolean,
        version: VCardVersion = VCardVersion.V4_0,
        callback: (result: ExportResult) -> Unit,
    ) {
        var written = false
        var counts = RunCounts(0, 0)
        try {
            if (outputStream == null) {
                callback(EXPORT_FAIL)
                return
            }

            if (showExportingToast) {
                context.toast(org.fossify.commons.R.string.exporting)
            }

            val cards = ArrayList<VCard>()
            counts = runEach(
                contacts,
                onFailure = { e -> Log.w("VcfExporter", "skipped a contact", e) }
            ) { contact ->
                cards.add(contact.toVCard(readPhoto(context, contact)))
                true
            }

            Ezvcard.write(cards).version(version).go(outputStream)
            outputStream.flush()
            written = true
        } catch (e: Exception) {
            context.showErrorToast(e)
        }

        callback(exportResult(counts.done, counts.failed, written))
    }

    /** The photo file's bytes, or null when the contact has no photo or it cannot be read. */
    private fun readPhoto(context: Context, contact: Contact): ByteArray? {
        if (contact.photoUri.isEmpty()) return null
        try {
            return context.contentResolver.openInputStream(contact.photoUri.toUri())?.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

/** The result of an export that built [exported] cards, [failed] of them not, and [written] them out in full. */
fun exportResult(exported: Int, failed: Int, written: Boolean): VcfExporter.ExportResult = when {
    !written || exported == 0 -> EXPORT_FAIL
    failed > 0 -> VcfExporter.ExportResult.EXPORT_PARTIAL
    else -> VcfExporter.ExportResult.EXPORT_OK
}
