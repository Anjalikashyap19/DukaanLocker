package com.iadv.dukaanlocker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.R
import com.iadv.dukaanlocker.ui.theme.LocalAppColors

/**
 * Standard empty-state view (inspired by Bspotted error/empty screens):
 * illustration (empty_state_illustration.png) → bold title → secondary subtitle → faint hint.
 * Use wherever a screen has no data to show.
 */
@Composable
fun EmptyStateView(
    title: String,
    subtitle: String? = null,
    hint: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.empty_state_illustration),
            contentDescription = null,
            modifier = Modifier.width(240.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                subtitle,
                fontSize = 14.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
        if (!hint.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                hint,
                fontSize = 12.sp,
                color = colors.textSecondary.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )
        }
    }
}
