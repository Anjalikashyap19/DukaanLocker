package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.ui.components.LauncherLogo
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.LocalAppColors

@Composable
fun LoginScreen(
    onOwnerLogin: (email: String, password: String, onDone: () -> Unit) -> Unit,
    onManagerLogin: (code: String) -> Unit,
    onRegister: (name: String, email: String, password: String, mobile: String, onDone: () -> Unit) -> Unit,
    onRegisterWithMsme: (msmeNumber: String, mobile: String, sessionId: String, captchaText: String, onDone: () -> Unit) -> Unit = { _, _, _, _, onDone -> onDone() },
    onInitMsmeCaptcha: (onResult: (sessionId: String, captchaImage: String) -> Unit) -> Unit = { onResult -> onResult("", "") },
    onMsmeLoginRequest: (msmeNumber: String, onResult: (success: Boolean, message: String?) -> Unit) -> Unit = { _, _ -> },
    onMsmeLoginVerify: (msmeNumber: String, otp: String, onResult: (success: Boolean, message: String?) -> Unit) -> Unit = { _, _, _ -> },
    onGoogleSignIn: () -> Unit = {},
    onBackToMain: () -> Unit,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    isBiometricLoginEnabled: Boolean = false,
    onBiometricLogin: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    var selectedView by remember { mutableStateOf<Any?>(null) }

    var biometricTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (isBiometricLoginEnabled && !biometricTriggered) {
            biometricTriggered = true
            kotlinx.coroutines.delay(800)
            onBiometricLogin()
        }
    }

    var regName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regMobile by remember { mutableStateOf("") }
    var regPasswordVisible by remember { mutableStateOf(false) }
    var regIsChecking by remember { mutableStateOf(false) }
    var regNameError by remember { mutableStateOf(false) }
    var regEmailError by remember { mutableStateOf(false) }
    var regMobileError by remember { mutableStateOf(false) }
    var regPasswordError by remember { mutableStateOf(false) }

    var registerWithMsme by remember { mutableStateOf(false) }
    var regMsmeNumber by remember { mutableStateOf("") }
    var regMsmeError by remember { mutableStateOf(false) }
    var regCaptcha by remember { mutableStateOf(generateCaptcha()) }
    var regCaptchaInput by remember { mutableStateOf("") }
    var regCaptchaError by remember { mutableStateOf(false) }
    var msmeSessionId by remember { mutableStateOf("") }
    var msmeCaptchaImage by remember { mutableStateOf("") }
    var msmeCaptchaLoading by remember { mutableStateOf(false) }

    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var loginIsChecking by remember { mutableStateOf(false) }
    var loginEmailError by remember { mutableStateOf(false) }
    var loginPasswordError by remember { mutableStateOf(false) }

    var accessCode by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }

    val regStrength = remember(regPassword) { evaluatePasswordStrength(regPassword) }

    fun isStrongPassword(pw: String): Boolean {
        if (pw.length < 8) return false
        if (!pw.any { it.isUpperCase() }) return false
        if (!pw.any { it.isLowerCase() }) return false
        if (!pw.any { it.isDigit() }) return false
        if (!pw.any { !it.isLetterOrDigit() }) return false
        return true
    }

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

    fun validateAndRegisterWithMsme() {
        regMsmeError = !isValidMsme(regMsmeNumber)
        regMobileError = regMobile.length != 10
        regCaptchaError = regCaptchaInput.isBlank()
        if (!regMsmeError && !regMobileError && !regCaptchaError) {
            regIsChecking = true
            onRegisterWithMsme(
                regMsmeNumber.trim().uppercase(),
                regMobile,
                msmeSessionId,
                regCaptchaInput.trim()
            ) {
                regIsChecking = false
            }
        }
    }

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
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                    contentDescription = AppStrings.get(lang, "Home"),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onToggleTheme, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Icon(
                    if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = AppStrings.get(lang, "Toggle theme"),
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

        val contentAlign = if (selectedView == null || selectedView == "register") Alignment.TopCenter else Alignment.Center
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = contentAlign
        ) {
            when (selectedView) {
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
                    onLogin = { validateAndLogin() },
                    onMsmeLoginRequest = onMsmeLoginRequest,
                    onMsmeLoginVerify = onMsmeLoginVerify
                )

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

                false -> ManagerLoginForm(
                    colors = colors,
                    lang = lang,
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
