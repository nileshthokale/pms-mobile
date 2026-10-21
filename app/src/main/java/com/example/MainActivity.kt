package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ConfigurableRestAiPrescriptionService
import com.example.data.PharmacyDatabase
import com.example.data.PharmacyRepository
import com.example.model.PrescriptionStatus
import com.example.model.UserRole
import com.example.ui.BridgeScreen
import com.example.ui.PharmacyViewModel
import com.example.ui.UiState
import com.example.ui.screens.ApprovedPrescriptionsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.IncomingPrescriptionsScreen
import com.example.ui.screens.MedicineCatalogueScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.PrescriptionReviewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UploadPrescriptionDialog
import com.example.ui.theme.PharmacyBridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

@Composable
fun AppRoot() {
    AuthenticatedPharmacyApp(
        currentUserId = PharmacyRepository.DEFAULT_WORKSPACE_ID,
        userEmail = "admin@citycarepharmacy.local",
        userDisplayName = "Chief Pharmacist"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedPharmacyApp(
    currentUserId: String,
    userEmail: String,
    userDisplayName: String
) {
    val viewModel: PharmacyViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val db = PharmacyDatabase.getInstance(app)
                PharmacyViewModel(
                    repository = PharmacyRepository(db.pharmacyDao(), currentUserId),
                    aiService = ConfigurableRestAiPrescriptionService(),
                    currentUserId = currentUserId,
                    userEmail = userEmail,
                    userDisplayName = userDisplayName
                )
            }
        }
    )

    val systemDark = isSystemInDarkTheme()
    val darkOverride by viewModel.isDarkThemeOverride.collectAsStateWithLifecycle()
    val isDark = darkOverride ?: systemDark

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val selectedRxId by viewModel.selectedPrescriptionId.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val prescriptionsState by viewModel.prescriptionsState.collectAsStateWithLifecycle()
    val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
    val staffState by viewModel.staffState.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val aiSetupWarning by viewModel.aiSetupWarning.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val prescriptions = (prescriptionsState as? UiState.Success)?.data.orEmpty()
    val catalog = (catalogState as? UiState.Success)?.data.orEmpty()
    val staffList = (staffState as? UiState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsState as? UiState.Success)?.data.orEmpty()

    val activeRole = remember(profile) {
        UserRole.entries.firstOrNull { it.name == profile?.activeRole } ?: UserRole.ADMIN
    }
    val pharmacyName = profile?.pharmacyName ?: "City Care Pharmacy"

    var showUploadDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    if (currentScreen != BridgeScreen.DASHBOARD) {
        BackHandler {
            viewModel.navigateBack()
        }
    }

    val mobileBottomItems = remember(activeRole) {
        if (activeRole == UserRole.ADMIN) {
            listOf(
                NavDestination(BridgeScreen.DASHBOARD, "Home", Icons.Default.Dashboard),
                NavDestination(BridgeScreen.INCOMING, "Queue", Icons.Default.Inbox),
                NavDestination(BridgeScreen.REVIEW, "Review", Icons.Default.RateReview),
                NavDestination(BridgeScreen.MORE, "More", Icons.Default.MoreHoriz)
            )
        } else {
            listOf(
                NavDestination(BridgeScreen.DASHBOARD, "Home", Icons.Default.Dashboard),
                NavDestination(BridgeScreen.INCOMING, "Queue", Icons.Default.Inbox),
                NavDestination(BridgeScreen.APPROVED, "Ready", Icons.Default.AssignmentTurnedIn),
                NavDestination(BridgeScreen.HISTORY, "History", Icons.Default.History)
            )
        }
    }

    val railItems = remember(activeRole) {
        if (activeRole == UserRole.ADMIN) {
            listOf(
                NavDestination(BridgeScreen.DASHBOARD, "Home", Icons.Default.Dashboard),
                NavDestination(BridgeScreen.INCOMING, "Queue", Icons.Default.Inbox),
                NavDestination(BridgeScreen.REVIEW, "Review", Icons.Default.RateReview),
                NavDestination(BridgeScreen.APPROVED, "Ready", Icons.Default.AssignmentTurnedIn),
                NavDestination(BridgeScreen.CATALOGUE, "Items", Icons.Default.Inventory2),
                NavDestination(BridgeScreen.HISTORY, "History", Icons.Default.History),
                NavDestination(BridgeScreen.SETTINGS, "Settings", Icons.Default.Settings)
            )
        } else {
            mobileBottomItems
        }
    }

    val adminSecondaryScreens = remember {
        setOf(
            BridgeScreen.APPROVED,
            BridgeScreen.CATALOGUE,
            BridgeScreen.HISTORY,
            BridgeScreen.SETTINGS
        )
    }

    val pendingReviewBadgeCount = remember(prescriptions) {
        prescriptions.count {
            it.statusEnum() in setOf(PrescriptionStatus.PENDING, PrescriptionStatus.NEEDS_REVIEW)
        }
    }

    val approvedReadyCount = remember(prescriptions) {
        prescriptions.count {
            it.statusEnum() in setOf(
                PrescriptionStatus.APPROVED,
                PrescriptionStatus.IMPORTED,
                PrescriptionStatus.COMPLETED
            )
        }
    }

    val selectedPrescription = remember(prescriptions, selectedRxId) {
        prescriptions.firstOrNull { it.id == selectedRxId } ?: prescriptions.firstOrNull()
    }

    PharmacyBridgeTheme(darkTheme = isDark) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 680.dp
            val showBackInTopBar = !useNavigationRail &&
                activeRole == UserRole.ADMIN &&
                currentScreen in adminSecondaryScreens

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            if (showBackInTopBar) {
                                IconButton(
                                    onClick = { viewModel.navigateBack() },
                                    modifier = Modifier.testTag("top_bar_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to More"
                                    )
                                }
                            }
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (showBackInTopBar) currentScreen.title else "Pharmacy Bridge",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("header_role_badge")
                                ) {
                                    Text(
                                        text = if (activeRole == UserRole.ADMIN) "Pharmacist" else "Counter Staff",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        maxLines = 1
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showUploadDialog = true },
                                modifier = Modifier.testTag("top_bar_upload_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = "Upload Prescription Photo"
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleDarkTheme(isDark) },
                                modifier = Modifier.testTag("toggle_theme_button")
                            ) {
                                Icon(
                                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Light/Dark Theme"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar {
                            mobileBottomItems.forEach { dest ->
                                val isSelected = currentScreen == dest.screen ||
                                    (dest.screen == BridgeScreen.MORE && currentScreen in adminSecondaryScreens)
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { viewModel.navigateTo(dest.screen) },
                                    icon = {
                                        if (dest.screen == BridgeScreen.INCOMING && pendingReviewBadgeCount > 0) {
                                            BadgedBox(
                                                badge = { Badge { Text(pendingReviewBadgeCount.toString()) } }
                                            ) {
                                                Icon(imageVector = dest.icon, contentDescription = dest.label)
                                            }
                                        } else {
                                            Icon(imageVector = dest.icon, contentDescription = dest.label)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("nav_${dest.screen.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            railItems.forEach { dest ->
                                NavigationRailItem(
                                    selected = currentScreen == dest.screen,
                                    onClick = { viewModel.navigateTo(dest.screen) },
                                    icon = {
                                        Icon(imageVector = dest.icon, contentDescription = dest.label)
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag("rail_${dest.screen.name.lowercase()}")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (isBusy) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                                when (currentScreen) {
                                    BridgeScreen.DASHBOARD -> {
                                        DashboardScreen(
                                            pharmacyName = pharmacyName,
                                            activeRole = activeRole,
                                            prescriptions = prescriptions,
                                            auditLogs = auditLogs,
                                            catalogCount = catalog.size,
                                            onNavigate = { viewModel.navigateTo(it) },
                                            onSelectPrescription = { viewModel.selectPrescriptionForReview(it) },
                                            onOpenUploadDialog = { showUploadDialog = true },
                                            onSwitchRole = { viewModel.switchActiveRole(it) }
                                        )
                                    }
                                    BridgeScreen.INCOMING -> {
                                        IncomingPrescriptionsScreen(
                                            prescriptions = prescriptions,
                                            activeRole = activeRole,
                                            onSelectPrescription = { rxId ->
                                                if (activeRole == UserRole.ADMIN) {
                                                    viewModel.selectPrescriptionForReview(rxId)
                                                }
                                            },
                                            onOpenUploadDialog = { showUploadDialog = true }
                                        )
                                    }
                                    BridgeScreen.REVIEW -> {
                                        PrescriptionReviewScreen(
                                            prescription = selectedPrescription,
                                            allPrescriptions = prescriptions,
                                            catalog = catalog,
                                            aiSetupWarning = aiSetupWarning,
                                            isBusy = isBusy,
                                            onSelectPrescription = { viewModel.selectPrescriptionForReview(it) },
                                            onAnalyzePrescription = { viewModel.analyzePrescriptionWithConfiguredAi(it) },
                                            onSaveDraft = { pName, pAge, pContact, doc, date, items, notes ->
                                                selectedPrescription?.let { rx ->
                                                    viewModel.saveReviewDraft(
                                                        rx.id,
                                                        pName,
                                                        pAge,
                                                        pContact,
                                                        doc,
                                                        date,
                                                        items,
                                                        notes
                                                    )
                                                }
                                            },
                                            onApprove = { pName, pAge, pContact, doc, date, items, notes ->
                                                selectedPrescription?.let { rx ->
                                                    viewModel.approvePrescription(
                                                        rx,
                                                        pName,
                                                        pAge,
                                                        pContact,
                                                        doc,
                                                        date,
                                                        items,
                                                        notes
                                                    )
                                                }
                                            },
                                            onReject = { reason ->
                                                selectedPrescription?.let { rx ->
                                                    viewModel.rejectPrescription(rx, reason)
                                                }
                                            },
                                            onDelete = {
                                                selectedPrescription?.let { rx ->
                                                    viewModel.deletePrescription(rx)
                                                }
                                            },
                                            onOpenSettings = { viewModel.navigateTo(BridgeScreen.SETTINGS) }
                                        )
                                    }
                                    BridgeScreen.MORE -> {
                                        MoreScreen(
                                            approvedCount = approvedReadyCount,
                                            catalogCount = catalog.size,
                                            historyCount = prescriptions.size,
                                            onNavigate = { viewModel.navigateTo(it) }
                                        )
                                    }
                                    BridgeScreen.CATALOGUE -> {
                                        MedicineCatalogueScreen(
                                            catalog = catalog,
                                            isBusy = isBusy,
                                            onImportCsv = { viewModel.importCsvCatalogue(it) },
                                            onDeleteItem = { viewModel.deleteCatalogItem(it) }
                                        )
                                    }
                                    BridgeScreen.APPROVED -> {
                                        ApprovedPrescriptionsScreen(
                                            pharmacyName = pharmacyName,
                                            prescriptions = prescriptions,
                                            onClaimForCounter = { rx, term ->
                                                viewModel.claimPrescriptionForCounter(rx, term)
                                            },
                                            onMarkImported = { rx, term, completeNow ->
                                                viewModel.markPrescriptionImported(rx, term, completeNow)
                                            },
                                            onMarkCompleted = { rx ->
                                                viewModel.markPrescriptionCompleted(rx)
                                            },
                                            onExportJsonAudit = { rx ->
                                                viewModel.recordJsonExportAudit(rx)
                                            }
                                        )
                                    }
                                    BridgeScreen.HISTORY -> {
                                        HistoryScreen(
                                            prescriptions = prescriptions,
                                            auditLogs = auditLogs
                                        )
                                    }
                                    BridgeScreen.SETTINGS -> {
                                        SettingsScreen(
                                            profile = profile,
                                            staffList = staffList,
                                            isBusy = isBusy,
                                            onSaveSettings = { pName, role, retDays, endpoint, model, authM, reqFmt ->
                                                viewModel.saveSettings(
                                                    pName,
                                                    role,
                                                    retDays,
                                                    endpoint,
                                                    model,
                                                    authM,
                                                    reqFmt
                                                )
                                            },
                                            onAddStaff = { name, email, role, term ->
                                                viewModel.addStaffMember(name, email, role, term)
                                            },
                                            onToggleStaff = { viewModel.toggleStaffMember(it) },
                                            onDeleteStaff = { viewModel.deleteStaffMember(it) },
                                            onPurgeRetention = { viewModel.purgeExpiredRecords(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUploadDialog) {
        UploadPrescriptionDialog(
            activeRole = activeRole,
            defaultStaffName = profile?.displayName?.ifBlank { userDisplayName } ?: "Counter Operator",
            onDismiss = { showUploadDialog = false },
            onSubmit = { pName, pAge, pContact, notes, localPath, base64 ->
                viewModel.submitPrescription(
                    submittedByRole = activeRole,
                    submittedByName = profile?.displayName?.ifBlank { userDisplayName } ?: "Operator",
                    patientName = pName,
                    patientAge = pAge,
                    patientContact = pContact,
                    staffNotes = notes,
                    imageLocalPath = localPath,
                    imageBase64Preview = base64,
                    onSubmitted = { rxId ->
                        showUploadDialog = false
                        if (activeRole == UserRole.ADMIN) {
                            viewModel.selectPrescriptionForReview(rxId)
                        } else {
                            viewModel.navigateTo(BridgeScreen.INCOMING)
                        }
                    }
                )
            }
        )
    }
}

private data class NavDestination(
    val screen: BridgeScreen,
    val label: String,
    val icon: ImageVector
)
