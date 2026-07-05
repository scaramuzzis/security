/**
 * Test di integrazione del modulo eventi contro l'emulatore Firestore.
 * Criterio di accettazione Sprint 3: due prenotazioni simultanee
 * sull'ultimo posto → una sola passa.
 * Esecuzione: npm run test:emulator (dalla cartella firebase/functions).
 */
import { describe, it, expect, beforeAll } from "vitest";
import { initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error("Questi test girano solo contro l'emulatore (FIRESTORE_EMULATOR_HOST).");
}

initializeApp({ projectId: "demo-libreribra" });
const db = getFirestore();

const { bookEventCore, cancelBookingCore, checkInCore } = await import(
  "../lib/events.js"
);

const in48h = Timestamp.fromMillis(Date.now() + 48 * 3600 * 1000);

async function makeEvent(id, { capacity = 1, waitlistLimit = 5 } = {}) {
  await db.doc(`events/${id}`).set({
    title: `Evento ${id}`,
    status: "published",
    starts_at: in48h,
    capacity,
    booked_count: 0,
    waitlist_limit: waitlistLimit,
    price: 0,
  });
}

describe("Prenotazione con capienza (invariante n.3)", () => {
  it("due prenotazioni simultanee sull'ultimo posto: una sola confermata", { timeout: 20000 }, async () => {
    await makeEvent("ev-race");
    const results = await Promise.allSettled([
      bookEventCore("user_a", "ev-race", 1, []),
      bookEventCore("user_b", "ev-race", 1, []),
    ]);
    const outcomes = results
      .filter((r) => r.status === "fulfilled")
      .map((r) => r.value.status)
      .sort();
    expect(outcomes).toEqual(["confirmed", "waitlisted"]);

    const ev = await db.doc("events/ev-race").get();
    expect(ev.data().booked_count).toBe(1);
    expect(ev.data().status).toBe("sold_out");
  });

  it("doppia prenotazione dello stesso utente respinta", async () => {
    await makeEvent("ev-double", { capacity: 10 });
    await bookEventCore("user_a", "ev-double", 2, []);
    await expect(bookEventCore("user_a", "ev-double", 1, [])).rejects.toThrow(
      /già una prenotazione/
    );
  });

  it("numero posti fuori range respinto", async () => {
    await makeEvent("ev-seats", { capacity: 10 });
    await expect(bookEventCore("user_a", "ev-seats", 5, [])).rejects.toThrow();
    await expect(bookEventCore("user_a", "ev-seats", 0, [])).rejects.toThrow();
  });

  it("lista d'attesa piena → errore esplicito", async () => {
    await makeEvent("ev-full", { capacity: 1, waitlistLimit: 1 });
    await bookEventCore("u1", "ev-full", 1, []);
    await bookEventCore("u2", "ev-full", 1, []); // in lista
    await expect(bookEventCore("u3", "ev-full", 1, [])).rejects.toThrow(
      /al completo/
    );
  });
});

describe("Cancellazione e promozione FIFO", () => {
  it("chi cancella libera il posto al primo in lista, che riceve QR e notifica", async () => {
    await makeEvent("ev-promo");
    await bookEventCore("holder", "ev-promo", 1, []);
    const wl = await bookEventCore("waiter", "ev-promo", 1, []);
    expect(wl.status).toBe("waitlisted");
    expect(wl.waitlistPosition).toBe(1);

    const { promotedUid } = await cancelBookingCore("holder", "ev-promo");
    expect(promotedUid).toBe("waiter");

    const promoted = await db.doc("bookings/ev-promo_waiter").get();
    expect(promoted.data().status).toBe("confirmed");
    expect(promoted.data().qr_token).toBeTruthy();

    const ev = await db.doc("events/ev-promo").get();
    expect(ev.data().booked_count).toBe(1); // netto invariato

    const notifs = await db.collection("users/waiter/notifications").get();
    expect(notifs.docs.some((d) => d.data().type === "waitlist_promoted")).toBe(true);
  });

  it("cancellazione a meno di 6 ore dall'inizio respinta", async () => {
    const soon = Timestamp.fromMillis(Date.now() + 3 * 3600 * 1000);
    await db.doc("events/ev-late").set({
      title: "Tra poco",
      status: "published",
      starts_at: soon,
      capacity: 5,
      booked_count: 0,
      waitlist_limit: 0,
      price: 0,
    });
    await bookEventCore("user_a", "ev-late", 1, []);
    await expect(cancelBookingCore("user_a", "ev-late")).rejects.toThrow(
      /meno di 6 ore/
    );
  });
});

describe("Check-in con punti (idempotente)", () => {
  beforeAll(async () => {
    await db.doc("loyalty_wallet/guest").set({
      balance: 0,
      earned_this_year: 0,
      tier: "lettore",
    });
  });

  it("doppia scansione dello stesso QR accredita i 20 punti UNA volta", async () => {
    await makeEvent("ev-checkin", { capacity: 5 });
    const booking = await bookEventCore("guest", "ev-checkin", 1, []);

    const first = await checkInCore("ev-checkin", "guest", booking.qrToken, "staff_1");
    expect(first.alreadyCheckedIn).toBe(false);
    expect(first.pointsBalance).toBe(20);

    const second = await checkInCore("ev-checkin", "guest", booking.qrToken, "staff_1");
    expect(second.alreadyCheckedIn).toBe(true);
    expect(second.pointsBalance).toBe(20); // nessun doppio accredito

    const txs = await db.collection("users/guest/loyalty_transactions").get();
    expect(txs.size).toBe(1);
    expect(txs.docs[0].data().source).toBe("event_checkin");
  });

  it("QR sbagliato respinto", async () => {
    await makeEvent("ev-badqr", { capacity: 5 });
    await bookEventCore("guest", "ev-badqr", 1, []);
    await expect(
      checkInCore("ev-badqr", "guest", "qr-falsificato", "staff_1")
    ).rejects.toThrow(/QR non valido/);
  });
});
