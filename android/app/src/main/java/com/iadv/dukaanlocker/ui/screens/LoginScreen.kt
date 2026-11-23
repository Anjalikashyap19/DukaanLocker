package com.iadv.dukaanlocker.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.api.RateLimitInfo
import com.iadv.dukaanlocker.R
import com.iadv.dukaanlocker.ui.components.FieldErrorText
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.AppColors
import com.iadv.dukaanlocker.ui.theme.LocalAppColors

@Composable
fun LoginScreen(
    onOwnerLogin: (email: String, password: String, onDone: (success: Boolean) -> Unit) -> Unit,
    onManagerLogin: (code: String, onDone: (success: Boolean) -> Unit) -> Unit,
    onRegister: () -> Unit,
    onRegisterWithMsme: (msmeNumber: String, mobile: String, sessionId: String, captchaText: String, onDone: () -> Unit) -> Unit = { _, _, _, _, onDone -> onDone() },
    onInitMsmeCaptcha: (onResult: (sessionId: String, captchaImage: String) -> Unit) -> Unit = { onResult -> onResult("", "") },
    onMsmeLoginRequest: (msmeNumber: String, onResult: (success: Boolean, message: String?, limit: RateLimitInfo?) -> Unit) -> Unit = { _, _ -> },
    onMsmeLoginVerify: (msmeNumber: String, otp: String, onResult: (success: Boolean, message: String?) -> Unit) -> Unit = { _, _, _ -> },
    onForgotPasswordRequest: (mobile: String, onResult: (success: Boolean, message: String?, limit: RateLimitInfo?) -> Unit) -> Unit = { _, _ -> },
    onForgotPasswordReset: (mobile: String, otp: String, password: String, onResult: (success: Boolean, message: String?) -> Unit) -> Unit = { _, _, _, _ -> },
    onMobileHint: (email: String, onResult: (mobileEnding: String?) -> Unit) -> Unit = { _, onResult -> onResult(null) },
    onGoogleSignIn: () -> Unit = {},
    onBackToMain: () -> Unit,
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

    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var loginIsChecking by remember { mutableStateOf(false) }
    var loginEmailError by remember { mutableStateOf(false) }
    var loginPasswordError by remember { mutableStateOf(false) }
    // Shown on the form when an action needs attention but has no field of its own
    // (today: tapping "forgot password?" without a registered email typed).
    var loginFormMessage by remember { mutableStateOf<String?>(null) }

    var mobileEnding by remember { mutableStateOf<String?>(null) }

    var accessCode by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    // Swapping the auth view disposes the fields of the old one. Clearing focus first
    // keeps Compose's focus tree consistent (a field removed while focused can leave
    // an active parent with no focused child and crash the next tap).
    val authFocusManager = LocalFocusManager.current
    var ownerLockoutMs by remember { mutableStateOf(LoginRateLimiter.lockoutRemainingMs(context, LoginRateLimiter.OWNER)) }
    var managerLockoutMs by remember { mutableStateOf(LoginRateLimiter.lockoutRemainingMs(context, LoginRateLimiter.MANAGER)) }

    LaunchedEffect(ownerLockoutMs > 0L) {
        if (ownerLockoutMs <= 0L) return@LaunchedEffect
        while (ownerLockoutMs > 0L) {
            kotlinx.coroutines.delay(1000)
            ownerLockoutMs = LoginRateLimiter.lockoutRemainingMs(context, LoginRateLimiter.OWNER)
        }
    }
    LaunchedEffect(managerLockoutMs > 0L) {
        if (managerLockoutMs <= 0L) return@LaunchedEffect
        while (managerLockoutMs > 0L) {
            kotlinx.coroutines.delay(1000)
            managerLockoutMs = LoginRateLimiter.lockoutRemainingMs(context, LoginRateLimiter.MANAGER)
        }
    }

    fun validateAndLogin() {
        loginEmailError = !isValidEmail(loginEmail)
        loginPasswordError = loginPassword.isBlank()
        if (!loginEmailError && !loginPasswordError && ownerLockoutMs <= 0L) {
            loginIsChecking = true
            onOwnerLogin(loginEmail.trim(), loginPassword) { success ->
                loginIsChecking = false
                if (success) {
                    LoginRateLimiter.recordSuccess(context, LoginRateLimiter.OWNER)
                } else {
                    ownerLockoutMs = LoginRateLimiter.recordFailure(context, LoginRateLimiter.OWNER)
                }
            }
        }
    }

    // The keyboard counts as open when the IME inset is non-zero; the sign-in form then
    // trims itself down so only the email/password fields and the LOGIN button are shown.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // One back arrow, identical on every auth screen. It returns to home from the
            // sign-in root and back to the sign-in form from an embedded sub-view, so the
            // embedded forms no longer draw a second arrow of their own.
            // Standard 48dp touch target around a 24dp Material back arrow so the
            // icon is centred and reads like the other screen app bars.
            IconButton(
                onClick = {
                    if (selectedView == null) {
                        onBackToMain()
                    } else {
                        authFocusManager.clearFocus()
                        selectedView = null
                        codeError = false
                        mobileEnding = null
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = AppStrings.get(lang, "Back"),
                    modifier = Modifier.size(24.dp),
                    tint = colors.textPrimary
                )
            }
        }

        AnimatedContent(
            // The sign-in form is the root of this screen; manager, MSME and forgot
            // password are one step deeper, so they slide in from the right and slide
            // back out to the left, matching the app's screen transitions.
            targetState = selectedView,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val forward = targetState != null
                val enter = slideInHorizontally(
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) { width -> if (forward) width / 12 else -width / 12 } + fadeIn(
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                )
                val exit = fadeOut(
                    animationSpec = tween(120, easing = FastOutLinearInEasing)
                )
                enter togetherWith exit
            },
            label = "authViewTransition"
        ) { view ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // The idle sign-in screen never scrolls; only while the keyboard is open,
                    // so the focused field and the LOGIN button stay reachable.
                    .then(if (imeVisible) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (view == null) {
                    Text(
                        text = AppStrings.get(lang, "Sign In"),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = AppStrings.get(lang, "Access your business document vault."),
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.85f).padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                } else {
                    // Sub-views (manager, MSME, forgot password) have no page title above
                    // them, so keep a small gap under the top bar.
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Box(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (view) {
                        null -> LoginForm(
                            colors = colors,
                            lang = lang,
                            email = loginEmail,
                            onEmailChange = { loginEmail = it; loginEmailError = false; loginFormMessage = null },
                            emailError = loginEmailError,
                            password = loginPassword,
                            onPasswordChange = { loginPassword = it; loginPasswordError = false },
                            passwordVisible = loginPasswordVisible,
                            onTogglePasswordVisible = { loginPasswordVisible = !loginPasswordVisible },
                            passwordError = loginPasswordError,
                            isChecking = loginIsChecking,
                            compact = imeVisible,
                            lockoutRemainingMs = ownerLockoutMs,
                            formMessage = loginFormMessage,
                            onLogin = { validateAndLogin() },
                            onForgotPassword = {
                                // The reset is keyed to the account's registered email, so
                                // require a valid email on the sign-in form first instead of
                                // opening a reset flow that cannot be tied to an account.
                                val typedEmail = loginEmail.trim()
                                if (!isValidEmail(typedEmail)) {
                                    loginEmailError = true
                                    loginFormMessage = AppStrings.get(
                                        lang,
                                        "Enter your registered email address to reset your password"
                                    )
                                } else {
                                    loginEmailError = false
                                    loginFormMessage = null
                                    mobileEnding = null
                                    // The hint fills in the masked tail shown above the
                                    // mobile field of the reset screen.
                                    onMobileHint(typedEmail) { ending -> mobileEnding = ending }
                                    authFocusManager.clearFocus()
                                    selectedView = "forgot"
                                }
                            },
                            onMsmeLoginRequest = onMsmeLoginRequest,
                            onMsmeLoginVerify = onMsmeLoginVerify,
                            onMsmeLoginTap = {
                                authFocusManager.clearFocus()
                                selectedView = "msme"
                            },
                            onGoogleSignIn = onGoogleSignIn,
                            onManagerLoginTap = {
                                authFocusManager.clearFocus()
                                selectedView = "manager"
                            }
                        )

                        "msme" -> MsmeLoginForm(
                            colors = colors,
                            lang = lang,
                            onMsmeLoginRequest = onMsmeLoginRequest,
                            onMsmeLoginVerify = onMsmeLoginVerify,
                            onBack = {
                                authFocusManager.clearFocus()
                                selectedView = null
                            },
                            scrollable = false,
                            showBack = false
                        )

                        "manager" -> ManagerLoginForm(
                            colors = colors,
                            lang = lang,
                            accessCode = accessCode,
                            onAccessCodeChange = { accessCode = it.uppercase().take(6); codeError = false },
                            codeError = codeError,
                            lockoutRemainingMs = managerLockoutMs,
                            onLogin = {
                                when {
                                    managerLockoutMs > 0L -> Unit
                                    accessCode.length == 6 -> {
                                        loginIsChecking = true
                                        onManagerLogin(accessCode) { success ->
                                            loginIsChecking = false
                                            if (success) {
                                                LoginRateLimiter.recordSuccess(context, LoginRateLimiter.MANAGER)
                                            } else {
                                                managerLockoutMs = LoginRateLimiter.recordFailure(context, LoginRateLimiter.MANAGER)
                                            }
                                        }
                                    }
                                    else -> codeError = true
                                }
                            }
                        )

                        "forgot" -> ForgotPasswordScreen(
                            colors = colors,
                            lang = lang,
                            mobileEnding = mobileEnding,
                            onRequest = onForgotPasswordRequest,
                            onReset = onForgotPasswordReset,
                            onDone = {
                                authFocusManager.clearFocus()
                                selectedView = null
                                loginPassword = ""
                                loginPasswordError = false
                            }
                        )
                    }
                }

                if (view == null && !imeVisible) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.border.copy(alpha = 0.5f))
                        Text(
                            "  ${AppStrings.get(lang, "Don't have an account?")}  ",
                            fontSize = 12.sp,
                            color = colors.textSecondary.copy(alpha = 0.8f),
                            letterSpacing = 1.sp
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.border.copy(alpha = 0.5f))
                    }

                    TextButton(
                        onClick = onRegister,
                        modifier = Modifier.padding(top = 4.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)
                    ) {
                        Text(
                            AppStrings.get(lang, "Sign Up"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = AppStrings.get(lang, "Secure Business Locker for Your Business"),
                        fontSize = 10.sp,
                        color = colors.textSecondary.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LoginForm(
    colors: AppColors,
    lang: String,
    email: String, onEmailChange: (String) -> Unit, emailError: Boolean,
    password: String, onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean, onTogglePasswordVisible: () -> Unit,
    passwordError: Boolean,
    isChecking: Boolean,
    compact: Boolean = false,
    lockoutRemainingMs: Long = 0L,
    formMessage: String? = null,
    onLogin: () -> Unit,
    onForgotPassword: () -> Unit = {},
    onMsmeLoginRequest: (msmeNumber: String, onResult: (Boolean, String?, RateLimitInfo?) -> Unit) -> Unit = { _, _ -> },
    onMsmeLoginVerify: (msmeNumber: String, otp: String, onResult: (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onMsmeLoginTap: () -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onManagerLoginTap: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LoginUiTextField(
            label = AppStrings.get(lang, "Email Address"),
            placeholder = "you@example.com",
            value = email,
            onValueChange = onEmailChange,
            leadingIcon = Icons.Default.Email,
            isError = emailError,
            errorText = if (emailError) AppStrings.get(lang, "Enter a valid email address") else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
            colors = colors
        )

        LoginUiTextField(
            label = AppStrings.get(lang, "Password"),
            placeholder = AppStrings.get(lang, "Enter your password"),
            value = password,
            onValueChange = onPasswordChange,
            leadingIcon = Icons.Default.Lock,
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisible, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) AppStrings.get(lang, "Hide password") else AppStrings.get(lang, "Show password"),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            isError = passwordError,
            errorText = if (passwordError) AppStrings.get(lang, "Password is required") else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (email.isNotBlank() && password.isNotBlank()) {
                        onLogin()
                    }
                }
            ),
            colors = colors
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onForgotPassword,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)
            ) {
                Text(
                    AppStrings.get(lang, "forgot password?"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary
                )
            }
        }

        if (!formMessage.isNullOrBlank()) {
            Text(
                text = formMessage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.error,
                modifier = Modifier.fillMaxWidth()
            )
        }

        LoginLockoutBanner(colors = colors, remainingMs = lockoutRemainingMs)

        Button(
            onClick = onLogin,
            enabled = email.isNotBlank() && password.isNotBlank() && !isChecking && lockoutRemainingMs <= 0L,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.background,
                disabledContainerColor = colors.primary.copy(alpha = 0.4f),
                disabledContentColor = colors.background.copy(alpha = 0.6f)
            )
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = colors.background,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    AppStrings.get(lang, "LOGIN"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        if (!compact) {
            SecondaryLoginOptions(
                colors = colors,
                lang = lang,
                onMsmeLoginTap = onMsmeLoginTap,
                onManagerLoginTap = onManagerLoginTap,
                onGoogleSignIn = onGoogleSignIn
            )
        }
    }
}

/** MSME, manager and Google entry points; hidden while the keyboard is open. */
@Composable
private fun SecondaryLoginOptions(
    colors: AppColors,
    lang: String,
    onMsmeLoginTap: () -> Unit,
    onManagerLoginTap: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SecondaryAuthButton(
            label = AppStrings.get(lang, "Login with MSME / Udyam"),
            colors = colors,
            onClick = onMsmeLoginTap
        ) {
            Icon(
                Icons.Default.Business,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        SecondaryAuthButton(
            label = AppStrings.get(lang, "Manager Login"),
            colors = colors,
            onClick = onManagerLoginTap
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        SecondaryAuthButton(
            label = AppStrings.get(lang, "Google Sign-In"),
            colors = colors,
            onClick = onGoogleSignIn
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** One pill-shaped secondary action shared by MSME, manager and Google sign-in. */
@Composable
private fun SecondaryAuthButton(
    label: String,
    colors: AppColors,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.cardBg,
            contentColor = colors.textPrimary
        )
    ) {
        icon()
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun LoginUiTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    prefixText: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    keyboardActions: KeyboardActions = KeyboardActions(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    colors: AppColors,
    modifier: Modifier = Modifier
) {
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = if (isError) colors.error else colors.primary,
        unfocusedBorderColor = if (isError) colors.error.copy(alpha = 0.5f) else colors.border,
        focusedLabelColor = if (isError) colors.error else colors.primary,
        unfocusedLabelColor = colors.textSecondary,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        cursorColor = colors.primary
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp, color = colors.textSecondary) },
        placeholder = { Text(placeholder, fontSize = 14.sp, color = colors.textSecondary.copy(alpha = 0.4f)) },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (isError) colors.error else colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        prefix = prefixText?.let {
            {
                Text(
                    text = it,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        },
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = if (isError && errorText != null) {
            { FieldErrorText(text = errorText, colors = colors) }
        } else {
            null
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        colors = textFieldColors,
        shape = RoundedCornerShape(12.dp)
    )
}
