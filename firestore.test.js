const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_1";
const BOB_UID = "bob_student_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validRoutinePayload(uid, offsetMs = 5000) {
  const ts = new Date(Date.now() - offsetMs);
  return {
    userId: uid,
    title: "Calculus Lecture",
    subject: "Mathematics",
    location: "Room 204",
    dayOfWeek: 1,
    startMinuteOfDay: 540,
    durationMinutes: 60,
    reminderMinutesBefore: 15,
    reminderEnabled: true,
    notes: "Bring graphing calculator",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validAssignmentPayload(uid, offsetMs = 5000) {
  const ts = new Date(Date.now() - offsetMs);
  return {
    userId: uid,
    title: "Chemistry Lab Report",
    subject: "Chemistry",
    details: "Titration analysis and error bounds",
    dueAtMillis: Date.now() + 86400000,
    priority: "HIGH",
    completed: false,
    reminderMinutesBefore: 60,
    reminderEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validStudySessionPayload(uid, offsetMs = 5000) {
  const ts = new Date(Date.now() - offsetMs);
  return {
    userId: uid,
    title: "Organic Chem Review",
    subject: "Chemistry",
    scheduledAtMillis: Date.now() + 3600000,
    durationMinutes: 45,
    reminderMinutesBefore: 10,
    reminderEnabled: true,
    completed: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated users are denied on all StudyPulse collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection(`users/${ALICE_UID}/routines`).get());
  await assertFails(unauth.collection(`users/${ALICE_UID}/assignments`).get());
  await assertFails(unauth.collection(`users/${ALICE_UID}/study_sessions`).get());
  await assertFails(
    unauth
      .collection(`users/${ALICE_UID}/routines`)
      .doc("r1")
      .set(validRoutinePayload(ALICE_UID))
  );
});

test("Cross-user isolation: Bob cannot read, create, update, or delete Alice's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection(`users/${ALICE_UID}/routines`)
      .doc("r1")
      .set(validRoutinePayload(ALICE_UID));
    await db
      .collection(`users/${ALICE_UID}/assignments`)
      .doc("a1")
      .set(validAssignmentPayload(ALICE_UID));
    await db
      .collection(`users/${ALICE_UID}/study_sessions`)
      .doc("s1")
      .set(validStudySessionPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/routines`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/assignments`).doc("a1").get());
  await assertFails(
    bobDb
      .collection(`users/${ALICE_UID}/assignments`)
      .doc("a1")
      .update({ completed: true, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/study_sessions`).doc("s1").delete());
  await assertFails(
    bobDb
      .collection(`users/${ALICE_UID}/routines`)
      .doc("r2")
      .set(validRoutinePayload(BOB_UID))
  );
});

test("Owner can list, create, update, and delete routines with valid payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const routines = aliceDb.collection(`users/${ALICE_UID}/routines`);

  // Empty list query succeeds
  await assertSucceeds(routines.get());

  // Valid create succeeds
  await assertSucceeds(routines.doc("routine_1").set(validRoutinePayload(ALICE_UID)));

  // Partial toggle reminderEnabled succeeds
  await assertSucceeds(
    routines.doc("routine_1").update({
      reminderEnabled: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Full edit succeeds
  await assertSucceeds(
    routines.doc("routine_1").update({
      title: "Calculus II Seminar",
      subject: "Mathematics",
      location: "Hall B",
      dayOfWeek: 3,
      startMinuteOfDay: 600,
      durationMinutes: 90,
      reminderMinutesBefore: 30,
      reminderEnabled: true,
      notes: "Updated room",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / @DocumentId 'id' field on create
  await assertFails(
    routines.doc("routine_bad").set({
      ...validRoutinePayload(ALICE_UID),
      id: "routine_bad",
    })
  );

  // Reject immutable createdAt mutation on update
  await assertFails(
    routines.doc("routine_1").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject future timestamp
  await assertFails(
    routines.doc("routine_future").set(validRoutinePayload(ALICE_UID, -60000))
  );

  await assertSucceeds(routines.doc("routine_1").delete());
});

test("Owner assignments enforce priority enum, bounds, and partial completion", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const assignments = aliceDb.collection(`users/${ALICE_UID}/assignments`);

  await assertSucceeds(assignments.doc("a1").set(validAssignmentPayload(ALICE_UID)));

  // Toggle completed
  await assertSucceeds(
    assignments.doc("a1").update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid priority rejected
  await assertFails(
    assignments.doc("a2").set({
      ...validAssignmentPayload(ALICE_UID),
      priority: "URGENT",
    })
  );

  // Ghost field update rejected
  await assertFails(
    assignments.doc("a1").update({
      completed: false,
      extraAdminFlag: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(assignments.doc("a1").delete());
});

test("Owner study sessions enforce duration bounds and allow completion/edit/delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessions = aliceDb.collection(`users/${ALICE_UID}/study_sessions`);

  await assertSucceeds(sessions.doc("s1").set(validStudySessionPayload(ALICE_UID)));
  await assertSucceeds(
    sessions.doc("s1").update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Out-of-bounds duration rejected
  await assertFails(
    sessions.doc("s_bad").set({
      ...validStudySessionPayload(ALICE_UID),
      durationMinutes: 2,
    })
  );

  await assertSucceeds(sessions.doc("s1").delete());
});
