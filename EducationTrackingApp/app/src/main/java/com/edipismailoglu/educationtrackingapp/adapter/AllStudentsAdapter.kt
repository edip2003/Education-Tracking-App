package com.edipismailoglu.educationtrackingapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.model.StudentWithClass

class AllStudentsAdapter(private val studentList: List<StudentWithClass>) :
    RecyclerView.Adapter<AllStudentsAdapter.StudentViewHolder>() {

    class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.studentNameText)
        val emailText: TextView = itemView.findViewById(R.id.studentEmailText)
        val stageText: TextView = itemView.findViewById(R.id.academicStageText)
        val gradeText: TextView = itemView.findViewById(R.id.gradeLevelText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_all, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = studentList[position]
        holder.nameText.text = student.name
        holder.emailText.text = student.email
        holder.stageText.text = "Stage: ${student.academicStage}"
        holder.gradeText.text = "Grade: ${student.gradeLevel}"
    }

    override fun getItemCount(): Int = studentList.size
}
