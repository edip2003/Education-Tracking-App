package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.databinding.FragmentAllStudentsBinding
import com.edipismailoglu.educationtrackingapp.model.StudentWithClass
import com.edipismailoglu.educationtrackingapp.adapter.AllStudentsAdapter
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AllStudentsFragment : Fragment() {

    private var _binding: FragmentAllStudentsBinding? = null
    private val binding get() = _binding!!

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val studentList = mutableListOf<StudentWithClass>()
    private lateinit var adapter: AllStudentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllStudentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Back button returns to HomeFragment
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack(R.id.homeFragment, false)
            }
        })

        binding.textHeader.text = "All Students Enrolled in Your Classes"
        adapter = AllStudentsAdapter(studentList)
        binding.recyclerViewStudents.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewStudents.adapter = adapter

        loadAllStudents()
    }

    private fun loadAllStudents() {
        val currentUser = auth.currentUser ?: return

        showLoading(true)
        studentList.clear()

        db.collection("Classes")
            .whereEqualTo("createdBy", currentUser.uid)
            .get()
            .addOnSuccessListener { classDocuments ->
                if (classDocuments.isEmpty) {
                    showLoading(false)
                    binding.textEmptyStudents.visibility = View.VISIBLE
                    binding.textEmptyStudents.text = "You have not created any classes yet."
                    return@addOnSuccessListener
                }

                var pendingRequests = classDocuments.size()
                for (classDoc in classDocuments) {
                    val classId = classDoc.id
                    val academicStage = classDoc.getString("academicStage") ?: "-"
                    val gradeLevel = classDoc.getString("gradeLevel") ?: "-"

                    db.collection("Classes").document(classId).collection("Students")
                        .get()
                        .addOnSuccessListener { studentDocs ->
                            for (doc in studentDocs) {
                                val name = doc.getString("name") ?: continue
                                val email = doc.getString("email") ?: continue

                                studentList.add(
                                    StudentWithClass(
                                        name = name,
                                        email = email,
                                        academicStage = academicStage,
                                        gradeLevel = gradeLevel
                                    )
                                )
                            }
                            pendingRequests--
                            if (pendingRequests == 0) {
                                showLoading(false)
                                adapter.notifyDataSetChanged()
                                updateEmptyState()
                            }
                        }
                        .addOnFailureListener {
                            pendingRequests--
                            if (pendingRequests == 0) {
                                showLoading(false)
                                adapter.notifyDataSetChanged()
                                updateEmptyState()
                            }
                            Toast.makeText(requireContext(), "Failed to load students for class: $classId", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(requireContext(), "Failed to load your classes.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateEmptyState() {
        if (studentList.isEmpty()) {
            binding.textEmptyStudents.visibility = View.VISIBLE
            binding.textEmptyStudents.text = "No students enrolled in any of your classes."
        } else {
            binding.textEmptyStudents.visibility = View.GONE
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
