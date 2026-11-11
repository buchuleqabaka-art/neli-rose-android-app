package com.example.neli_roseapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * Names used for activity requests in Firestore, shared by the resident's screen (which writes
 * them) and the care team's screen (which reads them).
 *
 * Who may read and change what is decided by the Firestore rules in firestore.rules, not by the
 * app: a resident sees only their own requests, and only an account whose users/{id} document
 * has role = "caregiver" can see all of them and mark them as seen.
 */
object CareRequests {
    const val COLLECTION = "activityRequests"
    const val USERS_COLLECTION = "users"
    const val FIELD_ROLE = "role"
    const val ROLE_CAREGIVER = "caregiver"

    const val FIELD_USER_ID = "userId"
    const val FIELD_RESIDENT_NAME = "residentName"
    const val FIELD_REQUEST = "request"
    const val FIELD_PREFERRED_DATE = "preferredDate"
    const val FIELD_STATUS = "status"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_SEEN_BY = "seenBy"
    const val FIELD_SEEN_AT = "seenAt"

    const val STATUS_PENDING = "pending"
    const val STATUS_SEEN = "seen"

    /** When the request was made. While it is still waiting to be sent, the phone's own time is used. */
    fun createdAtMillis(document: DocumentSnapshot): Long? =
        document.getTimestamp(FIELD_CREATED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time

    /** The lines under a request: when it was made, the preferred day if any, and its status. */
    fun describe(document: DocumentSnapshot, status: String): String {
        val lines = mutableListOf<String>()
        createdAtMillis(document)?.let { lines.add("Asked on ${formatDateTime(it)}") }
        val preferred = document.getString(FIELD_PREFERRED_DATE).orEmpty()
        if (preferred.isNotBlank()) lines.add("Preferred day: ${CareDates.display(preferred)}")
        lines.add(status)
        return lines.joinToString("\n")
    }
}

/** The care team's list of residents' activity requests. Opened from Profile by a caregiver account. */
class CareRequestsActivity : AppCompatActivity() {

    private var listener: ListenerRegistration? = null

    private lateinit var tvStatus: TextView
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_care_requests)

        tvStatus = findViewById(R.id.tvCareRequestsStatus)
        container = findViewById(R.id.containerCareRequests)
        findViewById<View>(R.id.btnRequestsBack).setOnClickListener { finish() }

        val uid = DataManager.currentUserId()
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        if (uid == null || firestore == null) {
            tvStatus.text = "Please log in to see requests."
            return
        }

        listener = firestore.collection(CareRequests.COLLECTION)
            .orderBy(CareRequests.FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    container.removeAllViews()
                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = "Requests cannot be shown. This account may not be set up as a caregiver, " +
                        "or the phone is offline."
                } else {
                    render(snapshot.documents, uid)
                }
            }
    }

    override fun onDestroy() {
        listener?.remove()
        listener = null
        super.onDestroy()
    }

    private fun render(documents: List<DocumentSnapshot>, uid: String) {
        container.removeAllViews()
        tvStatus.visibility = if (documents.isEmpty()) View.VISIBLE else View.GONE
        tvStatus.text = "No requests yet."

        for (document in documents) {
            val row = layoutInflater.inflate(R.layout.item_request, container, false)
            val resident = document.getString(CareRequests.FIELD_RESIDENT_NAME).orEmpty().ifBlank { "A resident" }
            row.findViewById<TextView>(R.id.tvRequestText).text =
                "$resident: ${document.getString(CareRequests.FIELD_REQUEST).orEmpty()}"

            val seen = document.getString(CareRequests.FIELD_STATUS) == CareRequests.STATUS_SEEN
            row.findViewById<TextView>(R.id.tvRequestMeta).text =
                CareRequests.describe(document, if (seen) "Marked as seen" else "New")

            val btnSeen = row.findViewById<Button>(R.id.btnRequestSeen)
            btnSeen.visibility = if (seen) View.GONE else View.VISIBLE
            btnSeen.setOnClickListener {
                btnSeen.isEnabled = false
                document.reference.update(
                    CareRequests.FIELD_STATUS, CareRequests.STATUS_SEEN,
                    CareRequests.FIELD_SEEN_BY, uid,
                    CareRequests.FIELD_SEEN_AT, FieldValue.serverTimestamp()
                ).addOnFailureListener { error ->
                    btnSeen.isEnabled = true
                    Toast.makeText(
                        this,
                        "Could not mark as seen: ${error.localizedMessage ?: "please try again"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            container.addView(row)
        }
    }
}
