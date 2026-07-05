/**
 * Gestione ruoli (docs/03): l'admin promuove/revoca lo staff dal backoffice.
 * Il ruolo 'admin' NON si assegna via API: solo con lo script
 * firebase/scripts/make-admin.mjs (superficie d'attacco minima).
 */

import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";

const STORE_ID = "libreribra-sangiovanni";
const ASSIGNABLE_ROLES = ["parent", "staff"];

export const setUserRole = onCall(async (request) => {
  if (request.auth?.token?.role !== "admin") {
    throw new HttpsError("permission-denied", "Riservato all'amministratore.");
  }
  const { targetUid, role } = request.data ?? {};
  if (typeof targetUid !== "string" || !ASSIGNABLE_ROLES.includes(role)) {
    throw new HttpsError(
      "invalid-argument",
      `targetUid e role (${ASSIGNABLE_ROLES.join("|")}) richiesti. Il ruolo admin si assegna solo da script.`
    );
  }
  if (targetUid === request.auth!.uid) {
    throw new HttpsError("failed-precondition", "Non puoi cambiare il tuo stesso ruolo.");
  }

  const db = getFirestore();
  const userRef = db.doc(`users/${targetUid}`);
  const snap = await userRef.get();
  if (!snap.exists) throw new HttpsError("not-found", "Utente non trovato.");
  if (snap.data()?.role === "admin") {
    throw new HttpsError("failed-precondition", "Il ruolo di un admin non si cambia via API.");
  }

  await getAuth().setCustomUserClaims(targetUid, { role });
  await userRef.update({ role, updated_at: FieldValue.serverTimestamp() });
  await db.collection("admin_logs").add({
    actor_uid: request.auth!.uid,
    action: "role_change",
    target_ref: `users/${targetUid}`,
    payload: { new_role: role },
    at: FieldValue.serverTimestamp(),
    store_id: STORE_ID,
  });
  // Nota operativa: il claim entra in vigore al prossimo refresh del token
  // (max 1h) o al prossimo login dell'utente.
  return { ok: true, role };
});
