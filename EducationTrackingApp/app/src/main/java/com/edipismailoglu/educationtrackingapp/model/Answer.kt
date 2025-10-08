package com.edipismailoglu.educationtrackingapp.model

data class Answer(
    val studentEmail: String = "",
    var answerText: String = "",
    val uploadDate: String = "",
    var grade: Int? = null,
    val taskId: String = "",
    var fileUrl: String? = null,
    var imageUrl: String? = null, // صورة الإجابة

    // بيانات إضافية للمهمة
    var taskOwnerEmail: String? = null, // إيميل المعلم صاحب المهمة
    var taskDescriptionText: String? = null,
    var taskUploadDate: String? = null,
    var taskFinishDate: String? = null,
    var taskImageUrl: String? = null, // صورة المهمة
    val taskTeacherEmail: String? = null

)
