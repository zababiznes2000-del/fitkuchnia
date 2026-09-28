package com.example.data.model

data class AppMessage(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val sentBy: String = "Administrator",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val attachmentUrl: String? = null,
    val attachmentName: String? = null,
    val attachmentType: String? = null, // "apk", "document", "file"
    val isRead: Boolean = false
) {
    val isApkAttachment: Boolean
        get() = attachmentType == "apk" ||
                attachmentType == "zip" ||
                attachmentUrl?.endsWith(".apk", ignoreCase = true) == true ||
                attachmentUrl?.endsWith(".zip", ignoreCase = true) == true ||
                attachmentName?.endsWith(".apk", ignoreCase = true) == true ||
                attachmentName?.endsWith(".zip", ignoreCase = true) == true ||
                title.contains("aktualizacj", ignoreCase = true) ||
                title.contains("nowa wersja", ignoreCase = true)

    val isZipAttachment: Boolean
        get() = false // Zgodnie z wymaganiem użytkownika: aktualizacje to zawsze bezpośrednie pliki APK

    val hasAttachment: Boolean
        get() = !attachmentUrl.isNullOrBlank()

    val hasInstallGuide: Boolean
        get() = body.contains("INSTRUKCJA INSTALACJI", ignoreCase = true) ||
                body.contains("Instrukcja instalacji", ignoreCase = true) ||
                isApkAttachment

    /**
     * Zwraca bezpośredni adres pliku APK (zabezpiecza przed linkami .zip).
     */
    val effectiveDownloadUrl: String
        get() {
            val url = attachmentUrl?.trim() ?: ""
            return if (url.endsWith(".zip", ignoreCase = true)) {
                url.substringBeforeLast(".zip") + ".apk"
            } else {
                url
            }
        }

    val safeAttachmentName: String
        get() {
            val raw = attachmentName?.trim()?.ifBlank { null }
            if (raw != null) {
                return if (raw.endsWith(".zip", ignoreCase = true)) {
                    raw.substringBeforeLast(".zip") + ".apk"
                } else if (!raw.endsWith(".apk", ignoreCase = true) && isApkAttachment) {
                    "$raw.apk"
                } else {
                    raw
                }
            }
            return if (isApkAttachment) "FitKuchnia-aktualizacja.apk" else "Załącznik"
        }
}
