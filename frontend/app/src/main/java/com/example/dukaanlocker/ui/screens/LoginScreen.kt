package com.example.dukaanlocker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import com.example.dukaanlocker.R
import com.example.dukaanlocker.ui.theme.*

// ── Password Strength ──────────────────────────────────────────────────────────
private enum class PasswordStrength(val label: String, val color: Color, val level: Int) {
    NONE("Enter a password", Color.Gray, 0),
    WEAK("Weak", Color(0xFFEF4444), 1),
    FAIR("Fair", Color(0xFFF59E0B), 2),
    GOOD("Good", Color(0xFF3B82F6), 3),
    STRONG("Strong", Color(0xFF22C55E), 4)
}

private fun evaluatePasswordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) return PasswordStrength.NONE
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when {
        score <= 1 -> PasswordStrength.WEAK
        score <= 2 -> PasswordStrength.FAIR
        score <= 3 -> PasswordStrength.GOOD
        else -> PasswordStrength.STRONG
    }
}

private fun isValidEmail(email: String): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

// ── Main Login Screen ──────────────────────────────────────────────────────────
@Composable
fun LoginScreen(
    onOwnerLogin: (email: String, password: String) -> Unit,
    onManagerLogin: (code: String) -> Unit,
    onRegister: (name: String, email: String, password: String, mobile: String) -> Unit = { _, _, _, _ -> },
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    // null = role selection, "register" = register form, true = owner login, false = manager login
    var selectedView by remember { mutableStateOf<Any?>(null) }

    // ── Register fields ──
    var regName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regMobile by remember { mutableStateOf("") }
    var regPasswordVisible by remember { mutableStateOf(false) }
    var regIsChecking by remember { mutableStateOf(false) }
    // Validation error states
    var regNameError by remember { mutableStateOf(false) }
    var regEmailError by remember { mutableStateOf(false) }
    var regMobileError by remember { mutableStateOf(false) }
    var regPasswordError by remember { mutableStateOf(false) }

    // ── Login fields ──
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var loginIsChecking by remember { mutableStateOf(false) }
    var loginEmailError by remember { mutableStateOf(false) }
    var loginPasswordError by remember { mutableStateOf(false) }

    // ── Manager fields ──
    var accessCode by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }

    val regStrength = remember(regPassword) { evaluatePasswordStrength(regPassword) }

    // ── Validate & submit register ──
    fun validateAndRegister() {
        regNameError = regName.isBlank()
        regEmailError = !isValidEmail(regEmail)
        regMobileError = regMobile.length != 10
        regPasswordError = regPassword.length < 6
        if (!regNameError && !regEmailError && !regMobileError && !regPasswordError) {
            regIsChecking = true
            onRegister(regName.trim(), regEmail.trim(), regPassword, regMobile)
        }
    }

    // ── Validate & submit login ──
    fun validateAndLogin() {
        loginEmailError = !isValidEmail(loginEmail)
        loginPasswordError = loginPassword.isBlank()
        if (!loginEmailError && !loginPasswordError) {
            loginIsChecking = true
            onOwnerLogin(loginEmail.trim(), loginPassword)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top section: Logo + Title ────────────────────────────────────────
        Spacer(modifier = Modifier.height(24.dp))

        // App Logo (custom launcher icon)
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(colors = listOf(colors.primary.copy(alpha = 0.15f), colors.background))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "DukaanLocker Logo",
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "DUKAAN LOCKER",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 3.sp
        )

        Text(
            text = "Secure Business Document Vault",
            fontSize = 12.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )

        // Theme toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onToggleTheme, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Icon(
                    if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle theme",
                    modifier = Modifier.size(14.dp),
                    tint = colors.textSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (isDarkTheme) "Light" else "Dark",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }
        }

        // ── Content area ─────────────────────────────────────────────────────
        // Uses weight(1f) to fill remaining space — no scrolling needed
        val contentAlign = if (selectedView == null) Alignment.TopCenter else Alignment.Center
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = contentAlign
        ) {
            when (selectedView) {
                // ── ROLE SELECTION ──────────────────────────────────────────
                null -> RoleSelectionContent(
                    colors = colors,
                    onSelectRegister = { selectedView = "register" },
                    onSelectOwnerLogin = { selectedView = true },
                    onSelectManagerLogin = { selectedView = false }
                )

                // ── OWNER LOGIN (Email + Password only) ────────────────────
                true -> OwnerLoginForm(
                    colors = colors,
                    email = loginEmail,
                    onEmailChange = { loginEmail = it; loginEmailError = false },
                    emailError = loginEmailError,
                    password = loginPassword,
                    onPasswordChange = { loginPassword = it; loginPasswordError = false },
                    passwordVisible = loginPasswordVisible,
                    onTogglePasswordVisible = { loginPasswordVisible = !loginPasswordVisible },
                    passwordError = loginPasswordError,
                    isChecking = loginIsChecking,
                    onBack = { selectedView = null; loginIsChecking = false },
                    onLogin = { validateAndLogin() }
                )

                // ── REGISTER FORM ──────────────────────────────────────────
                "register" -> RegisterForm(
                    colors = colors,
                    name = regName, onNameChange = { regName = it; regNameError = false },
                    nameError = regNameError,
                    email = regEmail, onEmailChange = { regEmail = it; regEmailError = false },
                    emailError = regEmailError,
                    password = regPassword, onPasswordChange = { regPassword = it; regPasswordError = false },
                    passwordVisible = regPasswordVisible, onTogglePasswordVisible = { regPasswordVisible = !regPasswordVisible },
                    passwordError = regPasswordError,
                    passwordStrength = regStrength,
                    mobile = regMobile, onMobileChange = { regMobile = it.filter { c -> c.isDigit() }.take(10); regMobileError = false },
                    mobileError = regMobileError,
                    isChecking = regIsChecking,
                    onBack = { selectedView = null; regIsChecking = false },
                    onRegister = { validateAndRegister() }
                )

                // ── MANAGER LOGIN ──────────────────────────────────────────
                false -> ManagerLoginForm(
                    colors = colors,
                    accessCode = accessCode,
                    onAccessCodeChange = { accessCode = it.uppercase().take(6); codeError = false },
                    codeError = codeError,
                    onBack = { selectedView = null },
                    onLogin = { onManagerLogin(accessCode) }
                )
            }
        }

        // ── Footer (with navigation bar padding to avoid overlap) ────────────
        Text(
            text = "Secure Business Locker for Your Business",
            fontSize = 10.sp,
            color = colors.textSecondary.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ROLE SELECTION
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun RoleSelectionContent(
    colors: AppColors,
    onSelectRegister: () -> Unit,
    onSelectOwnerLogin: () -> Unit,
    onSelectManagerLogin: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "GET STARTED",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textSecondary,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Register Now Card — most prominent
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectRegister() },
            colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.12f)),
            border = BorderStroke(2.dp, colors.primary),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Register Now", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    Text(
                        "First time? Create your secure vault",
                        fontSize = 11.sp,
                        color = colors.accent.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Separator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Divider(modifier = Modifier.weight(1f), color = colors.border.copy(alpha = 0.5f))
            Text(
                "  Returning User?  ",
                fontSize = 10.sp,
                color = colors.textSecondary.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
            Divider(modifier = Modifier.weight(1f), color = colors.border.copy(alpha = 0.5f))
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Owner Login Card
        RoleCard(
            icon = Icons.Default.Person,
            title = "Business Owner",
            subtitle = "Sign in with email & password",
            onClick = onSelectOwnerLogin
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Manager Login Card
        RoleCard(
            icon = Icons.Default.Lock,
            title = "Manager",
            subtitle = "Access assigned businesses with code",
            onClick = onSelectManagerLogin
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// OWNER LOGIN FORM (Email + Password only)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun OwnerLoginForm(
    colors: AppColors,
    email: String, onEmailChange: (String) -> Unit, emailError: Boolean,
    password: String, onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean, onTogglePasswordVisible: () -> Unit,
    passwordError: Boolean,
    isChecking: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Back + title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Owner Sign In", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("Access your business dashboard", fontSize = 12.sp, color = colors.textSecondary)
                }
            }

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email Address", color = colors.textSecondary) },
                placeholder = { Text("you@example.com", color = colors.textSecondary.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                isError = emailError,
                supportingText = if (emailError) {{ Text("Enter a valid email address", color = Color.Red) }} else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary, cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Password with eye toggle
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password", color = colors.textSecondary) },
                placeholder = { Text("Enter your password", color = colors.textSecondary.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisible, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = passwordError,
                supportingText = if (passwordError) {{ Text("Password is required", color = Color.Red) }} else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary, cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Login Button
            Button(
                onClick = onLogin,
                enabled = email.isNotBlank() && password.isNotBlank() && !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.background, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SECURE ACCESS", fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// REGISTER FORM
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun RegisterForm(
    colors: AppColors,
    name: String, onNameChange: (String) -> Unit, nameError: Boolean,
    email: String, onEmailChange: (String) -> Unit, emailError: Boolean,
    password: String, onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean, onTogglePasswordVisible: () -> Unit,
    passwordError: Boolean, passwordStrength: PasswordStrength,
    mobile: String, onMobileChange: (String) -> Unit, mobileError: Boolean,
    isChecking: Boolean,
    onBack: () -> Unit,
    onRegister: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Back + title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Create Account", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("Set up your business document vault", fontSize = 12.sp, color = colors.textSecondary)
                }
            }

            // Full Name
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Full Name", color = colors.textSecondary) },
                placeholder = { Text("Ramesh Sharma", color = colors.textSecondary.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                isError = nameError,
                supportingText = if (nameError) {{ Text("Name is required", color = Color.Red) }} else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary, cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Mobile Number (right below Full Name)
            OutlinedTextField(
                value = mobile,
                onValueChange = onMobileChange,
                label = { Text("Mobile Number", color = colors.textSecondary) },
                placeholder = { Text("9876543210", color = colors.textSecondary.copy(alpha = 0.4f)) },
                leadingIcon = {
                    Text(
                        text = "+91",
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                },
                isError = mobileError,
                supportingText = if (mobileError) {{ Text("Enter a valid 10-digit mobile number", color = Color.Red) }} else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary, cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email Address", color = colors.textSecondary) },
                placeholder = { Text("you@example.com", color = colors.textSecondary.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                isError = emailError,
                supportingText = if (emailError) {{ Text("Enter a valid email address", color = Color.Red) }} else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary, cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Password with eye toggle + strength indicator
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password", color = colors.textSecondary) },
                placeholder = { Text("Min 6 characters", color = colors.textSecondary.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisible, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = passwordError,
                supportingText = {
                    if (passwordError) {
                        Text("Password must be at least 6 characters", color = Color.Red)
                    } else if (password.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Strength bar
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            ) {
                                repeat(4) { index ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(
                                                if (index < passwordStrength.level) passwordStrength.color
                                                else colors.border.copy(alpha = 0.4f)
                                            )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                passwordStrength.label,
                                fontSize = 11.sp,
                                color = passwordStrength.color,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary, cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Register Button
            Button(
                onClick = onRegister,
                enabled = name.isNotBlank() && email.isNotBlank() && password.length >= 6 && mobile.length == 10 && !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.background, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CREATE VAULT", fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// MANAGER LOGIN FORM
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ManagerLoginForm(
    colors: AppColors,
    accessCode: String,
    onAccessCodeChange: (String) -> Unit,
    codeError: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back + title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Manager Access", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("Enter access code provided by owner", fontSize = 12.sp, color = colors.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Access Code
            OutlinedTextField(
                value = accessCode,
                onValueChange = onAccessCodeChange,
                label = { Text("Access Code", color = colors.textSecondary) },
                placeholder = { Text("e.g. X7K9M2", color = colors.textSecondary.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(20.dp)) },
                isError = codeError,
                supportingText = if (codeError) {{ Text("Invalid access code", color = Color.Red) }} else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.secondary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.secondary, cursorColor = colors.secondary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Code format hint boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(6) { i ->
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (i < accessCode.length) colors.secondary.copy(alpha = 0.2f)
                                else colors.border.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (i < accessCode.length) accessCode[i].toString() else "●",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (i < accessCode.length) colors.secondary else colors.textSecondary.copy(alpha = 0.3f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onLogin,
                enabled = accessCode.length == 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.secondary, contentColor = colors.textOnPrimary)
            ) {
                Icon(Icons.Default.Login, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("VERIFY & ACCESS", fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// REUSABLE ROLE CARD
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun RoleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(subtitle, fontSize = 11.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
