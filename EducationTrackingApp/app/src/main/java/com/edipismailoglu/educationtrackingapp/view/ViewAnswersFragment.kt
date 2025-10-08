package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.edipismailoglu.educationtrackingapp.adapter.AnswerAdapter
import com.edipismailoglu.educationtrackingapp.databinding.FragmentViewAnswersBinding
import com.edipismailoglu.educationtrackingapp.model.Answer
import com.google.firebase.firestore.FirebaseFirestore

class ViewAnswersFragment : Fragment() {

    private var _binding: FragmentViewAnswersBinding? = null
    private val binding get() = _binding!!

    private lateinit var taskId: String
    private lateinit var adapter: AnswerAdapter
    private val answerList = mutableListOf<Answer>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewAnswersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskId = arguments?.getString("taskId") ?: run {
            Toast.makeText(requireContext(), "No task ID found", Toast.LENGTH_SHORT).show()
            return
        }

        binding.AnswerRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = AnswerAdapter(answerList)
        binding.AnswerRecyclerView.adapter = adapter

        loadAnswers()
    }

    private fun loadAnswers() {
        val db = FirebaseFirestore.getInstance()

        db.collection("Submissions")
            .whereEqualTo("taskId", taskId)
            .get()
            .addOnSuccessListener { snapshot ->
                answerList.clear()
                for (doc in snapshot.documents) {
                    val studentEmail = doc.getString("studentEmail") ?: continue
                    val answerText = doc.getString("answerText") ?: ""
                    val uploadDate = doc.getTimestamp("uploadDate")?.toDate()?.toString() ?: ""
                    val grade = doc.getLong("grade")?.toInt()
                    val imageUrl = doc.getString("imageUrl")
                    val fileUrl = doc.getString("fileUrl")

                    val answer = Answer(
                        studentEmail = studentEmail,
                        answerText = answerText,
                        uploadDate = uploadDate,
                        grade = grade,
                        taskId = taskId,
                        imageUrl = imageUrl,
                        fileUrl = fileUrl,
                        taskDescriptionText = null,      // سيتم تحميله لاحقًا
                        taskUploadDate = null,           // سيتم تحميله لاحقًا
                        taskFinishDate = null,           // سيتم تحميله لاحقًا
                        taskImageUrl = null              // سيتم تحميله لاحقًا
                    )
                    answerList.add(answer)
                }

                if (answerList.isEmpty()) {
                    Toast.makeText(requireContext(), "No answers found", Toast.LENGTH_SHORT).show()
                } else {
                    loadTaskDetails()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error loading answers", Toast.LENGTH_SHORT).show()
            }
    }


    private fun loadTaskDetails() {
        val db = FirebaseFirestore.getInstance()

        db.collection("Posts").document(taskId).get()
            .addOnSuccessListener { doc ->
                val comment = doc.getString("comment") ?: "No description"
                val uploadDate = doc.getTimestamp("uploadDate")?.toDate()?.toString() ?: "Unknown"
                val finishDate = doc.getString("finishDate") ?: "Unknown"
                val taskImageUrl = doc.getString("downloadUrl")

                for (answer in answerList) {
                    answer.taskDescriptionText = comment
                    answer.taskUploadDate = uploadDate
                    answer.taskFinishDate = finishDate
                    answer.taskImageUrl = taskImageUrl
                }

                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load task details", Toast.LENGTH_SHORT).show()
            }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
