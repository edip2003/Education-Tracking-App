package com.edipismailoglu.educationtrackingapp.view

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.adapter.ClassAdapter
import com.edipismailoglu.educationtrackingapp.databinding.DialogCreateClassBinding
import com.edipismailoglu.educationtrackingapp.databinding.FragmentMyClassesBinding
import com.edipismailoglu.educationtrackingapp.model.ClassModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class MyClassesFragment : Fragment() {

    private var _binding: FragmentMyClassesBinding? = null
    private val binding get() = _binding!!
    private val classList = ArrayList<ClassModel>()
    private lateinit var adapter: ClassAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var classListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyClassesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ClassAdapter(
            classList,
            onUploadClick = { classModel ->
                val action = MyClassesFragmentDirections
                    .actionMyClassesFragmentToUploadFragment(
                        classModel.academicStage,
                        classModel.gradeLevel
                    )
                findNavController().navigate(action)
            },
            onShowFinishedClick = { classModel ->
                val action = MyClassesFragmentDirections
                    .actionMyClassesFragmentToFinishedTasksFragment(
                        classModel.academicStage,
                        classModel.gradeLevel
                    )
                findNavController().navigate(action)
            },
            onViewStudentsClick = { classModel ->
                val action = MyClassesFragmentDirections
                    .actionMyClassesFragmentToStudentsFragment(
                        classModel.academicStage,
                        classModel.gradeLevel
                    )
                findNavController().navigate(action)
            },
            onEditClick = { classModel, classId ->
                showEditClassDialog(classModel, classId)
            },
            onDeleteClick = { classModel ->
                deleteClass(classModel)
            },
            onViewTasksClick = { classModel ->
                val action = MyClassesFragmentDirections
                    .actionMyClassesFragmentToTeacherClassTasksFragment(
                        classModel.academicStage,
                        classModel.gradeLevel
                    )
                findNavController().navigate(action)
            }
        )

        binding.recyclerViewMyClasses.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewMyClasses.adapter = adapter

        loadClasses()
    }

    private fun loadClasses() {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(requireContext(), "User not authenticated.", Toast.LENGTH_SHORT).show()
            return
        }

        classListener?.remove()

        classListener = db.collection("Classes")
            .whereEqualTo("createdBy", currentUser.uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (!isAdded) return@addSnapshotListener

                if (error != null) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to load classes: ${error.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addSnapshotListener
                }

                classList.clear()
                snapshots?.documents?.forEach { doc ->
                    try {
                        val classItem = doc.toObject(ClassModel::class.java)
                        classItem?.let {
                            it.id = doc.id

                            classList.add(it)
                        }
                    } catch (e: Exception) {

                    }
                }

                adapter.notifyDataSetChanged()
                binding.textNoClasses.visibility = if (classList.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun deleteClass(cls: ClassModel) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Class")
            .setMessage("Are you sure you want to delete the class:\nStage: ${cls.academicStage}, Grade: ${cls.gradeLevel}?\nAll related tasks will also be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                val targetClass = "${cls.academicStage} - ${cls.gradeLevel}"

                db.collection("Posts")
                    .whereEqualTo("targetClass", targetClass)
                    .get()
                    .addOnSuccessListener { taskDocs ->
                        for (taskDoc in taskDocs) {
                            db.collection("Posts").document(taskDoc.id).delete()
                        }

                        db.collection("Classes").document(cls.id)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Class and all tasks deleted.", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(requireContext(), "Failed to delete class: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to delete tasks: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditClassDialog(cls: ClassModel, classId: String) {
        val dialogBinding = DialogCreateClassBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Edit Class")
            .setView(dialogBinding.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        val stageList = listOf("Primary", "Middle", "High")
        val adapterStage = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, stageList)
        dialogBinding.editAcademicStage.setAdapter(adapterStage)
        dialogBinding.editGradeLevel.isEnabled = false

        dialogBinding.editAcademicStage.setOnItemClickListener { _, _, position, _ ->
            val selectedStage = stageList[position]
            val grades = when (selectedStage) {
                "Primary" -> (1..5).map { it.toString() }
                "Middle" -> (6..8).map { it.toString() }
                "High" -> (9..12).map { it.toString() }
                else -> emptyList()
            }
            val gradeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, grades)
            dialogBinding.editGradeLevel.setAdapter(gradeAdapter)
            dialogBinding.editGradeLevel.setText("", false)
            dialogBinding.editGradeLevel.isEnabled = true
        }

        dialogBinding.editAcademicStage.setText(cls.academicStage, false)
        dialogBinding.editGradeLevel.setText(cls.gradeLevel, false)
        dialogBinding.editGradeLevel.isEnabled = true

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val newStage = dialogBinding.editAcademicStage.text.toString().trim()
                val newGrade = dialogBinding.editGradeLevel.text.toString().trim()

                if (newStage.isEmpty() || newGrade.isEmpty()) {
                    Toast.makeText(requireContext(), "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                dialogBinding.editAcademicStage.setBackgroundResource(android.R.color.transparent)
                dialogBinding.editGradeLevel.setBackgroundResource(android.R.color.transparent)

                if (newStage == cls.academicStage && newGrade == cls.gradeLevel) {
                    Toast.makeText(requireContext(), "No changes made to the class.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                db.collection("Classes")
                    .get()
                    .addOnSuccessListener { documents ->
                        var conflictFound = false

                        for (doc in documents) {
                            val existingStage = doc.getString("academicStage") ?: continue
                            val existingGrade = doc.getString("gradeLevel") ?: continue
                            val creatorId = doc.getString("createdBy") ?: ""
                            val docId = doc.id

                            val isSameClass = (existingStage.equals(newStage, true) && existingGrade.equals(newGrade, true))

                            if (isSameClass && docId != classId) {
                                dialogBinding.editAcademicStage.setBackgroundResource(R.drawable.bg_input_error)
                                dialogBinding.editGradeLevel.setBackgroundResource(R.drawable.bg_input_error)

                                val msg = if (creatorId == auth.currentUser?.uid) {
                                    "You already created this class. Please choose different values."
                                } else {
                                    "This class already exists and was created by another teacher."
                                }

                                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                                conflictFound = true
                                break
                            }
                        }

                        if (conflictFound) return@addOnSuccessListener

                        val oldTargetClass = "${cls.academicStage} - ${cls.gradeLevel}"
                        val newTargetClass = "$newStage - $newGrade"
                        db.collection("Posts")
                            .whereEqualTo("targetClass", oldTargetClass)
                            .get()
                            .addOnSuccessListener { taskDocs ->
                                for (taskDoc in taskDocs) {
                                    db.collection("Posts").document(taskDoc.id).update("targetClass", newTargetClass)
                                }

                                db.collection("Classes").document(classId)
                                    .update(mapOf("academicStage" to newStage, "gradeLevel" to newGrade))
                                    .addOnSuccessListener {
                                        Toast.makeText(requireContext(), "Class updated successfully.", Toast.LENGTH_SHORT).show()
                                        dialog.dismiss()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(requireContext(), "Failed to update class: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                            }
                            .addOnFailureListener {
                                Toast.makeText(requireContext(), "Failed to update tasks: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        classListener?.remove()
        _binding = null
    }
}
