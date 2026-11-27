package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import org.fossify.contacts.R

/** Label string resource to type id, in the order the edit form matched them. */
typealias TypeLabels = List<Pair<Int, Int>>

/** The type id whose label is [text], or [fallback] (the custom type, or other for events). */
fun typeIdOf(text: String, labels: TypeLabels, fallback: Int, label: (Int) -> String): Int =
    labels.firstOrNull { label(it.first) == text }?.second ?: fallback

/** Phone type labels, matched in this order. */
val PHONE_TYPE_LABELS: TypeLabels = listOf(
    org.fossify.commons.R.string.mobile to CommonDataKinds.Phone.TYPE_MOBILE,
    org.fossify.commons.R.string.home to CommonDataKinds.Phone.TYPE_HOME,
    org.fossify.commons.R.string.work to CommonDataKinds.Phone.TYPE_WORK,
    org.fossify.commons.R.string.main_number to CommonDataKinds.Phone.TYPE_MAIN,
    org.fossify.commons.R.string.work_fax to CommonDataKinds.Phone.TYPE_FAX_WORK,
    org.fossify.commons.R.string.home_fax to CommonDataKinds.Phone.TYPE_FAX_HOME,
    org.fossify.commons.R.string.pager to CommonDataKinds.Phone.TYPE_PAGER,
    org.fossify.commons.R.string.other to CommonDataKinds.Phone.TYPE_OTHER,
)

/** Email type labels, matched in this order. */
val EMAIL_TYPE_LABELS: TypeLabels = listOf(
    org.fossify.commons.R.string.home to CommonDataKinds.Email.TYPE_HOME,
    org.fossify.commons.R.string.work to CommonDataKinds.Email.TYPE_WORK,
    org.fossify.commons.R.string.mobile to CommonDataKinds.Email.TYPE_MOBILE,
    org.fossify.commons.R.string.other to CommonDataKinds.Email.TYPE_OTHER,
)

/** Event type labels, matched in this order. */
val EVENT_TYPE_LABELS: TypeLabels = listOf(
    org.fossify.commons.R.string.anniversary to CommonDataKinds.Event.TYPE_ANNIVERSARY,
    org.fossify.commons.R.string.birthday to CommonDataKinds.Event.TYPE_BIRTHDAY,
)

/** Address type labels, matched in this order. */
val ADDRESS_TYPE_LABELS: TypeLabels = listOf(
    org.fossify.commons.R.string.home to CommonDataKinds.StructuredPostal.TYPE_HOME,
    org.fossify.commons.R.string.work to CommonDataKinds.StructuredPostal.TYPE_WORK,
    org.fossify.commons.R.string.other to CommonDataKinds.StructuredPostal.TYPE_OTHER,
)

/** IM protocol labels, matched in this order. */
val IM_TYPE_LABELS: TypeLabels = listOf(
    R.string.aim to CommonDataKinds.Im.PROTOCOL_AIM,
    R.string.windows_live to CommonDataKinds.Im.PROTOCOL_MSN,
    R.string.yahoo to CommonDataKinds.Im.PROTOCOL_YAHOO,
    R.string.skype to CommonDataKinds.Im.PROTOCOL_SKYPE,
    R.string.qq to CommonDataKinds.Im.PROTOCOL_QQ,
    R.string.hangouts to CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK,
    R.string.icq to CommonDataKinds.Im.PROTOCOL_ICQ,
    R.string.jabber to CommonDataKinds.Im.PROTOCOL_JABBER,
)
