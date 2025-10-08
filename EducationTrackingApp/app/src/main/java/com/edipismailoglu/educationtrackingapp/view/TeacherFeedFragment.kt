package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.*
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.adapter.PostAdapter
import com.edipismailoglu.educationtrackingapp.databinding.FragmentTeacherFeedBinding
import com.edipismailoglu.educationtrackingapp.model.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class TeacherFeedFragment : Fragment(), PopupMenu.OnMenuItemClickListener {

    private var _binding: FragmentTeacherFeedBinding? = null
    private val binding get() = _binding!!
    private lateinit var popup: PopupMenu
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val postList: ArrayList<Post> = arrayListOf()
    private var adapter: PostAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeacherFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userType = arguments?.getString("userType")
        if (!userType.isNullOrEmpty()) {
            (activity as? MainActivity)?.currentUserType = userType
        } else {
            Toast.makeText(requireContext(), "User type is unknown", Toast.LENGTH_SHORT).show()
        }

        val navController = findNavController()
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle

        savedStateHandle?.getLiveData<Boolean>("post_added")?.observe(viewLifecycleOwner) { added ->
            if (added == true) {
                loadPostsFromFirestore()
                savedStateHandle.remove<Boolean>("post_added")
            }
        }

        popup = PopupMenu(requireContext(), binding.floatingActionButton)
        popup.menuInflater.inflate(R.menu.my_popup_menu, popup.menu)
        popup.setOnMenuItemClickListener(this)

        binding.floatingActionButton.setOnClickListener { popup.show() }

        adapter = PostAdapter(postList, requireContext(), "teacher")
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        loadPostsFromFirestore()
    }

    private fun loadPostsFromFirestore() {
        val currentUserEmail = auth.currentUser?.email ?: run {
            Log.e("TeacherFeedFragment", "No user signed in")
            Toast.makeText(requireContext(), "Please sign in first", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("TeacherFeedFragment", "Loading posts for: $currentUserEmail")

        db.collection("Posts")
            .whereEqualTo("email", currentUserEmail)
            .orderBy("uploadDate", Query.Direction.DESCENDING)
            .addSnapshotListener { value, error ->
                if (!isAdded) return@addSnapshotListener

                if (error != null) {
                    Log.e("TeacherFeedFragment", "Error loading posts: ${error.localizedMessage}")
                    Toast.makeText(requireContext(), "Error loading posts: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                value?.let {
                    postList.clear()
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val now = Date()

                    for (document in it.documents) {
                        try {
                            val comment = document.getString("comment") ?: continue
                            val email = document.getString("email") ?: continue
                            val downloadUrl = document.getString("downloadUrl") ?: continue
                            val timestamp = document.getTimestamp("uploadDate")
                            val uploadDate = timestamp?.toDate()?.let { date ->
                                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
                            } ?: ""
                            val finishDate = document.getString("finishDate") ?: continue
                            val finish = sdf.parse(finishDate)

                            if (finish != null && !finish.before(now)) {
                                val postId = document.id
                                val targetClass = document.getString("targetClass") ?: ""
                                val post = Post(email, comment, downloadUrl, uploadDate, finishDate, postId, targetClass)
                                postList.add(post)
                            }
                        } catch (e: Exception) {
                            Log.e("TeacherFeedFragment", "Error processing document: ${e.localizedMessage}")
                            e.printStackTrace()
                        }
                    }
                    Log.d("TeacherFeedFragment", "Loaded ${postList.size} posts")
                    adapter?.notifyDataSetChanged()
                }
            }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.uploadMenu -> {
                findNavController().navigate(R.id.action_feedFragment_to_uploadFragment)
            }
            R.id.logoutMenu -> {
                auth.signOut()
                findNavController().navigate(R.id.firstFragment)
            }
            R.id.viewMyClasses -> {
                findNavController().navigate(R.id.action_feedFragment_to_myClassesFragment)
            }
            R.id.finished -> {
                findNavController().navigate(R.id.action_feedFragment_to_finishedTasksFragment)
            }
            R.id.createClass -> {
                showCreateClassDialog()
            }
        }
        return true
    }

    private fun showCreateClassDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_class, null)
        val academicStageInput = dialogView.findViewById<AutoCompleteTextView>(R.id.editAcademicStage)
        val gradeLevelInput = dialogView.findViewById<AutoCompleteTextView>(R.id.editGradeLevel)

        val stageList = listOf("Primary", "Middle", "High")
        val adapterStage = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, stageList)
        academicStageInput.setAdapter(adapterStage)
        gradeLevelInput.isEnabled = false

        academicStageInput.setOnItemClickListener { _, _, position, _ ->
            val selectedStage = stageList[position]
            val grades = when (selectedStage) {
                "Primary" -> (1..5).map { it.toString() }
                "Middle" -> (6..8).map { it.toString() }
                "High" -> (9..12).map { it.toString() }
                else -> emptyList()
            }
            val gradeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, grades)
            gradeLevelInput.setAdapter(gradeAdapter)
            gradeLevelInput.setText("", false)
            gradeLevelInput.isEnabled = true
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Create New Class")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val academicStage = academicStageInput.text.toString().trim()
            val academicStageLower = academicStage.lowercase()
            val gradeLevel = gradeLevelInput.text.toString().trim()

            if (academicStage.isEmpty() || gradeLevel.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
                Toast.makeText(requireContext(), "You must be signed in as a teacher", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection("Classes")
                .get()
                .addOnSuccessListener { result ->
                    var classExistsBySelf = false
                    var classExistsByOther = false
                    var otherEmail: String? = null

                    for (doc in result) {
                        val docStage = doc.getString("academicStage")?.lowercase()?.trim()
                        val docGrade = doc.getString("gradeLevel")?.trim()
                        val createdBy = doc.getString("createdBy")
                        val email = doc.getString("creatorEmail") ?: "Another teacher"

                        if (docStage == academicStageLower && docGrade == gradeLevel) {
                            if (createdBy == userId) {
                                classExistsBySelf = true
                            } else {
                                classExistsByOther = true
                                otherEmail = email
                            }
                        }
                    }

                    when {
                        classExistsBySelf -> {
                            academicStageInput.setBackgroundResource(R.drawable.bg_input_error)
                            gradeLevelInput.setBackgroundResource(R.drawable.bg_input_error)
                            Toast.makeText(requireContext(), "You already created this class.", Toast.LENGTH_LONG).show()
                        }
                        classExistsByOther -> {
                            academicStageInput.setBackgroundResource(R.drawable.bg_input_error)
                            gradeLevelInput.setBackgroundResource(R.drawable.bg_input_error)
                            Toast.makeText(requireContext(), "This class was already created by $otherEmail.", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            academicStageInput.setBackgroundResource(android.R.color.transparent)
                            gradeLevelInput.setBackgroundResource(android.R.color.transparent)
                            val classData = hashMapOf(
                                "academicStage" to academicStage,
                                "gradeLevel" to gradeLevel,
                                "createdBy" to userId,
                                "creatorEmail" to (auth.currentUser?.email ?: "unknown"),
                                "createdAt" to System.currentTimeMillis()
                            )
                            db.collection("Classes")
                                .add(classData)
                                .addOnSuccessListener {
                                    Toast.makeText(requireContext(), "Class created successfully!", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to check existing classes: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}