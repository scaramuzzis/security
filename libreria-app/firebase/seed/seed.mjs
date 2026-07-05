/**
 * Seed dati di base per LibreriBrà (dev o emulatore).
 *
 * Emulatore:  FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node seed.mjs
 * Progetto dev: GOOGLE_APPLICATION_CREDENTIALS=<sa.json> node seed.mjs
 *
 * Idempotente: usa doc id fissi, rilanciarlo sovrascrive gli stessi documenti.
 * ⚠️ Indirizzo, orari e contatti sono PLACEHOLDER da sostituire con i dati
 * reali del negozio prima del pilot.
 */
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

initializeApp({ projectId: process.env.GCLOUD_PROJECT ?? "demo-libreribra" });
const db = getFirestore();
const STORE_ID = "libreribra-sangiovanni";
const now = FieldValue.serverTimestamp();
const base = { store_id: STORE_ID, created_at: now, updated_at: now };

await db.doc(`store_locations/${STORE_ID}`).set({
  ...base,
  name: "LibreriBrà",
  photos: [],
  address: "Via PLACEHOLDER 00, 00183 Roma (San Giovanni)",
  lat: 41.8853,
  lng: 12.5113,
  phone: "+39 06 0000000",
  whatsapp: "+39 000 0000000",
  email: "info@libreribra.example",
  instagram: "libreribra",
  opening_hours: {
    lun: [], // chiuso
    mar: ["09:30-13:30", "15:30-19:30"],
    mer: ["09:30-13:30", "15:30-19:30"],
    gio: ["09:30-13:30", "15:30-19:30"],
    ven: ["09:30-13:30", "15:30-19:30"],
    sab: ["09:30-19:30"],
    dom: ["10:00-13:00"],
  },
  closures: [],
  directions_note: "Metro A San Giovanni, poi 5 minuti a piedi.",
  parking_note: "Strisce blu in zona; parcheggio PLACEHOLDER nelle vicinanze.",
});

const categories = [
  ["lettura-animata", "Letture animate", "📖", 1],
  ["laboratorio", "Laboratori creativi", "🎨", 2],
  ["incontro-autore", "Incontri con autori", "✍️", 3],
  ["gruppo-lettura", "Gruppi di lettura", "👥", 4],
  ["festa", "Feste e ricorrenze", "🎉", 5],
];
for (const [id, name, icon, sort] of categories) {
  await db.doc(`event_categories/${id}`).set({ ...base, name, icon, sort });
}

const games = [
  ["parole-in-fuga", "Parole in fuga", "Ricomponi le parole scappate dai libri!", ["6-8", "9-12"], 1, 1],
  ["il-paroliere", "Il paroliere", "Quanto conosci l'italiano? Mettiti alla prova.", ["6-8", "9-12"], 1, 2],
  ["ricomponi-la-figura", "Ricomponi la figura", "Rimetti insieme le illustrazioni più belle.", ["6-8", "9-12"], 2, 3],
  ["quiz-esploratore", "Quiz del piccolo esploratore", "Storie, scienza e mondi da scoprire.", ["6-8", "9-12"], 1, 4],
  ["trova-le-differenze", "Trova le differenze", "Occhio ai dettagli delle tavole illustrate!", ["6-8", "9-12"], 1, 5],
];
for (const [id, title, description, age_bands, reward_points, sort] of games) {
  await db.doc(`games/${id}`).set({
    ...base,
    title,
    description,
    age_bands,
    reward_points,
    sort,
    status: "hidden", // si attivano nello Sprint 7 con i contenuti pronti
    engine: "native", // eventuale override a 'webview' dopo lo spike S0-6
  });
}

console.log("Seed completato:");
console.log(`  1 store_location (${STORE_ID}) — DATI PLACEHOLDER da aggiornare`);
console.log(`  ${categories.length} event_categories`);
console.log(`  ${games.length} games (status: hidden fino allo Sprint 7)`);
process.exit(0);
