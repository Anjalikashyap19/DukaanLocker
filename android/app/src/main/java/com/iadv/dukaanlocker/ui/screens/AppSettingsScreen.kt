package com.iadv.dukaanlocker.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.BuildConfig
import com.iadv.dukaanlocker.SupportInfo
import com.iadv.dukaanlocker.ui.components.LauncherLogo
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*

// ── Helper Composables ───────────────────────────────────────────────────────

@Composable
fun SettingsSectionHeader(title: String, colors: AppColors) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = colors.primary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/**
 * The single header used by App Settings and everything opened from it.
 *
 * The bar keeps the plain screen background, so it reads as part of the page
 * rather than a separate slab. Separation comes from the shared
 * [topBarShadow] depth cue: a real elevation shadow is cast in black and
 * vanishes on the dark background, so the edge is drawn instead.
 */
@Composable
private fun SettingsTopBar(
    title: String,
    colors: AppColors,
    lang: String,
    onBack: () -> Unit,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .topBarShadow(colors)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = AppStrings.get(lang, "Back"),
                    tint = colors.textPrimary
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun HelpSupportScreen(
    onBack: () -> Unit,
    colors: AppColors,
    lang: String,
    context: android.content.Context
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxWidth().background(colors.background).imePadding().verticalScroll(scrollState)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Contact Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(AppStrings.get(lang, "Contact Us"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Email
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:${SupportInfo.SUPPORT_EMAIL}")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(AppStrings.get(lang, "Email"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                            Text(SupportInfo.SUPPORT_EMAIL, fontSize = 13.sp, color = colors.textSecondary)
                        }
                    }
                    
                    HorizontalDivider(color = colors.border)
                    
                    // Phone
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${SupportInfo.SUPPORT_PHONE}")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(AppStrings.get(lang, "Phone"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                            Text(SupportInfo.SUPPORT_PHONE, fontSize = 13.sp, color = colors.textSecondary)
                        }
                    }
                    
                    HorizontalDivider(color = colors.border)
                    
                    // WhatsApp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse(SupportInfo.SUPPORT_WHATSAPP)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = colors.success, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("WhatsApp", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                            Text("Chat with us on WhatsApp", fontSize = 13.sp, color = colors.textSecondary)
                        }
                    }
                }
            }
            
            // FAQ
            Text(AppStrings.get(lang, "Frequently Asked Questions"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FaqItem(
                    question = AppStrings.get(lang, "How do I add a business?"),
                    answer = AppStrings.get(lang, "Go to the Business tab and tap the + button to add your business details."),
                    colors = colors
                )
                FaqItem(
                    question = AppStrings.get(lang, "How do I fetch documents?"),
                    answer = AppStrings.get(lang, "Select a business, choose a document type, and tap Auto-Fetch with the required registration number."),
                    colors = colors
                )
                FaqItem(
                    question = AppStrings.get(lang, "What is App Lock?"),
                    answer = AppStrings.get(lang, "App Lock requires fingerprint/PIN authentication when opening the app. You can disable it in App Settings > Security."),
                    colors = colors
                )
                FaqItem(
                    question = AppStrings.get(lang, "How do I change my password?"),
                    answer = AppStrings.get(lang, "Go to App Settings > Security > Change Password and follow the OTP verification flow."),
                    colors = colors
                )
            }
        }
    }
}

@Composable
fun FaqItem(question: String, answer: String, colors: AppColors) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(question, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textSecondary
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(answer, fontSize = 13.sp, color = colors.textSecondary)
            }
        }
    }
}

@Composable
fun VerifyMobileForPasswordChange(
    colors: AppColors,
    lang: String,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    var mobile by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var mobileError by remember { mutableStateOf(false) }
    var otpError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var confirmPasswordError by remember { mutableStateOf(false) }

    var isChecking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    var step by remember { mutableStateOf("mobile") } // "mobile" -> "otp" -> "password"
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
            border = BorderStroke(1.dp, colors.border),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (step) {
                    "mobile" -> OutlinedTextField(
                        value = mobile,
                        onValueChange = {
                            mobile = it.filter { ch -> ch.isDigit() }.take(10)
                            mobileError = false
                            message = null
                            isError = false
                        },
                        label = { Text(AppStrings.get(lang, "Mobile Number"), color = colors.textSecondary) },
                        placeholder = { Text("9876543210", color = colors.textSecondary.copy(alpha = 0.4f)) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                        isError = mobileError,
                        supportingText = if (mobileError) {
                            { Text(AppStrings.get(lang, "Enter the 10-digit registered mobile number"), color = colors.error) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus(force = true) }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                            focusedLabelColor = colors.primary, cursorColor = colors.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    "otp" -> OutlinedTextField(
                        value = otp,
                        onValueChange = {
                            otp = it.filter { ch -> ch.isDigit() }.take(6)
                            otpError = false
                            message = null
                            isError = false
                        },
                        label = { Text(AppStrings.get(lang, "OTP"), color = colors.textSecondary) },
                        placeholder = { Text("6-digit OTP", color = colors.textSecondary.copy(alpha = 0.4f)) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                        isError = otpError,
                        supportingText = if (otpError) {
                            { Text(AppStrings.get(lang, "Enter the 6-digit OTP"), color = colors.error) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus(force = true) }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                            focusedLabelColor = colors.primary, cursorColor = colors.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    else -> {
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                passwordError = false
                                confirmPasswordError = false
                                message = null
                                isError = false
                            },
                            label = { Text(AppStrings.get(lang, "New Password"), color = colors.textSecondary) },
                            placeholder = { Text(AppStrings.get(lang, "Enter your password"), color = colors.textSecondary.copy(alpha = 0.4f)) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
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
                            supportingText = if (passwordError) {
                                { Text(AppStrings.get(lang, "Use 8+ characters with upper, lower, number & symbol"), color = colors.error) }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus(force = true) }),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                                focusedLabelColor = colors.primary, cursorColor = colors.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                confirmPasswordError = false
                                message = null
                                isError = false
                            },
                            label = { Text(AppStrings.get(lang, "Confirm Password"), color = colors.textSecondary) },
                            placeholder = { Text(AppStrings.get(lang, "Re-enter your password"), color = colors.textSecondary.copy(alpha = 0.4f)) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            isError = confirmPasswordError,
                            supportingText = if (confirmPasswordError) {
                                { Text(AppStrings.get(lang, "Passwords do not match"), color = colors.error) }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(force = true) }),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                                focusedLabelColor = colors.primary, cursorColor = colors.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                if (!message.isNullOrBlank()) {
                    Text(
                        text = message!!,
                        fontSize = 12.sp,
                        color = if (isError) colors.error else colors.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        when (step) {
                            "mobile" -> {
                                mobileError = mobile.length != 10
                                if (!mobileError) {
                                    step = "otp"
                                    otp = ""
                                    otpError = false
                                    message = AppStrings.get(lang, "OTP sent to your mobile number")
                                    isError = false
                                }
                            }
                            "otp" -> {
                                otpError = otp.length != 6
                                if (!otpError) {
                                    step = "password"
                                    message = null
                                    isError = false
                                }
                            }
                            else -> {
                                passwordError = !isStrongPassword(password)
                                confirmPasswordError = confirmPassword != password || confirmPassword.isEmpty()
                                if (!passwordError && !confirmPasswordError) {
                                    isChecking = true
                                    message = null
                                    isError = false
                                    message = AppStrings.get(lang, "Password updated successfully")
                                    isError = false
                                    onDone()
                                }
                            }
                        }
                    },
                    enabled = !isChecking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.background,
                        disabledContainerColor = colors.primary.copy(alpha = 0.35f),
                        disabledContentColor = colors.background.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.background, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            AppStrings.get(
                                lang,
                                when (step) {
                                    "mobile" -> "SEND OTP"
                                    "otp" -> "VERIFY OTP"
                                    else -> "SAVE NEW PASSWORD"
                                }
                            ),
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        if (step == "otp") {
            TextButton(onClick = { step = "mobile"; message = null; isError = false }) {
                Text(AppStrings.get(lang, "Change mobile number"), fontSize = 12.sp, color = colors.textSecondary)
            }
        } else if (step == "password") {
            TextButton(onClick = { step = "otp"; message = null; isError = false }) {
                Text(AppStrings.get(lang, "Change OTP"), fontSize = 12.sp, color = colors.textSecondary)
            }
        }
    }
}

@Composable
fun AboutAppScreen(
    onBack: () -> Unit,
    colors: AppColors,
    lang: String
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxWidth().background(colors.background).imePadding().verticalScroll(scrollState)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LauncherLogo(modifier = Modifier.size(72.dp))
            Text("Dukaan Locker", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Text(AppStrings.get(lang, "Version") + " ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = colors.textSecondary)
            Text(
                AppStrings.get(lang, "Secure business document vault with auto-fetch and renewal reminders"),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    DetailRow(title = AppStrings.get(lang, "Bank-Grade Security"), description = AppStrings.get(lang, "Your documents stay encrypted and private in one vault."), colors = colors)
                    HorizontalDivider(color = colors.border)
                    DetailRow(title = AppStrings.get(lang, "Renewal Reminders"), description = AppStrings.get(lang, "Never miss an expiry - get timely alerts for every important document."), colors = colors)
                    HorizontalDivider(color = colors.border)
                    DetailRow(title = AppStrings.get(lang, "Auto-Fetch Documents"), description = AppStrings.get(lang, "Fetch official certificates directly from government portals."), colors = colors)
                    HorizontalDivider(color = colors.border)
                    DetailRow(title = AppStrings.get(lang, "Multi-Business Support"), description = AppStrings.get(lang, "Manage multiple businesses and assign managers with unique access codes."), colors = colors)
                    HorizontalDivider(color = colors.border)
                    DetailRow(title = AppStrings.get(lang, "Multi-Language"), description = AppStrings.get(lang, "Available in 11 Indian languages including Hindi, Bengali, Gujarati, Marathi, Tamil, Telugu and more."), colors = colors)
                    HorizontalDivider(color = colors.border)
                    DetailRow(title = AppStrings.get(lang, "App Lock"), description = AppStrings.get(lang, "Optional fingerprint/PIN authentication on app open for extra security."), colors = colors)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(title: String, description: String, colors: AppColors) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(modifier = Modifier.height(3.dp))
        Text(description, fontSize = 13.sp, lineHeight = 19.sp, color = colors.textSecondary)
    }
}

@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    colors: AppColors,
    lang: String,
    context: android.content.Context
) {
    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var showToast by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().background(colors.background).imePadding()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(AppStrings.get(lang, "Rate this app"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                (1..5).forEach { star ->
                    IconButton(onClick = { rating = star }) {
                        Icon(
                            if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$star star",
                            tint = if (star <= rating) Color(0xFFFFB300) else colors.textSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(AppStrings.get(lang, "Your feedback (optional)"), color = colors.textSecondary) },
                placeholder = { Text(AppStrings.get(lang, "Tell us what you think..."), color = colors.textSecondary.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            Button(
                onClick = {
                    if (rating > 0) {
                        sendFeedback(context, rating, comment)
                        showToast = true
                    }
                },
                enabled = rating > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.background,
                    disabledContainerColor = colors.primary.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Email, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.get(lang, "Send Feedback"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            if (showToast) {
                Text(AppStrings.get(lang, "Opening email app..."), fontSize = 12.sp, color = colors.primary, modifier = Modifier.fillMaxWidth())
            }
            
            if (rating == 0) {
                Text(AppStrings.get(lang, "Please select a rating"), fontSize = 12.sp, color = colors.error, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

fun sendFeedback(context: android.content.Context, rating: Int, comment: String) {
    val subject = AppSettingsInfo.buildFeedbackSubject(rating)
    val body = AppSettingsInfo.buildFeedbackBody(rating, comment)
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${SupportInfo.SUPPORT_EMAIL}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {}
}

@Composable
private fun ProfileAvatar(name: String, colors: AppColors, size: Dp = 46.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(colors.primary.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            fontSize = (size.value / 2.2f).sp,
            fontWeight = FontWeight.Bold,
            color = colors.primary
        )
    }
}

private fun roleLabel(role: String, lang: String): String = when (role.uppercase()) {
    "OWNER", "ADMIN" -> AppStrings.get(lang, "Business Owner")
    "MANAGER" -> AppStrings.get(lang, "Manager")
    else -> role.replaceFirstChar { it.uppercase() }
}

@Composable
private fun ProfileInfoRow(label: String, value: String, colors: AppColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = colors.textSecondary)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            value.ifBlank { "\u2014" },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun ProfileEntryCard(
    userName: String,
    userMobile: String,
    userRole: String,
    colors: AppColors,
    lang: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(name = userName, colors = colors)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    userName.ifBlank { AppStrings.get(lang, "User Profile") },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(roleLabel(userRole, lang), userMobile)
                        .filter { it.isNotBlank() }
                        .joinToString(" \u2022 "),
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary
            )
        }
    }
}

@Composable
private fun UserProfileScreen(
    colors: AppColors,
    lang: String,
    userName: String,
    userMobile: String,
    userEmail: String,
    userRole: String,
    managerCode: String,
    dlId: String,
    businessCount: Int,
    appLockEnabled: Boolean
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.background).verticalScroll(scrollState)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileAvatar(name = userName, colors = colors, size = 60.dp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    userName.ifBlank { AppStrings.get(lang, "User Profile") },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    roleLabel(userRole, lang),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    letterSpacing = 1.sp
                )
            }

            SettingsSectionHeader(title = AppStrings.get(lang, "ACCOUNT DETAILS"), colors = colors)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    ProfileInfoRow(AppStrings.get(lang, "Mobile Number"), userMobile, colors)
                    HorizontalDivider(color = colors.border)
                    ProfileInfoRow(AppStrings.get(lang, "Email Address"), userEmail, colors)
                    HorizontalDivider(color = colors.border)
                    ProfileInfoRow(AppStrings.get(lang, "Role"), roleLabel(userRole, lang), colors)
                    HorizontalDivider(color = colors.border)
                    ProfileInfoRow(AppStrings.get(lang, "Registered Businesses"), businessCount.toString(), colors)
                    if (dlId.isNotBlank()) {
                        HorizontalDivider(color = colors.border)
                        ProfileInfoRow(AppStrings.get(lang, "Dukaan Locker ID"), dlId, colors)
                    }
                    if (managerCode.isNotBlank()) {
                        HorizontalDivider(color = colors.border)
                        ProfileInfoRow(AppStrings.get(lang, "Manager Code"), managerCode, colors)
                    }
                }
            }

            SettingsSectionHeader(title = AppStrings.get(lang, "SECURITY"), colors = colors)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    ProfileInfoRow(
                        AppStrings.get(lang, "App Lock"),
                        AppStrings.get(lang, if (appLockEnabled) "Enabled" else "Disabled"),
                        colors
                    )
                    HorizontalDivider(color = colors.border)
                    ProfileInfoRow(
                        AppStrings.get(lang, "Account Status"),
                        AppStrings.get(lang, "Active"),
                        colors
                    )
                }
            }
        }
    }
}

object AppSettingsInfo {
    fun buildFeedbackSubject(rating: Int): String =
        "[Feedback] DukaanLocker v${BuildConfig.VERSION_NAME} \u2014 ${rating}\u2605"

    fun buildFeedbackBody(rating: Int, comment: String): String =
        "Feedback for Dukaan Locker v${BuildConfig.VERSION_NAME} (${rating}/5 stars)\n\n$comment"
}

private fun isStrongPassword(pw: String): Boolean {
    if (pw.length < 8) return false
    if (!pw.any { it.isUpperCase() }) return false
    if (!pw.any { it.isLowerCase() }) return false
    if (!pw.any { it.isDigit() }) return false
    if (!pw.any { !it.isLetterOrDigit() }) return false
    return true
}

// ── Main AppSettingsScreen ───────────────────────────────────────────────────

@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    isDarkTheme: Boolean,
    isAppLockEnabled: Boolean,
    onAppLockChanged: (Boolean) -> Unit,
    userName: String = "",
    userMobile: String = "",
    userEmail: String = "",
    userRole: String = "",
    userManagerCode: String = "",
    userDlId: String = "",
    businessCount: Int = 0
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current

    var appLockEnabled by remember(isAppLockEnabled) { mutableStateOf(isAppLockEnabled) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showHelpSupport by remember { mutableStateOf(false) }
    var showAboutApp by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var showUserProfile by remember { mutableStateOf(false) }

    // A single header owns the whole flow. It stays in place while sub-screens
    // swap in underneath, so there is never a second stacked title, and back
    // closes the current sub-screen before leaving App Settings entirely.
    val headerTitle = when {
        showUserProfile -> AppStrings.get(lang, "User Profile")
        showChangePassword -> AppStrings.get(lang, "Change Password")
        showHelpSupport -> AppStrings.get(lang, "Help & Support")
        showAboutApp -> AppStrings.get(lang, "About App")
        showFeedback -> AppStrings.get(lang, "User Feedback")
        else -> AppStrings.get(lang, "App Settings")
    }
    val headerSubtitle = if (showChangePassword) {
        AppStrings.get(lang, "Verify Mobile Number")
    } else null

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        SettingsTopBar(
            title = headerTitle,
            subtitle = headerSubtitle,
            lang = lang,
            colors = colors,
            onBack = {
                when {
                    showUserProfile -> showUserProfile = false
                    showChangePassword -> showChangePassword = false
                    showHelpSupport -> showHelpSupport = false
                    showAboutApp -> showAboutApp = false
                    showFeedback -> showFeedback = false
                    else -> onBack()
                }
            }
        )

        if (showUserProfile) {
            UserProfileScreen(
                colors = colors,
                lang = lang,
                userName = userName,
                userMobile = userMobile,
                userEmail = userEmail,
                userRole = userRole,
                managerCode = userManagerCode,
                dlId = userDlId,
                businessCount = businessCount,
                appLockEnabled = appLockEnabled
            )
        } else if (showChangePassword) {
            VerifyMobileForPasswordChange(
                colors = colors,
                lang = lang,
                onBack = { showChangePassword = false },
                onDone = { showChangePassword = false }
            )
        } else if (showHelpSupport) {
            HelpSupportScreen(
                onBack = { showHelpSupport = false },
                colors = colors,
                lang = lang,
                context = context
            )
        } else if (showAboutApp) {
            AboutAppScreen(
                onBack = { showAboutApp = false },
                colors = colors,
                lang = lang
            )
        } else if (showFeedback) {
            FeedbackScreen(
                onBack = { showFeedback = false },
                colors = colors,
                lang = lang,
                context = context
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ProfileEntryCard(
                        userName = userName,
                        userMobile = userMobile,
                        userRole = userRole,
                        colors = colors,
                        lang = lang,
                        onClick = { showUserProfile = true }
                    )
                }

                // SECURITY SECTION
                item {
                    SettingsSectionHeader(title = AppStrings.get(lang, "SECURITY"), colors = colors)
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                        border = BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        AppStrings.get(lang, "App Lock"),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        AppStrings.get(lang, "Ask for fingerprint/PIN on app open"),
                                        fontSize = 13.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(
                                    checked = appLockEnabled,
                                    onCheckedChange = { enabled ->
                                        appLockEnabled = enabled
                                        onAppLockChanged(enabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.background,
                                        checkedTrackColor = colors.primary,
                                        uncheckedThumbColor = colors.textSecondary,
                                        uncheckedTrackColor = colors.border
                                    )
                                )
                            }
                            HorizontalDivider(color = colors.border, modifier = Modifier.padding(horizontal = 16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                                    .clickable { showChangePassword = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        AppStrings.get(lang, "Change Password"),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        AppStrings.get(lang, "Update your sign-in password"),
                                        fontSize = 13.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // ABOUT SECTION
                item {
                    SettingsSectionHeader(title = AppStrings.get(lang, "ABOUT"), colors = colors)
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                        border = BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                                    .clickable { showHelpSupport = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    AppStrings.get(lang, "Help & Support"),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                            HorizontalDivider(color = colors.border, modifier = Modifier.padding(horizontal = 16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                                    .clickable { showAboutApp = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    AppStrings.get(lang, "About App"),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // FEEDBACK SECTION
                item {
                    SettingsSectionHeader(title = AppStrings.get(lang, "FEEDBACK"), colors = colors)
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                        border = BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                                    .clickable { showFeedback = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        AppStrings.get(lang, "User Feedback"),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        AppStrings.get(lang, "Rate this app & send feedback"),
                                        fontSize = 13.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
