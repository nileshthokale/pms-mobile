package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.MedicineCatalogItem
import com.example.model.PrescriptionRequest
import com.example.model.PrescriptionStatus
import com.example.ui.PharmacyViewModel
import com.example.ui.components.DesktopBridgeStatusBanner
import com.example.ui.components.StatusBadge
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun MedicineCatalogueScreen(
    catalog: List<MedicineCatalogItem>,
    isBusy: Boolean,
    onImportCsv: (String) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var csvText by remember { mutableStateOf(PharmacyViewModel.SAMPLE_ITEM_MASTER_CSV) }

    val filtered = remember(catalog, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) catalog
        else catalog.filter {
            it.itemName.lowercase().contains(q) ||
                it.itemCode.lowercase().contains(q) ||
                it.genericName.lowercase().contains(q)
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
                    text = "Pharmacy Item Master",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Verified item names, units, pack sizes, MRP, and GST% from CSV. Never invents stock or batch numbers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showCsvImportDialog = true },
                enabled = !isBusy,
                modifier = Modifier.testTag("import_csv_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = "Import CSV",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import CSV")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Item Master by item name, generic composition, or code...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty()) {
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
                    Text(
                        text = "Item Master Catalogue is Empty",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap 'Import CSV' to import your existing pharmacy Item Master CSV (or load the sample Item Master).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.itemName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "[${item.itemCode}]",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (item.genericName.isNotBlank()) {
                                    Text(
                                        text = item.genericName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Unit: ${item.unit} • Pack: ${item.packSize} • MRP: ₹${String.format("%.2f", item.mrp)} • GST: ${item.gstPercent}% • HSN: ${item.hsnCode.ifBlank { "N/A" }}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            IconButton(onClick = { onDeleteItem(item.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete item"
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showCsvImportDialog) {
        AlertDialog(
            onDismissRequest = { showCsvImportDialog = false },
            title = { Text("Import Pharmacy Item Master CSV") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Paste your pharmacy Item Master CSV below. Required columns: item_name (or name) and mrp (or rate). Optional: item_code, generic_name, unit, pack_size, gst_percent, hsn_code.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = csvText,
                        onValueChange = { csvText = it },
                        label = { Text("CSV Content") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .testTag("csv_import_textarea"),
                        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportCsv(csvText)
                        showCsvImportDialog = false
                    },
                    modifier = Modifier.testTag("confirm_csv_import_button")
                ) {
                    Text("Validate & Import CSV")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCsvImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ApprovedPrescriptionsScreen(
    pharmacyName: String,
    prescriptions: List<PrescriptionRequest>,
    onClaimForCounter: (PrescriptionRequest, String) -> Unit,
    onMarkImported: (PrescriptionRequest, String, Boolean) -> Unit,
    onMarkCompleted: (PrescriptionRequest) -> Unit,
    onExportJsonAudit: (PrescriptionRequest) -> Unit
) {
    val context = LocalContext.current
    val readyList = remember(prescriptions) {
        prescriptions.filter {
            it.statusEnum() in setOf(
                PrescriptionStatus.APPROVED,
                PrescriptionStatus.IMPORTED,
                PrescriptionStatus.COMPLETED
            )
        }
    }
    var jsonModalPrescription by remember { mutableStateOf<PrescriptionRequest?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ready for Counter Sale",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Approved prescriptions ready for the counter. Claim a prescription for your counter terminal or export JSON for the desktop Counter Sale screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (readyList.isEmpty()) {
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
                        Text(
                            text = "No Approved Prescriptions Yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Approve a reviewed prescription to prepare it for Counter Sale import.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(readyList, key = { it.id }) { rx ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${rx.requestCode} • ${rx.patientName.ifBlank { "Patient" }}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Reviewed by ${rx.reviewedBy.ifBlank { "Admin" }} • ${rx.items.size} verified item(s)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(status = rx.statusEnum())
                        }

                        // Mandatory "Ready for Counter Sale" Button
                        Button(
                            onClick = {
                                onExportJsonAudit(rx)
                                jsonModalPrescription = rx
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ready_for_counter_sale_${rx.requestCode}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(id = R.string.ready_for_counter_sale),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.ready_for_counter_sale) + " — View / Export JSON")
                        }

                        if (rx.claimedByCounterId.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Claim lock",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Claimed by ${rx.claimedByCounterId} • Token: ${rx.claimToken}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        // Recoverable Claim & Import State Transitions
                        if (rx.statusEnum() == PrescriptionStatus.APPROVED) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onClaimForCounter(rx, "COUNTER-1") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Lock COUNTER-1")
                                }
                                OutlinedButton(
                                    onClick = { onClaimForCounter(rx, "COUNTER-2") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Lock COUNTER-2")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    onMarkImported(
                                        rx,
                                        rx.claimedByCounterId.ifBlank { "COUNTER-1" },
                                        false
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Mark Imported")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mark IMPORTED (Test Duplicate-Import Protection)")
                            }
                        } else if (rx.statusEnum() == PrescriptionStatus.IMPORTED) {
                            Button(
                                onClick = { onMarkCompleted(rx) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Finalize Counter Sale (Transition to COMPLETED)")
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (jsonModalPrescription != null) {
        val rx = jsonModalPrescription!!
        val jsonPayload = remember(rx, pharmacyName) { rx.toDesktopExportJson(pharmacyName) }

        AlertDialog(
            onDismissRequest = { jsonModalPrescription = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Code, contentDescription = "JSON Contract")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Counter Sale JSON Export (${rx.requestCode})")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Status: Desktop Not Connected. Your Python/PySide6 desktop app can fetch or import this structured JSON payload and verify claimToken to prevent duplicate counter sales.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = jsonPayload,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("RxJSON", jsonPayload))
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy JSON")
                    }
                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, jsonPayload)
                                type = "application/json"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Prescription JSON"))
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { jsonModalPrescription = null }) {
                    Text("Close")
                }
            }
        )
    }
}
