package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun PortionSelectorCard(
    currentPortions: Int,
    onPortionsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = modifier.testTag("portion_selector_card")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = "Liczba porcji",
                    tint = NeonGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Liczba porcji",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Kalkulator Moduł 3",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Minus button
                IconButton(
                    onClick = { if (currentPortions > 1) onPortionsChange(currentPortions - 1) },
                    enabled = currentPortions > 1,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .testTag("portion_minus_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zmniejsz porcje",
                        tint = if (currentPortions > 1) NeonGreen else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Interactive Portion Box (Kwadracik z cyfrą)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonGreenContainer)
                        .border(1.5.dp, NeonGreen, RoundedCornerShape(10.dp))
                        .clickable { showDialog = true }
                        .testTag("portion_number_box"),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = currentPortions,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "PortionNumber"
                    ) { target ->
                        Text(
                            text = target.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = NeonGreenBright,
                            fontSize = 20.sp
                        )
                    }
                }

                // Plus button
                IconButton(
                    onClick = { if (currentPortions < 20) onPortionsChange(currentPortions + 1) },
                    enabled = currentPortions < 20,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .testTag("portion_plus_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zwiększ porcje",
                        tint = if (currentPortions < 20) NeonGreen else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Direct input dialog
    if (showDialog) {
        var inputVal by remember { mutableStateOf(currentPortions.toString()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Zmień liczbę porcji", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        text = "Moduł 3 automatycznie przeliczy tabelę składników oraz gramatury w krokach przepisu:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputVal,
                        onValueChange = { inputVal = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Liczba osób / porcji (1-20)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            focusedLabelColor = NeonGreen,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("portion_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = inputVal.toIntOrNull()?.coerceIn(1, 20) ?: currentPortions
                        onPortionsChange(parsed)
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    modifier = Modifier.testTag("portion_confirm_btn")
                ) {
                    Text("Przelicz porcje", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Anuluj", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
