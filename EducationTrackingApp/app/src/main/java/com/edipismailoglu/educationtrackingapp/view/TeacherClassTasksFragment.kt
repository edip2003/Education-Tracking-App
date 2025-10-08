package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.edipismailoglu.educationtrackingapp.adapter.PostAdapter
import com.edipismailoglu.educationtrackingapp.databinding.FragmentTeacherClassTasksBinding
import com.edipismailoglu.educationtrackingapp.model.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class TeacherClassTasksFragment : Fragment() {

    private var _binding: FragmentTeacherClassTasksBinding? = null
    private val binding get() = _binding!!
    private val postList = ArrayList<Post>()
    private var adapter: PostAdapter? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var targetClass: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeacherClassTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let {
            val academicStage = it.getString("academicStage")?.trim()?.replaceFirstChar { c -> c.uppercase() }
            val gradeLevel = it.getString("gradeLevel")?.trim()
            if (!academicStage.isNullOrEmpty() && !gradeLevel.isNullOrEmpty()) {
                targetClass = "$academicStage - $gradeLevel"
                binding.classTitleText.text = "Class Tasks: $targetClass"
            } else {
                binding.classTitleText.text = "All Tasks You Created"
            }
        } ?: run {
            binding.classTitleText.text = "All Tasks You Created"
        }

        adapter = PostAdapter(postList, requireContext(), "teacher")
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        loadTasks()
    }

    private fun loadTasks() {
        binding.progressBar.visibility = View.VISIBLE

        val currentUserEmail = auth.currentUser?.email ?: return

        val baseQuery = db.collection("Posts")
            .whereEqualTo("email", currentUserEmail)

        val finalQuery = targetClass?.let {
            baseQuery.whereEqualTo("targetClass", it)
        } ?: baseQuery

        finalQuery.orderBy("uploadDate", Query.Direction.DESCENDING)
            .addSnapshotListener { value, error ->
                if (!isAdded) return@addSnapshotListener

                binding.progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(requireContext(), "Error loading tasks: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                            val postId = document.id
                            val classStr = document.getString("targetClass") ?: ""

                            if (finish != null && !finish.before(now)) {
                                val post = Post(email, comment, downloadUrl, uploadDate, finishDate, postId, classStr)
                                postList.add(post)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    adapter?.notifyDataSetChanged()
                    binding.textNoTasks.visibility = if (postList.isEmpty()) View.VISIBLE else View.GONE
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        adapter = null
    }
}
