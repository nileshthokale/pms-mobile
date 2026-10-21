package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.AuditLogEntry
import com.example.model.PrescriptionRequest
import com.example.model.PrescriptionStatus
import com.example.model.UserRole
import com.example.ui.BridgeScreen
import com.example.ui.PharmacyViewModel
import com.example.ui.components.DesktopBridgeStatusBanner
import com.example.ui.components.NeedsReviewPill
import com.example.ui.components.PrescriptionPreviewImage
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatTimestamp
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@Composable
fun DashboardScreen(
    pharmacyName: String,
    activeRole: UserRole,
    prescriptions: List<PrescriptionRequest>,
    auditLogs: List<AuditLogEntry>,
    catalogCount: Int,
    onNavigate: (BridgeScreen) -> Unit,
    onSelectPrescription: (String) -> Unit,
    onOpenUploadDialog: () -> Unit,
    onSwitchRole: (UserRole) -> Unit
) {
    val pendingCount = prescriptions.count { it.statusEnum() == PrescriptionStatus.PENDING }
    val reviewCount = prescriptions.count {
        it.statusEnum() in setOf(PrescriptionStatus.NEEDS_REVIEW, PrescriptionStatus.ANALYZING)
    }
    val approvedCount = prescriptions.count { it.statusEnum() == PrescriptionStatus.APPROVED }
    val importedCount = prescriptions.count {
        it.statusEnum() in setOf(PrescriptionStatus.IMPORTED, PrescriptionStatus.COMPLETED)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_list"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            DesktopBridgeStatusBanner()
        }

        item {
            val nextActionText = when {
                activeRole == UserRole.ADMIN && (pendingCount + reviewCount) > 0 ->
                    "Next step: ${pendingCount + reviewCount} prescription(s) waiting for pharmacist review."
                approvedCount > 0 ->
                    "Next step: $approvedCount approved prescription(s) ready for counter sale."
                else ->
                    "Next step: Upload a prescription photo to start a new counter request."
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pharmacyName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Role: ${activeRole.label}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val next = if (activeRole == UserRole.ADMIN) UserRole.COUNTER_STAFF else UserRole.ADMIN
                                onSwitchRole(next)
                            },
                            modifier = Modifier.testTag("switch_role_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Switch Role",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (activeRole == UserRole.ADMIN) "Switch to Staff" else "Switch to Admin"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = nextActionText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenUploadDialog,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_upload_rx_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Upload Prescription",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Photo")
                        }
                        if (activeRole == UserRole.ADMIN) {
                            OutlinedButton(
                                onClick = { onNavigate(BridgeScreen.INCOMING) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dashboard_queue_button")
                            ) {
                                Text("Review Queue (${pendingCount + reviewCount})")
                            }
                        } else if (approvedCount > 0) {
                            OutlinedButton(
                                onClick = { onNavigate(BridgeScreen.APPROVED) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dashboard_ready_button")
                            ) {
                                Text("Open Ready ($approvedCount)")
                            }
                        }
                    }
                }
            }
        }

        // How the app works (4 simple numbered steps)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("how_the_app_works_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "How the app works",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1. Upload a prescription photo.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "2. Pharmacist reviews and approves the medicine details.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "3. Counter staff opens the approved prescription.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "4. Verify local stock and batches in the desktop application, then complete the sale using the existing desktop workflow.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // KPI Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiStatCard(
                        title = "Pending Upload",
                        count = pendingCount,
                        subtitle = "Waiting for analysis",
                        icon = Icons.Default.HourglassEmpty,
                        onClick = { onNavigate(BridgeScreen.INCOMING) },
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatCard(
                        title = "Awaiting Review",
                        count = reviewCount,
                        subtitle = "Needs pharmacist check",
                        icon = Icons.Default.RateReview,
                        onClick = { onNavigate(BridgeScreen.INCOMING) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiStatCard(
                        title = "Ready for Counter",
                        count = approvedCount,
                        subtitle = "Approved for sale",
                        icon = Icons.Default.CheckCircle,
                        onClick = { onNavigate(BridgeScreen.APPROVED) },
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatCard(
                        title = "Item Master",
                        count = catalogCount,
                        subtitle = "$importedCount Rx imported",
                        icon = Icons.Default.Inventory2,
                        onClick = {
                            if (activeRole == UserRole.ADMIN) onNavigate(BridgeScreen.CATALOGUE)
                            else onNavigate(BridgeScreen.APPROVED)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Prescriptions needing immediate attention
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Prescription Queue",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = { onNavigate(BridgeScreen.INCOMING) }) {
                    Text("View All")
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View all prescriptions",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (prescriptions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Empty queue",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Prescriptions in Queue Yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Counter staff or admin can capture a prescription photo to start extraction and review.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(prescriptions.take(4), key = { it.id }) { rx ->
                PrescriptionRowCard(
                    prescription = rx,
                    activeRole = activeRole,
                    onClick = {
                        if (activeRole == UserRole.ADMIN) {
                            onSelectPrescription(rx.id)
                        } else {
                            onNavigate(BridgeScreen.INCOMING)
                        }
                    }
                )
            }
        }

        // Recent Activity Audit Feed
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Audit Activity",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = { onNavigate(BridgeScreen.HISTORY) }) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Audit History",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Full History")
                }
            }
        }

        if (auditLogs.isEmpty()) {
            item {
                Text(
                    text = "No audit events recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        } else {
            items(auditLogs.take(5), key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "[${log.action}] ${log.requestCode} • ${log.actorName}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = log.details,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatTimestamp(log.createdAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun KpiStatCard(
    title: String,
    count: Int,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun IncomingPrescriptionsScreen(
    prescriptions: List<PrescriptionRequest>,
    activeRole: UserRole,
    onSelectPrescription: (String) -> Unit,
    onOpenUploadDialog: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<PrescriptionStatus?>(null) }

    val filtered = remember(prescriptions, searchQuery, selectedFilter) {
        prescriptions.filter { rx ->
            val matchesStatus = selectedFilter == null || rx.statusEnum() == selectedFilter
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                rx.requestCode.lowercase().contains(q) ||
                rx.patientName.lowercase().contains(q) ||
                rx.doctorName.lowercase().contains(q) ||
                rx.staffNotes.lowercase().contains(q)
            matchesStatus && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Incoming Prescriptions",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = if (activeRole == UserRole.ADMIN) {
                        "Tap any request to analyze, review, and approve."
                    } else {
                        "Upload counter prescriptions and track live review status."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onOpenUploadDialog,
                modifier = Modifier.testTag("incoming_upload_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = "New Upload",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Rx")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("incoming_search_input"),
            placeholder = { Text("Search by Request ID (RX-...), patient, doctor, or notes") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text("All (${prescriptions.size})") }
            )
            PrescriptionStatus.entries.forEach { st ->
                val count = prescriptions.count { it.statusEnum() == st }
                FilterChip(
                    selected = selectedFilter == st,
                    onClick = { selectedFilter = if (selectedFilter == st) null else st },
                    label = { Text("${st.label} ($count)") }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching prescriptions found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { rx ->
                    PrescriptionRowCard(
                        prescription = rx,
                        activeRole = activeRole,
                        onClick = { onSelectPrescription(rx.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun PrescriptionRowCard(
    prescription: PrescriptionRequest,
    activeRole: UserRole,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("prescription_card_${prescription.requestCode}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = prescription.requestCode,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (prescription.needsReviewCount > 0) {
                        NeedsReviewPill(text = "${prescription.needsReviewCount} Needs Review")
                    }
                }
                StatusBadge(status = prescription.statusEnum())
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Patient: ${prescription.patientName.ifBlank { "Not specified" }}" +
                            if (prescription.patientAge.isNotBlank()) " (${prescription.patientAge} yrs)" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (prescription.doctorName.isNotBlank()) {
                        Text(
                            text = "Doctor: ${prescription.doctorName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (prescription.staffNotes.isNotBlank()) {
                        Text(
                            text = "Counter Note: ${prescription.staffNotes}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (prescription.reviewNotes.isNotBlank()) {
                        Text(
                            text = "Pharmacist Note: ${prescription.reviewNotes}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTimestamp(prescription.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${prescription.items.size} item(s)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (prescription.readyForCounterSale) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Ready for Counter Sale",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (activeRole == UserRole.ADMIN) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Open Review & Extraction →",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun UploadPrescriptionDialog(
    activeRole: UserRole,
    defaultStaffName: String,
    onDismiss: () -> Unit,
    onSubmit: (
        patientName: String,
        patientAge: String,
        patientContact: String,
        staffNotes: String,
        imageLocalPath: String,
        imageBase64Preview: String
    ) -> Unit
) {
    val context = LocalContext.current
    var patientName by remember { mutableStateOf("") }
    var patientAge by remember { mutableStateOf("") }
    var patientContact by remember { mutableStateOf("") }
    var staffNotes by remember { mutableStateOf("") }
    var imageLocalPath by remember { mutableStateOf("") }
    var imageBase64Preview by remember {
        mutableStateOf(PharmacyViewModel.generateSyntheticPrescriptionBase64())
    }
    var captureStatus by remember {
        mutableStateOf("Synthetic test prescription slip preloaded (or capture/pick photo below)")
    }

    val saveBitmapHelper: (Bitmap) -> Unit = { bmp ->
        try {
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                minOf(bmp.width, 720),
                minOf(bmp.height, 960),
                true
            )
            val file = File(context.filesDir, "rx_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            val byteOut = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 65, byteOut)
            imageLocalPath = file.absolutePath
            imageBase64Preview = Base64.encodeToString(byteOut.toByteArray(), Base64.NO_WRAP)
            captureStatus = "Saved private prescription photo (${file.name})"
        } catch (e: Exception) {
            captureStatus = "Error processing photo: ${e.localizedMessage}"
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            saveBitmapHelper(bitmap)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            captureStatus = "Camera permission declined. You can still pick an image or use the test slip."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                saveBitmapHelper(bmp)
            } catch (e: Exception) {
                captureStatus = "Could not load selected image: ${e.localizedMessage}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Upload Prescription Photo (${activeRole.label})",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrescriptionPreviewImage(
                    base64Preview = imageBase64Preview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )

                Text(
                    text = captureStatus,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                cameraLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("capture_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Camera",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera")
                    }

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Photo Picker")
                    }
                }

                OutlinedTextField(
                    value = patientName,
                    onValueChange = { patientName = it },
                    label = { Text("Patient Name (Optional — no real PII in demos)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_patient_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = patientAge,
                        onValueChange = { patientAge = it },
                        label = { Text("Age (Opt)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = patientContact,
                        onValueChange = { patientContact = it },
                        label = { Text("Contact # (Opt)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f)
                    )
                }

                OutlinedTextField(
                    value = staffNotes,
                    onValueChange = { staffNotes = it },
                    label = { Text("Counter Staff Notes (e.g. Waiting at Counter 1)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_staff_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val path = imageLocalPath.ifBlank { "/private/rx_synthetic_${System.currentTimeMillis()}.jpg" }
                    onSubmit(
                        patientName,
                        patientAge,
                        patientContact,
                        staffNotes,
                        path,
                        imageBase64Preview
                    )
                },
                modifier = Modifier.testTag("submit_prescription_button")
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = "Submit",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Submit to Queue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
