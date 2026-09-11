package com.iadv.dukaanlocker

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iadv.dukaanlocker.api.*
import com.iadv.dukaanlocker.ui.navigation.BottomTab
import com.iadv.dukaanlocker.ui.navigation.Screen
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

data class AppUiState(
    val isLoggedIn: Boolean = false,
    val authToken: String? = null,
    val currentUserId: Long = -1,
    val currentUserName: String = "",
    val currentUserEmail: String = "",
    val currentUserRole: String = "",
    val currentUserManagerCode: String = "",
    val shops: List<ShopResponse> = emptyList(),
    val selectedShop: ShopResponse? = null,
    val shopDocuments: List<DocumentResponse> = emptyList(),
    val managers: List<ManagerResponse> = emptyList(),
    val managerShopAssignments: Map<Long, List<String>> = emptyMap(),
    val currentScreen: Screen = Screen.Onboarding,
    val navigationHistory: List<Screen> = emptyList(),
    val editShopTarget: ShopResponse? = null,
    val selectedBottomTab: BottomTab = BottomTab.Home,
    val bottomTabHistory: List<BottomTab> = emptyList(),
    val isDarkTheme: Boolean = true,
    val language: String = "en",
    val isLoading: Boolean = false,
    val isLoadingShops: Boolean = false,
    val isLoadingManagers: Boolean = false,
    val isLoadingDocuments: Boolean = false,
    val isAppUnlocked: Boolean = false,
    val isBiometricLoginEnabled: Boolean = false,
    val viewDocumentId: Long? = null,
    val viewDocumentName: String = "",
    val pendingUploadDoc: DocumentItem? = null,
    val showFetchDialog: Boolean = false,
    val fetchTargetDoc: DocumentItem? = null,
    val docForView: DocumentItem? = null,
    val showAppUnlockFailedDialog: Boolean = false,
    val showBiometricLoginFailedDialog: Boolean = false,
    val showBiometricLoginPrompt: Boolean = false,
    val biometricLoginFailed: Boolean = false,
    val showAppUnlockPrompt: Boolean = false,
    val notifications: List<NotificationItem> = emptyList(),
    val unreadNotificationCount: Long = 0,
    val isLoadingNotifications: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application
    private val api: ApiService = ApiClient.getApiService(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        val isLoggedIn = ApiClient.isLoggedIn(application)
        _uiState.value = AppUiState(
            isLoggedIn = isLoggedIn,
            authToken = ApiClient.getToken(application),
            currentUserId = ApiClient.getUserId(application),
            currentUserName = ApiClient.getUserName(application),
            currentUserEmail = ApiClient.getUserEmail(application),
            currentUserRole = ApiClient.getUserRole(application),
            currentUserManagerCode = ApiClient.getManagerCode(application),
            isDarkTheme = LockerStorage.getTheme(application),
            language = LockerStorage.getLanguage(application),
            isBiometricLoginEnabled = LockerStorage.isBiometricLoginEnabled(application)
        )
    }

    fun updateState(update: (AppUiState) -> AppUiState) {
        _uiState.value = update(_uiState.value)
    }

    fun setScreen(screen: Screen) {
        updateState { it.copy(currentScreen = screen) }
    }

    fun navigateTo(screen: Screen) {
        val current = _uiState.value.currentScreen
        if (current != screen) {
            updateState {
                it.copy(
                    navigationHistory = it.navigationHistory + current,
                    currentScreen = screen
                )
            }
        }
    }

    fun goBack() {
        val history = _uiState.value.navigationHistory
        if (history.isNotEmpty()) {
            val previous = history.last()
            updateState {
                it.copy(
                    navigationHistory = it.navigationHistory.dropLast(1),
                    currentScreen = previous
                )
            }
        }
    }

    fun setBottomTab(tab: BottomTab) {
        val current = _uiState.value.selectedBottomTab
        if (current != tab) {
            updateState {
                it.copy(
                    bottomTabHistory = it.bottomTabHistory + current,
                    selectedBottomTab = tab
                )
            }
        }
    }

    fun goBackTab() {
        val history = _uiState.value.bottomTabHistory
        if (history.isNotEmpty()) {
            val previous = history.last()
            updateState {
                it.copy(
                    bottomTabHistory = it.bottomTabHistory.dropLast(1),
                    selectedBottomTab = previous
                )
            }
        }
    }

    fun setTheme(dark: Boolean) {
        LockerStorage.saveTheme(context, dark)
        updateState { it.copy(isDarkTheme = dark) }
    }

    fun setLanguage(code: String) {
        LockerStorage.saveLanguage(context, code)
        updateState { it.copy(language = code) }
    }

    fun navigateToHome() {
        val role = _uiState.value.currentUserRole
        updateState {
            it.copy(
                currentScreen = if (role == "MANAGER") Screen.ManagerHome else Screen.OwnerHome,
                selectedBottomTab = BottomTab.Home,
                bottomTabHistory = emptyList(),
                navigationHistory = emptyList(),
                shops = emptyList(),
                shopDocuments = emptyList(),
                managers = emptyList(),
                managerShopAssignments = emptyMap(),
                selectedShop = null,
                editShopTarget = null,
                pendingUploadDoc = null,
                viewDocumentId = null,
                viewDocumentName = "",
                docForView = null,
                fetchTargetDoc = null,
                showFetchDialog = false
            )
        }
        loadShops()
        if (role == "ADMIN") loadManagers()
        loadNotifications()
    }

    fun navigateToLogin() {
        updateState {
            it.copy(
                currentScreen = Screen.Login,
                biometricLoginFailed = false,
                showBiometricLoginPrompt = false
            )
        }
    }

    fun logout() {
        ApiClient.clearAuth(context)
        updateState {
            it.copy(
                isLoggedIn = false,
                authToken = null,
                currentUserId = -1,
                currentUserName = "",
                currentUserEmail = "",
                currentUserRole = "",
                currentUserManagerCode = "",
                shops = emptyList(),
                shopDocuments = emptyList(),
                managers = emptyList(),
                managerShopAssignments = emptyMap(),
                selectedShop = null,
                editShopTarget = null,
                pendingUploadDoc = null,
                viewDocumentId = null,
                viewDocumentName = "",
                docForView = null,
                fetchTargetDoc = null,
                showFetchDialog = false,
                currentScreen = Screen.Login,
                navigationHistory = emptyList(),
                bottomTabHistory = emptyList(),
                selectedBottomTab = BottomTab.Home
            )
        }
        Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
    }

    fun logoutManager() {
        ApiClient.clearAuth(context)
        updateState {
            it.copy(
                isLoggedIn = false,
                authToken = null,
                currentUserId = -1,
                currentUserName = "",
                currentUserEmail = "",
                currentUserRole = "",
                currentUserManagerCode = "",
                shops = emptyList(),
                shopDocuments = emptyList(),
                managers = emptyList(),
                managerShopAssignments = emptyMap(),
                selectedShop = null,
                editShopTarget = null,
                pendingUploadDoc = null,
                viewDocumentId = null,
                viewDocumentName = "",
                docForView = null,
                fetchTargetDoc = null,
                showFetchDialog = false,
                currentScreen = Screen.Login,
                navigationHistory = emptyList(),
                bottomTabHistory = emptyList(),
                selectedBottomTab = BottomTab.Home
            )
        }
    }

    fun saveAuth(auth: AuthResponse) {
        ApiClient.saveAuth(context, auth)
        updateState {
            it.copy(
                authToken = auth.token,
                currentUserId = auth.userId,
                currentUserName = auth.userName,
                currentUserEmail = auth.emailId,
                currentUserRole = auth.role,
                currentUserManagerCode = auth.managerCode ?: it.currentUserManagerCode,
                isLoggedIn = true
            )
        }
    }

    fun openDocument(doc: DocumentItem) {
        val docId = doc.id.toLongOrNull()
        if (docId != null) {
            updateState { it.copy(viewDocumentId = docId, viewDocumentName = doc.name) }
        } else {
            updateState { it.copy(docForView = doc) }
        }
    }

    fun closeDocumentViewer() {
        updateState { it.copy(viewDocumentId = null, viewDocumentName = "") }
    }

    fun showFetchDialog(doc: DocumentItem) {
        updateState { it.copy(showFetchDialog = true, fetchTargetDoc = doc) }
    }

    fun dismissFetchDialog() {
        updateState { it.copy(showFetchDialog = false, fetchTargetDoc = null) }
    }

    fun dismissCertDialog() {
        updateState { it.copy(docForView = null) }
    }

    fun findBusinessFor(doc: DocumentItem): BusinessProfile? {
        return _uiState.value.shops.find { it.id.toString() == doc.businessId }?.let { shopToBusiness(it) }
    }

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

    fun toDocumentItems(docs: List<DocumentResponse>): List<DocumentItem> = docs.map { doc ->
        DocumentItem(
            id = doc.id.toString(),
            businessId = doc.shopId.toString(),
            type = doc.documentType,
            name = mapDocumentName(doc.documentType),
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
    }

    private fun mapDocumentName(type: String): String = when (type) {
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
        else -> type.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    fun loadShops() {
        viewModelScope.launch {
            updateState { it.copy(isLoadingShops = true) }
            try {
                val response = if (_uiState.value.currentUserRole == "MANAGER") {
                    api.getMyAssignedShops()
                } else {
                    api.getMyShops()
                }
                if (response.isSuccessful) {
                    val shopList = response.body() ?: emptyList()
                    updateState { it.copy(shops = shopList) }
                    supervisorScope {
                        shopList.forEach { shop ->
                            async { loadDocuments(shop.id) }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to load shops", e)
            }
            updateState { it.copy(isLoadingShops = false) }
        }
    }

    fun loadDocuments(shopId: Long) {
        viewModelScope.launch {
            updateState { it.copy(isLoadingDocuments = true) }
            try {
                val response = api.getShopDocuments(shopId)
                if (response.isSuccessful) {
                    val docs = response.body() ?: emptyList()
                    _uiState.update { state ->
                        state.copy(shopDocuments = state.shopDocuments.filter { d -> d.shopId != shopId } + docs)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to load documents for shop $shopId", e)
            }
            updateState { it.copy(isLoadingDocuments = false) }
        }
    }

    fun loadManagers() {
        viewModelScope.launch {
            updateState { it.copy(isLoadingManagers = true) }
            try {
                val response = api.getManagers()
                if (response.isSuccessful) {
                    val mgrs = response.body() ?: emptyList()
                    updateState { it.copy(managers = mgrs) }
                    val assignments = mutableMapOf<Long, List<String>>()
                    supervisorScope {
                        mgrs.map { mgr ->
                            async {
                                try {
                                    val shopsResponse = api.getManagerShops(mgr.id)
                                    if (shopsResponse.isSuccessful) {
                                        mgr.id to (shopsResponse.body() ?: emptyList()).map { it.id.toString() }
                                    } else {
                                        mgr.id to emptyList<String>()
                                    }
                                } catch (e: Exception) {
                                    mgr.id to emptyList<String>()
                                }
                            }
                        }.forEach { deferred ->
                            val (mgrId, shopIds) = deferred.await()
                            assignments[mgrId] = shopIds
                        }
                    }
                    updateState { it.copy(managerShopAssignments = assignments) }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to load managers", e)
            }
            updateState { it.copy(isLoadingManagers = false) }
        }
    }

    fun login(email: String, password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val response = api.login(LoginRequest(emailId = email, password = password))
                if (response.isSuccessful) {
                    val auth = response.body() ?: return@launch
                    saveAuth(auth)
                    navigateToHome()
                    Toast.makeText(context, "Welcome, ${_uiState.value.currentUserName}!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Login failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            updateState { it.copy(isLoading = false) }
            onDone()
        }
    }

    fun register(name: String, email: String, password: String, mobile: String, onDone: () -> Unit) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val response = api.register(RegisterRequest(userName = name, mobileNumber = mobile, emailId = email, password = password))
                if (response.isSuccessful) {
                    val auth = response.body() ?: return@launch
                    saveAuth(auth)
                    updateState { it.copy(currentScreen = Screen.Wizard) }
                    Toast.makeText(context, "Account created! Welcome, ${_uiState.value.currentUserName}!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Registration failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            updateState { it.copy(isLoading = false) }
            onDone()
        }
    }

    fun registerWithMsme(msmeNumber: String, mobile: String, sessionId: String, captchaText: String, onDone: () -> Unit) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val response = api.registerWithMsme(RegisterWithMsmeRequest(msmeNumber = msmeNumber, mobileNumber = mobile, sessionId = sessionId, captchaText = captchaText))
                if (response.isSuccessful) {
                    val auth = response.body() ?: return@launch
                    val authResponse = AuthResponse(
                        token = auth.token, tokenType = auth.tokenType, userId = auth.userId,
                        userName = auth.userName, mobileNumber = auth.mobileNumber, emailId = auth.emailId,
                        role = auth.role, certificatePdfUrl = auth.certificatePdfUrl,
                        shopId = auth.shopId, shopName = auth.shopName
                    )
                    saveAuth(authResponse)
                    updateState { it.copy(currentScreen = Screen.OwnerHome) }
                    loadShops()
                    if (auth.role == "ADMIN") loadManagers()
                    loadNotifications()
                    Toast.makeText(context, "MSME verified! Welcome, ${auth.userName}!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "MSME registration failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            updateState { it.copy(isLoading = false) }
            onDone()
        }
    }

    fun loginByCode(code: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val response = api.loginByCode(ManagerCodeLoginRequest(managerCode = code))
                if (response.isSuccessful) {
                    val auth = response.body() ?: return@launch
                    ApiClient.saveAuth(context, auth)
                    updateState {
                        it.copy(
                            authToken = auth.token,
                            currentUserId = auth.userId,
                            currentUserName = auth.userName,
                            currentUserEmail = auth.emailId,
                            currentUserRole = auth.role,
                            currentUserManagerCode = auth.managerCode ?: code,
                            isLoggedIn = true,
                            currentScreen = Screen.ManagerHome
                        )
                    }
                    loadShops()
                    loadNotifications()
                    Toast.makeText(context, "Welcome, ${auth.userName}!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Login failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            updateState { it.copy(isLoading = false) }
        }
    }

    fun msmeLoginRequest(msmeNumber: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.msmeLoginRequest(MsmeOtpRequest(msmeNumber = msmeNumber))
                if (response.isSuccessful) {
                    onResult(true, response.body()?.message)
                } else {
                    onResult(false, response.parseErrorMessage())
                }
            } catch (e: Exception) {
                onResult(false, "Network error: ${e.message}")
            }
        }
    }

    fun msmeLoginVerify(msmeNumber: String, otp: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val response = api.msmeLoginVerify(MsmeOtpVerifyRequest(msmeNumber = msmeNumber, otp = otp))
                if (response.isSuccessful) {
                    val auth = response.body() ?: return@launch
                    val authResponse = AuthResponse(
                        token = auth.token, tokenType = auth.tokenType, userId = auth.userId,
                        userName = auth.userName, mobileNumber = auth.mobileNumber, emailId = auth.emailId,
                        role = auth.role, certificatePdfUrl = auth.certificatePdfUrl,
                        shopId = auth.shopId, shopName = auth.shopName
                    )
                    saveAuth(authResponse)
                    updateState { it.copy(currentScreen = Screen.OwnerHome) }
                    loadShops()
                    if (auth.role == "ADMIN") loadManagers()
                    loadNotifications()
                    Toast.makeText(context, "Welcome back, ${auth.userName}!", Toast.LENGTH_LONG).show()
                    onResult(true, null)
                } else {
                    onResult(false, response.parseErrorMessage())
                }
            } catch (e: Exception) {
                onResult(false, "Network error: ${e.message}")
            }
            updateState { it.copy(isLoading = false) }
        }
    }

    fun biometricLogin(credentials: BiometricCredentials, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val response = api.biometricLogin(BiometricLoginRequest(userId = credentials.userId, emailId = credentials.email, token = credentials.token))
                if (response.isSuccessful) {
                    val auth = response.body() ?: return@launch
                    saveAuth(auth)
                    navigateToHome()
                    Toast.makeText(context, "Welcome back, ${_uiState.value.currentUserName}!", Toast.LENGTH_SHORT).show()
                    onResult(true, null)
                } else {
                    onResult(false, response.parseErrorMessage())
                }
            } catch (e: Exception) {
                onResult(false, "Network error: ${e.message}")
            }
            updateState { it.copy(isLoading = false) }
        }
    }

    fun createManager(name: String, bizList: List<String>) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val timestamp = System.currentTimeMillis() % 10000000L
                val uniqueMobile = "9${timestamp.toString().padStart(9, '0')}"
                val uniqueSuffix = (1000..9999).random()
                val emailPrefix = name.lowercase().replace(" ", ".")
                val uniqueEmail = "${emailPrefix}${uniqueSuffix}@dukaanlocker.com"
                val response = api.createManager(CreateManagerRequest(userName = name, mobileNumber = uniqueMobile, emailId = uniqueEmail))
                if (response.isSuccessful) {
                    val newMgr = response.body() ?: return@launch
                    for (bizId in bizList) {
                        val shopId = bizId.toLongOrNull()
                        if (shopId != null) api.assignShopToManager(newMgr.id, shopId)
                    }
                    loadManagers()
                    Toast.makeText(context, "Manager '$name' created!\nCode: ${newMgr.managerCode}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            updateState { it.copy(isLoading = false) }
        }
    }

    fun deleteManager(managerId: String) {
        viewModelScope.launch {
            val id = managerId.toLongOrNull()
            if (id != null) {
                var failedCount = 0
                for (shop in _uiState.value.shops) {
                    try {
                        val resp = api.deactivateAssignment(id, shop.id)
                        if (!resp.isSuccessful) failedCount++
                    } catch (_: Exception) { failedCount++ }
                }
                loadManagers()
                if (failedCount > 0) {
                    Toast.makeText(context, "Manager access revoked ($failedCount shops failed)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Manager access revoked", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun disableManager(managerId: String) {
        viewModelScope.launch {
            val id = managerId.toLongOrNull() ?: return@launch
            try {
                val resp = api.disableManager(id)
                if (resp.isSuccessful) {
                    Toast.makeText(context, "Manager disabled", Toast.LENGTH_SHORT).show()
                    loadManagers()
                } else {
                    Toast.makeText(context, "Failed: ${resp.parseErrorMessage()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun enableManager(managerId: String) {
        viewModelScope.launch {
            val id = managerId.toLongOrNull() ?: return@launch
            try {
                val resp = api.enableManager(id)
                if (resp.isSuccessful) {
                    Toast.makeText(context, "Manager enabled", Toast.LENGTH_SHORT).show()
                    loadManagers()
                } else {
                    Toast.makeText(context, "Failed: ${resp.parseErrorMessage()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createShop(biz: BusinessProfile, pendingManagerId: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val response = api.createShop(CreateShopRequest(
                    shopName = biz.name, ownerName = biz.ownerName,
                    mobile = ApiClient.getUserMobile(context).ifBlank { "0000000000" },
                    category = biz.category, scale = biz.scale, state = biz.state, city = biz.city,
                    branchName = biz.branchName.ifBlank { null }
                ))
                if (response.isSuccessful) {
                    val shopId = response.body()?.id
                    if (shopId != null && pendingManagerId != null) {
                        try {
                            val assignResp = api.assignShopToManager(pendingManagerId.toLong(), shopId)
                            if (!assignResp.isSuccessful) {
                                Toast.makeText(context, "Shop created but manager assignment failed", Toast.LENGTH_SHORT).show()
                            }
                            loadManagers()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Shop created but manager assignment failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Toast.makeText(context, "${biz.name} created!", Toast.LENGTH_SHORT).show()
                }
                loadShops()
                updateState { it.copy(currentScreen = Screen.OwnerHome) }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            updateState { it.copy(isLoading = false) }
            onDone()
        }
    }

    fun updateShop(shopId: Long, biz: BusinessProfile, pendingManagerId: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                api.updateShop(shopId, UpdateShopRequest(
                    shopName = biz.name, ownerName = biz.ownerName, category = biz.category,
                    scale = biz.scale, state = biz.state, city = biz.city,
                    branchName = biz.branchName.ifBlank { null }
                ))
                try {
                    val currentManagerId = _uiState.value.managerShopAssignments.entries.find { shopId.toString() in it.value }?.key
                    if (currentManagerId != null) {
                        api.deactivateAssignment(currentManagerId, shopId)
                    }
                    if (pendingManagerId != null) {
                        api.assignShopToManager(pendingManagerId.toLong(), shopId)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Shop updated but manager assignment failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                loadManagers()
                Toast.makeText(context, "${biz.name} updated!", Toast.LENGTH_SHORT).show()
                loadShops()
                updateState { it.copy(editShopTarget = null, currentScreen = Screen.OwnerHome) }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            updateState { it.copy(isLoading = false) }
            onDone()
        }
    }

    fun editBusiness(biz: BusinessProfile) {
        val shop = _uiState.value.shops.find { it.id.toString() == biz.id }
        updateState { it.copy(editShopTarget = shop) }
        navigateTo(Screen.AddBusiness)
    }

    fun addBusiness() {
        updateState { it.copy(editShopTarget = null) }
        navigateTo(Screen.AddBusiness)
    }

    fun cancelAddBusiness() {
        updateState { it.copy(editShopTarget = null) }
        goBack()
    }

    fun saveWizardProfile(wizard: WizardAnswers) {
        viewModelScope.launch {
            try {
                api.createOrUpdateProfile(BusinessProfileRequest(
                    businessCount = wizard.businessCount, crossCategory = wizard.crossCategory,
                    multipleBranches = wizard.multipleBranches, operationScope = wizard.operationScope,
                    businessPresence = wizard.digitalReadiness
                ))
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to save wizard profile", e)
            }
            updateState { it.copy(currentScreen = Screen.AddBusiness) }
        }
    }

    fun skipWizard() {
        viewModelScope.launch {
            try {
                api.createOrUpdateProfile(BusinessProfileRequest(businessCount = "ONE", operationScope = "CITY", businessPresence = "PHYSICAL"))
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to save wizard profile (skip)", e)
            }
            updateState { it.copy(currentScreen = Screen.AddBusiness) }
        }
    }

    fun skipWizardAndLogout() {
        ApiClient.clearAuth(context)
        updateState {
            it.copy(
                isLoggedIn = false,
                shops = emptyList(),
                shopDocuments = emptyList(),
                managers = emptyList(),
                managerShopAssignments = emptyMap(),
                currentScreen = Screen.Login,
                navigationHistory = emptyList()
            )
        }
    }

    fun enableBiometricLogin(cipher: javax.crypto.Cipher, token: String, userId: Long, userName: String, email: String, role: String): Boolean {
        val success = BiometricCredentialManager.storeCredentials(context, cipher, token, userId, userName, email, role)
        if (success) {
            LockerStorage.saveBiometricLoginEnabled(context, true)
            updateState { it.copy(isBiometricLoginEnabled = true) }
            Toast.makeText(context, "Biometric login enabled!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to enable biometric login", Toast.LENGTH_SHORT).show()
        }
        return success
    }

    fun disableBiometricLogin() {
        BiometricCredentialManager.clearCredentials(context)
        LockerStorage.saveBiometricLoginEnabled(context, false)
        updateState { it.copy(isBiometricLoginEnabled = false) }
        Toast.makeText(context, "Biometric login disabled", Toast.LENGTH_SHORT).show()
    }

    fun uploadDocument(doc: DocumentItem, uri: Uri) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                if (bytes.size > 10 * 1024 * 1024) {
                    Toast.makeText(context, "File too large (max 10MB)", Toast.LENGTH_LONG).show()
                    updateState { it.copy(isLoading = false) }
                    return@launch
                }
                val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val fileName = "${doc.type.lowercase()}.pdf"
                val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
                val shopId = doc.businessId.toLongOrNull() ?: return@launch
                val urlDocType = doc.type.lowercase().replace("_", "-")
                val response = api.uploadDocument(shopId = shopId, documentType = urlDocType, file = filePart, documentNumber = null, issueDate = null, expiryDate = null)
                if (response.isSuccessful) {
                    Toast.makeText(context, "${doc.name} uploaded!", Toast.LENGTH_SHORT).show()
                    loadDocuments(shopId)
                } else {
                    Toast.makeText(context, "Upload failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Upload error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            updateState { it.copy(isLoading = false) }
        }
    }

    fun initUdyamCaptcha(onResult: (String, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.initUdyamSession()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val body = response.body() ?: run {
                            onResult("", "")
                            return@withContext
                        }
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
    }

    fun fetchGst(shopId: String, gstin: String, onResult: (Boolean, GstVerificationResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                android.util.Log.d("GST_FETCH", ">>> REQUEST: shopId=$shopId, gstin=$gstin")
                val response = api.fetchGst(GstFetchRequest(shopId = shopId, gstin = gstin))
                android.util.Log.d("GST_FETCH", "<<< RESPONSE CODE: ${response.code()}")
                android.util.Log.d("GST_FETCH", "<<< RESPONSE BODY: ${response.body()}")
                if (!response.isSuccessful) {
                    android.util.Log.e("GST_FETCH", "<<< ERROR BODY: ${response.errorBody()?.string()}")
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.success) {
                            onResult(true, body)
                        } else {
                            onResult(false, body)
                        }
                    } else {
                        Toast.makeText(context, "GST verification failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                        onResult(false, null)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GST_FETCH", "<<< EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                    onResult(false, null)
                }
            }
        }
    }

    fun fetchMsme(shopId: String, udyamNumber: String, sessionId: String, captchaText: String, onResult: (Boolean, UdyamVerifyResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.fetchUdyam(UdyamFetchRequest(shopId = shopId, udyamNumber = udyamNumber, sessionId = sessionId, captchaText = captchaText))
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.success) {
                            onResult(true, body)
                        } else {
                            onResult(false, body)
                        }
                    } else {
                        Toast.makeText(context, "MSME verification failed: ${response.parseErrorMessage()}", Toast.LENGTH_LONG).show()
                        onResult(false, null)
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                    onResult(false, null)
                }
            }
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            updateState { it.copy(isLoadingNotifications = true) }
            try {
                val response = api.getNotifications()
                if (response.isSuccessful) {
                    val notifications = response.body() ?: emptyList()
                    val unreadCount = notifications.count { !it.isRead }.toLong()
                    updateState { it.copy(notifications = notifications, unreadNotificationCount = unreadCount) }
                }
            } catch (e: Exception) {
                android.util.Log.e("Notifications", "Failed to load: ${e.message}")
            }
            updateState { it.copy(isLoadingNotifications = false) }
        }
    }

    fun markNotificationsRead() {
        viewModelScope.launch {
            try {
                api.markNotificationsAsRead()
                updateState { state ->
                    state.copy(
                        notifications = state.notifications.map { it.copy(isRead = true) },
                        unreadNotificationCount = 0
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("Notifications", "Failed to mark as read: ${e.message}")
            }
        }
    }

    fun registerDeviceToken(token: String) {
        viewModelScope.launch {
            try {
                api.registerDeviceToken(mapOf("token" to token, "platform" to "ANDROID"))
                android.util.Log.d("Notifications", "Device token registered")
            } catch (e: Exception) {
                android.util.Log.e("Notifications", "Failed to register token: ${e.message}")
            }
        }
    }
}
