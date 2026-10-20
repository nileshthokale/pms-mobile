package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.MedicineCatalogueMatcher
import com.example.model.MedicineCatalogItem
import com.example.model.PrescriptionMedicineItem
import com.example.model.PrescriptionRequest
import com.example.ui.BridgeScreen
import com.example.ui.components.NeedsReviewPill
import com.example.ui.components.PrescriptionPreviewImage
import com.example.ui.components.StatusBadge
import java.util.UUID

@Composable
fun PrescriptionReviewScreen(
    prescription: PrescriptionRequest?,
    allPrescriptions: List<PrescriptionRequest>,
    catalog: List<MedicineCatalogItem>,
    aiSetupWarning: List<String>,
    isBusy: Boolean,
    onSelectPrescription: (String) -> Unit,
    onAnalyzePrescription: (PrescriptionRequest) -> Unit,
    onSaveDraft: (
        patientName: String,
        patientAge: String,
        patientContact: String,
        doctorName: String,
        prescriptionDate: String,
        items: List<PrescriptionMedicineItem>,
        reviewNotes: String
    ) -> Unit,
    onApprove: (
        patientName: String,
        patientAge: String,
        patientContact: String,
        doctorName: String,
        prescriptionDate: String,
        items: List<PrescriptionMedicineItem>,
        reviewNotes: String
    ) -> Unit,
    onReject: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    if (prescription == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Select a Prescription to Review",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose an incoming prescription from the queue below to inspect the handwritten slip beside extracted fields.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            allPrescriptions.take(5).forEach { rx ->
                OutlinedButton(
                    onClick = { onSelectPrescription(rx.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("${rx.requestCode} — ${rx.patientName.ifBlank { "Unspecified" }} (${rx.status})")
                }
            }
        }
        return
    }

    var patientName by remember(prescription.id, prescription.updatedAt) {
        mutableStateOf(prescription.patientName)
    }
    var patientAge by remember(prescription.id, prescription.updatedAt) {
        mutableStateOf(prescription.patientAge)
    }
    var patientContact by remember(prescription.id, prescription.updatedAt) {
        mutableStateOf(prescription.patientContact)
    }
    var doctorName by remember(prescription.id, prescription.updatedAt) {
        mutableStateOf(prescription.doctorName)
    }
    var prescriptionDate by remember(prescription.id, prescription.updatedAt) {
        mutableStateOf(prescription.prescriptionDate)
    }
    var reviewNotes by remember(prescription.id, prescription.updatedAt) {
        mutableStateOf(prescription.reviewNotes)
    }

    val editableItems = remember(prescription.id, prescription.updatedAt) {
        mutableStateListOf<PrescriptionMedicineItem>().apply {
            addAll(prescription.parsedItems())
        }
    }

    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectionReasonInput by remember { mutableStateOf("") }

    LaunchedEffect(prescription.id) {
        if (editableItems.isEmpty() && prescription.items.isNotEmpty()) {
            editableItems.clear()
            editableItems.addAll(prescription.parsedItems())
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val isWideLayout = maxWidth >= 720.dp

        if (isWideLayout) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ReviewLeftImagePane(
                        prescription = prescription,
                        aiSetupWarning = aiSetupWarning,
                        isBusy = isBusy,
                        onAnalyze = { onAnalyzePrescription(prescription) },
                        onOpenSettings = onOpenSettings
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ReviewRightFormPane(
                        prescription = prescription,
                        patientName = patientName,
                        onPatientNameChange = { patientName = it },
                        patientAge = patientAge,
                        onPatientAgeChange = { patientAge = it },
                        patientContact = patientContact,
                        onPatientContactChange = { patientContact = it },
                        doctorName = doctorName,
                        onDoctorNameChange = { doctorName = it },
                        prescriptionDate = prescriptionDate,
                        onPrescriptionDateChange = { prescriptionDate = it },
                        reviewNotes = reviewNotes,
                        onReviewNotesChange = { reviewNotes = it },
                        editableItems = editableItems,
                        catalog = catalog,
                        isBusy = isBusy,
                        onSaveDraft = {
                            onSaveDraft(
                                patientName,
                                patientAge,
                                patientContact,
                                doctorName,
                                prescriptionDate,
                                editableItems.toList(),
                                reviewNotes
                            )
                        },
                        onApprove = {
                            onApprove(
                                patientName,
                                patientAge,
                                patientContact,
                                doctorName,
                                prescriptionDate,
                                editableItems.toList(),
                                reviewNotes
                            )
                        },
                        onShowReject = { showRejectDialog = true },
                        onDelete = onDelete
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ReviewLeftImagePane(
                    prescription = prescription,
                    aiSetupWarning = aiSetupWarning,
                    isBusy = isBusy,
                    onAnalyze = { onAnalyzePrescription(prescription) },
                    onOpenSettings = onOpenSettings
                )
                ReviewRightFormPane(
                    prescription = prescription,
                    patientName = patientName,
                    onPatientNameChange = { patientName = it },
                    patientAge = patientAge,
                    onPatientAgeChange = { patientAge = it },
                    patientContact = patientContact,
                    onPatientContactChange = { patientContact = it },
                    doctorName = doctorName,
                    onDoctorNameChange = { doctorName = it },
                    prescriptionDate = prescriptionDate,
                    onPrescriptionDateChange = { prescriptionDate = it },
                    reviewNotes = reviewNotes,
                    onReviewNotesChange = { reviewNotes = it },
                    editableItems = editableItems,
                    catalog = catalog,
                    isBusy = isBusy,
                    onSaveDraft = {
                        onSaveDraft(
                            patientName,
                            patientAge,
                            patientContact,
                            doctorName,
                            prescriptionDate,
                            editableItems.toList(),
                            reviewNotes
                        )
                    },
                    onApprove = {
                        onApprove(
                            patientName,
                            patientAge,
                            patientContact,
                            doctorName,
                            prescriptionDate,
                            editableItems.toList(),
                            reviewNotes
                        )
                    },
                    onShowReject = { showRejectDialog = true },
                    onDelete = onDelete
                )
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject Prescription ${prescription.requestCode}") },
            text = {
                OutlinedTextField(
                    value = rejectionReasonInput,
                    onValueChange = { rejectionReasonInput = it },
                    label = { Text("Reason for rejection (shown to counter staff)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rejectionReasonInput.isNotBlank()) {
                            onReject(rejectionReasonInput)
                            showRejectDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReviewLeftImagePane(
    prescription: PrescriptionRequest,
    aiSetupWarning: List<String>,
    isBusy: Boolean,
    onAnalyze: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Original Prescription (${prescription.requestCode})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Uploaded by ${prescription.submittedByName} (${prescription.submittedByRole})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status = prescription.statusEnum())
            }

            PrescriptionPreviewImage(
                base64Preview = prescription.imageBase64Preview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            Button(
                onClick = onAnalyze,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analyze_prescription_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Analyze Prescription",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyze Prescription with Configured AI")
            }

            if (aiSetupWarning.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_setup_required_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Setup Required",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Provider Setup Required (No Fake Results)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        aiSetupWarning.forEach { reason ->
                            Text(
                                text = "• $reason",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configure API",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open API Configuration")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewRightFormPane(
    prescription: PrescriptionRequest,
    patientName: String,
    onPatientNameChange: (String) -> Unit,
    patientAge: String,
    onPatientAgeChange: (String) -> Unit,
    patientContact: String,
    onPatientContactChange: (String) -> Unit,
    doctorName: String,
    onDoctorNameChange: (String) -> Unit,
    prescriptionDate: String,
    onPrescriptionDateChange: (String) -> Unit,
    reviewNotes: String,
    onReviewNotesChange: (String) -> Unit,
    editableItems: androidx.compose.runtime.snapshots.SnapshotStateList<PrescriptionMedicineItem>,
    catalog: List<MedicineCatalogItem>,
    isBusy: Boolean,
    onSaveDraft: () -> Unit,
    onApprove: () -> Unit,
    onShowReject: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Extracted Prescription Details & Verification",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Safety Policy: Never guess unreadable values or missing quantities. Resolve all 'Needs Review' flags before approving.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = patientName,
                    onValueChange = onPatientNameChange,
                    label = { Text("Patient Name") },
                    modifier = Modifier.weight(1.4f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = patientAge,
                    onValueChange = onPatientAgeChange,
                    label = { Text("Age") },
                    modifier = Modifier.weight(0.6f),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = doctorName,
                    onValueChange = onDoctorNameChange,
                    label = { Text("Doctor Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = prescriptionDate,
                    onValueChange = onPrescriptionDateChange,
                    label = { Text("Rx Date") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = patientContact,
                onValueChange = onPatientContactChange,
                label = { Text("Patient Contact Number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Prescribed Medicines (${editableItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = {
                        val newItem = PrescriptionMedicineItem(
                            itemId = "line_${editableItems.size + 1}_${UUID.randomUUID().toString().take(5)}",
                            extractedMedicineName = "",
                            strength = "",
                            dosageForm = "Tablet",
                            dosageInstructions = "1-0-1 after meals",
                            frequency = "BD",
                            duration = "5 days",
                            explicitQuantity = "Needs Review",
                            needsReview = true,
                            reviewReason = "Manually added — confirm Item Master match & quantity"
                        )
                        editableItems.add(newItem)
                    },
                    modifier = Modifier.testTag("add_medicine_line_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Medicine")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Medicine Line")
                }
            }

            editableItems.forEachIndexed { idx, item ->
                MedicineLineEditorCard(
                    index = idx,
                    item = item,
                    catalog = catalog,
                    onUpdate = { updated -> editableItems[idx] = updated },
                    onRemove = { editableItems.removeAt(idx) }
                )
            }

            OutlinedTextField(
                value = reviewNotes,
                onValueChange = onReviewNotesChange,
                label = { Text("Pharmacist Verification Notes") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveDraft,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_review_draft_button")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = "Save Draft")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Draft")
                }
                Button(
                    onClick = onApprove,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("approve_prescription_button")
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Approve")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Approve & Ready")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onShowReject,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.Cancel, contentDescription = "Reject")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject Request")
                }

                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Request")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineLineEditorCard(
    index: Int,
    item: PrescriptionMedicineItem,
    catalog: List<MedicineCatalogItem>,
    onUpdate: (PrescriptionMedicineItem) -> Unit,
    onRemove: () -> Unit
) {
    val evaluation = remember(item.extractedMedicineName, item.strength, catalog) {
        MedicineCatalogueMatcher.matchMedicineItem(item, catalog)
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.needsReview) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Line #${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (item.needsReview) {
                        NeedsReviewPill(text = "Needs Review")
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove medicine line"
                    )
                }
            }

            if (item.reviewReason.isNotBlank()) {
                Text(
                    text = "Review Flag: ${item.reviewReason}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.extractedMedicineName,
                    onValueChange = { newName ->
                        val updated = item.copy(extractedMedicineName = newName)
                        onUpdate(MedicineCatalogueMatcher.matchMedicineItem(updated, catalog).updatedItem)
                    },
                    label = { Text("Medicine Name") },
                    modifier = Modifier.weight(1.4f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = item.strength,
                    onValueChange = { newStrength ->
                        val updated = item.copy(strength = newStrength)
                        onUpdate(MedicineCatalogueMatcher.matchMedicineItem(updated, catalog).updatedItem)
                    },
                    label = { Text("Strength") },
                    modifier = Modifier.weight(0.8f),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.dosageForm,
                    onValueChange = { onUpdate(item.copy(dosageForm = it)) },
                    label = { Text("Form (Tab/Cap/Syr)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = item.explicitQuantity,
                    onValueChange = { onUpdate(item.copy(explicitQuantity = it)) },
                    label = { Text("Written Qty") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.dosageInstructions,
                    onValueChange = { onUpdate(item.copy(dosageInstructions = it)) },
                    label = { Text("Dosage (e.g. 1-0-1)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = item.frequency,
                    onValueChange = { onUpdate(item.copy(frequency = it)) },
                    label = { Text("Freq") },
                    modifier = Modifier.weight(0.6f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = item.duration,
                    onValueChange = { onUpdate(item.copy(duration = it)) },
                    label = { Text("Duration") },
                    modifier = Modifier.weight(0.8f),
                    singleLine = true
                )
            }

            // Item Master Catalogue Match Selector
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = if (item.matchedItemName.isNotBlank()) {
                        "${item.matchedItemName} (${item.matchedPackSize} • MRP ₹${item.verifiedMrp} • GST ${item.verifiedGstPercent}%)"
                    } else {
                        "Select verified item from Pharmacy Item Master (${evaluation.candidates.size} candidate matches)"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Verified Pharmacy Item Master Match (${item.matchStatus})") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    val options = if (evaluation.candidates.isNotEmpty()) {
                        evaluation.candidates.map { it.catalogItem }
                    } else {
                        catalog
                    }
                    if (options.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Item Master is empty — import CSV in Catalogue tab") },
                            onClick = { dropdownExpanded = false }
                        )
                    } else {
                        options.forEach { catItem ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "${catItem.itemName} [${catItem.itemCode}]",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Unit: ${catItem.unit} | Pack: ${catItem.packSize} | MRP: ₹${catItem.mrp} | GST: ${catItem.gstPercent}%",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                },
                                onClick = {
                                    onUpdate(MedicineCatalogueMatcher.applyManualSelection(item, catItem))
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = !item.needsReview,
                    onCheckedChange = { verified ->
                        onUpdate(
                            item.copy(
                                needsReview = !verified,
                                reviewReason = if (verified) "" else "Flagged for pharmacist review"
                            )
                        )
                    }
                )
                Text(
                    text = "Pharmacist Verified (Uncheck if line still Needs Review)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
