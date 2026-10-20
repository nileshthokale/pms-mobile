package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

enum class UserRole(val label: String) {
    ADMIN("Admin / Pharmacist"),
    COUNTER_STAFF("Counter Staff")
}

enum class PrescriptionStatus(val label: String) {
    PENDING("Pending"),
    ANALYZING("Analyzing"),
    NEEDS_REVIEW("Needs Review"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    IMPORTED("Imported"),
    COMPLETED("Completed");

    companion object {
        fun fromString(value: String?): PrescriptionStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
        }

        fun isValidTransition(from: PrescriptionStatus, to: PrescriptionStatus): Boolean {
            if (from == COMPLETED) return false
            if (from == to) return true
            return when (from) {
                PENDING -> to in setOf(ANALYZING, NEEDS_REVIEW, REJECTED)
                ANALYZING -> to in setOf(NEEDS_REVIEW, PENDING, REJECTED)
                NEEDS_REVIEW -> to in setOf(ANALYZING, APPROVED, REJECTED)
                APPROVED -> to in setOf(NEEDS_REVIEW, IMPORTED, COMPLETED, REJECTED)
                REJECTED -> to in setOf(NEEDS_REVIEW, PENDING)
                IMPORTED -> to in setOf(COMPLETED)
                COMPLETED -> false
            }
        }
    }
}

enum class AiAuthMethod(val label: String, val description: String) {
    BEARER_HEADER("Authorization: Bearer <KEY>", "Standard Bearer token header (OpenAI, Anthropic/OpenRouter, Custom APIs)"),
    X_API_KEY_HEADER("x-api-key / x-goog-api-key Header", "Header-based API key authentication"),
    QUERY_PARAM_KEY("Query Parameter (?key=<KEY>)", "URL query parameter authentication")
}

enum class AiRequestFormat(val label: String, val description: String) {
    OPENAI_VISION("OpenAI-Compatible Vision JSON", "Standard messages[] with image_url data URI"),
    GEMINI_REST("Generative Language contents[] JSON", "contents[].parts[] with inlineData base64"),
    GENERIC_JSON_VISION("Generic Vision JSON Payload", "Simple { model, prompt, image_base64 } JSON contract")
}

data class PrescriptionMedicineItem(
    val itemId: String = "",
    val extractedMedicineName: String = "",
    val strength: String = "",
    val dosageForm: String = "",
    val dosageInstructions: String = "",
    val frequency: String = "",
    val duration: String = "",
    val explicitQuantity: String = "",
    val additionalInstructions: String = "",
    val needsReview: Boolean = true,
    val reviewReason: String = "",
    // Verified Item Master match fields (never invented by AI)
    val matchedItemCode: String = "",
    val matchedItemName: String = "",
    val matchedUnit: String = "",
    val matchedPackSize: String = "",
    val verifiedMrp: Double = 0.0,
    val verifiedGstPercent: Double = 0.0,
    val matchStatus: String = "UNMATCHED" // EXACT_MATCH, MULTIPLE_MATCHES, UNMATCHED, MANUAL_VERIFIED
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("itemId", itemId.take(64))
            put("extractedMedicineName", extractedMedicineName.take(150))
            put("strength", strength.take(60))
            put("dosageForm", dosageForm.take(60))
            put("dosageInstructions", dosageInstructions.take(200))
            put("frequency", frequency.take(60))
            put("duration", duration.take(60))
            put("explicitQuantity", explicitQuantity.take(40))
            put("additionalInstructions", additionalInstructions.take(250))
            put("needsReview", needsReview)
            put("reviewReason", reviewReason.take(200))
            put("matchedItemCode", matchedItemCode.take(64))
            put("matchedItemName", matchedItemName.take(200))
            put("matchedUnit", matchedUnit.take(60))
            put("matchedPackSize", matchedPackSize.take(60))
            put("verifiedMrp", verifiedMrp)
            put("verifiedGstPercent", verifiedGstPercent)
            put("matchStatus", matchStatus.take(40))
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): PrescriptionMedicineItem {
            return PrescriptionMedicineItem(
                itemId = obj.optString("itemId", ""),
                extractedMedicineName = obj.optString("extractedMedicineName", obj.optString("medicineName", "")),
                strength = obj.optString("strength", ""),
                dosageForm = obj.optString("dosageForm", ""),
                dosageInstructions = obj.optString("dosageInstructions", ""),
                frequency = obj.optString("frequency", ""),
                duration = obj.optString("duration", ""),
                explicitQuantity = obj.optString("explicitQuantity", ""),
                additionalInstructions = obj.optString("additionalInstructions", ""),
                needsReview = obj.optBoolean("needsReview", false),
                reviewReason = obj.optString("reviewReason", ""),
                matchedItemCode = obj.optString("matchedItemCode", ""),
                matchedItemName = obj.optString("matchedItemName", ""),
                matchedUnit = obj.optString("matchedUnit", ""),
                matchedPackSize = obj.optString("matchedPackSize", ""),
                verifiedMrp = obj.optDouble("verifiedMrp", 0.0).let { if (it.isNaN()) 0.0 else it },
                verifiedGstPercent = obj.optDouble("verifiedGstPercent", 0.0).let { if (it.isNaN()) 0.0 else it },
                matchStatus = obj.optString("matchStatus", "UNMATCHED")
            )
        }

        fun serializeList(items: List<PrescriptionMedicineItem>): String {
            val arr = JSONArray()
            items.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }

        fun deserializeList(jsonStr: String): List<PrescriptionMedicineItem> {
            if (jsonStr.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<PrescriptionMedicineItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(fromJsonObject(obj))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: String = "default_pharmacy",
    val userId: String = "default_pharmacy",
    val email: String = "admin@pharmacy.local",
    val displayName: String = "Chief Pharmacist",
    val pharmacyName: String = "City Care Pharmacy",
    val activeRole: String = UserRole.ADMIN.name,
    val retentionDays: Long = 30L,
    val aiEndpoint: String = "",
    val aiModelName: String = "",
    val aiAuthMethod: String = AiAuthMethod.BEARER_HEADER.name,
    val aiRequestFormat: String = AiRequestFormat.OPENAI_VISION.name,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "prescriptions")
data class PrescriptionRequest(
    @PrimaryKey val id: String = "",
    val userId: String = "default_pharmacy",
    val requestCode: String = "",
    val status: String = PrescriptionStatus.PENDING.name,
    val submittedByRole: String = UserRole.COUNTER_STAFF.name,
    val submittedByName: String = "",
    val patientName: String = "",
    val patientAge: String = "",
    val patientContact: String = "",
    val staffNotes: String = "",
    val doctorName: String = "",
    val prescriptionDate: String = "",
    val imageLocalPath: String = "",
    val imageBase64Preview: String = "",
    val itemsJson: String = "[]",
    val needsReviewCount: Long = 0L,
    val reviewedBy: String = "",
    val reviewNotes: String = "",
    val readyForCounterSale: Boolean = false,
    val claimedByCounterId: String = "",
    val claimToken: String = "",
    val claimExpiresAtEpochMs: Long = 0L,
    val importedAtEpochMs: Long = 0L,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun parsedItems(): List<PrescriptionMedicineItem> {
        return PrescriptionMedicineItem.deserializeList(itemsJson)
    }

    val items: List<PrescriptionMedicineItem>
        get() = parsedItems()

    val itemCount: Int
        get() = parsedItems().size

    val createdAt: Long
        get() = createdAtEpochMs

    val updatedAt: Long
        get() = updatedAtEpochMs

    fun statusEnum(): PrescriptionStatus = PrescriptionStatus.fromString(status)

    fun isClaimActive(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        return claimedByCounterId.isNotBlank() && claimExpiresAtEpochMs > nowEpochMs
    }

    fun toDesktopExportJson(pharmacyName: String): String {
        val root = JSONObject()
        root.put("schemaVersion", "1.0")
        root.put("exportType", "COUNTER_SALE_PRESCRIPTION_BRIDGE")
        root.put("desktopConnectionState", "DESKTOP_NOT_CONNECTED")
        root.put("pharmacyName", pharmacyName)
        root.put("prescriptionId", id)
        root.put("requestCode", requestCode)
        root.put("status", status)
        root.put("readyForCounterSale", readyForCounterSale)
        root.put("claimedByCounterId", claimedByCounterId)
        root.put("claimToken", claimToken)
        root.put("claimExpiresAtEpochMs", claimExpiresAtEpochMs)
        root.put("importedAtEpochMs", importedAtEpochMs)

        val patientObj = JSONObject().apply {
            put("name", patientName)
            put("age", patientAge)
            put("contact", patientContact)
        }
        root.put("patient", patientObj)

        val rxMeta = JSONObject().apply {
            put("doctorName", doctorName)
            put("prescriptionDate", prescriptionDate)
            put("reviewedBy", reviewedBy)
            put("reviewNotes", reviewNotes)
            put("staffNotes", staffNotes)
        }
        root.put("prescriptionMetadata", rxMeta)

        val lineItemsArray = JSONArray()
        parsedItems().forEachIndexed { index, item ->
            val itemJson = JSONObject().apply {
                put("lineNumber", index + 1)
                put("extractedMedicineName", item.extractedMedicineName)
                put("strength", item.strength)
                put("dosageForm", item.dosageForm)
                put("dosageInstructions", item.dosageInstructions)
                put("frequency", item.frequency)
                put("duration", item.duration)
                put("explicitQuantity", item.explicitQuantity)
                put("additionalInstructions", item.additionalInstructions)
                put("needsReview", item.needsReview)
                put("itemMasterMatch", JSONObject().apply {
                    put("matchStatus", item.matchStatus)
                    put("itemCode", item.matchedItemCode)
                    put("itemName", item.matchedItemName)
                    put("unit", item.matchedUnit)
                    put("packSize", item.matchedPackSize)
                    put("verifiedMrp", item.verifiedMrp)
                    put("verifiedGstPercent", item.verifiedGstPercent)
                    put("stockAvailability", JSONObject.NULL) // Must come from local SQLite at counter
                    put("batchNumber", JSONObject.NULL)       // Selected at desktop Counter Sale screen
                })
            }
            lineItemsArray.put(itemJson)
        }
        root.put("lineItems", lineItemsArray)
        return root.toString(2)
    }
}

@Entity(tableName = "catalog_items")
data class MedicineCatalogItem(
    @PrimaryKey val id: String = "",
    val userId: String = "default_pharmacy",
    val itemCode: String = "",
    val itemName: String = "",
    val genericName: String = "",
    val unit: String = "Tablet",
    val packSize: String = "10x10",
    val mrp: Double = 0.0,
    val gstPercent: Double = 12.0,
    val hsnCode: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "staff_accounts")
data class StaffAccount(
    @PrimaryKey val id: String = "",
    val userId: String = "default_pharmacy",
    val staffName: String = "",
    val staffEmail: String = "",
    val role: String = UserRole.COUNTER_STAFF.name,
    val counterTerminalId: String = "COUNTER-1",
    val isActive: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntry(
    @PrimaryKey val id: String = "",
    val userId: String = "default_pharmacy",
    val prescriptionId: String = "",
    val requestCode: String = "",
    val action: String = "CREATED",
    val actorName: String = "",
    val actorRole: String = UserRole.ADMIN.name,
    val details: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    val createdAt: Long
        get() = createdAtEpochMs
}
