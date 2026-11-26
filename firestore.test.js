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

const safeNow = () => new Date(Date.now() - 1000);
const futureTs = () => new Date(Date.now() + 60000);

function validMinimalUserDoc() {
  return {
    email: "alice@example.com",
    balance: 10000.0,
    leverage: 500,
    currency: "USD",
    createdAt: safeNow(),
  };
}

function validOpenTradeDoc(overrides = {}) {
  return {
    symbol: "XAUUSD",
    type: "BUY",
    lots: 0.1,
    openPrice: 4165.54,
    sl: 4150.0,
    tp: 4195.0,
    commission: 0.7,
    swap: 0.0,
    openTime: safeNow(),
    ...overrides,
  };
}

function validTradeHistoryDoc(overrides = {}) {
  return {
    symbol: "XAUUSD",
    type: "BUY",
    lots: 0.1,
    openPrice: 4165.54,
    closePrice: 4175.54,
    profit: 100.0,
    commission: 0.7,
    swap: -0.3,
    netProfit: 99.0,
    openTime: new Date(Date.now() - 60000),
    closeTime: safeNow(),
    ...overrides,
  };
}

test("Unauthenticated users are denied access to users, open_trades, and trade_history", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validMinimalUserDoc())
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("open_trades").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("open_trades")
      .doc("t1")
      .set(validOpenTradeDoc())
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("trade_history").get()
  );
});

test("Owner can create, read, and update users/{uid} with minimal cross-platform and full Android fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  const created = {
    ...validMinimalUserDoc(),
    displayName: "Alice Trader",
    photoURL: "https://lh3.googleusercontent.com/a/example-photo",
  };
  await assertSucceeds(userRef.set(created));
  await assertSucceeds(userRef.get());

  await assertSucceeds(
    userRef.update({
      displayName: "Alice Pro",
      photoURL: "https://lh3.googleusercontent.com/a/updated-photo",
      balance: 10250.75,
      deposit: 10000.0,
      realizedProfit: 251.45,
      totalCommission: -0.7,
      totalSwap: 0.0,
      accountName: "Fusion Pro",
      brokerName: "Fusion Markets Pty Ltd",
      accountId: "420784 - FusionMarkets-Real",
      serverName: "Local Access Server",
      isReal: true,
      logoType: "AUTO",
      updatedAt: safeNow(),
    })
  );

  // Unsafe non-HTTPS photoURL is rejected
  await assertFails(
    userRef.update({
      photoURL: "javascript:alert(1)",
    })
  );

  // Immutable createdAt cannot be modified on update
  await assertFails(
    userRef.update({
      createdAt: new Date(Date.now() - 500000),
    })
  );

  // Ghost fields are rejected
  await assertFails(
    userRef.update({
      isAdmin: true,
    })
  );

  // String balance is rejected (must be numeric)
  await assertFails(
    userRef.update({
      balance: "10250.75",
    })
  );
});

test("Owner can create, list ordered by openTime desc, modify SL/TP, and delete open_trades", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const openCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("open_trades");

  const t1Ref = openCol.doc("trade_001");
  const t2Ref = openCol.doc("trade_002");

  await assertSucceeds(
    t1Ref.set(
      validOpenTradeDoc({
        openTime: new Date(Date.now() - 20000),
        ticketId: 385000101,
      })
    )
  );
  await assertSucceeds(
    t2Ref.set(
      validOpenTradeDoc({
        type: "SELL",
        lots: 0.25,
        openTime: new Date(Date.now() - 5000),
      })
    )
  );

  const listSnap = await assertSucceeds(
    openCol.orderBy("openTime", "desc").get()
  );
  if (listSnap.docs.length !== 2 || listSnap.docs[0].id !== "trade_002") {
    throw new Error("Expected 2 open trades ordered by openTime desc");
  }

  // Modify SL/TP without touching openTime
  await assertSucceeds(
    t1Ref.update({
      sl: 4155.0,
      tp: 4205.0,
      updatedAt: safeNow(),
    })
  );

  // Cannot mutate immutable openTime
  await assertFails(
    t1Ref.update({
      openTime: safeNow(),
    })
  );

  // Cannot store live floating P&L or currentPrice on open_trades (free tier protection)
  await assertFails(
    t1Ref.update({
      profit: 45.2,
      currentPrice: 4170.0,
    })
  );

  await assertSucceeds(t2Ref.delete());
});

test("Invalid numeric types, invalid trade type, and future timestamps are rejected on open_trades and trade_history", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const openCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("open_trades");
  const histCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("trade_history");

  await assertFails(
    openCol.doc("bad_lots").set(validOpenTradeDoc({ lots: "0.10" }))
  );
  await assertFails(
    openCol.doc("bad_type").set(validOpenTradeDoc({ type: "HOLD" }))
  );
  await assertFails(
    openCol.doc("future_open").set(validOpenTradeDoc({ openTime: futureTs() }))
  );
  await assertFails(
    histCol
      .doc("bad_net")
      .set(validTradeHistoryDoc({ netProfit: "99.00" }))
  );
  await assertFails(
    histCol
      .doc("future_close")
      .set(validTradeHistoryDoc({ closeTime: futureTs() }))
  );
});

test("Atomic runTransaction closes open trade, writes trade_history with netProfit, and updates user balance", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);
  const openRef = userRef.collection("open_trades").doc("trade_tx_1");
  const dealRef = userRef.collection("trade_history").doc("deal_tx_1");

  await assertSucceeds(userRef.set(validMinimalUserDoc()));
  await assertSucceeds(
    openRef.set(
      validOpenTradeDoc({
        openTime: new Date(Date.now() - 30000),
      })
    )
  );

  await assertSucceeds(
    aliceDb.runTransaction(async (tx) => {
      const openSnap = await tx.get(openRef);
      const userSnap = await tx.get(userRef);
      if (!openSnap.exists) {
        throw new Error("Open trade missing");
      }
      const openData = openSnap.data();
      const currentBalance = userSnap.data().balance;
      const grossProfit = 100.0;
      const commission = openData.commission;
      const swap = -0.3;
      const netProfit = grossProfit - commission + swap;

      tx.delete(openRef);
      tx.set(dealRef, {
        symbol: openData.symbol,
        type: openData.type,
        lots: openData.lots,
        openPrice: openData.openPrice,
        closePrice: 4175.54,
        profit: grossProfit,
        commission,
        swap,
        netProfit,
        openTime: openData.openTime,
        closeTime: safeNow(),
        sl: openData.sl,
        tp: openData.tp,
        isWithdrawal: false,
        hitOutcome: "TP_HIT",
        updatedAt: safeNow(),
      });
      tx.update(userRef, {
        balance: currentBalance + netProfit,
        realizedProfit: grossProfit,
        totalCommission: -commission,
        totalSwap: swap,
        updatedAt: safeNow(),
      });
    })
  );

  const histSnap = await assertSucceeds(
    userRef.collection("trade_history").orderBy("closeTime", "desc").get()
  );
  if (histSnap.docs.length !== 1 || histSnap.docs[0].data().netProfit !== 99.0) {
    throw new Error("Expected 1 closed trade with netProfit = 99.0");
  }
});

test("Cross-user isolation blocks Bob from accessing Alice's account, open_trades, and trade_history", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceUserRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(aliceUserRef.set(validMinimalUserDoc()));
  await assertSucceeds(
    aliceUserRef.collection("open_trades").doc("t1").set(validOpenTradeDoc())
  );
  await assertSucceeds(
    aliceUserRef
      .collection("trade_history")
      .doc("d1")
      .set(validTradeHistoryDoc())
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const targetUserRef = bobDb.collection("users").doc(ALICE_UID);

  await assertFails(targetUserRef.get());
  await assertFails(targetUserRef.update({ balance: 999999 }));
  await assertFails(targetUserRef.delete());
  await assertFails(targetUserRef.collection("open_trades").get());
  await assertFails(targetUserRef.collection("open_trades").doc("t1").get());
  await assertFails(
    targetUserRef.collection("open_trades").doc("t1").update({ sl: 1.0 })
  );
  await assertFails(targetUserRef.collection("open_trades").doc("t1").delete());
  await assertFails(targetUserRef.collection("trade_history").get());
  await assertFails(targetUserRef.collection("trade_history").doc("d1").get());
  await assertFails(
    targetUserRef.collection("trade_history").doc("d1").delete()
  );
});

test("Legacy collection paths (positions, history, accounts) are denied by default-deny", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertFails(userRef.collection("positions").get());
  await assertFails(
    userRef.collection("positions").doc("p1").set(validOpenTradeDoc())
  );
  await assertFails(userRef.collection("history").get());
  await assertFails(userRef.collection("accounts").doc("main").get());
});
