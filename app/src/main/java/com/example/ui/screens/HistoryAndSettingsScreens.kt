package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AiProviderConfig
import com.example.model.AiAuthMethod
import com.example.model.AiRequestFormat
import com.example.model.AuditLogEntry
import com.example.model.PrescriptionRequest
import com.example.model.StaffAccount
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.ui.BridgeScreen
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun MoreScreen(
    approvedCount: Int,
    catalogCount: Int,
    historyCount: Int,
    onNavigate: (BridgeScreen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "More Tools & Records",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Open approved prescriptions for Counter Sale, manage the medicine catalogue, or adjust pharmacy settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        MoreDestinationCard(
            title = "Ready for Counter Sale",
            subtitle = "View approved prescriptions ($approvedCount ready) and export JSON for the desktop Counter Sale screen.",
            icon = Icons.Default.AssignmentTurnedIn,
            tag = "more_option_approved",
            onClick = { onNavigate(BridgeScreen.APPROVED) }
        )

        MoreDestinationCard(
            title = "Items / Item Master",
            subtitle = "Search and manage the medicine catalogue ($catalogCount items imported from CSV).",
            icon = Icons.Default.Inventory2,
            tag = "more_option_catalogue",
            onClick = { onNavigate(BridgeScreen.CATALOGUE) }
        )

        MoreDestinationCard(
            title = "History",
            subtitle = "View previous prescriptions and audit activity ($historyCount total requests).",
            icon = Icons.Default.History,
            tag = "more_option_history",
            onClick = { onNavigate(BridgeScreen.HISTORY) }
        )

        MoreDestinationCard(
            title = "Settings",
            subtitle = "Manage AI configuration, pharmacy details, staff/terminals, retention and desktop integration.",
            icon = Icons.Default.Settings,
            tag = "more_option_settings",
            onClick = { onNavigate(BridgeScreen.SETTINGS) }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MoreDestinationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HistoryScreen(
    prescriptions: List<PrescriptionRequest>,
    auditLogs: List<AuditLogEntry>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Prescription History & Audit Trail",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Immutable log of uploads, AI extractions, approvals, rejections, JSON exports, counter claim locks, and imports.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Text(
                text = "Audit Log Events (${auditLogs.size})",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (auditLogs.isEmpty()) {
            item {
                Text(
                    text = "No audit events recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(auditLogs, key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${log.action} • ${log.requestCode}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatTimestamp(log.createdAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Actor: ${log.actorName} (${log.actorRole})",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = log.details,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "All Requests Snapshot (${prescriptions.size})",
                style = MaterialTheme.typography.titleLarge
            )
        }

        items(prescriptions, key = { "hist_${it.id}" }) { rx ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                            text = "${rx.requestCode} — ${rx.patientName.ifBlank { "Unspecified" }}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "Created: ${formatTimestamp(rx.createdAt)} • Updated: ${formatTimestamp(rx.updatedAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusBadge(status = rx.statusEnum())
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun SettingsScreen(
    profile: UserProfile?,
    staffList: List<StaffAccount>,
    isBusy: Boolean,
    onSaveSettings: (
        pharmacyName: String,
        activeRole: UserRole,
        retentionDays: Int,
        aiEndpoint: String,
        aiModelName: String,
        aiAuthMethod: AiAuthMethod,
        aiRequestFormat: AiRequestFormat
    ) -> Unit,
    onAddStaff: (name: String, email: String, role: UserRole, terminalId: String) -> Unit,
    onToggleStaff: (StaffAccount) -> Unit,
    onDeleteStaff: (String) -> Unit,
    onPurgeRetention: (Int) -> Unit
) {
    val effectiveConfig = remember(profile) { AiProviderConfig.fromEnvironmentAndProfile(profile) }

    var pharmacyName by remember(profile) { mutableStateOf(profile?.pharmacyName ?: "City Care Pharmacy") }
    var activeRole by remember(profile) {
        mutableStateOf(
            UserRole.entries.firstOrNull { it.name == profile?.activeRole } ?: UserRole.ADMIN
        )
    }
    var retentionDaysText by remember(profile) {
        mutableStateOf((profile?.retentionDays ?: 30L).toString())
    }
    var aiEndpoint by remember(profile) {
        mutableStateOf(profile?.aiEndpoint?.ifBlank { effectiveConfig.endpointUrl } ?: "")
    }
    var aiModelName by remember(profile) {
        mutableStateOf(profile?.aiModelName?.ifBlank { effectiveConfig.modelName } ?: "")
    }
    var aiAuthMethod by remember(profile) {
        mutableStateOf(effectiveConfig.authMethod)
    }
    var aiRequestFormat by remember(profile) {
        mutableStateOf(effectiveConfig.requestFormat)
    }

    var newStaffName by remember { mutableStateOf("") }
    var newStaffEmail by remember { mutableStateOf("") }
    var newStaffTerminal by remember { mutableStateOf("COUNTER-1") }
    var newStaffRole by remember { mutableStateOf(UserRole.COUNTER_STAFF) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Pharmacy Workspace & Role Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Pharmacy Workspace & Active Role",
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = pharmacyName,
                    onValueChange = { pharmacyName = it },
                    label = { Text("Pharmacy Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = "Current Operator Mode",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserRole.entries.forEach { role ->
                        FilterChip(
                            selected = activeRole == role,
                            onClick = { activeRole = role },
                            label = { Text(role.label) }
                        )
                    }
                }
            }
        }

        // 2. Provider-Agnostic AI API Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "AI Provider Configuration (Provider-Agnostic)",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Secrets Policy: Store your AI_API_KEY in the AI Studio Secrets panel (.env). Configure your provider's endpoint URL, model name, authentication header, and payload format below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = if (effectiveConfig.isConfigured()) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (effectiveConfig.isConfigured()) {
                            "AI API Status: Configured (${effectiveConfig.modelName})"
                        } else {
                            "AI API Status: Not Configured — ${effectiveConfig.missingConfigReasons().joinToString("; ")}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedTextField(
                    value = aiEndpoint,
                    onValueChange = { aiEndpoint = it },
                    label = { Text("AI API Endpoint URL") },
                    placeholder = { Text("https://api.openai.com/v1/chat/completions") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = aiModelName,
                    onValueChange = { aiModelName = it },
                    label = { Text("Model Name") },
                    placeholder = { Text("gpt-4o / claude-3-5-sonnet / gemini-3.5-flash") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "Authentication Method",
                    style = MaterialTheme.typography.labelLarge
                )
                AiAuthMethod.entries.forEach { method ->
                    FilterChip(
                        selected = aiAuthMethod == method,
                        onClick = { aiAuthMethod = method },
                        label = { Text(method.label) }
                    )
                }

                Text(
                    text = "Request Payload Format",
                    style = MaterialTheme.typography.labelLarge
                )
                AiRequestFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = aiRequestFormat == fmt,
                        onClick = { aiRequestFormat = fmt },
                        label = { Text(fmt.label) }
                    )
                }
            }
        }

        // 3. Retention Policy & Data Purge
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Privacy & Prescription Retention Policy",
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = retentionDaysText,
                    onValueChange = { retentionDaysText = it.filter { c -> c.isDigit() } },
                    label = { Text("Retention Period (Days)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onSaveSettings(
                                pharmacyName,
                                activeRole,
                                retentionDaysText.toIntOrNull() ?: 30,
                                aiEndpoint,
                                aiModelName,
                                aiAuthMethod,
                                aiRequestFormat
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_settings_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save All Settings")
                    }
                    OutlinedButton(
                        onClick = {
                            onPurgeRetention(retentionDaysText.toIntOrNull() ?: 30)
                        },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.CleaningServices, contentDescription = "Purge")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Purge Expired Now")
                    }
                }
            }
        }

        // 4. Counter Staff Accounts Management
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Manage Counter Staff & Terminals (${staffList.size})",
                    style = MaterialTheme.typography.titleLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newStaffName,
                        onValueChange = { newStaffName = it },
                        label = { Text("Operator Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newStaffTerminal,
                        onValueChange = { newStaffTerminal = it },
                        label = { Text("Terminal ID") },
                        modifier = Modifier.weight(0.8f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = newStaffEmail,
                    onValueChange = { newStaffEmail = it },
                    label = { Text("Operator Email / Identifier") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UserRole.entries.forEach { r ->
                            FilterChip(
                                selected = newStaffRole == r,
                                onClick = { newStaffRole = r },
                                label = { Text(r.label) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (newStaffName.isNotBlank()) {
                                onAddStaff(newStaffName, newStaffEmail, newStaffRole, newStaffTerminal)
                                newStaffName = ""
                                newStaffEmail = ""
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Add Staff")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                staffList.forEach { staff ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${staff.staffName} (${staff.role})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Terminal: ${staff.counterTerminalId} • ${staff.staffEmail.ifBlank { "No email" }}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Switch(
                                checked = staff.isActive,
                                onCheckedChange = { onToggleStaff(staff) }
                            )
                            IconButton(onClick = { onDeleteStaff(staff.id) }) {
                                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete staff")
                            }
                        }
                    }
                }
            }
        }

        // 5. Desktop Python/PySide6 Integration Contract Documentation
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Python / PySide6 Desktop Integration Contract",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Your existing Python/PySide6/SQLite Counter Sale app remains unchanged. When building the desktop connector, use these state transitions and fields:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = """
1. State Machine:
   PENDING -> ANALYZING -> NEEDS_REVIEW -> APPROVED -> IMPORTED -> COMPLETED
2. Multi-Counter Claim Lock:
   Acquire claimToken + claimExpiresAtEpochMs (5 min TTL) on APPROVED records with readyForCounterSale == true.
3. Duplicate-Import Guard:
   Reject import if status in [IMPORTED, COMPLETED] or claimToken mismatch.
                        """.trimIndent(),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}
