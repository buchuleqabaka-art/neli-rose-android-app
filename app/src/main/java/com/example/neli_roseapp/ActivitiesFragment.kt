package com.example.neli_roseapp

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.util.Calendar
import java.util.Locale

class ActivitiesFragment : Fragment() {

    private var selectedFilter = 0 // 0: All, 1: Joined, 2: Available

    private val activitiesToday = mutableListOf<TodayActivity>()

    private lateinit var tvFilterAll: TextView
    private lateinit var tvFilterJoined: TextView
    private lateinit var tvFilterAvailable: TextView
    private lateinit var containerActivities: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_activities, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        activitiesToday.clear()
        activitiesToday.addAll(DataManager.getActivities(ctx))

        tvFilterAll = view.findViewById(R.id.tvFilterAll)
        tvFilterJoined = view.findViewById(R.id.tvFilterJoined)
        tvFilterAvailable = view.findViewById(R.id.tvFilterAvailable)
        containerActivities = view.findViewById(R.id.containerActivitiesToday)

        val etRequest = view.findViewById<EditText>(R.id.etActivityRequest)
        val tvDate = view.findViewById<TextView>(R.id.tvActivityDate)
        val btnSend = view.findViewById<Button>(R.id.btnSendActivityRequest)

        val filters = listOf(tvFilterAll, tvFilterJoined, tvFilterAvailable)
        filters.forEachIndexed { index, tv ->
            tv.setOnClickListener {
                selectedFilter = index
                updateFilterUI(filters)
                renderActivities()
            }
        }

        tvDate.setOnClickListener {
            val cal = Calendar.getInstance()
            val dialog = DatePickerDialog(
                ctx,
                { _, y, m, d ->
                    tvDate.text = String.format(Locale.getDefault(), "%02d/%02d/%d", d, m + 1, y)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
            dialog.datePicker.minDate = System.currentTimeMillis() - 1000
            dialog.show()
        }

        btnSend.setOnClickListener {
            val req = etRequest.text.toString().trim()
            if (req.isBlank()) {
                Toast.makeText(ctx, "Type what you would like to do", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "Request sent to the care team", Toast.LENGTH_SHORT).show()
                etRequest.text.clear()
                tvDate.text = "Select Date"
            }
        }

        renderActivities()
    }

    private fun updateFilterUI(filters: List<TextView>) {
        val ctx = requireContext()
        filters.forEachIndexed { i, tv ->
            if (i == selectedFilter) {
                tv.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neli_light_green))
            } else {
                tv.setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.white))
            }
        }
    }

    private fun renderActivities() {
        val ctx = requireContext()
        containerActivities.removeAllViews()

        val filteredList = activitiesToday.withIndex().filter { (_, a) ->
            when (selectedFilter) {
                1 -> a.joined
                2 -> !a.joined
                else -> true
            }
        }

        if (filteredList.isEmpty()) {
            val emptyTv = TextView(ctx).apply {
                text = "Nothing here yet"
                textSize = 13f
                setPadding(0, 16, 0, 16)
            }
            containerActivities.addView(emptyTv)
            return
        }

        filteredList.forEach { (originalIndex, activity) ->
            val row = LayoutInflater.from(ctx).inflate(android.R.layout.simple_list_item_2, containerActivities, false)
            val text1 = row.findViewById<TextView>(android.R.id.text1)
            val text2 = row.findViewById<TextView>(android.R.id.text2)

            text1.text = "${activity.time}  -  ${activity.name}"
            text1.textSize = 15f
            text2.text = "${activity.place}   [${if (activity.joined) "Joined" else "Tap to Join"}]"
            text2.textSize = 13f

            row.setOnClickListener {
                activitiesToday[originalIndex] = activity.copy(joined = !activity.joined)
                DataManager.saveActivities(ctx, activitiesToday)
                renderActivities()
            }

            containerActivities.addView(row)
        }
    }
}
