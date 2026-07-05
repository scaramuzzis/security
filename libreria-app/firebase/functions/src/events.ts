/**
 * Eventi con prenotazione — logica transazionale (docs/05 §B).
 * Invariante n.3: booked_count ≤ capacity, garantito in transazione.
 * Le funzioni *Core sono pure rispetto all'auth (testabili sull'emulatore);
 * le callable in fondo fanno solo auth + validazione input.
 */

import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { randomBytes } from "node:crypto";
import { applyLoyaltyTransaction } from "./loyalty";

const REGION = "europe-west1";
const STORE_ID = "libreribra-sangiovanni";
const CHECKIN_POINTS = 20;
const MAX_SEATS = 4;
const CANCEL_CUTOFF_MS = 6 * 3600 * 1000; // 6 ore prima dell'inizio

export type BookingOutcome =
  | { status: "confirmed"; qrToken: string }
  | { status: "waitlisted"; waitlistPosition: number };

export async function bookEventCore(
  uid: string,
  eventId: string,
  seats: number,
  childIds: string[]
): Promise<BookingOutcome> {
  if (!Number.isInteger(seats) || seats < 1 || seats > MAX_SEATS) {
    throw new HttpsError("invalid-argument", `Posti: da 1 a ${MAX_SEATS}.`);
  }
  const db = getFirestore();
  const eventRef = db.doc(`events/${eventId}`);
  const bookingRef = db.doc(`bookings/${eventId}_${uid}`);

  return db.runTransaction(async (t) => {
    const eventSnap = await t.get(eventRef);
    if (!eventSnap.exists) {
      throw new HttpsError("not-found", "Evento non trovato.");
    }
    const event = eventSnap.data()!;
    if (!["published", "sold_out"].includes(event.status)) {
      throw new HttpsError("failed-precondition", "Le prenotazioni sono chiuse.");
    }
    if ((event.starts_at as Timestamp).toMillis() <= Date.now()) {
      throw new HttpsError("failed-precondition", "L'evento è già iniziato.");
    }

    const existing = await t.get(bookingRef);
    if (
      existing.exists &&
      ["confirmed", "waitlisted", "checked_in"].includes(existing.data()!.status)
    ) {
      throw new HttpsError("already-exists", "Hai già una prenotazione per questo evento.");
    }

    const bookedCount = (event.booked_count as number) ?? 0;
    const capacity = event.capacity as number;
    const common = {
      event_id: eventId,
      uid,
      seats,
      child_ids: childIds,
      store_id: STORE_ID,
      created_at: FieldValue.serverTimestamp(),
      updated_at: FieldValue.serverTimestamp(),
    };

    if (bookedCount + seats <= capacity) {
      const qrToken = randomBytes(16).toString("hex");
      t.set(bookingRef, {
        ...common,
        status: "confirmed",
        qr_token: qrToken,
        waitlist_position: null,
      });
      t.update(eventRef, {
        booked_count: bookedCount + seats,
        status: bookedCount + seats >= capacity ? "sold_out" : event.status,
        updated_at: FieldValue.serverTimestamp(),
      });
      return { status: "confirmed", qrToken };
    }

    // Posti finiti → lista d'attesa FIFO
    const waitlisted = await t.get(
      db
        .collection("bookings")
        .where("event_id", "==", eventId)
        .where("status", "==", "waitlisted")
    );
    const waitlistLimit = (event.waitlist_limit as number) ?? 0;
    if (waitlisted.size >= waitlistLimit) {
      throw new HttpsError(
        "resource-exhausted",
        "Posti e lista d'attesa al completo per questo evento."
      );
    }
    const position =
      waitlisted.docs.reduce(
        (max, d) => Math.max(max, (d.data().waitlist_position as number) ?? 0),
        0
      ) + 1;
    t.set(bookingRef, {
      ...common,
      status: "waitlisted",
      qr_token: null,
      waitlist_position: position,
    });
    return { status: "waitlisted", waitlistPosition: position };
  });
}

export async function cancelBookingCore(
  uid: string,
  eventId: string
): Promise<{ promotedUid: string | null }> {
  const db = getFirestore();
  const eventRef = db.doc(`events/${eventId}`);
  const bookingRef = db.doc(`bookings/${eventId}_${uid}`);

  return db.runTransaction(async (t) => {
    const [bookingSnap, eventSnap] = await Promise.all([
      t.get(bookingRef),
      t.get(eventRef),
    ]);
    if (!bookingSnap.exists) {
      throw new HttpsError("not-found", "Prenotazione non trovata.");
    }
    const booking = bookingSnap.data()!;
    if (!["confirmed", "waitlisted"].includes(booking.status)) {
      throw new HttpsError("failed-precondition", "Prenotazione non annullabile.");
    }

    if (booking.status === "waitlisted") {
      t.update(bookingRef, {
        status: "cancelled_by_user",
        updated_at: FieldValue.serverTimestamp(),
      });
      return { promotedUid: null };
    }

    const event = eventSnap.data()!;
    const startsMs = (event.starts_at as Timestamp).toMillis();
    if (startsMs - Date.now() < CANCEL_CUTOFF_MS) {
      throw new HttpsError(
        "failed-precondition",
        "Mancano meno di 6 ore all'evento: per annullare chiama la libreria."
      );
    }

    // Tutte le letture prima delle scritture (vincolo delle transazioni Admin SDK)
    const waitlisted = await t.get(
      db
        .collection("bookings")
        .where("event_id", "==", eventId)
        .where("status", "==", "waitlisted")
        .orderBy("waitlist_position")
        .limit(5)
    );

    let bookedCount = (event.booked_count as number) - (booking.seats as number);
    t.update(bookingRef, {
      status: "cancelled_by_user",
      updated_at: FieldValue.serverTimestamp(),
    });

    // Promozione FIFO del primo in lista che entra nei posti liberati
    let promotedUid: string | null = null;
    for (const cand of waitlisted.docs) {
      const c = cand.data();
      if (bookedCount + (c.seats as number) <= (event.capacity as number)) {
        const qrToken = randomBytes(16).toString("hex");
        t.update(cand.ref, {
          status: "confirmed",
          qr_token: qrToken,
          waitlist_position: null,
          updated_at: FieldValue.serverTimestamp(),
        });
        t.set(db.collection(`users/${c.uid}/notifications`).doc(), {
          type: "waitlist_promoted",
          title: "Si è liberato un posto! 🎉",
          body: `La tua prenotazione per "${event.title}" è confermata.`,
          deep_link: `/evento/${eventId}`,
          read: false,
          sent_at: FieldValue.serverTimestamp(),
          store_id: STORE_ID,
        });
        bookedCount += c.seats as number;
        promotedUid = c.uid as string;
        break;
      }
    }

    t.update(eventRef, {
      booked_count: bookedCount,
      status:
        event.status === "sold_out" && bookedCount < (event.capacity as number)
          ? "published"
          : event.status,
      updated_at: FieldValue.serverTimestamp(),
    });
    return { promotedUid };
  });
}

export async function checkInCore(
  eventId: string,
  bookingUid: string,
  qrToken: string,
  operatorUid: string
): Promise<{ alreadyCheckedIn: boolean; pointsBalance: number }> {
  const db = getFirestore();
  const bookingRef = db.doc(`bookings/${eventId}_${bookingUid}`);

  const alreadyCheckedIn = await db.runTransaction(async (t) => {
    const snap = await t.get(bookingRef);
    if (!snap.exists) {
      throw new HttpsError("not-found", "Prenotazione non trovata.");
    }
    const b = snap.data()!;
    if (b.status === "checked_in") {
      return true;
    }
    if (b.status !== "confirmed" || b.qr_token !== qrToken) {
      throw new HttpsError("failed-precondition", "QR non valido per questa prenotazione.");
    }
    t.update(bookingRef, {
      status: "checked_in",
      updated_at: FieldValue.serverTimestamp(),
    });
    return false;
  });

  // Idempotente per refId: un solo accredito anche con doppia scansione.
  const result = await applyLoyaltyTransaction({
    uid: bookingUid,
    amount: CHECKIN_POINTS,
    source: "event_checkin",
    refId: `${eventId}_${bookingUid}`,
    operatorUid,
  });
  return { alreadyCheckedIn, pointsBalance: result.balanceAfter };
}

// ---------- Callable ----------

export const bookEvent = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Accesso richiesto.");
  const { eventId, seats, childIds } = request.data ?? {};
  if (typeof eventId !== "string" || eventId.length === 0) {
    throw new HttpsError("invalid-argument", "eventId mancante.");
  }
  return bookEventCore(
    uid,
    eventId,
    seats ?? 1,
    Array.isArray(childIds) ? childIds.filter((c) => typeof c === "string") : []
  );
});

export const cancelBooking = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Accesso richiesto.");
  const { eventId } = request.data ?? {};
  if (typeof eventId !== "string" || eventId.length === 0) {
    throw new HttpsError("invalid-argument", "eventId mancante.");
  }
  return cancelBookingCore(uid, eventId);
});

export const checkIn = onCall(async (request) => {
  const role = request.auth?.token?.role as string | undefined;
  if (!role || !["staff", "admin"].includes(role)) {
    throw new HttpsError("permission-denied", "Riservato allo staff.");
  }
  const { eventId, bookingUid, qrToken } = request.data ?? {};
  if (
    typeof eventId !== "string" ||
    typeof bookingUid !== "string" ||
    typeof qrToken !== "string"
  ) {
    throw new HttpsError("invalid-argument", "eventId, bookingUid e qrToken richiesti.");
  }
  return checkInCore(eventId, bookingUid, qrToken, request.auth!.uid);
});

// ---------- Reminder T-24h / T-2h (notifiche in-app; push FCM: Sprint 5) ----------

const REMINDER_WINDOWS: Array<{ flag: string; hours: number; title: string }> = [
  { flag: "reminder_24h_sent", hours: 24, title: "Ci vediamo domani! 📅" },
  { flag: "reminder_2h_sent", hours: 2, title: "Ci siamo quasi! ⏰" },
];

export const eventReminders = onSchedule(
  { schedule: "every 60 minutes", region: REGION },
  async () => {
    const db = getFirestore();
    for (const w of REMINDER_WINDOWS) {
      const from = Timestamp.fromMillis(Date.now() + (w.hours - 0.5) * 3600 * 1000);
      const to = Timestamp.fromMillis(Date.now() + (w.hours + 0.5) * 3600 * 1000);
      const events = await db
        .collection("events")
        .where("status", "in", ["published", "sold_out"])
        .where("starts_at", ">=", from)
        .where("starts_at", "<=", to)
        .get();

      for (const ev of events.docs) {
        if (ev.data()[w.flag]) continue;
        const bookings = await db
          .collection("bookings")
          .where("event_id", "==", ev.id)
          .where("status", "==", "confirmed")
          .get();
        const batch = db.batch();
        for (const b of bookings.docs) {
          batch.set(db.collection(`users/${b.data().uid}/notifications`).doc(), {
            type: "event_reminder",
            title: w.title,
            body: `"${ev.data().title}" ti aspetta in libreria.`,
            deep_link: `/evento/${ev.id}`,
            read: false,
            sent_at: FieldValue.serverTimestamp(),
            store_id: STORE_ID,
          });
        }
        batch.update(ev.ref, { [w.flag]: true });
        await batch.commit();
      }
    }
  }
);
