package com.example.raw_pesms.UI.student

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    private lateinit var progressBar: ProgressBar

    private var lastInsertedStudentId: String? = null
    private var selectedGrade: String? = null
    private var selectedSection: String? = null
    private var isEnrollmentInProgress = false

    private val viewModel: AttendanceViewModel by activityViewModels { AttendanceViewModelFactory(requireActivity().application) }

    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startCameraForEnrollment()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required for face enrollment", Toast.LENGTH_LONG).show()
        }
    }

    // Camera launcher for enrollment
    private val enrollLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        handleCameraResult(bitmap)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_add_student, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupSpinners()
        setupClickListeners()
    }

    private fun initializeViews(view: View) {
        firstNameInput = view.findViewById(R.id.editFirstName)
        middleInitialInput = view.findViewById(R.id.editMiddleInitial)
        lastNameInput = view.findViewById(R.id.editLastName)
        gradeSpinner = view.findViewById(R.id.spinnerGrade)
        sectionSpinner = view.findViewById(R.id.spinnerSection)
        addButton = view.findViewById(R.id.btnAddStudent)
        enrollButton = view.findViewById(R.id.btnEnrollFace)

        // Add progress bar if it exists in your layout
        progressBar = view.findViewById<ProgressBar>(R.id.progressBar) ?: run {
            // Create programmatically if not in layout
            ProgressBar(requireContext()).apply {
                visibility = View.GONE
                isIndeterminate = true
            }
        }

        // Initially hide the enroll button
        enrollButton.visibility = View.GONE
    }

    private fun setupSpinners() {
        // Load dynamic folders
        viewModel.loadGrades()
        viewModel.grades.observe(viewLifecycleOwner) { grades ->
            val list = listOf("Select Grade") + grades
            gradeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, list)
        }

        gradeSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                selectedGrade = parent.getItemAtPosition(pos).toString().takeIf { it != "Select Grade" }
                if (selectedGrade != null) {
                    viewModel.loadSections(selectedGrade!!)
                } else {
                    // Clear sections if no grade selected
                    sectionSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("Select Section"))
                    selectedSection = null
                }
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
    }

    private fun setupClickListeners() {
        addButton.setOnClickListener {
            addStudent()
        }

        enrollButton.setOnClickListener {
            if (isEnrollmentInProgress) {
                Toast.makeText(requireContext(), "Enrollment already in progress...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (lastInsertedStudentId == null) {
                Toast.makeText(requireContext(), "Please add student first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Double-check spinner selections before proceeding
            val currentGradeSelection = gradeSpinner.selectedItem?.toString()
            val currentSectionSelection = sectionSpinner.selectedItem?.toString()

            if (currentGradeSelection == "Select Grade" || currentGradeSelection == null) {
                Toast.makeText(requireContext(), "Please select a grade first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentSectionSelection == "Select Section" || currentSectionSelection == null) {
                Toast.makeText(requireContext(), "Please select a section first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Update the selected values from spinners
            selectedGrade = currentGradeSelection
            selectedSection = currentSectionSelection

            android.util.Log.d("AddStudent", "Enroll button clicked - Grade: $selectedGrade, Section: $selectedSection, StudentId: $lastInsertedStudentId")

            // Check camera permission
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCameraForEnrollment()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun addStudent() {
        val first = firstNameInput.text.toString().trim()
        val mid = middleInitialInput.text.toString().trim()
        val last = lastNameInput.text.toString().trim()
        val grade = selectedGrade
        val section = selectedSection

        // Validation
        if (first.isEmpty() || last.isEmpty()) {
            Toast.makeText(requireContext(), "First name and last name are required", Toast.LENGTH_SHORT).show()
            return
        }

        if (grade == null || section == null) {
            Toast.makeText(requireContext(), "Please select grade and section", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading
        addButton.isEnabled = false
        addButton.text = "Adding..."

        // Firestore data map
        val studentData = hashMapOf(
            "firstName" to first,
            "middleInitial" to mid,
            "lastName" to last,
            "grade" to grade,
            "section" to section,
            "ownerId" to currentUser.uid,
            "faceEnrolled" to false,
            "createdAt" to com.google.firebase.Timestamp.now()
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
                Toast.makeText(requireContext(), "Student added successfully! Now you can enroll their face.", Toast.LENGTH_LONG).show()
                enrollButton.visibility = View.VISIBLE
                enrollButton.text = "📸 Enroll Face"
                clearInputs()

                // Reset button
                addButton.isEnabled = true
                addButton.text = "Add Student"
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error adding student: ${e.message}", Toast.LENGTH_LONG).show()
                android.util.Log.e("AddStudent", "Error adding student", e)

                // Reset button
                addButton.isEnabled = true
                addButton.text = "Add Student"
            }
    }

    private fun startCameraForEnrollment() {
        try {
            Toast.makeText(requireContext(), "Position the student's face in the camera frame", Toast.LENGTH_LONG).show()
            enrollLauncher.launch(null)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error opening camera: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("AddStudent", "Error opening camera", e)
        }
    }

    private fun handleCameraResult(bitmap: Bitmap?) {
        val studentId = lastInsertedStudentId
        val grade = selectedGrade
        val section = selectedSection

        // Enhanced validation with logging
        android.util.Log.d("AddStudent", "handleCameraResult - studentId: $studentId, grade: $grade, section: $section")

        if (studentId == null) {
            Toast.makeText(requireContext(), "Missing student ID. Please add a student first.", Toast.LENGTH_SHORT).show()
            android.util.Log.e("AddStudent", "Student ID is null")
            return
        }

        if (grade == null) {
            Toast.makeText(requireContext(), "Missing grade information. Please select a grade.", Toast.LENGTH_SHORT).show()
            android.util.Log.e("AddStudent", "Grade is null")
            return
        }

        if (section == null) {
            Toast.makeText(requireContext(), "Missing section information. Please select a section.", Toast.LENGTH_SHORT).show()
            android.util.Log.e("AddStudent", "Section is null")
            return
        }

        if (bitmap == null) {
            Toast.makeText(requireContext(), "No image captured. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }

        isEnrollmentInProgress = true
        enrollButton.isEnabled = false
        enrollButton.text = "Processing..."
        progressBar.visibility = View.VISIBLE

        val manager = FaceRecognitionManager(requireContext())

        lifecycleScope.launch {
            try {
                // Detect faces in the image
                val faces = manager.detectFaces(bitmap)

                if (faces.isEmpty()) {
                    Toast.makeText(requireContext(), "No face detected in the image. Please try again with better lighting.", Toast.LENGTH_LONG).show()
                    resetEnrollmentUI()
                    return@launch
                }

                if (faces.size > 1) {
                    Toast.makeText(requireContext(), "Multiple faces detected. Please ensure only one person is in the frame.", Toast.LENGTH_LONG).show()
                    resetEnrollmentUI()
                    return@launch
                }

                // Get face embedding
                val face = faces.first()
                val embedding = manager.embed(face)

                if (embedding == null) {
                    Toast.makeText(requireContext(), "Failed to process face. Please try again.", Toast.LENGTH_SHORT).show()
                    resetEnrollmentUI()
                    return@launch
                }

                val embeddingString = embedding.joinToString(",")

                // Enroll the face
                viewModel.enrollStudentFace(studentId, grade, section, embeddingString, null)

                // Update Firestore to mark face as enrolled
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("owners")
                        .document(currentUser.uid)
                        .collection("grades")
                        .document(grade)
                        .collection("sections")
                        .document(section)
                        .collection("students")
                        .document(studentId)
                        .update("faceEnrolled", true)
                }

                Toast.makeText(requireContext(), "✅ Face enrolled successfully!", Toast.LENGTH_LONG).show()

                // Hide enroll button and reset state
                enrollButton.visibility = View.GONE
                lastInsertedStudentId = null
                selectedGrade = null
                selectedSection = null

            } catch (e: Exception) {
                android.util.Log.e("AddStudent", "Error during face enrollment", e)

                val errorMessage = when {
                    e.message?.contains("facenet.tflite") == true ||
                            e.message?.contains("tflite") == true -> {
                        "Face recognition model not found. Please ensure facenet.tflite is in assets folder."
                    }
                    e.message?.contains("TensorFlow") == true -> {
                        "TensorFlow Lite initialization failed. Please check model files."
                    }
                    e.message?.contains("assets") == true -> {
                        "Model file missing from assets. Please add facenet.tflite to app/src/main/assets/"
                    }
                    else -> "Error during face enrollment: ${e.message}"
                }

                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            } finally {
                resetEnrollmentUI()
            }
        }
    }

    private fun resetEnrollmentUI() {
        isEnrollmentInProgress = false
        enrollButton.isEnabled = true
        enrollButton.text = "📸 Enroll Face"
        progressBar.visibility = View.GONE
    }

    private fun clearInputs() {
        firstNameInput.text.clear()
        middleInitialInput.text.clear()
        lastNameInput.text.clear()
        gradeSpinner.setSelection(0)
        sectionSpinner.setSelection(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Reset state when fragment is destroyed
        lastInsertedStudentId = null
        isEnrollmentInProgress = false
        // Don't reset selectedGrade and selectedSection as they're managed by spinners
    }
}