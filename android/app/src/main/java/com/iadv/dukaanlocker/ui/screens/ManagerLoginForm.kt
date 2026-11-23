package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.ui.components.FieldErrorText
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.theme.AppColors

@Composable
fun ManagerLoginForm(
    colors: AppColors,
    lang: String,
    accessCode: String,
    onAccessCodeChange: (String) -> Unit,
    codeError: Boolean,
    lockoutRemainingMs: Long = 0L,
    onLogin: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header: title only, the root login screen owns the single back arrow ──
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                AppStrings.get(lang, "Manager Access"),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                AppStrings.get(lang, "Enter access code provided by owner"),
                fontSize = 12.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        UiTextField(
            label = AppStrings.get(lang, "Access Code"),
            placeholder = "XXXXXX",
            value = accessCode,
            onValueChange = { newValue ->
                val filtered = newValue.filter { ch -> ch.isLetterOrDigit() }.uppercase().take(6)
                onAccessCodeChange(filtered)
            },
            leadingIcon = Icons.Default.Lock,
            isError = codeError,
            errorText = if (codeError) AppStrings.get(lang, "Invalid access code") else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
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
            colors = colors
        )

        // ── Entered characters reflected in slots ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(6) { index ->
                val filled = index < accessCode.length
                val isNext = index == accessCode.length
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.cardBg)
                        .border(
                            width = if (isNext || codeError) 1.5.dp else 1.dp,
                            color = when {
                                codeError -> colors.error
                                isNext -> colors.primary
                                filled -> colors.primary.copy(alpha = 0.45f)
                                else -> colors.border
                            },
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (filled) accessCode[index].toString() else "",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textPrimary
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                AppStrings.get(lang, "Access code is 6 characters"),
                fontSize = 11.sp,
                color = colors.textSecondary.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${accessCode.length}/6",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary.copy(alpha = 0.8f)
            )
        }

        LoginLockoutBanner(colors = colors, remainingMs = lockoutRemainingMs)

        Button(
            onClick = onLogin,
            enabled = accessCode.length == 6 && lockoutRemainingMs <= 0L,
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
            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                AppStrings.get(lang, "VERIFY & ACCESS"),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

/** ── Reusable input field (shared pattern) ── */
@Composable
private fun UiTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    isError: Boolean = false,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    keyboardActions: KeyboardActions = KeyboardActions(),
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
        placeholder = { Text(placeholder, fontSize = 12.sp, color = colors.textSecondary.copy(alpha = 0.4f)) },
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
        isError = isError,
        supportingText = if (isError && errorText != null) {
            { FieldErrorText(text = errorText, colors = colors) }
        } else {
            null
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        colors = textFieldColors,
        shape = RoundedCornerShape(12.dp),
        textStyle = TextStyle(
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
        )
    )
}
