package com.example.dukaanlocker.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dukaanlocker.api.ApiClient
import com.example.dukaanlocker.api.StreamDocumentRequest
import com.example.dukaanlocker.api.ViewDocumentRequest
import com.example.dukaanlocker.ui.theme.GoldColor
import com.example.dukaanlocker.ui.theme.LightGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Screen for securely viewing documents using one-time view tokens.
 * 
 * Security Features:
 * - One-time view tokens with 15-second TTL
 * - Documents streamed directly from private S3 bucket
 * - No S3 URLs exposed to client
 * - Automatic token cleanup after viewing
 * 
 * @param documentId The ID of the document to view
 * @param documentName Display name of the document
 * @param onBack Callback to navigate back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    documentId: Long,
    documentName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pdfBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var currentPage by remember { mutableStateOf(0) }
    
    // Use DisposableEffect to clean up bitmaps when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            // Recycle all bitmaps to free memory
            pdfBitmaps.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    // Function to load document
    suspend fun loadDocument() {
        isLoading = true
        errorMessage = null
        
        try {
            // Step 1: Request view token
            val tokenResponse = withContext(Dispatchers.IO) {
                ApiClient.getDocumentStreamApi(context).requestViewToken(
                    ViewDocumentRequest(documentId = documentId)
                )
            }
            
            if (!tokenResponse.isSuccessful) {
                errorMessage = "Failed to get view token: ${tokenResponse.code()}"
                isLoading = false
                return
            }
            
            val tokenData = tokenResponse.body()
            if (tokenData == null) {
                errorMessage = "Invalid response from server"
                isLoading = false
                return
            }
            
            // Step 2: Stream document using the token
            val streamResponse = withContext(Dispatchers.IO) {
                ApiClient.getDocumentStreamApi(context).streamDocument(
                    StreamDocumentRequest(viewToken = tokenData.viewToken)
                )
            }
            
            if (!streamResponse.isSuccessful) {
                errorMessage = "Failed to stream document: ${streamResponse.code()}"
                isLoading = false
                return
            }
            
            val responseBody = streamResponse.body()
            if (responseBody == null) {
                errorMessage = "Empty response body"
                isLoading = false
                return
            }
            
            // Step 3: Save to temporary file and render PDF
            val tempFile = withContext(Dispatchers.IO) {
                saveResponseBodyToFile(context, responseBody, "temp_document.pdf")
            }
            
            if (tempFile == null) {
                errorMessage = "Failed to save document"
                isLoading = false
                return
            }
            
            // Step 4: Render PDF pages to bitmaps
            val bitmaps = withContext(Dispatchers.IO) {
                renderPdfToBitmaps(tempFile)
            }
            
            pdfBitmaps = bitmaps
            isLoading = false
            
            // Step 5: Clean up temporary file
            tempFile.delete()
            
        } catch (e: Exception) {
            errorMessage = "Error loading document: ${e.message}"
            isLoading = false
        }
    }

    // Load document on first composition
    LaunchedEffect(documentId) {
        coroutineScope.launch {
            loadDocument()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = documentName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Refresh button
                    IconButton(
                        onClick = {
                            // Recycle old bitmaps before reloading
                            pdfBitmaps.forEach { bitmap ->
                                if (!bitmap.isRecycled) {
                                    bitmap.recycle()
                                }
                            }
                            pdfBitmaps = emptyList()
                            currentPage = 0
                            coroutineScope.launch {
                                loadDocument()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                    
                    // Open in external app button
                    if (pdfBitmaps.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                // Create temporary file for external viewing
                                coroutineScope.launch {
                                    try {
                                        val tempFile = withContext(Dispatchers.IO) {
                                            saveResponseBodyToFile(context, null, "temp_share.pdf")
                                        }
                                        
                                        if (tempFile != null) {
                                            val uri = Uri.fromFile(tempFile)
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/pdf")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                            
                                            // Clean up after delay
                                            kotlinx.coroutines.delay(1000)
                                            tempFile.delete()
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "Error opening document: ${e.message}"
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Open in external app"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = GoldColor,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading document...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Securely fetching from server",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                errorMessage != null -> {
                    // Error state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Failed to load document",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        loadDocument()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
                
                pdfBitmaps.isEmpty() -> {
                    // No document state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No document to display",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                else -> {
                    // Document viewer
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Page indicator
                        if (pdfBitmaps.size > 1) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = LightGold
                            ) {
                                Text(
                                    text = "Page ${currentPage + 1} of ${pdfBitmaps.size}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = GoldColor
                                )
                            }
                        }
                        
                        // PDF page image
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = pdfBitmaps[currentPage].asImageBitmap(),
                                contentDescription = "PDF Page ${currentPage + 1}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                        
                        // Navigation controls (if multiple pages)
                        if (pdfBitmaps.size > 1) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Previous button
                                    Button(
                                        onClick = {
                                            if (currentPage > 0) {
                                                currentPage--
                                            }
                                        },
                                        enabled = currentPage > 0,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (currentPage > 0) GoldColor 
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Text("Previous")
                                    }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    // Next button
                                    Button(
                                        onClick = {
                                            if (currentPage < pdfBitmaps.size - 1) {
                                                currentPage++
                                            }
                                        },
                                        enabled = currentPage < pdfBitmaps.size - 1,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (currentPage < pdfBitmaps.size - 1) GoldColor 
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Text("Next")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Save response body to a temporary file.
 */
private suspend fun saveResponseBodyToFile(
    context: Context,
    responseBody: okhttp3.ResponseBody?,
    fileName: String
): File? {
    return withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, fileName)
            if (responseBody != null) {
                responseBody.byteStream().use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Render PDF pages to bitmaps with memory-efficient settings.
 */
private suspend fun renderPdfToBitmaps(pdfFile: File): List<Bitmap> {
    return withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        
        try {
            val fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            
            val pdfRenderer = PdfRenderer(fileDescriptor)
            val pageCount = pdfRenderer.pageCount
            
            // Limit to first 20 pages to prevent memory issues
            val maxPages = minOf(pageCount, 20)
            
            for (i in 0 until maxPages) {
                val page = pdfRenderer.openPage(i)
                
                // Calculate scale factor based on page size
                val scaleFactor = when {
                    page.width > 1000 -> 1.5f  // Large pages
                    page.width > 500 -> 2.0f   // Medium pages
                    else -> 2.5f                // Small pages
                }
                
                // Create bitmap for the page with size limit
                val bitmap = Bitmap.createBitmap(
                    (page.width * scaleFactor).toInt().coerceAtMost(2048),
                    (page.height * scaleFactor).toInt().coerceAtMost(2048),
                    Bitmap.Config.ARGB_8888
                )
                
                // Set white background
                bitmap.eraseColor(android.graphics.Color.WHITE)
                
                // Render the page
                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                
                bitmaps.add(bitmap)
                page.close()
            }
            
            pdfRenderer.close()
            fileDescriptor.close()
            
        } catch (e: Exception) {
            // Clean up any bitmaps created before error
            bitmaps.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
        
        bitmaps
    }
}
