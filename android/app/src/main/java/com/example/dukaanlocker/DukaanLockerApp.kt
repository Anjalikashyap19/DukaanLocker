package com.example.dukaanlocker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.dukaanlocker.api.*
import com.example.dukaanlocker.ui.screens.*
import com.example.dukaanlocker.ui.strings.LocalAppLanguage
import com.example.dukaanlocker.ui.theme.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DukaanLockerApp(onThemeChange: (Boolean) -> Unit = {}, onLanguageChanged: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient.getApiService(context) }

    // ── Auth state ──
    var isLoggedIn by remember { mutableStateOf(ApiClient.isLoggedIn(context)) }
    var authToken by remember { mutableStateOf(ApiClient.getToken(context)) }
    var currentUserId by remember { mutableStateOf(ApiClient.getUserId(context)) }
    var currentUserName by remember { mutableStateOf(ApiClient.getUserName(context)) }
    var currentUserEmail by remember { mutableStateOf(ApiClient.getUserEmail(context)) }
    var currentUserRole by remember { mutableStateOf(ApiClient.getUserRole(context)) }
    var currentUserManagerCode by remember { mutableStateOf(ApiClient.getManagerCode(context)) }

    // ── Business/Shop state ──
    var shops by remember { mutableStateOf<List<ShopResponse>>(emptyList()) }
    var selectedShop by remember { mutableStateOf<ShopResponse?>(null) }
    var shopDocuments by remember { mutableStateOf<List<DocumentResponse>>(emptyList()) }

    // ── Manager state ──
    var managers by remember { mutableStateOf<List<ManagerResponse>>(emptyList()) }
    // Maps manager ID to list of assigned shop IDs
    var managerShopAssignments by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }

    // ── Navigation state ──
    var currentScreen by remember { mutableStateOf("splash") }
    var editShopTarget by remember { mutableStateOf<ShopResponse?>(null) }
    var selectedBottomTab by remember { mutableStateOf("home") }

    // ── Theme state ──
    var isDarkTheme by remember { mutableStateOf(LockerStorage.getTheme(context)) }

    // ── Language state ──
    var language by remember { mutableStateOf(LockerStorage.getLanguage(context)) }

    // ── Dialog states ──
    var docForView by remember { mutableStateOf<DocumentItem?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // ── Document Viewer state ──
    var viewDocumentId by remember { mutableStateOf<Long?>(null) }
    var viewDocumentName by remember { mutableStateOf<String>("") }

    // ── Upload / Fetch state ──
    var pendingUploadDoc by remember { mutableStateOf<DocumentItem?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var showFetchDialog by remember { mutableStateOf(false) }
    var fetchTargetDoc by remember { mutableStateOf<DocumentItem?>(null) }

    // ── Helper: Load documents for a shop (accumulates into shopDocuments) ──
    suspend fun loadDocuments(shopId: Long) {
        try {
            val response = api.getShopDocuments(shopId)
            if (response.isSuccessful) {
                val docs = response.body() ?: emptyList()
                shopDocuments = shopDocuments.filter { it.shopId != shopId } + docs
            }
        } catch (e: Exception) {
            // Log error for debugging
            android.util.Log.e("DukaanLocker", "Failed to load documents for shop $shopId", e)
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && pendingUploadDoc != null) {
            scope.launch {
                isUploading = true
                isLoading = true
                try {
                    val doc = pendingUploadDoc!!
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: return@launch
                    inputStream.close()
                    val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
                    val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                    val fileName = "${doc.type.lowercase()}.pdf"
                    val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
                    val shopId = doc.businessId.toLongOrNull() ?: return@launch
                    val urlDocType = doc.type.lowercase().replace("_", "-")
                    val response = api.uploadDocumentViaDocs(
                        shopId = shopId,
                        documentType = urlDocType,
                        file = filePart
                    )
                    if (response.isSuccessful) {
                        Toast.makeText(context, "${doc.name} uploaded!", Toast.LENGTH_SHORT).show()
                        loadDocuments(shopId)
                    } else {
                        Toast.makeText(context, "Upload failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Upload error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                isUploading = false
                isLoading = false
                pendingUploadDoc = null
            }
        }
    }

    // ── Helper: Load shops + their documents from API ──
    suspend fun loadShops() {
        try {
            val response = if (currentUserRole == "MANAGER") {
                api.getMyAssignedShops()
            } else {
                api.getMyShops()
            }
            if (response.isSuccessful) {
                shops = response.body() ?: emptyList()
                // Load documents for all shops so each card shows correct doc count
                for (shop in shops) {
                    loadDocuments(shop.id)
                }
            }
        } catch (e: Exception) {
            // Log error for debugging
            android.util.Log.e("DukaanLocker", "Failed to load shops", e)
        }
    }

    // ── Helper: Load managers and their assigned shops ──
    suspend fun loadManagers() {
        try {
            val response = api.getManagers()
            if (response.isSuccessful) {
                val mgrs = response.body() ?: emptyList()
                managers = mgrs
                // Fetch assigned shops for each manager
                val assignments = mutableMapOf<Long, List<String>>()
                for (mgr in mgrs) {
                    try {
                        val shopsResponse = api.getManagerShops(mgr.id)
                        if (shopsResponse.isSuccessful) {
                            val assignedShops = shopsResponse.body() ?: emptyList()
                            assignments[mgr.id] = assignedShops.map { it.id.toString() }
                        }
                    } catch (e: Exception) {
                        // Log error for debugging
                        android.util.Log.e("DukaanLocker", "Failed to load shops for manager ${mgr.id}", e)
                        assignments[mgr.id] = emptyList()
                    }
                }
                managerShopAssignments = assignments
            }
        } catch (e: Exception) {
            // Log error for debugging
            android.util.Log.e("DukaanLocker", "Failed to load managers", e)
        }
    }

    // ── Helper: Map a shop to a BusinessProfile ──
    fun shopToBusiness(shop: ShopResponse): BusinessProfile = BusinessProfile(
        id = shop.id.toString(),
        name = shop.shopName,
        ownerName = shop.ownerName,
        category = shop.category,
        scale = shop.scale,
        state = shop.state,
        city = shop.city,
        branchName = shop.branchName ?: ""
    )

    // ── Helper: Open a document using secure streaming flow ──
    // Navigates to DocumentViewerScreen which uses one-time view tokens
    // to securely stream documents from S3 without exposing URLs.
    fun openDocument(doc: DocumentItem) {
        val docId = doc.id.toLongOrNull()
        if (docId != null) {
            // Navigate to secure document viewer
            viewDocumentId = docId
            viewDocumentName = doc.name
        } else {
            // Fallback to certificate dialog if document ID is invalid
            docForView = doc
        }
    }
    
    fun closeDocumentViewer() {
        viewDocumentId = null
        viewDocumentName = ""
    }

    fun findBusinessFor(doc: DocumentItem): BusinessProfile? =
        shops.find { it.id.toString() == doc.businessId }?.let { shopToBusiness(it) }

    // ── Determine initial screen ──
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            // Navigate to appropriate home screen based on role
            currentScreen = if (currentUserRole == "MANAGER") "manager_home" else "owner_home"
            loadShops()
            if (currentUserRole == "ADMIN") {
                loadManagers()
            }
        }
    }

    val onToggleTheme: () -> Unit = {
        isDarkTheme = !isDarkTheme
        LockerStorage.saveTheme(context, isDarkTheme)
        onThemeChange(isDarkTheme)
    }

    // Status bar background color
    val statusBarBg = when {
        currentScreen == "onboarding" -> Color(0xFF2563EB)
        isDarkTheme -> DarkBg
        else -> Color(0xFFF8FAFC)
    }

    // Update system status bar and nav bar icon colors to match app theme
    val activity = context as? Activity
    SideEffect {
        activity?.let { act ->
            val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
            controller.isAppearanceLightStatusBars = !isDarkTheme
            controller.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    CompositionLocalProvider(LocalAppLanguage provides language) {
        DukaanLockerTheme(darkTheme = isDarkTheme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = statusBarBg
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Status bar background
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .background(statusBarBg)
                    )

                    // Main content area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                    when (currentScreen) {
                        "splash" -> {
                            SplashScreen(
                                isDarkTheme = isDarkTheme,
                                onSplashDone = { currentScreen = "onboarding" }
                            )
                        }

                        "onboarding" -> {
                            MainScreen(
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                onGetStarted = {
                                    currentScreen = "login"
                                },
                                onLanguageChanged = { code ->
                                    language = code
                                    LockerStorage.saveLanguage(context, code)
                                    onLanguageChanged(code)
                                }
                            )
                        }

                        "login" -> {
                            LoginScreen(
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                onBackToMain = {
                                    currentScreen = "onboarding"
                                },
                                onOwnerLogin = { email, password, onDone ->
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            val response = api.login(LoginRequest(emailId = email, password = password))
                                            if (response.isSuccessful) {
                                                val auth = response.body()!!
                                                ApiClient.saveAuth(context, auth)
                                                authToken = auth.token
                                                currentUserId = auth.userId
                                                currentUserName = auth.userName
                                                currentUserEmail = auth.emailId
                                                currentUserRole = auth.role
                                                isLoggedIn = true
                                                currentScreen = "owner_home"
                                                loadShops()
                                                if (auth.role == "ADMIN") loadManagers()
                                                Toast.makeText(context, "Welcome, ${auth.userName}!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Login failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                        isLoading = false
                                        onDone()
                                    }
                                },
                                onRegister = { name, email, password, mobile, onDone ->
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            val response = api.register(RegisterRequest(
                                                userName = name,
                                                mobileNumber = mobile,
                                                emailId = email,
                                                password = password
                                            ))
                                            if (response.isSuccessful) {
                                                val auth = response.body()!!
                                                ApiClient.saveAuth(context, auth)
                                                authToken = auth.token
                                                currentUserId = auth.userId
                                                currentUserName = auth.userName
                                                currentUserEmail = auth.emailId
                                                currentUserRole = auth.role
                                                isLoggedIn = true
                                                currentScreen = "wizard"
                                                Toast.makeText(context, "Account created! Welcome, ${auth.userName}!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Registration failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                        isLoading = false
                                        onDone()
                                    }
                                },
                                onInitMsmeCaptcha = { onResult ->
                                    scope.launch {
                                        try {
                                            val response = api.initUdyamSession()
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                if (response.isSuccessful) {
                                                    val body = response.body()!!
                                                    onResult(body.sessionId, body.captchaBase64)
                                                } else {
                                                    Toast.makeText(context, "Failed to load captcha: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                                                    onResult("", "")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                Toast.makeText(context, "Network error loading captcha: ${e.message}", Toast.LENGTH_LONG).show()
                                                onResult("", "")
                                            }
                                        }
                                    }
                                },
                                onRegisterWithMsme = { msmeNumber, mobile, password, sessionId, captchaText, onDone ->
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            val response = api.registerWithMsme(
                                                RegisterWithMsmeRequest(
                                                    msmeNumber = msmeNumber,
                                                    mobileNumber = mobile,
                                                    password = password,
                                                    sessionId = sessionId,
                                                    captchaText = captchaText
                                                )
                                            )
                                            if (response.isSuccessful) {
                                                val auth = response.body()!!
                                                // Save auth using the base AuthResponse fields
                                                val authResponse = AuthResponse(
                                                    token = auth.token,
                                                    tokenType = auth.tokenType,
                                                    userId = auth.userId,
                                                    userName = auth.userName,
                                                    mobileNumber = auth.mobileNumber,
                                                    emailId = auth.emailId,
                                                    role = auth.role
                                                )
                                                ApiClient.saveAuth(context, authResponse)
                                                authToken = auth.token
                                                currentUserId = auth.userId
                                                currentUserName = auth.userName
                                                currentUserEmail = auth.emailId
                                                currentUserRole = auth.role
                                                isLoggedIn = true
                                                // After MSME registration, shop is already created on backend
                                                // Skip wizard and go directly to owner_home
                                                currentScreen = "owner_home"
                                                // Load shops and documents
                                                loadShops()
                                                if (auth.role == "ADMIN") loadManagers()
                                                val shopInfo = if (!auth.shopName.isNullOrBlank()) {
                                                    "\nShop: ${auth.shopName}"
                                                } else ""
                                                val emailInfo = if (!auth.emailId.isNullOrBlank()) {
                                                    "\nLogin: ${auth.emailId}"
                                                } else ""
                                                val certMsg = if (!auth.certificatePdfUrl.isNullOrBlank()) {
                                                    "\nCertificate: ${auth.certificatePdfUrl}"
                                                } else ""
                                                Toast.makeText(context, "MSME verified! Welcome, ${auth.userName}!$shopInfo$emailInfo$certMsg", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "MSME registration failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                        isLoading = false
                                        onDone()
                                    }
                                },
                                onManagerLogin = { code ->
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            val response = api.loginByCode(ManagerCodeLoginRequest(managerCode = code))
                                            if (response.isSuccessful) {
                                                val auth = response.body()!!
                                                ApiClient.saveAuth(context, auth)
                                                authToken = auth.token
                                                currentUserId = auth.userId
                                                currentUserName = auth.userName
                                                currentUserEmail = auth.emailId
                                                currentUserRole = auth.role
                                                currentUserManagerCode = auth.managerCode ?: code
                                                isLoggedIn = true
                                                currentScreen = "manager_home"
                                                loadShops()
                                                Toast.makeText(context, "Welcome, ${auth.userName}!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Login failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                        isLoading = false
                                    }
                                }
                            )
                        }

                        "wizard" -> {
                            WizardScreen(
                                onComplete = { wizard ->
                                    scope.launch {
                                        try {
                                            api.createOrUpdateProfile(BusinessProfileRequest(
                                                businessCount = wizard.businessCount,
                                                crossCategory = wizard.crossCategory,
                                                multipleBranches = wizard.multipleBranches,
                                                operationScope = wizard.operationScope,
                                                businessPresence = wizard.digitalReadiness
                                            ))
                                        } catch (e: Exception) {
                                            // Log error for debugging
                                            android.util.Log.e("DukaanLocker", "Failed to save wizard profile", e)
                                        }
                                        currentScreen = "add_business"
                                    }
                                },
                                onSkip = {
                                    scope.launch {
                                        try {
                                            api.createOrUpdateProfile(BusinessProfileRequest(
                                                businessCount = "ONE",
                                                operationScope = "CITY",
                                                businessPresence = "PHYSICAL"
                                            ))
                                        } catch (e: Exception) {
                                            // Log error for debugging
                                            android.util.Log.e("DukaanLocker", "Failed to save wizard profile (skip)", e)
                                        }
                                        currentScreen = "add_business"
                                    }
                                },
                                onBackToLogin = {
                                    ApiClient.clearAuth(context)
                                    isLoggedIn = false
                                    currentScreen = "login"
                                }
                            )
                        }

                        "add_business" -> {
                            // Find currently assigned manager for this shop
                            val currentShopId = editShopTarget?.id?.toString()
                            val currentManagerId = if (currentShopId != null) {
                                managerShopAssignments.entries.find { currentShopId in it.value }?.key?.toString()
                            } else null
                            var pendingManagerId by remember { mutableStateOf<String?>(currentManagerId) }

                            AddBusinessScreen(
                                initial = editShopTarget?.let { shop ->
                                    BusinessProfile(
                                        id = shop.id.toString(),
                                        name = shop.shopName,
                                        ownerName = shop.ownerName,
                                        category = shop.category,
                                        scale = shop.scale,
                                        state = shop.state,
                                        city = shop.city,
                                        branchName = shop.branchName ?: ""
                                    )
                                },
                                managers = managers.map { mgr ->
                                    ManagerAccess(
                                        id = mgr.id.toString(),
                                        code = mgr.managerCode ?: mgr.id.toString(),
                                        managerName = mgr.userName,
                                        assignedBusinessIds = managerShopAssignments[mgr.id] ?: emptyList()
                                    )
                                },
                                assignedManagerId = currentManagerId,
                                onManagerSelected = { managerId ->
                                    pendingManagerId = managerId
                                },
                                onSave = { biz ->
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            var shopId: Long? = null
                                            if (editShopTarget != null) {
                                                api.updateShop(editShopTarget!!.id, UpdateShopRequest(
                                                    shopName = biz.name,
                                                    ownerName = biz.ownerName,
                                                    category = biz.category,
                                                    scale = biz.scale,
                                                    state = biz.state,
                                                    city = biz.city,
                                                    branchName = biz.branchName.ifBlank { null }
                                                ))
                                                shopId = editShopTarget!!.id
                                                Toast.makeText(context, "${biz.name} updated!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val response = api.createShop(CreateShopRequest(
                                                    shopName = biz.name,
                                                    ownerName = biz.ownerName,
                                                    mobile = ApiClient.getUserMobile(context).ifBlank { "0000000000" },
                                                    category = biz.category,
                                                    scale = biz.scale,
                                                    state = biz.state,
                                                    city = biz.city,
                                                    branchName = biz.branchName.ifBlank { null }
                                                ))
                                                if (response.isSuccessful) {
                                                    shopId = response.body()?.id
                                                }
                                                Toast.makeText(context, "${biz.name} created!", Toast.LENGTH_SHORT).show()
                                            }
                                            // Assign to manager if selected and shop was created
                                            if (shopId != null && pendingManagerId != null) {
                                                try {
                                                    api.assignShopToManager(pendingManagerId!!.toLong(), shopId!!)
                                                    loadManagers()
                                                } catch (e: Exception) {
                                                    // Handle error
                                                }
                                            }
                                            loadShops()
                                            editShopTarget = null
                                            currentScreen = "owner_home"
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                        isLoading = false
                                    }
                                },
                                onCancel = {
                                    editShopTarget = null
                                    currentScreen = "owner_home"
                                }
                            )
                        }

                        "owner_home" -> {
                            OwnerHomeScreen(
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                showAddBusiness = currentUserRole == "ADMIN",
                                showManageManagers = currentUserRole == "ADMIN",
                                user = UserAccount(
                                    mobile = ApiClient.getUserMobile(context),
                                    name = currentUserName,
                                    email = currentUserEmail,
                                    role = currentUserRole
                                ),
                                businesses = shops.map { shop ->
                                    BusinessProfile(
                                        id = shop.id.toString(),
                                        name = shop.shopName,
                                        ownerName = shop.ownerName,
                                        category = shop.category,
                                        scale = shop.scale,
                                        state = shop.state,
                                        city = shop.city,
                                        branchName = shop.branchName ?: ""
                                    )
                                },
                                onBusinessSelected = { bizId ->
                                    scope.launch {
                                        loadDocuments(bizId.toLongOrNull() ?: return@launch)
                                    }
                                },
                                documents = shopDocuments.map { doc ->
                                    DocumentItem(
                                        id = doc.id.toString(),
                                        businessId = doc.shopId.toString(),
                                        type = doc.documentType,
                                        name = when (doc.documentType) {
                                            "MSME_CERTIFICATE" -> "MSME Certificate"
                                            "GST" -> "GST Registration"
                                            "PAN" -> "PAN Card"
                                            "FSSAI_FOOD_LICENSE" -> "FSSAI Food License"
                                            "TRADE_LICENSE" -> "Trade License"
                                            "SHOP_ESTABLISHMENT" -> "Shop & Establishment"
                                            "PROFESSIONAL_TAX" -> "Professional Tax"
                                            "TRADEMARK" -> "Trademark"
                                            "PROPERTY_TAX" -> "Property Tax"
                                            "IEC" -> "Import Export Code"
                                            "POLLUTION_CONTROL" -> "Pollution Control"
                                            "FIRE_SAFETY" -> "Fire Safety"
                                            "LABOUR_LICENSE" -> "Labour License"
                                            "SHOP_INSURANCE" -> "Shop Insurance"
                                            "DRUG_LICENSE" -> "Drug License"
                                            else -> doc.documentType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                                        },
                                        status = when (doc.status) {
                                            "UPLOADED", "VALID" -> "UPLOADED"
                                            "NOT_UPLOADED" -> "MISSING"
                                            else -> doc.status
                                        },
                                        regNumber = doc.documentNumber ?: "",
                                        expiryDate = doc.expiryDate ?: "",
                                        issueDate = doc.issueDate ?: "",
                                        fileUrl = doc.fileUrl
                                    )
                                },
                                managers = managers.map { mgr ->
                                    ManagerAccess(
                                        id = mgr.id.toString(),
                                        code = mgr.managerCode ?: mgr.id.toString(),
                                        managerName = mgr.userName,
                                        assignedBusinessIds = managerShopAssignments[mgr.id] ?: emptyList()
                                    )
                                },
                                onAddBusiness = {
                                    editShopTarget = null
                                    currentScreen = "add_business"
                                },
                                onEditBusiness = { biz ->
                                    val shop = shops.find { it.id.toString() == biz.id }
                                    editShopTarget = shop
                                    currentScreen = "add_business"
                                },
                                onManageManagers = {
                                    currentScreen = "manage_managers"
                                },
                                onFetchDoc = { doc ->
                                    fetchTargetDoc = doc
                                    showFetchDialog = true
                                },
                                onUploadDoc = { doc ->
                                    pendingUploadDoc = doc
                                    uploadLauncher.launch("*/*")
                                },
                                onViewDoc = { doc ->
                                    openDocument(doc)
                                },
                                onDeleteDoc = { doc ->
                                    Toast.makeText(context, "${doc.name} - delete via API", Toast.LENGTH_SHORT).show()
                                },
                                onLogout = {
                                    ApiClient.clearAuth(context)
                                    isLoggedIn = false
                                    authToken = null
                                    currentScreen = "login"
                                    Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        "manage_managers" -> {
                            ManageManagersScreen(
                                managers = managers.map { mgr ->
                                    ManagerAccess(
                                        code = mgr.managerCode ?: mgr.id.toString(),
                                        managerName = mgr.userName,
                                        id = mgr.id.toString(),
                                        assignedBusinessIds = managerShopAssignments[mgr.id] ?: emptyList()
                                    )
                                },
                                businesses = shops.map { shop ->
                                    BusinessProfile(
                                        id = shop.id.toString(),
                                        name = shop.shopName,
                                        ownerName = shop.ownerName,
                                        category = shop.category,
                                        scale = shop.scale,
                                        state = shop.state,
                                        city = shop.city,
                                        branchName = shop.branchName ?: ""
                                    )
                                },
                                managerShopAssignments = managerShopAssignments.mapKeys { it.key.toString() }.mapValues { it.value },
                                onAddManager = { name, bizList ->
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            // Create manager - backend auto-generates unique code
                                            // Generate unique mobile and email to avoid conflicts
                                            val timestamp = System.currentTimeMillis() % 10000000L
                                            val uniqueMobile = "9${timestamp.toString().padStart(9, '0')}"
                                            val uniqueSuffix = (1000..9999).random()
                                            val emailPrefix = name.lowercase().replace(" ", ".")
                                            val uniqueEmail = "${emailPrefix}${uniqueSuffix}@dukaanlocker.com"
                                            val response = api.createManager(CreateManagerRequest(
                                                userName = name,
                                                mobileNumber = uniqueMobile,
                                                emailId = uniqueEmail
                                            ))
                                            if (response.isSuccessful) {
                                                val newMgr = response.body()!!
                                                // Assign shops
                                                for (bizId in bizList) {
                                                    val shopId = bizId.toLongOrNull()
                                                    if (shopId != null) {
                                                        api.assignShopToManager(newMgr.id, shopId)
                                                    }
                                                }
                                                loadManagers()
                                                Toast.makeText(context, "Manager '$name' created!\nCode: ${newMgr.managerCode}", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                        isLoading = false
                                    }
                                },
                                onDeleteManager = { code ->
                                    scope.launch {
                                        val managerId = code.toLongOrNull()
                                        if (managerId != null) {
                                            // Deactivate all shop assignments
                                            for (shop in shops) {
                                                try {
                                                    api.deactivateAssignment(managerId, shop.id)
                                                } catch (e: Exception) {
                                                    // Log error for debugging
                                                    android.util.Log.e("DukaanLocker", "Failed to deactivate assignment for manager $managerId", e)
                                                }
                                            }
                                            loadManagers()
                                            Toast.makeText(context, "Manager access revoked", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onBack = { currentScreen = "owner_home" }
                            )
                        }

                        "manager_home" -> {
                            ManagerHomeScreen(
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                user = UserAccount(
                                    mobile = ApiClient.getUserMobile(context),
                                    name = currentUserName,
                                    email = currentUserEmail,
                                    role = currentUserRole,
                                    managerCode = currentUserManagerCode
                                ),
                                managerAccess = ManagerAccess(
                                    id = currentUserId.toString(),
                                    code = currentUserManagerCode,
                                    managerName = currentUserName,
                                    assignedBusinessIds = shops.map { it.id.toString() }
                                ),
                                businesses = shops.map { shop ->
                                    BusinessProfile(
                                        id = shop.id.toString(),
                                        name = shop.shopName,
                                        ownerName = shop.ownerName,
                                        category = shop.category,
                                        scale = shop.scale,
                                        state = shop.state,
                                        city = shop.city,
                                        branchName = shop.branchName ?: ""
                                    )
                                },
                                documents = shopDocuments.map { doc ->
                                    DocumentItem(
                                        id = doc.id.toString(),
                                        businessId = doc.shopId.toString(),
                                        type = doc.documentType,
                                        name = when (doc.documentType) {
                                            "MSME_CERTIFICATE" -> "MSME Certificate"
                                            "GST" -> "GST Registration"
                                            "PAN" -> "PAN Card"
                                            "FSSAI_FOOD_LICENSE" -> "FSSAI Food License"
                                            "TRADE_LICENSE" -> "Trade License"
                                            "SHOP_ESTABLISHMENT" -> "Shop & Establishment"
                                            "PROFESSIONAL_TAX" -> "Professional Tax"
                                            "TRADEMARK" -> "Trademark"
                                            "PROPERTY_TAX" -> "Property Tax"
                                            "IEC" -> "Import Export Code"
                                            "POLLUTION_CONTROL" -> "Pollution Control"
                                            "FIRE_SAFETY" -> "Fire Safety"
                                            "LABOUR_LICENSE" -> "Labour License"
                                            "SHOP_INSURANCE" -> "Shop Insurance"
                                            "DRUG_LICENSE" -> "Drug License"
                                            else -> doc.documentType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                                        },
                                        status = when (doc.status) {
                                            "UPLOADED", "VALID" -> "UPLOADED"
                                            "NOT_UPLOADED" -> "MISSING"
                                            else -> doc.status
                                        },
                                        regNumber = doc.documentNumber ?: "",
                                        expiryDate = doc.expiryDate ?: "",
                                        issueDate = doc.issueDate ?: "",
                                        fileUrl = doc.fileUrl
                                    )
                                },
                                onFetchDoc = { doc ->
                                    fetchTargetDoc = doc
                                    showFetchDialog = true
                                },
                                onUploadDoc = { doc ->
                                    pendingUploadDoc = doc
                                    uploadLauncher.launch("*/*")
                                },
                                onViewDoc = { doc ->
                                    openDocument(doc)
                                },
                                onDeleteDoc = { doc -> },
                                onLogout = {
                                    currentUserManagerCode = ""
                                    ApiClient.clearAuth(context)
                                    isLoggedIn = false
                                    currentScreen = "login"
                                }
                            )
                        }
                    }

                    // ── Document Viewer overlay (secure streaming) ──
                    if (viewDocumentId != null) {
                        DocumentViewerScreen(
                            documentId = viewDocumentId!!,
                            documentName = viewDocumentName,
                            onBack = { closeDocumentViewer() }
                        )
                    }
                    
                    // ── Loading overlay ──
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = if (isDarkTheme) Color.White else Color(0xFF2563EB))
                        }
                    }

                    // ── Fetch Document Dialog ──
                    if (showFetchDialog && fetchTargetDoc != null) {
                        val fetchDoc = fetchTargetDoc!!
                        val shop = shops.find { it.id.toString() == fetchDoc.businessId }
                        FetchDocumentDialog(
                            doc = fetchDoc,
                            shopName = shop?.shopName ?: "Business",
                            onDismiss = {
                                showFetchDialog = false
                                fetchTargetDoc = null
                            },
                            onSuccess = { regNum, issueDate, expiryDate ->
                                showFetchDialog = false
                                fetchTargetDoc = null
                            }
                        )
                    }

                    // ── Certificate Viewer Dialog (fallback when no file URL) ──
                    if (docForView != null) {
                        val viewDoc = docForView!!
                        CertificateViewerDialog(
                            doc = viewDoc,
                            business = findBusinessFor(viewDoc)
                                ?: BusinessProfile(name = currentUserName),
                            onDismiss = { docForView = null }
                        )
                    }
                }

                // ── Bottom Navigation Bar ──
                if (isLoggedIn && currentScreen in listOf("owner_home", "manager_home")) {
                    BottomNavBar(
                        currentRoute = selectedBottomTab,
                        onNavigate = { route ->
                            selectedBottomTab = route
                            when (route) {
                                "home" -> { /* already on home */ }
                                "business" -> { /* show business list */ }
                                "docs" -> { /* navigate to docs view */ }
                                "team" -> { currentScreen = "manage_managers" }
                                "settings" -> { /* open settings */ }
                            }
                        },
                        isDarkTheme = isDarkTheme
                    )
                }
            }
            }
        }
    }
}
