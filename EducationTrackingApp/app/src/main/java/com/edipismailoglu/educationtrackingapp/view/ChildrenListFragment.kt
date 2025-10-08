package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.adapter.ChildAdapter
import com.edipismailoglu.educationtrackingapp.databinding.FragmentChildrenListBinding
import com.edipismailoglu.educationtrackingapp.model.Child
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ChildrenListFragment : Fragment() {

    private var _binding: FragmentChildrenListBinding? = null
    private val binding get() = _binding!!
    private val childList = ArrayList<Child>()
    private lateinit var adapter: ChildAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChildrenListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ChildAdapter(childList) { anchorView, child, _ ->
            showChildOptions(anchorView, child)
        }

        binding.recyclerViewChildren.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewChildren.adapter = adapter

        loadChildren()
    }

    private fun loadChildren() {
        val parentId = Firebase.auth.currentUser?.uid ?: return
        val db = Firebase.firestore

        db.collection("users").document(parentId).collection("children")
            .get()
            .addOnSuccessListener { documents ->
                childList.clear()
                for (doc in documents) {
                    val name = doc.getString("name") ?: continue
                    val email = doc.getString("email") ?: continue
                    val age = doc.getString("age") ?: continue
                    val gender = doc.getString("gender") ?: continue
                    val academicStage = doc.getString("academicStage") ?: continue
                    val gradeLevel = doc.getString("gradeLevel") ?: continue

                    childList.add(Child(name, email, age, gender, academicStage, gradeLevel))
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load children.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showChildOptions(anchorView: View, child: Child) {
        val popupMenu = PopupMenu(requireContext(), anchorView)
        popupMenu.menuInflater.inflate(R.menu.child_item_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.viewEvaluationsMenu -> {
                    val action = ChildrenListFragmentDirections.actionChildrenListFragmentToChildEvaluationsFragment(
                        child.name, child.email, child.age, child.gender, child.academicStage, child.gradeLevel
                    )
                    findNavController().navigate(action)
                    true
                }
                R.id.editChildMenu -> {
                    findChildDocumentId(child) { docId ->
                        showAddChildDialog(child, docId)
                    }
                    true
                }
                R.id.deleteChildMenu -> {
                    confirmDeleteChild(child)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun findChildDocumentId(child: Child, callback: (String) -> Unit) {
        val parentId = Firebase.auth.currentUser?.uid ?: return

        Firebase.firestore.collection("users").document(parentId).collection("children")
            .whereEqualTo("email", child.email)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    callback(documents.documents[0].id)
                } else {
                    Toast.makeText(requireContext(), "Child not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to find child.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDeleteChild(child: Child) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Child")
            .setMessage("Are you sure you want to delete ${child.name}?")
            .setPositiveButton("Yes") { _, _ -> deleteChild(child) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteChild(child: Child) {
        val parentId = Firebase.auth.currentUser?.uid ?: return

        Firebase.firestore.collection("users").document(parentId).collection("children")
            .whereEqualTo("email", child.email)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    Firebase.firestore.collection("users").document(parentId)
                        .collection("children").document(doc.id)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Child deleted.", Toast.LENGTH_SHORT).show()
                            loadChildren()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to delete child.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAddChildDialog(childToEdit: Child? = null, documentId: String? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_child, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.childNameEditText)
        val emailInput = dialogView.findViewById<EditText>(R.id.childEmailEditText)
        val ageInput = dialogView.findViewById<EditText>(R.id.childAgeEditText)
        val genderGroup = dialogView.findViewById<RadioGroup>(R.id.childGenderRadioGroup)
        val academicStageInput = dialogView.findViewById<AutoCompleteTextView>(R.id.childAcademicStageEditText)
        val gradeLevelInput = dialogView.findViewById<AutoCompleteTextView>(R.id.childGradeLevelEditText)

        val stageMap = mutableMapOf<String, MutableList<String>>()
        val stageList = mutableListOf<String>()

        Firebase.firestore.collection("Classes").get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val stage = doc.getString("academicStage") ?: continue
                    val grade = doc.getString("gradeLevel") ?: continue
                    if (!stageList.contains(stage)) stageList.add(stage)
                    stageMap.getOrPut(stage) { mutableListOf() }.add(grade)
                }

                val stageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, stageList)
                academicStageInput.setAdapter(stageAdapter)

                academicStageInput.setOnItemClickListener { _, _, pos, _ ->
                    val grades = stageMap[stageList[pos]]?.distinct() ?: emptyList()
                    val gradeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, grades)
                    gradeLevelInput.setText("")
                    gradeLevelInput.setAdapter(gradeAdapter)
                    gradeLevelInput.isEnabled = true
                }

                academicStageInput.setOnClickListener { academicStageInput.showDropDown() }
                gradeLevelInput.setOnClickListener { gradeLevelInput.showDropDown() }

                if (childToEdit != null) {
                    nameInput.setText(childToEdit.name)
                    emailInput.setText(childToEdit.email)
                    ageInput.setText(childToEdit.age)
                    academicStageInput.setText(childToEdit.academicStage)
                    gradeLevelInput.setText(childToEdit.gradeLevel)
                    emailInput.isEnabled = false
                    academicStageInput.isEnabled = false
                    gradeLevelInput.isEnabled = false
                    if (childToEdit.gender == "Male") genderGroup.check(R.id.maleRadioButton)
                    if (childToEdit.gender == "Female") genderGroup.check(R.id.femaleRadioButton)
                }

                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle(if (childToEdit == null) "Add Child" else "Edit Child")
                    .setView(dialogView)
                    .setPositiveButton("Save", null)
                    .setNegativeButton("Cancel", null)
                    .create()

                dialog.show()

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = nameInput.text.toString().trim()
                    val email = emailInput.text.toString().trim()
                    val age = ageInput.text.toString().trim()
                    val academicStage = academicStageInput.text.toString().trim()
                    val gradeLevel = gradeLevelInput.text.toString().trim()
                    val gender = dialogView.findViewById<RadioButton>(
                        genderGroup.checkedRadioButtonId
                    )?.text?.toString() ?: ""

                    if (name.isEmpty() || email.isEmpty() || age.isEmpty() || academicStage.isEmpty() || gradeLevel.isEmpty() || gender.isEmpty()) {
                        Toast.makeText(requireContext(), "Please fill all fields.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val parentId = Firebase.auth.currentUser?.uid ?: return@setOnClickListener

                    if (childToEdit == null) {
                        Firebase.firestore.collection("users")
                            .whereEqualTo("email", email)
                            .whereEqualTo("userType", "student")
                            .get()
                            .addOnSuccessListener { students ->
                                if (students.isEmpty) {
                                    Toast.makeText(requireContext(), "No student found with this email.", Toast.LENGTH_SHORT).show()
                                    return@addOnSuccessListener
                                }

                                val studentDoc = students.documents[0]
                                val realStage = studentDoc.getString("academicStage") ?: ""
                                val realGrade = studentDoc.getString("gradeLevel") ?: ""

                                if (academicStage != realStage || gradeLevel != realGrade) {
                                    Toast.makeText(requireContext(), "Stage and Grade do not match student's registered information.", Toast.LENGTH_SHORT).show()
                                    return@addOnSuccessListener
                                }

                                Firebase.firestore.collection("users").document(parentId)
                                    .collection("children").add(
                                        mapOf(
                                            "name" to name,
                                            "email" to email,
                                            "age" to age,
                                            "gender" to gender,
                                            "academicStage" to academicStage,
                                            "gradeLevel" to gradeLevel,
                                            "parentId" to parentId,
                                            "createdAt" to System.currentTimeMillis()
                                        )
                                    ).addOnSuccessListener {
                                        Toast.makeText(requireContext(), "Child added successfully!", Toast.LENGTH_SHORT).show()
                                        dialog.dismiss()
                                        loadChildren()
                                    }
                            }
                    } else {
                        Firebase.firestore.collection("users").document(parentId)
                            .collection("children").document(documentId!!)
                            .update(
                                mapOf(
                                    "name" to name,
                                    "age" to age,
                                    "gender" to gender
                                )
                            ).addOnSuccessListener {
                                Toast.makeText(requireContext(), "Child updated successfully!", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                                loadChildren()
                            }
                    }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
