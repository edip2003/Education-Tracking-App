package com.edipismailoglu.educationtrackingapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.model.ClassModel
import java.text.SimpleDateFormat
import java.util.*

class ClassAdapter(
    private val classList: List<ClassModel>,
    private val onUploadClick: (ClassModel) -> Unit,
    private val onShowFinishedClick: (ClassModel) -> Unit,
    private val onViewStudentsClick: (ClassModel) -> Unit,
    private val onEditClick: (ClassModel, String) -> Unit,
    private val onDeleteClick: (ClassModel) -> Unit,
    private val onViewTasksClick: (ClassModel) -> Unit
) : RecyclerView.Adapter<ClassAdapter.ClassViewHolder>() {

    class ClassViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val stageText: TextView = itemView.findViewById(R.id.classStageText)
        val gradeText: TextView = itemView.findViewById(R.id.classGradeLevelText)
        val createdAtText: TextView = itemView.findViewById(R.id.classCreatedAtText)
        val uploadButton: Button = itemView.findViewById(R.id.uploadTaskButton)
        val showFinishedButton: Button = itemView.findViewById(R.id.showFinishedTasksButton)
        val optionsMenuIcon: View = itemView.findViewById(R.id.optionsMenuIcon)
        // إضافة مرجع لـ viewTasksButton إذا كنت تريد استخدامه كزر منفصل
        val viewTasksButton: Button = itemView.findViewById(R.id.viewTasksButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_class, parent, false)
        return ClassViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        val cls = classList[position]
        holder.stageText.text = "Stage: ${cls.academicStage}"
        holder.gradeText.text = "Grade: ${cls.gradeLevel}"
        holder.createdAtText.text = "Created: ${
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(cls.createdAt))
        }"

        // ✅ زر "Upload Task"
        holder.uploadButton.setOnClickListener {
            onUploadClick(cls)
        }

        // ✅ زر "Show Finished Tasks"
        holder.showFinishedButton.setOnClickListener {
            onShowFinishedClick(cls)
        }

        // ✅ القائمة المنبثقة لعرض الخيارات
        holder.optionsMenuIcon.setOnClickListener {
            val popup = PopupMenu(holder.itemView.context, holder.optionsMenuIcon)
            popup.menuInflater.inflate(R.menu.class_popup_menu, popup.menu)

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.viewClassTasksMenu -> {
                        onViewTasksClick(cls) // ✅ دالة لعرض المهام
                        true
                    }
                    R.id.editClassMenu -> {
                        onEditClick(cls, cls.id) // ✅ تمرير cls وـ cls.id
                        true
                    }
                    R.id.deleteClassMenu -> {
                        onDeleteClick(cls) // ✅ دالة لحذف الصف
                        true
                    }
                    R.id.viewStudentsMenu -> {
                        onViewStudentsClick(cls) // ✅ دالة لعرض الطلاب
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }

        // إذا كنت تريد استخدام زر منفصل لـ "View Tasks" بدلاً من القائمة المنبثقة
         holder.viewTasksButton.setOnClickListener {
             onViewTasksClick(cls)
         }
    }

    override fun getItemCount(): Int = classList.size
}