package org.fossify.contacts.models

/**
 * One row of the contacts data table as RawContactLoader reads it. The generic columns keep the
 * getter their readers use: [data2Int] and [data5Int] are the integer reads of DATA2 and DATA5.
 */
data class DataRow(
    val rawId: Int,
    val contactId: Int,
    val mimetype: String?,
    val isPrimary: Int,
    val data1: String?,
    val data2Int: Int,
    val data3: String?,
    val data4: String?,
    val data5: String?,
    val data5Int: Int,
    val data6: String?,
    val data7: String?,
    val data8: String?,
    val data9: String?,
    val data10: String?,
    val accountName: String?,
    val starred: Int,
    val photoUri: String?,
    val photoThumbnailUri: String?,
    val customRingtone: String?
)
