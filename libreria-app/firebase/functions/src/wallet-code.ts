/**
 * QR wallet a rotazione: codici monouso a scadenza breve al posto
 * dell'UID statico. Uno screenshot inoltrato scade in 90 secondi
 * e comunque si brucia al primo utilizzo.
 */

import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { randomBytes } from "node:crypto";

const REGION = "europe-west1";
const CODE_TTL_S = 90;
const CODE_PREFIX = "W"; // distingue i codici dagli UID nel campo unico di cassa

function makeCode(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = randomBytes(8);
  let s = CODE_PREFIX;
  for (const b of bytes) s += alphabet[b % alphabet.length];
  return s;
}

export async function mintWalletCodeCore(
  uid: string
): Promise<{ code: string; expiresInS: number }> {
  const db = getFirestore();
  const code = makeCode();
  await db.doc(`wallet_codes/${code}`).set({
    uid,
    used: false,
    expires_at: Timestamp.fromMillis(Date.now() + CODE_TTL_S * 1000),
    created_at: FieldValue.serverTimestamp(),
  });
  return { code, expiresInS: CODE_TTL_S };
}

/** L'app la chiama all'apertura del wallet e poi ogni ~60s. */
export const mintWalletCode = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Accesso richiesto.");
  return mintWalletCodeCore(uid);
});

/**
 * Risolve il campo "cliente" della cassa: codice a rotazione (bruciato
 * in transazione: niente riuso, niente replay in parallelo) oppure,
 * in fallback, un UID diretto (procedura di emergenza per lo staff).
 */
export async function resolveWalletCustomer(customer: string): Promise<string> {
  const db = getFirestore();
  const input = customer.trim().toUpperCase();

  if (input.startsWith(CODE_PREFIX) && input.length === 9) {
    const ref = db.doc(`wallet_codes/${input}`);
    return db.runTransaction(async (t) => {
      const snap = await t.get(ref);
      if (!snap.exists) {
        throw new HttpsError("not-found", "Codice non riconosciuto: fai rigenerare il QR al cliente.");
      }
      const c = snap.data()!;
      if (c.used) {
        throw new HttpsError("failed-precondition", "Codice già utilizzato: fai rigenerare il QR al cliente.");
      }
      if ((c.expires_at as Timestamp).toMillis() < Date.now()) {
        throw new HttpsError("failed-precondition", "Codice scaduto: fai rigenerare il QR al cliente.");
      }
      t.update(ref, { used: true, used_at: FieldValue.serverTimestamp() });
      return c.uid as string;
    });
  }

  // Fallback UID (es. cliente senza telefono carico, cercato per email nel backoffice)
  const userSnap = await db.doc(`users/${customer.trim()}`).get();
  if (!userSnap.exists) {
    throw new HttpsError("not-found", "Cliente non trovato: controlla il codice o l'UID.");
  }
  return customer.trim();
}

/** Pulizia notturna dei codici scaduti (restano al massimo 24h). */
export const cleanupWalletCodes = onSchedule(
  { schedule: "every day 03:30", timeZone: "Europe/Rome", region: REGION },
  async () => {
    const db = getFirestore();
    const expired = await db
      .collection("wallet_codes")
      .where("expires_at", "<", Timestamp.now())
      .limit(500)
      .get();
    if (expired.empty) return;
    const batch = db.batch();
    expired.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
  }
);
