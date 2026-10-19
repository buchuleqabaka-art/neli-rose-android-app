package com.example.neli_roseapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class ScheduleFragment : Fragment() {

    private val events = mutableListOf<DayEvent>()

    private lateinit var tvProgressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var containerEvents: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        events.clear()
        events.addAll(DataManager.getScheduleEvents(ctx))

        tvProgressText = view.findViewById(R.id.tvScheduleProgressText)
        progressBar = view.findViewById(R.id.progressSchedule)
        containerEvents = view.findViewById(R.id.containerScheduleEvents)

        val etTitle = view.findViewById<EditText>(R.id.etEventTitle)
        val etTime = view.findViewById<EditText>(R.id.etEventTime)
        val etNote = view.findViewById<EditText>(R.id.etEventNote)
        val btnLog = view.findViewById<Button>(R.id.btnLogSchedule)

        renderEvents()

        btnLog.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val time = etTime.text.toString().trim()
            val note = etNote.text.toString().trim()

            when {
                title.isBlank() ->
                    Toast.makeText(ctx, "Enter an event name", Toast.LENGTH_SHORT).show()
                !Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(time) ->
                    Toast.makeText(ctx, "Time must look like 09:00", Toast.LENGTH_SHORT).show()
                else -> {
                    val padded = if (time.length == 4) "0$time" else time
                    events.add(DayEvent(padded, title, note))
                    events.sortBy { it.time }
                    DataManager.saveScheduleEvents(ctx, events)
                    etTitle.text.clear()
                    etNote.text.clear()
                    etTime.setText("09:00")
                    renderEvents()
                }
            }
        }
    }

    private fun renderEvents() {
        val ctx = requireContext()
        containerEvents.removeAllViews()

        val doneCount = events.count { it.done }
        tvProgressText.text = "$doneCount of ${events.size} done"
        val pct = if (events.isEmpty()) 0 else (doneCount * 100 / events.size)
        progressBar.progress = pct

        events.forEachIndexed { index, event ->
            val row = LayoutInflater.from(ctx).inflate(android.R.layout.simple_list_item_2, containerEvents, false)
            val text1 = row.findViewById<TextView>(android.R.id.text1)
            val text2 = row.findViewById<TextView>(android.R.id.text2)

            text1.text = "${event.time}  -  ${event.title}"
            text1.textSize = 15f
            text2.text = if (event.done) "Done" else if (event.note.isNotBlank()) event.note else "Pending"
            text2.textSize = 13f

            row.setBackgroundColor(
                if (event.done) ContextCompat.getColor(ctx, R.color.neli_light_green)
                else ContextCompat.getColor(ctx, R.color.neli_cream)
            )

            row.setOnClickListener {
                events[index] = event.copy(done = !event.done)
                DataManager.saveScheduleEvents(ctx, events)
                renderEvents()
            }

            containerEvents.addView(row)
        }
    }
}
