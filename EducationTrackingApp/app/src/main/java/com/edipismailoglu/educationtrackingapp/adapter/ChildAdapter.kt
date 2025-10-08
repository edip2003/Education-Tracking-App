package com.edipismailoglu.educationtrackingapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.model.Child

class ChildAdapter(private val childList: List<Child>,private val onOptionsClick: (View, Child, Int) -> Unit) : RecyclerView.Adapter<ChildAdapter.ChildViewHolder>() {
    class ChildViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.childNameText)
        val emailText: TextView = view.findViewById(R.id.childEmailText)
        val ageGenderText: TextView = view.findViewById(R.id.childAgeGenderText)
        val optionsMenuIcon: ImageView = view.findViewById(R.id.optionsMenuIcon)
        val stageGradeText = itemView.findViewById<TextView>(R.id.childStageGradeText)


    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_child, parent, false)
        return ChildViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChildViewHolder, position: Int) {
        val child = childList[position]
        holder.nameText.text = child.name
        holder.emailText.text = child.email
        holder.ageGenderText.text = "Age: ${child.age}, Gender: ${child.gender}"
        holder.stageGradeText.text = "Stage: ${child.academicStage} - Grade: ${child.gradeLevel}"

        holder.optionsMenuIcon.setOnClickListener {
            onOptionsClick(it, child,position)
        }
    }

    override fun getItemCount(): Int = childList.size
}
