package com.example.raw_pesms.UI.student

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.raw_pesms.R
import com.example.raw_pesms.data.AI.FaceRecognitionManager
import com.example.raw_pesms.data.model.Student
import com.example.raw_pesms.viewmodel.AttendanceViewModel
import com.example.raw_pesms.viewmodel.AttendanceViewModelFactory
import kotlinx.coroutines.launch

class AddStudentFragment : Fragment() {

    private lateinit var firstNameInput: EditText
    private lateinit var middleInitialInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var gradeSpinner: Spinner
    private lateinit var sectionSpinner: Spinner
    private lateinit var addButton: Button
    private lateinit var enrollButton: Button

    private var lastInsertedStudentId: String? = null
    private var selectedGrade: String? = null
    private var selectedSection: String? = null

    private val viewModel: AttendanceViewModel by activityViewModels { AttendanceViewModelFactory(requireActivity().application) }

    // Camera launcher for enrollment
    private val enrollLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        val id = lastInsertedStudentId ?: return@registerForActivityResult
        val grade = selectedGrade ?: return@registerForActivityResult
        val section = selectedSection ?: return@registerForActivityResult
        if (bmp == null) return@registerForActivityResult

        val manager = FaceRecognitionManager(requireContext())
        lifecycleScope.launch {
            val faces = manager.detectFaces(bmp)
            val face = faces.firstOrNull()
            if (face == null) {
                Toast.makeText(requireContext(), "No face detected for enrollment", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val embedding = manager.embed(face)?.joinToString(",")
            viewModel.enrollStudentFace(id, grade, section, embedding, null)
            Toast.makeText(requireContext(), "Face enrolled!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_add_student, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        firstNameInput = view.findViewById(R.id.editFirstName)
        middleInitialInput = view.findViewById(R.id.editMiddleInitial)
        lastNameInput = view.findViewById(R.id.editLastName)
        gradeSpinner = view.findViewById(R.id.spinnerGrade)
        sectionSpinner = view.findViewById(R.id.spinnerSection)
        addButton = view.findViewById(R.id.btnAddStudent)
        enrollButton = view.findViewById(R.id.btnEnrollFace)

        // load dynamic folders
        viewModel.loadGrades()
        viewModel.grades.observe(viewLifecycleOwner) { grades ->
            val list = listOf("Select Grade") + grades
            gradeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, list)
        }

        gradeSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                selectedGrade = parent.getItemAtPosition(pos).toString().takeIf { it != "Select Grade" }
                if (selectedGrade != null) viewModel.loadSections(selectedGrade!!)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        viewModel.sections.observe(viewLifecycleOwner) { sections ->
            val list = listOf("Select Section") + sections
            sectionSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, list)
        }

        sectionSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                selectedSection = parent.getItemAtPosition(pos).toString().takeIf { it != "Select Section" }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        addButton.setOnClickListener {
            val first = firstNameInput.text.toString().trim()
            val mid = middleInitialInput.text.toString().trim()
            val last = lastNameInput.text.toString().trim()
            val grade = selectedGrade
            val section = selectedSection

            if (first.isEmpty() || last.isEmpty() || grade == null || section == null) {
                Toast.makeText(requireContext(), "Fill name and choose grade/section", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Firestore data map
            val studentData = hashMapOf(
                "firstName" to first,
                "middleInitial" to mid,
                "lastName" to last,
                "grade" to grade,
                "section" to section,
                "ownerId" to currentUser.uid
            )

            // Save to Firestore under owner's grades/sections/students
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("owners")
                .document(currentUser.uid)
                .collection("grades")
                .document(grade)
                .collection("sections")
                .document(section)
                .collection("students")
                .add(studentData)
                .addOnSuccessListener { docRef ->
                    lastInsertedStudentId = docRef.id
                    Toast.makeText(requireContext(), "Student added. You may enroll a face.", Toast.LENGTH_SHORT).show()
                    enrollButton.visibility = View.VISIBLE
                    clearInputs()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error adding student: ${e.message}", Toast.LENGTH_SHORT).show()
                    android.util.Log.e("Firestore", "Error adding student", e)
                }
        }

        enrollButton.setOnClickListener {
            if (lastInsertedStudentId == null) {
                Toast.makeText(requireContext(), "Add student first.", Toast.LENGTH_SHORT).show()
            } else {
                enrollLauncher.launch(null)
            }
        }
    }

    private fun clearInputs() {
        firstNameInput.text.clear()
        middleInitialInput.text.clear()
        lastNameInput.text.clear()
        gradeSpinner.setSelection(0)
        sectionSpinner.setSelection(0)
    }
}
