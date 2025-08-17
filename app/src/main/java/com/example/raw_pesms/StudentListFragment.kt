package com.example.raw_pesms.UI.student

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.raw_pesms.R
import com.example.raw_pesms.data.model.Student
import com.example.raw_pesms.viewmodel.AttendanceViewModel
import com.example.raw_pesms.viewmodel.AttendanceViewModelFactory

class StudentListFragment : Fragment() {

    private lateinit var list: ListView
    private lateinit var btnDelete: Button
    private lateinit var adapter: ArrayAdapter<String>

    private var grade: String? = null
    private var section: String? = null

    private var students: List<Student> = emptyList()

    private val vm: AttendanceViewModel by activityViewModels { AttendanceViewModelFactory(requireActivity().application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            grade = it.getString("grade")
            section = it.getString("section")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_student_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        list = view.findViewById(R.id.listViewStudents)
        btnDelete = view.findViewById(R.id.btnDelete)

        val g = grade; val s = section
        if (g == null || s == null) {
            Toast.makeText(requireContext(), "Grade/section not provided", Toast.LENGTH_SHORT).show()
            return
        }

        vm.loadStudents(g, s)
        vm.students.observe(viewLifecycleOwner) { st ->
            students = st
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, st.map { it.fullName() })
            list.adapter = adapter
        }

        list.setOnItemClickListener { _, _, pos, _ ->
            Toast.makeText(requireContext(), students[pos].fullName(), Toast.LENGTH_SHORT).show()
        }

        btnDelete.setOnClickListener {
            val pos = list.checkedItemPosition
            if (pos != ListView.INVALID_POSITION) {
                val st = students[pos]
                vm.deleteStudent(st.grade, st.section, st.id)
            } else {
                Toast.makeText(requireContext(), "Tap an item first", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
