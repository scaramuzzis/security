/**
 * Operazioni loyalty (docs/05 §G): accredito acquisti in cassa,
 * coupon a soglie, scadenza punti FIFO, riconciliazione notturna.
 */

import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { createHash, randomBytes } from "node:crypto";
import { applyLoyaltyTransaction } from "./loyalty";

const REGION = "europe-west1";
const STORE_ID = "libreribra-sangiovanni";
const MAX_PURCHASE_EUR_STAFF = 300; // oltre: serve ruolo admin
const COUPON_VALIDITY_DAYS = 90;

export const COUPON_TIERS: Record<number, { value: number }> = {
  200: { value: 5 },
  500: { value: 15 },
  900: { value: 30 },
};

function hashPin(pin: string, uid: string): string {
  return createHash("sha256").update(`${pin}:${uid}`).digest("hex");
}

async function verifyStaffPin(staffUid: string, pin: string): Promise<void> {
  const snap = await getFirestore().doc(`users/${staffUid}`).get();
  const stored = snap.data()?.staff_pin_hash as string | undefined;
  if (!stored || stored !== hashPin(pin, staffUid)) {
    throw new HttpsError("permission-denied", "PIN operatore non valido.");
  }
}

/** Solo admin: imposta il PIN di cassa di un membro dello staff. */
export const setStaffPin = onCall(async (request) => {
  if (request.auth?.token?.role !== "admin") {
    throw new HttpsError("permission-denied", "Riservato all'amministratore.");
  }
  const { staffUid, pin } = request.data ?? {};
  if (typeof staffUid !== "string" || !/^\d{4,6}$/.test(pin ?? "")) {
    throw new HttpsError("invalid-argument", "staffUid e PIN numerico (4-6 cifre) richiesti.");
  }
  const db = getFirestore();
  await db.doc(`users/${staffUid}`).update({
    staff_pin_hash: hashPin(pin, staffUid),
    updated_at: FieldValue.serverTimestamp(),
  });
  await db.collection("admin_logs").add({
    actor_uid: request.auth!.uid,
    action: "role_change",
    target_ref: `users/${staffUid}`,
    payload: { set: "staff_pin" },
    at: FieldValue.serverTimestamp(),
    store_id: STORE_ID,
  });
  return { ok: true };
});

/**
 * Accredito punti per acquisto in cassa: lo staff scansiona il QR wallet
 * del cliente e inserisce importo + PIN. 1 punto per € (arrotondato giù).
 * Idempotente per receiptId (numero scontrino).
 */
export const creditPurchasePoints = onCall(async (request) => {
  const role = request.auth?.token?.role as string | undefined;
  if (!role || !["staff", "admin"].includes(role)) {
    throw new HttpsError("permission-denied", "Riservato allo staff.");
  }
  const { customerUid, amountEur, receiptId, pin } = request.data ?? {};
  if (
    typeof customerUid !== "string" ||
    typeof receiptId !== "string" ||
    receiptId.length === 0 ||
    typeof amountEur !== "number" ||
    !Number.isFinite(amountEur) ||
    amountEur <= 0
  ) {
    throw new HttpsError("invalid-argument", "customerUid, amountEur e receiptId richiesti.");
  }
  if (amountEur > MAX_PURCHASE_EUR_STAFF && role !== "admin") {
    throw new HttpsError(
      "failed-precondition",
      `Importi oltre ${MAX_PURCHASE_EUR_STAFF}€ richiedono conferma dell'amministratore.`
    );
  }
  await verifyStaffPin(request.auth!.uid, pin ?? "");

  const points = Math.floor(amountEur);
  if (points < 1) {
    throw new HttpsError("invalid-argument", "Importo troppo basso per generare punti.");
  }

  const result = await applyLoyaltyTransaction({
    uid: customerUid,
    amount: points,
    source: "purchase",
    refId: receiptId,
    operatorUid: request.auth!.uid,
  });
  return {
    pointsCredited: result.duplicate ? 0 : points,
    balance: result.balanceAfter,
    duplicate: result.duplicate,
  };
});

function makeCouponCode(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = randomBytes(8);
  let s = "";
  for (const b of bytes) s += alphabet[b % alphabet.length];
  return `LB-${s.slice(0, 4)}-${s.slice(4)}`;
}

/**
 * Riscatto punti → coupon sconto (soglie 200/500/900).
 * Debito punti e creazione coupon nella stessa transazione.
 */
export const redeemCoupon = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Accesso richiesto.");

  const tierPoints = request.data?.tierPoints;
  const tier = COUPON_TIERS[tierPoints as number];
  if (!tier) {
    throw new HttpsError(
      "invalid-argument",
      `Soglie disponibili: ${Object.keys(COUPON_TIERS).join(", ")} punti.`
    );
  }

  const db = getFirestore();
  const code = makeCouponCode();
  const result = await applyLoyaltyTransaction({
    uid,
    amount: -tierPoints,
    source: "coupon_redeem",
    refId: code,
    extraWrites: (t) => {
      t.set(db.doc(`discounts/${code}`), {
        code,
        type: "amount",
        value: tier.value,
        owner_uid: uid,
        status: "active",
        source: "loyalty",
        expires_at: Timestamp.fromMillis(
          Date.now() + COUPON_VALIDITY_DAYS * 24 * 3600 * 1000
        ),
        redeemed_by_operator: null,
        store_id: STORE_ID,
        created_at: FieldValue.serverTimestamp(),
        updated_at: FieldValue.serverTimestamp(),
      });
    },
  });

  return { code, valueEur: tier.value, balance: result.balanceAfter };
});

/** Redemption del coupon in cassa (staff + PIN). */
export const markCouponRedeemed = onCall(async (request) => {
  const role = request.auth?.token?.role as string | undefined;
  if (!role || !["staff", "admin"].includes(role)) {
    throw new HttpsError("permission-denied", "Riservato allo staff.");
  }
  const { code, pin } = request.data ?? {};
  if (typeof code !== "string" || code.length === 0) {
    throw new HttpsError("invalid-argument", "Codice coupon richiesto.");
  }
  await verifyStaffPin(request.auth!.uid, pin ?? "");

  const db = getFirestore();
  return db.runTransaction(async (t) => {
    const ref = db.doc(`discounts/${code}`);
    const snap = await t.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "Coupon inesistente.");
    const c = snap.data()!;
    if (c.status !== "active") {
      throw new HttpsError("failed-precondition", `Coupon già ${c.status === "redeemed" ? "utilizzato" : "scaduto"}.`);
    }
    if ((c.expires_at as Timestamp).toMillis() < Date.now()) {
      throw new HttpsError("failed-precondition", "Coupon scaduto.");
    }
    t.update(ref, {
      status: "redeemed",
      redeemed_by_operator: request.auth!.uid,
      updated_at: FieldValue.serverTimestamp(),
    });
    return { ok: true, valueEur: c.value };
  });
});

/**
 * Scadenza punti (equivalenza FIFO): per ogni utente,
 * da_scadere = max(0, accrediti_scaduti − spese − scadenze_precedenti),
 * limitato al saldo corrente. Job giornaliero.
 */
export async function expireUserPoints(uid: string): Promise<number> {
  const db = getFirestore();
  const txs = await db.collection(`users/${uid}/loyalty_transactions`).get();

  let expiredEarnings = 0;
  let spent = 0;
  let alreadyExpired = 0;
  let balance = 0;
  const now = Date.now();

  for (const d of txs.docs) {
    const tx = d.data();
    balance += tx.amount as number;
    if (tx.source === "expiry") {
      alreadyExpired += -(tx.amount as number);
    } else if ((tx.amount as number) > 0) {
      const exp = tx.earned_expires_at as Timestamp | null;
      if (exp && exp.toMillis() < now) expiredEarnings += tx.amount as number;
    } else {
      spent += -(tx.amount as number);
    }
  }

  const toExpire = Math.min(
    Math.max(0, expiredEarnings - spent - alreadyExpired),
    balance
  );
  if (toExpire <= 0) return 0;

  await applyLoyaltyTransaction({
    uid,
    amount: -toExpire,
    source: "expiry",
    refId: `expiry_${new Date().toISOString().slice(0, 10)}`,
  });
  return toExpire;
}

export const expirePointsJob = onSchedule(
  { schedule: "every day 04:00", timeZone: "Europe/Rome", region: REGION },
  async () => {
    const db = getFirestore();
    const wallets = await db.collection("loyalty_wallet").where("balance", ">", 0).get();
    for (const w of wallets.docs) {
      await expireUserPoints(w.id);
    }
  }
);

/**
 * Riconciliazione notturna (invariante n.2): balance == Σ transazioni.
 * Un drift non viene corretto in silenzio: finisce in admin_logs.
 */
export async function reconcileWallet(uid: string): Promise<number> {
  const db = getFirestore();
  const [wallet, txs] = await Promise.all([
    db.doc(`loyalty_wallet/${uid}`).get(),
    db.collection(`users/${uid}/loyalty_transactions`).get(),
  ]);
  const balance = (wallet.data()?.balance as number) ?? 0;
  const computed = txs.docs.reduce((sum, d) => sum + (d.data().amount as number), 0);
  const drift = balance - computed;
  if (drift !== 0) {
    await db.collection("admin_logs").add({
      actor_uid: "system",
      action: "wallet_drift_detected",
      target_ref: `loyalty_wallet/${uid}`,
      payload: { balance, computed, drift },
      at: FieldValue.serverTimestamp(),
      store_id: STORE_ID,
    });
  }
  return drift;
}

export const reconcileWalletsJob = onSchedule(
  { schedule: "every day 05:00", timeZone: "Europe/Rome", region: REGION },
  async () => {
    const db = getFirestore();
    const wallets = await db.collection("loyalty_wallet").get();
    for (const w of wallets.docs) {
      await reconcileWallet(w.id);
    }
  }
);
