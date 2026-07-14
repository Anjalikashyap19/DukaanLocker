package com.example.dukaanlocker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dukaanlocker.ui.theme.*

@Composable
fun LoginScreen(
    onOwnerLogin: (mobile: String, name: String) -> Unit,
    onManagerLogin: (code: String) -> Unit,
    onRegister: (mobile: String, name: String) -> Unit = onOwnerLogin,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    // Role selection: null = not chosen, "register" = register, true = owner, false = manager
    var isOwner by remember { mutableStateOf<Any?>(null) }

    // Owner fields
    var mobile by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }

    // Manager fields
    var accessCode by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // App Logo
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(colors = listOf(colors.primary, colors.background))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = colors.background,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "DUKAAN LOCKER",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 3.sp
        )

        Text(
            text = "Secure Business Document Vault",
            fontSize = 13.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
        )

        // Theme toggle button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onToggleTheme) {
                Icon(
                    if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle theme",
                    modifier = Modifier.size(16.dp),
                    tint = colors.textSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(                            if (isDarkTheme) "Light" else "Dark",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Role Selection Cards ──────────────────────────────────────────
        if (isOwner == null) {
            // Prompt
            Text(
                text = "GET STARTED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Register Now Card — most prominent
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isOwner = "register"
                    },
                colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.12f)),
                border = BorderStroke(2.dp, colors.primary),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Register Now",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                        Text(
                            "First time user? Create your secure business vault",
                            fontSize = 12.sp,
                            color = colors.accent.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Separator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(modifier = Modifier.weight(1f), color = colors.border.copy(alpha = 0.5f))
                Text(
                    "  Returning User?  ",
                    fontSize = 11.sp,
                    color = colors.textSecondary.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                Divider(modifier = Modifier.weight(1f), color = colors.border.copy(alpha = 0.5f))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Owner Card
            RoleCard(
                icon = Icons.Default.Store,
                title = "Business Owner",
                subtitle = "Manage all your businesses, documents & team",
                onClick = { isOwner = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Manager Card
            RoleCard(
                icon = Icons.Default.Person,
                title = "Manager",
                subtitle = "Access assigned businesses using owner's code",
                onClick = { isOwner = false }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Your data is encrypted & stored securely on your device",
                fontSize = 11.sp,
                color = colors.textSecondary.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // ── Register Form ────────────────────────────────────────────────
        if (isOwner == "register") {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { it / 4 }
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
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Back + title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isOwner = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Create Account",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Set up your business document vault",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Mobile
                        OutlinedTextField(
                            value = mobile,
                            onValueChange = { mobile = it.filter { c -> c.isDigit() }.take(10) },
                            label = { Text("Mobile Number", color = colors.textSecondary) },
                            placeholder = { Text("9876543210", color = colors.textSecondary.copy(alpha = 0.4f)) },
                            leadingIcon = {
                                Text(
                                    text = "+91",
                                    color = colors.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                                focusedLabelColor = colors.primary, cursorColor = colors.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Name
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name", color = colors.textSecondary) },
                            placeholder = { Text("Ramesh Sharma", color = colors.textSecondary.copy(alpha = 0.4f)) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                                focusedLabelColor = colors.primary, cursorColor = colors.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                isChecking = true
                                onRegister(mobile, name)
                            },
                            enabled = mobile.length == 10 && name.isNotBlank() && !isChecking,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.background, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CREATE VAULT", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── Owner Login Form ──────────────────────────────────────────────
        if (isOwner == true) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { it / 4 }
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
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Back + title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isOwner = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Owner Sign In",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Access your business dashboard",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Mobile
                        OutlinedTextField(
                            value = mobile,
                            onValueChange = { mobile = it.filter { c -> c.isDigit() }.take(10) },
                            label = { Text("Mobile Number", color = colors.textSecondary) },
                            placeholder = { Text("9876543210", color = colors.textSecondary.copy(alpha = 0.4f)) },
                            leadingIcon = {
                                Text(
                                    text = "+91",
                                    color = colors.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                                focusedLabelColor = colors.primary, cursorColor = colors.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Name
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name", color = colors.textSecondary) },
                            placeholder = { Text("Ramesh Sharma", color = colors.textSecondary.copy(alpha = 0.4f)) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                                focusedLabelColor = colors.primary, cursorColor = colors.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                isChecking = true
                                onOwnerLogin(mobile, name)
                            },
                            enabled = mobile.length == 10 && name.isNotBlank() && !isChecking,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.background, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.VpnKey, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SECURE ACCESS", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── Manager Login Form ────────────────────────────────────────────
        if (isOwner == false) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { it / 4 }
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
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Back + title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isOwner = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Manager Access",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Enter access code provided by owner",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Info card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.background),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Ask your business owner for your unique 6-character access code",
                                    fontSize = 12.sp, color = colors.textSecondary
                                )
                            }
                        }

                        // Access Code
                        OutlinedTextField(
                            value = accessCode,
                            onValueChange = {
                                accessCode = it.uppercase().take(6)
                                codeError = false
                            },
                            label = { Text("Access Code", color = colors.textSecondary) },
                            placeholder = { Text("e.g. X7K9M2", color = colors.textSecondary.copy(alpha = 0.4f)) },
                            leadingIcon = {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                            },
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

                        // Code format hint
                        Row(verticalAlignment = Alignment.CenterVertically) {
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

                        Button(
                            onClick = {
                                onManagerLogin(accessCode)
                            },
                            enabled = accessCode.length == 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.secondary, contentColor = colors.textOnPrimary)
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("VERIFY & ACCESS", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

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
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(subtitle, fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
