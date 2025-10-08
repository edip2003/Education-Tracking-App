package com.edipismailoglu.educationtrackingapp.view

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import com.google.firebase.storage.ktx.storage
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.databinding.FragmentStudentUserBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import androidx.appcompat.app.AlertDialog

class StudentUserFragment : Fragment() {

    private var _binding: FragmentStudentUserBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore
    private val USER_TYPE = "student"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.hideBottomNav()

        binding.signInButton.setOnClickListener { signInClicked(it) }
        binding.signUpButton.setOnClickListener { signUpClicked(it) }
    }

    private lateinit var selectedImageUri: Uri
    private var tempImageView: ImageView? = null

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                tempImageView?.setImageURI(uri)
            }
        }
    }

    fun signInClicked(view: View) {
        val email = binding.userEmailText.text.toString().trim()
        val password = binding.passwordText.text.toString().trim()

        if (email.isNotEmpty() && password.isNotEmpty()) {
            binding.signInButton.isEnabled = false

            val loadingView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading, null)
            val loadingText = loadingView.findViewById<TextView>(R.id.loadingText)
            loadingText.text = "Loading..."

            val loadingDialog = AlertDialog.Builder(requireContext())
                .setView(loadingView)
                .setCancelable(false)
                .create()
            loadingDialog.show()

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val user = auth.currentUser
                    user?.let {
                        checkUserTypeAndNavigate(it.uid)
                    }
                    loadingDialog.dismiss()
                    binding.signInButton.isEnabled = true
                }
                .addOnFailureListener { exception ->
                    loadingDialog.dismiss()
                    binding.signInButton.isEnabled = true
                    Toast.makeText(requireContext(), exception.localizedMessage, Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "Please enter your email and password.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkUserTypeAndNavigate(userId: String) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || !isVisible) return@addOnSuccessListener
                if (document.exists()) {
                    val userType = document.getString("userType") ?: "unknown"
                    if (userType == USER_TYPE) {
                        (activity as? MainActivity)?.checkUserTypeAndNavigate(userId)
                        Toast.makeText(requireContext(), "Sign-in successful!", Toast.LENGTH_SHORT).show()
                    } else {
                        auth.signOut()
                        Toast.makeText(context, "This account is registered as $userType, not as a student.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnFailureListener {
                if (!isAdded || !isVisible) return@addOnFailureListener
                Toast.makeText(context, it.localizedMessage, Toast.LENGTH_SHORT).show()
            }
    }
    fun signUpClicked(view: View) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_student_signup, null)
        val editName = dialogView.findViewById<EditText>(R.id.editName)
        val editSurname = dialogView.findViewById<EditText>(R.id.editSurname)
        val editEmail = dialogView.findViewById<EditText>(R.id.editEmail)
        val editPassword = dialogView.findViewById<EditText>(R.id.editPassword)
        val editAcademicStage = dialogView.findViewById<AutoCompleteTextView>(R.id.editAcademicStage)
        val editGradeLevel = dialogView.findViewById<AutoCompleteTextView>(R.id.editGradeLevel)

        val imageView = dialogView.findViewById<ImageView>(R.id.profileImage)
        val selectImageButton = dialogView.findViewById<Button>(R.id.buttonSelectImage)
        tempImageView = imageView

        selectImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            activityResultLauncher.launch(intent)
        }

        editAcademicStage.isEnabled = false
        editGradeLevel.isEnabled = false

        val stagesMap = mutableMapOf<String, MutableList<String>>()
        val stages = mutableListOf<String>()
        db.collection("Classes")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(requireContext(), "No classes available. Please contact your teacher.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                for (doc in snapshot.documents) {
                    val academicStage = doc.getString("academicStage") ?: continue
                    val gradeLevel = doc.getString("gradeLevel") ?: continue
                    if (!stages.contains(academicStage)) {
                        stages.add(academicStage)
                    }
                    stagesMap.getOrPut(academicStage) { mutableListOf() }.add(gradeLevel)
                }

                val stageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, stages)
                editAcademicStage.setAdapter(stageAdapter)
                editAcademicStage.isEnabled = true

                editAcademicStage.setOnItemClickListener { _, _, position, _ ->
                    val selectedStage = stages[position]
                    val grades = stagesMap[selectedStage]?.distinct() ?: emptyList()
                    val gradeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, grades)
                    editGradeLevel.setText("")
                    editGradeLevel.setAdapter(gradeAdapter)
                    editGradeLevel.isEnabled = true
                }
            }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Register Student")
            .setView(dialogView)
            .setPositiveButton("Register", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val registerButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            registerButton.setOnClickListener {
                registerButton.isEnabled = false

                val loadingView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading, null)
                val loadingText = loadingView.findViewById<TextView>(R.id.loadingText)
                loadingText.text = "Loading..."
                val loadingDialog = AlertDialog.Builder(requireContext()).setView(loadingView).setCancelable(false).create()
                loadingDialog.show()

                val name = editName.text.toString().trim()
                val surname = editSurname.text.toString().trim()
                val email = editEmail.text.toString().trim()
                val password = editPassword.text.toString().trim()
                val academicStage = editAcademicStage.text.toString().trim()
                val gradeLevel = editGradeLevel.text.toString().trim()

                if (name.isEmpty() || surname.isEmpty() || email.isEmpty() || password.isEmpty()
                    || academicStage.isEmpty() || gradeLevel.isEmpty()) {
                    Toast.makeText(requireContext(), "Please fill all fields.", Toast.LENGTH_SHORT).show()
                    registerButton.isEnabled = true
                    loadingDialog.dismiss()
                    return@setOnClickListener
                }

                db.collection("Classes")
                    .whereEqualTo("academicStage", academicStage)
                    .whereEqualTo("gradeLevel", gradeLevel)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.isEmpty) {
                            Toast.makeText(requireContext(), "Class ($academicStage - $gradeLevel) not found.", Toast.LENGTH_LONG).show()
                            registerButton.isEnabled = true
                            loadingDialog.dismiss()
                            return@addOnSuccessListener
                        }

                        val classId = snapshot.documents[0].id

                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener {
                                val user = auth.currentUser!!
                                val uid = user.uid

                                fun saveToFirestore(profileUrl: String? = null) {
                                    val userData = hashMapOf(
                                        "email" to email,
                                        "userType" to USER_TYPE,
                                        "name" to name,
                                        "surname" to surname,
                                        "academicStage" to academicStage,
                                        "gradeLevel" to gradeLevel,
                                        "createdAt" to System.currentTimeMillis()
                                    )
                                    if (profileUrl != null) {
                                        userData["profileImage"] = profileUrl
                                    }

                                    db.collection("users").document(uid).set(userData)
                                        .addOnSuccessListener {
                                            val studentData = hashMapOf(
                                                "email" to email,
                                                "userType" to USER_TYPE,
                                                "name" to name,
                                                "surname" to surname,
                                                "createdAt" to System.currentTimeMillis()
                                            )
                                            db.collection("Classes").document(classId)
                                                .collection("Students").document(uid).set(studentData)
                                                .addOnSuccessListener {
                                                    Toast.makeText(requireContext(), "Registration successful.", Toast.LENGTH_SHORT).show()
                                                    loadingDialog.dismiss()
                                                    dialog.dismiss()
                                                }
                                                .addOnFailureListener {
                                                    loadingDialog.dismiss()
                                                    Toast.makeText(requireContext(), "Failed to save student: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                        .addOnFailureListener {
                                            loadingDialog.dismiss()
                                            Toast.makeText(requireContext(), "Failed to save user: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                }

                                if (::selectedImageUri.isInitialized) {
                                    val storageRef = Firebase.storage.reference.child("profileImages/${uid}.jpg")
                                    storageRef.putFile(selectedImageUri)
                                        .addOnSuccessListener {
                                            storageRef.downloadUrl.addOnSuccessListener { uri ->
                                                saveToFirestore(uri.toString())
                                            }
                                        }
                                        .addOnFailureListener {
                                            loadingDialog.dismiss()
                                            Toast.makeText(requireContext(), "Failed to upload image: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                } else {
                                    saveToFirestore()
                                }
                            }
                            .addOnFailureListener {
                                loadingDialog.dismiss()
                                Toast.makeText(requireContext(), it.localizedMessage, Toast.LENGTH_SHORT).show()
                            }
                    }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
