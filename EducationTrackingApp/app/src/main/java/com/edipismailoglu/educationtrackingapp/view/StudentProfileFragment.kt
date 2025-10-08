package com.edipismailoglu.educationtrackingapp.view

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.databinding.FragmentStudentProfileBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.squareup.picasso.Picasso

class StudentProfileFragment : Fragment() {

    private var _binding: FragmentStudentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private var selectedImageUri: Uri? = null
    private var tempImageView: ImageView? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                tempImageView?.setImageURI(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadProfile()

        binding.logoutButton.setOnClickListener {
            showLogoutConfirmation()
        }

        binding.editProfileButton.setOnClickListener {
            showEditProfileDialog()
        }
    }

    private fun loadProfile() {
        val user = auth.currentUser ?: return

        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                if (!document.exists()) {
                    Toast.makeText(requireContext(), "Profile not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val name = document.getString("name") ?: ""
                val surname = document.getString("surname") ?: ""
                val academicStage = document.getString("academicStage") ?: ""
                val gradeLevel = document.getString("gradeLevel") ?: ""
                val profileImageUrl = document.getString("profileImage")
                val email = document.getString("email") ?: ""

                binding.userNameText.text = "$name $surname"
                binding.userEmailText.text = email
                binding.userAcademicStageText.text = "Academic Stage: $academicStage"
                binding.userGradeLevelText.text = "Grade Level: $gradeLevel"

                if (!profileImageUrl.isNullOrEmpty()) {
                    Picasso.get().load(profileImageUrl).into(binding.profileImage)
                }
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes") { _, _ ->
                val loadingDialog = createLoadingDialog()
                loadingDialog.show()

                auth.signOut()
                binding.logoutButton.postDelayed({
                    loadingDialog.dismiss()
                    val navController = androidx.navigation.Navigation.findNavController(requireView())
                    navController.navigate(R.id.firstFragment)
                    Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
                }, 1000)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_student_signup, null)
        val editName = dialogView.findViewById<EditText>(R.id.editName)
        val editSurname = dialogView.findViewById<EditText>(R.id.editSurname)
        val editAcademicStage = dialogView.findViewById<AutoCompleteTextView>(R.id.editAcademicStage)
        val editGradeLevel = dialogView.findViewById<AutoCompleteTextView>(R.id.editGradeLevel)
        val editEmail = dialogView.findViewById<EditText>(R.id.editEmail)
        val editPassword = dialogView.findViewById<EditText>(R.id.editPassword)
        val passwordLayout = dialogView.findViewById<LinearLayout>(R.id.passwordLayout)
        val academicStageLayout = editAcademicStage.parent.parent as View
        val gradeLevelLayout = editGradeLevel.parent.parent as View

        val profileImageView = dialogView.findViewById<ImageView>(R.id.profileImage)
        val uploadPhotoButton = dialogView.findViewById<Button>(R.id.buttonSelectImage)

        tempImageView = profileImageView

        val currentUser = auth.currentUser ?: return

        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) return@addOnSuccessListener

                editName.setText(document.getString("name") ?: "")
                editSurname.setText(document.getString("surname") ?: "")
                editAcademicStage.setText(document.getString("academicStage") ?: "")
                editGradeLevel.setText(document.getString("gradeLevel") ?: "")
                editEmail.setText(document.getString("email") ?: "")
                editEmail.isEnabled = false
                editPassword.setText("")
                passwordLayout.visibility = View.GONE
                academicStageLayout.visibility = View.GONE
                gradeLevelLayout.visibility = View.GONE

                val imageUrl = document.getString("profileImage")
                if (!imageUrl.isNullOrEmpty()) {
                    Picasso.get().load(imageUrl).into(profileImageView)
                }

                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("Edit Profile")
                    .setView(dialogView)
                    .setPositiveButton("Save", null)
                    .setNegativeButton("Cancel", null)
                    .create()

                dialog.setOnShowListener {
                    val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

                    uploadPhotoButton.setOnClickListener {
                        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        imagePickerLauncher.launch(intent)
                    }

                    saveButton.setOnClickListener {
                        val name = editName.text.toString().trim()
                        val surname = editSurname.text.toString().trim()

                        if (name.isEmpty() || surname.isEmpty()) {
                            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        val loadingDialog = createLoadingDialog()
                        loadingDialog.show()
                        saveButton.isEnabled = false

                        val updates = mutableMapOf<String, Any>(
                            "name" to name,
                            "surname" to surname
                        )

                        if (selectedImageUri != null) {
                            val storageRef = Firebase.storage.reference.child("profileImages/${currentUser.uid}.jpg")
                            storageRef.putFile(selectedImageUri!!)
                                .addOnSuccessListener {
                                    storageRef.downloadUrl.addOnSuccessListener { uri ->
                                        updates["profileImage"] = uri.toString()
                                        saveProfileData(updates, loadingDialog, dialog, saveButton)
                                    }
                                }
                                .addOnFailureListener {
                                    loadingDialog.dismiss()
                                    saveButton.isEnabled = true
                                    Toast.makeText(requireContext(), "Image upload failed", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            saveProfileData(updates, loadingDialog, dialog, saveButton)
                        }
                    }
                }

                dialog.show()
            }
    }

    private fun saveProfileData(
        updates: Map<String, Any>,
        loadingDialog: AlertDialog,
        dialog: AlertDialog,
        saveButton: Button
    ) {
        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid)
            .update(updates)
            .addOnSuccessListener {
                loadingDialog.dismiss()
                Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                loadProfile()
            }
            .addOnFailureListener {
                loadingDialog.dismiss()
                saveButton.isEnabled = true
                Toast.makeText(requireContext(), "Update failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createLoadingDialog(): AlertDialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading, null)
        view.findViewById<TextView>(R.id.loadingText)?.text = "Loading..."

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
