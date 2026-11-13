
package com.ats.sentinel

import android.content.ContentResolver
import android.provider.CallLog
import android.provider.ContactsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ATSContact(
    val name: String,
    val phoneNumber: String
)

data class ATSCallLogEntry(
    val name: String,
    val phoneNumber: String,
    val callType: String,
    val date: String,
    val duration: String
)

object ATSDeviceDataReader {

    fun readContacts(
        resolver: ContentResolver
    ): List<ATSContact> {

        val result = mutableListOf<ATSContact>()

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
            )?.use { cursor ->

                val nameIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )

                val numberIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                if (nameIndex >= 0 && numberIndex >= 0) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex).orEmpty()
                        val number = cursor.getString(numberIndex).orEmpty()

                        if (number.isNotBlank()) {
                            result.add(
                                ATSContact(
                                    name = name.ifBlank { "Unknown contact" },
                                    phoneNumber = number
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        return result.distinctBy { it.phoneNumber }
    }

    fun readCallHistory(
        resolver: ContentResolver
    ): List<ATSCallLogEntry> {

        val result = mutableListOf<ATSCallLogEntry>()

        try {
            val projection = arrayOf(
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

                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

                while (cursor.moveToNext() && result.size < 200) {

                    val name = if (nameIndex >= 0)
                        cursor.getString(nameIndex).orEmpty()
                    else ""

                    val number = if (numberIndex >= 0)
                        cursor.getString(numberIndex).orEmpty()
                    else ""

                    val type = if (typeIndex >= 0)
                        cursor.getInt(typeIndex)
                    else 0

                    val timestamp = if (dateIndex >= 0)
                        cursor.getLong(dateIndex)
                    else 0L

                    val seconds = if (durationIndex >= 0)
                        cursor.getLong(durationIndex)
                    else 0L

                    val callType = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                        CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
                        else -> "OTHER"
                    }

                    val formattedDate = if (timestamp > 0) {
                        SimpleDateFormat(
                            "yyyy-MM-dd HH:mm",
                            Locale.getDefault()
                        ).format(Date(timestamp))
                    } else {
                        "Unknown date"
                    }

                    val duration = String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        seconds / 60,
                        seconds % 60
                    )

                    result.add(
                        ATSCallLogEntry(
                            name = name.ifBlank { "Unknown caller" },
                            phoneNumber = number,
                            callType = callType,
                            date = formattedDate,
                            duration = duration
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
