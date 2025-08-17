package com.example.raw_pesms

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SelectGradeSectionFragment : Fragment() {

    private lateinit var editGrade: EditText
    private lateinit var editSection: EditText
    private lateinit var btnConfirm: Button

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_select_grade_section, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        editGrade = view.findViewById(R.id.editGrade)
        editSection = view.findViewById(R.id.editSection)
        btnConfirm = view.findViewById(R.id.btnConfirm)

        btnConfirm.setOnClickListener {
            saveGradeAndSection()
        }
    }

    private fun saveGradeAndSection() {
        // Read + sanitize inputs
        val rawGrade = editGrade.text.toString().trim()
        val rawSection = editSection.text.toString().trim()
        val grade = sanitizeDocId(rawGrade)
        val section = sanitizeDocId(rawSection)

        if (grade.isEmpty() || section.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter both Grade and Section (no slashes).", Toast.LENGTH_SHORT).show()
            return
        }

        // Get ownerId from prefs or FirebaseAuth as fallback
        val prefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        var ownerId = prefs.getString("logged_in_user_uid", "")?.trim().orEmpty()

        if (ownerId.isBlank()) {
            // fallback to auth current user
            ownerId = auth.currentUser?.uid ?: ""
            if (ownerId.isNotBlank()) {
                prefs.edit().putString("logged_in_user_uid", ownerId).apply()
            }
        }

        if (ownerId.isBlank()) {
            Toast.makeText(requireContext(), "You must be signed in to save grades/sections. Please sign in.", Toast.LENGTH_LONG).show()
            // Optionally, redirect to sign-in screen:
            try {
                startActivity(Intent(requireContext(), SignInActivity::class.java))
            } catch (e: Exception) {
                // If your SignInActivity is in different package/name, adjust accordingly
                Log.w("SelectGradeSection", "Could not start SignInActivity: ${e.message}")
            }
            return
        }

        // Disable button while saving
        btnConfirm.isEnabled = false
        val oldText = btnConfirm.text
        btnConfirm.text = "Saving..."

        // Use a batch write so both grade doc and section doc are written together
        val ownerRef = db.collection("owners").document(ownerId)
        val gradeRef = ownerRef.collection("grades").document(grade)
        val sectionRef = gradeRef.collection("sections").document(section)

        val batch = db.batch()
        batch.set(gradeRef, mapOf("name" to rawGrade))     // save original (raw) label if you want
        batch.set(sectionRef, mapOf("name" to rawSection))

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Saved!", Toast.LENGTH_SHORT).show()
                editGrade.text.clear()
                editSection.text.clear()
                // Re-enable & restore
                btnConfirm.isEnabled = true
                btnConfirm.text = oldText

                // Navigate to HomeFragment — make sure action id exists in your nav graph
                // If this action id is different in your project, update it accordingly.
                try {
                    findNavController().navigate(R.id.action_selectGradeSectionFragment_to_homeFragment)
                } catch (ex: Exception) {
                    // Navigation failed (maybe action id differs) — just log it and stay in place
                    Log.w("SelectGradeSection", "Navigation failed: ${ex.message}")
                }
            }
            .addOnFailureListener { e ->
                Log.e("SelectGradeSection", "Failed to save grade/section", e)
                Toast.makeText(requireContext(), "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                btnConfirm.isEnabled = true
                btnConfirm.text = oldText
            }
    }

    // Replace path-unfriendly chars (like '/') and trim; Firestore docIds cannot contain '/'
    private fun sanitizeDocId(input: String): String {
        return input.replace("/", "-").trim()
    }
}
