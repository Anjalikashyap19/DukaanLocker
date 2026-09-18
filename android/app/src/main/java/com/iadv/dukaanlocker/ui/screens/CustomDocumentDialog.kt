package com.iadv.dukaanlocker.ui.screens

import android.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*

@Composable
fun CustomDocumentDialog(
    onDismiss: () -> Unit,
    onUpload: (documentName: String) -> Unit,
    selectedFileName: String? = null,
    onChooseFile: () -> Unit
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    var documentName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBg,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    AppStrings.get(lang, "Add Custom Document"),
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    AppStrings.get(lang, "Enter the document name and choose a PDF file to upload."),
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )

                OutlinedTextField(
                    value = documentName,
                    onValueChange = { documentName = it },
                    label = { Text(AppStrings.get(lang, "Document Name"), color = colors.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = colors.primary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.primary,
                        cursorColor = colors.textPrimary,

                    ),

                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedButton(
                    onClick = onChooseFile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Icon(
                        Icons.Default.FileOpen,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (selectedFileName != null) selectedFileName
                        else AppStrings.get(lang, "Choose File"),
                        color = if (selectedFileName != null) colors.textPrimary else colors.textSecondary,
                        fontSize = 14.sp
                    )
                }

                Text(
                    AppStrings.get(lang, "Only PDF files up to 10MB are allowed."),
                    fontSize = 11.sp,
                    color = colors.textSecondary.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpload(documentName.trim()) },
                enabled = documentName.isNotBlank() && selectedFileName != null,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.background
                )
            ) {
                Text(AppStrings.get(lang, "Upload"), fontWeight = FontWeight.Bold , color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.get(lang, "Cancel"), color = colors.textSecondary)
            }
        }
    )
}
