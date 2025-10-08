package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.edipismailoglu.educationtrackingapp.databinding.FragmentStudentsBinding
import com.edipismailoglu.educationtrackingapp.adapter.StudentAdapter
import com.edipismailoglu.educationtrackingapp.model.Student
import com.google.firebase.firestore.FirebaseFirestore

class StudentsFragment : Fragment() {

    private var _binding: FragmentStudentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: StudentAdapter
    private val studentList = mutableListOf<Student>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStudentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        adapter = StudentAdapter(studentList)

        binding.recyclerViewStudents.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewStudents.adapter = adapter

        // Get arguments
        val stage = arguments?.getString("academicStage")
        val grade = arguments?.getString("gradeLevel")

        if (stage == null || grade == null) {
            Toast.makeText(requireContext(), "Error: Missing class information.", Toast.LENGTH_LONG).show()
            Log.e("StudentsFragment", "Arguments missing: stage=$stage, grade=$grade")
            return
        }

        binding.textHeader.text = "Students of $stage - $grade"

        Log.d("StudentsFragment", "Loading students for stage=$stage, grade=$grade")
        loadStudents(stage, grade)
    }

    private fun loadStudents(stage: String, grade: String) {
        db.collection("Classes")
            .whereEqualTo("academicStage", stage)
            .whereEqualTo("gradeLevel", grade)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(requireContext(), "Class not found.", Toast.LENGTH_LONG).show()
                    binding.textEmptyStudents.visibility = View.VISIBLE
                    binding.textEmptyStudents.text = "Class $stage - $grade not found."
                    return@addOnSuccessListener
                }

                val classDoc = snapshot.documents[0]
                val classId = classDoc.id

                db.collection("Classes").document(classId)
                    .collection("Students")
                    .addSnapshotListener { studentSnapshot, error ->
                        if (!isAdded) return@addSnapshotListener

                        if (error != null) {
                            Toast.makeText(requireContext(), "Failed to load students: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                            binding.textEmptyStudents.visibility = View.VISIBLE
                            binding.textEmptyStudents.text = "Failed to load students."
                            Log.e("StudentsFragment", "Error loading students: ${error.localizedMessage}")
                            return@addSnapshotListener
                        }

                        studentSnapshot?.let {
                            studentList.clear()
                            Log.d("StudentsFragment", "Snapshot received with ${it.documents.size} documents")
                            for (doc in it.documents) {
                                val student = doc.toObject(Student::class.java)
                                student?.let {
                                    studentList.add(it)
                                    Log.d("StudentsFragment", "Added student: ${it.name}, ${it.email}")
                                }
                            }
                            adapter.notifyDataSetChanged()
                            binding.textEmptyStudents.visibility = if (studentList.isEmpty()) View.VISIBLE else View.GONE
                            if (studentList.isEmpty()) {
                                binding.textEmptyStudents.text = "No students found for $stage - $grade."
                                Log.d("StudentsFragment", "No students found for $stage - $grade")
                            } else {
                                Log.d("StudentsFragment", "Found ${studentList.size} students")
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading class: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                Log.e("StudentsFragment", "Error loading class: ${e.localizedMessage}")
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
