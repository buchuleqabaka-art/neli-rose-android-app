package com.example.neli_roseapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.DateFormat
import java.util.Date

class HealthFragment : Fragment() {

    private var glasses = 6
    private val symptoms = listOf(
        "Headache", "Dizziness", "Nausea", "Fatigue", "Chest pain",
        "Shortness of breath", "Joint pain", "Other"
    )

    private val meds = mutableListOf<Med>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_health, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()

        // Load saved state
        val vitalsMap = DataManager.getVitals(ctx)
        glasses = DataManager.getHydration(ctx)
        meds.clear()
        meds.addAll(DataManager.getMeds(ctx))

        val etBP = view.findViewById<EditText>(R.id.etBP)
        val etHR = view.findViewById<EditText>(R.id.etHR)
        val etTemp = view.findViewById<EditText>(R.id.etTemp)
        val etWeight = view.findViewById<EditText>(R.id.etWeight)
        val btnSaveVitals = view.findViewById<Button>(R.id.btnSaveVitals)

        // Populate saved vitals
        etBP.setText(vitalsMap["bp"])
        etHR.setText(vitalsMap["hr"])
        etTemp.setText(vitalsMap["temp"])
        etWeight.setText(vitalsMap["weight"])

        btnSaveVitals.setOnClickListener {
            val bp = etBP.text.toString().trim()
            val hr = etHR.text.toString().trim()
            val temp = etTemp.text.toString().trim()
            val weight = etWeight.text.toString().trim()

            val errors = VitalsValidation.errors(bp, hr, temp, weight)
            etBP.error = errors["bp"]
            etHR.error = errors["hr"]
            etTemp.error = errors["temp"]
            etWeight.error = errors["weight"]
            if (errors.isNotEmpty()) return@setOnClickListener
            if (DataManager.saveVitals(ctx, bp, hr, temp, weight)) {
                Toast.makeText(ctx, "Vitals saved on this device", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "Could not save vitals. Please try again.", Toast.LENGTH_LONG).show()
            }
        }

        val btnMed1 = view.findViewById<Button>(R.id.btnMed1)
        val btnMed2 = view.findViewById<Button>(R.id.btnMed2)
        val btnMed3 = view.findViewById<Button>(R.id.btnMed3)
        val tvHydrationProgressText = view.findViewById<TextView>(R.id.tvHydrationProgressText)
        val progressHydration = view.findViewById<ProgressBar>(R.id.progressHydration)
        val btnAddGlass = view.findViewById<Button>(R.id.btnAddGlass)
        val spinnerSymptoms = view.findViewById<Spinner>(R.id.spinnerSymptoms)
        val btnLogSymptom = view.findViewById<Button>(R.id.btnLogSymptom)

        // Setup symptoms spinner
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, symptoms)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSymptoms.adapter = adapter

        // Setup medication toggle buttons
        val medButtons = listOf(btnMed1, btnMed2, btnMed3)
        medButtons.forEachIndexed { i, btn ->
            if (i in meds.indices) {
                updateMedButton(btn, meds[i].taken)
                btn.setOnClickListener {
                    meds[i] = meds[i].copy(taken = !meds[i].taken)
                    updateMedButton(btn, meds[i].taken)
                    DataManager.saveMeds(ctx, meds)
                }
            }
        }

        // This is a manually reset counter, not a prescribed hydration goal.
        updateHydration(tvHydrationProgressText, progressHydration)
        btnAddGlass.setOnClickListener {
            if (glasses < 999) {
                glasses++
                updateHydration(tvHydrationProgressText, progressHydration)
                DataManager.saveHydration(ctx, glasses)
            }
        }
        view.findViewById<Button>(R.id.btnResetHydration).setOnClickListener {
            glasses = 0
            DataManager.saveHydration(ctx, glasses)
            updateHydration(tvHydrationProgressText, progressHydration)
        }

        val symptomHistory = view.findViewById<TextView>(R.id.tvSymptomHistory)
        fun renderSymptoms() {
            val entries = DataManager.getSymptoms(ctx)
            symptomHistory.text = if (entries.isEmpty()) "No symptoms recorded on this device."
            else entries.take(10).joinToString("\n") { (name, time) ->
                "${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))}: $name"
            }
        }
        renderSymptoms()
        btnLogSymptom.setOnClickListener {
            val selected = spinnerSymptoms.selectedItem?.toString() ?: return@setOnClickListener
            if (DataManager.addSymptom(ctx, selected, System.currentTimeMillis())) {
                renderSymptoms()
                Toast.makeText(ctx, "Saved on this device only. No one was notified.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(ctx, "Could not save symptom. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateMedButton(btn: Button, taken: Boolean) {
        val ctx = requireContext()
        if (taken) {
            btn.text = "Taken"
            btn.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neli_light_green))
            btn.setTextColor(ContextCompat.getColor(ctx, R.color.neli_green))
        } else {
            btn.text = "Pending"
            btn.setBackgroundColor(0xFFFF9B9B.toInt())
            btn.setTextColor(0xFFD50000.toInt())
        }
    }

    private fun updateHydration(tvText: TextView, progressBar: ProgressBar) {
        tvText.text = "$glasses glasses recorded since reset"
        progressBar.progress = glasses.coerceAtMost(progressBar.max)
    }
}
