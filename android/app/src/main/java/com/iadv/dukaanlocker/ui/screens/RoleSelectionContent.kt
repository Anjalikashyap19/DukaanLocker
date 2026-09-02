package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

        RoleCard(
            icon = Icons.Default.Person,
            title = AppStrings.get(lang, "Business Owner"),
            subtitle = AppStrings.get(lang, "Sign in with email & password"),
            onClick = onSelectOwnerLogin
        )

        Spacer(modifier = Modifier.height(10.dp))

        RoleCard(
            icon = Icons.Default.Lock,
            title = AppStrings.get(lang, "Manager"),
            subtitle = AppStrings.get(lang, "Access assigned businesses with code"),
            onClick = onSelectManagerLogin
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onGoogleSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.cardBg,
                contentColor = colors.textPrimary
            ),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = AppStrings.get(lang, "Google Sign-In"),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(AppStrings.get(lang, "Google sign in"), fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
    }
}

@Composable
fun RoleCard(
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
