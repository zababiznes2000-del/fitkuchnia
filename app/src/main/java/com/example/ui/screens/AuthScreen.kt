package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.data.UserAccount
import com.example.data.local.AppRecipeStorage
import com.example.ui.theme.*

enum class AuthMode {
    LOGIN,
    REGISTER
}

@Composable
fun AuthScreen(
    onLogin: (email: String, password: String) -> String?,
    onRegister: (email: String, name: String, password: String, passwordConfirm: String) -> String?,
    sessionExpiredMessage: String? = null,
    onDismissExpiredMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }

    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordConfirmInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    val isSuperAdminEmail = remember(emailInput) {
        UserAccount.isAdministrator(emailInput)
    }

    val glowColor by animateColorAsState(
        targetValue = if (isSuperAdminEmail) AmberEnergy.copy(alpha = 0.22f) else NeonGreen.copy(alpha = 0.15f),
        label = "glowColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .testTag("auth_screen")
    ) {
        // Decorative background glow
        Box(
            modifier = Modifier
                .size(380.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-80).dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            glowColor,
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Icon / Shield
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (isSuperAdminEmail) AmberEnergy.copy(alpha = 0.2f) else NeonGreenContainer)
                    .border(2.dp, if (isSuperAdminEmail) AmberEnergy else NeonGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSuperAdminEmail) Icons.Default.Shield else Icons.Default.RestaurantMenu,
                    contentDescription = "Logo",
                    tint = if (isSuperAdminEmail) AmberEnergy else NeonGreenBright,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "FitKuchnia",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 1.sp
            )

            Text(
                text = if (isSuperAdminEmail) "Strefa Administratora" else "Społeczność Zdrowego Gotowania",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSuperAdminEmail) AmberEnergy else NeonGreenBright,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Mode Selector Tabs (Logowanie / Rejestracja)
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    // Tab 1: Logowanie
                    Surface(
                        color = if (mode == AuthMode.LOGIN) {
                            if (isSuperAdminEmail) AmberEnergy.copy(alpha = 0.25f) else NeonGreen
                        } else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                mode = AuthMode.LOGIN
                                errorMessage = null
                            }
                            .testTag("tab_login")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                tint = if (mode == AuthMode.LOGIN) {
                                    if (isSuperAdminEmail) AmberEnergy else Color.Black
                                } else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Logowanie",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (mode == AuthMode.LOGIN) {
                                    if (isSuperAdminEmail) AmberEnergy else Color.Black
                                } else TextSecondary
                            )
                        }
                    }

                    // Tab 2: Rejestracja
                    Surface(
                        color = if (mode == AuthMode.REGISTER) NeonGreen else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                mode = AuthMode.REGISTER
                                errorMessage = null
                            }
                            .testTag("tab_register")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = if (mode == AuthMode.REGISTER) Color.Black else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Utwórz konto",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (mode == AuthMode.REGISTER) Color.Black else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Baner informacyjny o wygaśnięciu sesji po braku aktywności (minimum 8 minut)
            if (!sessionExpiredMessage.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF2B1D0E),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberEnergy.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("session_expired_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AmberEnergy.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = AmberEnergy,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SESJA APLIKACJI WYGASŁA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = AmberEnergy,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = sessionExpiredMessage,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }
                        IconButton(
                            onClick = onDismissExpiredMessage,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Zamknij komunikat",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Main Auth Card
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSuperAdminEmail) AmberEnergy.copy(alpha = 0.5f) else DarkCardBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (mode == AuthMode.LOGIN) {
                        // Header title for LOGIN
                        if (isSuperAdminEmail) {
                            Surface(
                                color = AmberEnergy.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "👑 KONTO ADMINISTRATORA",
                                        color = AmberEnergy,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Dostęp Administratora",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Podaj hasło główne administratora, aby uzyskać dostęp.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "Zaloguj się do swojego konta",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Wprowadź swój e-mail oraz hasło utworzone przy rejestracji.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Email Field
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                errorMessage = null
                            },
                            label = { Text("Twój adres e-mail") },
                            placeholder = { Text("np. jan.kowalski@gmail.com") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = if (isSuperAdminEmail) AmberEnergy else NeonGreenBright
                                )
                            },
                            trailingIcon = {
                                if (isSuperAdminEmail) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "Admin",
                                        tint = AmberEnergy,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isSuperAdminEmail) AmberEnergy else NeonGreenBright,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Password Field
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                errorMessage = null
                            },
                            label = {
                                Text(if (isSuperAdminEmail) "Hasło Administratora" else "Twoje hasło")
                            },
                            placeholder = { Text("••••••••") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isSuperAdminEmail) Icons.Default.Key else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isSuperAdminEmail) AmberEnergy else NeonGreenBright
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isPasswordVisible) "Ukryj hasło" else "Pokaż hasło",
                                        tint = TextSecondary
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { keyboardController?.hide() }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isSuperAdminEmail) AmberEnergy else NeonGreenBright,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input")
                        )

                    } else {
                        // REGISTER MODE
                        Text(
                            text = "Nowe konto FitKuchnia",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Zarejestruj się, aby bezpiecznie zarządzać przepisami i listą zakupów.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // 1. Email
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                errorMessage = null
                            },
                            label = { Text("Twój adres e-mail") },
                            placeholder = { Text("np. anna.kowalska@gmail.com") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = NeonGreenBright)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGreenBright,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_email_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Nick / Name
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = {
                                nameInput = it
                                errorMessage = null
                            },
                            label = { Text("Twój Nick / Imię (min. 3 znaki)") },
                            placeholder = { Text("np. Anna Fit") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = NeonGreenBright)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGreenBright,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_name_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Password
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                errorMessage = null
                            },
                            label = { Text("Wymyśl hasło (min. 6 znaków)") },
                            placeholder = { Text("••••••••") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = NeonGreenBright)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGreenBright,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_password_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 4. Repeat Password
                        OutlinedTextField(
                            value = passwordConfirmInput,
                            onValueChange = {
                                passwordConfirmInput = it
                                errorMessage = null
                            },
                            label = { Text("Powtórz hasło") },
                            placeholder = { Text("••••••••") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.LockReset, contentDescription = null, tint = NeonGreenBright)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isConfirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            },
                            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { keyboardController?.hide() }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGreenBright,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_confirm_password_input")
                        )
                    }

                    // Error display box
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = Color(0xFFFF5252).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Main Submit Button
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            errorMessage = null
                            if (mode == AuthMode.LOGIN) {
                                val email = emailInput.trim()
                                val pass = passwordInput.trim()
                                if (email.isBlank() || !email.contains("@")) {
                                    errorMessage = "Wprowadź prawidłowy adres e-mail."
                                    return@Button
                                }
                                if (pass.isBlank()) {
                                    errorMessage = "Wprowadź hasło."
                                    return@Button
                                }
                                isSubmitting = true
                                val err = onLogin(email, pass)
                                isSubmitting = false
                                if (err != null) {
                                    errorMessage = err
                                }
                            } else {
                                val email = emailInput.trim()
                                val name = nameInput.trim()
                                val pass = passwordInput.trim()
                                val confirm = passwordConfirmInput.trim()

                                if (email.isBlank() || !email.contains("@")) {
                                    errorMessage = "Wprowadź prawidłowy adres e-mail."
                                    return@Button
                                }
                                if (name.length < 3) {
                                    errorMessage = "Twój Nick musi mieć co najmniej 3 znaki."
                                    return@Button
                                }
                                if (pass.length < 6) {
                                    errorMessage = "Hasło musi mieć co najmniej 6 znaków."
                                    return@Button
                                }
                                if (pass != confirm) {
                                    errorMessage = "Podane hasła nie są identyczne!"
                                    return@Button
                                }

                                isSubmitting = true
                                val err = onRegister(email, name, pass, confirm)
                                isSubmitting = false
                                if (err != null) {
                                    errorMessage = err
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSuperAdminEmail && mode == AuthMode.LOGIN) AmberEnergy else NeonGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("auth_submit_button")
                    ) {
                        Icon(
                            imageVector = if (mode == AuthMode.LOGIN) {
                                if (isSuperAdminEmail) Icons.Default.Security else Icons.Default.Login
                            } else Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (mode == AuthMode.LOGIN) {
                                if (isSuperAdminEmail) "Zaloguj jako Administrator" else "Zaloguj się"
                            } else "Zarejestruj konto i wejdź",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Secondary switcher text button
                    TextButton(
                        onClick = {
                            mode = if (mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN
                            errorMessage = null
                        }
                    ) {
                        Text(
                            text = if (mode == AuthMode.LOGIN) {
                                "Nie masz jeszcze konta? Kliknij tutaj, aby utworzyć konto"
                            } else {
                                "Masz już konto? Kliknij tutaj, aby się zalogować"
                            },
                            color = NeonGreenBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
