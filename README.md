# Pharmacy Prescription Bridge

**Pharmacy Prescription Bridge** is a standalone prescription capture, AI-assisted extraction, pharmacist review, Item Master catalogue matching, and Counter Sale preparation application designed to pair with an existing **Python / PySide6 / SQLite Pharmacy Management System**.

---

## 1. Architecture & Core Features

- **Role-Based Workflows (`ADMIN` & `COUNTER_STAFF`)**:
  - **Counter Staff**: Capture a doctor's handwritten prescription using the device camera or photo picker, attach optional patient details/counter notes, submit to the queue, and track live status (`PENDING → ANALYZING → NEEDS_REVIEW → APPROVED / REJECTED → IMPORTED → COMPLETED`).
  - **Admin / Pharmacist**: Review incoming prescriptions side-by-side (original slip beside editable extracted fields), run AI extraction, match lines against the imported CSV Item Master, approve requests as **Ready for Counter Sale**, manage staff accounts/terminals, and export structured JSON.
- **Provider-Agnostic AI Prescription Analysis (`AiPrescriptionService.kt`)**:
  - Does **not** hardcode or assume a single AI vendor.
  - Supports configurable endpoint URL, model name, authentication method (`BEARER_HEADER`, `X_API_KEY_HEADER`, `QUERY_PARAM_KEY`), and request payload format (`OPENAI_VISION`, `GEMINI_REST`, `GENERIC_JSON_VISION`).
  - **Safety & Validation**: Never invents medicine names, strengths, dosage instructions, or quantities. Unreadable or missing values are automatically flagged as **`Needs Review`**. Missing quantities are never calculated automatically.
  - If the AI API key or endpoint is not configured, the app displays a clear **AI Provider Setup Required** notice and never returns fake results.
- **Verified Medicine Catalogue Matching (`MedicineCatalogueMatcher.kt`)**:
  - Import your existing pharmacy **Item Master CSV** (`item_code,item_name,generic_name,unit,pack_size,mrp,gst_percent,hsn_code`).
  - Matches extracted medicines against verified item names, units, pack sizes, MRP, and GST%. Requires manual pharmacist selection whenever multiple items match or a medicine is uncertain. Never invents prices, GST rates, stock availability, or batch numbers.
- **Zero-Trust Persistence & Security (`firestore.rules`)**:
  - Backed by **Firebase Authentication** (Google Sign-In via Jetpack Credential Manager) and **Cloud Firestore** (Enterprise named database).
  - Strict per-pharmacy isolation (`userId == request.auth.uid`), schema validation (`isValid[Entity]`), terminal state locking on `COMPLETED` prescriptions, and append-only audit logs (`/audit_logs/{logId}`).

---

## 2. Required Configuration (`.env` / AI Studio Secrets Panel)

Configure the following secrets in the **Secrets panel in AI Studio** (propagated via `.env` / `.env.example` into `BuildConfig`):

| Variable | Purpose | Example |
|---|---|---|
| `AI_API_KEY` | API key for your chosen Vision AI provider | `sk-...` or provider key |
| `AI_API_ENDPOINT` | Full HTTPS endpoint for multimodal extraction | `https://api.openai.com/v1/chat/completions` |
| `AI_MODEL_NAME` | Vision model identifier | `gpt-4o`, `claude-3-5-sonnet`, `gemini-3.5-flash` |
| `AI_AUTH_METHOD` | `BEARER_HEADER`, `X_API_KEY_HEADER`, or `QUERY_PARAM_KEY` | `BEARER_HEADER` |
| `AI_REQUEST_FORMAT` | `OPENAI_VISION`, `GEMINI_REST`, or `GENERIC_JSON_VISION` | `OPENAI_VISION` |
| `DESKTOP_BRIDGE_API_TOKEN` | Shared token for future Python/PySide6 desktop connector | `bridge_secret_token` |

Non-secret endpoint, model name, authentication header style, and payload format can also be updated at runtime in **Settings → AI Provider Configuration**.

---

## 3. Python / PySide6 Desktop Integration Contract

This first build keeps your existing **Python / PySide6 / SQLite** desktop application untouched and clearly displays **`Desktop Not Connected`** in the UI.

When implementing the Python desktop Counter Sale connector:

1. **State Machine**:
   `PENDING → ANALYZING → NEEDS_REVIEW → APPROVED → IMPORTED → COMPLETED`
2. **Recoverable Multi-Counter Claim Lock**:
   - Before importing an `APPROVED` prescription (`readyForCounterSale == true`), the counter terminal (`COUNTER-1`, `COUNTER-2`, etc.) acquires a claim lock (`claimedByCounterId`, `claimToken`, `claimExpiresAtEpochMs` with a 5-minute TTL).
   - If another counter holds an active, non-expired claim, concurrent claims are rejected.
   - If a counter crashes and `claimExpiresAtEpochMs <= now`, another counter can safely recover the claim.
3. **Duplicate-Import Protection**:
   - Once transitioned to `IMPORTED` or `COMPLETED`, any subsequent import request is rejected.
4. **Structured JSON Export Schema (`toDesktopExportJson`)**:
   - Contains `schemaVersion`, `prescriptionId`, `requestCode`, `status`, `claimToken`, `patient`, `prescriptionMetadata`, and `lineItems[]` with `itemMasterMatch` (`itemCode`, `itemName`, `unit`, `packSize`, `verifiedMrp`, `verifiedGstPercent`, while leaving `stockAvailability` and `batchNumber` as `null` to be resolved from your local SQLite stock/batch tables at the counter).
