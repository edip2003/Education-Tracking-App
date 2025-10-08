package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.navigation.Navigation
import com.edipismailoglu.educationtrackingapp.R
import com.edipismailoglu.educationtrackingapp.databinding.FragmentFirstBinding


class FirstFragment : Fragment() {
    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private var backPressedTime: Long = 0
    private val BACK_PRESS_INTERVAL = 2000



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // BottomNavigationView Gizlemek🔒
        (activity as? MainActivity)?.hideBottomNav()

        binding.studentBtn.setOnClickListener { studentEntryClicked(it) }
        binding.teacherBtn.setOnClickListener { teacherEntryClicked(it) }
        binding.parentBtn.setOnClickListener { parentEntryClicked(it) }
        // Retrieve the userType argument
        val userType = arguments?.getString("userType") ?: "unknown"
        // For debugging, print the userType or use it in your UI
        println("User type received: $userType")
        // Example: Set a TextView to display the userType

        //Çıkış Kontrol Mantığı
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < BACK_PRESS_INTERVAL) {
                    requireActivity().finishAffinity() // Close the app completely
                } else {
                    backPressedTime = currentTime
                    Toast.makeText(requireContext(), "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        })

    }
    fun studentEntryClicked(view: View){
        // Create a bundle to pass the userType argument
        val args = Bundle().apply {
            putString("userType", "student")
        }

        // Navigate to StudentUserFragment using the action
        Navigation.findNavController(view).navigate(
            R.id.action_firstFragment_to_studentUserFragment,
            args
        )

    }
    fun teacherEntryClicked(view: View){
        // Create a bundle to pass the userType argument
        val args = Bundle().apply {
            putString("userType", "teacher")
        }

        // Navigate to teacherUserFragment using the action
        Navigation.findNavController(view).navigate(
            R.id.action_firstFragment_to_teacherUserFragment,
            args
        )

    }
    fun parentEntryClicked(view: View){

        // Create a bundle to pass the userType argument
        val args = Bundle().apply {
            putString("userType", "parent")
        }

        // Navigate to ParentUserFragment using the action
        Navigation.findNavController(view).navigate(
            R.id.action_firstFragment_to_parentUserFragment,
            args
        )

    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding=null

    }

    }
