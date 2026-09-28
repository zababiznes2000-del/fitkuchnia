package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PortionCalculator
import com.example.data.Recipe
import com.example.ui.theme.*

@Composable
fun HandsFreeCookingScreen(
    recipe: Recipe,
    portions: Int,
    currentStepIndex: Int,
    onNextStep: (Int) -> Unit,
    onPrevStep: () -> Unit,
    onRepeatStep: () -> Unit,
    onStartTimer: (minutes: Int, title: String) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scaled = remember(recipe, portions) {
        PortionCalculator.scaleRecipe(recipe, portions)
    }

    val totalSteps = scaled.steps.size
    val safeIndex = currentStepIndex.coerceIn(0, (totalSteps - 1).coerceAtLeast(0))
    val currentStep = scaled.steps.getOrNull(safeIndex)

    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {}
    }

    Surface(
        color = Color.Black,
        modifier = modifier.fillMaxSize().testTag("hands_free_cooking_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Exit button & Step progress indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit button (large touch target)
                OutlinedButton(
                    onClick = onExit,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("hands_free_exit_btn")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Zakończ", fontWeight = FontWeight.Bold)
                }

                // Step count pill
                Surface(
                    color = NeonGreenContainer,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "KROK ${safeIndex + 1} Z $totalSteps",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = NeonGreenBright,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Central Area: Huge typography and timer trigger if available
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Giant Step Instruction Text
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentStep?.text ?: "Gotowanie ukończone!",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                lineHeight = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("hands_free_step_text")
                        )

                        if (currentStep?.timerMinutes != null) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    onStartTimer(currentStep.timerMinutes, "Krok ${safeIndex + 1}: ${recipe.title}")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonGreenContainer,
                                    contentColor = NeonGreenBright
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonGreen),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                                modifier = Modifier.testTag("hands_free_step_timer_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "URUCHOM ${currentStep.timerMinutes} MINUT",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Huge Controls (Easy to tap with elbow or dirty wrist)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                // Secondary row: Repeat (TTS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = onRepeatStep,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreenBright),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = Modifier.testTag("hands_free_repeat_btn")
                    ) {
                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("POWTÓRZ NA GŁOS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Giant Primary Row: WSTECZ & DALEJ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Back button
                    Button(
                        onClick = onPrevStep,
                        enabled = safeIndex > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurfaceVariant,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .testTag("hands_free_prev_btn")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WSTECZ", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }

                    // Next / Complete button
                    Button(
                        onClick = {
                            if (safeIndex < totalSteps - 1) {
                                onNextStep(totalSteps)
                            } else {
                                onExit()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(72.dp)
                            .testTag("hands_free_next_btn")
                    ) {
                        Text(
                            text = if (safeIndex < totalSteps - 1) "DALEJ >" else "GOTOWE! ✓",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }
}
