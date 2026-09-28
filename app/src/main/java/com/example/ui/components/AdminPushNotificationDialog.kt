package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.data.UserAccount
import com.example.notifications.PushNotificationManager
import com.example.ui.theme.*
import com.example.util.AppVersionManager

@Composable
fun AdminPushNotificationDialog(
    adminEmail: String,
    onDismiss: () -> Unit,
    onNotificationSent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var isPasswordVerified by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    var notificationTitle by remember { mutableStateOf("") }
    var notificationBody by remember { mutableStateOf("") }
    var attachmentUrl by remember { mutableStateOf("") }
    var attachmentName by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    val defaultInstallInstructions = """
📱 INSTRUKCJA INSTALACJI AKTUALIZACJI NA TELEFONIE (KROK PO KROKU):
1. Dotknij przycisku „Pobierz plik APK” w tej wiadomości.
2. Po zakończeniu pobierania dotknij pobranego pliku w pasku powiadomień lub w folderze „Pobrane” (Downloads).
3. Kliknij pobrany plik instalacyjny FitKuchnia.apk, aby od razu rozpocząć instalację (bez konieczności rozpakowywania).
4. Jeśli system Android wyświetli zapytanie o instalację z tego źródła:
   • Kliknij „Ustawienia” w oknie komunikatu.
   • Włącz przełącznik „Zezwalaj z tego źródła”.
   • Wróć jednym gestem i kliknij „Zainstaluj” (lub „Aktualizuj”).
5. Gotowe! Otwórz nową wersję FitKuchnia. Wszystkie Twoje przepisy i dane zostaną zachowane!
""".trimIndent()

    var includeInstallGuide by remember { mutableStateOf(false) }
    var installGuideText by remember { mutableStateOf(defaultInstallInstructions) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = !isSending, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberEnergy.copy(alpha = 0.6f)),
            tonalElevation = 10.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("admin_push_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isPasswordVerified) {
                    // Faza 1: Weryfikacja hasła Administratora
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AmberEnergy.copy(alpha = 0.15f))
                            .border(2.dp, AmberEnergy, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Zabezpieczenie hasłem",
                            tint = AmberEnergy,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Autoryzacja Administratora",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Wysyłanie powiadomień push do wszystkich użytkowników wymaga podania hasła administratora.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            authError = null
                        },
                        label = { Text("Hasło administratora") },
                        placeholder = { Text("Wpisz hasło") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = AmberEnergy)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                if (passwordInput.trim() == UserAccount.ADMIN_PASSWORD_EXACT) {
                                    isPasswordVerified = true
                                    authError = null
                                } else {
                                    authError = "Niepoprawne hasło administratora! Dostęp zablokowany."
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberEnergy,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_password_auth_input")
                    )

                    AnimatedVisibility(
                        visible = authError != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = Color(0xFFFF5252).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = authError ?: "",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("admin_auth_cancel_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) {
                            Text("Anuluj")
                        }

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                if (passwordInput.trim() == UserAccount.ADMIN_PASSWORD_EXACT) {
                                    isPasswordVerified = true
                                    authError = null
                                } else {
                                    authError = "Niepoprawne hasło administratora! Dostęp zablokowany."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberEnergy,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("admin_auth_confirm_btn")
                        ) {
                            Text("Odblokuj", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Faza 2: Formularz wysyłania powiadomień
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(NeonGreenContainer)
                            .border(2.dp, NeonGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Powiadomienie do wszystkich",
                            tint = NeonGreenBright,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Wyślij powiadomienie Push",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Wiadomość trafi do wszystkich zarejestrowanych użytkowników FitKuchnia",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonGreenBright,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pole 1: Tytuł
                    OutlinedTextField(
                        value = notificationTitle,
                        onValueChange = {
                            notificationTitle = it
                            formError = null
                        },
                        label = { Text("Tytuł powiadomienia") },
                        placeholder = { Text("np. Nowa aktualizacja FitKuchnia!") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Title, contentDescription = null, tint = NeonGreenBright)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreenBright,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("push_title_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pole 2: Treść wiadomości
                    OutlinedTextField(
                        value = notificationBody,
                        onValueChange = {
                            notificationBody = it
                            formError = null
                        },
                        label = { Text("Treść wiadomości") },
                        placeholder = { Text("np. Weszła nowa wersja aplikacji z poprawkami edycji przepisów!") },
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreenBright,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("push_body_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sekcja opcjonalnego załącznika (Moduł 4: Załączniki APK i pliki)
                    Surface(
                        color = DarkBg,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Attachment,
                                        contentDescription = null,
                                        tint = NeonGreenBright,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Załącznik (opcjonalny plik / APK)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (attachmentUrl.isNotBlank()) {
                                    TextButton(
                                        onClick = {
                                            attachmentUrl = ""
                                            attachmentName = ""
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Usuń", color = Color(0xFFFF5252), fontSize = 11.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Szybkie przyciski załączników: APK oraz ZIP
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        attachmentUrl = AppVersionManager.DEFAULT_APK_DOWNLOAD_URL
                                        attachmentName = "FitKuchnia-v${BuildConfig.VERSION_NAME}.apk"
                                        includeInstallGuide = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreenBright),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .testTag("attach_current_apk_chip")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Android,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "📎 Plik APK (v${BuildConfig.VERSION_NAME})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        attachmentUrl = AppVersionManager.DEFAULT_APK_DOWNLOAD_URL
                                        attachmentName = "FitKuchnia.apk"
                                        includeInstallGuide = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreenBright),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .testTag("attach_universal_apk_chip")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "📦 Główny FitKuchnia.apk",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = attachmentUrl,
                                onValueChange = { attachmentUrl = it },
                                label = { Text("Link URL do pliku / Firebase Storage") },
                                placeholder = { Text("https://.../FitKuchnia.apk") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonGreenBright,
                                    unfocusedBorderColor = DarkCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("push_attachment_url_input")
                            )

                            if (attachmentUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = attachmentName,
                                    onValueChange = { attachmentName = it },
                                    label = { Text("Nazwa wyświetlana pliku") },
                                    placeholder = { Text("np. FitKuchnia-v${BuildConfig.VERSION_NAME}.apk") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonGreenBright,
                                        unfocusedBorderColor = DarkCardBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("push_attachment_name_input")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sekcja automatycznej instrukcji instalacji APK krok po kroku z przełącznikiem
                    Surface(
                        color = DarkBg,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (includeInstallGuide) NeonGreen.copy(alpha = 0.6f) else DarkCardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_install_guide_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = if (includeInstallGuide) NeonGreenBright else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Dołącz instrukcję instalacji APK krok po kroku",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Automatycznie uzupełniony poradnik dla użytkownika",
                                            fontSize = 11.sp,
                                            color = if (includeInstallGuide) NeonGreenBright else TextSecondary
                                        )
                                    }
                                }

                                Switch(
                                    checked = includeInstallGuide,
                                    onCheckedChange = { includeInstallGuide = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NeonGreenBright,
                                        checkedTrackColor = NeonGreenContainer,
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = DarkSurface
                                    ),
                                    modifier = Modifier.testTag("toggle_install_guide_switch")
                                )
                            }

                            AnimatedVisibility(
                                visible = includeInstallGuide,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    Text(
                                        text = "Podgląd i edycja instrukcji instalacji na telefonie:",
                                        fontSize = 11.sp,
                                        color = NeonGreenBright,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = installGuideText,
                                        onValueChange = { installGuideText = it },
                                        minLines = 4,
                                        maxLines = 8,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonGreenBright,
                                            unfocusedBorderColor = DarkCardBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("install_guide_text_input")
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = { installGuideText = defaultInstallInstructions },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Przywróć domyślną treść", fontSize = 11.sp, color = TextMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = formError != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = Color(0xFFFF5252).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formError ?: "",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSending,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("push_cancel_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) {
                            Text("Anuluj")
                        }

                        Button(
                            onClick = {
                                val cleanTitle = notificationTitle.trim()
                                val cleanBody = notificationBody.trim()

                                if (cleanTitle.isBlank()) {
                                    formError = "Wpisz tytuł powiadomienia."
                                    return@Button
                                }
                                if (cleanBody.isBlank()) {
                                    formError = "Wpisz treść powiadomienia."
                                    return@Button
                                }

                                val finalBody = if (includeInstallGuide && installGuideText.isNotBlank()) {
                                    if (cleanBody.contains(installGuideText.take(30))) {
                                        cleanBody
                                    } else {
                                        "$cleanBody\n\n$installGuideText"
                                    }
                                } else {
                                    cleanBody
                                }

                                val rawUrl = attachmentUrl.trim().ifBlank { null }
                                val rawName = attachmentName.trim().ifBlank { null }

                                // Zgodnie z wymaganiem: wymuszenie bezpośredniego pliku APK (brak ZIP)
                                val trimmedUrl = rawUrl?.let {
                                    if (it.endsWith(".zip", ignoreCase = true)) {
                                        it.substringBeforeLast(".zip") + ".apk"
                                    } else {
                                        it
                                    }
                                }
                                val trimmedName = rawName?.let {
                                    if (it.endsWith(".zip", ignoreCase = true)) {
                                        it.substringBeforeLast(".zip") + ".apk"
                                    } else if (!it.endsWith(".apk", ignoreCase = true)) {
                                        "$it.apk"
                                    } else {
                                        it
                                    }
                                }
                                val detectedType = if (trimmedUrl != null) "apk" else null

                                isSending = true
                                formError = null

                                PushNotificationManager.sendNotificationToAllUsers(
                                    context = context,
                                    title = cleanTitle,
                                    message = finalBody,
                                    adminEmail = adminEmail,
                                    attachmentUrl = trimmedUrl,
                                    attachmentName = trimmedName,
                                    attachmentType = detectedType,
                                    onSuccess = {
                                        isSending = false
                                        Toast.makeText(
                                            context,
                                            "Powiadomienie zostało wysłane do wszystkich użytkowników!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onNotificationSent("Powiadomienie zostało wysłane do wszystkich użytkowników!")
                                        onDismiss()
                                    },
                                    onError = { error ->
                                        isSending = false
                                        formError = error
                                    }
                                )
                            },
                            enabled = !isSending,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("push_send_btn")
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Wysyłanie...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wyślij", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
