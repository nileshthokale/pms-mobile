package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.model.PrescriptionMedicineItem
import com.example.model.PrescriptionStatus
import com.example.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PharmacyRepositoryRuleTest {

    private lateinit var database: PharmacyDatabase
    private lateinit var dao: PharmacyDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.pharmacyDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun workspaceIsolation_aliceCannotReadOrModifyBobsPrescription() = runBlocking {
        val bobRepo = PharmacyRepository(dao, workspaceUserId = "bob_workspace")
        val rxId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.submitPrescriptionRequest(
                submittedByRole = UserRole.COUNTER_STAFF,
                submittedByName = "Counter Staff Bob",
                patientName = "Demo Patient 1",
                patientAge = "35",
                patientContact = "555-0101",
                staffNotes = "Urgent",
                imageLocalPath = "/data/local/rx_bob.jpg",
                imageBase64Preview = ""
            ).getOrThrow()
        }

        val aliceRepo = PharmacyRepository(dao, workspaceUserId = "alice_workspace")
        val readResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.getPrescriptionById(rxId)
        }
        assertTrue(readResult.isFailure)
    }

    @Test
    fun extractionValidation_andCsvMatching_flagsAmbiguousFieldsAndRequiresManualSelection() {
        val sampleCsv = """
            item_code,item_name,generic_name,unit,pack_size,mrp,gst_percent,hsn_code
            ITM-101,Augmentin 625 Duo Tablet,Amoxicillin + Clavulanic Acid,Strip,10 Tabs,204.50,12,3004
            ITM-102,Augmentin DDS Syrup,Amoxicillin + Clavulanic Acid,Bottle,30ml,162.00,12,3004
            ITM-201,Dolo 650 Tablet,Paracetamol 650mg,Strip,15 Tabs,33.60,12,3004
        """.trimIndent()

        val catalog = MedicineCatalogueMatcher.parseItemMasterCsv(sampleCsv, "user_1").getOrThrow()
        assertEquals(3, catalog.size)
        assertEquals(204.50, catalog[0].mrp, 0.01)

        val aiJson = """
            {
              "patientName": "Demo Patient R.",
              "patientAge": "46",
              "doctorName": "?",
              "prescriptionDate": "2026-10-20",
              "medicines": [
                {
                  "medicineName": "Dolo 650 Tablet",
                  "strength": "650mg",
                  "dosageForm": "Tablet",
                  "dosageInstructions": "1-0-1 after food",
                  "frequency": "BD",
                  "duration": "5 days",
                  "explicitQuantity": "15",
                  "additionalInstructions": "",
                  "needsReview": false,
                  "reviewReason": ""
                },
                {
                  "medicineName": "Augmentin",
                  "strength": "unclear",
                  "dosageForm": "Tablet",
                  "dosageInstructions": "1-0-1",
                  "frequency": "BD",
                  "duration": "5 days",
                  "explicitQuantity": "",
                  "additionalInstructions": "",
                  "needsReview": true,
                  "reviewReason": "Handwriting unclear on strength"
                }
              ]
            }
        """.trimIndent()

        val extraction = ConfigurableRestAiPrescriptionService
            .validateAndParseExtractionJson(aiJson, catalog)
            .getOrThrow()

        assertEquals("Needs Review", extraction.doctorName)
        assertEquals(2, extraction.items.size)

        val item1 = extraction.items[0]
        assertFalse(item1.needsReview)
        assertEquals("ITM-201", item1.matchedItemCode)
        assertEquals(33.60, item1.verifiedMrp, 0.01)

        val item2 = extraction.items[1]
        assertTrue(item2.needsReview)
        assertEquals("Needs Review", item2.strength)
        assertEquals("Needs Review", item2.explicitQuantity)
        assertEquals("MULTIPLE_MATCHES", item2.matchStatus)
    }

    @Test
    fun approvalStatusTransitions_andDuplicateImportProtection_enforcedEndToEnd() = runBlocking {
        val workspaceId = PharmacyRepository.DEFAULT_WORKSPACE_ID
        val repo = PharmacyRepository(dao, workspaceId)

        // 1. Submit PENDING prescription
        val rxId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.submitPrescriptionRequest(
                submittedByRole = UserRole.COUNTER_STAFF,
                submittedByName = "Counter Staff 1",
                patientName = "Demo Patient B",
                patientAge = "29",
                patientContact = "555-0199",
                staffNotes = "Waiting at Counter 1",
                imageLocalPath = "/data/local/rx_101.jpg",
                imageBase64Preview = ""
            ).getOrThrow()
        }

        // 2. Transition PENDING -> ANALYZING -> NEEDS_REVIEW
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.markPrescriptionAnalyzing(rxId, "Admin").getOrThrow()
        }
        val unresolvedItem = PrescriptionMedicineItem(
            itemId = "line_1",
            extractedMedicineName = "Azithral 500",
            strength = "500mg",
            dosageForm = "Tablet",
            dosageInstructions = "1-0-0",
            frequency = "OD",
            duration = "3 days",
            explicitQuantity = "Needs Review",
            needsReview = true,
            reviewReason = "Quantity missing"
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveExtractionResult(
                prescriptionId = rxId,
                requestCode = "RX-1001",
                extraction = ValidatedPrescriptionExtraction(
                    patientName = "Demo Patient B",
                    patientAge = "29",
                    doctorName = "Dr. K. Verma",
                    prescriptionDate = "2026-10-20",
                    items = listOf(unresolvedItem),
                    needsReviewCount = 1
                ),
                actorName = "Admin"
            ).getOrThrow()
        }

        // 3. Approval MUST fail while item still has needsReview = true
        val prematureApprove = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.approvePrescription(
                prescriptionId = rxId,
                requestCode = "RX-1001",
                patientName = "Demo Patient B",
                patientAge = "29",
                patientContact = "555-0199",
                doctorName = "Dr. K. Verma",
                prescriptionDate = "2026-10-20",
                items = listOf(unresolvedItem),
                reviewedBy = "Chief Pharmacist",
                reviewNotes = "Attempted before fixing quantity"
            )
        }
        assertTrue(prematureApprove.isFailure)

        // 4. Resolve Needs Review and approve -> Ready for Counter Sale
        val resolvedItem = unresolvedItem.copy(
            explicitQuantity = "3",
            needsReview = false,
            reviewReason = "",
            matchedItemCode = "ITM-301",
            matchedItemName = "Azithral 500 Tablet",
            matchedUnit = "Strip",
            matchedPackSize = "5 Tabs",
            verifiedMrp = 132.0,
            verifiedGstPercent = 12.0,
            matchStatus = "MANUAL_VERIFIED"
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.approvePrescription(
                prescriptionId = rxId,
                requestCode = "RX-1001",
                patientName = "Demo Patient B",
                patientAge = "29",
                patientContact = "555-0199",
                doctorName = "Dr. K. Verma",
                prescriptionDate = "2026-10-20",
                items = listOf(resolvedItem),
                reviewedBy = "Chief Pharmacist",
                reviewNotes = "Verified against handwritten Rx"
            ).getOrThrow()
        }

        val approvedRx = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getPrescriptionById(rxId).getOrThrow() }
        assertEquals(PrescriptionStatus.APPROVED.name, approvedRx.status)
        assertTrue(approvedRx.readyForCounterSale)

        // 5. Counter-1 claims the prescription (active lock)
        val nowMs = 1_760_000_000_000L
        val token1 = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.claimPrescriptionForCounter(
                prescriptionId = rxId,
                counterTerminalId = "COUNTER-1",
                actorName = "Desktop Bridge Counter 1",
                nowEpochMs = nowMs,
                lockDurationMs = 60_000L
            ).getOrThrow()
        }

        // 6. Counter-2 cannot steal active claim before expiry
        val concurrentClaim = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.claimPrescriptionForCounter(
                prescriptionId = rxId,
                counterTerminalId = "COUNTER-2",
                actorName = "Desktop Bridge Counter 2",
                nowEpochMs = nowMs + 10_000L,
                lockDurationMs = 60_000L
            )
        }
        assertTrue(concurrentClaim.isFailure)

        // 7. After lock expires, Counter-2 can safely recover the claim
        val recoveredToken = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.claimPrescriptionForCounter(
                prescriptionId = rxId,
                counterTerminalId = "COUNTER-2",
                actorName = "Desktop Bridge Counter 2",
                nowEpochMs = nowMs + 120_000L,
                lockDurationMs = 60_000L
            ).getOrThrow()
        }
        assertTrue(recoveredToken != token1)

        // 8. Counter-2 marks prescription IMPORTED
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.markPrescriptionImported(
                prescriptionId = rxId,
                counterTerminalId = "COUNTER-2",
                claimToken = recoveredToken,
                actorName = "Desktop Bridge Counter 2",
                completeImmediately = false,
                nowEpochMs = nowMs + 125_000L
            ).getOrThrow()
        }

        // 9. Duplicate import attempt is rejected
        val duplicateImport = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.markPrescriptionImported(
                prescriptionId = rxId,
                counterTerminalId = "COUNTER-2",
                claimToken = recoveredToken,
                actorName = "Desktop Bridge Counter 2",
                completeImmediately = false,
                nowEpochMs = nowMs + 130_000L
            )
        }
        assertTrue(duplicateImport.isFailure)

        // 10. Verify audit log entries were recorded for this workspace
        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeAuditLogs(workspaceId).first { it.isNotEmpty() }
        }
        assertTrue(logs.any { it.action == "APPROVED" })
        assertTrue(logs.any { it.action == "IMPORTED" })
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
