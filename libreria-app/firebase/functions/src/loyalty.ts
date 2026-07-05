/**
 * Core del wallet fedeltà — UNICA via di scrittura su loyalty_wallet
 * e loyalty_transactions (event-sourced, append-only).
 * Invariante n.2 del data model: balance == Σ transazioni.
 */

import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

export type LoyaltySource =
  | "purchase"
  | "event_checkin"
  | "game"
  | "referral"
  | "welcome"
  | "coupon_redeem"
  | "lottery_ticket"
  | "lottery_refund"
  | "expiry"
  | "admin_adjust";

const POINTS_VALIDITY_MONTHS = 12;

export interface LoyaltyTxInput {
  uid: string;
  amount: number; // positivo = accredito, negativo = spesa
  source: LoyaltySource;
  /** Se presente, la transazione è idempotente: id = `${source}_${refId}` */
  refId?: string;
  operatorUid?: string;
}

export async function applyLoyaltyTransaction(
  input: LoyaltyTxInput
): Promise<{ balanceAfter: number; duplicate: boolean }> {
  const { uid, amount, source, refId, operatorUid } = input;
  if (!Number.isInteger(amount) || amount === 0) {
    throw new HttpsError("invalid-argument", "Importo punti non valido.");
  }

  const db = getFirestore();
  const walletRef = db.doc(`loyalty_wallet/${uid}`);
  const txCol = db.collection(`users/${uid}/loyalty_transactions`);
  const txRef = refId ? txCol.doc(`${source}_${refId}`) : txCol.doc();

  return db.runTransaction(async (t) => {
    if (refId) {
      const existing = await t.get(txRef);
      if (existing.exists) {
        const wallet = await t.get(walletRef);
        return {
          balanceAfter: (wallet.data()?.balance as number) ?? 0,
          duplicate: true,
        };
      }
    }

    const wallet = await t.get(walletRef);
    if (!wallet.exists) {
      throw new HttpsError("not-found", "Wallet inesistente.");
    }
    const balance = (wallet.data()?.balance as number) ?? 0;
    const balanceAfter = balance + amount;
    if (balanceAfter < 0) {
      throw new HttpsError(
        "failed-precondition",
        "Punti insufficienti per questa operazione."
      );
    }

    const expiresAt =
      amount > 0
        ? Timestamp.fromMillis(
            Date.now() + POINTS_VALIDITY_MONTHS * 30 * 24 * 3600 * 1000
          )
        : null;

    t.set(txRef, {
      amount,
      source,
      ref_id: refId ?? null,
      operator_uid: operatorUid ?? null,
      earned_expires_at: expiresAt,
      balance_after: balanceAfter,
      created_at: FieldValue.serverTimestamp(),
    });

    t.update(walletRef, {
      balance: balanceAfter,
      ...(amount > 0
        ? { earned_this_year: FieldValue.increment(amount) }
        : {}),
      updated_at: FieldValue.serverTimestamp(),
    });

    return { balanceAfter, duplicate: false };
  });
}
