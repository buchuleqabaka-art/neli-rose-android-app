package com.example.neli_roseapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.neli_roseapp.db.MedicationEntity

class HealthFragment : Fragment() {

    private val symptoms = listOf(
        "Headache", "Dizziness", "Nausea", "Fatigue", "Chest pain",
        "Shortness of breath", "Joint pain", "Other"
    )

    // every record on this screen belongs to this day
    private var today = CareDates.today()
    private var glasses = 0

    private lateinit var tvLastVitals: TextView
    private lateinit var containerMedications: LinearLayout
    private lateinit var tvMedsEmpty: TextView
    private lateinit var tvHydrationProgressText: TextView
    private lateinit var progressHydration: ProgressBar
    private lateinit var containerSymptoms: LinearLayout
    private lateinit var tvSymptomsEmpty: TextView

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
        today = CareDates.today()

        tvLastVitals = view.findViewById(R.id.tvLastVitals)
        containerMedications = view.findViewById(R.id.containerMedications)
        tvMedsEmpty = view.findViewById(R.id.tvMedsEmpty)
        tvHydrationProgressText = view.findViewById(R.id.tvHydrationProgressText)
        progressHydration = view.findViewById(R.id.progressHydration)
        containerSymptoms = view.findViewById(R.id.containerSymptoms)
        tvSymptomsEmpty = view.findViewById(R.id.tvSymptomsEmpty)

        val etBP = view.findViewById<EditText>(R.id.etBP)
        val etHR = view.findViewById<EditText>(R.id.etHR)
        val etTemp = view.findViewById<EditText>(R.id.etTemp)
        val etWeight = view.findViewById<EditText>(R.id.etWeight)
        val btnSaveVitals = view.findViewById<Button>(R.id.btnSaveVitals)
        val btnAddMedication = view.findViewById<Button>(R.id.btnAddMedication)
        val btnAddGlass = view.findViewById<Button>(R.id.btnAddGlass)
        val spinnerSymptoms = view.findViewById<Spinner>(R.id.spinnerSymptoms)
        val etSymptomNotes = view.findViewById<EditText>(R.id.etSymptomNotes)
        val btnLogSymptom = view.findViewById<Button>(R.id.btnLogSymptom)

        // Vitals are checked first and only saved when every filled-in field is a sensible reading
        btnSaveVitals.setOnClickListener {
            val check = VitalsValidator.check(
                etBP.text.toString(),
                etHR.text.toString(),
                etTemp.text.toString(),
                etWeight.text.toString()
            )
            etBP.error = check.bloodPressureError
            etHR.error = check.heartRateError
            etTemp.error = check.temperatureError
            etWeight.error = check.weightError

            val values = check.values
            if (values == null) {
                Toast.makeText(ctx, check.formError ?: "Please correct the marked fields", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            launchUi {
                if (DataManager.saveVitals(ctx, values)) {
                    listOf(etBP, etHR, etTemp, etWeight).forEach { it.text.clear() }
                    Toast.makeText(ctx, "Vitals saved", Toast.LENGTH_SHORT).show()
                    showLatestVitals()
                } else {
                    Toast.makeText(ctx, "Could not save. Please log in again.", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnAddMedication.setOnClickListener { showMedicationDialog(null) }

        // Hydration tracker: one count per day
        btnAddGlass.setOnClickListener {
            if (glasses >= CareRules.HYDRATION_GOAL) {
                Toast.makeText(ctx, "Daily goal reached!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            launchUi {
                DataManager.saveHydration(ctx, today, glasses + 1)
                showHydration()
            }
        }

        // Symptom logger: saved with notes, date and time
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, symptoms)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSymptoms.adapter = adapter

        btnLogSymptom.setOnClickListener {
            val selectedSymptom = spinnerSymptoms.selectedItem?.toString() ?: return@setOnClickListener
            val notes = etSymptomNotes.text.toString().trim()
            launchUi {
                if (DataManager.logSymptom(ctx, selectedSymptom, notes)) {
                    etSymptomNotes.text.clear()
                    Toast.makeText(ctx, "Saved: $selectedSymptom", Toast.LENGTH_SHORT).show()
                    showSymptoms()
                } else {
                    Toast.makeText(ctx, "Could not save. Please log in again.", Toast.LENGTH_LONG).show()
                }
            }
        }

        launchUi {
            showLatestVitals()
            showMedications()
            showHydration()
            showSymptoms()
        }
    }

    private suspend fun showLatestVitals() {
        val latest = DataManager.getLatestVitals(requireContext())
        if (latest == null) {
            tvLastVitals.text = "No vitals saved yet"
            return
        }
        val reading = VitalsValidator.describe(
            latest.systolic, latest.diastolic, latest.heartRate, latest.temperatureC, latest.weightKg
        )
        val savedWhen = when {
            latest.recordedAt == 0L -> "saved before this update, date not recorded"
            latest.date == today -> "saved today at ${formatTime(latest.recordedAt)}"
            else -> "saved on ${formatDateTime(latest.recordedAt)}"
        }
        tvLastVitals.text = "Last reading: $reading\n($savedWhen)"
    }

    private suspend fun showMedications() {
        val ctx = requireContext()
        val items = DataManager.getMedications(ctx, today)
        containerMedications.removeAllViews()
        tvMedsEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        for (item in items) {
            val medication = item.medication
            val row = layoutInflater.inflate(R.layout.item_medication, containerMedications, false)
            row.findViewById<TextView>(R.id.tvMedName).text = medication.name
            row.findViewById<TextView>(R.id.tvMedTime).text = "Time: ${medication.time}"

            val btnToggle = row.findViewById<Button>(R.id.btnMedToggle)
            updateMedButton(btnToggle, item.taken)
            btnToggle.setOnClickListener {
                launchUi {
                    DataManager.setMedicationTaken(ctx, medication.id, today, !item.taken)
                    showMedications()
                }
            }

            row.findViewById<Button>(R.id.btnMedEdit).setOnClickListener { showMedicationDialog(medication) }
            row.findViewById<Button>(R.id.btnMedDelete).setOnClickListener {
                ctx.confirm(
                    "Remove ${medication.name}?",
                    "It will no longer be listed from today. Earlier days keep their record.",
                    "Remove"
                ) {
                    launchUi {
                        DataManager.removeMedication(ctx, medication)
                        showMedications()
                    }
                }
            }
            containerMedications.addView(row)
        }
    }

    /** Add a new medicine (existing = null) or change one. */
    private fun showMedicationDialog(existing: MedicationEntity?) {
        val ctx = requireContext()
        val form = layoutInflater.inflate(R.layout.dialog_medication, null)
        val etName = form.findViewById<EditText>(R.id.etDialogMedName)
        val btnTime = form.findViewById<Button>(R.id.btnDialogMedTime)

        // entries from the old app hold text such as "Morning 08:00"; keep just the time
        var time = CareTimes.minutesOfDay(existing?.time ?: "")
            ?.let { CareTimes.format(it / 60, it % 60) } ?: "08:00"
        etName.setText(existing?.name ?: "")
        btnTime.text = time
        btnTime.setOnClickListener {
            ctx.pickTime(time) { picked ->
                time = picked
                btnTime.text = picked
            }
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) "Add a medicine" else "Edit medicine")
            .setView(form)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    etName.error = "Enter the medicine's name"
                    return@setOnClickListener
                }
                launchUi {
                    if (existing == null) {
                        DataManager.addMedication(ctx, name, time)
                    } else {
                        DataManager.updateMedication(ctx, existing, name, time)
                    }
                    dialog.dismiss()
                    showMedications()
                }
            }
        }
        dialog.show()
    }

    private fun updateMedButton(btn: Button, taken: Boolean) {
        val ctx = requireContext()
        if (taken) {
            btn.text = "Taken today (tap to undo)"
            btn.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neli_light_green))
            btn.setTextColor(ContextCompat.getColor(ctx, R.color.neli_green))
        } else {
            btn.text = "Not taken yet (tap when taken)"
            btn.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neli_pending_bg))
            btn.setTextColor(ContextCompat.getColor(ctx, R.color.neli_pending_text))
        }
    }

    private suspend fun showHydration() {
        glasses = DataManager.getHydration(requireContext(), today)
        tvHydrationProgressText.text = "$glasses of ${CareRules.HYDRATION_GOAL} glasses today"
        progressHydration.max = CareRules.HYDRATION_GOAL
        progressHydration.progress = glasses
    }

    private suspend fun showSymptoms() {
        val logs = DataManager.getRecentSymptoms(requireContext(), 5)
        containerSymptoms.removeAllViews()
        tvSymptomsEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE

        for (log in logs) {
            val row = layoutInflater.inflate(R.layout.item_symptom, containerSymptoms, false)
            row.findViewById<TextView>(R.id.tvSymptomTitle).text = log.symptom
            val loggedWhen = formatDateTime(log.loggedAt)
            row.findViewById<TextView>(R.id.tvSymptomMeta).text =
                if (log.notes.isBlank()) loggedWhen else "$loggedWhen\n${log.notes}"
            containerSymptoms.addView(row)
        }
    }
}
