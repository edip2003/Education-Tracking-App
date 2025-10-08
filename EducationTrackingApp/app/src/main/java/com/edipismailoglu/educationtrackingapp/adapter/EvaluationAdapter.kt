package com.edipismailoglu.educationtrackingapp.adapter

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.model.Answer
import com.squareup.picasso.Picasso

class EvaluationAdapter(
    private val context: Context,
    private val evaluations: List<Answer>
) : RecyclerView.Adapter<EvaluationAdapter.EvaluationViewHolder>() {

    inner class EvaluationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val teacherEmailText: TextView = view.findViewById(R.id.teacherEmailText)
        val uploadDateText: TextView = view.findViewById(R.id.recyclerDateText)
        val finishDateText: TextView = view.findViewById(R.id.taskFinishDateText)
        val taskImageView: ImageView = view.findViewById(R.id.recyclerImageView)
        val taskCommentText: TextView = view.findViewById(R.id.recyclerCommentText)
        val studentAnswerText: TextView = view.findViewById(R.id.studentAnswerText)
        val answerUploadDateText: TextView = view.findViewById(R.id.answerUploadDateText)
        val answerImageView: ImageView = view.findViewById(R.id.answerImageView)
        val attachedFileName: TextView = view.findViewById(R.id.attachedFileName)
        val evaluationText: TextView = view.findViewById(R.id.evaluationText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EvaluationViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_evaluation_row, parent, false)
        return EvaluationViewHolder(view)
    }

    override fun onBindViewHolder(holder: EvaluationViewHolder, position: Int) {
        val item = evaluations[position]

        val cancelButton = holder.itemView.findViewById<Button>(R.id.cancelButton)
        val sendReportButton = holder.itemView.findViewById<Button>(R.id.sendReportButton)
        cancelButton.visibility = View.GONE
        sendReportButton.visibility = View.GONE

        holder.teacherEmailText.text = "Teacher: ${item.taskOwnerEmail ?: "Unknown"}"
        holder.taskCommentText.text = item.taskDescriptionText ?: "No Comment"
        holder.uploadDateText.text = "Upload Date: ${item.taskUploadDate ?: "-"}"
        holder.finishDateText.text = "Finish Date: ${item.taskFinishDate ?: "-"}"

        val taskImageUrl = item.taskImageUrl
        if (!taskImageUrl.isNullOrEmpty()) {
            holder.taskImageView.visibility = View.VISIBLE
            Picasso.get().load(taskImageUrl).into(holder.taskImageView)
            holder.taskImageView.setOnClickListener {
                showImageFullscreen(taskImageUrl)
            }
        } else {
            holder.taskImageView.visibility = View.GONE
        }

        holder.studentAnswerText.text = "Student Answer: ${item.answerText}"
        holder.answerUploadDateText.text = "Answer Upload Date: ${item.uploadDate ?: "-"}"

        val answerImageUrl = item.imageUrl
        if (!answerImageUrl.isNullOrEmpty()) {
            holder.answerImageView.visibility = View.VISIBLE
            Picasso.get().load(answerImageUrl).into(holder.answerImageView)
            holder.answerImageView.setOnClickListener {
                showImageFullscreen(answerImageUrl)
            }
        } else {
            holder.answerImageView.visibility = View.GONE
        }

        val fileUrl = item.fileUrl
        if (!fileUrl.isNullOrEmpty()) {
            holder.attachedFileName.text = "Attached File: ${fileUrl.substringAfterLast("/")}"
            holder.attachedFileName.visibility = View.VISIBLE
            holder.attachedFileName.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl))
                context.startActivity(intent)
            }
        } else {
            holder.attachedFileName.visibility = View.GONE
        }

        if (item.grade != null) {
            holder.evaluationText.text = "Grade: ${item.grade} / 100"
            holder.evaluationText.setTextColor(
                when {
                    item.grade!! >= 75 -> Color.parseColor("#4CAF50")
                    item.grade!! >= 50 -> Color.parseColor("#FFC107")
                    else -> Color.parseColor("#F44336")
                }
            )
        } else {
            holder.evaluationText.text = "Grade: No grade"
            holder.evaluationText.setTextColor(Color.GRAY)
        }

        holder.itemView.setOnClickListener {
            showEvaluationDialog(item)
        }
    }

    private fun showEvaluationDialog(answer: Answer) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.item_evaluation_row)

        val teacherEmailText = dialog.findViewById<TextView>(R.id.teacherEmailText)
        val taskCommentText = dialog.findViewById<TextView>(R.id.recyclerCommentText)
        val uploadDateText = dialog.findViewById<TextView>(R.id.recyclerDateText)
        val taskFinishDateText = dialog.findViewById<TextView>(R.id.taskFinishDateText)
        val taskImageView = dialog.findViewById<ImageView>(R.id.recyclerImageView)

        val answerUploadDateText = dialog.findViewById<TextView>(R.id.answerUploadDateText)
        val studentAnswerText = dialog.findViewById<TextView>(R.id.studentAnswerText)
        val answerImageView = dialog.findViewById<ImageView>(R.id.answerImageView)
        val attachedFileName = dialog.findViewById<TextView>(R.id.attachedFileName)
        val evaluationText = dialog.findViewById<TextView>(R.id.evaluationText)

        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)
        val sendReportButton = dialog.findViewById<Button>(R.id.sendReportButton)

        cancelButton.visibility = View.GONE
        sendReportButton.visibility = View.GONE

        teacherEmailText.text = "Teacher: ${answer.taskOwnerEmail ?: "Unknown"}"
        taskCommentText.text = answer.taskDescriptionText ?: "No Comment"
        uploadDateText.text = "Upload Date: ${answer.taskUploadDate ?: "-"}"
        taskFinishDateText.text = "Finish Date: ${answer.taskFinishDate ?: "-"}"

        val taskImageUrl = answer.taskImageUrl
        if (!taskImageUrl.isNullOrEmpty()) {
            taskImageView.visibility = View.VISIBLE
            Picasso.get().load(taskImageUrl).into(taskImageView)
            taskImageView.setOnClickListener {
                showImageFullscreen(taskImageUrl)
            }
        } else {
            taskImageView.visibility = View.GONE
        }

        answerUploadDateText.text = "Answer Upload Date: ${answer.uploadDate ?: "-"}"
        studentAnswerText.text = "Student Answer: ${answer.answerText}"

        val answerImageUrl = answer.imageUrl
        if (!answerImageUrl.isNullOrEmpty()) {
            answerImageView.visibility = View.VISIBLE
            Picasso.get().load(answerImageUrl).into(answerImageView)
            answerImageView.setOnClickListener {
                showImageFullscreen(answerImageUrl)
            }
        } else {
            answerImageView.visibility = View.GONE
        }

        val fileUrl = answer.fileUrl
        if (!fileUrl.isNullOrEmpty()) {
            attachedFileName.text = "Attached File: ${fileUrl.substringAfterLast("/")}"
            attachedFileName.visibility = View.VISIBLE
            attachedFileName.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl))
                context.startActivity(intent)
            }
        } else {
            attachedFileName.visibility = View.GONE
        }

        if (answer.grade != null) {
            evaluationText.text = "Grade: ${answer.grade} / 100"
            evaluationText.setTextColor(
                when {
                    answer.grade!! >= 75 -> Color.parseColor("#4CAF50")
                    answer.grade!! >= 50 -> Color.parseColor("#FFC107")
                    else -> Color.parseColor("#F44336")
                }
            )
        } else {
            evaluationText.text = "Grade: No grade"
            evaluationText.setTextColor(Color.GRAY)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showImageFullscreen(imageUrl: String) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_image_preview)
        val fullImage = dialog.findViewById<ImageView>(R.id.fullscreenImage)
        Picasso.get().load(imageUrl).into(fullImage)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun getItemCount(): Int = evaluations.size
}
