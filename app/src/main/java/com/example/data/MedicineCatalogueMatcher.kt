package com.example.data

import com.example.model.MedicineCatalogItem
import com.example.model.PrescriptionMedicineItem
import java.util.Locale

data class CatalogMatchCandidate(
    val catalogItem: MedicineCatalogItem,
    val score: Int,
    val matchReason: String
)

data class CatalogMatchEvaluation(
    val updatedItem: PrescriptionMedicineItem,
    val candidates: List<CatalogMatchCandidate>
)

object MedicineCatalogueMatcher {

    /**
     * Parses a pharmacy Item Master CSV text into verified MedicineCatalogItem records.
     * Supports standard headers: item_code/code, item_name/name/particulars, generic_name/composition,
     * unit/uom, pack_size/pack, mrp/rate, gst_percent/gst/tax, hsn_code/hsn.
     * Never invents prices, GST rates, stock availability, or batch numbers.
     */
    fun parseItemMasterCsv(csvContent: String, userId: String): Result<List<MedicineCatalogItem>> {
        val lines = csvContent.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.size < 2) {
            return Result.failure(
                IllegalArgumentException("CSV must contain a header row and at least one medicine item row.")
            )
        }

        val headers = splitCsvLine(lines.first()).map {
            it.lowercase(Locale.ROOT).replace("[^a-z0-9]".toRegex(), "")
        }

        val codeIdx = headers.indexOfFirst { it in setOf("itemcode", "code", "sku", "id", "itemid") }
        val nameIdx = headers.indexOfFirst { it in setOf("itemname", "name", "particulars", "medicinename", "productname", "description") }
        val genericIdx = headers.indexOfFirst { it in setOf("genericname", "generic", "composition", "salt") }
        val unitIdx = headers.indexOfFirst { it in setOf("unit", "uom", "dosageform", "form") }
        val packIdx = headers.indexOfFirst { it in setOf("packsize", "pack", "packing") }
        val mrpIdx = headers.indexOfFirst { it in setOf("mrp", "price", "rate", "maxretailprice") }
        val gstIdx = headers.indexOfFirst { it in setOf("gstpercent", "gst", "tax", "taxpercent", "gstrate") }
        val hsnIdx = headers.indexOfFirst { it in setOf("hsncode", "hsn") }

        if (nameIdx == -1 || mrpIdx == -1) {
            return Result.failure(
                IllegalArgumentException("CSV header must include at least item_name (or name) and mrp (or rate) columns.")
            )
        }

        val parsedItems = mutableListOf<MedicineCatalogItem>()
        for (i in 1 until lines.size) {
            val cols = splitCsvLine(lines[i])
            if (cols.size <= nameIdx) continue

            val rawName = cols.getOrNull(nameIdx)?.trim().orEmpty()
            if (rawName.isBlank()) continue

            val rawMrp = cols.getOrNull(mrpIdx)?.trim()?.replace("[^0-9.]".toRegex(), "").orEmpty()
            val mrpVal = rawMrp.toDoubleOrNull()
                ?: return Result.failure(
                    IllegalArgumentException("Row ${i + 1} ('$rawName') has an invalid or missing MRP value. Verified MRP is required.")
                )

            val rawGst = if (gstIdx >= 0) {
                cols.getOrNull(gstIdx)?.trim()?.replace("[^0-9.]".toRegex(), "").orEmpty()
            } else ""
            val gstVal = if (rawGst.isBlank()) 0.0 else (rawGst.toDoubleOrNull() ?: 0.0)

            val itemCode = if (codeIdx >= 0 && !cols.getOrNull(codeIdx).isNullOrBlank()) {
                cols[codeIdx].trim()
            } else {
                "ITM-${1000 + i}"
            }

            val genericName = if (genericIdx >= 0) cols.getOrNull(genericIdx)?.trim().orEmpty() else ""
            val unit = if (unitIdx >= 0 && !cols.getOrNull(unitIdx).isNullOrBlank()) {
                cols[unitIdx].trim()
            } else "Unit"
            val packSize = if (packIdx >= 0) cols.getOrNull(packIdx)?.trim().orEmpty() else ""
            val hsnCode = if (hsnIdx >= 0) cols.getOrNull(hsnIdx)?.trim().orEmpty() else ""

            parsedItems.add(
                MedicineCatalogItem(
                    userId = userId,
                    itemCode = itemCode.take(64),
                    itemName = rawName.take(200),
                    genericName = genericName.take(200),
                    unit = unit.take(60),
                    packSize = packSize.take(60),
                    mrp = mrpVal,
                    gstPercent = gstVal.coerceIn(0.0, 100.0),
                    hsnCode = hsnCode.take(40)
                )
            )
        }

        if (parsedItems.isEmpty()) {
            return Result.failure(IllegalArgumentException("No valid medicine rows found in CSV."))
        }
        return Result.success(parsedItems)
    }

    /**
     * Matches an extracted prescription medicine item against the verified Item Master catalogue.
     * - If multiple items match or the medicine is uncertain, requires manual selection (needsReview = true).
     * - Never invents prices, GST rates, stock availability, or batch numbers.
     */
    fun matchMedicineItem(
        item: PrescriptionMedicineItem,
        catalog: List<MedicineCatalogItem>
    ): CatalogMatchEvaluation {
        val rawName = item.extractedMedicineName.trim()
        if (rawName.isBlank() ||
            rawName.equals("Needs Review", ignoreCase = true) ||
            catalog.isEmpty()
        ) {
            return CatalogMatchEvaluation(
                updatedItem = item.copy(
                    needsReview = true,
                    reviewReason = if (catalog.isEmpty()) {
                        "Item Master empty — import CSV or select manually"
                    } else {
                        item.reviewReason.ifBlank { "Medicine name uncertain — manual verification required" }
                    },
                    matchStatus = "UNMATCHED"
                ),
                candidates = emptyList()
            )
        }

        val normalizedQuery = normalizeText("${item.extractedMedicineName} ${item.strength}".trim())
        val normalizedMedName = normalizeText(item.extractedMedicineName)
        val normalizedStrength = normalizeText(item.strength)

        val candidates = mutableListOf<CatalogMatchCandidate>()
        for (cat in catalog) {
            val normCatName = normalizeText(cat.itemName)
            val normGeneric = normalizeText(cat.genericName)

            when {
                normCatName == normalizedQuery || normCatName == normalizedMedName -> {
                    val strengthMatches = normalizedStrength.isBlank() || normCatName.contains(normalizedStrength)
                    val score = if (normCatName == normalizedQuery && strengthMatches) 100 else 85
                    candidates.add(
                        CatalogMatchCandidate(
                            catalogItem = cat,
                            score = score,
                            matchReason = if (score == 100) "Exact name & strength match" else "Exact brand name match"
                        )
                    )
                }
                normCatName.contains(normalizedMedName) || normalizedMedName.contains(normCatName) -> {
                    val strengthBonus = if (normalizedStrength.isNotBlank() && normCatName.contains(normalizedStrength)) 15 else 0
                    candidates.add(
                        CatalogMatchCandidate(
                            catalogItem = cat,
                            score = 70 + strengthBonus,
                            matchReason = "Partial Item Master name match"
                        )
                    )
                }
                normGeneric.isNotBlank() && (normGeneric.contains(normalizedMedName) || normalizedMedName.contains(normGeneric)) -> {
                    candidates.add(
                        CatalogMatchCandidate(
                            catalogItem = cat,
                            score = 60,
                            matchReason = "Generic composition match"
                        )
                    )
                }
            }
        }

        val sortedCandidates = candidates.sortedByDescending { it.score }

        return when {
            sortedCandidates.isEmpty() -> {
                CatalogMatchEvaluation(
                    updatedItem = item.copy(
                        needsReview = true,
                        reviewReason = item.reviewReason.ifBlank { "No matching item in Item Master — manual selection required" },
                        matchStatus = "UNMATCHED",
                        matchedItemCode = "",
                        matchedItemName = "",
                        matchedUnit = "",
                        matchedPackSize = "",
                        verifiedMrp = 0.0,
                        verifiedGstPercent = 0.0
                    ),
                    candidates = emptyList()
                )
            }
            sortedCandidates.size == 1 && sortedCandidates.first().score >= 85 && !item.needsReview -> {
                val winner = sortedCandidates.first().catalogItem
                CatalogMatchEvaluation(
                    updatedItem = item.copy(
                        matchedItemCode = winner.itemCode,
                        matchedItemName = winner.itemName,
                        matchedUnit = winner.unit,
                        matchedPackSize = winner.packSize,
                        verifiedMrp = winner.mrp,
                        verifiedGstPercent = winner.gstPercent,
                        matchStatus = "EXACT_MATCH"
                    ),
                    candidates = sortedCandidates
                )
            }
            else -> {
                // Multiple possible matches OR uncertain score -> require manual selection!
                val top = sortedCandidates.first().catalogItem
                CatalogMatchEvaluation(
                    updatedItem = item.copy(
                        needsReview = true,
                        reviewReason = if (sortedCandidates.size > 1) {
                            "${sortedCandidates.size} possible Item Master matches — select exact pack/strength manually"
                        } else {
                            item.reviewReason.ifBlank { "Partial catalogue match — confirm item manually" }
                        },
                        matchedItemCode = top.itemCode,
                        matchedItemName = top.itemName,
                        matchedUnit = top.unit,
                        matchedPackSize = top.packSize,
                        verifiedMrp = top.mrp,
                        verifiedGstPercent = top.gstPercent,
                        matchStatus = "MULTIPLE_MATCHES"
                    ),
                    candidates = sortedCandidates
                )
            }
        }
    }

    fun applyManualSelection(
        item: PrescriptionMedicineItem,
        selectedCatalogItem: MedicineCatalogItem
    ): PrescriptionMedicineItem {
        val stillNeedsReview = item.extractedMedicineName.isBlank() ||
            item.explicitQuantity.equals("Needs Review", ignoreCase = true)
        return item.copy(
            matchedItemCode = selectedCatalogItem.itemCode,
            matchedItemName = selectedCatalogItem.itemName,
            matchedUnit = selectedCatalogItem.unit,
            matchedPackSize = selectedCatalogItem.packSize,
            verifiedMrp = selectedCatalogItem.mrp,
            verifiedGstPercent = selectedCatalogItem.gstPercent,
            matchStatus = "MANUAL_VERIFIED",
            needsReview = stillNeedsReview,
            reviewReason = if (stillNeedsReview) "Verify explicit quantity before approval" else ""
        )
    }

    private fun normalizeText(input: String): String {
        return input.lowercase(Locale.ROOT)
            .replace("[^a-z0-9]".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }

    internal fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }
}
