package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.edipismailoglu.educationtrackingapp.adapter.PostAdapter
import com.edipismailoglu.educationtrackingapp.databinding.FragmentFinishedTasksBinding
import com.edipismailoglu.educationtrackingapp.model.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class FinishedTasksFragment : Fragment() {

    private var _binding: FragmentFinishedTasksBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val postList: ArrayList<Post> = arrayListOf()
    private var adapter: PostAdapter? = null

    private val academicStage: String by lazy { arguments?.getString("academicStage")?.trim() ?: "" }
    private val gradeLevel: String by lazy { arguments?.getString("gradeLevel")?.trim() ?: "" }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFinishedTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle

        savedStateHandle?.getLiveData<Boolean>("post_added")?.observe(viewLifecycleOwner) { added ->
            if (added == true) {
                loadFinishedTasks()
                savedStateHandle.remove<Boolean>("post_added")
            }
        }

        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()

        adapter = PostAdapter(postList, requireContext(), "teacher") {
            loadFinishedTasks()
        }

        binding.recyclerViewFinished.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewFinished.adapter = adapter

        val isClassSpecific = academicStage.isNotEmpty() && gradeLevel.isNotEmpty()
        if (isClassSpecific) {
            val stage = academicStage.replaceFirstChar { it.uppercase() }
            binding.textNoTasks.text = "There are no finished tasks for $stage - $gradeLevel."
        } else {
            binding.textNoTasks.text = "There are no finished tasks."
        }

        loadFinishedTasks()

        binding.deleteAllButton.setOnClickListener {
            val message = if (isClassSpecific) {
                val stage = academicStage.replaceFirstChar { it.uppercase() }
                "Are you sure you want to delete all finished tasks for $stage - $gradeLevel?"
            } else {
                "Are you sure you want to delete all finished tasks?"
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Confirm Deletion")
                .setMessage(message)
                .setPositiveButton("Yes") { _, _ ->
                    deleteAllFinishedTasks()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadFinishedTasks() {
        val currentUserEmail = auth.currentUser?.email ?: return
        val isClassSpecific = academicStage.isNotEmpty() && gradeLevel.isNotEmpty()
        val targetClass = if (isClassSpecific) {
            val stage = academicStage.replaceFirstChar { it.uppercase() }
            "$stage - $gradeLevel"
        } else {
            null
        }

        var query = db.collection("Posts")
            .whereEqualTo("email", currentUserEmail)

        if (targetClass != null) {
            query = query.whereEqualTo("targetClass", targetClass)
        }

        query.addSnapshotListener { value, error ->
            if (!isAdded) return@addSnapshotListener

            if (error != null) {
                println("Firestore Error: ${error.localizedMessage}")
                binding.textNoTasks.apply {
                    text = if (targetClass != null) {
                        "Failed to load tasks for $targetClass. Please check your connection and try again."
                    } else {
                        "Failed to load tasks. Please check your connection and try again."
                    }
                    alpha = 0f
                    visibility = View.VISIBLE
                    animate().alpha(1f).setDuration(500).start()
                }
                binding.deleteAllButton.visibility = View.GONE
                return@addSnapshotListener
            }

            value?.let {
                postList.clear()

                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val sdfWithTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
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
                        val targetClassFromDoc = document.getString("targetClass") ?: ""

                        if (finish != null && finish.before(now)) {
                            val postId = document.id
                            val post = Post(email, comment, downloadUrl, uploadDate, finishDate, postId, targetClassFromDoc)
                            postList.add(post)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                postList.sortWith { post1, post2 ->
                    try {
                        val date1 = sdfWithTime.parse(post1.finishDate)
                        val date2 = sdfWithTime.parse(post2.finishDate)
                        date2.compareTo(date1)
                    } catch (e: Exception) {
                        0
                    }
                }

                updateUI()
                adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun deleteAllFinishedTasks() {
        val currentUserEmail = auth.currentUser?.email ?: return
        val isClassSpecific = academicStage.isNotEmpty() && gradeLevel.isNotEmpty()
        val targetClass = if (isClassSpecific) {
            val stage = academicStage.replaceFirstChar { it.uppercase() }
            "$stage - $gradeLevel"
        } else {
            null
        }

        var query = db.collection("Posts")
            .whereEqualTo("email", currentUserEmail)

        if (targetClass != null) {
            query = query.whereEqualTo("targetClass", targetClass)
        }

        query.get()
            .addOnSuccessListener { result ->
                for (document in result.documents) {
                    val finishDate = document.getString("finishDate") ?: continue
                    try {
                        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        val finish = sdf.parse(finishDate)
                        val now = Date()
                        if (finish != null && finish.before(now)) {
                            db.collection("Posts").document(document.id).delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                Toast.makeText(
                    requireContext(),
                    if (targetClass != null) "All finished tasks for $targetClass have been deleted."
                    else "All finished tasks have been deleted.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to delete tasks.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUI() {
        if (postList.isEmpty()) {
            binding.textNoTasks.apply {
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(500).start()
            }
            binding.deleteAllButton.visibility = View.GONE
        } else {
            binding.textNoTasks.visibility = View.GONE
            binding.deleteAllButton.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        adapter = null
    }
}
