package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.edipismailoglu.educationtrackingapp.adapter.EvaluationAdapter
import com.edipismailoglu.educationtrackingapp.databinding.FragmentViewEvaluationsBinding
import com.edipismailoglu.educationtrackingapp.model.Answer
import com.edipismailoglu.educationtrackingapp.model.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ViewEvaluationsFragment : Fragment() {

    private var _binding: FragmentViewEvaluationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var childName: String
    private lateinit var childEmail: String
    private lateinit var childAge: String
    private lateinit var childGender: String

    private lateinit var adapter: EvaluationAdapter
    private val evaluatedAnswers = mutableListOf<Answer>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            childName = it.getString("childName", "")
            childEmail = it.getString("childEmail", "")
            childAge = it.getString("childAge", "")
            childGender = it.getString("childGender", "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewEvaluationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = EvaluationAdapter(requireContext(), evaluatedAnswers)
        binding.evaluationRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.evaluationRecyclerView.adapter = adapter

        displayChildInfo()
        loadEvaluatedAnswers()
    }

    private fun displayChildInfo() {
        binding.childInfoText.text = "👦 $childName  |  ✉ $childEmail\n🎂 Age: $childAge   ⚥ Gender: $childGender"
    }

    private fun loadEvaluatedAnswers() {
        db.collection("Submissions")
            .whereEqualTo("studentEmail", childEmail)
            .orderBy("uploadDate", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                evaluatedAnswers.clear()

                if (documents.isEmpty) {
                    adapter.notifyDataSetChanged()
                    Toast.makeText(requireContext(), "No evaluations found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                for (doc in documents) {
                    val answerText = doc.getString("answerText") ?: ""
                    val taskId = doc.getString("taskId") ?: ""
                    val grade = doc.getLong("grade")?.toInt()
                    val uploadDate = doc.getTimestamp("uploadDate")?.toDate()?.let {
                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(it)
                    } ?: ""

                    val fileUrl = doc.getString("fileUrl")
                    val imageUrl = doc.getString("imageUrl")

                    // بعد ذلك، نجلب بيانات المهمة المرتبطة بهذا التقييم
                    db.collection("Posts").document(taskId).get()
                        .addOnSuccessListener { taskDoc ->
                            val taskOwnerEmail = taskDoc.getString("email")
                            val taskDescriptionText = taskDoc.getString("comment")
                            val taskUploadDate = taskDoc.getTimestamp("uploadDate")?.toDate()?.let {
                                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(it)
                            }
                            val taskFinishDate = taskDoc.getString("finishDate")
                            val taskImageUrl = taskDoc.getString("downloadUrl")

                            val answer = Answer(
                                studentEmail = childEmail,
                                answerText = answerText,
                                uploadDate = uploadDate,
                                grade = grade,
                                taskId = taskId,
                                fileUrl = fileUrl,
                                imageUrl = imageUrl,
                                taskOwnerEmail = taskOwnerEmail,
                                taskDescriptionText = taskDescriptionText,
                                taskUploadDate = taskUploadDate,
                                taskFinishDate = taskFinishDate,
                                taskImageUrl = taskImageUrl
                            )

                            evaluatedAnswers.add(answer)
                            adapter.notifyDataSetChanged()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load evaluations", Toast.LENGTH_SHORT).show()
            }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
