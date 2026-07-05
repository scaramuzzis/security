/**
 * Prenota-e-ritira (docs/05 §C): requested → ready_for_pickup →
 * picked_up | expired | cancelled. Il pagamento avviene in cassa (MVP);
 * i punti dell'acquisto li accredita lo staff con creditPurchasePoints.
 */

import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";

const REGION = "europe-west1";
const STORE_ID = "libreribra-sangiovanni";
const PICKUP_DAYS = 7;
const ACTIVE_STATUSES = ["requested", "ready_for_pickup"];

export async function reserveBookCore(
  uid: string,
  bookId: string
): Promise<{ orderId: string }> {
  const db = getFirestore();
  const bookRef = db.doc(`books/${bookId}`);
  const invRef = db.doc(`inventory/${bookId}`);
  const orderRef = db.collection("orders").doc();

  return db.runTransaction(async (t) => {
    const [bookSnap, invSnap, activeSnap] = await Promise.all([
      t.get(bookRef),
      t.get(invRef),
      t.get(
        db
          .collection("orders")
          .where("uid", "==", uid)
          .where("book_id", "==", bookId)
          .where("status", "in", ACTIVE_STATUSES)
          .limit(1)
      ),
    ]);

    if (!bookSnap.exists) throw new HttpsError("not-found", "Libro non trovato.");
    const book = bookSnap.data()!;
    if (book.availability === "out") {
      throw new HttpsError(
        "failed-precondition",
        "È appena finito! Aggiungilo alla lista dei desideri: ti avvisiamo quando torna."
      );
    }
    if (!activeSnap.empty) {
      throw new HttpsError("already-exists", "Hai già una prenotazione attiva per questo libro.");
    }

    t.set(orderRef, {
      uid,
      book_id: bookId,
      book_title: book.title,
      status: "requested",
      pickup_deadline: null,
      payment: null,
      store_id: STORE_ID,
      created_at: FieldValue.serverTimestamp(),
      updated_at: FieldValue.serverTimestamp(),
    });
    t.set(
      invRef,
      {
        reserved_count: ((invSnap.data()?.reserved_count as number) ?? 0) + 1,
        updated_at: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
    return { orderId: orderRef.id };
  });
}

type TerminalStatus = "picked_up" | "expired" | "cancelled";

async function closeOrder(
  orderId: string,
  from: string[],
  to: TerminalStatus,
  expectedUid?: string
): Promise<FirebaseFirestore.DocumentData> {
  const db = getFirestore();
  const orderRef = db.doc(`orders/${orderId}`);
  return db.runTransaction(async (t) => {
    const snap = await t.get(orderRef);
    if (!snap.exists) throw new HttpsError("not-found", "Ordine non trovato.");
    const order = snap.data()!;
    if (expectedUid && order.uid !== expectedUid) {
      throw new HttpsError("permission-denied", "Non è un tuo ordine.");
    }
    if (!from.includes(order.status)) {
      throw new HttpsError("failed-precondition", `Ordine in stato ${order.status}: operazione non consentita.`);
    }
    t.update(orderRef, { status: to, updated_at: FieldValue.serverTimestamp() });
    t.set(
      db.doc(`inventory/${order.book_id}`),
      {
        reserved_count: FieldValue.increment(-1),
        updated_at: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
    return order;
  });
}

// ---------- Callable ----------

export const reserveBook = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Accesso richiesto.");
  const { bookId } = request.data ?? {};
  if (typeof bookId !== "string" || bookId.length === 0) {
    throw new HttpsError("invalid-argument", "bookId mancante.");
  }
  return reserveBookCore(uid, bookId);
});

export const cancelOrder = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Accesso richiesto.");
  const { orderId } = request.data ?? {};
  if (typeof orderId !== "string") throw new HttpsError("invalid-argument", "orderId mancante.");
  await closeOrder(orderId, ACTIVE_STATUSES, "cancelled", uid);
  return { ok: true };
});

export const markOrderReady = onCall(async (request) => {
  const role = request.auth?.token?.role as string | undefined;
  if (!role || !["staff", "admin"].includes(role)) {
    throw new HttpsError("permission-denied", "Riservato allo staff.");
  }
  const { orderId } = request.data ?? {};
  if (typeof orderId !== "string") throw new HttpsError("invalid-argument", "orderId mancante.");

  const db = getFirestore();
  const orderRef = db.doc(`orders/${orderId}`);
  const deadline = Timestamp.fromMillis(Date.now() + PICKUP_DAYS * 24 * 3600 * 1000);

  const order = await db.runTransaction(async (t) => {
    const snap = await t.get(orderRef);
    if (!snap.exists) throw new HttpsError("not-found", "Ordine non trovato.");
    const o = snap.data()!;
    if (o.status !== "requested") {
      throw new HttpsError("failed-precondition", `Ordine in stato ${o.status}.`);
    }
    t.update(orderRef, {
      status: "ready_for_pickup",
      pickup_deadline: deadline,
      updated_at: FieldValue.serverTimestamp(),
    });
    return o;
  });

  await db.collection(`users/${order.uid}/notifications`).add({
    type: "order_ready",
    title: "Ti aspetta in libreria! 📚",
    body: `"${order.book_title}" è pronto: te lo teniamo da parte per ${PICKUP_DAYS} giorni.`,
    deep_link: `/wallet`,
    read: false,
    sent_at: FieldValue.serverTimestamp(),
    store_id: STORE_ID,
  });
  return { ok: true, pickupDeadline: deadline.toDate().toISOString() };
});

export const markOrderPickedUp = onCall(async (request) => {
  const role = request.auth?.token?.role as string | undefined;
  if (!role || !["staff", "admin"].includes(role)) {
    throw new HttpsError("permission-denied", "Riservato allo staff.");
  }
  const { orderId } = request.data ?? {};
  if (typeof orderId !== "string") throw new HttpsError("invalid-argument", "orderId mancante.");
  await closeOrder(orderId, ["ready_for_pickup", "requested"], "picked_up");
  return { ok: true };
});

// ---------- Scadenza automatica ritiri ----------

export const expireOrdersJob = onSchedule(
  { schedule: "every day 06:00", timeZone: "Europe/Rome", region: REGION },
  async () => {
    const db = getFirestore();
    const expired = await db
      .collection("orders")
      .where("status", "==", "ready_for_pickup")
      .where("pickup_deadline", "<", Timestamp.now())
      .get();

    for (const doc of expired.docs) {
      const order = await closeOrder(doc.id, ["ready_for_pickup"], "expired");
      await db.collection(`users/${order.uid}/notifications`).add({
        type: "order_expired",
        title: "La prenotazione è scaduta",
        body: `"${order.book_title}" è tornato a scaffale. Se lo vuoi ancora, prenotalo di nuovo quando passi.`,
        deep_link: `/libro/${order.book_id}`,
        read: false,
        sent_at: FieldValue.serverTimestamp(),
        store_id: STORE_ID,
      });
    }
  }
);
