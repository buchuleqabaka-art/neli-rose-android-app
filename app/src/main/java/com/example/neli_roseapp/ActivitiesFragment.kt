package com.example.neli_roseapp

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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

class ActivitiesFragment : Fragment() {

    private var selectedFilter = 0 // 0: All, 1: Joined, 2: Available

    private val activitiesToday = mutableListOf<TodayActivity>()

    // preferred day for the request being written (yyyy-MM-dd), or null when none was chosen
    private var preferredDate: String? = null

    // keeps "My requests" up to date; removed when the screen goes away
    private var requestsListener: ListenerRegistration? = null

    private lateinit var tvFilterAll: TextView
    private lateinit var tvFilterJoined: TextView
    private lateinit var tvFilterAvailable: TextView
    private lateinit var containerActivities: LinearLayout
    private lateinit var containerMyRequests: LinearLayout
    private lateinit var tvRequestsEmpty: TextView

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

        tvFilterAll = view.findViewById(R.id.tvFilterAll)
        tvFilterJoined = view.findViewById(R.id.tvFilterJoined)
        tvFilterAvailable = view.findViewById(R.id.tvFilterAvailable)
        containerActivities = view.findViewById(R.id.containerActivitiesToday)
        containerMyRequests = view.findViewById(R.id.containerMyRequests)
        tvRequestsEmpty = view.findViewById(R.id.tvRequestsEmpty)

        val etRequest = view.findViewById<EditText>(R.id.etActivityRequest)
        val btnDate = view.findViewById<Button>(R.id.btnActivityDate)
        val btnSend = view.findViewById<Button>(R.id.btnSendActivityRequest)

        val filters = listOf(tvFilterAll, tvFilterJoined, tvFilterAvailable)
        filters.forEachIndexed { index, tv ->
            tv.setOnClickListener {
                selectedFilter = index
                updateFilterUI(filters)
                renderActivities()
            }
        }

        btnDate.setOnClickListener {
            ctx.pickDate(preferredDate ?: CareDates.today()) { picked ->
                if (picked < CareDates.today()) {
                    Toast.makeText(ctx, "Choose today or a later day", Toast.LENGTH_SHORT).show()
                } else {
                    preferredDate = picked
                    btnDate.text = "Preferred day: ${CareDates.display(picked)}"
                }
            }
        }

        btnSend.setOnClickListener { sendRequest(etRequest, btnDate) }

        launchUi {
            activitiesToday.clear()
            activitiesToday.addAll(DataManager.getActivities(ctx))
            renderActivities()
        }
        listenForMyRequests()
    }

    override fun onDestroyView() {
        requestsListener?.remove()
        requestsListener = null
        super.onDestroyView()
    }

    /**
     * Saves the request in the care team's database (Firestore).
     * "Request sent" is only shown once the server has confirmed it. Until then the request is
     * listed under "My requests" as waiting, and a refusal by the server is reported.
     */
    private fun sendRequest(etRequest: EditText, btnDate: Button) {
        val ctx = requireContext()
        val text = etRequest.text.toString().trim()
        if (text.isBlank()) {
            etRequest.error = "Type what you would like to do"
            return
        }
        val uid = DataManager.currentUserId()
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        if (uid == null || firestore == null) {
            Toast.makeText(ctx, "Requests cannot be sent right now. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }
        val preferred = preferredDate ?: ""

        launchUi {
            val data = hashMapOf<String, Any>(
                CareRequests.FIELD_USER_ID to uid,
                CareRequests.FIELD_RESIDENT_NAME to DataManager.getUserName(ctx),
                CareRequests.FIELD_REQUEST to text,
                CareRequests.FIELD_PREFERRED_DATE to preferred,
                CareRequests.FIELD_STATUS to CareRequests.STATUS_PENDING,
                CareRequests.FIELD_CREATED_AT to FieldValue.serverTimestamp()
            )

            // the form is emptied now because the request already shows under "My requests"
            etRequest.text.clear()
            preferredDate = null
            btnDate.text = "Choose a preferred day (optional)"

            firestore.collection(CareRequests.COLLECTION).add(data)
                .addOnSuccessListener {
                    if (view != null) {
                        Toast.makeText(ctx, "Request sent to the care team", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { error ->
                    if (view != null) {
                        Toast.makeText(
                            ctx,
                            "Your request could not be sent: ${error.localizedMessage ?: "please try again"}",
                            Toast.LENGTH_LONG
                        ).show()
                        // give the text back so it does not have to be typed again
                        if (etRequest.text.isBlank()) etRequest.setText(text)
                    }
                }
        }
    }

    private fun listenForMyRequests() {
        val uid = DataManager.currentUserId() ?: return
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull() ?: return
        requestsListener = firestore.collection(CareRequests.COLLECTION)
            .whereEqualTo(CareRequests.FIELD_USER_ID, uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (view == null) return@addSnapshotListener
                if (error != null || snapshot == null) {
                    containerMyRequests.removeAllViews()
                    tvRequestsEmpty.text = "Your requests cannot be shown right now."
                    tvRequestsEmpty.visibility = View.VISIBLE
                } else {
                    renderRequests(snapshot.documents)
                }
            }
    }

    private fun renderRequests(documents: List<DocumentSnapshot>) {
        val sorted = documents.sortedByDescending { CareRequests.createdAtMillis(it) ?: Long.MAX_VALUE }
        containerMyRequests.removeAllViews()
        tvRequestsEmpty.text = "You have not sent any requests yet."
        tvRequestsEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE

        for (document in sorted) {
            val row = layoutInflater.inflate(R.layout.item_request, containerMyRequests, false)
            row.findViewById<TextView>(R.id.tvRequestText).text = document.getString(CareRequests.FIELD_REQUEST) ?: ""

            val status = when {
                document.metadata.hasPendingWrites() -> "Waiting to send. It goes out when the phone is online."
                document.getString(CareRequests.FIELD_STATUS) == CareRequests.STATUS_SEEN -> "Seen by the care team"
                else -> "Sent to the care team"
            }
            row.findViewById<TextView>(R.id.tvRequestMeta).text = CareRequests.describe(document, status)
            containerMyRequests.addView(row)
        }
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
                textSize = 16f
                setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
                setPadding(0, 16, 0, 16)
            }
            containerActivities.addView(emptyTv)
            return
        }

        filteredList.forEach { (originalIndex, activity) ->
            val row = layoutInflater.inflate(R.layout.item_activity, containerActivities, false)
            row.findViewById<TextView>(R.id.tvActivityTitle).text = "${activity.time}  ${activity.name}"
            row.findViewById<TextView>(R.id.tvActivityPlace).text = activity.place

            val btnJoin = row.findViewById<Button>(R.id.btnActivityJoin)
            btnJoin.text = if (activity.joined) "Joined (tap to leave)" else "Join"
            btnJoin.setBackgroundColor(
                ContextCompat.getColor(ctx, if (activity.joined) R.color.neli_grey else R.color.neli_light_green)
            )
            btnJoin.setOnClickListener {
                activitiesToday[originalIndex] = activity.copy(joined = !activity.joined)
                launchUi {
                    DataManager.saveActivities(ctx, activitiesToday.toList())
                    renderActivities()
                }
            }

            containerActivities.addView(row)
        }
    }
}
