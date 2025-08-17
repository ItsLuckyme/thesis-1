package com.example.raw_pesms.UI.student

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import com.example.raw_pesms.R

class StudentAdapter(private val studentNames: MutableList<String>) : BaseAdapter() {

    private val selectedPositions = mutableSetOf<Int>()
    var deleteMode = false
        private set

    override fun getCount(): Int = studentNames.size

    override fun getItem(position: Int): String = studentNames[position]

    override fun getItemId(position: Int): Long = position.toLong()

    fun toggleDeleteMode() {
        deleteMode = !deleteMode
        if (!deleteMode) {
            selectedPositions.clear()
        }
        notifyDataSetChanged()
    }

    fun getSelectedNames(): List<String> =
        selectedPositions.map { studentNames[it] }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView ?: LayoutInflater.from(parent?.context).inflate(R.layout.item_student, parent, false)
        val checkBox = view.findViewById<CheckBox>(R.id.checkBoxSelect)
        val textView = view.findViewById<TextView>(R.id.textViewName)

        textView.text = studentNames[position]

        if (deleteMode) {
            checkBox.isVisible = true
            checkBox.isChecked = selectedPositions.contains(position)

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedPositions.add(position)
                } else {
                    selectedPositions.remove(position)
                }
            }

            // Also allow clicking the whole row to toggle checkbox
            view.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }
        } else {
            checkBox.isVisible = false
            // Disable click when not in delete mode
            view.setOnClickListener(null)
        }

        return view
    }
}
