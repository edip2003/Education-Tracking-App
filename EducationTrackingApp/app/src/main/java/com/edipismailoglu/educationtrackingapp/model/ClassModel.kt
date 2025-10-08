package com.edipismailoglu.educationtrackingapp.model

data class ClassModel(
    val academicStage: String = "",
    val gradeLevel: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    var id: String = ""
)
