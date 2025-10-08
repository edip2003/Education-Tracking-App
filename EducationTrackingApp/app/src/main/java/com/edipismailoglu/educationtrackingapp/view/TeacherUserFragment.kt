package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import com.google.firebase.storage.ktx.storage
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.databinding.FragmentTeacherUserBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout

class TeacherUserFragment : Fragment() {

    private var _binding: FragmentTeacherUserBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore
    private val USER_TYPE = "teacher"
    private lateinit var selectedImageUri: Uri
    private val storage = Firebase.storage

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                tempImageView?.setImageURI(uri) // تحديث الصورة في الواجهة
            }
        }
    }

    private var tempImageView: ImageView? = null // نحتفظ بصورة ImageView لنستخدمها بعد اختيار الصورة


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        val currentUser = auth.currentUser
        if (currentUser != null) {
            checkUserTypeAndNavigate(currentUser.uid)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeacherUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.hideBottomNav()
        binding.signInButton.setOnClickListener { signInClicked(it) }
        binding.signUpButton.setOnClickListener { signUpClicked(it) }
    }

    private fun signUpClicked(view: View) {
        // Open dialog for teacher registration
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_teacher_info, null)
        val editName = dialogView.findViewById<EditText>(R.id.editName)
        val editSurname = dialogView.findViewById<EditText>(R.id.editSurname)
        val editAge = dialogView.findViewById<EditText>(R.id.editAge)
        val editSex = dialogView.findViewById<AutoCompleteTextView>(R.id.editSex)
        val editEmail = dialogView.findViewById<EditText>(R.id.editEmail) // Added email field
        val passwordLayout = dialogView.findViewById<TextInputLayout>(R.id.passwordLayout)
        passwordLayout.visibility = View.VISIBLE
        val editPassword = dialogView.findViewById<EditText>(R.id.editPassword)


        val profileImageView = dialogView.findViewById<ImageView>(R.id.profilePhotoImageView)
        val uploadPhotoButton = dialogView.findViewById<MaterialButton>(R.id.uploadPhotoButton)
        tempImageView = profileImageView // ليظهر الصورة في الديالوج بعد الاختيار


        // Set up gender dropdown
        val genderOptions = listOf("Male", "Female")
        val genderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, genderOptions)
        editSex.setAdapter(genderAdapter)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Teacher Registration")
            .setView(dialogView)
            .setPositiveButton("Register", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            uploadPhotoButton.setOnClickListener {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intent)
            }

            val registerButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            registerButton.setOnClickListener {
                val loadingDialog = createLoadingDialog()
                loadingDialog.show()

                val name = editName.text.toString().trim()
                val surname = editSurname.text.toString().trim()
                val age = editAge.text.toString().trim()
                val sex = editSex.text.toString().trim()
                val email = editEmail.text.toString().trim()
                val password = editPassword.text.toString().trim()

                // Validate all fields
                if (name.isEmpty() || surname.isEmpty() || age.isEmpty() || sex.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (age.toIntOrNull() == null || age.toInt() !in 24..65) {
                    Toast.makeText(requireContext(), "Enter a valid age between 24 and 65", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (!genderOptions.contains(sex)) {
                    Toast.makeText(requireContext(), "Sex must be either 'Male' or 'Female'", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Check if email is already used
                db.collection("users").whereEqualTo("email", email).get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            val existingUserType = documents.first().getString("userType") ?: "unknown"
                            val msg = if (existingUserType == "student") {
                                "This email is already registered by a student. Please use a different email."
                            } else {
                                "This email is already used by a $existingUserType account."

                            }
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                            loadingDialog.dismiss()
                        } else {
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener {
                                    val uid = auth.currentUser!!.uid

                                    fun saveUserToFirestore(profileImageUrl: String? = null) {
                                        val userData = hashMapOf(
                                            "uid" to uid,
                                            "email" to email,
                                            "userType" to USER_TYPE,
                                            "name" to name,
                                            "surname" to surname,
                                            "age" to age,
                                            "sex" to sex,
                                            "createdAt" to System.currentTimeMillis()
                                        )
                                        if (profileImageUrl != null) {
                                            userData["profileImage"] = profileImageUrl
                                        }

                                        db.collection("users").document(uid)
                                            .set(userData)
                                            .addOnSuccessListener {
                                                loadingDialog.dismiss()
                                                Toast.makeText(requireContext(), "Registration completed successfully!", Toast.LENGTH_SHORT).show()
                                                dialog.dismiss()
                                            }
                                            .addOnFailureListener { e ->
                                                loadingDialog.dismiss()
                                                Toast.makeText(requireContext(), "Failed to save profile: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                    }

                                    if (::selectedImageUri.isInitialized) {
                                        val storageRef = storage.reference.child("profileImages/${uid}.jpg")
                                        storageRef.putFile(selectedImageUri)
                                            .addOnSuccessListener {
                                                storageRef.downloadUrl.addOnSuccessListener { uri ->
                                                    saveUserToFirestore(uri.toString())
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                loadingDialog.dismiss()
                                                Toast.makeText(requireContext(), "Image upload failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                    } else {
                                        saveUserToFirestore()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    loadingDialog.dismiss()
                                    Toast.makeText(requireContext(), "Registration failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .addOnFailureListener {
                        loadingDialog.dismiss()
                        Toast.makeText(requireContext(), "Error checking email: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        dialog.show()
    }

    private fun signInClicked(view: View) {
        val email = binding.userEmailText.text.toString().trim()
        val password = binding.passwordText.text.toString().trim()

        if (email.isNotEmpty() && password.isNotEmpty()) {

            binding.signInButton.isEnabled=false
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
                        loadingDialog.dismiss()
                    }
                }
                .addOnFailureListener { exception ->
                    loadingDialog.dismiss()
                    Toast.makeText(requireContext(), exception.localizedMessage, Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(requireContext(), "Please enter email and password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkUserTypeAndNavigate(userId: String) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userType = document.getString("userType")
                    if (userType == USER_TYPE) {
                        (activity as? MainActivity)?.checkUserTypeAndNavigate(userId)
                        Toast.makeText(requireContext(), "Sign-in successful!", Toast.LENGTH_SHORT).show()
                    }
                    else {
                        auth.signOut()
                        Toast.makeText(requireContext(), "This account is registered as a $userType, not a teacher", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "User profile not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Error: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createLoadingDialog(): AlertDialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading, null)
        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
    }




    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}