package com.example.data

import com.example.model.AiAuthMethod
import com.example.model.AiRequestFormat
import com.example.model.AuditLogEntry
import com.example.model.MedicineCatalogItem
import com.example.model.PrescriptionMedicineItem
import com.example.model.PrescriptionRequest
import com.example.model.PrescriptionStatus
import com.example.model.StaffAccount
import com.example.model.UserProfile
import com.example.model.UserRole
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PharmacyRepository(
    private val dao: PharmacyDao,
    private val workspaceUserId: String = DEFAULT_WORKSPACE_ID
) {

    fun observeUserProfile(userId: String = workspaceUserId): Flow<UserProfile?> =
        dao.observeUserProfile(userId)

    fun observePrescriptions(userId: String = workspaceUserId): Flow<List<PrescriptionRequest>> =
        dao.observePrescriptions(userId)

    fun observeCatalogItems(userId: String = workspaceUserId): Flow<List<MedicineCatalogItem>> =
        dao.observeCatalogItems(userId)

    fun observeStaffAccounts(userId: String = workspaceUserId): Flow<List<StaffAccount>> =
        dao.observeStaffAccounts(userId)

    fun observeAuditLogs(userId: String = workspaceUserId): Flow<List<AuditLogEntry>> =
        dao.observeAuditLogs(userId)

    // --- Profile Management ---

    suspend fun ensureUserProfile(
        email: String,
        displayName: String
    ): Result<UserProfile> = runCatching {
        val existing = dao.getUserProfile(workspaceUserId)
        if (existing != null) {
            existing
        } else {
            val now = System.currentTimeMillis()
            val created = UserProfile(
                id = workspaceUserId,
                userId = workspaceUserId,
                email = email.ifBlank { "admin@citycarepharmacy.local" }.take(200),
                displayName = displayName.ifBlank { "Chief Pharmacist" }.take(120),
                pharmacyName = "City Care Pharmacy",
                activeRole = UserRole.ADMIN.name,
                retentionDays = 30L,
                aiEndpoint = "",
                aiModelName = "",
                aiAuthMethod = AiAuthMethod.BEARER_HEADER.name,
                aiRequestFormat = AiRequestFormat.OPENAI_VISION.name,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            dao.upsertUserProfile(created)
            created
        }
    }

    suspend fun updateUserProfileSettings(
        pharmacyName: String,
        activeRole: UserRole,
        retentionDays: Int,
        aiEndpoint: String,
        aiModelName: String,
        aiAuthMethod: AiAuthMethod,
        aiRequestFormat: AiRequestFormat
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val existing = dao.getUserProfile(workspaceUserId) ?: UserProfile(
            id = workspaceUserId,
            userId = workspaceUserId,
            createdAtEpochMs = now
        )
        val updated = existing.copy(
            pharmacyName = pharmacyName.trim().ifBlank { "City Care Pharmacy" }.take(150),
            activeRole = activeRole.name,
            retentionDays = retentionDays.coerceIn(1, 3650).toLong(),
            aiEndpoint = aiEndpoint.trim().take(500),
            aiModelName = aiModelName.trim().take(120),
            aiAuthMethod = aiAuthMethod.name,
            aiRequestFormat = aiRequestFormat.name,
            updatedAtEpochMs = now
        )
        dao.upsertUserProfile(updated)
    }

    // --- Prescription Workflow Operations ---

    suspend fun submitPrescriptionRequest(
        submittedByRole: UserRole,
        submittedByName: String,
        patientName: String,
        patientAge: String,
        patientContact: String,
        staffNotes: String,
        imageLocalPath: String,
        imageBase64Preview: String
    ): Result<String> = runCatching {
        val docId = "rx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val requestCode = "RX-${(1000..9999).random()}"
        val now = System.currentTimeMillis()

        val request = PrescriptionRequest(
            id = docId,
            userId = workspaceUserId,
            requestCode = requestCode,
            status = PrescriptionStatus.PENDING.name,
            submittedByRole = submittedByRole.name,
            submittedByName = submittedByName.ifBlank { "Counter Staff" }.take(120),
            patientName = patientName.trim().take(150),
            patientAge = patientAge.trim().take(40),
            patientContact = patientContact.trim().take(50),
            staffNotes = staffNotes.trim().take(1000),
            doctorName = "",
            prescriptionDate = "",
            imageLocalPath = imageLocalPath.take(500),
            imageBase64Preview = imageBase64Preview.take(140000),
            itemsJson = "[]",
            needsReviewCount = 0L,
            reviewedBy = "",
            reviewNotes = "",
            readyForCounterSale = false,
            claimedByCounterId = "",
            claimToken = "",
            claimExpiresAtEpochMs = 0L,
            importedAtEpochMs = 0L,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        dao.upsertPrescription(request)
        recordAuditLog(
            prescriptionId = docId,
            requestCode = requestCode,
            action = "UPLOADED",
            actorName = submittedByName.ifBlank { "Counter Staff" },
            actorRole = submittedByRole,
            details = "Prescription uploaded (${patientName.ifBlank { "Patient Unspecified" }})"
        )
        docId
    }

    suspend fun getPrescriptionById(prescriptionId: String): Result<PrescriptionRequest> = runCatching {
        val rx = dao.getPrescriptionById(prescriptionId)
            ?: throw IllegalStateException("Prescription $prescriptionId not found")
        require(rx.userId == workspaceUserId) { "Unauthorized prescription access" }
        rx
    }

    suspend fun markPrescriptionAnalyzing(
        prescriptionId: String,
        actorName: String
    ): Result<Unit> = runCatching {
        val existing = dao.getPrescriptionById(prescriptionId)
            ?: throw IllegalStateException("Prescription not found")
        require(existing.userId == workspaceUserId) { "Unauthorized prescription access" }
        require(PrescriptionStatus.isValidTransition(existing.statusEnum(), PrescriptionStatus.ANALYZING)) {
            "Invalid status transition from ${existing.status} to ANALYZING"
        }

        dao.updatePrescription(
            existing.copy(
                status = PrescriptionStatus.ANALYZING.name,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveExtractionResult(
        prescriptionId: String,
        requestCode: String,
        extraction: ValidatedPrescriptionExtraction,
        actorName: String
    ): Result<Unit> = runCatching {
        val existing = dao.getPrescriptionById(prescriptionId)
            ?: throw IllegalStateException("Prescription not found")
        require(existing.userId == workspaceUserId) { "Unauthorized prescription access" }

        val updated = existing.copy(
            status = PrescriptionStatus.NEEDS_REVIEW.name,
            patientName = extraction.patientName.ifBlank { existing.patientName }.take(150),
            patientAge = extraction.patientAge.ifBlank { existing.patientAge }.take(40),
            doctorName = extraction.doctorName.take(150),
            prescriptionDate = extraction.prescriptionDate.take(50),
            itemsJson = PrescriptionMedicineItem.serializeList(extraction.items.take(25)),
            needsReviewCount = extraction.needsReviewCount.toLong(),
            readyForCounterSale = false,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        dao.updatePrescription(updated)
        recordAuditLog(
            prescriptionId = prescriptionId,
            requestCode = requestCode,
            action = "ANALYZED",
            actorName = actorName,
            actorRole = UserRole.ADMIN,
            details = "AI extracted ${extraction.items.size} medicine line(s); ${extraction.needsReviewCount} flagged Needs Review."
        )
    }

    suspend fun updateReviewedPrescriptionFields(
        prescriptionId: String,
        patientName: String,
        patientAge: String,
        patientContact: String,
        doctorName: String,
        prescriptionDate: String,
        items: List<PrescriptionMedicineItem>,
        reviewNotes: String
    ): Result<Unit> = runCatching {
        val existing = dao.getPrescriptionById(prescriptionId)
            ?: throw IllegalStateException("Prescription not found")
        require(existing.userId == workspaceUserId) { "Unauthorized prescription access" }

        val reviewCount = items.count { it.needsReview } +
            (if (patientName.equals("Needs Review", ignoreCase = true)) 1 else 0) +
            (if (doctorName.equals("Needs Review", ignoreCase = true)) 1 else 0)

        dao.updatePrescription(
            existing.copy(
                status = PrescriptionStatus.NEEDS_REVIEW.name,
                patientName = patientName.trim().take(150),
                patientAge = patientAge.trim().take(40),
                patientContact = patientContact.trim().take(50),
                doctorName = doctorName.trim().take(150),
                prescriptionDate = prescriptionDate.trim().take(50),
                itemsJson = PrescriptionMedicineItem.serializeList(items.take(25)),
                needsReviewCount = reviewCount.toLong(),
                reviewNotes = reviewNotes.trim().take(1000),
                readyForCounterSale = false,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    /**
     * Approves a prescription after admin verification and marks it Ready for Counter Sale.
     * Enforces safety rule: cannot approve if items list is empty or any item still has needsReview = true
     * or "Needs Review" placeholders.
     */
    suspend fun approvePrescription(
        prescriptionId: String,
        requestCode: String,
        patientName: String,
        patientAge: String,
        patientContact: String,
        doctorName: String,
        prescriptionDate: String,
        items: List<PrescriptionMedicineItem>,
        reviewedBy: String,
        reviewNotes: String
    ): Result<Unit> = runCatching {
        val existing = dao.getPrescriptionById(prescriptionId)
            ?: throw IllegalStateException("Prescription not found")
        require(existing.userId == workspaceUserId) { "Unauthorized prescription access" }

        if (items.isEmpty()) {
            throw IllegalArgumentException("At least one verified medicine item is required before approval.")
        }
        val unresolvedItems = items.filter {
            it.needsReview ||
                it.extractedMedicineName.isBlank() ||
                it.extractedMedicineName.equals("Needs Review", ignoreCase = true) ||
                it.explicitQuantity.isBlank() ||
                it.explicitQuantity.equals("Needs Review", ignoreCase = true)
        }
        if (unresolvedItems.isNotEmpty()) {
            throw IllegalArgumentException(
                "Cannot approve while ${unresolvedItems.size} medicine item(s) still require review or explicit quantity confirmation."
            )
        }
        if (patientName.equals("Needs Review", ignoreCase = true) ||
            doctorName.equals("Needs Review", ignoreCase = true)
        ) {
            throw IllegalArgumentException("Please resolve 'Needs Review' in Patient Name / Doctor Name before approving.")
        }

        val cleanedItems = items.take(25).map { it.copy(needsReview = false, reviewReason = "") }
        dao.updatePrescription(
            existing.copy(
                status = PrescriptionStatus.APPROVED.name,
                patientName = patientName.trim().take(150),
                patientAge = patientAge.trim().take(40),
                patientContact = patientContact.trim().take(50),
                doctorName = doctorName.trim().take(150),
                prescriptionDate = prescriptionDate.trim().take(50),
                itemsJson = PrescriptionMedicineItem.serializeList(cleanedItems),
                needsReviewCount = 0L,
                reviewedBy = reviewedBy.ifBlank { "Pharmacist Admin" }.take(120),
                reviewNotes = reviewNotes.trim().take(1000),
                readyForCounterSale = true,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )

        recordAuditLog(
            prescriptionId = prescriptionId,
            requestCode = requestCode,
            action = "APPROVED",
            actorName = reviewedBy.ifBlank { "Pharmacist Admin" },
            actorRole = UserRole.ADMIN,
            details = "Approved ${items.size} medicine line(s) and marked Ready for Counter Sale."
        )
    }

    suspend fun rejectPrescription(
        prescriptionId: String,
        requestCode: String,
        reviewedBy: String,
        rejectionReason: String
    ): Result<Unit> = runCatching {
        require(rejectionReason.isNotBlank()) { "Please provide a rejection reason for counter staff." }
        val existing = dao.getPrescriptionById(prescriptionId)
            ?: throw IllegalStateException("Prescription not found")
        require(existing.userId == workspaceUserId) { "Unauthorized prescription access" }

        dao.updatePrescription(
            existing.copy(
                status = PrescriptionStatus.REJECTED.name,
                reviewedBy = reviewedBy.ifBlank { "Pharmacist Admin" }.take(120),
                reviewNotes = rejectionReason.trim().take(1000),
                readyForCounterSale = false,
                claimedByCounterId = "",
                claimToken = "",
                claimExpiresAtEpochMs = 0L,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
        recordAuditLog(
            prescriptionId = prescriptionId,
            requestCode = requestCode,
            action = "REJECTED",
            actorName = reviewedBy.ifBlank { "Pharmacist Admin" },
            actorRole = UserRole.ADMIN,
            details = "Rejected prescription: ${rejectionReason.take(200)}"
        )
    }

    /**
     * Acquires a recoverable counter claim lock for an APPROVED prescription so multiple counter
     * terminals cannot simultaneously import the same prescription.
     * If a previous claim lock expired (claimExpiresAtEpochMs <= nowEpochMs), safely recovers the lock.
     */
    suspend fun claimPrescriptionForCounter(
        prescriptionId: String,
        counterTerminalId: String,
        actorName: String,
        nowEpochMs: Long = System.currentTimeMillis(),
        lockDurationMs: Long = 5 * 60 * 1000L
    ): Result<String> = runCatching {
        require(counterTerminalId.isNotBlank()) { "Counter terminal ID is required to claim a prescription." }
        val rx = dao.getPrescriptionById(prescriptionId)
            ?: throw IllegalStateException("Prescription not found")
        require(rx.userId == workspaceUserId) { "Unauthorized prescription access" }

        if (rx.statusEnum() == PrescriptionStatus.IMPORTED || rx.statusEnum() == PrescriptionStatus.COMPLETED) {
            throw IllegalStateException("Duplicate import prevented: Prescription ${rx.requestCode} has already been ${rx.status}.")
        }
        if (rx.statusEnum() != PrescriptionStatus.APPROVED || !rx.readyForCounterSale) {
            throw IllegalStateException("Prescription ${rx.requestCode} must be APPROVED and Ready for Counter Sale before claiming.")
        }

        if (rx.claimedByCounterId.isNotBlank() &&
            rx.claimedByCounterId != counterTerminalId &&
            rx.claimExpiresAtEpochMs > nowEpochMs
        ) {
            throw IllegalStateException(
                "Prescription ${rx.requestCode} is currently locked by ${rx.claimedByCounterId} until lock expiry."
            )
        }

        val newClaimToken = "CLM-${UUID.randomUUID().toString().take(12).uppercase()}"
        val expiresAt = nowEpochMs + lockDurationMs

        dao.updatePrescription(
            rx.copy(
                claimedByCounterId = counterTerminalId.take(80),
                claimToken = newClaimToken,
                claimExpiresAtEpochMs = expiresAt,
                updatedAtEpochMs = nowEpochMs
            )
        )

        recordAuditLog(
            prescriptionId = prescriptionId,
            requestCode = rx.requestCode,
            action = "CLAIMED",
            actorName = actorName,
            actorRole = UserRole.ADMIN,
            details = "Claim lock acquired by $counterTerminalId (token $newClaimToken)"
        )
        newClaimToken
    }

    /**
     * Marks a claimed prescription as IMPORTED (and optionally COMPLETED), preventing duplicate imports.
     */
    suspend fun markPrescriptionImported(
        prescriptionId: String,
        counterTerminalId: String,
        claimToken: String,
        actorName: String,
        completeImmediately: Boolean = false,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Result<Unit> = runCatching {
        val rx = dao.getPrescriptionById(prescriptionId)
            ?: throw IllegalStateException("Prescription not found")
        require(rx.userId == workspaceUserId) { "Unauthorized prescription access" }

        if (rx.statusEnum() == PrescriptionStatus.IMPORTED || rx.statusEnum() == PrescriptionStatus.COMPLETED) {
            throw IllegalStateException("Duplicate import rejected: Prescription ${rx.requestCode} was already imported.")
        }
        if (rx.claimToken.isNotBlank() && rx.claimToken != claimToken) {
            throw IllegalStateException("Invalid or superseded claimToken. Re-claim the prescription before importing.")
        }
        if (rx.claimedByCounterId.isNotBlank() && rx.claimedByCounterId != counterTerminalId) {
            throw IllegalStateException("Prescription is claimed by ${rx.claimedByCounterId}, not $counterTerminalId.")
        }

        val targetStatus = if (completeImmediately) PrescriptionStatus.COMPLETED else PrescriptionStatus.IMPORTED
        dao.updatePrescription(
            rx.copy(
                status = targetStatus.name,
                claimedByCounterId = counterTerminalId.take(80),
                importedAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        )

        recordAuditLog(
            prescriptionId = prescriptionId,
            requestCode = rx.requestCode,
            action = "IMPORTED",
            actorName = actorName,
            actorRole = UserRole.ADMIN,
            details = "Marked ${targetStatus.name} for terminal $counterTerminalId"
        )
    }

    suspend fun markPrescriptionCompleted(
        prescriptionId: String,
        actorName: String
    ): Result<Unit> = runCatching {
        val rx = dao.getPrescriptionById(prescriptionId)
            ?: throw IllegalStateException("Prescription not found")
        require(rx.userId == workspaceUserId) { "Unauthorized prescription access" }
        require(PrescriptionStatus.isValidTransition(rx.statusEnum(), PrescriptionStatus.COMPLETED)) {
            "Cannot transition from ${rx.status} to COMPLETED"
        }

        dao.updatePrescription(
            rx.copy(
                status = PrescriptionStatus.COMPLETED.name,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )

        recordAuditLog(
            prescriptionId = prescriptionId,
            requestCode = rx.requestCode,
            action = "IMPORTED",
            actorName = actorName,
            actorRole = UserRole.ADMIN,
            details = "Counter sale finalized (COMPLETED)."
        )
    }

    suspend fun deletePrescription(
        prescriptionId: String,
        requestCode: String,
        actorName: String
    ): Result<Unit> = runCatching {
        dao.deletePrescriptionById(prescriptionId)
        recordAuditLog(
            prescriptionId = prescriptionId,
            requestCode = requestCode,
            action = "DELETED",
            actorName = actorName,
            actorRole = UserRole.ADMIN,
            details = "Deleted prescription record and associated image reference."
        )
    }

    suspend fun purgeExpiredPrescriptions(
        retentionDays: Int,
        actorName: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Result<Int> = runCatching {
        val cutoffMs = nowEpochMs - (retentionDays.coerceAtLeast(1) * 24L * 60L * 60L * 1000L)
        val allDocs = dao.getAllPrescriptionsForUser(workspaceUserId)
        val expiredDocs = allDocs.filter { it.createdAtEpochMs < cutoffMs }
        for (doc in expiredDocs) {
            dao.deletePrescriptionById(doc.id)
        }
        if (expiredDocs.isNotEmpty()) {
            recordAuditLog(
                prescriptionId = "retention_purge",
                requestCode = "RETENTION",
                action = "RETENTION_PURGED",
                actorName = actorName,
                actorRole = UserRole.ADMIN,
                details = "Purged ${expiredDocs.size} prescription(s) older than $retentionDays day(s)."
            )
        }
        expiredDocs.size
    }

    // --- Medicine Catalogue Operations ---

    suspend fun importCatalogItems(items: List<MedicineCatalogItem>): Result<Int> = runCatching {
        var importedCount = 0
        val now = System.currentTimeMillis()
        for (item in items) {
            val cleanCode = item.itemCode.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_").take(64).ifBlank {
                "itm_${UUID.randomUUID().toString().take(8)}"
            }
            val docId = "${workspaceUserId.take(12)}_$cleanCode"
            val entity = MedicineCatalogItem(
                id = docId,
                userId = workspaceUserId,
                itemCode = item.itemCode.take(64),
                itemName = item.itemName.take(200),
                genericName = item.genericName.take(200),
                unit = item.unit.ifBlank { "Unit" }.take(60),
                packSize = item.packSize.take(60),
                mrp = item.mrp,
                gstPercent = item.gstPercent,
                hsnCode = item.hsnCode.take(40),
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            dao.upsertCatalogItem(entity)
            importedCount++
        }
        importedCount
    }

    suspend fun deleteCatalogItem(itemId: String): Result<Unit> = runCatching {
        dao.deleteCatalogItemById(itemId)
    }

    // --- Staff Accounts Management ---

    suspend fun addStaffAccount(
        staffName: String,
        staffEmail: String,
        role: UserRole,
        counterTerminalId: String
    ): Result<String> = runCatching {
        require(staffName.isNotBlank()) { "Staff member name is required." }
        val docId = "staff_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val now = System.currentTimeMillis()
        val staff = StaffAccount(
            id = docId,
            userId = workspaceUserId,
            staffName = staffName.trim().take(120),
            staffEmail = staffEmail.trim().take(200),
            role = role.name,
            counterTerminalId = counterTerminalId.trim().ifBlank { "COUNTER-1" }.take(60),
            isActive = true,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        dao.upsertStaffAccount(staff)
        docId
    }

    suspend fun toggleStaffActive(staff: StaffAccount): Result<Unit> = runCatching {
        dao.upsertStaffAccount(
            staff.copy(
                isActive = !staff.isActive,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteStaffAccount(staffId: String): Result<Unit> = runCatching {
        dao.deleteStaffAccountById(staffId)
    }

    // --- Audit Logging ---

    suspend fun recordAuditLog(
        prescriptionId: String,
        requestCode: String,
        action: String,
        actorName: String,
        actorRole: UserRole,
        details: String
    ): Result<String> = runCatching {
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val entry = AuditLogEntry(
            id = logId,
            userId = workspaceUserId,
            prescriptionId = prescriptionId.take(128),
            requestCode = requestCode.take(40),
            action = action,
            actorName = actorName.ifBlank { "System" }.take(120),
            actorRole = actorRole.name,
            details = details.take(1000),
            createdAtEpochMs = System.currentTimeMillis()
        )
        dao.insertAuditLog(entry)
        logId
    }

    companion object {
        const val DEFAULT_WORKSPACE_ID = "default_pharmacy"
    }
}
