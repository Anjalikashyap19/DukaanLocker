package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.AppColors

@Composable
fun ManagerLoginForm(
    colors: AppColors,
    lang: String,
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = AppStrings.get(lang, "Back"), tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(AppStrings.get(lang, "Manager Access"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(AppStrings.get(lang, "Enter access code provided by owner"), fontSize = 12.sp, color = colors.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = accessCode,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { ch -> ch.isLetterOrDigit() }.uppercase().take(6)
                    onAccessCodeChange(filtered)
                },
                label = { Text(AppStrings.get(lang, "Access Code"), color = colors.textSecondary) },
                placeholder = { Text("e.g. X7K9M2", color = colors.textSecondary.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(20.dp)) },
                isError = codeError,
                supportingText = if (codeError) {{ Text(AppStrings.get(lang, "Invalid access code"), color = colors.error) }} else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (accessCode.length == 6) {
                            onLogin()
                        }
                    }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.secondary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.secondary, cursorColor = colors.secondary
                ),
                shape = RoundedCornerShape(12.dp)
            )

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
                Text(AppStrings.get(lang, "VERIFY & ACCESS"), fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
            }
        }
    }
}
