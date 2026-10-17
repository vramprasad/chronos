package com.prasad.chronos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_time, container, false)
        val dbHelper = DatabaseHelper(requireContext())

        val btnIn = view.findViewById<Button>(R.id.btnIn)
        val btnOut = view.findViewById<Button>(R.id.btnOut)
        val btnFetchCurrentSession =
            view.findViewById<Button>(R.id.btnFetchCurrentSession) // Active Summary Button
        val btnClear = view.findViewById<Button>(R.id.btnClear)
        val tvDisplay = view.findViewById<TextView>(R.id.tvDisplay)

        // Note: btnFetch ("All Entries") is omitted completely here to remove its functionality

        fun saveLog(status: String) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentDateTime = dateFormat.format(Date())
            if (dbHelper.insertTimestamp(currentDateTime, status)) {
                Toast.makeText(context, "$status Logged: $currentDateTime", Toast.LENGTH_SHORT)
                    .show()
                // Auto refresh summary immediately upon a status entry
                btnFetchCurrentSession.performClick()
            }
        }

        btnIn.setOnClickListener { saveLog("IN") }
        btnOut.setOnClickListener { saveLog("OUT") }

        btnClear.setOnClickListener {
            val rows = dbHelper.clearAllTimestamps()
            tvDisplay.text = "Database entries cleared."
            Toast.makeText(context, "Deleted $rows entries", Toast.LENGTH_SHORT).show()
        }

        // Active Summary Click Listener
        btnFetchCurrentSession.setOnClickListener {
            val reportSummary = dbHelper.getActiveSummaryReport()
            tvDisplay.text = reportSummary
        }

        return view
    }
}