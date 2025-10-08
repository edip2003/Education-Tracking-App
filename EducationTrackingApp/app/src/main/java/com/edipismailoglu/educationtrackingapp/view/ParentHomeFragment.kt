package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.databinding.FragmentParentHomeBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class ParentHomeFragment : Fragment() {

    private var _binding: FragmentParentHomeBinding? = null
    private val binding get() = _binding!!
    private val auth = Firebase.auth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.showBottomNav()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "You must be signed in first.", Toast.LENGTH_SHORT).show()
            return
        }

        loadParentData(currentUser.uid)

        binding.cardEnrolledClasses.setOnClickListener {
            findNavController().navigate(R.id.action_parentHomeFragment_to_childrenListFragment)
        }

        binding.cardAssignments.setOnClickListener {
            val action = ParentHomeFragmentDirections.actionParentHomeFragmentToParentFeedFragment(
                screenMode = "all",
                userType = "parent"
            )
            findNavController().navigate(action)
        }
    }

    private fun loadParentData(userId: String) {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val email = doc.getString("email") ?: ""
                binding.welcomeText.text = "Welcome, $email 👨‍👩‍👧‍👦"

                loadChildrenAndTasks(userId)
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                Toast.makeText(requireContext(), "Failed to load user data.", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
            }
    }

    private fun loadChildrenAndTasks(userId: String) {
        db.collection("users").document(userId).collection("children").get()
            .addOnSuccessListener { children ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val childCount = children.size()
                binding.enrolledClassCountText.text = "Registered Children: $childCount"

                if (childCount == 0) {
                    binding.assignmentCountText.text = "Total Tasks: 0"
                    binding.progressBar.visibility = View.GONE
                    return@addOnSuccessListener
                }

                val childInfoList = children.mapNotNull {
                    val email = it.getString("email") ?: return@mapNotNull null
                    val stage = it.getString("academicStage") ?: return@mapNotNull null
                    val grade = it.getString("gradeLevel") ?: return@mapNotNull null
                    Triple(email, stage, grade)
                }

                loadTasksForChildren(childInfoList)
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.enrolledClassCountText.text = "Registered Children: N/A"
                binding.assignmentCountText.text = "Total Tasks: N/A"
                binding.progressBar.visibility = View.GONE
            }
    }

    private fun loadTasksForChildren(children: List<Triple<String, String, String>>) {
        db.collection("Posts").get()
            .addOnSuccessListener { posts ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val targetClasses = children.map { "${it.second} - ${it.third}" }
                val filteredTasks = posts.documents.filter {
                    val targetClass = it.getString("targetClass") ?: ""
                    targetClasses.contains(targetClass)
                }

                binding.assignmentCountText.text = "Total Tasks: ${filteredTasks.size}"
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.assignmentCountText.text = "Total Tasks: N/A"
                binding.progressBar.visibility = View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
