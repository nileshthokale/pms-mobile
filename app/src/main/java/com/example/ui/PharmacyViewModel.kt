package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiAnalysisResult
import com.example.data.AiProviderConfig
import com.example.data.MedicineCatalogueMatcher
import com.example.data.PharmacyRepository
import com.example.data.PrescriptionAnalysisProvider
import com.example.model.AiAuthMethod
import com.example.model.AiRequestFormat
import com.example.model.AuditLogEntry
import com.example.model.MedicineCatalogItem
import com.example.model.PrescriptionMedicineItem
import com.example.model.PrescriptionRequest
import com.example.model.StaffAccount
import com.example.model.UserProfile
import com.example.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class BridgeScreen(val title: String) {
    DASHBOARD("Dashboard"),
    INCOMING("Incoming"),
    REVIEW("Review"),
    CATALOGUE("Catalogue"),
    APPROVED("Approved"),
    HISTORY("History"),
    SETTINGS("Settings")
}

class PharmacyViewModel(
    private val repository: PharmacyRepository,
    private val aiService: PrescriptionAnalysisProvider,
    val currentUserId: String,
    val userEmail: String,
    val userDisplayName: String
) : ViewModel() {

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Error observing profile", err)
            emit(UiState.Error(err.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val prescriptionsState: StateFlow<UiState<List<PrescriptionRequest>>> =
        repository.observePrescriptions(currentUserId)
            .map<List<PrescriptionRequest>, UiState<List<PrescriptionRequest>>> { UiState.Success(it) }
            .catch { err ->
                Log.w(TAG, "Error observing prescriptions", err)
                emit(UiState.Error(err.message ?: "Failed to load prescriptions"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val catalogState: StateFlow<UiState<List<MedicineCatalogItem>>> =
        repository.observeCatalogItems(currentUserId)
            .map<List<MedicineCatalogItem>, UiState<List<MedicineCatalogItem>>> { UiState.Success(it) }
            .catch { err ->
                Log.w(TAG, "Error observing catalog", err)
                emit(UiState.Error(err.message ?: "Failed to load medicine catalogue"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val staffState: StateFlow<UiState<List<StaffAccount>>> =
        repository.observeStaffAccounts(currentUserId)
            .map<List<StaffAccount>, UiState<List<StaffAccount>>> { UiState.Success(it) }
            .catch { err ->
                Log.w(TAG, "Error observing staff", err)
                emit(UiState.Error(err.message ?: "Failed to load staff accounts"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val auditLogsState: StateFlow<UiState<List<AuditLogEntry>>> =
        repository.observeAuditLogs(currentUserId)
            .map<List<AuditLogEntry>, UiState<List<AuditLogEntry>>> { UiState.Success(it) }
            .catch { err ->
                Log.w(TAG, "Error observing audit logs", err)
                emit(UiState.Error(err.message ?: "Failed to load audit history"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _currentScreen = MutableStateFlow(BridgeScreen.DASHBOARD)
    val currentScreen: StateFlow<BridgeScreen> = _currentScreen.asStateFlow()

    private val _selectedPrescriptionId = MutableStateFlow<String?>(null)
    val selectedPrescriptionId: StateFlow<String?> = _selectedPrescriptionId.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _aiSetupWarning = MutableStateFlow<List<String>>(emptyList())
    val aiSetupWarning: StateFlow<List<String>> = _aiSetupWarning.asStateFlow()

    private val _isDarkThemeOverride = MutableStateFlow<Boolean?>(null)
    val isDarkThemeOverride: StateFlow<Boolean?> = _isDarkThemeOverride.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(userEmail, userDisplayName)
        }
    }

    fun navigateTo(screen: BridgeScreen, prescriptionId: String? = null) {
        if (prescriptionId != null) {
            _selectedPrescriptionId.value = prescriptionId
        }
        _currentScreen.value = screen
    }

    fun selectPrescriptionForReview(prescriptionId: String) {
        _selectedPrescriptionId.value = prescriptionId
        _currentScreen.value = BridgeScreen.REVIEW
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun dismissAiSetupWarning() {
        _aiSetupWarning.value = emptyList()
    }

    fun toggleDarkTheme(currentlyDark: Boolean) {
        _isDarkThemeOverride.value = !currentlyDark
    }

    fun switchActiveRole(newRole: UserRole) {
        val currentProfile = (profileState.value as? UiState.Success)?.data ?: UserProfile(
            userId = currentUserId,
            email = userEmail,
            displayName = userDisplayName
        )
        viewModelScope.launch {
            repository.updateUserProfileSettings(
                pharmacyName = currentProfile.pharmacyName,
                activeRole = newRole,
                retentionDays = currentProfile.retentionDays.toInt(),
                aiEndpoint = currentProfile.aiEndpoint,
                aiModelName = currentProfile.aiModelName,
                aiAuthMethod = AiAuthMethod.entries.firstOrNull { it.name == currentProfile.aiAuthMethod }
                    ?: AiAuthMethod.BEARER_HEADER,
                aiRequestFormat = AiRequestFormat.entries.firstOrNull { it.name == currentProfile.aiRequestFormat }
                    ?: AiRequestFormat.OPENAI_VISION
            ).onSuccess {
                _bannerMessage.value = "Switched active role to ${newRole.label}"
                if (newRole == UserRole.COUNTER_STAFF &&
                    _currentScreen.value in setOf(BridgeScreen.REVIEW, BridgeScreen.CATALOGUE, BridgeScreen.SETTINGS)
                ) {
                    _currentScreen.value = BridgeScreen.INCOMING
                }
            }.onFailure {
                _bannerMessage.value = "Failed to switch role: ${it.localizedMessage}"
            }
        }
    }

    fun submitPrescription(
        submittedByRole: UserRole,
        submittedByName: String,
        patientName: String,
        patientAge: String,
        patientContact: String,
        staffNotes: String,
        imageLocalPath: String,
        imageBase64Preview: String,
        onSubmitted: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.submitPrescriptionRequest(
                submittedByRole = submittedByRole,
                submittedByName = submittedByName,
                patientName = patientName,
                patientAge = patientAge,
                patientContact = patientContact,
                staffNotes = staffNotes,
                imageLocalPath = imageLocalPath,
                imageBase64Preview = imageBase64Preview
            )
            _isBusy.value = false
            result.onSuccess { rxId ->
                _bannerMessage.value = "Prescription submitted to queue."
                _selectedPrescriptionId.value = rxId
                onSubmitted(rxId)
            }.onFailure { err ->
                _bannerMessage.value = "Upload failed: ${err.localizedMessage}"
            }
        }
    }

    fun analyzePrescriptionWithConfiguredAi(prescription: PrescriptionRequest) {
        val profile = (profileState.value as? UiState.Success)?.data
        val catalog = (catalogState.value as? UiState.Success)?.data.orEmpty()
        val config = AiProviderConfig.fromEnvironmentAndProfile(profile)

        if (!config.isConfigured()) {
            _aiSetupWarning.value = config.missingConfigReasons()
            _bannerMessage.value = "AI API is not configured. See setup instructions."
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            _aiSetupWarning.value = emptyList()
            val actorName = profile?.displayName?.ifBlank { userDisplayName } ?: "Admin"
            repository.markPrescriptionAnalyzing(prescription.id, actorName)

            val imageBase64 = prescription.imageBase64Preview.ifBlank {
                generateSyntheticPrescriptionBase64()
            }

            when (val aiResult = aiService.analyzePrescriptionImage(imageBase64, config, catalog)) {
                is AiAnalysisResult.Success -> {
                    repository.saveExtractionResult(
                        prescriptionId = prescription.id,
                        requestCode = prescription.requestCode,
                        extraction = aiResult.extraction,
                        actorName = actorName
                    ).onSuccess {
                        _bannerMessage.value =
                            "Extracted ${aiResult.extraction.items.size} item(s). Please verify all fields before approval."
                    }.onFailure { err ->
                        _bannerMessage.value = "Failed to save extraction: ${err.localizedMessage}"
                    }
                }
                is AiAnalysisResult.NotConfigured -> {
                    _aiSetupWarning.value = aiResult.missingItems
                }
                is AiAnalysisResult.Error -> {
                    _bannerMessage.value = aiResult.message
                }
            }
            _isBusy.value = false
        }
    }

    fun saveReviewDraft(
        prescriptionId: String,
        patientName: String,
        patientAge: String,
        patientContact: String,
        doctorName: String,
        prescriptionDate: String,
        items: List<PrescriptionMedicineItem>,
        reviewNotes: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateReviewedPrescriptionFields(
                prescriptionId = prescriptionId,
                patientName = patientName,
                patientAge = patientAge,
                patientContact = patientContact,
                doctorName = doctorName,
                prescriptionDate = prescriptionDate,
                items = items,
                reviewNotes = reviewNotes
            ).onSuccess {
                _bannerMessage.value = "Draft saved (Needs Review)."
            }.onFailure { err ->
                _bannerMessage.value = "Save failed: ${err.localizedMessage}"
            }
            _isBusy.value = false
        }
    }

    fun approvePrescription(
        prescription: PrescriptionRequest,
        patientName: String,
        patientAge: String,
        patientContact: String,
        doctorName: String,
        prescriptionDate: String,
        items: List<PrescriptionMedicineItem>,
        reviewNotes: String
    ) {
        val profile = (profileState.value as? UiState.Success)?.data
        val reviewer = profile?.displayName?.ifBlank { userDisplayName } ?: "Pharmacist Admin"
        viewModelScope.launch {
            _isBusy.value = true
            repository.approvePrescription(
                prescriptionId = prescription.id,
                requestCode = prescription.requestCode,
                patientName = patientName,
                patientAge = patientAge,
                patientContact = patientContact,
                doctorName = doctorName,
                prescriptionDate = prescriptionDate,
                items = items,
                reviewedBy = reviewer,
                reviewNotes = reviewNotes
            ).onSuccess {
                _bannerMessage.value = "${prescription.requestCode} Approved & Marked Ready for Counter Sale!"
                _currentScreen.value = BridgeScreen.APPROVED
            }.onFailure { err ->
                _bannerMessage.value = err.message ?: "Approval validation failed"
            }
            _isBusy.value = false
        }
    }

    fun rejectPrescription(
        prescription: PrescriptionRequest,
        rejectionReason: String
    ) {
        val profile = (profileState.value as? UiState.Success)?.data
        val reviewer = profile?.displayName?.ifBlank { userDisplayName } ?: "Pharmacist Admin"
        viewModelScope.launch {
            _isBusy.value = true
            repository.rejectPrescription(
                prescriptionId = prescription.id,
                requestCode = prescription.requestCode,
                reviewedBy = reviewer,
                rejectionReason = rejectionReason
            ).onSuccess {
                _bannerMessage.value = "${prescription.requestCode} marked as Rejected."
            }.onFailure { err ->
                _bannerMessage.value = err.message ?: "Failed to reject prescription"
            }
            _isBusy.value = false
        }
    }

    fun deletePrescription(prescription: PrescriptionRequest) {
        val profile = (profileState.value as? UiState.Success)?.data
        val actor = profile?.displayName?.ifBlank { userDisplayName } ?: "Admin"
        viewModelScope.launch {
            _isBusy.value = true
            repository.deletePrescription(
                prescriptionId = prescription.id,
                requestCode = prescription.requestCode,
                actorName = actor
            ).onSuccess {
                if (_selectedPrescriptionId.value == prescription.id) {
                    _selectedPrescriptionId.value = null
                }
                _bannerMessage.value = "Deleted ${prescription.requestCode}."
            }.onFailure { err ->
                _bannerMessage.value = "Delete failed: ${err.localizedMessage}"
            }
            _isBusy.value = false
        }
    }

    fun claimPrescriptionForCounter(prescription: PrescriptionRequest, counterTerminalId: String) {
        val profile = (profileState.value as? UiState.Success)?.data
        val actor = profile?.displayName?.ifBlank { userDisplayName } ?: "Admin"
        viewModelScope.launch {
            _isBusy.value = true
            repository.claimPrescriptionForCounter(
                prescriptionId = prescription.id,
                counterTerminalId = counterTerminalId,
                actorName = actor
            ).onSuccess { token ->
                _bannerMessage.value = "Locked for $counterTerminalId (Claim Token: $token)"
            }.onFailure { err ->
                _bannerMessage.value = err.message ?: "Claim failed"
            }
            _isBusy.value = false
        }
    }

    fun markPrescriptionImported(
        prescription: PrescriptionRequest,
        counterTerminalId: String,
        completeImmediately: Boolean
    ) {
        val profile = (profileState.value as? UiState.Success)?.data
        val actor = profile?.displayName?.ifBlank { userDisplayName } ?: "Admin"
        viewModelScope.launch {
            _isBusy.value = true
            repository.markPrescriptionImported(
                prescriptionId = prescription.id,
                counterTerminalId = counterTerminalId.ifBlank { prescription.claimedByCounterId.ifBlank { "COUNTER-1" } },
                claimToken = prescription.claimToken,
                actorName = actor,
                completeImmediately = completeImmediately
            ).onSuccess {
                _bannerMessage.value =
                    if (completeImmediately) "${prescription.requestCode} marked COMPLETED."
                    else "${prescription.requestCode} marked IMPORTED."
            }.onFailure { err ->
                _bannerMessage.value = err.message ?: "Import transition failed"
            }
            _isBusy.value = false
        }
    }

    fun markPrescriptionCompleted(prescription: PrescriptionRequest) {
        val profile = (profileState.value as? UiState.Success)?.data
        val actor = profile?.displayName?.ifBlank { userDisplayName } ?: "Admin"
        viewModelScope.launch {
            _isBusy.value = true
            repository.markPrescriptionCompleted(prescription.id, actor).onSuccess {
                _bannerMessage.value = "${prescription.requestCode} finalized (COMPLETED)."
            }.onFailure { err ->
                _bannerMessage.value = err.message ?: "Failed to complete"
            }
            _isBusy.value = false
        }
    }

    fun recordJsonExportAudit(prescription: PrescriptionRequest) {
        val profile = (profileState.value as? UiState.Success)?.data
        val actor = profile?.displayName?.ifBlank { userDisplayName } ?: "Admin"
        viewModelScope.launch {
            repository.recordAuditLog(
                prescriptionId = prescription.id,
                requestCode = prescription.requestCode,
                action = "EXPORTED_JSON",
                actorName = actor,
                actorRole = UserRole.ADMIN,
                details = "Exported structured Counter Sale JSON payload."
            )
        }
    }

    fun importCsvCatalogue(csvText: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val parseResult = MedicineCatalogueMatcher.parseItemMasterCsv(csvText, currentUserId)
            parseResult.fold(
                onSuccess = { items ->
                    repository.importCatalogItems(items).onSuccess { count ->
                        _bannerMessage.value = "Imported $count verified medicine item(s) into Item Master."
                    }.onFailure { err ->
                        _bannerMessage.value = "Firestore import failed: ${err.localizedMessage}"
                    }
                },
                onFailure = { err ->
                    _bannerMessage.value = "CSV validation error: ${err.message}"
                }
            )
            _isBusy.value = false
        }
    }

    fun deleteCatalogItem(itemId: String) {
        viewModelScope.launch {
            repository.deleteCatalogItem(itemId).onFailure {
                _bannerMessage.value = "Failed to delete item: ${it.localizedMessage}"
            }
        }
    }

    fun addStaffMember(name: String, email: String, role: UserRole, terminalId: String) {
        viewModelScope.launch {
            repository.addStaffAccount(name, email, role, terminalId).onSuccess {
                _bannerMessage.value = "Added $name (${role.label})"
            }.onFailure {
                _bannerMessage.value = "Could not add staff: ${it.message}"
            }
        }
    }

    fun toggleStaffMember(staff: StaffAccount) {
        viewModelScope.launch {
            repository.toggleStaffActive(staff)
        }
    }

    fun deleteStaffMember(staffId: String) {
        viewModelScope.launch {
            repository.deleteStaffAccount(staffId)
        }
    }

    fun saveSettings(
        pharmacyName: String,
        activeRole: UserRole,
        retentionDays: Int,
        aiEndpoint: String,
        aiModelName: String,
        aiAuthMethod: AiAuthMethod,
        aiRequestFormat: AiRequestFormat
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateUserProfileSettings(
                pharmacyName = pharmacyName,
                activeRole = activeRole,
                retentionDays = retentionDays,
                aiEndpoint = aiEndpoint,
                aiModelName = aiModelName,
                aiAuthMethod = aiAuthMethod,
                aiRequestFormat = aiRequestFormat
            ).onSuccess {
                _bannerMessage.value = "Saved pharmacy & AI provider settings."
            }.onFailure {
                _bannerMessage.value = "Failed to save settings: ${it.localizedMessage}"
            }
            _isBusy.value = false
        }
    }

    fun purgeExpiredRecords(retentionDays: Int) {
        val profile = (profileState.value as? UiState.Success)?.data
        val actor = profile?.displayName?.ifBlank { userDisplayName } ?: "Admin"
        viewModelScope.launch {
            _isBusy.value = true
            repository.purgeExpiredPrescriptions(retentionDays, actor).onSuccess { count ->
                _bannerMessage.value = "Retention policy executed: purged $count expired record(s)."
            }.onFailure {
                _bannerMessage.value = "Retention purge failed: ${it.localizedMessage}"
            }
            _isBusy.value = false
        }
    }

    companion object {
        private const val TAG = "PharmacyVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        const val SAMPLE_ITEM_MASTER_CSV = """item_code,item_name,generic_name,unit,pack_size,mrp,gst_percent,hsn_code
ITM-1001,Dolo 650 Tablet,Paracetamol 650mg,Strip,15 Tabs,33.60,12,30049099
ITM-1002,Augmentin 625 Duo Tablet,Amoxicillin 500mg + Clavulanic Acid 125mg,Strip,10 Tabs,204.50,12,30041000
ITM-1003,Augmentin DDS Suspension,Amoxicillin 400mg + Clavulanic Acid 57mg,Bottle,30ml,162.00,12,30041000
ITM-1004,Pan D Capsule PR,Pantoprazole 40mg + Domperidone 30mg,Strip,15 Caps,210.00,12,30049034
ITM-1005,Azithral 500 Tablet,Azithromycin 500mg,Strip,5 Tabs,132.40,12,30042061
ITM-1006,Shelcal 500 Tablet,Calcium 500mg + Vitamin D3 250 IU,Strip,15 Tabs,144.00,12,30049099
ITM-1007,Ascoril LS Syrup,Levosalbutamol + Ambroxol + Guaiphenesin,Bottle,100ml,118.00,12,30049099"""

        /**
         * Generates a synthetic test prescription pad image (no real patient data)
         * so operators on emulators without physical camera photos can upload and review a visual slip.
         */
        fun generateSyntheticPrescriptionBase64(
            patientLabel: String = "Synthetic Test Slip (No Real Patient Data)",
            rxLines: List<String> = listOf(
                "Rx:",
                "1) Tab. Dolo 650mg — 1-0-1 x 5 days (Qty: 15)",
                "2) Tab. Augmentin 625 Duo — 1-0-1 x 5 days (Qty: 10)",
                "3) Cap. Pan D — 1-0-0 B/F x 10 days (Qty: ?)"
            )
        ): String {
            return try {
                val width = 520
                val height = 380
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.parseColor("#FFFDF7"))

                val borderPaint = Paint().apply {
                    color = Color.parseColor("#0F766E")
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
                canvas.drawRect(12f, 12f, (width - 12).toFloat(), (height - 12).toFloat(), borderPaint)

                val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#0F766E")
                    textSize = 20f
                    isFakeBoldText = true
                }
                canvas.drawText("SYNTHETIC TEST PRESCRIPTION SLIP", 28f, 48f, headerPaint)

                val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#475569")
                    textSize = 15f
                }
                canvas.drawText("Dr. S. Kulkarni (Reg. #SYN-000) | Date: 2026-10-20", 28f, 76f, subPaint)
                canvas.drawText(patientLabel, 28f, 102f, subPaint)

                val linePaint = Paint().apply {
                    color = Color.parseColor("#CBD5E1")
                    strokeWidth = 2f
                }
                canvas.drawLine(24f, 118f, (width - 24).toFloat(), 118f, linePaint)

                val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1E293B")
                    textSize = 17f
                }
                var y = 156f
                for (line in rxLines) {
                    canvas.drawText(line, 32f, y, bodyPaint)
                    y += 38f
                }

                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            } catch (e: Exception) {
                ""
            }
        }
    }
}
