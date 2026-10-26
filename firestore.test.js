const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_trader_1";
const BOB_UID = "bob_trader_2";

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

const serverTs = () => new Date();

function validPositionPayload(userId) {
  return {
    userId,
    ticketId: 1001,
    symbol: "XAUUSD",
    isBuy: true,
    lots: 0.1,
    openPrice: 4165.54,
    currentPrice: 4165.54,
    profit: 0.0,
    openTime: "2026.10.19 10:00:00",
    createdAt: serverTs(),
    updatedAt: serverTs(),
  };
}

function validHistoryPayload(userId) {
  return {
    userId,
    ticketId: 1001,
    symbol: "XAUUSD",
    isBuy: true,
    lots: 0.1,
    openPrice: 4165.54,
    closePrice: 4168.54,
    profit: 30.0,
    openTime: "2026.10.19 10:00:00",
    closeTime: "2026.10.19 10:15:00",
    commission: -0.5,
    swap: 0.0,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  };
}

function validAccountPayload(userId) {
  return {
    userId,
    balance: 518.16,
    deposit: 674.08,
    realizedProfit: -139.83,
    totalSwap: 0.0,
    totalCommission: -15.4,
    accountName: "REAL 21",
    brokerName: "Fusion Markets Pty Ltd",
    accountId: "420784 - FusionMarkets-Real",
    serverName: "Local Access Server",
    isReal: true,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  };
}

test("Unauthenticated user cannot read or write positions, history, or accounts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("positions").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("positions")
      .doc("pos_1")
      .set(validPositionPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("history").get()
  );
});

test("Authenticated user can create, read, update, and delete their own position", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const posRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("positions")
    .doc("pos_1001");

  await assertSucceeds(posRef.set(validPositionPayload(ALICE_UID)));
  await assertSucceeds(posRef.get());
  await assertSucceeds(
    posRef.update({
      currentPrice: 4167.24,
      profit: 17.0,
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(posRef.delete());
});

test("Authenticated user cannot read or write another user's positions or history", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("history")
      .doc("hist_1001")
      .set(validHistoryPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("history")
      .doc("hist_1001")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("history")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Shadow update with extra unauthorized field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badPayload = {
    ...validPositionPayload(ALICE_UID),
    isAdminOverride: true,
  };
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("positions")
      .doc("pos_bad")
      .set(badPayload)
  );
});

test("Authenticated user can create and update their own trading account document", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const accRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("accounts")
    .doc("main");

  await assertSucceeds(accRef.set(validAccountPayload(ALICE_UID)));
  await assertSucceeds(
    accRef.update({
      balance: 548.16,
      realizedProfit: -109.83,
      updatedAt: serverTs(),
    })
  );
});
