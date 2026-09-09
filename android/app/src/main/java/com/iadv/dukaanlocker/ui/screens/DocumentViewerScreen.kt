package com.iadv.dukaanlocker.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape

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
import com.iadv.dukaanlocker.api.ApiClient
import com.iadv.dukaanlocker.api.StreamDocumentRequest
import com.iadv.dukaanlocker.api.ViewDocumentRequest
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import java.io.File
import java.io.FileOutputStream
import okio.sink
import okio.buffer

private const val TAG = "DocumentViewer"
private const val MAX_RENDERED_AHEAD = 2
private const val MAX_RENDERED_BEHIND = 1

data class PdfPageInfo(
    val pageIndex: Int,
    val width: Int,
    val height: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    documentId: Long,
    documentName: String,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pageInfos by remember { mutableStateOf<List<PdfPageInfo>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var renderedPages by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }

    DisposableEffect(documentId) {
        onDispose {
            renderedPages.values.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    suspend fun loadDocument() {
        isLoading = true
        errorMessage = null
        pdfFile?.delete()
        renderedPages.values.forEach { if (!it.isRecycled) it.recycle() }

        val maxRetries = 2
        for (attempt in 1..maxRetries) {
            try {
                Log.d(TAG, "Attempt $attempt/$maxRetries - Requesting view token for documentId=$documentId")
                val tokenResponse = withContext(Dispatchers.IO) {
                    ApiClient.getDocumentStreamApi(context).requestViewToken(
                        ViewDocumentRequest(documentId = documentId)
                    )
                }

                if (!tokenResponse.isSuccessful) {
                    val error = "Failed to get view token: ${tokenResponse.code()}"
                    Log.e(TAG, error)
                    if (tokenResponse.code() in 400..499) {
                        errorMessage = error
                        isLoading = false
                        return
                    }
                    throw Exception(error)
                }

                val tokenData = tokenResponse.body() ?: throw Exception("Invalid response from server")
                Log.d(TAG, "Got view token: ${tokenData.viewToken}")

                val streamResponse = withContext(Dispatchers.IO) {
                    ApiClient.getDocumentStreamApi(context).streamDocument(
                        StreamDocumentRequest(viewToken = tokenData.viewToken)
                    )
                }

                if (!streamResponse.isSuccessful) {
                    val error = "Failed to stream document: ${streamResponse.code()}"
                    Log.e(TAG, error)
                    if (streamResponse.code() in 400..499) {
                        errorMessage = error
                        isLoading = false
                        return
                    }
                    throw Exception(error)
                }

                val responseBody = streamResponse.body() ?: throw Exception("Empty response body")
                Log.d(TAG, "Got document stream response, contentLength=${responseBody.contentLength()}")

                val tempFile = withContext(Dispatchers.IO) {
                    saveResponseBodyToFile(context, responseBody, "temp_document.pdf")
                } ?: throw Exception("Failed to save document to file")

                Log.d(TAG, "Document saved to: ${tempFile.absolutePath}, size=${tempFile.length()}")

                if (tempFile.length() < 100) {
                    val fileContent = tempFile.readText()
                    tempFile.delete()
                    throw Exception("Invalid document response: $fileContent")
                }

                val infos = withContext(Dispatchers.IO) {
                    getPdfPageInfos(tempFile)
                }

                if (infos.isEmpty()) {
                    tempFile.delete()
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

                Log.d(TAG, "PDF has ${infos.size} pages")
                renderedPages.values.forEach { if (!it.isRecycled) it.recycle() }
                pdfFile?.delete()

                pageInfos = infos
                pdfFile = tempFile
                currentPage = 0
                renderedPages = emptyMap()
                isLoading = false
                return

            } catch (e: Exception) {
                Log.e(TAG, "Error loading document (attempt $attempt/$maxRetries)", e)
                val isRetryable = e is java.io.EOFException ||
                    e is java.io.IOException ||
                    (e.message?.contains("ChunkedSource") == true) ||
                    (e.message?.contains("connection") == true && !e.message!!.contains("refused"))

                if (isRetryable && attempt < maxRetries) {
                    kotlinx.coroutines.delay(1000L * attempt)
                } else {
                    errorMessage = when {
                        e is java.io.EOFException -> AppStrings.get(lang, "Connection lost. Please check your network and try again.")
                        e.message?.contains("ChunkedSource") == true -> AppStrings.get(lang, "Connection interrupted. Please try again.")
                        e.message?.contains("view token") == true -> AppStrings.get(lang, "Session expired. Please try again.")
                        else -> "${AppStrings.get(lang, "Error loading document")}: ${e.message}"
                    }
                    isLoading = false
                    return
                }
            }
        }

        errorMessage = AppStrings.get(lang, "Failed to load document after multiple attempts. Please try again later.")
        isLoading = false
    }

    LaunchedEffect(documentId) {
        coroutineScope.launch { loadDocument() }
    }

    fun renderPageIfNeeded(pageIndex: Int) {
        if (renderedPages.containsKey(pageIndex)) return
        val file = pdfFile ?: return
        if (pageIndex < 0 || pageIndex >= pageInfos.size) return

        coroutineScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                renderSinglePage(file, pageIndex)
            }
            if (bitmap != null) {
                renderedPages = renderedPages + (pageIndex to bitmap)
            }
        }
    }

    fun recycleDistantPages(current: Int) {
        val toRecycle = renderedPages.keys.filter { page ->
            page < current - MAX_RENDERED_BEHIND || page > current + MAX_RENDERED_AHEAD
        }
        if (toRecycle.isNotEmpty()) {
            val newMap = renderedPages.toMutableMap()
            toRecycle.forEach { page ->
                newMap.remove(page)?.let { bitmap ->
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            renderedPages = newMap
        }
    }

    LaunchedEffect(currentPage, pageInfos.size) {
        if (pageInfos.isEmpty()) return@LaunchedEffect
        renderPageIfNeeded(currentPage)
        for (i in 1..MAX_RENDERED_AHEAD) {
            renderPageIfNeeded(currentPage + i)
        }
        for (i in 1..MAX_RENDERED_BEHIND) {
            renderPageIfNeeded(currentPage - i)
        }
        recycleDistantPages(currentPage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = documentName, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.get(lang, "Back"),

                            )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.primary.copy(alpha = 0.15f)
                ),
                modifier = Modifier.height(78.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(AppStrings.get(lang, "Loading document..."), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(AppStrings.get(lang, "Securely fetching from server"), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }

                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(64.dp), tint = colors.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(AppStrings.get(lang, "Failed to load document"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = colors.textPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(errorMessage ?: AppStrings.get(lang, "Unknown error"), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = colors.textSecondary)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { coroutineScope.launch { loadDocument() } }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(AppStrings.get(lang, "Retry"))
                            }
                        }
                    }
                }

                pageInfos.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                             Text(AppStrings.get(lang, "No document to display"), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (pageInfos.size > 1) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = colors.accent
                            ) {
                             Text("${AppStrings.get(lang, "Page")} ${currentPage + 1} ${AppStrings.get(lang, "of")} ${pageInfos.size}",
                                 modifier = Modifier.fillMaxWidth().padding(12.dp),
                                 textAlign = TextAlign.Center,
                                 style = MaterialTheme.typography.bodyMedium,
                                 fontWeight = FontWeight.Medium,
                                 color = colors.primary
                             )
                            }
                        }

                        val listState = rememberLazyListState()

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(pageInfos, key = { _, page -> page.pageIndex }) { index, pageInfo ->
                                val bitmap = renderedPages[pageInfo.pageIndex]
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "${AppStrings.get(lang, "PDF Page")} ${pageInfo.pageIndex + 1}",
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.FillWidth
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height((pageInfo.height * 0.5f).dp.coerceIn(200.dp, 600.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(32.dp))
                                        }
                                    }
                                }
                            }
                        }

                        if (pageInfos.size > 1) {
                            Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 4.dp) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            if (currentPage > 0) {
                                                currentPage--
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(currentPage)
                                                }
                                            }
                                        },
                                        enabled = currentPage > 0,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (currentPage > 0) colors.primary else colors.border
                                        )
                                     ) { Text(AppStrings.get(lang, "Previous")) }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Button(
                                        onClick = {
                                            if (currentPage < pageInfos.size - 1) {
                                                currentPage++
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(currentPage)
                                                }
                                            }
                                        },
                                        enabled = currentPage < pageInfos.size - 1,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (currentPage < pageInfos.size - 1) colors.primary else colors.border
                                        )
                                     ) { Text(AppStrings.get(lang, "Next")) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun saveResponseBodyToFile(
    context: Context,
    responseBody: okhttp3.ResponseBody?,
    fileName: String
): File? {
    return withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, fileName)
            Log.d(TAG, "Saving to file: ${tempFile.absolutePath}")
            if (responseBody != null) {
                val source = responseBody.source()
                FileOutputStream(tempFile).use { outputStream ->
                    val sink = outputStream.sink().buffer()
                    val bufferSize = 8192L
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
            try { File(context.cacheDir, fileName).delete() } catch (_: Exception) {}
            null
        }
    }
}

private fun getPdfPageInfos(pdfFile: File): List<PdfPageInfo> {
    val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
    val pdfRenderer = PdfRenderer(fileDescriptor)
    val infos = mutableListOf<PdfPageInfo>()

    val maxPages = minOf(pdfRenderer.pageCount, 50)
    for (i in 0 until maxPages) {
        val page = pdfRenderer.openPage(i)
        infos.add(PdfPageInfo(pageIndex = i, width = page.width, height = page.height))
        page.close()
    }

    pdfRenderer.close()
    fileDescriptor.close()
    return infos
}

private fun renderSinglePage(pdfFile: File, pageIndex: Int): Bitmap? {
    return try {
        val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(fileDescriptor)

        if (pageIndex >= pdfRenderer.pageCount) {
            pdfRenderer.close()
            fileDescriptor.close()
            return null
        }

        val page = pdfRenderer.openPage(pageIndex)

        val scaleFactor = when {
            page.width > 1000 -> 1.5f
            page.width > 500 -> 2.0f
            else -> 2.5f
        }

        val bitmap = Bitmap.createBitmap(
            (page.width * scaleFactor).toInt().coerceAtMost(1600),
            (page.height * scaleFactor).toInt().coerceAtMost(1600),
            Bitmap.Config.ARGB_8888
        )
        bitmap.eraseColor(android.graphics.Color.WHITE)

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        Log.d(TAG, "Rendered page ${pageIndex + 1}")

        page.close()
        pdfRenderer.close()
        fileDescriptor.close()

        bitmap
    } catch (e: Exception) {
        Log.e(TAG, "Error rendering page ${pageIndex + 1}", e)
        null
    }
}
