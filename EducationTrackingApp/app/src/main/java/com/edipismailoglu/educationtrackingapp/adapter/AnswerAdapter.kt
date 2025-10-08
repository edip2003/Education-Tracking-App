package com.edipismailoglu.educationtrackingapp.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.model.Answer
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import com.itextpdf.text.Document
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter
import com.itextpdf.text.Font
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.Anchor
import com.itextpdf.text.Chunk
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.os.Handler
import android.os.Looper
import android.app.Dialog
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage

class AnswerAdapter(private val answerList: MutableList<Answer>) :
    RecyclerView.Adapter<AnswerAdapter.AnswerHolder>() {

    inner class AnswerHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val emailText: TextView = itemView.findViewById(R.id.emailText)
        val answerText: TextView = itemView.findViewById(R.id.answerText)
        val taskDescriptionText: TextView = itemView.findViewById(R.id.taskDescriptionText)
        val answerImageView: ImageView = itemView.findViewById(R.id.answerImageView)
        val attachedFileName: TextView = itemView.findViewById(R.id.attachedFileName)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
        val gradeValueText: TextView = itemView.findViewById(R.id.gradeValueText)
        val gradeProgressBar: ProgressBar = itemView.findViewById(R.id.gradeProgressBar)
        val gradeText: TextView = itemView.findViewById(R.id.gradeText)
        val gradeButton: Button = itemView.findViewById(R.id.gradeButton)
        val optionsIcon: ImageView = itemView.findViewById(R.id.optionsMenuIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnswerHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.answer_row, parent, false)
        return AnswerHolder(view)
    }

    override fun onBindViewHolder(holder: AnswerHolder, position: Int) {
        val answer = answerList[position]
        val context = holder.itemView.context

        holder.emailText.text = context.getString(R.string.student_email, answer.studentEmail)
        holder.answerText.text = context.getString(R.string.answer, answer.answerText)
        holder.taskDescriptionText.text = answer.taskDescriptionText ?: "-"

        // Format date
        holder.dateText.text = formatDate(answer.uploadDate, context)

        // Set grade information
        val grade = answer.grade ?: 0
        holder.gradeValueText.text = context.getString(R.string.grade_value, if (answer.grade != null) "$grade/100" else "-")
        holder.gradeProgressBar.progress = grade
        holder.gradeText.text = context.getString(R.string.grade_value, "$grade/100")

        // Handle image
        if (!answer.imageUrl.isNullOrEmpty()) {
            holder.answerImageView.visibility = View.VISIBLE
            Picasso.get().load(answer.imageUrl).error(R.drawable.error_placeholder).into(holder.answerImageView)
        } else {
            holder.answerImageView.visibility = View.GONE
        }

        // Handle attached file
        answer.fileUrl?.let { url ->
            holder.attachedFileName.visibility = View.VISIBLE
            val fileName = url.substringAfterLast("/")
            holder.attachedFileName.text = context.getString(R.string.attached_file, fileName)
            holder.attachedFileName.setOnClickListener { downloadAndOpenFile(context, url) }
        } ?: run {
            holder.attachedFileName.visibility = View.GONE
        }

        // Image click listener
        holder.answerImageView.setOnClickListener {
            showImageDialog(context, answer.imageUrl)
        }

        // Set progress bar color
        holder.gradeProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(getGradeColor(grade))
        )

        holder.gradeButton.setOnClickListener {
            showGradeDialog(context, answer)
        }

        // Options menu
        holder.optionsIcon.setOnClickListener { view ->
            val popup = PopupMenu(context, view)
            popup.menuInflater.inflate(R.menu.answer_menu, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_edit -> {
                        showViewMyAnswerDialog(context, answer)
                        true
                    }

                    R.id.menu_delete -> {
                        confirmDelete(context, answer, position)
                        true
                    }
                    R.id.menu_view_evaluation -> {
                        showEvaluationDialog(context, answer)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = answerList.size

    private fun formatDate(dateString: String?, context: Context): String {
        if (dateString.isNullOrEmpty()) return context.getString(R.string.upload_date, "-")
        return try {
            val inputFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss 'GMT'XXX yyyy", Locale.US)
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val date = inputFormat.parse(dateString)
            context.getString(R.string.upload_date, date?.let { outputFormat.format(it) } ?: dateString)
        } catch (e: Exception) {
            context.getString(R.string.upload_date, dateString)
        }
    }

    private fun getGradeColor(grade: Int): String = when {
        grade > 70 -> "#4CAF50"
        grade in 50..70 -> "#FFC107"
        grade < 50 -> "#F44336"
        else -> "#888888"
    }

    private fun showImageDialog(context: Context, imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) return
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_image_preview)
        val fullImage = dialog.findViewById<ImageView>(R.id.fullscreenImage)
        Picasso.get().load(imageUrl).error(R.drawable.error_placeholder).into(fullImage)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun downloadAndOpenFile(context: Context, fileUrl: String) {
        val fileName = fileUrl.substringAfterLast("/")
        val client = OkHttpClient()

        val request = Request.Builder().url(fileUrl).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, context.getString(R.string.file_load_failed), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, context.getString(R.string.file_load_failed), Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                response.body?.let { body ->
                    try {
                        val file = File(context.cacheDir, fileName)
                        file.outputStream().use { output ->
                            body.byteStream().use { input ->
                                input.copyTo(output)
                            }
                        }

                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, getMimeType(fileName))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        Handler(Looper.getMainLooper()).post {
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.no_suitable_app), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, context.getString(R.string.file_load_failed), Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        response.body?.close()
                    }
                }
            }
        })
    }

    private fun showViewMyAnswerDialog(context: Context, answer: Answer) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_view_my_answer, null)
        val alertDialog = AlertDialog.Builder(context).setView(dialogView).create()

        val answerTextView = dialogView.findViewById<EditText>(R.id.answerTextView)
        val imagePreview = dialogView.findViewById<ImageView>(R.id.imagePreview)
        val fileNameTextView = dialogView.findViewById<TextView>(R.id.fileNameTextView)
        val editButton = dialogView.findViewById<Button>(R.id.editButton)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

        answerTextView.setText(answer.answerText)

        if (!answer.imageUrl.isNullOrEmpty()) {
            Picasso.get().load(answer.imageUrl).into(imagePreview)
            imagePreview.visibility = View.VISIBLE
        } else {
            imagePreview.visibility = View.GONE
        }

        val fileUrl = answer.fileUrl
        if (!fileUrl.isNullOrEmpty()) {
            val fileName = fileUrl.substringAfterLast("/")
            fileNameTextView.text = fileName
            fileNameTextView.visibility = View.VISIBLE
            fileNameTextView.setOnClickListener {
                downloadAndOpenFile(context, fileUrl)
            }
        }
        else {
            fileNameTextView.visibility = View.GONE
        }

        editButton.setOnClickListener {
            alertDialog.dismiss()
            showEditAnswerDialog(context, answer)
        }

        closeButton.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }


    private fun showEditAnswerDialog(context: Context, answer: Answer) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_submit_answer, null)
        val alertDialog = AlertDialog.Builder(context).setView(dialogView).create()

        val answerText = dialogView.findViewById<EditText>(R.id.answerEditText)
        val uploadImageButton = dialogView.findViewById<Button>(R.id.uploadImageButton)
        val uploadFileButton = dialogView.findViewById<Button>(R.id.uploadFileButton)
        val imagePreview = dialogView.findViewById<ImageView>(R.id.imagePreview)
        val selectedFileName = dialogView.findViewById<TextView>(R.id.selectedFileName)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.uploadProgressBar)
        val submitButton = dialogView.findViewById<Button>(R.id.submitButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)

        answerText.setText(answer.answerText)

        if (!answer.imageUrl.isNullOrEmpty()) {
            Picasso.get().load(answer.imageUrl).into(imagePreview)
            imagePreview.visibility = View.VISIBLE
        }
        val currentFileUrl = answer.fileUrl
        if (!currentFileUrl.isNullOrEmpty()) {
            val fileName = currentFileUrl.substringAfterLast("/")
            selectedFileName.text = fileName
            selectedFileName.visibility = View.VISIBLE
            selectedFileName.setOnClickListener {
                downloadAndOpenFile(context, currentFileUrl)
            }
        }


        var selectedImageUri: Uri? = null
        var selectedFileUri: Uri? = null

        val imagePicker = (context as? androidx.fragment.app.FragmentActivity)?.activityResultRegistry
            ?.register("edit_image_picker", androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
                uri?.let {
                    selectedImageUri = it
                    imagePreview.setImageURI(it)
                    imagePreview.visibility = View.VISIBLE
                }
            }

        val filePicker = (context as? androidx.fragment.app.FragmentActivity)?.activityResultRegistry
            ?.register("edit_file_picker", androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
                uri?.let {
                    selectedFileUri = it
                    val fileName = uri.lastPathSegment ?: "Selected File"
                    selectedFileName.text = fileName
                    selectedFileName.visibility = View.VISIBLE
                }
            }

        uploadImageButton.setOnClickListener {
            imagePicker?.launch("image/*")
        }

        uploadFileButton.setOnClickListener {
            filePicker?.launch("*/*")
        }

        submitButton.setOnClickListener {
            val updatedText = answerText.text.toString().trim()
            if (updatedText.isEmpty() && selectedImageUri == null && selectedFileUri == null) {
                Toast.makeText(context, "Please enter an answer or select files", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            submitButton.isEnabled = false
            cancelButton.isEnabled = false

            val db = FirebaseFirestore.getInstance()
            val storage = FirebaseStorage.getInstance()

            fun updateFirestore(imageUrl: String?, fileUrl: String?) {
                db.collection("Submissions")
                    .whereEqualTo("taskId", answer.taskId)
                    .whereEqualTo("studentEmail", answer.studentEmail)
                    .get()
                    .addOnSuccessListener { docs ->
                        if (docs.isEmpty) {
                            Toast.makeText(context, "Answer not found", Toast.LENGTH_SHORT).show()
                            alertDialog.dismiss()
                            return@addOnSuccessListener
                        }
                        for (doc in docs) {
                            val updates = mutableMapOf<String, Any?>(
                                "answerText" to updatedText
                            )
                            imageUrl?.let { updates["imageUrl"] = it }
                            fileUrl?.let { updates["fileUrl"] = it }

                            db.collection("Submissions").document(doc.id)
                                .update(updates)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Answer updated", Toast.LENGTH_SHORT).show()
                                    alertDialog.dismiss()
                                    notifyDataSetChanged()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Failed to update answer", Toast.LENGTH_SHORT).show()
                                    progressBar.visibility = View.GONE
                                    submitButton.isEnabled = true
                                    cancelButton.isEnabled = true
                                }
                        }
                    }
            }

            if (selectedImageUri != null) {
                val imageRef = storage.reference.child("submissions/images/${UUID.randomUUID()}.jpg")
                imageRef.putFile(selectedImageUri!!)
                    .addOnSuccessListener {
                        imageRef.downloadUrl.addOnSuccessListener { imageUrl ->
                            if (selectedFileUri != null) {
                                val fileRef = storage.reference.child("submissions/files/${UUID.randomUUID()}")
                                fileRef.putFile(selectedFileUri!!)
                                    .addOnSuccessListener {
                                        fileRef.downloadUrl.addOnSuccessListener { fileUrl ->
                                            updateFirestore(imageUrl.toString(), fileUrl.toString())
                                        }
                                    }
                            } else {
                                updateFirestore(imageUrl.toString(), null)
                            }
                        }
                    }
            } else if (selectedFileUri != null) {
                val fileRef = storage.reference.child("submissions/files/${UUID.randomUUID()}")
                fileRef.putFile(selectedFileUri!!)
                    .addOnSuccessListener {
                        fileRef.downloadUrl.addOnSuccessListener { fileUrl ->
                            updateFirestore(null, fileUrl.toString())
                        }
                    }
            } else {
                updateFirestore(null, null)
            }
        }

        cancelButton.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }


    private fun updateAnswer(context: Context, answer: Answer, newText: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("Submissions")
            .whereEqualTo("taskId", answer.taskId)
            .whereEqualTo("studentEmail", answer.studentEmail)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(context, context.getString(R.string.update_failed), Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                for (doc in documents) {
                    db.collection("Submissions").document(doc.id)
                        .update("answerText", newText)
                        .addOnSuccessListener {
                            Toast.makeText(context, context.getString(R.string.answer_updated), Toast.LENGTH_SHORT).show()
                            answer.answerText = newText
                            notifyDataSetChanged()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, context.getString(R.string.update_failed), Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, context.getString(R.string.update_failed), Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDelete(context: Context, answer: Answer, position: Int) {
        AlertDialog.Builder(context)
            .setTitle(R.string.confirm_deletion)
            .setMessage(context.getString(R.string.delete_confirmation, answer.studentEmail))
            .setPositiveButton(R.string.yes) { _, _ ->
                val db = FirebaseFirestore.getInstance()
                db.collection("Submissions")
                    .whereEqualTo("taskId", answer.taskId)
                    .whereEqualTo("studentEmail", answer.studentEmail)
                    .get()
                    .addOnSuccessListener { docs ->
                        if (docs.isEmpty) {
                            Toast.makeText(context, context.getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }
                        for (doc in docs) {
                            db.collection("Submissions").document(doc.id).delete()
                                .addOnSuccessListener {
                                    Toast.makeText(context, context.getString(R.string.delete_success), Toast.LENGTH_SHORT).show()
                                    answerList.removeAt(position)
                                    notifyItemRemoved(position)
                                    notifyItemRangeChanged(position, answerList.size)
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, context.getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, context.getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showGradeDialog(context: Context, answer: Answer) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(context.getString(R.string.enter_grade, answer.studentEmail))

        val input = EditText(context)
        input.hint = context.getString(R.string.grade_hint)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        builder.setView(input)

        builder.setPositiveButton(R.string.submit) { _, _ ->
            val gradeInput = input.text.toString().toIntOrNull()
            if (gradeInput != null && gradeInput in 0..100) {
                updateGrade(context, answer, gradeInput)
            } else {
                Toast.makeText(context, context.getString(R.string.invalid_grade), Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    private fun updateGrade(context: Context, answer: Answer, gradeInput: Int) {
        val db = FirebaseFirestore.getInstance()
        db.collection("Submissions")
            .whereEqualTo("taskId", answer.taskId)
            .whereEqualTo("studentEmail", answer.studentEmail)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(context, context.getString(R.string.update_failed), Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                for (doc in documents) {
                    db.collection("Submissions").document(doc.id)
                        .update("grade", gradeInput)
                        .addOnSuccessListener {
                            Toast.makeText(context, context.getString(R.string.grade_updated), Toast.LENGTH_SHORT).show()
                            answer.grade = gradeInput
                            notifyDataSetChanged()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, context.getString(R.string.update_failed), Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, context.getString(R.string.update_failed), Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEvaluationDialog(context: Context, answer: Answer) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.item_evaluation_row)

        val answerImageView = dialog.findViewById<ImageView>(R.id.answerImageView)
        val taskImageView = dialog.findViewById<ImageView>(R.id.recyclerImageView)
        val attachedFileText = dialog.findViewById<TextView>(R.id.attachedFileName)
        val evaluationText = dialog.findViewById<TextView>(R.id.evaluationText)
        val studentAnswerText = dialog.findViewById<TextView>(R.id.studentAnswerText)
        val commentText = dialog.findViewById<TextView>(R.id.recyclerCommentText)
        val uploadDateText = dialog.findViewById<TextView>(R.id.recyclerDateText)
        val answerUploadDateText = dialog.findViewById<TextView>(R.id.answerUploadDateText)
        val taskFinishDateText = dialog.findViewById<TextView>(R.id.taskFinishDateText)
        val sendReportButton = dialog.findViewById<Button>(R.id.sendReportButton)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)

        studentAnswerText.text = context.getString(R.string.student_answer, answer.answerText)
        evaluationText.text = context.getString(R.string.evaluation, answer.grade?.toString() ?: "-")
        commentText.text = context.getString(R.string.description, answer.taskDescriptionText ?: "-")
        uploadDateText.text = formatDate(answer.taskUploadDate, context)
        answerUploadDateText?.text = formatDate(answer.uploadDate, context)
        taskFinishDateText?.text = context.getString(R.string.finish_date, answer.taskFinishDate ?: "-")

        // Handle images
        if (!answer.imageUrl.isNullOrEmpty()) {
            answerImageView.visibility = View.VISIBLE
            Picasso.get().load(answer.imageUrl).error(R.drawable.error_placeholder).into(answerImageView)
            answerImageView.setOnClickListener { showImageDialog(context, answer.imageUrl) }
        } else {
            answerImageView.visibility = View.GONE
        }

        if (!answer.taskImageUrl.isNullOrEmpty()) {
            taskImageView.visibility = View.VISIBLE
            Picasso.get().load(answer.taskImageUrl).error(R.drawable.error_placeholder).into(taskImageView)
            taskImageView.setOnClickListener { showImageDialog(context, answer.taskImageUrl) }
        } else {
            taskImageView.visibility = View.GONE
        }

        // Handle attached file
        answer.fileUrl?.let { url ->
            val fileName = url.substringAfterLast("/")
            attachedFileText.text = context.getString(R.string.attached_file, fileName)
            attachedFileText.visibility = View.VISIBLE
            attachedFileText.setOnClickListener { downloadAndOpenFile(context, url) }
        } ?: run {
            attachedFileText.visibility = View.GONE
        }

        sendReportButton.setOnClickListener {
            generatePdfAndSend(context, answer)
            dialog.dismiss()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast(".", "").lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "txt" -> "text/plain"
            else -> "*/*"
        }
    }

    private fun generatePdfAndSend(context: Context, answer: Answer) {
        var outputStream: FileOutputStream? = null
        var writer: PdfWriter? = null
        val document = Document()
        try {
            val fileName = "report_${answer.studentEmail}_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, fileName)
            outputStream = FileOutputStream(file)
            writer = PdfWriter.getInstance(document, outputStream)
            document.open()

            val titleFont = Font(Font.FontFamily.HELVETICA, 18f, Font.BOLD)
            val headerFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD)
            val normalFont = Font(Font.FontFamily.HELVETICA, 12f, Font.NORMAL)
            val linkFont = Font(Font.FontFamily.HELVETICA, 12f, Font.UNDERLINE, com.itextpdf.text.BaseColor.BLUE)

            fun addSection(title: String) {
                val p = Paragraph("\n$title\n", headerFont)
                p.spacingBefore = 10f
                p.spacingAfter = 5f
                document.add(p)
            }

            fun addText(label: String, value: String) {
                document.add(Paragraph("$label $value", normalFont))
            }

            fun addLink(label: String, url: String?) {
                if (!url.isNullOrEmpty()) {
                    document.add(Paragraph("$label:", normalFont))
                    val link = Anchor(Chunk(url, linkFont)).apply { reference = url }
                    document.add(link)
                } else {
                    addText(label, "No URL available")
                }
            }

            // Title
            document.add(Paragraph("📄 Student Evaluation Report\n\n", titleFont))

            // Sections
            addSection("👦 Student Info")
            addText("Email:", answer.studentEmail)

            addSection("📘 Task Info")
            addText("Description:", answer.taskDescriptionText ?: "-")
            addText("Upload Date:", formatDate(answer.taskUploadDate, context).substringAfter(": "))
            addText("Finish Date:", answer.taskFinishDate ?: "-")
            addLink("Task Image URL", answer.taskImageUrl)

            addSection("✍️ Student Answer")
            addText("Answer Text:", answer.answerText.ifBlank { "-" })
            addText("Answer Date:", formatDate(answer.uploadDate, context).substringAfter(": "))
            addLink("Answer Image URL", answer.imageUrl)

            addSection("📎 Attached File")
            addLink("File URL", answer.fileUrl)

            addSection("✅ Evaluation")
            addText("Grade:", "${answer.grade ?: "-"} / 100")

            document.close()

            // Send
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Student Evaluation Report")
                putExtra(Intent.EXTRA_TEXT, "Evaluation report for ${answer.studentEmail}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Send Report"))
            Toast.makeText(context, "PDF report generated and ready to send", Toast.LENGTH_SHORT).show()

            Handler(Looper.getMainLooper()).postDelayed({ file.delete() }, 60000)

        } catch (e: Exception) {
            Toast.makeText(context, "PDF generation failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            if (document.isOpen) document.close()
            writer?.close()
            outputStream?.close()
        }
    }

}