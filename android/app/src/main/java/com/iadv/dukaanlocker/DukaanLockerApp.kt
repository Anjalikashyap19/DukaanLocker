package com.iadv.dukaanlocker

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iadv.dukaanlocker.api.*
import com.iadv.dukaanlocker.ui.navigation.BottomTab
import com.iadv.dukaanlocker.ui.navigation.Screen
import com.iadv.dukaanlocker.ui.screens.*
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*

@Composable
fun DukaanLockerApp(
    onThemeChange: (Boolean) -> Unit = {},
    onLanguageChanged: (String) -> Unit = {},
    onGoogleSignIn: () -> Unit = {},
    registerGoogleAuthHandlers: ((onSuccess: (token: String, userId: Long, userName: String, email: String, mobileNumber: String, role: String) -> Unit, onError: (Exception) -> Unit) -> Unit)? = null,
    onAppUnlock: ((onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onBiometricLogin: ((onSuccess: (androidx.biometric.BiometricPrompt.CryptoObject) -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onEnableBiometricLogin: ((cipher: javax.crypto.Cipher, token: String, userId: Long, userName: String, email: String, role: String) -> Boolean)? = null,
    onAuthenticateForEnable: ((onSuccess: (androidx.biometric.BiometricPrompt.CryptoObject) -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    vm: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()

    // File picker launcher for document upload
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && state.pendingUploadDoc != null) {
            vm.uploadDocument(state.pendingUploadDoc!!, uri)
            vm.updateState { it.copy(pendingUploadDoc = null) }
        } else {
            vm.updateState { it.copy(pendingUploadDoc = null) }
        }
    }

    // Launch file picker when pendingUploadDoc is set
    LaunchedEffect(state.pendingUploadDoc) {
        state.pendingUploadDoc?.let {
            filePickerLauncher.launch("application/pdf")
        }
    }

    // ── GOOGLE SIGN-IN ──
    LaunchedEffect(Unit) {
        registerGoogleAuthHandlers?.invoke(
            { token, userId, userName, email, mobileNumber, role ->
                val auth = AuthResponse(token = token, tokenType = "Bearer", userId = userId, userName = userName, mobileNumber = mobileNumber, emailId = email, role = role)
                vm.saveAuth(auth, sendWelcomePush = true)
                vm.navigateToHome()
                Toast.makeText(context, "Welcome, $userName!", Toast.LENGTH_SHORT).show()
            },
            { exception ->
                Toast.makeText(context, "Google Sign-In failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    // ── APP UNLOCK ──
    LaunchedEffect(Unit) {
        if (onAppUnlock != null) {
            vm.updateState { it.copy(showAppUnlockPrompt = true) }
        } else {
            vm.updateState { it.copy(isAppUnlocked = true) }
        }
    }

    LaunchedEffect(state.showAppUnlockPrompt) {
        if (state.showAppUnlockPrompt && onAppUnlock != null && !state.isAppUnlocked) {
            onAppUnlock(
                { vm.updateState { it.copy(showAppUnlockPrompt = false, isAppUnlocked = true) } },
                { vm.updateState { it.copy(showAppUnlockPrompt = false, showAppUnlockFailedDialog = true) } }
            )
        }
    }

    // ── BIOMETRIC LOGIN ──
    LaunchedEffect(state.showBiometricLoginPrompt) {
        if (state.showBiometricLoginPrompt && onBiometricLogin != null) {
            onBiometricLogin(
                { cryptoObject ->
                    vm.updateState { it.copy(showBiometricLoginPrompt = false) }
                    val cipher = cryptoObject.cipher
                    if (cipher != null) {
                        val credentials = BiometricCredentialManager.getCredentialsWithCipher(context, cipher)
                        if (credentials != null) {
                            vm.biometricLogin(credentials) { success, _ ->
                                if (!success) vm.updateState { it.copy(biometricLoginFailed = true) }
                            }
                        } else {
                            vm.updateState { it.copy(biometricLoginFailed = true) }
                            Toast.makeText(context, "Biometric login unavailable. Please sign in normally.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        vm.updateState { it.copy(biometricLoginFailed = true) }
                        Toast.makeText(context, "Biometric authentication failed.", Toast.LENGTH_LONG).show()
                    }
                },
                { vm.updateState { it.copy(showBiometricLoginPrompt = false, biometricLoginFailed = true) } }
            )
        }
    }

    val onToggleTheme: () -> Unit = {
        val newTheme = !state.isDarkTheme
        vm.setTheme(newTheme)
        onThemeChange(newTheme)
    }

    val statusBarBg = when {
        state.currentScreen == Screen.Onboarding -> Color(0xFF2563EB)
        state.isDarkTheme -> DarkBg
        else -> Color(0xFFF8FAFC)
    }

    val activity = context as? Activity
    LaunchedEffect(state.isDarkTheme) {
        activity?.let { act ->
            val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
            controller.isAppearanceLightStatusBars = !state.isDarkTheme
            controller.isAppearanceLightNavigationBars = !state.isDarkTheme
        }
    }

    CompositionLocalProvider(LocalAppLanguage provides state.language) {
        DukaanLockerTheme(darkTheme = state.isDarkTheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = statusBarBg) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().statusBarsPadding().background(statusBarBg))

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        BackHandler(enabled = state.viewDocumentId != null) {
                            vm.closeDocumentViewer()
                        }
                        BackHandler(
                            enabled = state.currentScreen != Screen.Onboarding && state.currentScreen != Screen.Login && state.viewDocumentId == null
                                    && !(state.navigationHistory.isEmpty() && (state.currentScreen is Screen.OwnerHome || state.currentScreen is Screen.ManagerHome) && state.selectedBottomTab == BottomTab.Home)
                        ) {
                            if (state.navigationHistory.isNotEmpty()) {
                                vm.goBack()
                            } else if (state.currentScreen is Screen.OwnerHome || state.currentScreen is Screen.ManagerHome) {
                                if (state.selectedBottomTab != BottomTab.Home) {
                                    if (state.bottomTabHistory.isNotEmpty()) {
                                        vm.goBackTab()
                                    } else {
                                        vm.setBottomTab(BottomTab.Home)
                                    }
                                }
                            }
                        }

                        val handleLogout: () -> Unit = { vm.logout() }

                        val docsTabContent: @Composable () -> Unit = {
                            DocsScreen(
                                documents = vm.toDocumentItems(state.shopDocuments),
                                onFetchDoc = { doc -> vm.showFetchDialog(doc) },
                                onUploadDoc = { doc -> vm.updateState { s -> s.copy(pendingUploadDoc = doc) } },
                                onViewDoc = { doc -> vm.openDocument(doc) },
                                businesses = state.shops,
                                isLoadingDocuments = state.isLoadingDocuments
                            )
                        }

                        val settingsTabContent: @Composable () -> Unit = {
                            SettingsScreen(
                                isDarkTheme = state.isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                user = UserAccount(mobile = ApiClient.getUserMobile(context), name = state.currentUserName, email = state.currentUserEmail, role = state.currentUserRole),
                                onLogout = handleLogout,
                                businesses = if (state.currentUserRole == "MANAGER") state.shops else emptyList(),
                                isLoadingShops = state.isLoadingShops
                            )
                        }

                        val manageManagersContent: @Composable () -> Unit = {
                            ManageManagersScreen(
                                managers = state.managers.map { mgr ->
                                    ManagerAccess(code = mgr.managerCode ?: mgr.id.toString(), managerName = mgr.userName, id = mgr.id.toString(), assignedBusinessIds = state.managerShopAssignments[mgr.id] ?: emptyList(), enabled = mgr.enabled)
                                },
                                businesses = state.shops.map { vm.shopToBusiness(it) },
                                managerShopAssignments = state.managerShopAssignments.mapKeys { it.key.toString() }.mapValues { it.value },
                                onAddManager = { name, bizList -> vm.createManager(name, bizList) },
                                onDeleteManager = { managerId -> vm.deleteManager(managerId) },
                                onDisableManager = { managerId -> vm.disableManager(managerId) },
                                onEnableManager = { managerId -> vm.enableManager(managerId) },
                                onBack = { vm.setBottomTab(BottomTab.Business) },
                                isLoadingManagers = state.isLoadingManagers
                            )
                        }

                        when (state.currentScreen) {
                            is Screen.Onboarding -> {
                                MainScreen(
                                    isDarkTheme = state.isDarkTheme,
                                    onToggleTheme = onToggleTheme,
                                    onGetStarted = { vm.navigateTo(Screen.Login) },
                                    onLanguageChanged = { code -> vm.setLanguage(code); onLanguageChanged(code) }
                                )
                            }

                            is Screen.Login -> {
                                LoginScreen(
                                    isDarkTheme = state.isDarkTheme,
                                    onToggleTheme = onToggleTheme,
                                    onBackToMain = { vm.setScreen(Screen.Onboarding) },
                                    isBiometricLoginEnabled = state.isBiometricLoginEnabled && BiometricCredentialManager.hasStoredCredentials(context),
                                    onBiometricLogin = { if (!state.biometricLoginFailed) vm.updateState { it.copy(showBiometricLoginPrompt = true) } },
                                    onGoogleSignIn = onGoogleSignIn,
                                    onOwnerLogin = { email, password, onDone -> vm.login(email, password, onDone) },
                                    onRegister = { name, email, password, mobile, onDone -> vm.register(name, email, password, mobile, onDone) },
                                    onInitMsmeCaptcha = { onResult -> vm.initUdyamCaptcha(onResult) },
                                    onRegisterWithMsme = { msmeNumber, mobile, sessionId, captchaText, onDone -> vm.registerWithMsme(msmeNumber, mobile, sessionId, captchaText, onDone) },
                                    onMsmeLoginRequest = { msmeNumber, onResult -> vm.msmeLoginRequest(msmeNumber, onResult) },
                                    onMsmeLoginVerify = { msmeNumber, otp, onResult -> vm.msmeLoginVerify(msmeNumber, otp, onResult) },
                                    onManagerLogin = { code -> vm.loginByCode(code) }
                                )
                            }

                            is Screen.Wizard -> {
                                WizardScreen(
                                    onComplete = { wizard -> vm.saveWizardProfile(wizard) },
                                    onSkip = { vm.skipWizard() },
                                    onBackToLogin = { vm.skipWizardAndLogout() }
                                )
                            }

                            is Screen.AddBusiness -> {
                                val currentShopId = state.editShopTarget?.id?.toString()
                                val currentManagerId = if (currentShopId != null) {
                                    state.managerShopAssignments.entries.find { currentShopId in it.value }?.key?.toString()
                                } else null
                                var pendingManagerId by remember { mutableStateOf<String?>(currentManagerId) }

                                AddBusinessScreen(
                                    initial = state.editShopTarget?.let { vm.shopToBusiness(it) },
                                    managers = state.managers.map { mgr ->
ManagerAccess(id = mgr.id.toString(), code = mgr.managerCode ?: mgr.id.toString(), managerName = mgr.userName, assignedBusinessIds = state.managerShopAssignments[mgr.id] ?: emptyList(), enabled = mgr.enabled)
                                    },
                                    assignedManagerId = currentManagerId,
                                    onManagerSelected = { managerId -> pendingManagerId = managerId },
                                    onSave = { biz ->
                                        if (state.editShopTarget != null) {
                                            vm.updateShop(state.editShopTarget!!.id, biz, pendingManagerId) {}
                                        } else {
                                            vm.createShop(biz, pendingManagerId) {}
                                        }
                                    },
                                    onCancel = { vm.cancelAddBusiness() }
                                )
                            }

                            is Screen.OwnerHome -> {
                                if (state.selectedBottomTab == BottomTab.Docs) {
                                    docsTabContent()
                                } else if (state.selectedBottomTab == BottomTab.Settings) {
                                    settingsTabContent()
                                } else if (state.selectedBottomTab == BottomTab.Team) {
                                    manageManagersContent()
                                } else {
                                    OwnerHomeScreen(
                                        isDarkTheme = state.isDarkTheme,
                                        onToggleTheme = onToggleTheme,
                                        isBiometricLoginEnabled = state.isBiometricLoginEnabled,
                                        isBiometricAvailable = onBiometricLogin != null,
                                        onAuthenticateForBiometric = {
                                            onAuthenticateForEnable?.invoke(
                                                { cryptoObject ->
                                                    val cipher = cryptoObject.cipher
                                                    if (cipher != null) {
                                                        vm.enableBiometricLogin(cipher, state.authToken ?: "", state.currentUserId, state.currentUserName, state.currentUserEmail, state.currentUserRole)
                                                    } else {
                                                        Toast.makeText(context, "Biometric authentication failed. Cipher is null.", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                { errorMessage -> Toast.makeText(context, "Authentication failed: $errorMessage", Toast.LENGTH_SHORT).show() }
                                            )
                                        },
                                        onBiometricLoginToggle = { enabled -> if (!enabled) vm.disableBiometricLogin() },
                                        showAddBusiness = state.currentUserRole == "ADMIN",
                                        showManageManagers = state.currentUserRole == "ADMIN",
                                        user = UserAccount(mobile = ApiClient.getUserMobile(context), name = state.currentUserName, email = state.currentUserEmail, role = state.currentUserRole),
                                        businesses = state.shops.map { vm.shopToBusiness(it) },
                                        onBusinessSelected = { bizId -> vm.loadDocuments(bizId.toLongOrNull() ?: return@OwnerHomeScreen) },
                                        documents = vm.toDocumentItems(state.shopDocuments),
                                        managers = state.managers.map { mgr -> ManagerAccess(id = mgr.id.toString(), code = mgr.managerCode ?: mgr.id.toString(), managerName = mgr.userName, assignedBusinessIds = state.managerShopAssignments[mgr.id] ?: emptyList()) },
                                        onAddBusiness = { vm.addBusiness() },
                                        onEditBusiness = { biz -> vm.editBusiness(biz) },
                                        onManageManagers = { vm.setBottomTab(BottomTab.Team) },
                                        onFetchDoc = { doc -> vm.showFetchDialog(doc) },
                                        onUploadDoc = { doc -> vm.updateState { s -> s.copy(pendingUploadDoc = doc) } },
                                        onViewDoc = { doc -> vm.openDocument(doc) },
                                        onDeleteDoc = { doc -> Toast.makeText(context, "${doc.name} - delete via API", Toast.LENGTH_SHORT).show() },
                                        onLogout = { vm.logout() },
                                        isLoadingShops = state.isLoadingShops,
                                        isLoadingDocuments = state.isLoadingDocuments,
                                        unreadNotificationCount = state.unreadNotificationCount,
                                        onNotifications = { vm.navigateTo(Screen.Notifications) }
                                    )
                                }
                            }

                            is Screen.ManagerHome -> {
                                if (state.selectedBottomTab == BottomTab.Docs) {
                                    docsTabContent()
                                } else if (state.selectedBottomTab == BottomTab.Settings) {
                                    settingsTabContent()
                                } else if (state.selectedBottomTab == BottomTab.Team) {
                                    manageManagersContent()
                                } else {
                                    ManagerHomeScreen(
                                        isDarkTheme = state.isDarkTheme,
                                        onToggleTheme = onToggleTheme,
                                        user = UserAccount(mobile = ApiClient.getUserMobile(context), name = state.currentUserName, email = state.currentUserEmail, role = state.currentUserRole, managerCode = state.currentUserManagerCode),
                                        managerAccess = ManagerAccess(id = state.currentUserId.toString(), code = state.currentUserManagerCode, managerName = state.currentUserName, assignedBusinessIds = state.shops.map { it.id.toString() }),
                                        businesses = state.shops.map { vm.shopToBusiness(it) },
                                        documents = vm.toDocumentItems(state.shopDocuments),
                                        onFetchDoc = { doc -> vm.showFetchDialog(doc) },
                                        onUploadDoc = { doc -> vm.updateState { s -> s.copy(pendingUploadDoc = doc) } },
                                        onViewDoc = { doc -> vm.openDocument(doc) },
                                        onDeleteDoc = { },
                                        onLogout = { vm.logoutManager() },
                                        isLoadingShops = state.isLoadingShops
                                    )
                                }
                            }

                            is Screen.Notifications -> {
                                NotificationScreen(
                                    notifications = state.notifications,
                                    onBack = { vm.goBack() },
                                    onMarkAllRead = { vm.markNotificationsRead() }
                                )
                            }
                        }

                        // ── Document Viewer overlay ──
                        if (state.viewDocumentId != null) {
                            DocumentViewerScreen(
                                documentId = state.viewDocumentId!!,
                                documentName = state.viewDocumentName,
                                onBack = { vm.closeDocumentViewer() }
                            )
                        }

                        // ── Loading overlay ──
                        if (state.isLoading) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = if (state.isDarkTheme) Color.White else Color(0xFF2563EB))
                            }
                        }

                        // ── Fetch Document Dialog ──
                        if (state.showFetchDialog && state.fetchTargetDoc != null) {
                            val fetchDoc = state.fetchTargetDoc!!
                            val shop = state.shops.find { it.id.toString() == fetchDoc.businessId }
                            FetchDocumentDialog(
                                doc = fetchDoc,
                                shopName = shop?.shopName ?: "Business",
                                onDismiss = { vm.dismissFetchDialog() },
                                onSuccess = { regNum, issue, expiry ->
                                    vm.dismissFetchDialog()
                                    if (shop != null) {
                                        vm.loadDocuments(shop.id)
                                    }
                                },
                                onFetchGst = { shopId, gstin, result ->
                                    vm.fetchGst(shopId, gstin) { success, response ->
                                        result(success, response)
                                    }
                                },
                                onInitMsmeCaptcha = { onResult ->
                                    vm.initUdyamCaptcha(onResult)
                                },
                                onFetchMsme = { shopId, udyamNumber, sessionId, captchaText, result ->
                                    vm.fetchMsme(shopId, udyamNumber, sessionId, captchaText) { success, response ->
                                        result(success, response)
                                    }
                                }
                            )
                        }

                        // ── Certificate Viewer Dialog ──
                        if (state.docForView != null) {
                            val viewDoc = state.docForView!!
                            CertificateViewerDialog(doc = viewDoc, business = vm.findBusinessFor(viewDoc) ?: BusinessProfile(name = state.currentUserName), onDismiss = { vm.dismissCertDialog() })
                        }

                        // ── App Unlock Failed Dialog ──
                        if (state.showAppUnlockFailedDialog) {
                            AlertDialog(
                                onDismissRequest = { vm.updateState { it.copy(showAppUnlockFailedDialog = false) } },
                                containerColor = if (state.isDarkTheme) Color(0xFF1E293B) else Color.White,
                                shape = RoundedCornerShape(20.dp),
                                title = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Authentication Failed", fontWeight = FontWeight.Bold, color = if (state.isDarkTheme) Color.White else Color.Black)
                                    }
                                },
                                text = { Text("Authentication failed. Please try again.", color = if (state.isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                confirmButton = {
                                    Button(onClick = { vm.updateState { it.copy(showAppUnlockFailedDialog = false, showAppUnlockPrompt = true) } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                        Text("Try Again", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = null
                            )
                        }

                        // ── Biometric Login Failed Dialog ──
                        if (state.showBiometricLoginFailedDialog) {
                            AlertDialog(
                                onDismissRequest = { vm.updateState { it.copy(showBiometricLoginFailedDialog = false) } },
                                containerColor = if (state.isDarkTheme) Color(0xFF1E293B) else Color.White,
                                shape = RoundedCornerShape(20.dp),
                                title = { Text("Biometric Login Unavailable", fontWeight = FontWeight.Bold, color = if (state.isDarkTheme) Color.White else Color.Black) },
                                text = { Text("Please sign in with your email and password.", color = if (state.isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                confirmButton = {
                                    Button(onClick = { vm.updateState { it.copy(showBiometricLoginFailedDialog = false) } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                        Text("OK", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = null
                            )
                        }
                    }

                    // ── Bottom Navigation Bar ──
                    if (state.isLoggedIn && state.viewDocumentId == null && (state.currentScreen is Screen.OwnerHome || state.currentScreen is Screen.ManagerHome)) {
                        BottomNavBar(currentTab = state.selectedBottomTab, onNavigate = { tab -> vm.setBottomTab(tab) }, isDarkTheme = state.isDarkTheme, showTeam = state.currentUserRole != "MANAGER")
                    }
                }
            }
        }
    }
}
