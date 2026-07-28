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
import com.example.dukaanlocker.ui.screens.*
import com.example.dukaanlocker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DukaanLockerApp(onThemeChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current

    // Global state
    var currentUser by remember { mutableStateOf(LockerStorage.getUser(context)) }
    var wizardAnswers by remember { mutableStateOf(LockerStorage.getWizard(context)) }
    var isWizardDone by remember { mutableStateOf(LockerStorage.isWizardDone(context)) }
    var businesses by remember { mutableStateOf(LockerStorage.getBusinesses(context)) }
    var managers by remember { mutableStateOf(LockerStorage.getManagers(context)) }
    var allDocuments by remember { mutableStateOf<List<DocumentItem>>(emptyList()) }

    // Navigation state
    var currentScreen by remember { mutableStateOf("") }
    var editBusinessTarget by remember { mutableStateOf<BusinessProfile?>(null) }

    // Bottom nav state (only used for owner home sub-tabs)
    var selectedBottomTab by remember { mutableStateOf("home") }

    // Theme state
    var isDarkTheme by remember { mutableStateOf(LockerStorage.getTheme(context)) }

    // Dialog states for document management
    var docForFetch by remember { mutableStateOf<DocumentItem?>(null) }
    var docForUpload by remember { mutableStateOf<DocumentItem?>(null) }
    var docForView by remember { mutableStateOf<DocumentItem?>(null) }

    // Helper: save state helpers
    fun saveDocuments(docs: List<DocumentItem>) {
        allDocuments = docs
        val grouped = docs.groupBy { it.businessId }
        grouped.forEach { entry ->
            LockerStorage.saveDocs(context, entry.key, entry.value)
        }
    }

    // Helper: generate DocumentItem list from required metadata
    fun generateDocItems(bizId: String, required: List<Pair<String, String>>): List<DocumentItem> {
        return required.map { pair ->
            DocumentItem(
                id = UUID.randomUUID().toString(),
                businessId = bizId,
                type = pair.first,
                name = pair.second,
                status = "MISSING"
            )
        }
    }

    // Helper to load documents for all businesses
    fun loadDocumentsForBusinesses() {
        val all = mutableListOf<DocumentItem>()
        businesses.forEach { biz ->
            val docs = LockerStorage.getDocs(context, biz.id)
            if (docs.isNotEmpty()) {
                all.addAll(docs)
            } else {
                val required = requiredDocMeta(biz.category, biz.scale)
                val newDocs = generateDocItems(biz.id, required)
                all.addAll(newDocs)
                LockerStorage.saveDocs(context, biz.id, newDocs)
            }
        }
        allDocuments = all
    }

    // Helper to sync documents for a specific business
    fun syncDocumentsForBusiness(business: BusinessProfile) {
        val existing = LockerStorage.getDocs(context, business.id)
        if (existing.isEmpty()) {
            val required = requiredDocMeta(business.category, business.scale)
            val newDocs = generateDocItems(business.id, required)
            LockerStorage.saveDocs(context, business.id, newDocs)
            allDocuments = allDocuments.filter { it.businessId != business.id } + newDocs
        }
    }

    // Determine initial screen
    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            currentScreen = "login"
        } else {
            loadDocumentsForBusinesses()
            if (currentUser!!.role == "OWNER") {
                if (!isWizardDone) {
                    currentScreen = "wizard"
                } else {
                    currentScreen = "owner_home"
                }
            } else {
                currentScreen = "manager_home"
            }
        }
    }

    val onToggleTheme: () -> Unit = {
        isDarkTheme = !isDarkTheme
        LockerStorage.saveTheme(context, isDarkTheme)
        onThemeChange(isDarkTheme)
    }

    // Status bar background color — dark bg for dark theme, light bg for light theme
    val statusBarBg = if (isDarkTheme) DarkBg else Color(0xFFF8FAFC)

    // Update system status bar and nav bar icon colors to match app theme
    val activity = context as? Activity
    SideEffect {
        activity?.let { act ->
            val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
            // Dark theme → white icons (light appearance = false)
            // Light theme → dark icons (light appearance = true)
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
                // Status bar background — provides solid color behind system status bar icons
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
                    // Screen Routing
                    when (currentScreen) {
                        "login" -> {
                            LoginScreen(
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                onOwnerLogin = { email, password ->
                                    val existingUser = LockerStorage.getUser(context)
                                    val user = if (existingUser != null && existingUser.email == email && existingUser.role == "OWNER") {
                                        existingUser
                                    } else {
                                        UserAccount(
                                            mobile = "",
                                            name = email.substringBefore("@"),
                                            email = email,
                                            password = password,
                                            role = "OWNER",
                                            managerCode = ""
                                        )
                                    }
                                    LockerStorage.saveUser(context, user)
                                    currentUser = user
                                    businesses = LockerStorage.getBusinesses(context)
                                    managers = LockerStorage.getManagers(context)
                                    if (LockerStorage.isWizardDone(context)) {
                                        currentScreen = "owner_home"
                                    } else {
                                        currentScreen = "wizard"
                                    }
                                },
                                onRegister = { name, email, password, mobile ->
                                    val user = UserAccount(
                                        mobile = mobile,
                                        name = name,
                                        email = email,
                                        password = password,
                                        role = "OWNER",
                                        managerCode = ""
                                    )
                                    LockerStorage.saveUser(context, user)
                                    currentUser = user
                                    businesses = LockerStorage.getBusinesses(context)
                                    managers = LockerStorage.getManagers(context)
                                    currentScreen = "wizard"
                                },
                                onManagerLogin = { code ->
                                    val access = LockerStorage.validateManagerCode(context, code.uppercase())
                                    if (access != null) {
                                        val managerUser = UserAccount(
                                            mobile = "",
                                            name = access.managerName,
                                            email = "",
                                            password = "",
                                            role = "MANAGER",
                                            managerCode = code.uppercase()
                                        )
                                        LockerStorage.saveUser(context, managerUser)
                                        currentUser = managerUser
                                        businesses = LockerStorage.getBusinesses(context)
                                        managers = LockerStorage.getManagers(context)
                                        loadDocumentsForBusinesses()
                                        currentScreen = "manager_home"
                                        Toast.makeText(context, "Welcome, ${access.managerName}!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Invalid access code. Please check with the owner.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        }

                        "wizard" -> {
                            WizardScreen(
                                onComplete = { wizard ->
                                    LockerStorage.saveWizard(context, wizard)
                                    wizardAnswers = wizard
                                    isWizardDone = true
                                    currentScreen = "add_business"
                                },
                                onSkip = {
                                    LockerStorage.saveWizard(context, WizardAnswers())
                                    isWizardDone = true
                                    currentScreen = "add_business"
                                },
                                onBackToLogin = {
                                    LockerStorage.clearUser(context)
                                    currentUser = null
                                    currentScreen = "login"
                                }
                            )
                        }

                        "add_business" -> {
                            AddBusinessScreen(
                                initial = editBusinessTarget,
                                onSave = { business ->
                                    val existing = businesses.toMutableList()
                                    val idx = existing.indexOfFirst { it.id == business.id }
                                    if (idx >= 0) {
                                        existing[idx] = business
                                    } else {
                                        existing.add(business)
                                    }
                                    businesses = existing
                                    LockerStorage.saveBusinesses(context, existing)
                                    syncDocumentsForBusiness(business)
                                    editBusinessTarget = null
                                    currentScreen = "owner_home"
                                    Toast.makeText(context, "${business.name} saved!", Toast.LENGTH_SHORT).show()
                                },
                                onCancel = {
                                    editBusinessTarget = null
                                    currentScreen = "owner_home"
                                }
                            )
                        }

                        "owner_home" -> {
                            OwnerHomeScreen(
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                user = currentUser ?: UserAccount("", "", "", "", "OWNER"),
                                businesses = businesses,
                                documents = allDocuments,
                                managers = managers,
                                onAddBusiness = {
                                    editBusinessTarget = null
                                    currentScreen = "add_business"
                                },
                                onEditBusiness = { business ->
                                    editBusinessTarget = business
                                    currentScreen = "add_business"
                                },
                                onManageManagers = {
                                    currentScreen = "manage_managers"
                                },
                                onFetchDoc = { doc -> docForFetch = doc },
                                onUploadDoc = { doc -> docForUpload = doc },
                                onViewDoc = { doc -> docForView = doc },
                                onDeleteDoc = { doc ->
                                    val updated = allDocuments.map {
                                        if (it.id == doc.id) it.copy(status = "MISSING", regNumber = "", expiryDate = "", issueDate = "")
                                        else it
                                    }
                                    saveDocuments(updated)
                                    Toast.makeText(context, "${doc.name} removed from vault", Toast.LENGTH_SHORT).show()
                                },
                                onLogout = {
                                    LockerStorage.clearUser(context)
                                    currentUser = null
                                    currentScreen = "login"
                                    Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        "manage_managers" -> {
                            ManageManagersScreen(
                                managers = managers,
                                businesses = businesses,
                                onAddManager = { name, bizList ->
                                    val code = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
                                    val newManager = ManagerAccess(
                                        code = code,
                                        managerName = name,
                                        assignedBusinessIds = bizList
                                    )
                                    val updated = managers + newManager
                                    managers = updated
                                    LockerStorage.saveManagers(context, updated)
                                    Toast.makeText(context, "Manager '$name' created! Code: $code", Toast.LENGTH_LONG).show()
                                },
                                onDeleteManager = { code ->
                                    val updated = managers.filter { it.code != code }
                                    managers = updated
                                    LockerStorage.saveManagers(context, updated)
                                    Toast.makeText(context, "Manager access revoked", Toast.LENGTH_SHORT).show()
                                },
                                onBack = { currentScreen = "owner_home" }
                            )
                        }

                        "manager_home" -> {
                            val user = currentUser ?: run {
                                currentScreen = "login"
                                return@Surface
                            }
                            val mgrAccess = if (user.managerCode.isNotBlank()) {
                                LockerStorage.validateManagerCode(context, user.managerCode)
                            } else null

                            if (mgrAccess == null && currentUser?.role == "MANAGER") {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Access code not found", color = Color.Red, fontWeight = FontWeight.Bold)
                                        TextButton(onClick = {
                                            LockerStorage.clearUser(context)
                                            currentUser = null
                                            currentScreen = "login"
                                        }) {
                                            Text("Go back", color = GoldColor)
                                        }
                                    }
                                }
                            } else {
                                ManagerHomeScreen(
                                    isDarkTheme = isDarkTheme,
                                    onToggleTheme = onToggleTheme,
                                    user = user,
                                    managerAccess = mgrAccess ?: ManagerAccess("", "", emptyList()),
                                    businesses = businesses,
                                    documents = allDocuments,
                                    onFetchDoc = { doc -> docForFetch = doc },
                                    onUploadDoc = { doc -> docForUpload = doc },
                                    onViewDoc = { doc -> docForView = doc },
                                    onDeleteDoc = { doc ->
                                        val updated = allDocuments.map {
                                            if (it.id == doc.id) it.copy(status = "MISSING", regNumber = "", expiryDate = "", issueDate = "")
                                            else it
                                        }
                                        saveDocuments(updated)
                                    },
                                    onLogout = {
                                        LockerStorage.clearUser(context)
                                        currentUser = null
                                        currentScreen = "login"
                                    }
                                )
                            }
                        }
                    }

                    // ── Dialogs ──────────────────────────────────────────────────────

                    // Document Fetch Dialog
                    docForFetch?.let { doc ->
                        val business = businesses.find { it.id == doc.businessId }
                        FetchDocumentDialog(
                            doc = doc,
                            shopName = business?.name ?: "Dukaan",
                            onDismiss = { docForFetch = null },
                            onSuccess = { regNum, issue, expiry ->
                                val updated = allDocuments.map {
                                    if (it.id == doc.id) it.copy(status = "FETCHED", regNumber = regNum, issueDate = issue, expiryDate = expiry)
                                    else it
                                }
                                saveDocuments(updated)
                                docForFetch = null
                                Toast.makeText(context, "${doc.name} auto-fetched successfully!", Toast.LENGTH_LONG).show()
                            }
                        )
                    }

                    // Document Upload Dialog
                    docForUpload?.let { doc ->
                        UploadDocumentDialog(
                            doc = doc,
                            onDismiss = { docForUpload = null },
                            onSuccess = {
                                val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                                val expiryCal = Calendar.getInstance().apply { add(Calendar.YEAR, 3) }
                                val expiryStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(expiryCal.time)
                                val randomRegNum = "UP-${(100000..999999).random()}-${doc.type}"

                                val updated = allDocuments.map {
                                    if (it.id == doc.id) it.copy(status = "UPLOADED", regNumber = randomRegNum, issueDate = todayStr, expiryDate = expiryStr)
                                    else it
                                }
                                saveDocuments(updated)
                                docForUpload = null
                                Toast.makeText(context, "${doc.name} uploaded securely!", Toast.LENGTH_LONG).show()
                            }
                        )
                    }

                    // Certificate Viewer Dialog
                    docForView?.let { doc ->
                        val business = businesses.find { it.id == doc.businessId }
                        if (business != null) {
                            CertificateViewerDialog(
                                doc = doc,
                                business = business,
                                onDismiss = { docForView = null }
                            )
                        } else {
                            docForView = null
                        }
                    }
                }

                // ── Bottom Navigation Bar ─────────────────────────────────────────
                if (currentUser != null && currentScreen in listOf("owner_home", "manager_home")) {
                    BottomNavBar(
                        currentRoute = selectedBottomTab,
                        onNavigate = { route ->
                            selectedBottomTab = route
                            when (route) {
                                "home" -> { /* already on home */ }
                                "business" -> { /* already on owner_home, could show business list */ }
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
