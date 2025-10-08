package com.edipismailoglu.educationtrackingapp.adapter

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.databinding.RecycleRowBinding
import com.edipismailoglu.educationtrackingapp.model.Answer
import com.edipismailoglu.educationtrackingapp.model.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PostAdapter(
    private val postList: ArrayList<Post>,
    private val context: Context,
    private val userType: String,
    private val onPostUpdated: (() -> Unit)? = null,
    private val onPostDeleted: (() -> Unit)? = null
) : RecyclerView.Adapter<PostAdapter.PostHolder>() {

    private var lastPdfTarget: com.squareup.picasso.Target? = null

    // Fonts for PDF generation
    private val titleFont = Font(Font.FontFamily.HELVETICA, 20f, Font.BOLD, com.itextpdf.text.BaseColor(0, 102, 204))
    private val labelFont = Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD, com.itextpdf.text.BaseColor.BLACK)
    private val valueFont = Font(Font.FontFamily.HELVETICA, 12f, Font.NORMAL, com.itextpdf.text.BaseColor.DARK_GRAY)
    private val captionFont = Font(Font.FontFamily.HELVETICA, 10f, Font.ITALIC, com.itextpdf.text.BaseColor.GRAY)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostHolder {
        val binding = RecycleRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostHolder(binding)
    }

    override fun onBindViewHolder(holder: PostHolder, position: Int) {
        val post = postList[position]
        bindPostData(holder, post)
        setupCommentClickListener(holder)
        setupImageClickListener(holder, post)
        setupOptionsMenu(holder, post)
        setupSubmitButtonForStudent(holder, post)
    }

    override fun getItemCount(): Int = postList.size

    class PostHolder(val binding: RecycleRowBinding) : RecyclerView.ViewHolder(binding.root)

    private fun bindPostData(holder: PostHolder, post: Post) {
        with(holder.binding) {
            recyclerEmailText.text = "📧 Teacher Email: ${post.email}"
            recyclerDateText.text = "📅 Upload Date: ${post.uploadDate}"
            recyclerFinishDateText.text = "⏰ Finish Date: ${post.finishDate}"
            recyclerCommentText.text = "📝 Task Description:\n${post.comment}"
            recyclerTargetClassChip.text = "🏫 Target Class: ${post.targetClass}"

            val stage = post.targetClass.lowercase()
            recyclerTargetClassChip.apply {
                when {
                    "primary" in stage -> chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#4CAF50")) // Green
                    "middle" in stage -> chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FFC107")) // Yellow
                    "high" in stage -> chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F44336")) // Red
                    else -> chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#9E9E9E")) // Gray
                }
            }

            Picasso.get().load(post.downloadUri).into(recyclerImageView)
        }
    }

    private fun setupCommentClickListener(holder: PostHolder) {
        holder.binding.recyclerCommentText.setOnClickListener {
            val dialogView = LayoutInflater.from(holder.itemView.context).inflate(R.layout.dialog_task_description, null)
            dialogView.findViewById<TextView>(R.id.fullDescriptionText).text = holder.binding.recyclerCommentText.text
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("Task Description")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun setupImageClickListener(holder: PostHolder, post: Post) {
        holder.binding.recyclerImageView.setOnClickListener {
            val dialog = Dialog(context)
            dialog.setContentView(R.layout.dialog_image_preview)
            val imageView = dialog.findViewById<ImageView>(R.id.fullscreenImage)
            Picasso.get().load(post.downloadUri).into(imageView)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.show()
        }
    }

    private fun setupOptionsMenu(holder: PostHolder, post: Post) {
        holder.binding.optionsMenuIcon.setOnClickListener {
            val popupMenu = PopupMenu(holder.itemView.context, holder.binding.optionsMenuIcon)
            val menuRes = if (userType == "teacher") R.menu.post_popup_menu else R.menu.post_popup_menu2
            popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
            if (userType == "parent") {
                popupMenu.menu.findItem(R.id.menu_view_my_answer)?.isVisible = false
                popupMenu.menu.findItem(R.id.menu_view_my_evaluation)?.isVisible = false
            }


            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_edit_task -> {
                        showEditTaskDialog(holder, post)
                        true
                    }
                    R.id.menu_share -> {
                        generateAndShareTaskPdf(post, holder)
                        true
                    }
                    R.id.menu_delete -> {
                        deletePost(holder, post)
                        true
                    }
                    R.id.menu_view_answers -> {

                        navigateToAnswers(holder, post.postId)
                        true
                    }
                    R.id.menu_view_my_answer -> {
                        if (userType == "parent") return@setOnMenuItemClickListener false
                        viewStudentAnswer(holder.itemView.context, post.postId)
                        true
                    }
                    R.id.menu_contact -> {
                        sendEmail(post)
                        true
                    }
                    R.id.menu_view_my_evaluation -> {
                        if (userType == "parent") return@setOnMenuItemClickListener false
                        loadEvaluationAndShowDialog(holder.itemView.context, post)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }

    private fun setupSubmitButtonForStudent(holder: PostHolder, post: Post) {
        if (userType != "student") {
            holder.binding.submitAnswerButton.visibility = View.GONE
            return
        }

        holder.binding.submitAnswerButton.visibility = View.VISIBLE
        val db = FirebaseFirestore.getInstance()
        val studentEmail = Firebase.auth.currentUser?.email

        db.collection("Submissions")
            .whereEqualTo("taskId", post.postId)
            .whereEqualTo("studentEmail", studentEmail)
            .get()
            .addOnSuccessListener { documents ->
                with(holder.binding.submitAnswerButton) {
                    if (!documents.isEmpty) {
                        text = "You have Answered this task✔"
                        isEnabled = false
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BDBDBD"))
                        setTextColor(Color.WHITE)
                    } else {
                        text = "Submit Answer"
                        isEnabled = true
                        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.purple_500))
                        setTextColor(Color.WHITE)
                        setOnClickListener { showAnswerDialog(holder.itemView.context, post.postId) }
                    }
                }
            }
    }

    private fun showLoadingDialog(message: String): AlertDialog {
        val loadingView = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null)
        loadingView.findViewById<TextView>(R.id.loadingText).text = message
        return AlertDialog.Builder(context)
            .setView(loadingView)
            .setCancelable(false)
            .create().apply {
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                show()
            }
    }

    private fun addFieldToDocument(document: Document, label: String, value: String) {
        val chunkLabel = com.itextpdf.text.Chunk("$label: ", labelFont)
        val chunkValue = com.itextpdf.text.Chunk(value, valueFont)
        val paragraph = Paragraph().apply {
            add(chunkLabel)
            add(chunkValue)
            spacingAfter = 8f
        }
        document.add(paragraph)
    }

    private fun addImageToDocument(document: Document, bitmap: android.graphics.Bitmap?) {
        if (bitmap != null) {
            document.add(Paragraph("Image:", labelFont).apply { spacingAfter = 4f })
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, stream)
            val image = com.itextpdf.text.Image.getInstance(stream.toByteArray())
            image.scaleToFit(300f, 300f)
            image.alignment = Element.ALIGN_CENTER
            image.spacingAfter = 5f
            document.add(image)
            document.add(Paragraph("Image attached to the task", captionFont).apply {
                alignment = Element.ALIGN_CENTER
                spacingAfter = 15f
            })
        } else {
            document.add(Paragraph("Image: Not available", valueFont).apply { spacingAfter = 15f })
        }
    }

    private fun generateAndShareTaskPdf(post: Post, holder: PostHolder) {
        val loadingDialog = showLoadingDialog("Generating PDF for Task...")
        try {
            lastPdfTarget = object : com.squareup.picasso.Target {
                override fun onBitmapLoaded(bitmap: android.graphics.Bitmap?, from: Picasso.LoadedFrom?) {
                    try {
                        val pdfFile = File(context.cacheDir, "Task_${System.currentTimeMillis()}.pdf")
                        val document = Document()
                        val outputStream = FileOutputStream(pdfFile)
                        PdfWriter.getInstance(document, outputStream)
                        document.open()

                        val title = Paragraph("Task Details", titleFont).apply {
                            alignment = Element.ALIGN_CENTER
                            spacingAfter = 20f
                        }
                        document.add(title)

                        addFieldToDocument(document, "Teacher Email", post.email)
                        addFieldToDocument(document, "Upload Date", post.uploadDate)
                        addFieldToDocument(document, "Finish Date", post.finishDate)
                        addFieldToDocument(document, "Target Class", post.targetClass)

                        document.add(Paragraph("Task Description:", labelFont).apply { spacingAfter = 4f })
                        document.add(Paragraph(post.comment, valueFont).apply { spacingAfter = 15f })

                        addImageToDocument(document, bitmap)

                        val footer = Paragraph(
                            "Generated on ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}",
                            captionFont
                        ).apply {
                            alignment = Element.ALIGN_RIGHT
                        }
                        document.add(footer)

                        document.close()
                        outputStream.close()

                        sharePdfFile(pdfFile, "Task: ${post.comment.take(50)}...", holder, loadingDialog)
                    } catch (e: Exception) {
                        loadingDialog.dismiss()
                        android.util.Log.e("PostAdapter", "Failed to generate task PDF", e)
                        Toast.makeText(context, "Failed to generate PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onBitmapFailed(e: Exception?, errorDrawable: android.graphics.drawable.Drawable?) {
                    loadingDialog.dismiss()
                    android.util.Log.e("PostAdapter", "Failed to load task image: ${post.downloadUri}", e)
                    Toast.makeText(context, "Failed to load task image", Toast.LENGTH_SHORT).show()
                }

                override fun onPrepareLoad(placeHolderDrawable: android.graphics.drawable.Drawable?) {}
            }

            if (post.downloadUri.isNullOrEmpty()) {
                lastPdfTarget?.onBitmapLoaded(null, null)
            } else {
                Picasso.get().load(post.downloadUri).into(lastPdfTarget!!)
            }
        } catch (e: Exception) {
            loadingDialog.dismiss()
            android.util.Log.e("PostAdapter", "Failed to initiate task PDF generation", e)
            Toast.makeText(context, "Failed to initiate PDF generation", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateAndShareReportPdf(answer: Answer, dialog: Dialog) {
        val loadingDialog = showLoadingDialog("Generating Report PDF...")
        try {
            lastPdfTarget = object : com.squareup.picasso.Target {
                override fun onBitmapLoaded(bitmap: android.graphics.Bitmap?, from: Picasso.LoadedFrom?) {
                    try {
                        val pdfFile = File(context.cacheDir, "Report_${System.currentTimeMillis()}.pdf")
                        val document = Document()
                        val outputStream = FileOutputStream(pdfFile)
                        PdfWriter.getInstance(document, outputStream)
                        document.open()

                        // Title
                        val title = Paragraph("Task and Answer Report", titleFont).apply {
                            alignment = Element.ALIGN_CENTER
                            spacingAfter = 20f
                        }
                        document.add(title)

                        // Task Details Section
                        document.add(Paragraph("Task Details", labelFont).apply { spacingAfter = 10f })
                        addFieldToDocument(document, "Teacher Email", answer.studentEmail)
                        addFieldToDocument(document, "Task Upload Date", answer.taskUploadDate ?: "-")
                        addFieldToDocument(document, "Task Finish Date", answer.taskFinishDate ?: "-")
                        document.add(Paragraph("Task Description:", labelFont).apply { spacingAfter = 4f })
                        document.add(Paragraph(answer.taskDescriptionText ?: "-", valueFont).apply { spacingAfter = 15f })
                        addImageToDocument(document, bitmap)

                        // Answer Details Section
                        document.add(Paragraph("Answer Details", labelFont).apply { spacingAfter = 10f })
                        addFieldToDocument(document, "Student Email", answer.studentEmail)
                        addFieldToDocument(document, "Answer Upload Date", answer.uploadDate ?: "-")
                        addFieldToDocument(document, "Grade", answer.grade?.toString() ?: "Not graded")
                        document.add(Paragraph("Student Answer:", labelFont).apply { spacingAfter = 4f })
                        document.add(Paragraph(answer.answerText, valueFont).apply { spacingAfter = 15f })

                        // Answer Image
                        if (!answer.imageUrl.isNullOrEmpty()) {
                            Picasso.get().load(answer.imageUrl).into(object : com.squareup.picasso.Target {
                                override fun onBitmapLoaded(answerBitmap: android.graphics.Bitmap?, from: Picasso.LoadedFrom?) {
                                    addImageToDocument(document, answerBitmap)
                                    addFooterAndShare(document, outputStream, pdfFile, "Report for Task: ${answer.taskDescriptionText?.take(50) ?: "Task"}...", dialog, loadingDialog)
                                }

                                override fun onBitmapFailed(e: Exception?, errorDrawable: android.graphics.drawable.Drawable?) {
                                    document.add(Paragraph("Answer Image: Failed to load", valueFont).apply { spacingAfter = 15f })
                                    addFooterAndShare(document, outputStream, pdfFile, "Report for Task: ${answer.taskDescriptionText?.take(50) ?: "Task"}...", dialog, loadingDialog)
                                }

                                override fun onPrepareLoad(placeHolderDrawable: android.graphics.drawable.Drawable?) {}
                            })
                        } else {
                            document.add(Paragraph("Answer Image: Not available", valueFont).apply { spacingAfter = 15f })
                            addFooterAndShare(document, outputStream, pdfFile, "Report for Task: ${answer.taskDescriptionText?.take(50) ?: "Task"}...", dialog, loadingDialog)
                        }
                    } catch (e: Exception) {
                        loadingDialog.dismiss()
                        dialog.dismiss()
                        android.util.Log.e("PostAdapter", "Failed to generate report PDF", e)
                        Toast.makeText(context, "Failed to generate report PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onBitmapFailed(e: Exception?, errorDrawable: android.graphics.drawable.Drawable?) {
                    try {
                        val pdfFile = File(context.cacheDir, "Report_${System.currentTimeMillis()}.pdf")
                        val document = Document()
                        val outputStream = FileOutputStream(pdfFile)
                        PdfWriter.getInstance(document, outputStream)
                        document.open()

                        val title = Paragraph("Task and Answer Report", titleFont).apply {
                            alignment = Element.ALIGN_CENTER
                            spacingAfter = 20f
                        }
                        document.add(title)

                        document.add(Paragraph("Task Details", labelFont).apply { spacingAfter = 10f })
                        addFieldToDocument(document, "Teacher Email", answer.studentEmail)
                        addFieldToDocument(document, "Task Upload Date", answer.taskUploadDate ?: "-")
                        addFieldToDocument(document, "Task Finish Date", answer.taskFinishDate ?: "-")
                        document.add(Paragraph("Task Description:", labelFont).apply { spacingAfter = 4f })
                        document.add(Paragraph(answer.taskDescriptionText ?: "-", valueFont).apply { spacingAfter = 15f })
                        document.add(Paragraph("Task Image: Not available", valueFont).apply { spacingAfter = 15f })

                        document.add(Paragraph("Answer Details", labelFont).apply { spacingAfter = 10f })
                        addFieldToDocument(document, "Student Email", answer.studentEmail)
                        addFieldToDocument(document, "Answer Upload Date", answer.uploadDate ?: "-")
                        addFieldToDocument(document, "Grade", answer.grade?.toString() ?: "Not graded")
                        document.add(Paragraph("Student Answer:", labelFont).apply { spacingAfter = 4f })
                        document.add(Paragraph(answer.answerText, valueFont).apply { spacingAfter = 15f })
                        document.add(Paragraph("Answer Image: Not available", valueFont).apply { spacingAfter = 15f })

                        addFooterAndShare(document, outputStream, pdfFile, "Report for Task: ${answer.taskDescriptionText?.take(50) ?: "Task"}...", dialog, loadingDialog)
                    } catch (e: Exception) {
                        loadingDialog.dismiss()
                        dialog.dismiss()
                        android.util.Log.e("PostAdapter", "Failed to generate report PDF without task image", e)
                        Toast.makeText(context, "Failed to generate report PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onPrepareLoad(placeHolderDrawable: android.graphics.drawable.Drawable?) {}
            }

            if (answer.taskImageUrl.isNullOrEmpty()) {
                lastPdfTarget?.onBitmapFailed(null, null)
            } else {
                Picasso.get().load(answer.taskImageUrl).into(lastPdfTarget!!)
            }
        } catch (e: Exception) {
            loadingDialog.dismiss()
            dialog.dismiss()
            android.util.Log.e("PostAdapter", "Failed to initiate report PDF generation", e)
            Toast.makeText(context, "Failed to initiate report PDF generation", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addFooterAndShare(document: Document, outputStream: FileOutputStream, pdfFile: File, subject: String, dialog: Dialog, loadingDialog: AlertDialog) {
        val footer = Paragraph(
            "Generated on ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}",
            captionFont
        ).apply {
            alignment = Element.ALIGN_RIGHT
        }
        document.add(footer)

        document.close()
        outputStream.close()

        sharePdfFile(pdfFile, subject, null, loadingDialog, dialog)
    }

    private fun sharePdfFile(pdfFile: File, subject: String, holder: PostHolder?, loadingDialog: AlertDialog, dialog: Dialog? = null) {
        val fileUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        loadingDialog.dismiss()
        dialog?.dismiss()
        (holder?.itemView?.context ?: context).startActivity(Intent.createChooser(shareIntent, "Share PDF"))
    }

    private fun deletePost(holder: PostHolder, post: Post) {
        AlertDialog.Builder(holder.itemView.context)
            .setTitle("Delete Confirmation")
            .setMessage("Are you sure you want to delete this post? This action cannot be undone.")
            .setPositiveButton("Yes, Delete") { _, _ ->
                val loadingDialog = showLoadingDialog("Deleting Post...")
                val db = FirebaseFirestore.getInstance()
                db.collection("Posts").document(post.postId).delete()
                    .addOnSuccessListener {
                        loadingDialog.dismiss()
                        Toast.makeText(holder.itemView.context, "Post deleted successfully", Toast.LENGTH_SHORT).show()
                        val index = postList.indexOfFirst { it.postId == post.postId }
                        if (index != -1) {
                            postList.removeAt(index)
                            notifyItemRemoved(index)
                        }
                        onPostDeleted?.invoke()
                    }
                    .addOnFailureListener {
                        loadingDialog.dismiss()
                        Toast.makeText(holder.itemView.context, "Error deleting the post", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToAnswers(holder: PostHolder, taskId: String) {
        val bundle = Bundle().apply { putString("taskId", taskId) }
        val navController = Navigation.findNavController(holder.itemView)
        val currentDestinationId = navController.currentDestination?.id
        val actionId = when (currentDestinationId) {
            R.id.teacherFeedFragment -> R.id.action_feedFragment_to_viewAnswersFragment
            R.id.finishedTasksFragment -> R.id.action_finishedTasksFragment_to_viewAnswersFragment
            R.id.teacherClassTasksFragment -> R.id.action_teacherClassTasksFragment_to_viewAnswersFragment
            else -> null
        }

        if (actionId != null) {
            navController.navigate(actionId, bundle)
        } else {
            Toast.makeText(holder.itemView.context, "Cannot navigate to answers from this screen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmail(post: Post) {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(post.email))
            putExtra(Intent.EXTRA_SUBJECT, "Task Description: ${post.comment}")
        }
        context.startActivity(Intent.createChooser(emailIntent, "Send Email"))
    }

    private fun loadEvaluationAndShowDialog(context: Context, post: Post) {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return

        val db = FirebaseFirestore.getInstance()
        db.collection("Submissions")
            .whereEqualTo("taskId", post.postId)
            .whereEqualTo("studentEmail", currentUserEmail)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(context, "You have not submitted an answer for this task.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val doc = documents.first()
                val answer = Answer(
                    studentEmail = doc.getString("studentEmail") ?: "",
                    answerText = doc.getString("answerText") ?: "-",
                    uploadDate = doc.getTimestamp("uploadDate")?.toDate()?.let {
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it)
                    } ?: "-",
                    grade = doc.getLong("grade")?.toInt(),
                    taskId = post.postId,
                    fileUrl = doc.getString("fileUrl"),
                    imageUrl = doc.getString("imageUrl"),
                    taskDescriptionText = post.comment,
                    taskUploadDate = post.uploadDate,
                    taskFinishDate = post.finishDate,
                    taskImageUrl = post.downloadUri,
                    taskTeacherEmail = post.email
                )

                showEvaluationDialog(context, answer)
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to load evaluation", Toast.LENGTH_SHORT).show()
            }
    }


    private fun showEvaluationDialog(context: Context, answer: Answer) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.item_evaluation_row)

        with(dialog) {
            findViewById<TextView>(R.id.studentAnswerText).text = answer.answerText
            findViewById<TextView>(R.id.evaluationText).text = answer.grade?.toString() ?: "-"
            findViewById<TextView>(R.id.recyclerCommentText).text = answer.taskDescriptionText ?: "-"
            findViewById<TextView>(R.id.recyclerDateText).text = answer.taskUploadDate ?: "-"
            findViewById<TextView>(R.id.answerUploadDateText).text = answer.uploadDate ?: "-"
            findViewById<TextView>(R.id.taskFinishDateText).text = answer.taskFinishDate ?: "-"
            findViewById<TextView>(R.id.teacherEmailText).text = "Teacher Email: ${answer.taskTeacherEmail ?: "-"}"


            val answerImageView = findViewById<ImageView>(R.id.answerImageView)
            if (!answer.imageUrl.isNullOrEmpty()) {
                answerImageView.visibility = View.VISIBLE
                Picasso.get().load(answer.imageUrl).into(answerImageView)
            } else {
                answerImageView.visibility = View.GONE
            }

            val taskImageView = findViewById<ImageView>(R.id.recyclerImageView)
            if (!answer.taskImageUrl.isNullOrEmpty()) {
                taskImageView.visibility = View.VISIBLE
                Picasso.get().load(answer.taskImageUrl).into(taskImageView)
            } else {
                taskImageView.visibility = View.GONE
            }

            val attachedFileText = findViewById<TextView>(R.id.attachedFileName)
            answer.fileUrl?.let { url ->
                val fileName = url.substringAfterLast("/")
                attachedFileText.text = fileName
                attachedFileText.visibility = View.VISIBLE
                attachedFileText.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            } ?: run { attachedFileText.visibility = View.GONE }

            findViewById<Button>(R.id.sendReportButton).setOnClickListener {
                generateAndShareReportPdf(answer, this)
            }

            findViewById<Button>(R.id.cancelButton).setOnClickListener {
                dismiss()
            }

            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            show()
        }
    }

    private fun viewStudentAnswer(context: Context, taskId: String) {
        val studentEmail = Firebase.auth.currentUser?.email ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("Submissions")
            .whereEqualTo("taskId", taskId)
            .whereEqualTo("studentEmail", studentEmail)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(context, "You have not submitted an answer yet", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val doc = documents.first()
                showViewMyAnswerDialog(context, taskId, doc.getString("answerText"), doc.getString("imageUrl"), doc.getString("fileUrl"))
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to retrieve your answer", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showViewMyAnswerDialog(context: Context, taskId: String, existingAnswer: String?, existingImageUrl: String?, existingFileUrl: String?) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_view_my_answer, null)
        val alertDialog = AlertDialog.Builder(context).setView(dialogView).create()

        with(dialogView) {
            findViewById<EditText>(R.id.answerTextView).setText(existingAnswer ?: "")

            val imagePreview = findViewById<ImageView>(R.id.imagePreview)
            if (!existingImageUrl.isNullOrEmpty()) {
                Picasso.get().load(existingImageUrl).into(imagePreview)
                imagePreview.visibility = View.VISIBLE
            } else {
                imagePreview.visibility = View.GONE
            }

            val fileNameTextView = findViewById<TextView>(R.id.fileNameTextView)
            if (!existingFileUrl.isNullOrEmpty()) {
                fileNameTextView.text = "View Attached File"
                fileNameTextView.visibility = View.VISIBLE
                fileNameTextView.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(existingFileUrl))
                    context.startActivity(intent)
                }
            } else {
                fileNameTextView.visibility = View.GONE
            }

            findViewById<Button>(R.id.editButton).setOnClickListener {
                alertDialog.dismiss()
                showAnswerDialog(context, taskId, existingAnswer, existingImageUrl, existingFileUrl)
            }

            findViewById<Button>(R.id.closeButton).setOnClickListener {
                alertDialog.dismiss()
            }
        }

        alertDialog.show()
    }

    private fun showEditTaskDialog(holder: PostHolder, post: Post) {
        val dialogView = LayoutInflater.from(holder.itemView.context).inflate(R.layout.dialog_edit_task, null)
        val commentInput = dialogView.findViewById<EditText>(R.id.editComment)
        val finishDateInput = dialogView.findViewById<EditText>(R.id.editFinishDate)
        val imageView = dialogView.findViewById<ImageView>(R.id.editImageView)

        commentInput.setText(post.comment)
        finishDateInput.setText(post.finishDate)
        Picasso.get().load(post.downloadUri).into(imageView)

        val calendar = Calendar.getInstance()
        val minDate = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        finishDateInput.setOnClickListener {
            DatePickerDialog(
                holder.itemView.context,
                { _, year, month, dayOfMonth ->
                    TimePickerDialog(
                        holder.itemView.context,
                        { _, hourOfDay, minute ->
                            val selectedCalendar = Calendar.getInstance()
                            selectedCalendar.set(year, month, dayOfMonth, hourOfDay, minute)
                            if (selectedCalendar.timeInMillis < minDate) {
                                Toast.makeText(holder.itemView.context, "Cannot select a date before today", Toast.LENGTH_SHORT).show()
                            } else {
                                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                finishDateInput.setText(sdf.format(selectedCalendar.time))
                            }
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply { datePicker.minDate = minDate }.show()
        }

        var selectedImageUri: Uri? = null
        val getImage = (holder.itemView.context as? androidx.fragment.app.FragmentActivity)?.activityResultRegistry
            ?.register("image_picker_key", androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
                uri?.let {
                    selectedImageUri = it
                    imageView.setImageURI(it)
                }
            }

        imageView.setOnClickListener { getImage?.launch("image/*") }

        val dialog = AlertDialog.Builder(holder.itemView.context)
            .setTitle("Edit Task")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newComment = commentInput.text.toString().trim()
                val newFinishDate = finishDateInput.text.toString().trim()

                if (newComment.isEmpty() || newFinishDate.isEmpty()) {
                    Toast.makeText(holder.itemView.context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val loadingDialog = showLoadingDialog("Saving New Changes...")
                val db = FirebaseFirestore.getInstance()
                val docRef = db.collection("Posts").document(post.postId)

                if (selectedImageUri != null) {
                    val storageRef = FirebaseStorage.getInstance().reference.child("images/${UUID.randomUUID()}.jpg")
                    storageRef.putFile(selectedImageUri!!)
                        .addOnSuccessListener {
                            storageRef.downloadUrl.addOnSuccessListener { uri ->
                                docRef.update(
                                    mapOf(
                                        "comment" to newComment,
                                        "finishDate" to newFinishDate,
                                        "downloadUrl" to uri.toString()
                                    )
                                ).addOnSuccessListener {
                                    loadingDialog.dismiss()
                                    Toast.makeText(holder.itemView.context, "Task updated", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                    onPostUpdated?.invoke()
                                }.addOnFailureListener {
                                    loadingDialog.dismiss()
                                    Toast.makeText(holder.itemView.context, "Failed to update task: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                } else {
                    docRef.update(
                        mapOf(
                            "comment" to newComment,
                            "finishDate" to newFinishDate
                        )
                    ).addOnSuccessListener {
                        loadingDialog.dismiss()
                        Toast.makeText(holder.itemView.context, "Task updated", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        onPostUpdated?.invoke()
                    }.addOnFailureListener {
                        loadingDialog.dismiss()
                        Toast.makeText(holder.itemView.context, "Failed to update task: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showAnswerDialog(context: Context, taskId: String, existingAnswer: String? = null, existingImageUrl: String? = null, existingFileUrl: String? = null) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_submit_answer, null)
        val alertDialog = AlertDialog.Builder(context).setView(dialogView).create()

        with(dialogView) {
            val answerText = findViewById<EditText>(R.id.answerEditText)
            val uploadImageButton = findViewById<Button>(R.id.uploadImageButton)
            val uploadFileButton = findViewById<Button>(R.id.uploadFileButton)
            val imagePreview = findViewById<ImageView>(R.id.imagePreview)
            val selectedFileName = findViewById<TextView>(R.id.selectedFileName)
            val progressBar = findViewById<ProgressBar>(R.id.uploadProgressBar)
            val submitButton = findViewById<Button>(R.id.submitButton)
            val cancelButton = findViewById<Button>(R.id.cancelButton)

            var selectedImageUri: Uri? = null
            var selectedFileUri: Uri? = null

            existingAnswer?.let { answerText.setText(it) }
            if (!existingImageUrl.isNullOrEmpty()) {
                Picasso.get().load(existingImageUrl).into(imagePreview)
                imagePreview.visibility = View.VISIBLE
            }
            if (!existingFileUrl.isNullOrEmpty()) {
                selectedFileName.text = Uri.parse(existingFileUrl).lastPathSegment ?: "View Attached File"
                selectedFileName.visibility = View.VISIBLE
                selectedFileName.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(existingFileUrl))
                    context.startActivity(intent)
                }
            }

            val imagePicker = (context as? androidx.fragment.app.FragmentActivity)?.activityResultRegistry
                ?.register("image_picker_key", androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
                    uri?.let {
                        selectedImageUri = it
                        imagePreview.setImageURI(it)
                        imagePreview.visibility = View.VISIBLE
                    }
                }

            val filePicker = (context as? androidx.fragment.app.FragmentActivity)?.activityResultRegistry
                ?.register("file_picker_key", androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
                    uri?.let {
                        selectedFileUri = it
                        val cursor = context.contentResolver.query(it, null, null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val displayName = it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                                selectedFileName.text = "Selected File: $displayName"
                                selectedFileName.visibility = View.VISIBLE
                            }
                        }
                    }
                }

            uploadImageButton.setOnClickListener { imagePicker?.launch("image/*") }
            uploadFileButton.setOnClickListener { filePicker?.launch("*/*") }

            submitButton.setOnClickListener {
                val answer = answerText.text.toString().trim()
                if (answer.isEmpty() && selectedImageUri == null && selectedFileUri == null) {
                    Toast.makeText(context, "Please provide an answer, image, or file", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                progressBar.visibility = View.VISIBLE
                submitButton.isEnabled = false
                cancelButton.isEnabled = false

                val db = FirebaseFirestore.getInstance()
                val storage = FirebaseStorage.getInstance()
                val answers = hashMapOf(
                    "taskId" to taskId,
                    "studentEmail" to Firebase.auth.currentUser?.email,
                    "answerText" to answer,
                    "uploadDate" to com.google.firebase.Timestamp.now(),
                    "grade" to null,
                    "imageUrl" to null as String?,
                    "fileUrl" to null as String?
                )

                fun submitAnswer(imageUrl: String? = null, fileUrl: String? = null) {
                    answers["imageUrl"] = imageUrl
                    answers["fileUrl"] = fileUrl
                    db.collection("Submissions").add(answers)
                        .addOnSuccessListener {
                            progressBar.visibility = View.GONE
                            Toast.makeText(context, "Answer uploaded successfully", Toast.LENGTH_SHORT).show()
                            alertDialog.dismiss()
                        }
                        .addOnFailureListener {
                            progressBar.visibility = View.GONE
                            submitButton.isEnabled = true
                            cancelButton.isEnabled = true
                            Toast.makeText(context, "Upload failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                }

                if (selectedImageUri != null) {
                    val imageRef = storage.reference.child("submissions/images/${UUID.randomUUID()}.jpg")
                    imageRef.putFile(selectedImageUri!!)
                        .addOnSuccessListener {
                            imageRef.downloadUrl.addOnSuccessListener { imageUri ->
                                if (selectedFileUri != null) {
                                    val fileRef = storage.reference.child("submissions/files/${UUID.randomUUID()}")
                                    fileRef.putFile(selectedFileUri!!)
                                        .addOnSuccessListener {
                                            fileRef.downloadUrl.addOnSuccessListener { fileUri ->
                                                submitAnswer(imageUri.toString(), fileUri.toString())
                                            }
                                        }
                                        .addOnFailureListener {
                                            progressBar.visibility = View.GONE
                                            submitButton.isEnabled = true
                                            cancelButton.isEnabled = true
                                            Toast.makeText(context, "File upload failed", Toast.LENGTH_SHORT).show()
                                        }
                                } else {
                                    submitAnswer(imageUri.toString())
                                }
                            }
                        }
                        .addOnFailureListener {
                            progressBar.visibility = View.GONE
                            submitButton.isEnabled = true
                            cancelButton.isEnabled = true
                            Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show()
                        }
                } else if (selectedFileUri != null) {
                    val fileRef = storage.reference.child("submissions/files/${UUID.randomUUID()}")
                    fileRef.putFile(selectedFileUri!!)
                        .addOnSuccessListener {
                            fileRef.downloadUrl.addOnSuccessListener { fileUri ->
                                submitAnswer(fileUrl = fileUri.toString())
                            }
                        }
                        .addOnFailureListener {
                            progressBar.visibility = View.GONE
                            submitButton.isEnabled = true
                            cancelButton.isEnabled = true
                            Toast.makeText(context, "File upload failed", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    submitAnswer()
                }
            }

            cancelButton.setOnClickListener { alertDialog.dismiss() }
        }

        alertDialog.show()
    }
}