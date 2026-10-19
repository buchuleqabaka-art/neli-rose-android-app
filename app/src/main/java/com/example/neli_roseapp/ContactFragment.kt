package com.example.neli_roseapp

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class ContactFragment : Fragment() {

    private val contactsList = mutableListOf<CareContact>()
    private var selectedQuickMessage: String? = null

    private lateinit var containerQuickCall: LinearLayout
    private lateinit var spinnerContact: Spinner

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
        contactsList.clear()
        contactsList.addAll(DataManager.getContacts(ctx))

        containerQuickCall = view.findViewById(R.id.containerQuickCall)
        spinnerContact = view.findViewById(R.id.spinnerContactPeople)

        val etName = view.findViewById<EditText>(R.id.etNewContactName)
        val etPhone = view.findViewById<EditText>(R.id.etNewContactPhone)
        val btnAddContact = view.findViewById<Button>(R.id.btnAddContact)

        val btnCallNurse = view.findViewById<Button>(R.id.btnCallNurse)
        val btnQuickMsg1 = view.findViewById<Button>(R.id.btnQuickMsg1)
        val btnQuickMsg2 = view.findViewById<Button>(R.id.btnQuickMsg2)
        val btnSendMessage = view.findViewById<Button>(R.id.btnSendMessage)

        renderContactsUI()

        btnAddContact.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (name.isBlank()) {
                Toast.makeText(ctx, "Enter contact name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phone.isBlank()) {
                Toast.makeText(ctx, "Enter phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newContact = CareContact(name, phone)
            contactsList.add(newContact)
            DataManager.saveContacts(ctx, contactsList)

            etName.text.clear()
            etPhone.text.clear()
            Toast.makeText(ctx, "Contact added!", Toast.LENGTH_SHORT).show()

            renderContactsUI()
        }

        // Direct call for caregiver nurse
        btnCallNurse.setOnClickListener { ctx.dial(CareData.nurse.phone) }

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
            val target = if (position in contactsList.indices) contactsList[position] else null
            val msg = selectedQuickMessage

            if (target == null || msg == null) {
                Toast.makeText(ctx, "Choose a contact and a quick message", Toast.LENGTH_SHORT).show()
            } else {
                val number = target.phone.filter { it.isDigit() || it == '+' }
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
    }

    private fun renderContactsUI() {
        val ctx = requireContext()

        // 1. Populate Quick Call list
        containerQuickCall.removeAllViews()
        contactsList.forEach { contact ->
            val layout = RelativeLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }

            val textLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                val lp = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                )
                lp.addRule(RelativeLayout.ALIGN_PARENT_START)
                layoutParams = lp
            }

            val tvName = TextView(ctx).apply {
                text = contact.name
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
            }

            val tvPhone = TextView(ctx).apply {
                text = contact.phone
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
            }

            textLayout.addView(tvName)
            textLayout.addView(tvPhone)

            val btnCall = ImageView(ctx).apply {
                setImageResource(R.drawable.ic_contact)
                contentDescription = "Call ${contact.name}"
                val lp = RelativeLayout.LayoutParams(
                    (32 * resources.displayMetrics.density).toInt(),
                    (32 * resources.displayMetrics.density).toInt()
                )
                lp.addRule(RelativeLayout.ALIGN_PARENT_END)
                lp.addRule(RelativeLayout.CENTER_VERTICAL)
                layoutParams = lp
                setOnClickListener { ctx.dial(contact.phone) }
            }

            layout.addView(textLayout)
            layout.addView(btnCall)
            containerQuickCall.addView(layout)
        }

        // 2. Populate Spinner choices
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, contactsList.map { "${it.name} (${it.phone})" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerContact.adapter = adapter
    }

    private fun updateQuickMsgButtons(btn1: Button, btn2: Button, isMsg1Selected: Boolean) {
        val ctx = requireContext()
        if (isMsg1Selected) {
            btn1.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neli_green))
            btn1.setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
            btn2.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neli_light_green))
            btn2.setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
        } else {
            btn2.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neli_green))
            btn2.setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
            btn1.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neli_light_green))
            btn1.setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
        }
    }
}
