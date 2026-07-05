/**
 * Bootstrap del PRIMO amministratore (una tantum, da terminale).
 *
 * Progetto reale:
 *   GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> \
 *     node scripts/make-admin.mjs admin@libreribra.it
 *
 * Emulatore:
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 \
 *     GCLOUD_PROJECT=demo-libreribra node scripts/make-admin.mjs admin@libreribra.it
 *
 * L'utente deve essersi già registrato (l'account deve esistere in Auth).
 * Gli admin successivi si creano ripetendo questo script: mai via API.
 */
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

const email = process.argv[2];
if (!email) {
  console.error("Uso: node scripts/make-admin.mjs <email-utente-esistente>");
  process.exit(1);
}

initializeApp();
const auth = getAuth();
const db = getFirestore();

const user = await auth.getUserByEmail(email).catch(() => null);
if (!user) {
  console.error(`Nessun utente Auth con email ${email}: deve prima registrarsi nell'app o dalla console.`);
  process.exit(1);
}

await auth.setCustomUserClaims(user.uid, { role: "admin" });
await db.doc(`users/${user.uid}`).set(
  { role: "admin", updated_at: FieldValue.serverTimestamp() },
  { merge: true }
);
await db.collection("admin_logs").add({
  actor_uid: "bootstrap_script",
  action: "role_change",
  target_ref: `users/${user.uid}`,
  payload: { new_role: "admin", email },
  at: FieldValue.serverTimestamp(),
  store_id: "libreribra-sangiovanni",
});

console.log(`✅ ${email} (${user.uid}) è ora admin.`);
console.log("Il ruolo è attivo dal prossimo login (o refresh token, max 1h).");
process.exit(0);
