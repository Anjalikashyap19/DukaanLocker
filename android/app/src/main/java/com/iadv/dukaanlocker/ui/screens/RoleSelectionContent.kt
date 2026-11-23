package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.R
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.theme.AppColors
import com.iadv.dukaanlocker.ui.theme.LocalAppColors

@Composable
fun RoleSelectionContent(
    colors: AppColors,
    lang: String,
    isBiometricLoginEnabled: Boolean = false,
    onSelectRegister: () -> Unit,
    onSelectOwnerLogin: () -> Unit,
    onSelectManagerLogin: () -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onBiometricLogin: () -> Unit = {}
) {
    val localColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ── "Create Account" — primary CTA (filled, most prominent) ──
        Button(
            onClick = onSelectRegister,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.textOnPrimary
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.textOnPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    AppStrings.get(lang, "Create Account"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textOnPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Divider: "Returning User?" ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = colors.border.copy(alpha = 0.5f))
            Text(
                "  ${AppStrings.get(lang, "Returning User?")}  ",
                fontSize = 10.sp,
                color = colors.textSecondary.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = colors.border.copy(alpha = 0.5f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Business Owner — secondary (clean text-led card, no icons) ──
        RoleCard(
            title = AppStrings.get(lang, "Business Owner"),
            subtitle = AppStrings.get(lang, "Sign in with email & password"),
            onClick = onSelectOwnerLogin,
            colors = colors,
            isSelected = false
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Manager — secondary (clean text-led card, no icons) ──
        RoleCard(
            title = AppStrings.get(lang, "Manager"),
            subtitle = AppStrings.get(lang, "Access assigned businesses with code"),
            onClick = onSelectManagerLogin,
            colors = colors,
            isSelected = false
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Google Sign-In — visually distinct but consistent ──
        Button(
            onClick = onGoogleSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.cardBg,
                contentColor = colors.textPrimary
            ),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = AppStrings.get(lang, "Google Sign-In"),
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    AppStrings.get(lang, "Google sign in"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
            }
        }

        // ── Biometric login (if enabled) ──
        if (isBiometricLoginEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onBiometricLogin,
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.textSecondary
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = AppStrings.get(lang, "Biometric Login"),
                        modifier = Modifier.size(18.dp),
                        tint = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        AppStrings.get(lang, "Biometric Login"),
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

/** ── Clean text-led role card (no decorative icons) ── */
@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    colors: AppColors,
    isSelected: Boolean = false
) {
    val cardBg = if (isSelected) colors.primary.copy(alpha = 0.10f) else colors.cardBg
    val borderColor = if (isSelected) colors.primary else colors.border

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 0.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
