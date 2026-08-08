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
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.EOFException
import okio.sink
import okio.buffer

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
    
    // Debug tag for logging
    val TAG = "DocumentViewer"
    
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

    // Function to load document with retry logic
    suspend fun loadDocument() {
        isLoading = true
        errorMessage = null
        
        val maxRetries = 2
        var lastException: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                Log.d(TAG, "Attempt $attempt/$maxRetries - Requesting view token for documentId=$documentId")
                // Step 1: Request view token
                val tokenResponse = withContext(Dispatchers.IO) {
                    ApiClient.getDocumentStreamApi(context).requestViewToken(
                        ViewDocumentRequest(documentId = documentId)
                    )
                }
                
                if (!tokenResponse.isSuccessful) {
                    val error = "Failed to get view token: ${tokenResponse.code()}"
                    Log.e(TAG, error)
                    // Don't retry on 4xx errors (client errors)
                    if (tokenResponse.code() in 400..499) {
                        errorMessage = error
                        isLoading = false
                        return
                    }
                    throw Exception(error)
                }
                
                val tokenData = tokenResponse.body()
                if (tokenData == null) {
                    throw Exception("Invalid response from server")
                }
                
                Log.d(TAG, "Got view token: ${tokenData.viewToken}")
                
                // Step 2: Stream document using the token
                Log.d(TAG, "Step 2: Streaming document with token")
                val streamResponse = withContext(Dispatchers.IO) {
                    ApiClient.getDocumentStreamApi(context).streamDocument(
                        StreamDocumentRequest(viewToken = tokenData.viewToken)
                    )
                }
                
                if (!streamResponse.isSuccessful) {
                    val error = "Failed to stream document: ${streamResponse.code()}"
                    Log.e(TAG, error)
                    // Don't retry on 4xx errors (client errors)
                    if (streamResponse.code() in 400..499) {
                        errorMessage = error
                        isLoading = false
                        return
                    }
                    throw Exception(error)
                }
                
                val responseBody = streamResponse.body()
                if (responseBody == null) {
                    throw Exception("Empty response body")
                }
                
                Log.d(TAG, "Got document stream response, contentLength=${responseBody.contentLength()}")
                
                // Step 3: Save to temporary file and render PDF
                Log.d(TAG, "Step 3: Saving document to temp file")
                val tempFile = withContext(Dispatchers.IO) {
                    saveResponseBodyToFile(context, responseBody, "temp_document.pdf")
                }
                
                if (tempFile == null) {
                    throw Exception("Failed to save document to file")
                }
                
                Log.d(TAG, "Document saved to: ${tempFile.absolutePath}, size=${tempFile.length()}")
                
                // Check if file is too small to be a valid PDF
                if (tempFile.length() < 100) {
                    // Try to read the error message from the file
                    val fileContent = tempFile.readText()
                    tempFile.delete()
                    throw Exception("Invalid document response: $fileContent")
                }
                
                // Step 4: Render PDF pages to bitmaps
                Log.d(TAG, "Step 4: Rendering PDF to bitmaps")
                val bitmaps = withContext(Dispatchers.IO) {
                    renderPdfToBitmaps(tempFile)
                }
                
                if (bitmaps.isEmpty()) {
                    Log.e(TAG, "PDF rendering returned empty list")
                    // Check if file is actually a PDF
                    if (tempFile.length() == 0L) {
                        throw Exception("Document file is empty")
                    } else {
                        // Try to check if it's an HTML error page
                        val firstBytes = tempFile.inputStream().use { input ->
                            val buffer = ByteArray(100)
                            val read = input.read(buffer)
                            String(buffer, 0, read)
                        }
                        if (firstBytes.contains("<!DOCTYPE") || firstBytes.contains("<html")) {
                            throw Exception("Server returned an error page instead of a document")
                        } else {
                            throw Exception("Failed to render PDF. The document may be corrupted or in an unsupported format.")
                        }
                    }
                }
                
                Log.d(TAG, "Successfully rendered ${bitmaps.size} pages")
                pdfBitmaps = bitmaps
                isLoading = false
                
                // Step 5: Clean up temporary file
                tempFile.delete()
                return  // Success - exit retry loop
                
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Error loading document (attempt $attempt/$maxRetries)", e)
                
                // Check if this is a retryable error
                val isRetryable = e is java.io.EOFException ||
                    e is java.io.IOException ||
                    (e.message?.contains("ChunkedSource") == true) ||
                    (e.message?.contains("connection") == true &&
                     !e.message!!.contains("refused"))
                
                if (isRetryable && attempt < maxRetries) {
                    Log.d(TAG, "Retryable error, waiting before retry...")
                    kotlinx.coroutines.delay(1000L * attempt) // Exponential backoff
                } else {
                    // Non-retryable error or max retries reached
                    errorMessage = when {
                        e is java.io.EOFException -> "Connection lost. Please check your network and try again."
                        e.message?.contains("ChunkedSource") == true -> "Connection interrupted. Please try again."
                        e.message?.contains("view token") == true -> "Session expired. Please try again."
                        else -> "Error loading document: ${e.message}"
                    }
                    isLoading = false
                    return
                }
            }
        }
        
        // If we get here, all retries failed
        errorMessage = "Failed to load document after multiple attempts. Please try again later."
        isLoading = false
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
 * Save response body to a temporary file with chunked reading support.
 */
private suspend fun saveResponseBodyToFile(
    context: Context,
    responseBody: okhttp3.ResponseBody?,
    fileName: String
): File? {
    val TAG = "DocumentViewer"
    return withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, fileName)
            Log.d(TAG, "Saving to file: ${tempFile.absolutePath}")
            if (responseBody != null) {
                // Use source-based reading for better chunked response handling
                val source = responseBody.source()
                FileOutputStream(tempFile).use { outputStream ->
                    val sink = outputStream.sink().buffer()
                    // Read in chunks to handle chunked transfer encoding properly
                    val bufferSize = 8192L  // 8KB chunks
                    var totalBytesRead = 0L
                    while (!source.exhausted()) {
                        val bytesRead = source.read(sink.buffer, bufferSize)
                        if (bytesRead == -1L) break
                        totalBytesRead += bytesRead
                    }
                    sink.flush()
                    Log.d(TAG, "Wrote $totalBytesRead bytes to file")
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error saving response body to file", e)
            // Delete partial file on error
            try {
                File(context.cacheDir, fileName).delete()
            } catch (_: Exception) {}
            null
        }
    }
}

/**
 * Render PDF pages to bitmaps with memory-efficient settings.
 */
private suspend fun renderPdfToBitmaps(pdfFile: File): List<Bitmap> {
    val TAG = "DocumentViewer"
    return withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        
        try {
            Log.d(TAG, "Opening PDF file: ${pdfFile.absolutePath}, size=${pdfFile.length()}")
            val fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            
            val pdfRenderer = PdfRenderer(fileDescriptor)
            val pageCount = pdfRenderer.pageCount
            Log.d(TAG, "PDF has $pageCount pages")
            
            // Limit to first 20 pages to prevent memory issues
            val maxPages = minOf(pageCount, 20)
            
            for (i in 0 until maxPages) {
                val page = pdfRenderer.openPage(i)
                Log.d(TAG, "Rendering page ${i + 1}/${maxPages}, width=${page.width}, height=${page.height}")
                
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
                
                Log.d(TAG, "Page ${i + 1} rendered successfully")
                bitmaps.add(bitmap)
                page.close()
            }
            
            pdfRenderer.close()
            fileDescriptor.close()
            Log.d(TAG, "PDF rendering complete, ${bitmaps.size} pages rendered")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering PDF", e)
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
