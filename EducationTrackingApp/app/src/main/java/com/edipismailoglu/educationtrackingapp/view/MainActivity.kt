package com.edipismailoglu.educationtrackingapp.view

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.edipismailoglu.educationtrackingapp.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var navController: NavController
    private val db = Firebase.firestore
    private lateinit var bottomNav: BottomNavigationView

    var currentUserType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = Firebase.auth
        bottomNav = findViewById(R.id.bottomNavigationView)
        hideBottomNav()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        navController = navHostFragment.navController

        setupNavigationListener()
        setupBottomNav()

        val currentUser = auth.currentUser
        if (currentUser != null && currentUserType == null) {
            checkUserTypeAndNavigate(currentUser.uid)
        } else if (currentUser == null) {
            navController.navigate(R.id.firstFragment)
        }
    }

    private fun setupNavigationListener() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.firstFragment,
                R.id.studentUserFragment,
                R.id.teacherUserFragment,
                R.id.parentUserFragment -> {
                    hideBottomNav()
                    currentUserType = null
                }
                else -> {
                    if (!currentUserType.isNullOrEmpty()) {
                        showBottomNav()
                    }
                }
            }
        }
    }

    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            val userType = currentUserType

            if (userType.isNullOrEmpty()) {
                showUnknownUserType()
                return@setOnItemSelectedListener false
            }

            when (item.itemId) {
                R.id.menu_home -> {
                    navigateToHome(userType)
                    true
                }
                R.id.menu_tasks -> {
                    navigateToTasks(userType)
                    true
                }
                R.id.menu_profile -> {
                    navigateToProfile(userType)
                    true
                }
                else -> false
            }
        }
    }

    private fun navigateToHome(userType: String) {
        val bundle = Bundle().apply { putString("userType", userType) }
        when (userType) {
            "teacher" -> navController.navigate(R.id.homeFragment, bundle)
            "student" -> navController.navigate(R.id.studentHomeFragment, bundle)
            "parent" -> navController.navigate(R.id.parentHomeFragment, bundle)
            else -> showUnknownUserType()
        }
    }

    private fun navigateToTasks(userType: String) {
        val bundle = Bundle().apply { putString("userType", userType) }
        when (userType) {
            "teacher" -> navController.navigate(R.id.teacherFeedFragment, bundle)
            "student" -> navController.navigate(R.id.studentFeedFragment, bundle)
            "parent" -> navController.navigate(R.id.parentFeedFragment, bundle)
            else -> showUnknownUserType()
        }
    }

    private fun navigateToProfile(userType: String) {
        val bundle = Bundle().apply { putString("userType", userType) }
        when (userType) {
            "teacher" -> navController.navigate(R.id.profileFragment, bundle)
            "student" -> navController.navigate(R.id.studentProfileFragment, bundle)
            "parent" -> navController.navigate(R.id.parentProfileFragment, bundle)
            else -> showUnknownUserType()
        }
    }

     fun checkUserTypeAndNavigate(userId: String) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userType = document.getString("userType") ?: "unknown"
                    currentUserType = userType

                    when (userType) {
                        "teacher" -> navController.navigate(R.id.homeFragment, Bundle().apply { putString("userType", userType) })
                        "student" -> navController.navigate(R.id.studentHomeFragment, Bundle().apply { putString("userType", userType) })
                        "parent" -> navController.navigate(R.id.parentHomeFragment, Bundle().apply { putString("userType", userType) })
                        else -> {
                            Toast.makeText(this, "Unknown user type", Toast.LENGTH_SHORT).show()
                            navController.navigate(R.id.firstFragment)
                        }
                    }

                    showBottomNav()
                } else {
                    auth.signOut()
                    navController.navigate(R.id.studentUserFragment)
                }
            }
            .addOnFailureListener {
                auth.signOut()
                navController.navigate(R.id.studentUserFragment)
            }
    }

    private fun showUnknownUserType() {
        Toast.makeText(this, "Unknown user type", Toast.LENGTH_SHORT).show()
    }

    fun hideBottomNav() {
        bottomNav.visibility = View.GONE
    }

    fun showBottomNav() {
        bottomNav.visibility = View.VISIBLE
    }

    fun setSelectedBottomNavItem(itemId: Int) {
        bottomNav.selectedItemId = itemId
    }
}
