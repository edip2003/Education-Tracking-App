package com.edipismailoglu.educationtrackingapp.view

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.edipismailoglu.educationtrackingapp.R
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.squareup.picasso.Picasso

class ParentProfileFragment : Fragment() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val storage = Firebase.storage

    private lateinit var userNameText: TextView
    private lateinit var userEmailText: TextView
    private lateinit var userAgeText: TextView
    private lateinit var userGenderText: TextView
    private lateinit var editProfileButton: Button
    private lateinit var logOutButton: Button
    private lateinit var profileImage: ImageView

    private var selectedImageUri: Uri? = null
    private var tempImageView: ImageView? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                tempImageView?.setImageURI(uri)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_parent_profile, container, false)

        userNameText = view.findViewById(R.id.userNameText)
        userEmailText = view.findViewById(R.id.userEmailText)
        userAgeText = view.findViewById(R.id.userAgeText)
        userGenderText = view.findViewById(R.id.userGenderText)
        editProfileButton = view.findViewById(R.id.editProfileButton)
        logOutButton = view.findViewById(R.id.logOutButton)
        profileImage = view.findViewById(R.id.profilePhotoImageView)

        loadParentData()

        editProfileButton.setOnClickListener { showEditProfileDialog() }
        logOutButton.setOnClickListener { confirmLogout() }

        return view
    }

    private fun loadParentData() {
        val currentUser = auth.currentUser ?: return

        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                if (!doc.exists()) return@addOnSuccessListener

                val name = doc.getString("name") ?: ""
                val surname = doc.getString("surname") ?: ""
                val email = doc.getString("email") ?: ""
                val age = doc.getString("age") ?: ""
                val sex = doc.getString("sex") ?: ""
                val imageUrl = doc.getString("profileImage")

                userNameText.text = "$name $surname"
                userEmailText.text = email
                userAgeText.text = "Age: $age"
                userGenderText.text = "Gender: $sex"

                if (!imageUrl.isNullOrEmpty()) {
                    Picasso.get().load(imageUrl).into(profileImage)
                } else {
                    profileImage.setImageResource(R.drawable.ic_default_profile)
                }
            }
    }

    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_parent_info, null)
        val editName = dialogView.findViewById<EditText>(R.id.editName)
        val editSurname = dialogView.findViewById<EditText>(R.id.editSurname)
        val editAge = dialogView.findViewById<EditText>(R.id.editAge)
        val editSex = dialogView.findViewById<AutoCompleteTextView>(R.id.editSex)
        val editEmail = dialogView.findViewById<EditText>(R.id.editEmail)
        val editPassword = dialogView.findViewById<EditText>(R.id.editPassword)
        val passwordLayout = dialogView.findViewById<LinearLayout>(R.id.passwordLayout)
        val profileImageView = dialogView.findViewById<ImageView>(R.id.profilePhotoImageView)
        val uploadPhotoButton = dialogView.findViewById<Button>(R.id.uploadPhotoButton)

        passwordLayout.visibility = View.GONE
        editEmail.isEnabled = false
        tempImageView = profileImageView

        val genderOptions = listOf("Male", "Female")
        val genderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, genderOptions)
        editSex.setAdapter(genderAdapter)
        editSex.setOnClickListener { editSex.showDropDown() }
        editSex.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) editSex.showDropDown() }

        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                editName.setText(doc.getString("name") ?: "")
                editSurname.setText(doc.getString("surname") ?: "")
                editAge.setText(doc.getString("age") ?: "")
                editSex.setText(doc.getString("sex") ?: "")
                editEmail.setText(doc.getString("email") ?: "")

                doc.getString("profileImage")?.let {
                    Picasso.get().load(it).into(profileImageView)
                }

                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("Edit Profile")
                    .setView(dialogView)
                    .setPositiveButton("Save", null)
                    .setNegativeButton("Cancel", null)
                    .create()

                dialog.show()

                uploadPhotoButton.setOnClickListener {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    imagePickerLauncher.launch(intent)
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = editName.text.toString().trim()
                    val surname = editSurname.text.toString().trim()
                    val age = editAge.text.toString().trim()
                    val sex = editSex.text.toString().trim()

                    if (name.isEmpty() || surname.isEmpty() || age.isEmpty() || sex.isEmpty()) {
                        Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (age.toIntOrNull() == null || age.toInt() !in 18..80) {
                        Toast.makeText(requireContext(), "Please enter a valid age between 18 and 80", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (sex != "Male" && sex != "Female") {
                        Toast.makeText(requireContext(), "Gender must be either 'Male' or 'Female'", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val updates = mutableMapOf<String, Any>(
                        "name" to name,
                        "surname" to surname,
                        "age" to age,
                        "sex" to sex
                    )

                    if (selectedImageUri != null) {
                        val storageRef = storage.reference.child("profileImages/${currentUser.uid}.jpg")
                        storageRef.putFile(selectedImageUri!!)
                            .addOnSuccessListener {
                                storageRef.downloadUrl.addOnSuccessListener { uri ->
                                    updates["profileImage"] = uri.toString()
                                    applyProfileUpdates(updates, dialog)
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(requireContext(), "Image upload failed", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        applyProfileUpdates(updates, dialog)
                    }
                }
            }
    }

    private fun applyProfileUpdates(updates: Map<String, Any>, dialog: AlertDialog) {
        val currentUser = auth.currentUser ?: return

        val loadingDialog = createLoadingDialog()
        loadingDialog.show()

        db.collection("users").document(currentUser.uid)
            .update(updates)
            .addOnSuccessListener {
                loadingDialog.dismiss()
                Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                loadParentData()
            }
            .addOnFailureListener {
                loadingDialog.dismiss()
                Toast.makeText(requireContext(), "Update failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle("Log out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes") { _, _ ->

                val loadingView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading, null)
                val loadingText = loadingView.findViewById<TextView>(R.id.loadingText)
                loadingText.text = "Loading..."

                val loadingDialog = AlertDialog.Builder(requireContext())
                    .setView(loadingView)
                    .setCancelable(false)
                    .create()
                loadingDialog.show()

                auth.signOut()

                logOutButton.postDelayed({
                    loadingDialog.dismiss()
                    requireActivity().recreate()
                    Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
                }, 1000)

            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createLoadingDialog(): AlertDialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading, null)
        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
    }
}
