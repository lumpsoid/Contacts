package org.fossify.contacts.helpers

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.OperationApplicationException
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.showErrorToast
import org.fossify.contacts.models.ExtraDetailRow
import org.fossify.contacts.models.ExtraDetails
import org.fossify.contacts.models.LabeledValue
import org.fossify.contacts.models.PhoneticName

/**
 * Reads and adds the [ExtraDetails] of device raw contacts: SIP address and relation rows and the phonetic parts
 * of the structured name row, none of which commons reads or writes. Blocking; call off the main thread.
 */
class ExtraDetailsStore(private val context: Context) {
    /** The details of every device raw contact that has any, by raw id; null when they could not be read. */
    fun readAll(): Map<Int, ExtraDetails>? = query(null)

    /**
     * Adds to raw contact [rawId] the parts of [extras] it lacks, then reads it again; whether it holds all of
     * [extras].
     */
    fun add(rawId: Int, extras: ExtraDetails): Boolean {
        val before = query(rawId) ?: return false
        val missing = extras.missingFrom(before[rawId] ?: ExtraDetails())
        if (missing.isEmpty()) return true
        if (!write(rawId, missing)) return false
        val after = query(rawId) ?: return false
        return extras.isHeldBy(after[rawId] ?: ExtraDetails())
    }

    /** Details of the device raw contacts [rawId] selects, all of them when it is null; null when unreadable. */
    private fun query(rawId: Int?): Map<Int, ExtraDetails>? {
        val projection = arrayOf(
            Data.RAW_CONTACT_ID,
            Data.MIMETYPE,
            Data.DATA1,
            Data.DATA2,
            Data.DATA3,
            StructuredName.PHONETIC_GIVEN_NAME,
            StructuredName.PHONETIC_MIDDLE_NAME,
            StructuredName.PHONETIC_FAMILY_NAME
        )
        val rawSelection = if (rawId == null) "" else " AND ${Data.RAW_CONTACT_ID} = ?"
        val selection = "${Data.MIMETYPE} IN (?, ?, ?)" + rawSelection
        val selectionArgs = listOf(
            SipAddress.CONTENT_ITEM_TYPE,
            Relation.CONTENT_ITEM_TYPE,
            StructuredName.CONTENT_ITEM_TYPE
        ) + listOfNotNull(rawId?.toString())
        return try {
            context.contentResolver.query(Data.CONTENT_URI, projection, selection, selectionArgs.toTypedArray(), null)
                ?.use { extraDetailsOf(rowsOf(it)) }
        } catch (e: SecurityException) {
            context.showErrorToast(e)
            null
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
            null
        } catch (e: IllegalStateException) {
            context.showErrorToast(e)
            null
        } catch (e: SQLiteException) {
            context.showErrorToast(e)
            null
        }
    }

    /** Every row of [cursor] as an [ExtraDetailRow]; the phonetic columns are null on rows that have none. */
    private fun rowsOf(cursor: Cursor): List<ExtraDetailRow> {
        val rows = ArrayList<ExtraDetailRow>()
        while (cursor.moveToNext()) {
            rows.add(
                ExtraDetailRow(
                    rawId = cursor.getIntValue(Data.RAW_CONTACT_ID),
                    mimetype = cursor.getStringValue(Data.MIMETYPE),
                    data1 = cursor.getStringValue(Data.DATA1),
                    data2 = cursor.getIntValue(Data.DATA2),
                    data3 = cursor.getStringValue(Data.DATA3),
                    phoneticGiven = cursor.getStringValue(StructuredName.PHONETIC_GIVEN_NAME),
                    phoneticMiddle = cursor.getStringValue(StructuredName.PHONETIC_MIDDLE_NAME),
                    phoneticFamily = cursor.getStringValue(StructuredName.PHONETIC_FAMILY_NAME)
                )
            )
        }
        return rows
    }

    /** Writes [missing] to raw contact [rawId]; false when the provider refused. */
    private fun write(rawId: Int, missing: ExtraDetails): Boolean {
        return try {
            val operations = ArrayList(
                missing.sipAddresses.map { insertOperation(rawId, SipAddress.CONTENT_ITEM_TYPE, it) } +
                    missing.relations.map { insertOperation(rawId, Relation.CONTENT_ITEM_TYPE, it) }
            )
            if (operations.isNotEmpty()) {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            }
            missing.phoneticName.isEmpty() || writePhonetic(rawId, missing.phoneticName)
        } catch (e: OperationApplicationException) {
            context.showErrorToast(e)
            false
        } catch (e: RemoteException) {
            context.showErrorToast(e)
            false
        } catch (e: SecurityException) {
            context.showErrorToast(e)
            false
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
            false
        } catch (e: IllegalStateException) {
            context.showErrorToast(e)
            false
        }
    }

    private fun insertOperation(rawId: Int, mimetype: String, value: LabeledValue): ContentProviderOperation =
        ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValue(Data.RAW_CONTACT_ID, rawId)
            .withValue(Data.MIMETYPE, mimetype)
            .withValue(Data.DATA1, value.value)
            .withValue(Data.DATA2, value.type)
            .withValue(Data.DATA3, value.label)
            .build()

    /** Sets the phonetic parts of the structured name row of [rawId], inserting that row when it has none. */
    private fun writePhonetic(rawId: Int, name: PhoneticName): Boolean {
        val values = ContentValues().apply {
            put(StructuredName.PHONETIC_GIVEN_NAME, name.given)
            put(StructuredName.PHONETIC_MIDDLE_NAME, name.middle)
            put(StructuredName.PHONETIC_FAMILY_NAME, name.family)
        }
        val updated = context.contentResolver.update(
            Data.CONTENT_URI,
            values,
            "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
            arrayOf(rawId.toString(), StructuredName.CONTENT_ITEM_TYPE)
        )
        if (updated > 0) return true
        values.put(Data.RAW_CONTACT_ID, rawId)
        values.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
        return context.contentResolver.insert(Data.CONTENT_URI, values) != null
    }
}

/** The [ExtraDetails] of each raw contact in [rows] that has any, by raw id. */
fun extraDetailsOf(rows: List<ExtraDetailRow>): Map<Int, ExtraDetails> {
    val sip = HashMap<Int, ArrayList<LabeledValue>>()
    val relations = HashMap<Int, ArrayList<LabeledValue>>()
    val phonetic = HashMap<Int, PhoneticName>()
    for (row in rows) {
        when (row.mimetype) {
            StructuredName.CONTENT_ITEM_TYPE -> {
                if (phonetic[row.rawId]?.isEmpty() ?: true) phonetic[row.rawId] = phoneticOf(row)
            }
            SipAddress.CONTENT_ITEM_TYPE -> addLabeled(sip, row)
            Relation.CONTENT_ITEM_TYPE -> addLabeled(relations, row)
        }
    }
    return (sip.keys + relations.keys + phonetic.keys)
        .associateWith { rawId ->
            ExtraDetails(sip[rawId].orEmpty(), relations[rawId].orEmpty(), phonetic[rawId] ?: PhoneticName())
        }
        .filterValues { !it.isEmpty() }
}

private fun phoneticOf(row: ExtraDetailRow) = PhoneticName(
    row.phoneticGiven ?: "",
    row.phoneticMiddle ?: "",
    row.phoneticFamily ?: ""
)

/** Appends the SIP address or relation of [row] to [lists] under its raw id, when it has a value. */
private fun addLabeled(lists: HashMap<Int, ArrayList<LabeledValue>>, row: ExtraDetailRow) {
    val value = labeledValue(row) ?: return
    lists.getOrPut(row.rawId) { ArrayList() }.add(value)
}

/** The SIP address or relation of [row]; null when its value is null or blank. */
private fun labeledValue(row: ExtraDetailRow): LabeledValue? {
    val value = row.data1
    if (value.isNullOrBlank()) return null
    return LabeledValue(value, row.data2, row.data3 ?: "")
}
