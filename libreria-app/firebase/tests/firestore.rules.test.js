/**
 * Test delle Firestore security rules (Sprint 1, criterio di accettazione:
 * "un client non può leggere i figli altrui" + collezioni economiche blindate).
 * Esecuzione: npm run test:emulator (richiede Java per l'emulatore).
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { beforeAll, afterAll, describe, it } from "vitest";
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, deleteDoc } from "firebase/firestore";

const __dirname = dirname(fileURLToPath(import.meta.url));
let testEnv;

const ALICE = "alice_uid";
const BOB = "bob_uid";

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-libreribra",
    firestore: {
      rules: readFileSync(join(__dirname, "..", "firestore.rules"), "utf8"),
    },
  });

  // Fixture scritte bypassando le rules (come farebbero le Functions)
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `users/${ALICE}`), { role: "parent", status: "active" });
    await setDoc(doc(db, `users/${ALICE}/children/kid1`), {
      nickname: "Leo",
      birth_year: 2018,
      consent_at: new Date(),
    });
    await setDoc(doc(db, `loyalty_wallet/${ALICE}`), { balance: 100 });
    await setDoc(doc(db, "books/book1"), { title: "Il GGG", availability: "in_stock" });
    await setDoc(doc(db, "posts/post1"), {
      status: "published",
      like_count: 0,
      comment_count: 0,
      save_count: 0,
    });
    await setDoc(doc(db, "store_locations/libreribra-sangiovanni"), {
      name: "LibreriBrà",
    });
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

const asAlice = () => testEnv.authenticatedContext(ALICE, { role: "parent" }).firestore();
const asBob = () => testEnv.authenticatedContext(BOB, { role: "parent" }).firestore();
const asStaff = () => testEnv.authenticatedContext("staff_uid", { role: "staff" }).firestore();
const asAnon = () => testEnv.unauthenticatedContext().firestore();

describe("Profili figlio (dati minori)", () => {
  it("il genitore legge i propri figli", () =>
    assertSucceeds(getDoc(doc(asAlice(), `users/${ALICE}/children/kid1`))));

  it("un altro utente NON legge i figli altrui", () =>
    assertFails(getDoc(doc(asBob(), `users/${ALICE}/children/kid1`))));

  it("lo staff NON legge i profili figlio", () =>
    assertFails(getDoc(doc(asStaff(), `users/${ALICE}/children/kid1`))));

  it("creazione figlio SENZA consent_at negata", () =>
    assertFails(
      setDoc(doc(asAlice(), `users/${ALICE}/children/kid2`), {
        nickname: "Mia",
        birth_year: 2017,
      })
    ));

  it("creazione figlio con consenso completo consentita", () =>
    assertSucceeds(
      setDoc(doc(asAlice(), `users/${ALICE}/children/kid2`), {
        nickname: "Mia",
        birth_year: 2017,
        consent_at: new Date(),
      })
    ));
});

describe("Wishlist libri", () => {
  it("l'utente aggiunge un libro alla propria wishlist", () =>
    assertSucceeds(
      setDoc(doc(asAlice(), `users/${ALICE}/wishlist/book1`), {
        book_id: "book1",
      })
    ));

  it("creazione con book_id incoerente col doc id negata", () =>
    assertFails(
      setDoc(doc(asAlice(), `users/${ALICE}/wishlist/book1x`), {
        book_id: "book9",
      })
    ));

  it("un altro utente NON legge la wishlist altrui", () =>
    assertFails(getDoc(doc(asBob(), `users/${ALICE}/wishlist/book1`))));
});

describe("Collezioni economiche blindate", () => {
  it("il client NON scrive il proprio wallet", () =>
    assertFails(
      setDoc(doc(asAlice(), `loyalty_wallet/${ALICE}`), { balance: 99999 })
    ));

  it("il client NON scrive transazioni punti", () =>
    assertFails(
      setDoc(doc(asAlice(), `users/${ALICE}/loyalty_transactions/tx1`), {
        amount: 500,
        source: "welcome",
      })
    ));

  it("il client NON scrive il proprio doc utente", () =>
    assertFails(setDoc(doc(asAlice(), `users/${ALICE}`), { role: "admin" })));

  it("il client NON crea biglietti lotteria direttamente", () =>
    assertFails(
      setDoc(doc(asAlice(), "lottery_tickets/round1_42"), {
        round_id: "round1",
        number: 42,
        uid: ALICE,
      })
    ));
});

describe("Feed", () => {
  it("like con doc id composito corretto consentito", () =>
    assertSucceeds(
      setDoc(doc(asAlice(), `posts/post1/likes/post1_${ALICE}`), { uid: ALICE })
    ));

  it("like con id di un altro utente negato", () =>
    assertFails(
      setDoc(doc(asAlice(), `posts/post1/likes/post1_${BOB}`), { uid: BOB })
    ));

  it("commento creato in stato pending consentito", () =>
    assertSucceeds(
      setDoc(doc(asAlice(), "posts/post1/comments/c1"), {
        author_uid: ALICE,
        text: "Che bel laboratorio!",
        status: "pending",
      })
    ));

  it("commento auto-approvato (status visible) negato", () =>
    assertFails(
      setDoc(doc(asAlice(), "posts/post1/comments/c2"), {
        author_uid: ALICE,
        text: "spam",
        status: "visible",
      })
    ));

  it("un parent NON crea post", () =>
    assertFails(
      setDoc(doc(asAlice(), "posts/post2"), {
        status: "published",
        like_count: 0,
        comment_count: 0,
        save_count: 0,
      })
    ));
});

describe("Lettura pubblica", () => {
  it("catalogo leggibile senza account", () =>
    assertSucceeds(getDoc(doc(asAnon(), "books/book1"))));

  it("scheda negozio leggibile senza account", () =>
    assertSucceeds(
      getDoc(doc(asAnon(), "store_locations/libreribra-sangiovanni"))
    ));

  it("un parent NON scrive il catalogo", () =>
    assertFails(setDoc(doc(asAlice(), "books/book1"), { price: 0 })));

  it("lo staff scrive il catalogo", () =>
    assertSucceeds(
      setDoc(doc(asStaff(), "books/book2"), { title: "Nuovo arrivo" })
    ));
});

describe("Profilo pubblico", () => {
  it("creazione con soli campi ammessi consentita", () =>
    assertSucceeds(
      setDoc(doc(asAlice(), `profiles/${ALICE}`), {
        display_name: "Alice",
        photo_url: null,
        neighborhood: "San Giovanni",
      })
    ));

  it("creazione con campi extra negata", () =>
    assertFails(
      setDoc(doc(asBob(), `profiles/${BOB}`), {
        display_name: "Bob",
        photo_url: null,
        neighborhood: null,
        role: "admin",
      })
    ));

  it("cancellazione diretta negata (cascata solo via Functions)", () =>
    assertFails(deleteDoc(doc(asAlice(), `profiles/${ALICE}`))));
});
