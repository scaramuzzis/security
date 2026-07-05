/**
 * Push FCM (docs/08): ogni notifica in-app creata dai moduli
 * (ordini, eventi, wishlist, lotteria) diventa anche una push.
 * Le transazionali passano sempre; le promozionali rispettano
 * le notification_prefs per categoria. Quiet hours 21–9 per le promo.
 */

import * as functionsV1 from "firebase-functions/v1";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

const REGION = "europe-west1";
const MAX_TOKENS_PER_USER = 5;

/** type notifica → categoria preferenze (null = transazionale, sempre inviata) */
const TYPE_TO_PREF_CATEGORY: Record<string, string | null> = {
  order_ready: null,
  order_expired: null,
  event_reminder: null,
  waitlist_promoted: null,
  restock: "novita",
  new_post: "novita",
  promo: "promozioni",
  lottery: "lotteria",
  points_expiring: null,
};

function inQuietHours(date = new Date()): boolean {
  const hourRome = Number(
    new Intl.DateTimeFormat("it-IT", {
      hour: "numeric",
      hour12: false,
      timeZone: "Europe/Rome",
    }).format(date)
  );
  return hourRome >= 21 || hourRome < 9;
}

export const registerDeviceToken = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Accesso richiesto.");
  const { token } = request.data ?? {};
  if (typeof token !== "string" || token.length < 10) {
    throw new HttpsError("invalid-argument", "Token FCM mancante.");
  }
  const db = getFirestore();
  await db.runTransaction(async (t) => {
    const ref = db.doc(`users/${uid}`);
    const snap = await t.get(ref);
    const tokens: string[] = (snap.data()?.fcm_tokens as string[]) ?? [];
    if (tokens.includes(token)) return;
    const next = [...tokens, token].slice(-MAX_TOKENS_PER_USER); // FIFO sui device
    t.update(ref, { fcm_tokens: next, updated_at: FieldValue.serverTimestamp() });
  });
  return { ok: true };
});

export const unregisterDeviceToken = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Accesso richiesto.");
  const { token } = request.data ?? {};
  if (typeof token !== "string") throw new HttpsError("invalid-argument", "Token mancante.");
  await getFirestore()
    .doc(`users/${uid}`)
    .update({
      fcm_tokens: FieldValue.arrayRemove(token),
      updated_at: FieldValue.serverTimestamp(),
    });
  return { ok: true };
});

/** Decide se una notifica va inviata come push. Pura e testabile. */
export function shouldSendPush(
  type: string,
  prefs: Record<string, boolean> | undefined,
  now = new Date()
): boolean {
  const category = TYPE_TO_PREF_CATEGORY[type] ?? null;
  if (category === null) return true; // transazionale
  if (prefs && prefs[category] === false) return false;
  if (inQuietHours(now)) return false; // promo mai 21–9
  return true;
}

export const onNotificationCreate = functionsV1
  .region(REGION)
  .firestore.document("users/{uid}/notifications/{notifId}")
  .onCreate(async (snap, context) => {
    const db = getFirestore();
    const notif = snap.data();
    const uid = context.params.uid as string;

    const userSnap = await db.doc(`users/${uid}`).get();
    const user = userSnap.data();
    const tokens: string[] = (user?.fcm_tokens as string[]) ?? [];
    if (tokens.length === 0) return;

    if (!shouldSendPush(notif.type as string, user?.notification_prefs)) return;

    const response = await getMessaging().sendEachForMulticast({
      tokens,
      notification: {
        title: notif.title as string,
        body: notif.body as string,
      },
      data: { deep_link: (notif.deep_link as string) ?? "" },
    });

    // Pulizia token invalidi (app disinstallata, token ruotato)
    const invalid = tokens.filter((_tok, i) => {
      const err = response.responses[i].error;
      return (
        err &&
        [
          "messaging/registration-token-not-registered",
          "messaging/invalid-registration-token",
        ].includes(err.code)
      );
    });
    if (invalid.length > 0) {
      await db.doc(`users/${uid}`).update({
        fcm_tokens: FieldValue.arrayRemove(...invalid),
      });
    }
  });
