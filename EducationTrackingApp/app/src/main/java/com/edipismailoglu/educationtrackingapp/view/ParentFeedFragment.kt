package com.edipismailoglu.educationtrackingapp.view

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.adapter.PostAdapter
import com.edipismailoglu.educationtrackingapp.databinding.FragmentParentFeedBinding
import com.edipismailoglu.educationtrackingapp.model.Child
import com.edipismailoglu.educationtrackingapp.model.Post
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class ParentFeedFragment : Fragment(), PopupMenu.OnMenuItemClickListener {

    private var _binding: FragmentParentFeedBinding? = null
    private val binding get() = _binding!!
    private lateinit var popup: PopupMenu
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val postList = ArrayList<Post>()
    private var adapter: PostAdapter? = null
    private val childList = mutableListOf<Triple<String, String, String>>()
    private var userType: String? = null
    private var screenMode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = ParentFeedFragmentArgs.fromBundle(requireArguments())
        screenMode = args.screenMode
        userType = args.userType
        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.showBottomNav()

        popup = PopupMenu(requireContext(), binding.floatingActionButton)
        popup.menuInflater.inflate(R.menu.my_popup_menu2, popup.menu)
        popup.setOnMenuItemClickListener(this)
        binding.floatingActionButton.setOnClickListener { popup.show() }

        adapter = PostAdapter(postList, requireContext(), "parent")
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        loadChildren()

        binding.swipeRefreshLayout.setOnRefreshListener {
            if (isInternetAvailable()) {
                loadChildren()
            } else {
                binding.swipeRefreshLayout.isRefreshing = false
                Snackbar.make(binding.root, "No internet connection", Snackbar.LENGTH_SHORT).show()
            }
        }

        if (screenMode == "all") {
            Toast.makeText(requireContext(), "Showing all tasks for your children", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadChildren() {
        val parentId = auth.currentUser?.uid ?: return
        db.collection("users").document(parentId).collection("children").get()
            .addOnSuccessListener { documents ->
                childList.clear()
                documents.forEach { doc ->
                    val email = doc.getString("email") ?: return@forEach
                    val academicStage = doc.getString("academicStage") ?: return@forEach
                    val gradeLevel = doc.getString("gradeLevel") ?: return@forEach
                    childList.add(Triple(email, academicStage, gradeLevel))
                }
                loadPostsForChildren()
            }
            .addOnFailureListener { e ->
                Snackbar.make(binding.root, "Failed to load children: ${e.localizedMessage}", Snackbar.LENGTH_SHORT).show()
            }
    }

    private fun loadPostsForChildren() {
        if (childList.isEmpty()) {
            postList.clear()
            adapter?.notifyDataSetChanged()
            binding.swipeRefreshLayout.isRefreshing = false
            Toast.makeText(requireContext(), "No children found.", Toast.LENGTH_SHORT).show()
            return
        }

        postList.clear()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val now = Date()

        // استخراج جميع الصفوف بدون تكرار
        val uniqueClasses = childList.map { "${it.second} - ${it.third}" }.distinct()

        if (uniqueClasses.isEmpty()) {
            binding.swipeRefreshLayout.isRefreshing = false
            Toast.makeText(requireContext(), "No valid classes found for your children.", Toast.LENGTH_SHORT).show()
            return
        }

        var pendingChunks = uniqueClasses.size

        for (targetClass in uniqueClasses) {
            db.collection("Posts")
                .whereEqualTo("targetClass", targetClass)
                .orderBy("uploadDate", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { result ->
                    result.documents.forEach { document ->
                        try {
                            val comment = document.getString("comment") ?: return@forEach
                            val taskEmail = document.getString("email") ?: return@forEach
                            val downloadUrl = document.getString("downloadUrl") ?: return@forEach
                            val finishDateStr = document.getString("finishDate") ?: return@forEach
                            val finishDate = sdf.parse(finishDateStr) ?: return@forEach
                            if (finishDate.before(now)) return@forEach

                            val uploadDate = document.getTimestamp("uploadDate")?.toDate()?.let {
                                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it)
                            } ?: ""

                            val post = Post(taskEmail, comment, downloadUrl, uploadDate, finishDateStr, document.id, targetClass)
                            postList.add(post)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    pendingChunks--
                    if (pendingChunks == 0) {
                        adapter?.notifyDataSetChanged()
                        binding.swipeRefreshLayout.isRefreshing = false

                        if (postList.isEmpty()) {
                            Toast.makeText(requireContext(), "No tasks available for your children.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener {
                    pendingChunks--
                    if (pendingChunks == 0) {
                        binding.swipeRefreshLayout.isRefreshing = false
                        Snackbar.make(binding.root, "Failed to load posts.", Snackbar.LENGTH_SHORT).show()
                    }
                }
        }
    }


    override fun onMenuItemClick(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.addChildMenu -> {
                showAddChildDialog()
                true
            }
            R.id.viewChildrenMenu -> {
                findNavController().navigate(R.id.action_parentFeedFragment_to_childrenListFragment)
                true
            }
            else -> false
        }
    }

    private fun showAddChildDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_child, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.childNameEditText)
        val emailInput = dialogView.findViewById<EditText>(R.id.childEmailEditText)
        val ageInput = dialogView.findViewById<EditText>(R.id.childAgeEditText)
        val genderGroup = dialogView.findViewById<RadioGroup>(R.id.childGenderRadioGroup)
        val academicStageInput = dialogView.findViewById<AutoCompleteTextView>(R.id.childAcademicStageEditText)
        val gradeLevelInput = dialogView.findViewById<AutoCompleteTextView>(R.id.childGradeLevelEditText)

        val stageMap = mutableMapOf<String, MutableList<String>>()
        val stageList = mutableListOf<String>()

        db.collection("Classes").get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val stage = doc.getString("academicStage") ?: continue
                    val grade = doc.getString("gradeLevel") ?: continue
                    if (!stageList.contains(stage)) stageList.add(stage)
                    stageMap.getOrPut(stage) { mutableListOf() }.add(grade)
                }

                val stageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, stageList)
                academicStageInput.setAdapter(stageAdapter)

                academicStageInput.setOnItemClickListener { _, _, position, _ ->
                    val selectedStage = stageList[position]
                    val grades = stageMap[selectedStage]?.distinct() ?: emptyList()
                    val gradeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, grades)
                    gradeLevelInput.setText("")
                    gradeLevelInput.setAdapter(gradeAdapter)
                    gradeLevelInput.isEnabled = true
                }

                academicStageInput.setOnClickListener { academicStageInput.showDropDown() }
                gradeLevelInput.setOnClickListener { gradeLevelInput.showDropDown() }

                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("Add Child")
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
                    val selectedGenderId = genderGroup.checkedRadioButtonId
                    val gender = if (selectedGenderId != -1) dialogView.findViewById<RadioButton>(selectedGenderId).text.toString() else ""

                    if (name.isEmpty() || email.isEmpty() || age.isEmpty() || gender.isEmpty() || academicStage.isEmpty() || gradeLevel.isEmpty()) {
                        Toast.makeText(requireContext(), "Please fill all fields.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val parentId = auth.currentUser?.uid ?: return@setOnClickListener

                    db.collection("users").whereEqualTo("email", email).whereEqualTo("userType", "student").get()
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

                            db.collection("users").document(parentId).collection("children")
                                .whereEqualTo("email", email)
                                .get()
                                .addOnSuccessListener { existingChildren ->
                                    if (existingChildren.isEmpty) {
                                        val childData = mapOf(
                                            "name" to name,
                                            "email" to email,
                                            "age" to age,
                                            "gender" to gender,
                                            "academicStage" to academicStage,
                                            "gradeLevel" to gradeLevel,
                                            "parentId" to parentId,
                                            "createdAt" to System.currentTimeMillis()
                                        )
                                        db.collection("users").document(parentId).collection("children").add(childData)
                                            .addOnSuccessListener {
                                                Toast.makeText(requireContext(), "Child added successfully!", Toast.LENGTH_SHORT).show()
                                                dialog.dismiss()
                                                loadChildren()
                                            }
                                    } else {
                                        Toast.makeText(requireContext(), "This child is already added.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load classes.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
