package com.example.notifications

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.AppMessage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object NotificationRepository {
    private const val TAG = "NotificationRepository"
    private const val COLLECTION_NOTIFICATIONS = "notifications"
    private const val COLLECTION_FCM_TOKENS = "fcm_tokens"
    private const val PREFS_NOTIFICATIONS = "fitkuchnia_notifications_store"
    private const val KEY_CACHED_MESSAGES = "cached_messages_json"
    private const val KEY_READ_MESSAGE_IDS = "read_message_ids"
    private const val KEY_LAST_NOTIFIED_TIMESTAMP = "last_notified_timestamp"

    private val _messagesFlow = MutableStateFlow<List<AppMessage>>(emptyList())
    val messagesFlow: StateFlow<List<AppMessage>> = _messagesFlow.asStateFlow()

    private var snapshotListener: ListenerRegistration? = null
    private var isListening = false
    private var currentActiveUserEmail: String? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NOTIFICATIONS, Context.MODE_PRIVATE)
    }

    /**
     * Inicjalizacja lokalnego bufora wiadomości
     */
    fun initialize(context: Context) {
        val cached = loadCachedMessages(context)
        if (cached.isNotEmpty()) {
            _messagesFlow.value = cached
        }
    }

    /**
     * Rejestracja tokenu urządzenia FCM w bazie Firestore oraz subskrypcja do tematu all_users
     */
    fun registerDeviceToken(context: Context, userEmail: String? = null) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    Log.d(TAG, "Pobrano token FCM: $token")
                    try {
                        val db = FirebaseFirestore.getInstance()
                        val tokenData = hashMapOf(
                            "token" to token,
                            "userEmail" to (userEmail ?: "anonymous"),
                            "platform" to "android",
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "updatedAtMillis" to System.currentTimeMillis()
                        )
                        db.collection(COLLECTION_FCM_TOKENS)
                            .document(token)
                            .set(tokenData)
                            .addOnSuccessListener {
                                Log.d(TAG, "Token FCM pomyślnie zarejestrowany w Firestore")
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Nie udało się zapisać tokenu FCM w Firestore: ${e.message}")
                            }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Błąd rejestracji tokenu w Firestore: ${e.message}")
                    }

                    FitKuchniaMessagingService.subscribeToAllUsersTopic(context)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Nie udało się pobrać tokenu FCM: ${e.message}")
                }
        } catch (e: Throwable) {
            Log.w(TAG, "Wyjątek podczas pobierania tokenu FCM", e)
        }
    }

    /**
     * Rozpoczyna nasłuchiwanie w czasie rzeczywistym na nowe wiadomości w Firestore.
     * Gdy pojawi się nowa wiadomość, aplikacja natychmiast wyświetla powiadomienie systemowe
     * i aktualizuje listę wiadomości.
     */
    fun startRealtimeListener(context: Context, userEmail: String? = null) {
        currentActiveUserEmail = userEmail
        registerDeviceToken(context, userEmail)

        if (isListening && snapshotListener != null) {
            return
        }

        try {
            val db = FirebaseFirestore.getInstance()
            val prefs = getPrefs(context)
            var lastNotifiedTime = prefs.getLong(KEY_LAST_NOTIFIED_TIMESTAMP, 0L)

            // Jeśli pierwsze uruchomienie, ustaw znacznik na teraz, aby nie zalewać starymi powiadomieniami
            if (lastNotifiedTime == 0L) {
                lastNotifiedTime = System.currentTimeMillis() - (1000L * 60 * 60 * 24 * 7) // 7 dni wstecz
                prefs.edit().putLong(KEY_LAST_NOTIFIED_TIMESTAMP, lastNotifiedTime).apply()
            }

            isListening = true
            snapshotListener = db.collection(COLLECTION_NOTIFICATIONS)
                .orderBy("createdAtMillis", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w(TAG, "Błąd nasłuchiwania Firestore notifications: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        val readIds = getPrefs(context).getStringSet(KEY_READ_MESSAGE_IDS, emptySet()) ?: emptySet()
                        val currentTimestamp = getPrefs(context).getLong(KEY_LAST_NOTIFIED_TIMESTAMP, 0L)
                        var maxTimestampSeen = currentTimestamp

                        val messages = snapshots.documents.mapNotNull { doc ->
                            try {
                                val id = doc.getString("id") ?: doc.id
                                val title = doc.getString("title") ?: "Wiadomość FitKuchnia"
                                val body = doc.getString("body")
                                    ?: doc.getString("message")
                                    ?: ""
                                val sentBy = doc.getString("sentBy") ?: "Administrator"
                                val createdAt = doc.getLong("createdAtMillis") ?: System.currentTimeMillis()
                                val attachmentUrl = doc.getString("attachmentUrl")
                                val attachmentName = doc.getString("attachmentName")
                                val attachmentType = doc.getString("attachmentType")
                                val isRead = readIds.contains(id)

                                AppMessage(
                                    id = id,
                                    title = title,
                                    body = body,
                                    sentBy = sentBy,
                                    createdAtMillis = createdAt,
                                    attachmentUrl = attachmentUrl,
                                    attachmentName = attachmentName,
                                    attachmentType = attachmentType,
                                    isRead = isRead
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Błąd parsowania dokumentu powiadomienia", e)
                                null
                            }
                        }

                        // Sprawdź, czy są nowe powiadomienia do wyświetlenia na pasku stanu
                        for (msg in messages) {
                            if (msg.createdAtMillis > currentTimestamp) {
                                // Sprawdź czy to nie wiadomość wysłana przez samego zalogowanego admina na tym urządzeniu
                                val isSelfSent = currentActiveUserEmail != null &&
                                        msg.sentBy.equals(currentActiveUserEmail, ignoreCase = true)

                                if (!isSelfSent) {
                                    Log.d(TAG, "Wykryto nowe powiadomienie od admina: ${msg.title}")
                                    FitKuchniaMessagingService.showNotification(
                                        context = context,
                                        title = msg.title,
                                        body = if (msg.hasAttachment) "${msg.body} 📎 [Załącznik]" else msg.body
                                    )
                                }
                                if (msg.createdAtMillis > maxTimestampSeen) {
                                    maxTimestampSeen = msg.createdAtMillis
                                }
                            }
                        }

                        if (maxTimestampSeen > currentTimestamp) {
                            getPrefs(context).edit().putLong(KEY_LAST_NOTIFIED_TIMESTAMP, maxTimestampSeen).apply()
                        }

                        _messagesFlow.value = messages
                        saveCachedMessages(context, messages)
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Błąd uruchamiania nasłuchiwania Firestore notifications", e)
        }
    }

    fun stopListener() {
        snapshotListener?.remove()
        snapshotListener = null
        isListening = false
    }

    /**
     * Wysłanie wiadomości/powiadomienia przez Administratora
     */
    fun sendAdminMessage(
        context: Context,
        title: String,
        body: String,
        adminEmail: String,
        attachmentUrl: String? = null,
        attachmentName: String? = null,
        attachmentType: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            val messageId = UUID.randomUUID().toString()
            val nowMillis = System.currentTimeMillis()

            val cleanAttachmentUrl = attachmentUrl?.trim()?.ifBlank { null }
            val cleanAttachmentName = attachmentName?.trim()?.ifBlank { null }
            val detectedType = attachmentType ?: if (cleanAttachmentUrl?.endsWith(".apk", ignoreCase = true) == true) "apk" else "file"

            val messageData = hashMapOf(
                "id" to messageId,
                "title" to title.trim(),
                "body" to body.trim(),
                "message" to body.trim(),
                "sentBy" to adminEmail.trim(),
                "topic" to "all_users",
                "createdAtMillis" to nowMillis,
                "timestamp" to FieldValue.serverTimestamp(),
                "attachmentUrl" to cleanAttachmentUrl,
                "attachmentName" to cleanAttachmentName,
                "attachmentType" to if (cleanAttachmentUrl != null) detectedType else null
            )

            db.collection(COLLECTION_NOTIFICATIONS)
                .document(messageId)
                .set(messageData)
                .addOnSuccessListener {
                    Log.d(TAG, "Wiadomość admina pomyślnie wysłana do kolekcji notifications: $messageId")

                    // Aktualizacja lokalnej listy dla natychmiastowej responsywności UI
                    val newMessage = AppMessage(
                        id = messageId,
                        title = title.trim(),
                        body = body.trim(),
                        sentBy = adminEmail.trim(),
                        createdAtMillis = nowMillis,
                        attachmentUrl = cleanAttachmentUrl,
                        attachmentName = cleanAttachmentName,
                        attachmentType = if (cleanAttachmentUrl != null) detectedType else null,
                        isRead = true
                    )
                    _messagesFlow.update { current ->
                        listOf(newMessage) + current.filterNot { it.id == messageId }
                    }
                    saveCachedMessages(context, _messagesFlow.value)

                    // Pokaż powiadomienie lokalne na urządzeniu administratora jako potwierdzenie
                    FitKuchniaMessagingService.showNotification(
                        context,
                        "Wysłano: ${title.trim()}",
                        body.trim()
                    )

                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Błąd wysyłania wiadomości przez admina", e)
                    onError("Błąd bazy danych Firebase: ${e.localizedMessage ?: "brak połączenia"}")
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Krytyczny błąd w sendAdminMessage", e)
            onError("Błąd: ${e.localizedMessage ?: "Nieoczekiwany wyjątek"}")
        }
    }

    fun markMessageAsRead(context: Context, messageId: String) {
        val prefs = getPrefs(context)
        val currentRead = prefs.getStringSet(KEY_READ_MESSAGE_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (currentRead.add(messageId)) {
            prefs.edit().putStringSet(KEY_READ_MESSAGE_IDS, currentRead).apply()
            _messagesFlow.update { list ->
                list.map { if (it.id == messageId) it.copy(isRead = true) else it }
            }
        }
    }

    fun markAllAsRead(context: Context) {
        val allIds = _messagesFlow.value.map { it.id }.toSet()
        val prefs = getPrefs(context)
        prefs.edit().putStringSet(KEY_READ_MESSAGE_IDS, allIds).apply()
        _messagesFlow.update { list ->
            list.map { it.copy(isRead = true) }
        }
    }

    private fun loadCachedMessages(context: Context): List<AppMessage> {
        return try {
            val json = getPrefs(context).getString(KEY_CACHED_MESSAGES, null) ?: return emptyList()
            val array = JSONArray(json)
            val readIds = getPrefs(context).getStringSet(KEY_READ_MESSAGE_IDS, emptySet()) ?: emptySet()
            val result = mutableListOf<AppMessage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                result.add(
                    AppMessage(
                        id = id,
                        title = obj.optString("title"),
                        body = obj.optString("body"),
                        sentBy = obj.optString("sentBy", "Administrator"),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                        attachmentUrl = obj.optString("attachmentUrl").takeIf { it.isNotBlank() },
                        attachmentName = obj.optString("attachmentName").takeIf { it.isNotBlank() },
                        attachmentType = obj.optString("attachmentType").takeIf { it.isNotBlank() },
                        isRead = readIds.contains(id)
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Błąd odczytu bufora wiadomości: ${e.message}")
            emptyList()
        }
    }

    private fun saveCachedMessages(context: Context, messages: List<AppMessage>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val array = JSONArray()
                for (m in messages.take(50)) {
                    val obj = JSONObject().apply {
                        put("id", m.id)
                        put("title", m.title)
                        put("body", m.body)
                        put("sentBy", m.sentBy)
                        put("createdAtMillis", m.createdAtMillis)
                        put("attachmentUrl", m.attachmentUrl ?: "")
                        put("attachmentName", m.attachmentName ?: "")
                        put("attachmentType", m.attachmentType ?: "")
                    }
                    array.put(obj)
                }
                getPrefs(context).edit().putString(KEY_CACHED_MESSAGES, array.toString()).apply()
            } catch (e: Exception) {
                Log.w(TAG, "Błąd zapisu bufora wiadomości: ${e.message}")
            }
        }
    }
}
