package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppMessage
import com.example.ui.theme.*
import com.example.util.FileDownloadHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    messages: List<AppMessage>,
    onBack: () -> Unit,
    onMarkAsRead: (String) -> Unit,
    onMarkAllAsRead: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val unreadCount = remember(messages) { messages.count { !it.isRead } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Wiadomości i Komunikaty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (unreadCount > 0) "$unreadCount nieprzeczytane" else "${messages.size} komunikatów",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unreadCount > 0) NeonGreenBright else TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("messages_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wróć",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(
                            onClick = onMarkAllAsRead,
                            modifier = Modifier.testTag("mark_all_read_btn")
                        ) {
                            Text(
                                text = "Oznacz przeczytane",
                                color = NeonGreenBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("messages_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Odśwież wiadomości",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    navigationIconContentColor = TextPrimary,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBg,
        modifier = modifier
            .fillMaxSize()
            .testTag("messages_screen")
    ) { innerPadding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(DarkSurface)
                            .border(1.5.dp, DarkCardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailRead,
                            contentDescription = null,
                            tint = NeonGreen.copy(alpha = 0.7f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Brak nowych wiadomości",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Wszystkie oficjalne komunikaty od administratora, aktualizacje oraz załączone pliki APK pojawią się w tym miejscu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreenBright),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sprawdź ponownie")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageCard(
                        message = message,
                        onCardClick = { onMarkAsRead(message.id) },
                        onDownloadAttachment = { url, fileName, isApk ->
                            FileDownloadHelper.downloadFile(
                                context = context,
                                url = url,
                                suggestedFileName = fileName,
                                isApk = isApk
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: AppMessage,
    onCardClick: () -> Unit,
    onDownloadAttachment: (url: String, fileName: String, isApk: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnread = !message.isRead

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isUnread) NeonGreen.copy(alpha = 0.8f) else DarkCardBorder
        ),
        tonalElevation = if (isUnread) 6.dp else 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onCardClick() }
            .testTag("message_card_${message.id}")
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header Row: Badge "ADMINISTRATOR" + date + unread dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = AmberEnergy.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberEnergy.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = AmberEnergy,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ADMINISTRATOR",
                                color = AmberEnergy,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    if (isUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeonGreenBright)
                        )
                    }
                }

                Text(
                    text = formatMessageDate(message.createdAtMillis),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Message Title
            Text(
                text = message.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Message Body
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 20.sp
            )

            // Optional Attachment Box (Module 4)
            if (message.hasAttachment && message.attachmentUrl != null) {
                Spacer(modifier = Modifier.height(14.dp))
                AttachmentBox(
                    message = message,
                    onDownload = {
                        onDownloadAttachment(
                            message.effectiveDownloadUrl,
                            message.safeAttachmentName,
                            message.isApkAttachment
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AttachmentBox(
    message: AppMessage,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isApk = message.isApkAttachment
    var isGuideExpanded by remember { mutableStateOf(false) }

    Surface(
        color = if (isApk) NeonGreenContainer.copy(alpha = 0.35f) else DarkBg,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isApk) NeonGreen.copy(alpha = 0.6f) else DarkCardBorder
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isApk) NeonGreen.copy(alpha = 0.2f) else DarkSurface)
                        .border(1.dp, if (isApk) NeonGreen else DarkCardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isApk) Icons.Default.Android else Icons.Default.Attachment,
                        contentDescription = "Załącznik",
                        tint = if (isApk) NeonGreenBright else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isApk) "Aktualizacja aplikacji (Bezpośredni plik APK)" else "Załączony plik do pobrania",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isApk) NeonGreenBright else TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = message.safeAttachmentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isApk) NeonGreen else MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("download_attachment_btn_${message.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isApk) "Pobierz plik APK" else "Pobierz plik",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            if (isApk || message.hasInstallGuide) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { isGuideExpanded = !isGuideExpanded },
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreenBright),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isGuideExpanded) Icons.Default.ExpandLess else Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGuideExpanded) "Ukryj instrukcję instalacji" else "📖 Jak zainstalować na telefonie? (Krok po kroku)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = isGuideExpanded) {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Instrukcja instalacji APK:",
                                fontWeight = FontWeight.Bold,
                                color = NeonGreenBright,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val steps = listOf(
                                "1. Kliknij zielony przycisk „Pobierz aktualizację APK” powyżej.",
                                "2. Rozwiń górny pasek powiadomień telefonu lub otwórz aplikację „Pobrane” (Downloads).",
                                "3. Kliknij pobrany plik FitKuchnia.apk (lub wypakuj ZIP).",
                                "4. Jeśli telefon wyświetli „Zablokowano instalację z nieznanych źródeł”, kliknij Ustawienia i włącz opcję „Zezwalaj z tego źródła”.",
                                "5. Dotknij „Zainstaluj” (lub „Aktualizuj”).",
                                "6. Otwórz nową wersję FitKuchnia – Twoje konto i przepisy zostaną zachowane!"
                            )
                            steps.forEach { step ->
                                Text(
                                    text = step,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMessageDate(millis: Long): String {
    return try {
        val now = System.currentTimeMillis()
        val diff = now - millis
        val calendarNow = Calendar.getInstance().apply { timeInMillis = now }
        val calendarMsg = Calendar.getInstance().apply { timeInMillis = millis }

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("pl"))

        if (calendarNow.get(Calendar.YEAR) == calendarMsg.get(Calendar.YEAR) &&
            calendarNow.get(Calendar.DAY_OF_YEAR) == calendarMsg.get(Calendar.DAY_OF_YEAR)
        ) {
            "Dzisiaj, ${timeFormat.format(Date(millis))}"
        } else if (calendarNow.get(Calendar.YEAR) == calendarMsg.get(Calendar.YEAR) &&
            calendarNow.get(Calendar.DAY_OF_YEAR) - calendarMsg.get(Calendar.DAY_OF_YEAR) == 1
        ) {
            "Wczoraj, ${timeFormat.format(Date(millis))}"
        } else {
            dateFormat.format(Date(millis))
        }
    } catch (_: Exception) {
        "Niedawno"
    }
}
