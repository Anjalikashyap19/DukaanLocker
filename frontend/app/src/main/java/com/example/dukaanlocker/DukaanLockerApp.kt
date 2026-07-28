package com.example.dukaanlocker

import android.app.Activity
import android.widget.Toast
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
import com.example.dukaanlocker.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DukaanLockerApp(onThemeChange: (Boolean) -> Unit = {}) {
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

    // ── Business/Shop state ──
    var shops by remember { mutableStateOf<List<ShopResponse>>(emptyList()) }
    var selectedShop by remember { mutableStateOf<ShopResponse?>(null) }
    var shopDocuments by remember { mutableStateOf<List<DocumentResponse>>(emptyList()) }

    // ── Manager state ──
    var managers by remember { mutableStateOf<List<ManagerResponse>>(emptyList()) }

    // ── Navigation state ──
    var currentScreen by remember { mutableStateOf("") }
    var editShopTarget by remember { mutableStateOf<ShopResponse?>(null) }
    var selectedBottomTab by remember { mutableStateOf("home") }

    // ── Theme state ──
    var isDarkTheme by remember { mutableStateOf(LockerStorage.getTheme(context)) }

    // ── Dialog states ──
    var docForView by remember { mutableStateOf<DocumentResponse?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // ── Helper: Load shops from API ──
    suspend fun loadShops() {
        try {
            val response = if (currentUserRole == "MANAGER") {
                api.getMyAssignedShops()
            } else {
                api.getMyShops()
            }
            if (response.isSuccessful) {
                shops = response.body() ?: emptyList()
            }
        } catch (e: Exception) {
            // Handle offline or error gracefully
        }
    }

    // ── Helper: Load documents for a shop ──
    suspend fun loadDocuments(shopId: Long) {
        try {
            val response = api.getShopDocuments(shopId)
            if (response.isSuccessful) {
                shopDocuments = response.body() ?: emptyList()
            }
        } catch (e: Exception) {
            // Handle offline or error gracefully
        }
    }

    // ── Helper: Load managers ──
    suspend fun loadManagers() {
        try {
            val response = api.getManagers()
            if (response.isSuccessful) {
                managers = response.body() ?: emptyList()
            }
        } catch (e: Exception) {
            // Handle offline or error gracefully
        }
    }

    // ── Determine initial screen ──
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            currentScreen = "login"
        } else {
            currentScreen = "owner_home"
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
    val statusBarBg = if (isDarkTheme) DarkBg else Color(0xFFF8FAFC)

    // Update system status bar and nav bar icon colors to match app theme
    val activity = context as? Activity
    SideEffect {
        activity?.let { act ->
            val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
            controller.isAppearanceLightStatusBars = !isDarkTheme
            controller.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

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
                        "login" -> {
                            LoginScreen(
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                onOwnerLogin = { email, password ->
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
                                                Toast.makeText(context, "Login failed: ${response.message()}", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                        isLoading = false
                                    }
                                },
                                onRegister = { name, email, password, mobile ->
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
                                                Toast.makeText(context, "Registration failed: ${response.message()}", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                        isLoading = false
                                    }
                                },
                                onManagerLogin = { code ->
                                    // Manager login uses email/password, not code
                                    Toast.makeText(context, "Please use email & password to login as manager", Toast.LENGTH_LONG).show()
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
                                        } catch (e: Exception) { /* proceed anyway */ }
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
                                        } catch (e: Exception) { /* proceed anyway */ }
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
                                onSave = { biz ->
                                    scope.launch {
                                        isLoading = true
                                        try {
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
                                                Toast.makeText(context, "${biz.name} updated!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                api.createShop(CreateShopRequest(
                                                    shopName = biz.name,
                                                    ownerName = biz.ownerName,
                                                    mobile = ApiClient.getUserMobile(context).ifBlank { "0000000000" },
                                                    category = biz.category,
                                                    scale = biz.scale,
                                                    state = biz.state,
                                                    city = biz.city,
                                                    branchName = biz.branchName.ifBlank { null }
                                                ))
                                                Toast.makeText(context, "${biz.name} created!", Toast.LENGTH_SHORT).show()
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
                                documents = shopDocuments.map { doc ->
                                    DocumentItem(
                                        id = doc.id.toString(),
                                        businessId = doc.shopId.toString(),
                                        type = doc.documentType,
                                        name = doc.documentType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                        status = when (doc.status) {
                                            "UPLOADED", "VALID" -> "UPLOADED"
                                            "NOT_UPLOADED" -> "MISSING"
                                            else -> doc.status
                                        },
                                        regNumber = doc.documentNumber ?: "",
                                        expiryDate = doc.expiryDate ?: "",
                                        issueDate = doc.issueDate ?: ""
                                    )
                                },
                                managers = managers.map { mgr ->
                                    ManagerAccess(
                                        code = mgr.id.toString(),
                                        managerName = mgr.userName,
                                        assignedBusinessIds = emptyList()
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
                                    // Upload document via API
                                    scope.launch {
                                        Toast.makeText(context, "Upload feature - select a file to upload ${doc.name}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onUploadDoc = { doc ->
                                    Toast.makeText(context, "Select a file to upload ${doc.name}", Toast.LENGTH_SHORT).show()
                                },
                                onViewDoc = { doc ->
                                    // Map DocumentItem to DocumentResponse for viewing
                                    docForView = DocumentResponse(
                                        id = doc.id.toLongOrNull() ?: 0,
                                        shopId = doc.businessId.toLongOrNull() ?: 0,
                                        documentType = doc.type,
                                        fileName = null,
                                        fileUrl = null,
                                        documentNumber = doc.regNumber,
                                        issueDate = doc.issueDate,
                                        expiryDate = doc.expiryDate,
                                        status = doc.status,
                                        version = 1,
                                        uploadedAt = null,
                                        updatedAt = null
                                    )
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
                                        code = mgr.id.toString(),
                                        managerName = mgr.userName,
                                        assignedBusinessIds = emptyList()
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
                                onAddManager = { name, bizList ->
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            // Create manager with temporary password
                                            val response = api.createManager(CreateManagerRequest(
                                                userName = name,
                                                mobileNumber = "0000000000",
                                                emailId = "${name.lowercase().replace(" ", ".")}@dukaanlocker.com",
                                                password = "Manager@123"
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
                                                Toast.makeText(context, "Manager '$name' created! ID: ${newMgr.id}", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Failed: ${response.message()}", Toast.LENGTH_LONG).show()
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
                                                } catch (e: Exception) { }
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
                                    role = currentUserRole
                                ),
                                managerAccess = ManagerAccess(
                                    code = currentUserId.toString(),
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
                                        name = doc.documentType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                        status = when (doc.status) {
                                            "UPLOADED", "VALID" -> "UPLOADED"
                                            "NOT_UPLOADED" -> "MISSING"
                                            else -> doc.status
                                        },
                                        regNumber = doc.documentNumber ?: "",
                                        expiryDate = doc.expiryDate ?: "",
                                        issueDate = doc.issueDate ?: ""
                                    )
                                },
                                onFetchDoc = { doc ->
                                    Toast.makeText(context, "Upload feature - select a file", Toast.LENGTH_SHORT).show()
                                },
                                onUploadDoc = { doc ->
                                    Toast.makeText(context, "Select a file to upload ${doc.name}", Toast.LENGTH_SHORT).show()
                                },
                                onViewDoc = { doc -> },
                                onDeleteDoc = { doc -> },
                                onLogout = {
                                    ApiClient.clearAuth(context)
                                    isLoggedIn = false
                                    currentScreen = "login"
                                }
                            )
                        }
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
