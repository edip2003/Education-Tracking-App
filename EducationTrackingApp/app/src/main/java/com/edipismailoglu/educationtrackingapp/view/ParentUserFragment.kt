package com.edipismailoglu.educationtrackingapp.view

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.navigation.Navigation
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.databinding.FragmentParentUserBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

class ParentUserFragment : Fragment() {

    private var _binding: FragmentParentUserBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore
    private val USER_TYPE = "parent"

    private var selectedImageUri: Uri? = null
    private var tempImageView: ImageView? = null
    private val storage = Firebase.storage

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                tempImageView?.setImageURI(uri)
            }
        }
    }

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
        _binding = FragmentParentUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.hideBottomNav()
        binding.signInButton.setOnClickListener { signInClicked(it) }
        binding.signUpButton.setOnClickListener { signUpClicked(it) }
    }

    fun signUpClicked(view: View) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_parent_info, null)
        val editName = dialogView.findViewById<EditText>(R.id.editName)
        val editSurname = dialogView.findViewById<EditText>(R.id.editSurname)
        val editAge = dialogView.findViewById<EditText>(R.id.editAge)
        val editSex = dialogView.findViewById<AutoCompleteTextView>(R.id.editSex)
        val editEmail = dialogView.findViewById<EditText>(R.id.editEmail)
        val passwordLayout = dialogView.findViewById<TextInputLayout>(R.id.passwordLayout)
        passwordLayout.visibility = View.VISIBLE
        val editPassword = dialogView.findViewById<EditText>(R.id.editPassword)

        val profileImageView = dialogView.findViewById<ImageView>(R.id.profilePhotoImageView)
        val uploadPhotoButton = dialogView.findViewById<MaterialButton>(R.id.uploadPhotoButton)
        tempImageView = profileImageView

        val genderOptions = listOf("Male", "Female")
        val genderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, genderOptions)
        editSex.setAdapter(genderAdapter)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Parent Registration")
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
                val loadingDialog = AlertDialog.Builder(requireContext())
                    .setView(R.layout.dialog_loading)
                    .setCancelable(false)
                    .create()
                loadingDialog.show()

                val name = editName.text.toString().trim()
                val surname = editSurname.text.toString().trim()
                val age = editAge.text.toString().trim()
                val sex = editSex.text.toString().trim()
                val email = editEmail.text.toString().trim()
                val password = editPassword.text.toString().trim()

                if (name.isEmpty() || surname.isEmpty() || age.isEmpty() || sex.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    loadingDialog.dismiss()
                    return@setOnClickListener
                }

                if (age.toIntOrNull() == null || age.toInt() !in 18..80) {
                    Toast.makeText(requireContext(), "Enter a valid age between 18 and 80", Toast.LENGTH_SHORT).show()
                    loadingDialog.dismiss()
                    return@setOnClickListener
                }

                if (!genderOptions.contains(sex)) {
                    Toast.makeText(requireContext(), "Sex must be either 'Male' or 'Female'", Toast.LENGTH_SHORT).show()
                    loadingDialog.dismiss()
                    return@setOnClickListener
                }

                db.collection("users").whereEqualTo("email", email).get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            Toast.makeText(requireContext(), "This email is already in use.", Toast.LENGTH_LONG).show()
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

                                    if (selectedImageUri != null) {
                                        val storageRef = storage.reference.child("profileImages/${uid}.jpg")
                                        storageRef.putFile(selectedImageUri!!)
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
        db.collection("users").document(userId).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val userType = document.getString("userType") ?: "unknown"
                if (userType == USER_TYPE) {
                    (activity as? MainActivity)?.checkUserTypeAndNavigate(userId)
                    Toast.makeText(requireContext(), "Sign-in successful!", Toast.LENGTH_SHORT).show()
                } else {
                    auth.signOut()
                    Toast.makeText(context, "This account is registered as a $userType, not a parent", Toast.LENGTH_LONG).show()
                }
            }
        }.addOnFailureListener { exception ->
            Toast.makeText(context, exception.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
