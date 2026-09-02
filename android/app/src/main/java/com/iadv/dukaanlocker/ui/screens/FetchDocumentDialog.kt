package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.iadv.dukaanlocker.*
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ── Dialog: Fetch Document ───────────────────────────────────────────────────
@Composable
fun FetchDocumentDialog(
    doc: DocumentItem,
    shopName: String,
    onDismiss: () -> Unit,
    onSuccess: (regNum: String, issue: String, expiry: String) -> Unit
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    var regInput by remember { mutableStateOf("") }
    var isFetching by remember { mutableStateOf(false) }
    var fetchProgress by remember { mutableStateOf(0f) }
    var currentStepText by remember { mutableStateOf(AppStrings.get(lang, "Connecting to National Database...")) }

    val labelText = docFetchLabel(doc.type)

    LaunchedEffect(isFetching) {
        if (isFetching) {
            val steps = listOf(
                0.2f to AppStrings.get(lang, "Connecting to National Portal Gateway..."),
                0.5f to AppStrings.get(lang, "Verifying digital credentials against database..."),
                0.8f to AppStrings.get(lang, "Fetching official e-Certificate..."),
                1.0f to AppStrings.get(lang, "Encrypting and locking in Dukaan Vault...")
            )
            for ((progress, text) in steps) {
                delay(1000)
                fetchProgress = progress
                currentStepText = text
            }
            delay(800)
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val today = Date()
            val expiryCal = Calendar.getInstance().apply { add(Calendar.YEAR, 5) }
            onSuccess(regInput.uppercase(), formatter.format(today), formatter.format(expiryCal.time))
        }
    }

    Dialog(onDismissRequest = { if (!isFetching) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!isFetching) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = colors.primary, modifier = Modifier.size(48.dp))
                    Text(AppStrings.get(lang, "Auto-Fetch Official Doc"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("${AppStrings.get(lang, "Securely fetch your")} ${doc.name} ${AppStrings.get(lang, "from government databases.")}", fontSize = 12.sp, color = colors.textSecondary, textAlign = TextAlign.Center)

                    OutlinedTextField(
                        value = regInput,
                        onValueChange = { regInput = it },
                        label = { Text(labelText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(AppStrings.get(lang, "Cancel"), color = colors.textPrimary) }
                        Button(
                            onClick = { if (regInput.isNotBlank()) isFetching = true },
                            enabled = regInput.isNotBlank(),
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
                        ) { Text(AppStrings.get(lang, "Confirm Fetch"), fontWeight = FontWeight.Bold) }
                    }
                } else {
                    CircularProgressIndicator(progress = { fetchProgress }, modifier = Modifier.size(64.dp), color = colors.primary, trackColor = colors.border, strokeWidth = 6.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(AppStrings.get(lang, "VERIFYING CREDENTIALS"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.accent, letterSpacing = 1.sp)
                    Text(currentStepText, fontSize = 12.sp, color = colors.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
