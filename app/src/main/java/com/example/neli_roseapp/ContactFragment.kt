package com.example.neli_roseapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.neli_roseapp.db.ContactEntity

class ContactFragment : Fragment() {

    private var contacts: List<ContactEntity> = emptyList()
    private var selectedQuickMessage: String? = null

    private lateinit var containerQuickCall: LinearLayout
    private lateinit var tvContactsEmpty: TextView
    private lateinit var spinnerContact: Spinner
    private lateinit var tvCaregiverCardName: TextView
    private lateinit var tvCaregiverCardHint: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_contact, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()

        containerQuickCall = view.findViewById(R.id.containerQuickCall)
        tvContactsEmpty = view.findViewById(R.id.tvContactsEmpty)
        spinnerContact = view.findViewById(R.id.spinnerContactPeople)
        tvCaregiverCardName = view.findViewById(R.id.tvCaregiverCardName)
        tvCaregiverCardHint = view.findViewById(R.id.tvCaregiverCardHint)

        val etName = view.findViewById<EditText>(R.id.etNewContactName)
        val etPhone = view.findViewById<EditText>(R.id.etNewContactPhone)
        val btnAddContact = view.findViewById<Button>(R.id.btnAddContact)

        val btnCallCaregiverCard = view.findViewById<Button>(R.id.btnCallCaregiverCard)
        val btnQuickMsg1 = view.findViewById<Button>(R.id.btnQuickMsg1)
        val btnQuickMsg2 = view.findViewById<Button>(R.id.btnQuickMsg2)
        val btnSendMessage = view.findViewById<Button>(R.id.btnSendMessage)

        btnAddContact.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (name.isBlank()) {
                etName.error = "Enter the contact's name"
                return@setOnClickListener
            }
            if (!CareRules.isPlausiblePhone(phone)) {
                etPhone.error = "Enter a phone number with at least 7 digits"
                return@setOnClickListener
            }

            launchUi {
                if (DataManager.addContact(ctx, name, phone)) {
                    etName.text.clear()
                    etPhone.text.clear()
                    Toast.makeText(ctx, "Contact added", Toast.LENGTH_SHORT).show()
                    loadContacts()
                } else {
                    Toast.makeText(ctx, "Could not save. Please log in again.", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Calls the contact the person chose as caregiver
        btnCallCaregiverCard.setOnClickListener {
            val caregiver = contacts.firstOrNull { it.isCaregiver }
            if (caregiver == null) {
                Toast.makeText(ctx, "Choose a caregiver in the Quick Call list first", Toast.LENGTH_LONG).show()
            } else {
                call(caregiver.phone)
            }
        }

        // Quick message toggle buttons
        btnQuickMsg1.setOnClickListener {
            selectedQuickMessage = "I'm doing well"
            updateQuickMsgButtons(btnQuickMsg1, btnQuickMsg2, true)
        }

        btnQuickMsg2.setOnClickListener {
            selectedQuickMessage = "Please call me"
            updateQuickMsgButtons(btnQuickMsg1, btnQuickMsg2, false)
        }

        // Send SMS to selected contact
        btnSendMessage.setOnClickListener {
            val position = spinnerContact.selectedItemPosition
            val target = if (position in contacts.indices) contacts[position] else null
            val msg = selectedQuickMessage

            if (target == null || msg == null) {
                Toast.makeText(ctx, "Choose a contact and a message first", Toast.LENGTH_SHORT).show()
            } else {
                val number = CareRules.cleanPhone(target.phone)
                val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).apply {
                    putExtra("sms_body", msg)
                }
                runCatching {
                    startActivity(intent)
                }.onFailure {
                    Toast.makeText(ctx, "No messaging app found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        launchUi { loadContacts() }
    }

    private suspend fun loadContacts() {
        contacts = DataManager.getContacts(requireContext())
        renderContactsUI()
    }

    private fun call(phone: String) {
        val ctx = requireContext()
        if (!ctx.dial(phone)) {
            Toast.makeText(ctx, "No phone app found on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderContactsUI() {
        val ctx = requireContext()

        // 1. Quick Call list, with Call, Set as caregiver, Edit and Delete on every contact
        containerQuickCall.removeAllViews()
        tvContactsEmpty.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE

        for (contact in contacts) {
            val row = layoutInflater.inflate(R.layout.item_contact, containerQuickCall, false)
            row.findViewById<TextView>(R.id.tvContactName).text = contact.name
            row.findViewById<TextView>(R.id.tvContactPhone).text = contact.phone
            row.findViewById<TextView>(R.id.tvContactBadge).visibility =
                if (contact.isCaregiver) View.VISIBLE else View.GONE

            val btnCall = row.findViewById<Button>(R.id.btnContactCall)
            btnCall.text = "Call ${contact.name}"
            btnCall.setOnClickListener { call(contact.phone) }

            val btnCaregiver = row.findViewById<Button>(R.id.btnContactCaregiver)
            btnCaregiver.visibility = if (contact.isCaregiver) View.GONE else View.VISIBLE
            btnCaregiver.setOnClickListener {
                launchUi {
                    DataManager.setCaregiver(ctx, contact.id)
                    Toast.makeText(ctx, "${contact.name} is now your caregiver", Toast.LENGTH_SHORT).show()
                    loadContacts()
                }
            }

            row.findViewById<Button>(R.id.btnContactEdit).setOnClickListener { showEditDialog(contact) }
            row.findViewById<Button>(R.id.btnContactDelete).setOnClickListener {
                ctx.confirm(
                    "Delete ${contact.name}?",
                    if (contact.isCaregiver) {
                        "This is your caregiver. The caregiver buttons will have nobody to call until you choose another contact."
                    } else {
                        "This contact will be removed from your list."
                    },
                    "Delete"
                ) {
                    launchUi {
                        DataManager.deleteContact(ctx, contact)
                        loadContacts()
                    }
                }
            }

            containerQuickCall.addView(row)
        }

        // 2. Choices for "Send to"
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, contacts.map { "${it.name} (${it.phone})" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerContact.adapter = adapter

        // 3. My Caregiver card
        val caregiver = contacts.firstOrNull { it.isCaregiver }
        if (caregiver == null) {
            tvCaregiverCardName.text = "No caregiver chosen yet"
            tvCaregiverCardHint.text = "Choose one in the Quick Call list with the button \"Set as my caregiver\"."
        } else {
            tvCaregiverCardName.text = caregiver.name
            tvCaregiverCardHint.text = caregiver.phone
        }
    }

    private fun showEditDialog(contact: ContactEntity) {
        val ctx = requireContext()
        val form = layoutInflater.inflate(R.layout.dialog_contact, null)
        val etName = form.findViewById<EditText>(R.id.etDialogContactName)
        val etPhone = form.findViewById<EditText>(R.id.etDialogContactPhone)
        etName.setText(contact.name)
        etPhone.setText(contact.phone)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Edit contact")
            .setView(form)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                if (name.isBlank()) {
                    etName.error = "Enter the contact's name"
                    return@setOnClickListener
                }
                if (!CareRules.isPlausiblePhone(phone)) {
                    etPhone.error = "Enter a phone number with at least 7 digits"
                    return@setOnClickListener
                }
                launchUi {
                    DataManager.updateContact(ctx, contact, name, phone)
                    dialog.dismiss()
                    loadContacts()
                }
            }
        }
        dialog.show()
    }

    private fun updateQuickMsgButtons(btn1: Button, btn2: Button, isMsg1Selected: Boolean) {
        val ctx = requireContext()
        val selected = if (isMsg1Selected) btn1 else btn2
        val other = if (isMsg1Selected) btn2 else btn1
        selected.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neli_green))
        selected.setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
        other.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neli_light_green))
        other.setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
    }
}
