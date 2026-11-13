package com.ats.sentinel

import android.content.ContentResolver
import android.provider.CallLog
import android.provider.ContactsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ATSContact(
    val contactId: Long,
    val phoneId: Long,
    val name: String,
    val phoneNumber: String
)

data class ATSCallLogEntry(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val callType: String,
    val date: String,
    val duration: String,
    val timestamp: Long
)

object ATSDeviceDataReader {

    private const val MAX_CALL_HISTORY = 200

    fun readContacts(
        resolver: ContentResolver
    ): List<ATSContact> {

        val result = mutableListOf<ATSContact>()

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME +
                    " COLLATE NOCASE ASC"
            )?.use { cursor ->

                val phoneIdIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone._ID
                )

                val contactIdIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                )

                val nameIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )

                val numberIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                if (
                    phoneIdIndex < 0 ||
                    contactIdIndex < 0 ||
                    nameIndex < 0 ||
                    numberIndex < 0
                ) {
                    return emptyList()
                }

                while (cursor.moveToNext()) {

                    val phoneId = cursor.getLong(phoneIdIndex)
                    val contactId = cursor.getLong(contactIdIndex)

                    val name = cursor.getString(nameIndex).orEmpty()
                    val number = cursor.getString(numberIndex).orEmpty()

                    if (number.isNotBlank()) {
                        result.add(
                            ATSContact(
                                contactId = contactId,
                                phoneId = phoneId,
                                name = name.ifBlank {
                                    "Unknown contact"
                                },
                                phoneNumber = number
                            )
                        )
                    }
                }
            }

        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        return result.distinctBy {
            "${it.contactId}_${it.phoneNumber}"
        }
    }

    fun readCallHistory(
        resolver: ContentResolver
    ): List<ATSCallLogEntry> {

        val result = mutableListOf<ATSCallLogEntry>()

        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )

            resolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )?.use { cursor ->

                val idIndex = cursor.getColumnIndex(
                    CallLog.Calls._ID
                )

                val nameIndex = cursor.getColumnIndex(
                    CallLog.Calls.CACHED_NAME
                )

                val numberIndex = cursor.getColumnIndex(
                    CallLog.Calls.NUMBER
                )

                val typeIndex = cursor.getColumnIndex(
                    CallLog.Calls.TYPE
                )

                val dateIndex = cursor.getColumnIndex(
                    CallLog.Calls.DATE
                )

                val durationIndex = cursor.getColumnIndex(
                    CallLog.Calls.DURATION
                )

                if (
                    idIndex < 0 ||
                    numberIndex < 0 ||
                    typeIndex < 0 ||
                    dateIndex < 0 ||
                    durationIndex < 0
                ) {
                    return emptyList()
                }

                while (
                    cursor.moveToNext() &&
                    result.size < MAX_CALL_HISTORY
                ) {

                    val id = cursor.getLong(idIndex)

                    val name = if (nameIndex >= 0) {
                        cursor.getString(nameIndex).orEmpty()
                    } else {
                        ""
                    }

                    val number = cursor.getString(numberIndex).orEmpty()

                    val type = cursor.getInt(typeIndex)

                    val timestamp = cursor.getLong(dateIndex)

                    val seconds = cursor.getLong(durationIndex)

                    val callType = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                        CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
                        else -> "OTHER"
                    }

                    val formattedDate = if (timestamp > 0L) {
                        SimpleDateFormat(
                            "yyyy-MM-dd HH:mm",
                            Locale.getDefault()
                        ).format(Date(timestamp))
                    } else {
                        "Unknown date"
                    }

                    val formattedDuration = String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        seconds / 60,
                        seconds % 60
                    )

                    result.add(
                        ATSCallLogEntry(
                            id = id,
                            name = name.ifBlank {
                                "Unknown caller"
                            },
                            phoneNumber = number,
                            callType = callType,
                            date = formattedDate,
                            duration = formattedDuration,
                            timestamp = timestamp
                        )
                    )
                }
            }

        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        return result
    }
}
