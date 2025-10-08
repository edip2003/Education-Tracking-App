package com.edipismailoglu.educationtrackingapp.view

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.adapter.PostAdapter
import com.edipismailoglu.educationtrackingapp.databinding.FragmentStudentFeedBinding
import com.edipismailoglu.educationtrackingapp.model.Post
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class StudentFeedFragment : Fragment() {

    private var _binding: FragmentStudentFeedBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val postList = ArrayList<Post>()
    private var adapter: PostAdapter? = null
    private var postListener: ListenerRegistration? = null
    private var filterType: String? = null
    private var userType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            filterType = it.getString("filterType")
            userType = it.getString("userType")
        }
        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PostAdapter(postList, requireContext(), "student")
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener {
            if (!isInternetAvailable()) {
                binding.swipeRefreshLayout.isRefreshing = false
                Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            } else {
                loadActiveTasks()
            }
        }

        if (isInternetAvailable()) {
            loadActiveTasks()
        } else {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadActiveTasks() {
        if (!isAdded || _binding == null) return

        binding.progressBar.visibility = View.VISIBLE
        binding.swipeRefreshLayout.isRefreshing = false

        val currentUser = auth.currentUser ?: run {
            binding.progressBar.visibility = View.GONE
            Snackbar.make(binding.root, "User is not authenticated", Snackbar.LENGTH_SHORT).show()
            return
        }

        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                if (document != null && document.exists()) {
                    val stage = document.getString("academicStage") ?: ""
                    val grade = document.getString("gradeLevel") ?: ""
                    val targetClass = "$stage - $grade".split("-").joinToString(" - ") { it.trim().lowercase().replaceFirstChar { c -> c.uppercase() } }
                    fetchFilteredPostsForClass(targetClass)
                } else {
                    binding.progressBar.visibility = View.GONE
                    Snackbar.make(binding.root, "User info not found", Snackbar.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.progressBar.visibility = View.GONE
                Snackbar.make(binding.root, "Failed to fetch user data: ${it.message}", Snackbar.LENGTH_SHORT).show()
            }
    }

    private fun fetchFilteredPostsForClass(targetClass: String) {
        postListener?.remove()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val now = Date()
        val currentUser = auth.currentUser ?: return

        db.collection("Posts")
            .whereEqualTo("targetClass", targetClass)
            .orderBy("uploadDate", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshots ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                db.collection("Submissions")
                    .whereEqualTo("studentEmail", currentUser.email)
                    .get()
                    .addOnSuccessListener { submissionsSnapshot ->
                        if (!isAdded || _binding == null) return@addOnSuccessListener

                        val answeredTaskIds = submissionsSnapshot.documents.mapNotNull { it.getString("taskId") }
                        postList.clear()

                        snapshots.documents.forEach { document ->
                            try {
                                val comment = document.getString("comment") ?: return@forEach
                                val email = document.getString("email") ?: return@forEach
                                val downloadUrl = document.getString("downloadUrl") ?: return@forEach
                                val timestamp = document.getTimestamp("uploadDate")
                                val uploadDate = timestamp?.toDate()?.let {
                                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it)
                                } ?: ""
                                val finishDate = document.getString("finishDate") ?: return@forEach
                                val finish = sdf.parse(finishDate) ?: return@forEach
                                val postId = document.id

                                if (finish.after(now)) {
                                    val isAnswered = answeredTaskIds.contains(postId)
                                    if ((filterType == "answered" && isAnswered) ||
                                        (filterType == "unanswered" && !isAnswered) ||
                                        (filterType.isNullOrEmpty())) {

                                        val post = Post(email, comment, downloadUrl, uploadDate, finishDate, postId, targetClass)
                                        postList.add(post)
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        adapter?.notifyDataSetChanged()
                        binding.progressBar.visibility = View.GONE

                        if (postList.isEmpty()) {
                            Snackbar.make(binding.root, "No tasks found for the selected filter.", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        if (!isAdded || _binding == null) return@addOnFailureListener
                        binding.progressBar.visibility = View.GONE
                        Snackbar.make(binding.root, "Failed to fetch student submissions.", Snackbar.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.progressBar.visibility = View.GONE
                Snackbar.make(binding.root, "Failed to fetch posts: ${it.message}", Snackbar.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        postListener?.remove()
        _binding = null
    }
}
