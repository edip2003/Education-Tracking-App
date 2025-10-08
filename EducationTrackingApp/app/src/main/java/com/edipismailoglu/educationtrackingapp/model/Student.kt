package com.edipismailoglu.educationtrackingapp.model

data class Student(
    val name: String = "",
    val email: String = "",
    val academicStage: String = "",
    val gradeLevel: String = "",
    val userType: String = "student"
)
