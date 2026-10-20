package com.example.data

import com.example.BuildConfig
import com.example.model.AiAuthMethod
import com.example.model.AiRequestFormat
import com.example.model.MedicineCatalogItem
import com.example.model.PrescriptionMedicineItem
import com.example.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AiProviderConfig(
    val apiKey: String,
    val endpointUrl: String,
    val modelName: String,
    val authMethod: AiAuthMethod,
    val requestFormat: AiRequestFormat
) {
    fun isConfigured(): Boolean {
        val validKey = apiKey.isNotBlank() &&
            !apiKey.equals("YOUR_AI_API_KEY_HERE", ignoreCase = true) &&
            !apiKey.equals("MY_AI_API_KEY", ignoreCase = true)
        val validEndpoint = endpointUrl.isNotBlank() &&
            !endpointUrl.equals("YOUR_AI_API_ENDPOINT_HERE", ignoreCase = true) &&
            (endpointUrl.startsWith("https://") || endpointUrl.startsWith("http://"))
        val validModel = modelName.isNotBlank() &&
            !modelName.equals("YOUR_AI_MODEL_NAME_HERE", ignoreCase = true)
        return validKey && validEndpoint && validModel
    }

    fun missingConfigReasons(): List<String> {
        val reasons = mutableListOf<String>()
        if (apiKey.isBlank() || apiKey.equals("YOUR_AI_API_KEY_HERE", ignoreCase = true)) {
            reasons.add("AI_API_KEY is not set in the AI Studio Secrets panel (.env)")
        }
        if (endpointUrl.isBlank() || endpointUrl.equals("YOUR_AI_API_ENDPOINT_HERE", ignoreCase = true)) {
            reasons.add("AI_API_ENDPOINT is not configured (set in .env or Settings -> API Configuration)")
        }
        if (modelName.isBlank() || modelName.equals("YOUR_AI_MODEL_NAME_HERE", ignoreCase = true)) {
            reasons.add("AI_MODEL_NAME is not configured (set in .env or Settings -> API Configuration)")
        }
        return reasons
    }

    companion object {
        fun fromEnvironmentAndProfile(profile: UserProfile?): AiProviderConfig {
            val envEndpoint = BuildConfig.AI_API_ENDPOINT.takeUnless {
                it.isBlank() || it == "YOUR_AI_API_ENDPOINT_HERE"
            }.orEmpty()
            val envModel = BuildConfig.AI_MODEL_NAME.takeUnless {
                it.isBlank() || it == "YOUR_AI_MODEL_NAME_HERE"
            }.orEmpty()
            val envAuth = BuildConfig.AI_AUTH_METHOD.takeUnless { it.isBlank() }
                ?: AiAuthMethod.BEARER_HEADER.name
            val envFormat = BuildConfig.AI_REQUEST_FORMAT.takeUnless { it.isBlank() }
                ?: AiRequestFormat.OPENAI_VISION.name

            val endpoint = profile?.aiEndpoint?.takeIf { it.isNotBlank() } ?: envEndpoint
            val model = profile?.aiModelName?.takeIf { it.isNotBlank() } ?: envModel
            val authMethod = AiAuthMethod.entries.firstOrNull {
                it.name.equals(profile?.aiAuthMethod?.takeIf { s -> s.isNotBlank() } ?: envAuth, ignoreCase = true)
            } ?: AiAuthMethod.BEARER_HEADER
            val requestFormat = AiRequestFormat.entries.firstOrNull {
                it.name.equals(profile?.aiRequestFormat?.takeIf { s -> s.isNotBlank() } ?: envFormat, ignoreCase = true)
            } ?: AiRequestFormat.OPENAI_VISION

            return AiProviderConfig(
                apiKey = BuildConfig.AI_API_KEY,
                endpointUrl = endpoint,
                modelName = model,
                authMethod = authMethod,
                requestFormat = requestFormat
            )
        }
    }
}

data class ValidatedPrescriptionExtraction(
    val patientName: String,
    val patientAge: String,
    val doctorName: String,
    val prescriptionDate: String,
    val items: List<PrescriptionMedicineItem>,
    val needsReviewCount: Int
)

sealed interface AiAnalysisResult {
    data class Success(val extraction: ValidatedPrescriptionExtraction) : AiAnalysisResult
    data class NotConfigured(val missingItems: List<String>) : AiAnalysisResult
    data class Error(val message: String) : AiAnalysisResult
}

interface PrescriptionAnalysisProvider {
    suspend fun analyzePrescriptionImage(
        imageBase64Jpeg: String,
        config: AiProviderConfig,
        catalog: List<MedicineCatalogItem>
    ): AiAnalysisResult
}

class ConfigurableRestAiPrescriptionService(
    private val httpClient: OkHttpClient = defaultOkHttpClient()
) : PrescriptionAnalysisProvider {

    override suspend fun analyzePrescriptionImage(
        imageBase64Jpeg: String,
        config: AiProviderConfig,
        catalog: List<MedicineCatalogItem>
    ): AiAnalysisResult = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext AiAnalysisResult.NotConfigured(config.missingConfigReasons())
        }
        if (imageBase64Jpeg.isBlank()) {
            return@withContext AiAnalysisResult.Error(
                "No prescription image data found. Please upload or capture a clear photo before analyzing."
            )
        }

        try {
            val resolvedEndpoint = config.endpointUrl.replace("{model}", config.modelName)
            val baseHttpUrl = resolvedEndpoint.toHttpUrlOrNull()
                ?: return@withContext AiAnalysisResult.Error("Invalid AI API endpoint URL: ${config.endpointUrl}")

            val finalUrl = if (config.authMethod == AiAuthMethod.QUERY_PARAM_KEY) {
                baseHttpUrl.newBuilder().addQueryParameter("key", config.apiKey).build()
            } else {
                baseHttpUrl
            }

            val payloadJson = buildRequestPayload(
                format = config.requestFormat,
                modelName = config.modelName,
                imageBase64 = imageBase64Jpeg,
                prompt = EXTRACTION_SYSTEM_PROMPT
            )

            val requestBuilder = Request.Builder()
                .url(finalUrl)
                .addHeader("Content-Type", "application/json")
                .post(payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType()))

            when (config.authMethod) {
                AiAuthMethod.BEARER_HEADER -> {
                    requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey}")
                }
                AiAuthMethod.X_API_KEY_HEADER -> {
                    requestBuilder.addHeader("x-api-key", config.apiKey)
                    requestBuilder.addHeader("x-goog-api-key", config.apiKey)
                }
                AiAuthMethod.QUERY_PARAM_KEY -> {
                    // Already attached as ?key=... query parameter
                }
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext AiAnalysisResult.Error(
                        "AI API returned HTTP ${response.code}: ${rawBody.take(240).ifBlank { response.message }}"
                    )
                }
                val extractedJsonText = unwrapProviderResponse(rawBody, config.requestFormat)
                val validated = validateAndParseExtractionJson(extractedJsonText, catalog)
                validated.fold(
                    onSuccess = { AiAnalysisResult.Success(it) },
                    onFailure = { err ->
                        AiAnalysisResult.Error(
                            "AI response validation failed: ${err.message ?: "Invalid structured JSON"}"
                        )
                    }
                )
            }
        } catch (e: Exception) {
            AiAnalysisResult.Error("AI analysis request failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        private const val EXTRACTION_SYSTEM_PROMPT = """
You are a strict clinical pharmacy prescription transcription assistant.
Analyze the handwritten prescription image and return ONLY a single valid JSON object matching this exact schema:
{
  "patientName": "string or 'Needs Review' if unreadable or empty string if absent",
  "patientAge": "string or empty string if absent",
  "doctorName": "string or 'Needs Review' if unreadable or empty string if absent",
  "prescriptionDate": "string or empty string if absent",
  "medicines": [
    {
      "medicineName": "exact written medicine name or 'Needs Review' if ambiguous",
      "strength": "exact written strength (e.g. '500mg') or 'Needs Review' if unclear",
      "dosageForm": "tablet | capsule | syrup | injection | ointment | drops | Needs Review",
      "dosageInstructions": "written instructions (e.g. '1-0-1 after meals')",
      "frequency": "written frequency (e.g. 'BD', 'TID', 'OD')",
      "duration": "written duration (e.g. '5 days')",
      "explicitQuantity": "ONLY the explicitly written quantity on the prescription, or 'Needs Review' if not explicitly written. NEVER calculate quantity from frequency * duration.",
      "additionalInstructions": "any extra notes",
      "needsReview": true/false,
      "reviewReason": "reason if any part of this line is ambiguous, illegible, or missing explicit quantity"
    }
  ]
}
CRITICAL SAFETY RULES:
1. NEVER invent medicine names, strengths, dosage instructions, or quantities.
2. Mark any unreadable or ambiguous value as "Needs Review" and set needsReview = true.
3. Do NOT calculate missing quantities and do NOT suggest medicine substitutions.
"""

        private fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        internal fun buildRequestPayload(
            format: AiRequestFormat,
            modelName: String,
            imageBase64: String,
            prompt: String
        ): String {
            return when (format) {
                AiRequestFormat.OPENAI_VISION -> {
                    JSONObject().apply {
                        put("model", modelName)
                        put("temperature", 0.1)
                        put("response_format", JSONObject().put("type", "json_object"))
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("type", "text")
                                        put("text", prompt)
                                    })
                                    put(JSONObject().apply {
                                        put("type", "image_url")
                                        put("image_url", JSONObject().apply {
                                            put("url", "data:image/jpeg;base64,$imageBase64")
                                        })
                                    })
                                })
                            })
                        })
                    }.toString()
                }
                AiRequestFormat.GEMINI_REST -> {
                    JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().put("text", prompt))
                                    put(JSONObject().put("inlineData", JSONObject().apply {
                                        put("mimeType", "image/jpeg")
                                        put("data", imageBase64)
                                    }))
                                })
                            })
                        })
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.1)
                            put("responseMimeType", "application/json")
                        })
                    }.toString()
                }
                AiRequestFormat.GENERIC_JSON_VISION -> {
                    JSONObject().apply {
                        put("model", modelName)
                        put("prompt", prompt)
                        put("image_base64", imageBase64)
                        put("mime_type", "image/jpeg")
                    }.toString()
                }
            }
        }

        internal fun unwrapProviderResponse(rawResponse: String, format: AiRequestFormat): String {
            val trimmed = rawResponse.trim()
            if (trimmed.isEmpty()) throw IllegalArgumentException("Empty response body from AI API")

            val root = JSONObject(trimmed)
            val contentText = when (format) {
                AiRequestFormat.OPENAI_VISION -> {
                    root.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        ?.takeIf { it.isNotBlank() }
                        ?: trimmed
                }
                AiRequestFormat.GEMINI_REST -> {
                    root.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text")
                        ?.takeIf { it.isNotBlank() }
                        ?: trimmed
                }
                AiRequestFormat.GENERIC_JSON_VISION -> {
                    root.optString("output").takeIf { it.isNotBlank() }
                        ?: root.optString("result").takeIf { it.isNotBlank() }
                        ?: trimmed
                }
            }
            return stripMarkdownCodeFence(contentText)
        }

        internal fun stripMarkdownCodeFence(text: String): String {
            var cleaned = text.trim()
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.removeSuffix("```").trim()
                }
            }
            return cleaned
        }

        /**
         * Strictly validates the structured JSON returned by the AI API before displaying it.
         * - Never invents medicine names, strengths, dosage instructions, or quantities.
         * - Flags unreadable, blank, or ambiguous values as "Needs Review".
         * - Matches each extracted line against the verified pharmacy Item Master.
         */
        fun validateAndParseExtractionJson(
            rawJson: String,
            catalog: List<MedicineCatalogItem> = emptyList()
        ): Result<ValidatedPrescriptionExtraction> {
            return try {
                val cleaned = stripMarkdownCodeFence(rawJson)
                val obj = JSONObject(cleaned)

                val patientName = sanitizeExtractedHeader(obj.optString("patientName", ""))
                val patientAge = obj.optString("patientAge", "").trim().take(40)
                val doctorName = sanitizeExtractedHeader(obj.optString("doctorName", ""))
                val prescriptionDate = obj.optString("prescriptionDate", "").trim().take(50)

                val medicinesArray = obj.optJSONArray("medicines")
                    ?: obj.optJSONArray("items")
                    ?: return Result.failure(
                        IllegalArgumentException("Response JSON is missing required 'medicines' array.")
                    )

                if (medicinesArray.length() == 0) {
                    return Result.failure(
                        IllegalArgumentException("No legible medicines were returned in 'medicines' array.")
                    )
                }

                val validatedItems = mutableListOf<PrescriptionMedicineItem>()
                for (i in 0 until minOf(medicinesArray.length(), 25)) {
                    val m = medicinesArray.optJSONObject(i) ?: continue
                    val rawMedName = m.optString("medicineName", m.optString("extractedMedicineName", "")).trim()
                    val rawStrength = m.optString("strength", "").trim()
                    val rawForm = m.optString("dosageForm", "").trim()
                    val rawDosage = m.optString("dosageInstructions", "").trim()
                    val rawFreq = m.optString("frequency", "").trim()
                    val rawDuration = m.optString("duration", "").trim()
                    val rawQty = m.optString("explicitQuantity", "").trim()
                    val rawExtra = m.optString("additionalInstructions", "").trim()
                    val aiFlaggedReview = m.optBoolean("needsReview", false)
                    val aiReviewReason = m.optString("reviewReason", "").trim()

                    val medAmbiguous = isAmbiguousValue(rawMedName)
                    val strengthAmbiguous = isAmbiguousValue(rawStrength)
                    val qtyAmbiguous = isAmbiguousValue(rawQty)

                    val reasons = mutableListOf<String>()
                    if (aiReviewReason.isNotBlank()) reasons.add(aiReviewReason)
                    if (medAmbiguous) reasons.add("Medicine name unreadable/ambiguous")
                    if (strengthAmbiguous) reasons.add("Strength not clearly written")
                    if (qtyAmbiguous) reasons.add("Quantity not explicitly written on Rx")

                    val initialItem = PrescriptionMedicineItem(
                        itemId = "line_${i + 1}_${UUID.randomUUID().toString().take(6)}",
                        extractedMedicineName = if (medAmbiguous) "Needs Review" else rawMedName,
                        strength = if (strengthAmbiguous) "Needs Review" else rawStrength,
                        dosageForm = rawForm.ifBlank { "Needs Review" },
                        dosageInstructions = rawDosage.ifBlank { "Needs Review" },
                        frequency = rawFreq,
                        duration = rawDuration,
                        explicitQuantity = if (qtyAmbiguous) "Needs Review" else rawQty,
                        additionalInstructions = rawExtra,
                        needsReview = aiFlaggedReview || medAmbiguous || strengthAmbiguous || qtyAmbiguous,
                        reviewReason = reasons.distinct().joinToString("; ")
                    )

                    val matchedEvaluation = MedicineCatalogueMatcher.matchMedicineItem(initialItem, catalog)
                    validatedItems.add(matchedEvaluation.updatedItem)
                }

                if (validatedItems.isEmpty()) {
                    return Result.failure(IllegalArgumentException("No valid medicine objects could be parsed."))
                }

                var reviewCount = validatedItems.count { it.needsReview }
                if (patientName.equals("Needs Review", ignoreCase = true)) reviewCount++
                if (doctorName.equals("Needs Review", ignoreCase = true)) reviewCount++

                Result.success(
                    ValidatedPrescriptionExtraction(
                        patientName = patientName,
                        patientAge = patientAge,
                        doctorName = doctorName,
                        prescriptionDate = prescriptionDate,
                        items = validatedItems,
                        needsReviewCount = reviewCount
                    )
                )
            } catch (e: Exception) {
                Result.failure(IllegalArgumentException("Malformed extraction JSON: ${e.message}", e))
            }
        }

        private fun sanitizeExtractedHeader(value: String): String {
            val trimmed = value.trim().take(150)
            if (trimmed.contains("?") ||
                trimmed.equals("unclear", ignoreCase = true) ||
                trimmed.equals("illegible", ignoreCase = true) ||
                trimmed.equals("needs_review", ignoreCase = true)
            ) {
                return "Needs Review"
            }
            return trimmed
        }

        private fun isAmbiguousValue(value: String): Boolean {
            if (value.isBlank()) return true
            val lower = value.lowercase()
            return lower == "needs review" ||
                lower == "needs_review" ||
                lower == "unknown" ||
                lower == "unclear" ||
                lower == "illegible" ||
                lower == "ambiguous" ||
                lower == "n/a" ||
                lower == "none" ||
                value.contains("?")
        }
    }
}
