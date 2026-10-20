const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_pharmacy_1";
const BOB_UID = "bob_pharmacy_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTimestamp() {
  return new Date(Date.now() - 1000);
}

function validPrescriptionPayload(userId, overrides = {}) {
  const ts = nowTimestamp();
  return {
    userId,
    requestCode: "RX-2026-1001",
    status: "PENDING",
    submittedByRole: "COUNTER_STAFF",
    submittedByName: "Counter Operator 1",
    patientName: "Sample Patient A",
    patientAge: "42",
    patientContact: "555-0100",
    staffNotes: "Waiting at counter 1",
    doctorName: "Dr. R. Sharma",
    prescriptionDate: "2026-10-20",
    imageLocalPath: "/data/user/0/rx_1001.jpg",
    imageBase64Preview: "",
    items: [
      {
        medicineName: "Paracetamol",
        strength: "650mg",
        dosageForm: "Tablet",
        dosageInstructions: "1-0-1 after meals",
        frequency: "BD",
        duration: "5 days",
        explicitQuantity: "10",
        needsReview: false,
      },
    ],
    needsReviewCount: 0,
    reviewedBy: "",
    reviewNotes: "",
    readyForCounterSale: false,
    claimedByCounterId: "",
    claimToken: "",
    claimExpiresAtEpochMs: 0,
    importedAtEpochMs: 0,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or create prescriptions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("prescriptions").get());
  await assertFails(
    unauthDb
      .collection("prescriptions")
      .doc("rx_1")
      .set(validPrescriptionPayload(ALICE_UID))
  );
});

test("Authenticated user: can create and query own prescriptions with userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("prescriptions")
      .doc("rx_alice_1")
      .set(validPrescriptionPayload(ALICE_UID))
  );

  await assertSucceeds(
    aliceDb
      .collection("prescriptions")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Authenticated user: fails list query without userId filter (rules are not filters)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("prescriptions").get());
});

test("Cross-user isolation: Alice cannot read or modify Bob's prescription", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb
      .collection("prescriptions")
      .doc("rx_bob_1")
      .set(validPrescriptionPayload(BOB_UID))
  );

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("prescriptions").doc("rx_bob_1").get());
  await assertFails(
    aliceDb.collection("prescriptions").doc("rx_bob_1").update({
      status: "APPROVED",
      updatedAt: nowTimestamp(),
    })
  );
});

test("Shadow update & invalid status rejection: rejects ghost fields and invalid enum", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("prescriptions")
      .doc("rx_ghost")
      .set(validPrescriptionPayload(ALICE_UID, { isVerifiedGhost: true }))
  );
  await assertFails(
    aliceDb
      .collection("prescriptions")
      .doc("rx_bad_status")
      .set(validPrescriptionPayload(ALICE_UID, { status: "HACKED_STATE" }))
  );
});

test("Terminal state locking: non-admin cannot update a COMPLETED prescription", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("prescriptions")
      .doc("rx_done")
      .set(validPrescriptionPayload(ALICE_UID, { status: "COMPLETED" }))
  );
  await assertFails(
    aliceDb.collection("prescriptions").doc("rx_done").update({
      status: "APPROVED",
      updatedAt: nowTimestamp(),
    })
  );
});

test("Audit logs: append-only (create succeeds, update fails)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("audit_logs").doc("log_1").set({
      userId: ALICE_UID,
      prescriptionId: "rx_alice_1",
      requestCode: "RX-2026-1001",
      action: "CREATED",
      actorName: "Counter Operator 1",
      actorRole: "COUNTER_STAFF",
      details: "Uploaded prescription photo",
      createdAt: nowTimestamp(),
    })
  );

  await assertFails(
    aliceDb.collection("audit_logs").doc("log_1").update({
      details: "Tampered log",
    })
  );
});
