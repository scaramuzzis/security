/**
 * LibreriBrà — Cloud Functions (Sprint 0: walking skeleton)
 *
 * Regola architetturale (docs/03-architecture.md): tutta la logica di dominio
 * vive qui. Il client FluxBuilder non scrive mai su collezioni economiche.
 *
 * Sprint 0 espone solo:
 *  - onUserCreate : bootstrap utente (doc users, wallet, custom claim 'parent')
 *  - healthCheck  : callable per verificare da FluxBuilder il collegamento
 *                   app → Functions → Firestore (criterio go/no-go dello spike)
 */

import * as functionsV1 from "firebase-functions/v1";
import { onCall } from "firebase-functions/v2/https";
import { setGlobalOptions } from "firebase-functions/v2";
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { randomBytes } from "node:crypto";

initializeApp();
const db = getFirestore();

const REGION = "europe-west1";
const STORE_ID = "libreribra-sangiovanni";
const WELCOME_BONUS_POINTS = 50;

setGlobalOptions({ region: REGION });

/** Codice referral leggibile, univoco per costruzione (8 char, senza ambigui 0/O/1/I). */
function makeReferralCode(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = randomBytes(8);
  let code = "";
  for (const b of bytes) {
    code += alphabet[b % alphabet.length];
  }
  return code;
}

/**
 * Bootstrap alla registrazione: ruolo di default 'parent' (custom claim),
 * documento utente, wallet a zero. Il bonus di benvenuto viene accreditato
 * SOLO al completamento dell'onboarding (callable dello Sprint 1), non qui:
 * paga il comportamento, non l'installazione.
 */
export const onUserCreate = functionsV1
  .region(REGION)
  .auth.user()
  .onCreate(async (user) => {
    await getAuth().setCustomUserClaims(user.uid, { role: "parent" });

    const batch = db.batch();
    batch.set(db.doc(`users/${user.uid}`), {
      email: user.email ?? null,
      role: "parent",
      status: "active",
      referral_code: makeReferralCode(),
      referred_by: null,
      onboarding_completed: false,
      notification_prefs: {
        eventi: true,
        novita: true,
        promozioni: false,
        lotteria: false,
      },
      device_fingerprints: [],
      store_id: STORE_ID,
      created_at: FieldValue.serverTimestamp(),
      updated_at: FieldValue.serverTimestamp(),
    });
    batch.set(db.doc(`loyalty_wallet/${user.uid}`), {
      balance: 0,
      earned_this_year: 0,
      tier: "lettore",
      expiring_next_30d: 0,
      welcome_bonus_points: WELCOME_BONUS_POINTS, // promesso in onboarding, accreditato a fine O5
      store_id: STORE_ID,
      created_at: FieldValue.serverTimestamp(),
      updated_at: FieldValue.serverTimestamp(),
    });
    await batch.commit();
  });

/**
 * Callable di spike: FluxBuilder la invoca autenticato e deve ricevere
 * eco di uid/ruolo + un round-trip Firestore. Se questo funziona su device
 * reale, il collegamento app → backend è dimostrato (criterio S0-1).
 */
export const healthCheck = onCall(async (request) => {
  const uid = request.auth?.uid ?? null;
  const role = (request.auth?.token?.role as string | undefined) ?? null;

  let userDocExists = false;
  if (uid) {
    const snap = await db.doc(`users/${uid}`).get();
    userDocExists = snap.exists;
  }

  return {
    ok: true,
    service: "libreribra-functions",
    time: new Date().toISOString(),
    authenticated: uid !== null,
    uid,
    role,
    userDocExists,
  };
});
