package com.edipismailoglu.educationtrackingapp.view

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.databinding.FragmentUploadBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import java.util.*

class UploadFragment : Fragment() {

    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null.")

    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    private var selectedImageUri: Uri? = null
    private var selectedBitmap: Bitmap? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        storage = Firebase.storage
        db = Firebase.firestore
        registerLaunchers()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTargetClassDropdown()

        val args = arguments
        val academicStage = args?.getString("academicStage")
        val gradeLevel = args?.getString("gradeLevel")

        if (!academicStage.isNullOrEmpty() && !gradeLevel.isNullOrEmpty()) {
            val targetClass = "$academicStage - $gradeLevel"
            binding.uploadTargetClassText.setText(targetClass)
            binding.uploadTargetClassText.isEnabled = false // Class passed from MyClassesFragment, cannot change
        } else {
            setupTargetClassDropdown()
            binding.uploadTargetClassText.isEnabled = true
        }

        binding.uploadButton.setOnClickListener { uploadClicked(it) }
        binding.uploadImageView.setOnClickListener { imageViewClicked(it) }

        binding.uploadFinishDateText.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedDate = String.format("%02d/%02d/%04d", day, month + 1, year)
                    binding.uploadFinishDateText.setText(selectedDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = calendar.timeInMillis
                show()
            }
        }

        binding.uploadFinishTimeText.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    val formattedTime = String.format("%02d:%02d", hour, minute)
                    binding.uploadFinishTimeText.setText(formattedTime)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }
    }

    private fun setupTargetClassDropdown() {
        val currentUser = auth.currentUser ?: return
        val classList = mutableListOf<String>()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, classList)
        binding.uploadTargetClassText.setAdapter(adapter)

        db.collection("Classes")
            .whereEqualTo("createdBy", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val stage = doc.getString("academicStage") ?: continue
                    val grade = doc.getString("gradeLevel") ?: continue
                    classList.add("$stage - $grade")
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to load classes: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun uploadClicked(view: View) {
        if (!isAdded || _binding == null) return

        binding.uploadButton.isEnabled = false

        val commentInput = binding.uploadCommentText.text.toString().trim()
        val raw = binding.uploadTargetClassText.text.toString().trim()
        val targetClass = raw.split("-").joinToString(" - ") { it.trim().lowercase().replaceFirstChar { c -> c.uppercase() } }
        val finishDateInput = binding.uploadFinishDateText.text.toString().trim()
        val finishTimeInput = binding.uploadFinishTimeText.text.toString().trim()

        var isValid = true

        if (selectedImageUri == null) {
            Toast.makeText(requireContext(), "Please select an image!", Toast.LENGTH_SHORT).show()
            isValid = false
        }
        if (commentInput.isEmpty()) {
            binding.uploadCommentText.error = "Comment is required"
            isValid = false
        }
        if (targetClass.isEmpty()) {
            binding.uploadTargetClassText.error = "Target class is required"
            isValid = false
        }
        if (finishDateInput.isEmpty()) {
            binding.uploadFinishDateText.error = "Finish date is required"
            isValid = false
        }
        if (finishTimeInput.isEmpty()) {
            binding.uploadFinishTimeText.error = "Finish time is required"
            isValid = false
        }

        if (!isValid) {
            binding.uploadButton.isEnabled = true
            return
        }

        val fullFinishDate = "$finishDateInput $finishTimeInput"
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "You are not logged in.", Toast.LENGTH_SHORT).show()
            binding.uploadButton.isEnabled = true
            return
        }

        val loadingView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading, null)
        val loadingDialog = AlertDialog.Builder(requireContext())
            .setView(loadingView)
            .setCancelable(false)
            .create()
        loadingDialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        loadingDialog.show()

        val uuid = UUID.randomUUID().toString()
        val imageName = "$uuid.jpg"
        val storageRef = storage.reference.child("images").child(imageName)

        selectedImageUri?.let { uri ->
            storageRef.putFile(uri)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        if (!isAdded || _binding == null) {
                            loadingDialog.dismiss()
                            return@addOnSuccessListener
                        }

                        val postMap = hashMapOf(
                            "downloadUrl" to downloadUri.toString(),
                            "email" to currentUser.email.orEmpty(),
                            "comment" to commentInput,
                            "targetClass" to targetClass.trim(),
                            "uploadDate" to Timestamp.now(),
                            "finishDate" to fullFinishDate
                        )

                        db.collection("Posts").add(postMap)
                            .addOnSuccessListener {
                                loadingDialog.dismiss()
                                Toast.makeText(requireContext(), "Task uploaded successfully!", Toast.LENGTH_SHORT).show()
                                Navigation.findNavController(view).previousBackStackEntry?.savedStateHandle?.set("post_added", true)

                                val userType = arguments?.getString("userType") ?: "teacher"
                                val bundle = Bundle().apply {
                                    putString("userType", userType)
                                }
                                Navigation.findNavController(view).navigate(R.id.action_uploadFragment_to_feedFragment, bundle)
                            }
                            .addOnFailureListener { e ->
                                loadingDialog.dismiss()
                                Toast.makeText(requireContext(), "Failed to upload task: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                binding.uploadButton.isEnabled = true
                            }
                    }.addOnFailureListener {
                        loadingDialog.dismiss()
                        Toast.makeText(requireContext(), "Failed to get image URL.", Toast.LENGTH_SHORT).show()
                        binding.uploadButton.isEnabled = true
                    }
                }
                .addOnFailureListener { e ->
                    loadingDialog.dismiss()
                    Toast.makeText(requireContext(), "Image upload failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    binding.uploadButton.isEnabled = true
                }
        }
    }

    private fun imageViewClicked(view: View) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permission)) {
                Snackbar.make(view, "You need to grant permission to access the gallery.", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Grant") { permissionLauncher.launch(permission) }
                    .show()
            } else {
                permissionLauncher.launch(permission)
            }
        } else {
            val intentToGallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            activityResultLauncher.launch(intentToGallery)
        }
    }

    private fun registerLaunchers() {
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val intentFromResult = result.data
                if (intentFromResult != null) {
                    selectedImageUri = intentFromResult.data
                    try {
                        selectedBitmap = if (Build.VERSION.SDK_INT >= 28) {
                            val source = ImageDecoder.createSource(requireActivity().contentResolver, selectedImageUri!!)
                            ImageDecoder.decodeBitmap(source)
                        } else {
                            MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, selectedImageUri)
                        }
                        binding.uploadImageView.setImageBitmap(selectedBitmap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            if (result) {
                val intentToGallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intentToGallery)
            } else {
                Toast.makeText(requireContext(), "Permission denied. Cannot open gallery.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
