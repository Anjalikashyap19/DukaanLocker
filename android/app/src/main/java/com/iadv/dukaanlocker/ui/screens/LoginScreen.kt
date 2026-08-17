package com.iadv.dukaanlocker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.ui.components.LauncherLogo
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*
import coil.compose.AsyncImage
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.iadv.dukaanlocker.R

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

// ── MSME / Udyam validation ─────────────────────────────────────────────────────
private val msmeRegex = Regex("^UDYAM-[A-Z]{2}-\\d{2}-\\d{7}$", RegexOption.IGNORE_CASE)

private fun isValidMsme(number: String): Boolean = msmeRegex.matches(number.trim())

// ── Client-side CAPTCHA ─────────────────────────────────────────────────────────
private val captchaChars = ('A'..'Z') + ('0'..'9')

private fun generateCaptcha(length: Int = 5): String {
    val random = java.util.Random()
    return (1..length).map { captchaChars[random.nextInt(captchaChars.size)] }.joinToString("")
}

// ── Main Login Screen ──────────────────────────────────────────────────────────
@Composable
fun LoginScreen(
    onOwnerLogin: (email: String, password: String, onDone: () -> Unit) -> Unit,
    onManagerLogin: (code: String) -> Unit,
    onRegister: (name: String, email: String, password: String, mobile: String, onDone: () -> Unit) -> Unit,
    onRegisterWithMsme: (msmeNumber: String, mobile: String, password: String, sessionId: String, captchaText: String, onDone: () -> Unit) -> Unit = { _, _, _, _, _, onDone -> onDone() },
    onInitMsmeCaptcha: (onResult: (sessionId: String, captchaImage: String) -> Unit) -> Unit = { onResult -> onResult("", "") },
    onGoogleSignIn: () -> Unit = {},
    onBackToMain: () -> Unit,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    // Biometric Login: Show prompt if enabled and credentials exist
    isBiometricLoginEnabled: Boolean = false,
    onBiometricLogin: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    // null = role selection, "register" = register form, true = owner login, false = manager login
    var selectedView by remember { mutableStateOf<Any?>(null) }
    
    // Auto-trigger biometric login prompt when arriving at login screen with biometric enabled
    var biometricTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (isBiometricLoginEnabled && !biometricTriggered) {
            biometricTriggered = true
            kotlinx.coroutines.delay(800) // Small delay to let the screen settle
            onBiometricLogin()
        }
    }

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

    // ── MSME register fields ──
    var registerWithMsme by remember { mutableStateOf(false) }
    var regMsmeNumber by remember { mutableStateOf("") }
    var regMsmeError by remember { mutableStateOf(false) }
    var regCaptcha by remember { mutableStateOf(generateCaptcha()) }
    var regCaptchaInput by remember { mutableStateOf("") }
    var regCaptchaError by remember { mutableStateOf(false) }
    // Government captcha state
    var msmeSessionId by remember { mutableStateOf("") }
    var msmeCaptchaImage by remember { mutableStateOf("") }
    var msmeCaptchaLoading by remember { mutableStateOf(false) }

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

    // Backend requires: 8+ chars, uppercase, lowercase, digit, special char
    fun isStrongPassword(pw: String): Boolean {
        if (pw.length < 8) return false
        if (!pw.any { it.isUpperCase() }) return false
        if (!pw.any { it.isLowerCase() }) return false
        if (!pw.any { it.isDigit() }) return false
        if (!pw.any { !it.isLetterOrDigit() }) return false
        return true
    }

    // ── Validate & submit register ──
    fun validateAndRegister() {
        regNameError = regName.isBlank()
        regEmailError = !isValidEmail(regEmail)
        regMobileError = regMobile.length != 10
        regPasswordError = !isStrongPassword(regPassword)
        if (!regNameError && !regEmailError && !regMobileError && !regPasswordError) {
            regIsChecking = true
            onRegister(regName.trim(), regEmail.trim(), regPassword, regMobile) {
                regIsChecking = false
            }
        }
    }

    // ── Validate & submit MSME register ──
    fun validateAndRegisterWithMsme() {
        regMsmeError = !isValidMsme(regMsmeNumber)
        regMobileError = regMobile.length != 10
        regPasswordError = !isStrongPassword(regPassword)
        // For government captcha: validate non-empty (server validates correctness)
        regCaptchaError = regCaptchaInput.isBlank()
        if (!regMsmeError && !regMobileError && !regPasswordError && !regCaptchaError) {
            regIsChecking = true
            onRegisterWithMsme(
                regMsmeNumber.trim().uppercase(),
                regMobile,
                regPassword,
                msmeSessionId,
                regCaptchaInput.trim()
            ) {
                regIsChecking = false
            }
        }
    }

    // ── Validate & submit login ──
    fun validateAndLogin() {
        loginEmailError = !isValidEmail(loginEmail)
        loginPasswordError = loginPassword.isBlank()
        if (!loginEmailError && !loginPasswordError) {
            loginIsChecking = true
            onOwnerLogin(loginEmail.trim(), loginPassword) {
                loginIsChecking = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top section: Back + Logo + Title ─────────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBackToMain,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back to home",
                    modifier = Modifier.size(20.dp),
                    tint = colors.textSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    AppStrings.get(lang, "Home"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // App Logo (launcher icon)
        LauncherLogo(modifier = Modifier.size(104.dp))

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "DUKAAN LOCKER",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            letterSpacing = 3.sp
        )

        Text(
            text = AppStrings.get(lang, "Secure Business Document Vault"),
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
                    AppStrings.get(lang, if (isDarkTheme) "Light" else "Dark"),
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }
        }

        // ── Content area ─────────────────────────────────────────────────────
        // Uses weight(1f) to fill remaining space — no scrolling needed
        val contentAlign = if (selectedView == null || selectedView == "register") Alignment.TopCenter else Alignment.Center
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
                    lang = lang,
                    isBiometricLoginEnabled = isBiometricLoginEnabled,
                    onSelectRegister = { selectedView = "register" },
                    onSelectOwnerLogin = { selectedView = true },
                    onSelectManagerLogin = { selectedView = false },
                    onGoogleSignIn = onGoogleSignIn,
                    onBiometricLogin = onBiometricLogin
                )

                // ── OWNER LOGIN (Email + Password only) ────────────────────
                true -> OwnerLoginForm(
                    colors = colors,
                    lang = lang,
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
                    lang = lang,
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
                    registerWithMsme = registerWithMsme,
                    onToggleRegisterWithMsme = {
                        registerWithMsme = !registerWithMsme
                        if (registerWithMsme && msmeSessionId.isBlank()) {
                            msmeCaptchaLoading = true
                            onInitMsmeCaptcha { sessionId, captchaImage ->
                                msmeSessionId = sessionId
                                msmeCaptchaImage = captchaImage
                                msmeCaptchaLoading = false
                            }
                        }
                    },
                    msmeNumber = regMsmeNumber,
                    onMsmeNumberChange = { regMsmeNumber = it.filter { ch -> ch.isLetterOrDigit() || ch == '-' }.uppercase(); regMsmeError = false },
                    msmeNumberError = regMsmeError,
                    captchaCode = if (msmeCaptchaImage.isNotBlank()) msmeCaptchaImage else regCaptcha,
                    isGovCaptcha = msmeCaptchaImage.isNotBlank(),
                    onRefreshCaptcha = {
                        if (registerWithMsme) {
                            msmeCaptchaLoading = true
                            onInitMsmeCaptcha { sessionId, captchaImage ->
                                msmeSessionId = sessionId
                                msmeCaptchaImage = captchaImage
                                regCaptchaInput = ""
                                regCaptchaError = false
                                msmeCaptchaLoading = false
                            }
                        } else {
                            regCaptcha = generateCaptcha()
                            regCaptchaInput = ""
                            regCaptchaError = false
                        }
                    },
                    captchaInput = regCaptchaInput,
                    msmeCaptchaLoading = msmeCaptchaLoading,
                    onCaptchaInputChange = { regCaptchaInput = it; regCaptchaError = false },
                    captchaError = regCaptchaError,
                    isChecking = regIsChecking,
                    onBack = { selectedView = null; regIsChecking = false },
                    onRegister = { if (registerWithMsme) validateAndRegisterWithMsme() else validateAndRegister() },
                    onGoogleSignIn = onGoogleSignIn
                )

                // ── MANAGER LOGIN (uses access code) ──────────
                false -> ManagerLoginForm(
                    colors = colors,
                    accessCode = accessCode,
                    onAccessCodeChange = { accessCode = it.uppercase().take(6); codeError = false },
                    codeError = codeError,
                    onBack = { selectedView = null; codeError = false },
                    onLogin = {
                        if (accessCode.length == 6) {
                            loginIsChecking = true
                            onManagerLogin(accessCode)
                        } else {
                            codeError = true
                        }
                    }
                )
            }
        }

        // ── Footer (with navigation bar padding to avoid overlap) ────────────
        Text(
            text = AppStrings.get(lang, "Secure Business Locker for Your Business"),
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
    lang: String,
    isBiometricLoginEnabled: Boolean = false,
    onSelectRegister: () -> Unit,
    onSelectOwnerLogin: () -> Unit,
    onSelectManagerLogin: () -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onBiometricLogin: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))



        Text(
            text = AppStrings.get(lang, "GET STARTED"),
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
                    Text(AppStrings.get(lang, "Register Now"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    Text(
                        AppStrings.get(lang, "First time? Create your secure vault"),
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
            HorizontalDivider(modifier = Modifier.weight(1f), color = colors.border.copy(alpha = 0.5f))
            Text(
                "  ${AppStrings.get(lang, "Returning User?")}  ",
                fontSize = 10.sp,
                color = colors.textSecondary.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = colors.border.copy(alpha = 0.5f))
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Owner Login Card
        RoleCard(
            icon = Icons.Default.Person,
            title = AppStrings.get(lang, "Business Owner"),
            subtitle = AppStrings.get(lang, "Sign in with email & password"),
            onClick = onSelectOwnerLogin
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Manager Login Card
        RoleCard(
            icon = Icons.Default.Lock,
            title = AppStrings.get(lang, "Manager"),
            subtitle = AppStrings.get(lang, "Access assigned businesses with code"),
            onClick = onSelectManagerLogin
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Google Sign-Up Button
        Button(
            onClick = onGoogleSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.DarkGray
            ),
            border = BorderStroke(1.dp, Color.LightGray)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = "Google Sign-In",
                tint = Color.Unspecified // Crucial! Keeps the original Google brand colors (Red, Yellow, Green, Blue)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(" Google sign in", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// OWNER LOGIN FORM (Email + Password only)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun OwnerLoginForm(
    colors: AppColors,
    lang: String,
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
                    Text(AppStrings.get(lang, "Owner Sign In"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(AppStrings.get(lang, "Access your business dashboard"), fontSize = 12.sp, color = colors.textSecondary)
                }
            }

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text(AppStrings.get(lang, "Email Address"), color = colors.textSecondary) },
                placeholder = { Text("you@example.com", color = colors.textSecondary.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                isError = emailError,
                supportingText = if (emailError) {{ Text(AppStrings.get(lang, "Enter a valid email address"), color = Color.Red) }} else null,
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
                label = { Text(AppStrings.get(lang, "Password"), color = colors.textSecondary) },
                placeholder = { Text(AppStrings.get(lang, "Enter your password"), color = colors.textSecondary.copy(alpha = 0.4f)) },
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
                supportingText = if (passwordError) {{ Text(AppStrings.get(lang, "Password is required"), color = Color.Red) }} else null,
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
                    Text(AppStrings.get(lang, "SECURE ACCESS"), fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
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
    lang: String,
    name: String, onNameChange: (String) -> Unit, nameError: Boolean,
    email: String, onEmailChange: (String) -> Unit, emailError: Boolean,
    password: String, onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean, onTogglePasswordVisible: () -> Unit,
    passwordError: Boolean, passwordStrength: PasswordStrength,
    mobile: String, onMobileChange: (String) -> Unit, mobileError: Boolean,
    registerWithMsme: Boolean, onToggleRegisterWithMsme: () -> Unit,
    msmeNumber: String, onMsmeNumberChange: (String) -> Unit, msmeNumberError: Boolean,
    captchaCode: String, isGovCaptcha: Boolean = false, onRefreshCaptcha: () -> Unit,
    captchaInput: String, msmeCaptchaLoading: Boolean = false, onCaptchaInputChange: (String) -> Unit, captchaError: Boolean,
    isChecking: Boolean,
    onBack: () -> Unit,
    onRegister: () -> Unit,
    onGoogleSignIn: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                    Text(
                        AppStrings.get(lang, if (registerWithMsme) "MSME Registration" else "Create Account"),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary
                    )
                    Text(
                        AppStrings.get(lang, if (registerWithMsme) "Verify your Udyam registration" else "Set up your business document vault"),
                        fontSize = 12.sp, color = colors.textSecondary
                    )
                }
            }

            // ── MSME toggle ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Business,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        AppStrings.get(lang, "Have MSME Number?"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        AppStrings.get(lang, "Register with your Udyam registration"),
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
                Switch(
                    checked = registerWithMsme,
                    onCheckedChange = { onToggleRegisterWithMsme() },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.primary)
                )
            }

            // ── MSME Number (MSME mode) / Full Name (normal mode) ──
            if (registerWithMsme) {
                OutlinedTextField(
                    value = msmeNumber,
                    onValueChange = onMsmeNumberChange,
                    label = { Text(AppStrings.get(lang, "MSME / Udyam Number"), color = colors.textSecondary) },
                    placeholder = { Text("UDYAM-XX-XX-XXXXXXX", color = colors.textSecondary.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    isError = msmeNumberError,
                    supportingText = if (msmeNumberError) {{
                        Text(AppStrings.get(lang, "Enter a valid Udyam number (e.g. UDYAM-UP-09-0001234)"), color = Color.Red)
                    }} else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.primary, cursorColor = colors.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(AppStrings.get(lang, "Full Name"), color = colors.textSecondary) },
                    placeholder = { Text("Ramesh Sharma", color = colors.textSecondary.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    isError = nameError,
                    supportingText = if (nameError) {{ Text(AppStrings.get(lang, "Name is required"), color = Color.Red) }} else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.primary, cursorColor = colors.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Mobile Number
            OutlinedTextField(
                value = mobile,
                onValueChange = onMobileChange,
                label = { Text(AppStrings.get(lang, "Mobile Number"), color = colors.textSecondary) },
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
                supportingText = if (mobileError) {{ Text(AppStrings.get(lang, "Enter a valid 10-digit mobile number"), color = Color.Red) }} else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary, cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // ── CAPTCHA (MSME mode) / Email (normal mode) ──
            if (registerWithMsme) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = captchaInput,
                        onValueChange = onCaptchaInputChange,
                        label = { Text(AppStrings.get(lang, "Enter CAPTCHA"), color = colors.textSecondary) },
                        leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                        isError = captchaError,
                        supportingText = if (captchaError) {{
                            Text(AppStrings.get(lang, "Captcha doesn't match"), color = Color.Red)
                        }} else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                            focusedLabelColor = colors.primary, cursorColor = colors.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (msmeCaptchaLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.primary.copy(alpha = 0.07f))
                                .border(1.dp, colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.primary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Loading government captcha...", fontSize = 12.sp, color = colors.textSecondary)
                            }
                        }
                    } else if (isGovCaptcha && (captchaCode.contains("data:image") || captchaCode.startsWith("http"))) {
                        // Government captcha image from base64 or URL
                        GovCaptchaBox(
                            captchaBase64 = captchaCode,
                            colors = colors,
                            onRefresh = onRefreshCaptcha
                        )
                    } else {
                        CaptchaBox(code = captchaCode, colors = colors, onRefresh = onRefreshCaptcha)
                    }
                }
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text(AppStrings.get(lang, "Email Address"), color = colors.textSecondary) },
                    placeholder = { Text("you@example.com", color = colors.textSecondary.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    isError = emailError,
                    supportingText = if (emailError) {{ Text(AppStrings.get(lang, "Enter a valid email address"), color = Color.Red) }} else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.primary, cursorColor = colors.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Password with eye toggle + strength indicator
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(AppStrings.get(lang, "Password"), color = colors.textSecondary) },
                placeholder = { Text(AppStrings.get(lang, "8+ chars, upper, lower, digit, special"), color = colors.textSecondary.copy(alpha = 0.4f)) },
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
                        Text(AppStrings.get(lang, "Must be 8+ chars with uppercase, lowercase, digit & special char"), color = Color.Red)
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
                                AppStrings.get(lang, passwordStrength.label),
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
                enabled = if (registerWithMsme) {
                    msmeNumber.isNotBlank() && mobile.length == 10 && password.length >= 8 &&
                        captchaInput.isNotBlank() && !isChecking
                } else {
                    name.isNotBlank() && email.isNotBlank() && password.length >= 8 && mobile.length == 10 && !isChecking
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.background, strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (registerWithMsme) Icons.Default.Business else Icons.Default.PersonAdd,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        AppStrings.get(lang, if (registerWithMsme) "REGISTER WITH MSME" else "CREATE VAULT"),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp
                    )
                }
            }




        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// GOVERNMENT CAPTCHA BOX (displays base64 image from Udyam portal)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun GovCaptchaBox(
    captchaBase64: String,
    colors: AppColors,
    onRefresh: () -> Unit
) {
    val imageBytes = remember(captchaBase64) {
        if (captchaBase64.startsWith("data:")) {
            runCatching {
                val b64 = captchaBase64.substringAfter(',')
                val decoded = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                // PNG IEND chunk signature: 49 45 4E 44 AE 42 60 82
                val iendSignature = byteArrayOf(
                    0x49, 0x45, 0x4E, 0x44,
                    0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
                )
                var iend = -1
                if (decoded.size >= iendSignature.size) {
                    for (i in 0..decoded.size - iendSignature.size) {
                        var match = true
                        for (j in iendSignature.indices) {
                            if (decoded[i + j] != iendSignature[j]) {
                                match = false
                                break
                            }
                        }
                        if (match) {
                            iend = i
                            break
                        }
                    }
                }
                if (iend > 0) decoded.copyOfRange(0, iend + iendSignature.size) else decoded
            }.getOrNull()
        } else {
            null
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Display the government captcha image
        Box(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primary.copy(alpha = 0.07f))
                .border(1.dp, colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                imageBytes != null -> coil.compose.AsyncImage(
                    model = imageBytes,
                    contentDescription = "Government CAPTCHA from Udyam portal",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                captchaBase64.startsWith("http") -> coil.compose.AsyncImage(
                    model = captchaBase64,
                    contentDescription = "Government CAPTCHA from Udyam portal",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                else -> Text(
                    "Couldn't load captcha. Tap refresh to retry.",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primary.copy(alpha = 0.07f))
                .border(1.dp, colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh captcha", tint = colors.primary)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CAPTCHA BOX (client-side generated)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun CaptchaBox(
    code: String,
    colors: AppColors,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primary.copy(alpha = 0.07f))
                .border(1.dp, colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                code.forEachIndexed { index, ch ->
                    Text(
                        text = ch.toString(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (index % 2 == 0) colors.primary else colors.secondary,
                        modifier = Modifier.rotate(if (index % 2 == 0) -6f else 6f)
                    )
                }
            }
        }
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primary.copy(alpha = 0.07f))
                .border(1.dp, colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh captcha", tint = colors.primary)
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
