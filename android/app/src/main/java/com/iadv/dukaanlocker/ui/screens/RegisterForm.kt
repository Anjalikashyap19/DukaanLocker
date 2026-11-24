package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.R
import com.iadv.dukaanlocker.api.RateLimitInfo
import com.iadv.dukaanlocker.ui.components.FieldErrorText
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun RegisterForm(
    colors: AppColors,
    lang: String,
    onBack: () -> Unit,
    onRegister: (name: String, email: String, password: String, mobile: String, onDone: () -> Unit) -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onInitMsmeCaptcha: (onResult: (sessionId: String, captchaImage: String) -> Unit) -> Unit = { onResult -> onResult("", "") },
    onMsmeLoginRequest: (msmeNumber: String, onResult: (success: Boolean, message: String?, limit: RateLimitInfo?) -> Unit) -> Unit = { _, _ -> },
    onRegisterWithMsme: (msmeNumber: String, mobile: String, sessionId: String, captchaText: String, onDone: () -> Unit) -> Unit = { _, _, _, _, onDone -> onDone() },
) {
    val focusManager = LocalFocusManager.current

    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var mobile by remember { mutableStateOf("") }
    var mobileError by remember { mutableStateOf(false) }
    var registerWithMsme by remember { mutableStateOf(false) }
    var msmeNumber by remember { mutableStateOf("") }
    var msmeNumberError by remember { mutableStateOf(false) }
    var captchaCode by remember { mutableStateOf(generateCaptcha()) }
    var regCaptchaInput by remember { mutableStateOf("") }
    var regCaptchaError by remember { mutableStateOf(false) }
    var msmeSessionId by remember { mutableStateOf("") }
    var msmeCaptchaImage by remember { mutableStateOf("") }
    var msmeCaptchaLoading by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }

    val regStrength = remember(password) { evaluatePasswordStrength(password) }

    fun isStrongPassword(pw: String): Boolean {
        if (pw.length < 8) return false
        if (!pw.any { it.isUpperCase() }) return false
        if (!pw.any { it.isLowerCase() }) return false
        if (!pw.any { it.isDigit() }) return false
        if (!pw.any { !it.isLetterOrDigit() }) return false
        return true
    }

    fun validateAndRegister() {
        nameError = name.isBlank()
        emailError = !isValidEmail(email)
        mobileError = mobile.length != 10
        passwordError = !isStrongPassword(password)
        if (!nameError && !emailError && !mobileError && !passwordError) {
            isChecking = true
            onRegister(name.trim(), email.trim(), password, mobile) {
                isChecking = false
            }
        }
    }

    fun validateAndRegisterWithMsme() {
        msmeNumberError = !isValidMsme(msmeNumber)
        mobileError = mobile.length != 10
        regCaptchaError = regCaptchaInput.isBlank()
        if (!msmeNumberError && !mobileError && !regCaptchaError) {
            isChecking = true
            onRegisterWithMsme(
                msmeNumber.trim().uppercase(),
                mobile,
                msmeSessionId,
                regCaptchaInput.trim()
            ) {
                isChecking = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Same 48dp touch target + 24dp Material arrow as the Sign In header,
            // so both auth screens share one back-arrow style.
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = AppStrings.get(lang, "Back"),
                    modifier = Modifier.size(24.dp),
                    tint = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    AppStrings.get(lang, if (registerWithMsme) "MSME Registration" else "Create Account"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    AppStrings.get(lang, if (registerWithMsme) "Verify your Udyam registration" else "Set up your business document vault"),
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primary.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Business,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    AppStrings.get(lang, "Have MSME Number?"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Text(
                    AppStrings.get(lang, "Register with your Udyam registration"),
                    fontSize = 10.sp,
                    color = colors.textSecondary
                )
            }
            Switch(
                checked = registerWithMsme,
                onCheckedChange = { newValue ->
                    focusManager.clearFocus(force = true)
                    registerWithMsme = newValue
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.primary,
                    checkedThumbColor = Color.White
                )
            )
        }

        if (registerWithMsme) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RegisterUiTextField(
                    label = AppStrings.get(lang, "MSME / Udyam Number"),
                    placeholder = "UDYAM-XX-XX-XXXXXXX",
                    value = msmeNumber,
                    onValueChange = {
                        val filtered = it.filter { ch -> ch.isLetterOrDigit() || ch == '-' }.uppercase()
                        msmeNumber = filtered
                        msmeNumberError = false
                    },
                    leadingIcon = Icons.Default.Business,
                    isError = msmeNumberError,
                    errorText = if (msmeNumberError) AppStrings.get(lang, "Enter a valid Udyam number (e.g. UDYAM-UP-09-0001234)") else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus(force = true) }),
                    colors = colors
                )
            }
        } else {
            RegisterUiTextField(
                label = AppStrings.get(lang, "Full Name"),
                placeholder = "Ramesh Sharma",
                value = name,
                onValueChange = { name = it; nameError = false },
                leadingIcon = Icons.Default.Person,
                isError = nameError,
                errorText = if (nameError) AppStrings.get(lang, "Name is required") else null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus(force = true) }),
                colors = colors
            )
        }

        RegisterUiTextField(
            label = AppStrings.get(lang, "Mobile Number"),
            placeholder = "9876543210",
            value = mobile,
            onValueChange = { mobile = it.filter { c -> c.isDigit() }.take(10); mobileError = false },
            leadingIcon = null,
            prefixText = "+91",
            isError = mobileError,
            errorText = if (mobileError) AppStrings.get(lang, "Enter a valid 10-digit mobile number") else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus(force = true) }),
            colors = colors
        )

        if (registerWithMsme) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RegisterUiTextField(
                    label = AppStrings.get(lang, "Enter CAPTCHA"),
                    placeholder = "Enter CAPTCHA",
                    value = regCaptchaInput,
                    onValueChange = { regCaptchaInput = it; regCaptchaError = false },
                    leadingIcon = Icons.Default.VerifiedUser,
                    isError = regCaptchaError,
                    errorText = if (regCaptchaError) AppStrings.get(lang, "Captcha doesn't match") else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus(force = true) }),
                    colors = colors
                )

                if (msmeCaptchaLoading) {
                    LoadingSurface(
                        message = "Loading captcha...",
                        colors = colors
                    )
                } else {
                    GovCaptchaBox(
                        captchaBase64 = msmeCaptchaImage,
                        colors = colors,
                        lang = lang,
                        onRefresh = {
                            msmeCaptchaLoading = true
                            onInitMsmeCaptcha { sessionId, captchaImage ->
                                msmeSessionId = sessionId
                                msmeCaptchaImage = captchaImage
                                regCaptchaInput = ""
                                regCaptchaError = false
                                msmeCaptchaLoading = false
                            }
                        }
                    )
                }
            }
        } else {
            RegisterUiTextField(
                label = AppStrings.get(lang, "Email Address"),
                placeholder = "you@example.com",
                value = email,
                onValueChange = { email = it; emailError = false },
                leadingIcon = Icons.Default.Email,
                isError = emailError,
                errorText = if (emailError) AppStrings.get(lang, "Enter a valid email address") else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus(force = true) }),
                colors = colors
            )

            RegisterUiTextField(
                label = AppStrings.get(lang, "Password"),
                placeholder = AppStrings.get(lang, "8+ chars, upper, lower, digit, special"),
                value = password,
                onValueChange = { password = it; passwordError = false },
                leadingIcon = Icons.Default.Lock,
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(48.dp)) {
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
                errorText = if (passwordError) AppStrings.get(lang, "Must be 8+ chars with uppercase, lowercase, digit & special char") else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus(force = true)
                        if (name.isNotBlank() && email.isNotBlank() && password.length >= 8 && mobile.length == 10) {
                            onRegister(name, email, password, mobile) { isChecking = false }
                        }
                    }
                ),
                colors = colors
            )
        }

        Button(
            onClick = {
                if (registerWithMsme) {
                    validateAndRegisterWithMsme()
                } else {
                    validateAndRegister()
                }
            },
            enabled = if (registerWithMsme) {
                msmeNumber.isNotBlank() && mobile.length == 10 &&
                    regCaptchaInput.isNotBlank() && msmeCaptchaImage.isNotEmpty() && !isChecking
            } else {
                name.isNotBlank() && email.isNotBlank() && password.length >= 8 && mobile.length == 10 && !isChecking
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.background,
                disabledContainerColor = colors.primary.copy(alpha = 0.4f),
                disabledContentColor = colors.textOnPrimary.copy(alpha = 0.6f)
            )
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = colors.background,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    if (registerWithMsme) Icons.Default.Business else Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.background
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    AppStrings.get(lang, if (registerWithMsme) "REGISTER WITH MSME" else "CREATE VAULT"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp, bottom = 16.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)
        ) {
            Text(
                AppStrings.get(lang, "Already have an account? Login"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.primary
            )
        }
    }
}

@Composable
private fun RegisterUiTextField(
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
        unfocusedBorderColor = colors.border,
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
        shape = RoundedCornerShape(12.dp),
        textStyle = TextStyle(
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
        )
    )
}

@Composable
private fun LoadingSurface(message: String, colors: AppColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.primary.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.primary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(message, fontSize = 12.sp, color = colors.textSecondary)
        }
    }
}

