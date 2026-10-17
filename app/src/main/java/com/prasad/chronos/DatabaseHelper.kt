package com.prasad.chronos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "TimestampDB"
        private const val DATABASE_VERSION = 5

        private const val TABLE_TIMESTAMPS = "timestamps"
        private const val COL_ID = "id"
        private const val COL_DATETIME = "date_time"
        private const val COL_STATUS = "status"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTimeTable = ("CREATE TABLE $TABLE_TIMESTAMPS ("
                + "$COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COL_DATETIME TEXT, "
                + "$COL_STATUS TEXT)")

        db?.execSQL(createTimeTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_TIMESTAMPS")
        onCreate(db)
    }

    fun insertTimestamp(dateTimeStr: String, status: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_DATETIME, dateTimeStr)
            put(COL_STATUS, status)
        }
        val result = db.insert(TABLE_TIMESTAMPS, null, values)
        db.close()
        return result != -1L
    }

    fun getAllTimestamps(): List<String> {
        val list = mutableListOf<String>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TIMESTAMPS ORDER BY $COL_ID DESC", null)
        if (cursor.moveToFirst()) {
            do {
                val dateTime = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATETIME))
                val status = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS))
                list.add("[$status] $dateTime")
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun clearAllTimestamps(): Int {
        val db = this.writableDatabase
        val rows = db.delete(TABLE_TIMESTAMPS, null, null)
        db.close()
        return rows
    }

    /**
     * Generates a structural Active Summary breakdown strictly fulfilling all 7 requested parameters.
     */
    fun getActiveSummaryReport(): String {
        val db = this.readableDatabase
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val fullSdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentDateStr = sdf.format(Date())

        // Get All IN OUT timestamps for current day sorted chronologically
        val query = "SELECT $COL_DATETIME, $COL_STATUS FROM $TABLE_TIMESTAMPS " +
                "WHERE $COL_DATETIME LIKE '$currentDateStr%' " +
                "ORDER BY $COL_ID ASC"

        val cursor = db.rawQuery(query, null)
        val logs = mutableListOf<Pair<Date, String>>()

        if (cursor.moveToFirst()) {
            do {
                val dateTimeStr = cursor.getString(0)
                val status = cursor.getString(1)
                val parsedDate = try {
                    fullSdf.parse(dateTimeStr)
                } catch (e: Exception) {
                    null
                }
                if (parsedDate != null) {
                    logs.add(Pair(parsedDate, status))
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()

        if (logs.isEmpty()) {
            return "No entries found for today ($currentDateStr)."
        }

        val firstInLog = logs.firstOrNull { it.second == "IN" }
        if (firstInLog == null) {
            return "Error: No 'IN' log recorded today to establish a baseline."
        }

        val firstInTime = firstInLog.first
        val lastOutLog = logs.lastOrNull { it.second == "OUT" }
        val currentTime = Date()

        // Calculate Expected OUT Time: First IN Time + 4 hours and 1 minute
        val expectedOutMillis = firstInTime.time + (4 * 60 * 60 * 1000L) + (1 * 60 * 1000L)
        val expectedOutTime = Date(expectedOutMillis)

        // Helper to cleanly print out durations
        fun formatDuration(millis: Long): String {
            if (millis < 0) return "0s"
            val seconds = (millis / 1000) % 60
            val minutes = (millis / (1000 * 60)) % 60
            val hours = (millis / (1000 * 60 * 60)) % 24
            val days = millis / (1000 * 60 * 60 * 24)
            return buildString {
                if (days > 0) append("${days}d ")
                if (hours > 0 || days > 0) append("${hours}h ")
                if (minutes > 0 || hours > 0 || days > 0) append("${minutes}m ")
                append("${seconds}s")
            }
        }

        // --- Break Calculations ---
        val breakDetailsBuilder = StringBuilder()
        var totalBreakMillis = 0L
        var lastOutTimeForBreak: Date? = null
        var breakCounter = 1

        for (i in logs.indices) {
            val (logDate, status) = logs[i]
            if (status == "OUT") {
                lastOutTimeForBreak = logDate
            } else if (status == "IN" && lastOutTimeForBreak != null) {
                if (logDate.after(lastOutTimeForBreak)) {
                    val breakGap = logDate.time - lastOutTimeForBreak.time
                    totalBreakMillis += breakGap
                    breakDetailsBuilder.append("  • Break $breakCounter: ${breakGap / 1000 / 60} mins\n")
                    breakCounter++
                }
                lastOutTimeForBreak = null
            }
        }

        // Include any ongoing/active break up to the current time if last status is OUT
        var activeBreakMillis = 0L
        if (lastOutTimeForBreak != null && currentTime.after(lastOutTimeForBreak)) {
            activeBreakMillis = currentTime.time - lastOutTimeForBreak.time
            breakDetailsBuilder.append("  • Break $breakCounter (Ongoing): ${activeBreakMillis / 1000 / 60} mins\n")
        }

        val totalBreakDurationStr = formatDuration(totalBreakMillis + activeBreakMillis)
        val breakDetails =
            if (breakDetailsBuilder.isEmpty()) "  No breaks recorded yet.\n" else breakDetailsBuilder.toString()

        // --- Dynamic Calculations 1 to 4 ---
        val lastOutMinusFirstInNoBreaks = if (lastOutLog != null) {
            formatDuration(lastOutLog.first.time - firstInTime.time)
        } else "N/A (No OUT log)"

        val lastOutMinusFirstInWithBreaks = if (lastOutLog != null) {
            formatDuration((lastOutLog.first.time - firstInTime.time) - totalBreakMillis)
        } else "N/A (No OUT log)"

        val currentTimeMinusFirstInNoBreaks = formatDuration(currentTime.time - firstInTime.time)
        val currentTimeMinusFirstInWithBreaks =
            formatDuration((currentTime.time - firstInTime.time) - (totalBreakMillis + activeBreakMillis))

        // --- Build string with exact structural metrics ---
        return buildString {
            append("=== ACTIVE SUMMARY ===\n")
            append("Today's Date : $currentDateStr\n")
            append("Current Time : ${timeSdf.format(currentTime)}\n")
            append("Frst IN Time : ${timeSdf.format(firstInTime)}\n")
            append("Exp OUT Time : ${timeSdf.format(expectedOutTime)}\n\n")
            append("OT-IB : $lastOutMinusFirstInNoBreaks\n")
            append("OT-EB : $lastOutMinusFirstInWithBreaks\n")
            append("CT-IB : $currentTimeMinusFirstInNoBreaks\n")
            append("CT-EB : $currentTimeMinusFirstInWithBreaks\n\n")

            append("Breaks\n")
            append(breakDetails)
            append("\n")
            append("Total Break Duration: $totalBreakDurationStr\n\n")

            append("All IN/OUT for Today:\n")
            logs.forEach { (date, status) ->
                val padding = if (status == "IN") " " else ""
                append("  [${status}]$padding ${timeSdf.format(date)}\n")
            }
        }
    }
}