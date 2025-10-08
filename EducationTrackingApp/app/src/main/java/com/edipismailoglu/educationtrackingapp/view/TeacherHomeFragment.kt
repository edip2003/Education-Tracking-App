package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.databinding.FragmentHomeBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class TeacherHomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var backPressedTime: Long = 0
    private val BACK_PRESS_INTERVAL = 2000

    private val db = FirebaseFirestore.getInstance()
    private val auth = Firebase.auth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
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

        // ✅ تحميل اسم المعلم لرسالة الترحيب
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name") ?: ""
                    val surname = document.getString("surname") ?: ""
                    binding.welcomeText.text = "Welcome, $name $surname 👋"
                } else {
                    binding.welcomeText.text = "Welcome 👋"
                }
            }
            .addOnFailureListener {
                binding.welcomeText.text = "Welcome 👋"
            }

        binding.classCountText.text = "Classes: ..."
        binding.studentCountText.text = "Students: ..."
        binding.taskCountText.text = "Tasks Posted: ..."

        // 🔄 تحميل عدد الصفوف
        db.collection("Classes")
            .whereEqualTo("createdBy", user.uid)
            .get()
            .addOnSuccessListener { classResult ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val classCount = classResult.size()
                binding.classCountText.text = "Classes: $classCount"

                if (classCount == 0) {
                    binding.studentCountText.text = "Students: 0"
                } else {
                    var totalStudents = 0
                    var completed = 0
                    for (doc in classResult) {
                        db.collection("Classes").document(doc.id)
                            .collection("Students")
                            .get()
                            .addOnSuccessListener { students ->
                                if (!isAdded || _binding == null) return@addOnSuccessListener

                                totalStudents += students.size()
                                completed++
                                if (completed == classCount) {
                                    binding.studentCountText.text = "Students: $totalStudents"
                                }
                            }
                    }
                }
            }

        // 🔄 Görev sayısı
        db.collection("Posts")
            .whereEqualTo("email", user.email)
            .get()
            .addOnSuccessListener { posts ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                binding.taskCountText.text = "Tasks Posted: ${posts.size()}"
            }

        // sayfalar arası geçiş
        binding.cardClasses.setOnClickListener {
            findNavController().navigate(R.id.myClassesFragment)
        }
        binding.cardStudents.setOnClickListener {
            findNavController().navigate(R.id.allStudentsFragment)
        }
        binding.cardTasks.setOnClickListener {
            findNavController().navigate(R.id.teacherClassTasksFragment)
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
