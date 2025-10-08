package com.edipismailoglu.educationtrackingapp.model

data class Post(
    val email: String = "",
    val comment: String = "",
    val downloadUri: String = "",
    val uploadDate: String = "",
    val finishDate: String = "",
    val postId: String = "",
    val targetClass: String = ""
) {
    constructor() : this("", "", "", "", "", "", "")
}