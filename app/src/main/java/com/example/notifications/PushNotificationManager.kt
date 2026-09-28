package com.example.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

object PushNotificationManager {
    private const val TAG = "PushNotificationManager"

    fun sendNotificationToAllUsers(
        context: Context,
        title: String,
        message: String,
        adminEmail: String,
        attachmentUrl: String? = null,
        attachmentName: String? = null,
        attachmentType: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        NotificationRepository.sendAdminMessage(
            context = context,
            title = title,
            body = message,
            adminEmail = adminEmail,
            attachmentUrl = attachmentUrl,
            attachmentName = attachmentName,
            attachmentType = attachmentType,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}
