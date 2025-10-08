package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.edipismailoglu.educationtrackingapp.databinding.FragmentStudentHomeBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class StudentHomeFragment : Fragment() {

    private var _binding: FragmentStudentHomeBinding? = null
    private val binding get() = _binding!!
    private val auth = Firebase.auth
    private val db = FirebaseFirestore.getInstance()
    private var backPressedTime: Long = 0
    private val BACK_PRESS_INTERVAL = 2000

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.showBottomNav()

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "You must be signed in first.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val name = doc.getString("name") ?: "Student"
                val surname = doc.getString("surname") ?: ""
                val stage = doc.getString("academicStage") ?: ""
                val grade = doc.getString("gradeLevel") ?: ""

                binding.welcomeText.text = "Hello $name $surname\nHave a great learning day!"
                val targetClass = "$stage - $grade".split("-").joinToString(" - ") { it.trim().lowercase().replaceFirstChar { c -> c.uppercase() } }
                binding.enrolledClassCountText.text = "Enrolled Class: $targetClass"

                db.collection("Posts")
                    .whereEqualTo("targetClass", targetClass)
                    .get()
                    .addOnSuccessListener { posts ->
                        if (!isAdded || _binding == null) return@addOnSuccessListener

                        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        val now = Date()
                        val activeTasks = posts.documents.filter {
                            val finishDateStr = it.getString("finishDate")
                            val finishDate = finishDateStr?.let { dateStr ->
                                try { sdf.parse(dateStr) } catch (e: Exception) { null }
                            }
                            finishDate != null && finishDate.after(now)
                        }
                        val activeTaskIds = activeTasks.map { it.id }

                        if (activeTaskIds.isEmpty()) {
                            binding.assignmentCountText.text = "Answered Tasks: 0"
                            binding.progressText.text = "Unanswered Tasks: 0"
                            binding.progressBar.visibility = View.GONE
                            return@addOnSuccessListener
                        }

                        db.collection("Submissions")
                            .whereEqualTo("studentEmail", user.email)
                            .whereIn("taskId", activeTaskIds)
                            .get()
                            .addOnSuccessListener { submissions ->
                                if (!isAdded || _binding == null) return@addOnSuccessListener

                                val answeredCount = submissions.documents.mapNotNull { it.getString("taskId") }.toSet().size
                                val unansweredCount = activeTaskIds.size - answeredCount
                                binding.assignmentCountText.text = "Answered Tasks: $answeredCount"
                                binding.progressText.text = "Unanswered Tasks: $unansweredCount"
                                binding.progressBar.visibility = View.GONE
                            }
                            .addOnFailureListener {
                                if (!isAdded || _binding == null) return@addOnFailureListener

                                binding.assignmentCountText.text = "Answered Tasks: N/A"
                                binding.progressText.text = "Unanswered Tasks: N/A"
                                binding.progressBar.visibility = View.GONE
                            }
                    }
                    .addOnFailureListener {
                        if (!isAdded || _binding == null) return@addOnFailureListener

                        binding.assignmentCountText.text = "Answered Tasks: N/A"
                        binding.progressText.text = "Unanswered Tasks: N/A"
                        binding.progressBar.visibility = View.GONE
                    }
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener

                Toast.makeText(requireContext(), "Failed to load student info", Toast.LENGTH_SHORT).show()
                binding.enrolledClassCountText.text = "Enrolled Class: N/A"
                binding.progressBar.visibility = View.GONE
            }

        binding.cardAssignments.setOnClickListener {
            val action = StudentHomeFragmentDirections
                .actionStudentHomeFragmentToStudentFeedFragment("answered", "student")
            findNavController().navigate(action)
        }

        binding.cardProgress.setOnClickListener {
            val action = StudentHomeFragmentDirections
                .actionStudentHomeFragmentToStudentFeedFragment("unanswered", "student")
            findNavController().navigate(action)
        }

        setupBackButton()
    }

    private fun setupBackButton() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - backPressedTime < BACK_PRESS_INTERVAL) {
                        requireActivity().finishAffinity()
                    } else {
                        backPressedTime = currentTime
                        Toast.makeText(requireContext(), "Press back again to exit", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
