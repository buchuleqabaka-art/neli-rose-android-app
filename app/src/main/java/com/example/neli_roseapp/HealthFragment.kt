package com.example.neli_roseapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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

        val vitalsWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                DataManager.saveVitals(
                    ctx,
                    etBP.text.toString().trim(),
                    etHR.text.toString().trim(),
                    etTemp.text.toString().trim(),
                    etWeight.text.toString().trim()
                )
            }
        }
        listOf(etBP, etHR, etTemp, etWeight).forEach { it.addTextChangedListener(vitalsWatcher) }

        btnSaveVitals.setOnClickListener {
            val bp = etBP.text.toString().trim()
            val hr = etHR.text.toString().trim()
            val temp = etTemp.text.toString().trim()
            val weight = etWeight.text.toString().trim()

            DataManager.saveVitals(ctx, bp, hr, temp, weight)
            Toast.makeText(ctx, "Vitals saved successfully!", Toast.LENGTH_SHORT).show()
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

        // Hydration tracker
        updateHydration(tvHydrationProgressText, progressHydration)
        btnAddGlass.setOnClickListener {
            if (glasses < 8) {
                glasses++
                updateHydration(tvHydrationProgressText, progressHydration)
                DataManager.saveHydration(ctx, glasses)
            } else {
                Toast.makeText(ctx, "Daily goal reached!", Toast.LENGTH_SHORT).show()
            }
        }

        // Symptom logger
        btnLogSymptom.setOnClickListener {
            val selectedSymptom = spinnerSymptoms.selectedItem?.toString()
            if (selectedSymptom != null) {
                Toast.makeText(ctx, "Logged: $selectedSymptom", Toast.LENGTH_SHORT).show()
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
        tvText.text = "$glasses / 8 Glasses"
        progressBar.progress = glasses
    }
}
