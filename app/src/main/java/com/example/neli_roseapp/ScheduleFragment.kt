package com.example.neli_roseapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.neli_roseapp.db.ScheduleEventEntity

class ScheduleFragment : Fragment() {

    // the day being shown; ticks are saved for this day only
    private var selectedDate = CareDates.today()

    // day and time chosen for the event being added
    private var newEventDate = selectedDate
    private var newEventTime = "09:00"

    private lateinit var tvScheduleDate: TextView
    private lateinit var tvProgressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvScheduleEmpty: TextView
    private lateinit var containerEvents: LinearLayout
    private lateinit var btnEventDate: Button

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

        tvScheduleDate = view.findViewById(R.id.tvScheduleDate)
        tvProgressText = view.findViewById(R.id.tvScheduleProgressText)
        progressBar = view.findViewById(R.id.progressSchedule)
        tvScheduleEmpty = view.findViewById(R.id.tvScheduleEmpty)
        containerEvents = view.findViewById(R.id.containerScheduleEvents)
        btnEventDate = view.findViewById(R.id.btnEventDate)

        val etTitle = view.findViewById<EditText>(R.id.etEventTitle)
        val etNote = view.findViewById<EditText>(R.id.etEventNote)
        val btnEventTime = view.findViewById<Button>(R.id.btnEventTime)
        val cbRepeat = view.findViewById<CheckBox>(R.id.cbEventRepeat)
        val btnAddEvent = view.findViewById<Button>(R.id.btnAddEvent)

        view.findViewById<Button>(R.id.btnPrevDay).setOnClickListener { showDay(CareDates.plusDays(selectedDate, -1)) }
        view.findViewById<Button>(R.id.btnNextDay).setOnClickListener { showDay(CareDates.plusDays(selectedDate, 1)) }
        view.findViewById<Button>(R.id.btnPickDay).setOnClickListener { ctx.pickDate(selectedDate) { showDay(it) } }

        btnEventDate.setOnClickListener {
            ctx.pickDate(newEventDate) { picked ->
                newEventDate = picked
                btnEventDate.text = "Day: ${dayLabel(picked)}"
            }
        }
        btnEventTime.text = "Time: $newEventTime"
        btnEventTime.setOnClickListener {
            ctx.pickTime(newEventTime) { picked ->
                newEventTime = picked
                btnEventTime.text = "Time: $picked"
            }
        }

        btnAddEvent.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val note = etNote.text.toString().trim()
            if (title.isEmpty()) {
                etTitle.error = "Enter what the event is"
                return@setOnClickListener
            }
            val date = newEventDate
            launchUi {
                if (DataManager.addScheduleEvent(ctx, title, note, newEventTime, date, cbRepeat.isChecked)) {
                    etTitle.text.clear()
                    etNote.text.clear()
                    cbRepeat.isChecked = false
                    Toast.makeText(ctx, "Event added", Toast.LENGTH_SHORT).show()
                    showDay(date)
                } else {
                    Toast.makeText(ctx, "Could not save. Please log in again.", Toast.LENGTH_LONG).show()
                }
            }
        }

        showDay(selectedDate)
    }

    private fun dayLabel(date: String): String =
        if (date == CareDates.today()) "today, ${CareDates.display(date)}" else CareDates.display(date)

    /** Shows the events of one day. A new event is added to the same day unless another is picked. */
    private fun showDay(date: String) {
        selectedDate = date
        newEventDate = date
        tvScheduleDate.text = if (date == CareDates.today()) "Today\n${CareDates.display(date)}" else CareDates.display(date)
        btnEventDate.text = "Day: ${dayLabel(date)}"
        launchUi { renderEvents() }
    }

    private suspend fun renderEvents() {
        val ctx = requireContext()
        val date = selectedDate
        val items = DataManager.getScheduleEvents(ctx, date)
        containerEvents.removeAllViews()

        val doneCount = items.count { it.done }
        tvProgressText.text = if (items.isEmpty()) "Nothing planned" else "$doneCount of ${items.size} done"
        progressBar.progress = if (items.isEmpty()) 0 else (doneCount * 100 / items.size)
        tvScheduleEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        for (item in items) {
            val event = item.event
            val row = layoutInflater.inflate(R.layout.item_schedule_event, containerEvents, false)
            row.findViewById<TextView>(R.id.tvEventTitle).text = "${event.time}  ${event.title}"

            val details = listOfNotNull(
                event.note.takeIf { it.isNotBlank() },
                if (event.repeatDaily) "Every day" else null
            ).joinToString(" - ")
            val tvDetail = row.findViewById<TextView>(R.id.tvEventDetail)
            tvDetail.text = details
            tvDetail.visibility = if (details.isEmpty()) View.GONE else View.VISIBLE

            row.setBackgroundColor(
                if (item.done) ContextCompat.getColor(ctx, R.color.neli_light_green)
                else ContextCompat.getColor(ctx, R.color.neli_cream)
            )

            val btnToggle = row.findViewById<Button>(R.id.btnEventToggle)
            btnToggle.text = if (item.done) "Done (tap to undo)" else "Not done yet (tap when done)"
            btnToggle.setBackgroundColor(
                ContextCompat.getColor(ctx, if (item.done) android.R.color.white else R.color.neli_light_green)
            )
            btnToggle.setOnClickListener {
                launchUi {
                    DataManager.setEventDone(ctx, event.id, date, !item.done)
                    renderEvents()
                }
            }

            row.findViewById<Button>(R.id.btnEventEdit).setOnClickListener { showEditDialog(event) }
            row.findViewById<Button>(R.id.btnEventDelete).setOnClickListener {
                val keepsHistory = CareRules.removalPlan(event.repeatDaily, event.startDate, CareDates.today()) ==
                    RemovalPlan.STOP_FROM_TODAY
                ctx.confirm(
                    "Remove ${event.title}?",
                    if (keepsHistory) "It will stop from today. Earlier days keep their record."
                    else "This event will be deleted.",
                    "Remove"
                ) {
                    launchUi {
                        DataManager.removeScheduleEvent(ctx, event)
                        renderEvents()
                    }
                }
            }

            containerEvents.addView(row)
        }
    }

    private fun showEditDialog(event: ScheduleEventEntity) {
        val ctx = requireContext()
        val form = layoutInflater.inflate(R.layout.dialog_event, null)
        val etTitle = form.findViewById<EditText>(R.id.etDialogEventTitle)
        val etNote = form.findViewById<EditText>(R.id.etDialogEventNote)
        val tvRepeat = form.findViewById<TextView>(R.id.tvDialogEventRepeat)
        val btnDate = form.findViewById<Button>(R.id.btnDialogEventDate)
        val btnTime = form.findViewById<Button>(R.id.btnDialogEventTime)

        var time = CareTimes.minutesOfDay(event.time)?.let { CareTimes.format(it / 60, it % 60) } ?: "09:00"
        var date = event.startDate
        etTitle.setText(event.title)
        etNote.setText(event.note)

        btnTime.text = "Time: $time"
        btnTime.setOnClickListener {
            ctx.pickTime(time) { picked ->
                time = picked
                btnTime.text = "Time: $picked"
            }
        }

        // only a one-off event has a day that can be changed
        if (event.repeatDaily) {
            btnDate.visibility = View.GONE
            tvRepeat.visibility = View.VISIBLE
        } else {
            tvRepeat.visibility = View.GONE
            btnDate.text = "Day: ${dayLabel(date)}"
            btnDate.setOnClickListener {
                ctx.pickDate(date) { picked ->
                    date = picked
                    btnDate.text = "Day: ${dayLabel(picked)}"
                }
            }
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Edit event")
            .setView(form)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = etTitle.text.toString().trim()
                if (title.isEmpty()) {
                    etTitle.error = "Enter what the event is"
                    return@setOnClickListener
                }
                launchUi {
                    DataManager.updateScheduleEvent(ctx, event, title, etNote.text.toString().trim(), time, date)
                    dialog.dismiss()
                    renderEvents()
                }
            }
        }
        dialog.show()
    }
}
