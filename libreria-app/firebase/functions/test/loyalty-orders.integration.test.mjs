/**
 * Test di integrazione Sprint 4: wallet sotto concorrenza, atomicità
 * punti+coupon, scadenza FIFO, ciclo ordini prenota-e-ritira.
 * Esecuzione: npm run test:emulator.
 */
import { describe, it, expect } from "vitest";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error("Questi test girano solo contro l'emulatore (FIRESTORE_EMULATOR_HOST).");
}
if (getApps().length === 0) initializeApp({ projectId: "demo-libreribra" });
const db = getFirestore();

const { applyLoyaltyTransaction } = await import("../lib/loyalty.js");
const { expireUserPoints, reconcileWallet } = await import("../lib/loyalty-ops.js");
const { reserveBookCore } = await import("../lib/orders.js");

async function makeWallet(uid, balance = 0) {
  await db.doc(`loyalty_wallet/${uid}`).set({
    balance,
    earned_this_year: balance,
    tier: "lettore",
  });
  if (balance > 0) {
    // transazione di apertura coerente col saldo (invariante n.2)
    await db.collection(`users/${uid}/loyalty_transactions`).add({
      amount: balance,
      source: "admin_adjust",
      ref_id: "fixture",
      earned_expires_at: Timestamp.fromMillis(Date.now() + 365 * 24 * 3600 * 1000),
      balance_after: balance,
      created_at: Timestamp.now(),
    });
  }
}

describe("Wallet sotto concorrenza (invariante n.2)", () => {
  it("10 movimenti paralleli: saldo finale esatto e drift zero", { timeout: 30000 }, async () => {
    await makeWallet("w_conc", 100);
    const moves = [
      ...Array(5).fill(10),   // +50
      ...Array(5).fill(-20),  // -100
    ];
    const results = await Promise.allSettled(
      moves.map((amount, i) =>
        applyLoyaltyTransaction({
          uid: "w_conc",
          amount,
          source: amount > 0 ? "game" : "coupon_redeem",
          refId: `mv_${i}`,
        })
      )
    );
    expect(results.every((r) => r.status === "fulfilled")).toBe(true);

    const wallet = await db.doc("loyalty_wallet/w_conc").get();
    expect(wallet.data().balance).toBe(50); // 100 + 50 − 100

    expect(await reconcileWallet("w_conc")).toBe(0);
  });

  it("due spese parallele oltre il saldo: una sola passa", { timeout: 30000 }, async () => {
    await makeWallet("w_race", 30);
    const results = await Promise.allSettled([
      applyLoyaltyTransaction({ uid: "w_race", amount: -20, source: "coupon_redeem", refId: "a" }),
      applyLoyaltyTransaction({ uid: "w_race", amount: -20, source: "coupon_redeem", refId: "b" }),
    ]);
    const ok = results.filter((r) => r.status === "fulfilled");
    const ko = results.filter((r) => r.status === "rejected");
    expect(ok.length).toBe(1);
    expect(ko.length).toBe(1);
    expect(String(ko[0].reason)).toMatch(/insufficienti/);

    const wallet = await db.doc("loyalty_wallet/w_race").get();
    expect(wallet.data().balance).toBe(10);
  });

  it("stesso refId due volte: un solo movimento (idempotenza)", async () => {
    await makeWallet("w_idem", 0);
    await applyLoyaltyTransaction({ uid: "w_idem", amount: 20, source: "event_checkin", refId: "evX" });
    const dup = await applyLoyaltyTransaction({ uid: "w_idem", amount: 20, source: "event_checkin", refId: "evX" });
    expect(dup.duplicate).toBe(true);
    const wallet = await db.doc("loyalty_wallet/w_idem").get();
    expect(wallet.data().balance).toBe(20);
  });
});

describe("Atomicità punti + coupon (extraWrites)", () => {
  it("se i punti non bastano, il coupon NON viene creato", async () => {
    await makeWallet("w_coupon", 100);
    await expect(
      applyLoyaltyTransaction({
        uid: "w_coupon",
        amount: -200,
        source: "coupon_redeem",
        refId: "LB-FAIL-0000",
        extraWrites: (t) =>
          t.set(db.doc("discounts/LB-FAIL-0000"), { code: "LB-FAIL-0000" }),
      })
    ).rejects.toThrow(/insufficienti/);

    const coupon = await db.doc("discounts/LB-FAIL-0000").get();
    expect(coupon.exists).toBe(false);
    const wallet = await db.doc("loyalty_wallet/w_coupon").get();
    expect(wallet.data().balance).toBe(100); // intatto
  });

  it("se i punti bastano, debito e coupon avvengono insieme", async () => {
    await applyLoyaltyTransaction({
      uid: "w_coupon",
      amount: -50,
      source: "coupon_redeem",
      refId: "LB-OK-0000",
      extraWrites: (t) =>
        t.set(db.doc("discounts/LB-OK-0000"), { code: "LB-OK-0000", status: "active" }),
    });
    const coupon = await db.doc("discounts/LB-OK-0000").get();
    expect(coupon.exists).toBe(true);
    const wallet = await db.doc("loyalty_wallet/w_coupon").get();
    expect(wallet.data().balance).toBe(50);
  });
});

describe("Scadenza punti (equivalenza FIFO)", () => {
  it("guadagnati 100 (scaduti), spesi 30 → scadono 70, saldo 0, drift 0", async () => {
    await makeWallet("w_exp", 0);
    await applyLoyaltyTransaction({ uid: "w_exp", amount: 100, source: "purchase", refId: "r1" });
    // Forza la scadenza nel passato (fixture di test)
    const tx = await db.doc("users/w_exp/loyalty_transactions/purchase_r1").get();
    await tx.ref.update({ earned_expires_at: Timestamp.fromMillis(Date.now() - 1000) });
    await applyLoyaltyTransaction({ uid: "w_exp", amount: -30, source: "coupon_redeem", refId: "c1" });

    const expired = await expireUserPoints("w_exp");
    expect(expired).toBe(70);

    const wallet = await db.doc("loyalty_wallet/w_exp").get();
    expect(wallet.data().balance).toBe(0);
    expect(await reconcileWallet("w_exp")).toBe(0);

    // Rilanciare il job non scade nulla due volte
    expect(await expireUserPoints("w_exp")).toBe(0);
  });
});

describe("Prenota-e-ritira", () => {
  async function makeBook(id, availability = "in_stock") {
    await db.doc(`books/${id}`).set({ title: `Libro ${id}`, availability, price: 10 });
  }

  it("prenotazione incrementa reserved_count; doppia prenotazione attiva respinta", async () => {
    await makeBook("bk1");
    const { orderId } = await reserveBookCore("reader", "bk1");
    expect(orderId).toBeTruthy();

    const inv = await db.doc("inventory/bk1").get();
    expect(inv.data().reserved_count).toBe(1);

    await expect(reserveBookCore("reader", "bk1")).rejects.toThrow(/già una prenotazione/);
  });

  it("libro esaurito → messaggio che invita alla wishlist", async () => {
    await makeBook("bk_out", "out");
    await expect(reserveBookCore("reader", "bk_out")).rejects.toThrow(/lista dei desideri/);
  });
});
