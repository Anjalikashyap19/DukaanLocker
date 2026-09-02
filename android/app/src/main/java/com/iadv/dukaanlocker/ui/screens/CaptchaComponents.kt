package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.theme.AppColors

@Composable
fun GovCaptchaBox(
    captchaBase64: String,
    colors: AppColors,
    lang: String,
    onRefresh: () -> Unit
) {
    val imageBytes = remember(captchaBase64) {
        if (captchaBase64.startsWith("data:")) {
            runCatching {
                val b64 = captchaBase64.substringAfter(',')
                val decoded = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
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
                imageBytes != null -> AsyncImage(
                    model = imageBytes,
                    contentDescription = AppStrings.get(lang, "Government CAPTCHA from Udyam portal"),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                captchaBase64.startsWith("http") -> AsyncImage(
                    model = captchaBase64,
                    contentDescription = AppStrings.get(lang, "Government CAPTCHA from Udyam portal"),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                else -> Text(
                    AppStrings.get(lang, "Couldn't load captcha. Tap refresh to retry."),
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
            Icon(Icons.Default.Refresh, contentDescription = AppStrings.get(lang, "Refresh captcha"), tint = colors.primary)
        }
    }
}

@Composable
fun CaptchaBox(
    code: String,
    colors: AppColors,
    lang: String,
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
            Icon(Icons.Default.Refresh, contentDescription = AppStrings.get(lang, "Refresh captcha"), tint = colors.primary)
        }
    }
}
