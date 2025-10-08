package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.edipismailoglu.educationtrackingapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class SplashFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore
    private var hasNavigated = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (hasNavigated) return

        auth = Firebase.auth

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isAdded || context == null || hasNavigated) return@postDelayed

            val navController = findNavController()
            val currentUser = auth.currentUser

            if (currentUser != null) {
                db.collection("users").document(currentUser.uid)
                    .get()
                    .addOnSuccessListener { document ->
                        if (!isAdded || hasNavigated) return@addOnSuccessListener

                        val userType = document.getString("userType")
                        val destinationId = when (userType) {
                            "student" -> R.id.studentHomeFragment
                            "parent" -> R.id.parentFeedFragment
                            "teacher" -> R.id.homeFragment // ✅ teacher now goes to home
                            else -> R.id.firstFragment
                        }

                        hasNavigated = true
                        navController.navigate(destinationId)
                    }
                    .addOnFailureListener {
                        if (!isAdded || hasNavigated) return@addOnFailureListener
                        hasNavigated = true
                        auth.signOut()
                        findNavController().navigate(R.id.firstFragment)
                    }
            } else {
                hasNavigated = true
                findNavController().navigate(R.id.firstFragment)
            }
        }, 1500)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hasNavigated = false
    }
}
