package com.iadv.dukaanlocker

import android.app.Application
import android.net.Uri
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
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Client-side budget for a single document auto-fetch call (UI shows a 60s countdown). */
const val FETCH_TIMEOUT_MS = 60_000L

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
    val binNotifications: List<NotificationItem> = emptyList(),
    val unreadNotificationCount: Long = 0,
    val isLoadingNotifications: Boolean = false,
    val showCustomDocDialog: Boolean = false,
    val customDocShopId: Long? = null,
    val selectedBusinessIdForDocs: String? = null,
    val targetMissingDocumentType: String? = null,
    val renewalSuccessOrder: RenewalOrderItem? = null,
    val toastQueue: List<ToastMessage> = emptyList()
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application
    private val api: ApiService = ApiClient.getApiService(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        val isLoggedIn = ApiClient.isLoggedIn(application)
        val role = ApiClient.getUserRole(application)
        val homeScreen = when {
            !isLoggedIn -> Screen.Onboarding
            role == "MANAGER" -> Screen.ManagerHome
            else -> Screen.OwnerHome
        }
        // Biometric login is disabled by default and stays disabled until the user
        // explicitly enables it. A pref without valid stored credentials is stale
        // (credentials were wiped on key invalidation/decrypt failure) -> heal to OFF.
        LockerStorage.ensureBiometricLoginDefaultDisabled(application)
        val biometricPrefEnabled = LockerStorage.isBiometricLoginEnabled(application)
        val biometricLoginEnabled = biometricPrefEnabled &&
            BiometricCredentialManager.hasStoredCredentials(application)
        if (biometricPrefEnabled && !biometricLoginEnabled) {
            LockerStorage.clearBiometricLoginPreference(application)
        }
        _uiState.value = AppUiState(
            isLoggedIn = isLoggedIn,
            currentScreen = homeScreen,
            authToken = ApiClient.getToken(application),
            currentUserId = ApiClient.getUserId(application),
            currentUserName = ApiClient.getUserName(application),
            currentUserEmail = ApiClient.getUserEmail(application),
            currentUserRole = role,
            currentUserManagerCode = ApiClient.getManagerCode(application),
            isDarkTheme = LockerStorage.getTheme(application),
            language = LockerStorage.getLanguage(application),
            isBiometricLoginEnabled = biometricLoginEnabled
        )
        if (isLoggedIn) {
            loadShops()
            if (role == "ADMIN") loadManagers()
            loadNotifications()
        }
    }

    fun updateState(update: (AppUiState) -> AppUiState) {
        _uiState.value = update(_uiState.value)
    }

    fun showToast(message: String, type: ToastType = ToastType.INFO, duration: Long = 3000) {
        val toast = ToastMessage(message = message, type = type, duration = duration)
        updateState { it.copy(toastQueue = it.toastQueue + toast) }
        // Auto-dismiss after duration
        viewModelScope.launch {
            kotlinx.coroutines.delay(duration)
            updateState { state ->
                state.copy(toastQueue = state.toastQueue.filterNot { it.id == toast.id })
            }
        }
    }

    fun dismissToast(toastId: Long) {
        updateState { it.copy(toastQueue = it.toastQueue.filterNot { it.id == toastId }) }
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

    fun setSelectedBusinessForDocs(businessId: String?) {
        updateState { it.copy(selectedBusinessIdForDocs = businessId) }
    }

    fun setTargetMissingDocument(documentType: String) {
        updateState { it.copy(targetMissingDocumentType = documentType) }
    }

    fun clearTargetMissingDocument() {
        updateState { it.copy(targetMissingDocumentType = null) }
    }

    fun openMissingDocumentNotification(notification: NotificationItem) {
        if (notification.type != "MISSING_DOCUMENT") return
        openMissingDocumentRoute(notification.referenceId, notification.metadata)
    }

    private fun openMissingDocumentRoute(shopId: Long?, documentType: String?) {
        // Return to the screen we came from (Owner/Manager home) so the user lands on Docs
        if (_uiState.value.navigationHistory.isNotEmpty()) goBack() else navigateToHome()
        setBottomTab(BottomTab.Docs)
        if (shopId != null) {
            setSelectedBusinessForDocs(shopId.toString())
            loadDocuments(shopId)
            if (_uiState.value.shops.isEmpty()) loadShops()
        }
        documentType?.let { setTargetMissingDocument(it) }
    }

    /**
     * Routes the app based on a tapped notification (extras captured by
     * MainActivity before composition). Safe to call repeatedly — the pending
     * route is consumed only when the user is logged in and unlocked.
     */
    fun consumePendingNotificationRoute() {
        if (!uiState.value.isLoggedIn) return
        val pending = MainActivity.pendingNotificationRoute ?: return
        MainActivity.clearPendingNotificationRoute()
        when (pending.type) {
            "MISSING_DOCUMENT" -> openMissingDocumentRoute(pending.shopId, pending.documentType)
            "EXPIRING_SOON", "EXPIRED", "RENEWAL_REQUESTED", "RENEWAL_COMPLETED" ->
                if (uiState.value.currentScreen != Screen.Notifications) navigateTo(Screen.Notifications)
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
        // Capture JWT before clearAuth; unregister runs async with this snapshot.
        val jwtSnapshot = ApiClient.getToken(context)
        viewModelScope.launch { ApiClient.unregisterDeviceToken(context, jwtSnapshot) }
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
        showToast("Logged out", ToastType.INFO)
    }

    fun logoutManager() {
        // Capture JWT before clearAuth; unregister runs async with this snapshot.
        val jwtSnapshot = ApiClient.getToken(context)
        viewModelScope.launch { ApiClient.unregisterDeviceToken(context, jwtSnapshot) }
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

    fun saveAuth(auth: AuthResponse, sendWelcomePush: Boolean = false) {
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
        registerCurrentFcmToken(sendWelcomePush)
    }

    /**
     * Registers the current FCM token only after auth has been persisted. Token
     * generation can complete before login, so registration from Activity startup
     * alone is not sufficient.
     */
    private fun registerCurrentFcmToken(sendWelcomePush: Boolean) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    android.util.Log.e("FCM", "Unable to obtain FCM token", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                if (token.isNullOrBlank()) {
                    android.util.Log.e("FCM", "Firebase returned an empty FCM token")
                    return@addOnCompleteListener
                }
                viewModelScope.launch {
                    ApiClient.registerDeviceToken(context, token, sendWelcomePush = sendWelcomePush)
                }
}
    }

    fun moveToBin() {
        viewModelScope.launch {
            try {
                val response = api.moveToBin()
                if (response.isSuccessful) {
                    updateState { it.copy(notifications = emptyList(), unreadNotificationCount = 0) }
                    loadNotifications()
                    showToast("All notifications moved to bin", ToastType.SUCCESS)
                } else {
                    showToast("Failed to move notifications to bin: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                android.util.Log.e("Notifications", "Failed to move to bin: ${e.message}")
                showToast("Network error: ${e.message}", ToastType.ERROR)
            }
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
                    saveAuth(auth, sendWelcomePush = true)
                    navigateToHome()
                    showToast("Welcome, ${_uiState.value.currentUserName}!", ToastType.SUCCESS)
                } else {
                    showToast("Login failed: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                showToast("Network error: ${e.message}", ToastType.ERROR)
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
                    saveAuth(auth, sendWelcomePush = false)
                    updateState { it.copy(currentScreen = Screen.Wizard) }
                    showToast("Account created! Welcome, ${_uiState.value.currentUserName}!", ToastType.SUCCESS)
                } else {
                    showToast("Registration failed: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                showToast("Network error: ${e.message}", ToastType.ERROR)
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
                    saveAuth(authResponse, sendWelcomePush = false)
                    updateState { it.copy(currentScreen = Screen.OwnerHome) }
                    loadShops()
                    if (auth.role == "ADMIN") loadManagers()
                    loadNotifications()
                    showToast("MSME verified! Welcome, ${auth.userName}!", ToastType.SUCCESS)
                } else {
                    showToast("MSME registration failed: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                showToast("Network error: ${e.message}", ToastType.ERROR)
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
                    saveAuth(auth, sendWelcomePush = true)
                    updateState { it.copy(currentScreen = Screen.ManagerHome) }
                    loadShops()
                    loadNotifications()
                    showToast("Welcome, ${auth.userName}!", ToastType.SUCCESS)
                } else {
                    showToast("Login failed: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                showToast("Network error: ${e.message}", ToastType.ERROR)
            }
            updateState { it.copy(isLoading = false) }
        }
    }

    /**
     * Requests an OTP for the given Udyam number.
     *
     * [onResult] receives the outcome plus the server's rate-limit state, so the caller
     * can start a countdown. On a 429 the state describes the retry wait; on success it
     * carries the post-send cooldown and how many sends remain before the lockout.
     */
    fun msmeLoginRequest(
        msmeNumber: String,
        onResult: (Boolean, String?, RateLimitInfo?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = api.msmeLoginRequest(MsmeOtpRequest(msmeNumber = msmeNumber))
                if (response.isSuccessful) {
                    val body = response.body()
                    onResult(
                        true,
                        body?.message,
                        RateLimitInfo(
                            retryAfterSeconds = body?.resendAvailableInSeconds ?: 0,
                            remainingSends = body?.remainingSends ?: 0
                        )
                    )
                } else {
                    val parsed = response.parseOtpError()
                    onResult(false, parsed.message, parsed.limit)
                }
            } catch (e: Exception) {
                onResult(false, "Network error: ${e.message}", null)
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
                    saveAuth(authResponse, sendWelcomePush = true)
                    updateState { it.copy(currentScreen = Screen.OwnerHome) }
                    loadShops()
                    if (auth.role == "ADMIN") loadManagers()
                    loadNotifications()
                    showToast("Welcome back, ${auth.userName}!", ToastType.SUCCESS)
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

    /**
     * Requests a password-reset OTP for a registered owner mobile number.
     *
     * The server answers identically whether or not the number holds an eligible
     * account, so [onResult] success means "the request was accepted", not "an OTP
     * was sent to a real account" — the UI must never claim otherwise.
     */
    fun forgotPasswordRequest(
        mobileNumber: String,
        onResult: (Boolean, String?, RateLimitInfo?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = api.forgotPassword(ForgotPasswordRequest(mobileNumber = mobileNumber))
                if (response.isSuccessful) {
                    val body = response.body()
                    onResult(
                        true,
                        body?.message,
                        RateLimitInfo(
                            retryAfterSeconds = body?.resendAvailableInSeconds ?: 0,
                            remainingSends = body?.remainingSends ?: 0
                        )
                    )
                } else {
                    val parsed = response.parseOtpError()
                    onResult(false, parsed.message, parsed.limit)
                }
            } catch (e: Exception) {
                onResult(false, "Network error: ${e.message}", null)
            }
        }
    }

    /** Verifies the reset OTP and stores the new password. Success means sign in. */
    fun resetPassword(mobileNumber: String, otp: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val response = api.resetPassword(
                    ResetPasswordRequest(mobileNumber = mobileNumber, otp = otp, password = password)
                )
                if (response.isSuccessful) {
                    val msg = response.body()?.message
                    showToast(msg ?: "Password updated. Sign in with your new password.", ToastType.SUCCESS)
                    onResult(true, msg)
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
                    saveAuth(auth, sendWelcomePush = true)
                    navigateToHome()
                    showToast("Welcome back, ${_uiState.value.currentUserName}!", ToastType.SUCCESS)
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
                    showToast("Manager '$name' created!\nCode: ${newMgr.managerCode}", ToastType.SUCCESS)
                } else {
                    showToast("Failed: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}", ToastType.ERROR)
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
                    showToast("Manager access revoked ($failedCount shops failed)", ToastType.WARNING)
                } else {
                    showToast("Manager access revoked", ToastType.SUCCESS)
                }
            }
        }
    }

    fun assignBusinessToManager(managerId: String, shopId: String) {
        viewModelScope.launch {
            try {
                val mgrId = managerId.toLongOrNull() ?: return@launch
                val sId = shopId.toLongOrNull() ?: return@launch
                val response = api.assignShopToManager(mgrId, sId)
                if (response.isSuccessful) {
                    showToast("Business assigned to manager", ToastType.SUCCESS)
                    loadManagers()
                } else {
                    showToast("Failed: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}", ToastType.ERROR)
            }
        }
    }

    fun disableManager(managerId: String) {
        viewModelScope.launch {
            val id = managerId.toLongOrNull() ?: return@launch
            try {
                val resp = api.disableManager(id)
                if (resp.isSuccessful) {
                    showToast("Manager disabled", ToastType.SUCCESS)
                    loadManagers()
                } else {
                    showToast("Failed: ${resp.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}", ToastType.ERROR)
            }
        }
    }

    fun enableManager(managerId: String) {
        viewModelScope.launch {
            val id = managerId.toLongOrNull() ?: return@launch
            try {
                val resp = api.enableManager(id)
                if (resp.isSuccessful) {
                    showToast("Manager enabled", ToastType.SUCCESS)
                    loadManagers()
                } else {
                    showToast("Failed: ${resp.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}", ToastType.ERROR)
            }
        }
    }

    /**
     * Normalized duplicate key mirroring the server rule (ShopService.assertNoDuplicateShop):
     * the shop NAME only — branch is a display label, not an identity. Folds the
     * name the same way the server does (strip accents, punctuation -> space,
     * collapse whitespace, lowercase) so the instant pre-check agrees with the
     * 409 the server would send anyway.
     */
    private fun shopDuplicateKey(name: String?): String {
        if (name.isNullOrBlank()) return ""
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }

    /** Words of an already-folded name; mirrors ShopService.tokens(). */
    private fun shopDuplicateTokens(name: String?): List<String> {
        val folded = shopDuplicateKey(name)
        return if (folded.isEmpty()) emptyList() else folded.split(" ")
    }

    /**
     * Mirrors ShopService.isSameBusinessName(): the two names are the same
     * business when their word sets are equal, or when one set is contained in
     * the other. So "Sehgal" collides with "Sehgal Automobiles" in either
     * direction — which is exactly the hole the exact-match check left open —
     * while "Anjali General Store" and "Anjali Electronics" still do not, since
     * neither word set contains the other. A name that folds to nothing never
     * matches anything.
     */
    private fun isSameBusinessName(left: String?, right: String?): Boolean {
        val leftTokens = shopDuplicateTokens(left)
        val rightTokens = shopDuplicateTokens(right)
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return false
        return leftTokens == rightTokens ||
            leftTokens.containsAll(rightTokens) ||
            rightTokens.containsAll(leftTokens)
    }

    /**
     * Returns the display name of the shop that already claims [name], or null
     * when the name is free. Used both to decide "duplicate" and to tell the
     * user *which* existing business they collided with — a subset match such as
     * "Sehgal" against "Sehgal Automobiles" reads as a mistake otherwise.
     */
    private fun findDuplicateShopName(name: String?, excludeShopId: Long? = null): String? {
        if (shopDuplicateKey(name).isEmpty()) return null
        return _uiState.value.shops
            .firstOrNull { it.id != excludeShopId && isSameBusinessName(name, it.shopName) }
            ?.shopName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun duplicateShopToast(name: String?, excludeShopId: Long? = null): String? {
        val clash = findDuplicateShopName(name, excludeShopId) ?: return null
        return "A business named \"$clash\" already exists"
    }

    fun createShop(biz: BusinessProfile, pendingManagerId: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            // Flip isLoading first: the AddBusiness Save latch is released when
            // isLoading drops, so every exit path below must clear it too.
            updateState { it.copy(isLoading = true) }
            // Instant pre-check against the shops already in memory so the user
            // gets feedback without a server round-trip (server re-checks anyway).
            duplicateShopToast(biz.name)?.let { toast ->
                showToast(toast, ToastType.ERROR)
                updateState { it.copy(isLoading = false) }
                onDone()
                return@launch
            }
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
                                showToast("Shop created but manager assignment failed", ToastType.WARNING)
                            }
                            loadManagers()
                        } catch (e: Exception) {
                            showToast("Shop created but manager assignment failed: ${e.message}", ToastType.WARNING)
                        }
                    }
                    showToast("${biz.name} created!", ToastType.SUCCESS)
                    loadShops()
                    updateState { it.copy(currentScreen = Screen.OwnerHome) }
                } else {
                    // Surface the server error (e.g. "Invalid scale") instead of
                    // bouncing back to Home as if the save had succeeded.
                    showToast(response.parseErrorMessage(), ToastType.ERROR)
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}", ToastType.ERROR)
            }
            updateState { it.copy(isLoading = false) }
            onDone()
        }
    }

    fun updateShop(shopId: Long, biz: BusinessProfile, pendingManagerId: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            // Same ordering as createShop: latch the loader before the pre-check
            // so every return path clears it.
            updateState { it.copy(isLoading = true) }
            duplicateShopToast(biz.name, excludeShopId = shopId)?.let { toast ->
                showToast(toast, ToastType.ERROR)
                updateState { it.copy(isLoading = false) }
                onDone()
                return@launch
            }
            try {
                val response = api.updateShop(shopId, UpdateShopRequest(
                    shopName = biz.name, ownerName = biz.ownerName, category = biz.category,
                    scale = biz.scale, state = biz.state, city = biz.city,
                    branchName = biz.branchName.ifBlank { null }
                ))
                if (!response.isSuccessful) {
                    // e.g. invalid scale — stay on the edit screen and show why
                    showToast(response.parseErrorMessage(), ToastType.ERROR)
                } else {
                    try {
                        val currentManagerId = _uiState.value.managerShopAssignments.entries.find { shopId.toString() in it.value }?.key
                        if (currentManagerId != null) {
                            api.deactivateAssignment(currentManagerId, shopId)
                        }
                        if (pendingManagerId != null) {
                            api.assignShopToManager(pendingManagerId.toLong(), shopId)
                        }
                    } catch (e: Exception) {
                        showToast("Shop updated but manager assignment failed: ${e.message}", ToastType.WARNING)
                    }
                    loadManagers()
                    showToast("${biz.name} updated!", ToastType.SUCCESS)
                    loadShops()
                    updateState { it.copy(editShopTarget = null, currentScreen = Screen.OwnerHome) }
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}", ToastType.ERROR)
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
        val jwtSnapshot = ApiClient.getToken(context)
        viewModelScope.launch { ApiClient.unregisterDeviceToken(context, jwtSnapshot) }
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
            showToast("Biometric login enabled!", ToastType.SUCCESS)
        } else {
            showToast("Failed to enable biometric login", ToastType.ERROR)
        }
        return success
    }

    fun disableBiometricLogin() {
        BiometricCredentialManager.clearCredentials(context)
        LockerStorage.saveBiometricLoginEnabled(context, false)
        updateState { it.copy(isBiometricLoginEnabled = false) }
        showToast("Biometric login disabled", ToastType.INFO)
    }

    fun uploadDocument(doc: DocumentItem, uri: Uri) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                if (bytes.size > 10 * 1024 * 1024) {
                    showToast("File too large (max 10MB)", ToastType.ERROR)
                    updateState { it.copy(isLoading = false) }
                    return@launch
                }
                val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val fileName = "${doc.type.lowercase()}.pdf"
                val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
                val shopId = doc.businessId.toLongOrNull() ?: return@launch
                // DocumentType enum on the backend expects uppercase with underscores (e.g., "SHOP_ESTABLISHMENT")
                val urlDocType = doc.type.uppercase()
                val response = api.uploadDocument(shopId = shopId, documentType = urlDocType, file = filePart, documentNumber = null, issueDate = null, expiryDate = null)
                if (response.isSuccessful) {
                    showToast("${doc.name} uploaded!", ToastType.SUCCESS)
                    loadDocuments(shopId)
                } else {
                    showToast("Upload failed: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                showToast("Upload error: ${e.message}", ToastType.ERROR)
            }
            updateState { it.copy(isLoading = false) }
        }
    }

    fun showCustomDocDialog(shopId: Long) {
        updateState { it.copy(showCustomDocDialog = true, customDocShopId = shopId) }
    }

    fun dismissCustomDocDialog() {
        updateState { it.copy(showCustomDocDialog = false, customDocShopId = null) }
    }

    fun uploadCustomDocument(documentName: String, uri: Uri) {
        val shopId = _uiState.value.customDocShopId ?: return
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                if (bytes.size > 10 * 1024 * 1024) {
                    showToast("File too large (max 10MB)", ToastType.ERROR)
                    updateState { it.copy(isLoading = false) }
                    return@launch
                }
                val urlDocType = "CUSTOM"
                val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", "${documentName.trim()}.pdf", requestBody)
                val response = api.uploadDocument(shopId = shopId, documentType = urlDocType, file = filePart, documentNumber = null, issueDate = null, expiryDate = null)
                if (response.isSuccessful) {
                    showToast("'$documentName' uploaded!", ToastType.SUCCESS)
                    dismissCustomDocDialog()
                    loadDocuments(shopId)
                } else {
                    showToast("Upload failed: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                showToast("Upload error: ${e.message}", ToastType.ERROR)
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
                        showToast("Failed to load captcha: ${response.parseErrorMessage()}", ToastType.ERROR)
                        onResult("", "")
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showToast("Network error loading captcha: ${e.message}", ToastType.ERROR)
                    onResult("", "")
                }
            }
        }
    }

    fun fetchGst(shopId: String, gstin: String, onResult: (Boolean, GstVerificationResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                android.util.Log.d("GST_FETCH", ">>> REQUEST: shopId=$shopId, gstin=$gstin")
                val response = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                    api.fetchGst(GstFetchRequest(shopId = shopId, gstin = gstin))
                }
                if (response == null) {
                    android.util.Log.w("GST_FETCH", "<<< TIMED OUT after ${FETCH_TIMEOUT_MS}ms")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(false, null)
                    }
                    return@launch
                }
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
                        showToast("GST verification failed: ${response.parseErrorMessage()}", ToastType.ERROR)
                        onResult(false, null)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GST_FETCH", "<<< EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showToast("Network error: ${e.message}", ToastType.ERROR)
                    onResult(false, null)
                }
            }
        }
    }

    fun fetchMsme(shopId: String, udyamNumber: String, sessionId: String, captchaText: String, onResult: (Boolean, UdyamVerifyResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                    api.fetchUdyam(UdyamFetchRequest(shopId = shopId, udyamNumber = udyamNumber, sessionId = sessionId, captchaText = captchaText))
                }
                if (response == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(false, null)
                    }
                    return@launch
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
                        showToast("MSME verification failed: ${response.parseErrorMessage()}", ToastType.ERROR)
                        onResult(false, null)
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showToast("Network error: ${e.message}", ToastType.ERROR)
                    onResult(false, null)
                }
            }
        }
    }

    fun fetchFssai(shopId: String, licenseNumber: String, onResult: (Boolean, FssaiVerificationResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                android.util.Log.d("FSSAI_FETCH", ">>> REQUEST: shopId=$shopId, licenseNumber=$licenseNumber")
                val response = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                    api.fetchFssai(FssaiFetchRequest(shopId = shopId, licenseNumber = licenseNumber))
                }
                if (response == null) {
                    android.util.Log.w("FSSAI_FETCH", "<<< TIMED OUT after ${FETCH_TIMEOUT_MS}ms")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        showToast("FSSAI fetch timed out. Please try again.", ToastType.ERROR)
                        onResult(false, null)
                    }
                    return@launch
                }
                android.util.Log.d("FSSAI_FETCH", "<<< RESPONSE CODE: ${response.code()}")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.success) {
                            onResult(true, body)
                        } else {
                            showToast(
                                body?.errorMessage
                                    ?: "FSSAI verification failed. Please check the license number and try again.",
                                ToastType.ERROR
                            )
                            onResult(false, body)
                        }
                    } else {
                        showToast("FSSAI verification failed: ${response.parseErrorMessage()}", ToastType.ERROR)
                        onResult(false, null)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FSSAI_FETCH", "<<< EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showToast("Network error: ${e.message}", ToastType.ERROR)
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

    fun clearNotifications() {
        viewModelScope.launch {
            try {
                val response = api.clearNotifications()
                if (response.isSuccessful) {
                    updateState { it.copy(notifications = emptyList(), unreadNotificationCount = 0) }
                    showToast("Notifications cleared", ToastType.SUCCESS)
                } else {
                    showToast("Failed to clear notifications: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                android.util.Log.e("Notifications", "Failed to clear: ${e.message}")
                showToast("Network error: ${e.message}", ToastType.ERROR)
            }
        }
    }

    fun loadBinNotifications() {
        viewModelScope.launch {
            try {
                val response = api.getBinNotifications()
                if (response.isSuccessful) {
                    val binNotifications = response.body() ?: emptyList()
                    updateState { it.copy(binNotifications = binNotifications) }
                }
            } catch (e: Exception) {
                android.util.Log.e("Notifications", "Failed to load bin: ${e.message}")
            }
        }
    }

    fun restoreNotification(notificationId: Long) {
        viewModelScope.launch {
            try {
                val response = api.restoreFromBin(notificationId)
                if (response.isSuccessful) {
                    updateState { state ->
                        state.copy(binNotifications = state.binNotifications.filterNot { it.id == notificationId })
                    }
                    loadNotifications()
                    showToast("Notification restored", ToastType.SUCCESS)
                } else {
                    android.util.Log.e("Notifications", "Failed to restore: ${response.code()}")
                    showToast("Failed to restore notification: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                android.util.Log.e("Notifications", "Failed to restore: ${e.message}")
                showToast("Network error: ${e.message}", ToastType.ERROR)
            }
        }
    }

    fun restoreAllFromBin() {
        viewModelScope.launch {
            try {
                val response = api.restoreAllFromBin()
                if (response.isSuccessful) {
                    updateState { it.copy(binNotifications = emptyList()) }
                    loadNotifications()
                    showToast("All notifications restored", ToastType.SUCCESS)
                } else {
                    android.util.Log.e("Notifications", "Failed to restore all: ${response.code()}")
                    showToast("Failed to restore notifications: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                android.util.Log.e("Notifications", "Failed to restore all: ${e.message}")
                showToast("Network error: ${e.message}", ToastType.ERROR)
            }
        }
    }

    fun permanentDeleteAll() {
        viewModelScope.launch {
            try {
                val response = api.permanentDelete()
                if (response.isSuccessful) {
                    updateState {
                        it.copy(notifications = emptyList(), binNotifications = emptyList(), unreadNotificationCount = 0)
                    }
                    showToast("Notifications deleted permanently", ToastType.SUCCESS)
                } else {
                    showToast("Failed to delete notifications: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                android.util.Log.e("Notifications", "Failed to permanently delete: ${e.message}")
                showToast("Network error: ${e.message}", ToastType.ERROR)
            }
        }
    }

    fun moveNotificationToBin(notificationId: Long) {
        viewModelScope.launch {
            try {
                val response = api.moveNotificationToBin(notificationId)
                if (response.isSuccessful) {
                    updateState { state ->
                        val remaining = state.notifications.filterNot { it.id == notificationId }
                        state.copy(
                            notifications = remaining,
                            unreadNotificationCount = remaining.count { !it.isRead }.toLong()
                        )
                    }
                    showToast("Notification moved to bin", ToastType.SUCCESS)
                } else {
                    android.util.Log.e("Notifications", "Failed to move notification to bin: ${response.code()}")
                    showToast("Failed to move to bin: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                android.util.Log.e("Notifications", "Failed to move notification to bin: ${e.message}")
                showToast("Network error: ${e.message}", ToastType.ERROR)
            }
        }
    }

    fun deleteNotification(notificationId: Long) {
        viewModelScope.launch {
            try {
                val response = api.deleteNotification(notificationId)
                if (response.isSuccessful) {
                    updateState { state ->
                        state.copy(binNotifications = state.binNotifications.filterNot { it.id == notificationId })
                    }
                    showToast("Notification deleted", ToastType.SUCCESS)
                } else {
                    android.util.Log.e("Notifications", "Failed to delete notification: ${response.code()}")
                    showToast("Failed to delete notification: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                android.util.Log.e("Notifications", "Failed to delete notification: ${e.message}")
                showToast("Network error: ${e.message}", ToastType.ERROR)
            }
        }
    }

    fun registerDeviceToken(token: String) {
        viewModelScope.launch {
            ApiClient.registerDeviceToken(context, token)
        }
    }

    fun requestRenewal(notification: NotificationItem) {
        val shopId = notification.referenceId
        val documentType = notification.metadata
        if (shopId == null || documentType.isNullOrBlank()) {
            showToast("Cannot start renewal for this alert", ToastType.ERROR)
            return
        }
        viewModelScope.launch {
            try {
                val response = api.requestRenewal(CreateRenewalRequest(shopId, documentType))
                if (response.isSuccessful) {
                    updateState { it.copy(renewalSuccessOrder = response.body()) }
                    showToast("Renewal requested", ToastType.SUCCESS)
                    loadNotifications()
                } else {
                    showToast("Failed to request renewal: ${response.parseErrorMessage()}", ToastType.ERROR)
                }
            } catch (e: Exception) {
                android.util.Log.e("Renewal", "Failed to request renewal: ${e.message}")
                showToast("Network error: ${e.message}", ToastType.ERROR)
            }
        }
    }

    fun dismissRenewalSuccess() {
        updateState { it.copy(renewalSuccessOrder = null) }
    }
}
