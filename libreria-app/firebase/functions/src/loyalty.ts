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
  /**
   * Scritture aggiuntive eseguite NELLA STESSA transazione del movimento
   * punti (es. creazione coupon, biglietto lotteria): o tutto o niente.
   * Non eseguite se la transazione risulta duplicata.
   */
  extraWrites?: (t: FirebaseFirestore.Transaction) => void;
  /**
   * Verifiche (SOLO letture) eseguite prima del movimento, nella stessa
   * transazione: se lancia, l'intera operazione è annullata.
   */
  preCheck?: (t: FirebaseFirestore.Transaction) => Promise<void>;
  /**
   * Ricalcola l'importo dopo preCheck (es. costo letto dal DB nella
   * stessa transazione). Deve restituire un intero non nullo.
   */
  amountOverride?: () => number;
}

export async function applyLoyaltyTransaction(
  input: LoyaltyTxInput
): Promise<{ balanceAfter: number; duplicate: boolean }> {
  const { uid, amount, source, refId, operatorUid, extraWrites, preCheck, amountOverride } =
    input;
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

    if (preCheck) await preCheck(t);

    const effAmount = amountOverride ? amountOverride() : amount;
    if (!Number.isInteger(effAmount) || effAmount === 0) {
      throw new HttpsError("invalid-argument", "Importo punti non valido.");
    }

    const wallet = await t.get(walletRef);
    if (!wallet.exists) {
      throw new HttpsError("not-found", "Wallet inesistente.");
    }
    const balance = (wallet.data()?.balance as number) ?? 0;
    const balanceAfter = balance + effAmount;
    if (balanceAfter < 0) {
      throw new HttpsError(
        "failed-precondition",
        "Punti insufficienti per questa operazione."
      );
    }

    const expiresAt =
      effAmount > 0
        ? Timestamp.fromMillis(
            Date.now() + POINTS_VALIDITY_MONTHS * 30 * 24 * 3600 * 1000
          )
        : null;

    t.set(txRef, {
      amount: effAmount,
      source,
      ref_id: refId ?? null,
      operator_uid: operatorUid ?? null,
      earned_expires_at: expiresAt,
      balance_after: balanceAfter,
      created_at: FieldValue.serverTimestamp(),
    });

    t.update(walletRef, {
      balance: balanceAfter,
      ...(effAmount > 0
        ? { earned_this_year: FieldValue.increment(effAmount) }
        : {}),
      updated_at: FieldValue.serverTimestamp(),
    });

    if (extraWrites) extraWrites(t);

    return { balanceAfter, duplicate: false };
  });
}
