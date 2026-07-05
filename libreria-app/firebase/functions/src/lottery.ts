/**
 * "Estrazione dei Lettori" — lotteria a 90 numeri SOLO a punti fedeltà
 * (docs/05 §H). Vincoli non negoziabili:
 *  - gate legale: un round non si apre senza rules_url (regolamento approvato);
 *  - solo utenti 18+ con ruolo parent (staff/admin esclusi per trasparenza);
 *  - un numero = un proprietario (doc id {roundId}_{number});
 *  - estrazione commit-reveal: hash del seed pubblicato alla chiusura,
 *    seed rivelato all'estrazione, esito riproducibile da chiunque;
 *  - sotto soglia minima: rimborso integrale automatico.
 */

import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { createHash, randomBytes } from "node:crypto";
import { applyLoyaltyTransaction } from "./loyalty";

const REGION = "europe-west1";
const STORE_ID = "libreribra-sangiovanni";
const TOTAL_NUMBERS = 90;
const PRIZE_COUNT = 3;
const ADULT_AGE = 18;

function sha256(s: string): string {
  return createHash("sha256").update(s).digest("hex");
}

function requireAdmin(request: { auth?: { token?: Record<string, unknown> } }): void {
  if (request.auth?.token?.role !== "admin") {
    throw new HttpsError("permission-denied", "Riservato all'amministratore.");
  }
}

// ---------- Ciclo di vita del round ----------

export const createLotteryRound = onCall(async (request) => {
  requireAdmin(request);
  const {
    name,
    opensAt,
    salesCloseAt,
    drawAt,
    ticketCostPoints = 150,
    maxTicketsPerUser = 5,
    minTicketsThreshold = 30,
    rulesUrl = "",
    prizes,
  } = request.data ?? {};

  if (typeof name !== "string" || name.length === 0) {
    throw new HttpsError("invalid-argument", "Nome del round richiesto.");
  }
  const opens = Date.parse(opensAt);
  const closes = Date.parse(salesCloseAt);
  const draw = Date.parse(drawAt);
  if (!(opens < closes && closes < draw)) {
    throw new HttpsError(
      "invalid-argument",
      "Date incoerenti: apertura < chiusura vendite < estrazione."
    );
  }
  if (!Array.isArray(prizes) || prizes.length !== PRIZE_COUNT) {
    throw new HttpsError("invalid-argument", `Servono esattamente ${PRIZE_COUNT} premi.`);
  }

  const db = getFirestore();
  const roundRef = db.collection("lottery_rounds").doc();
  const batch = db.batch();
  batch.set(roundRef, {
    name,
    status: "draft",
    opens_at: Timestamp.fromMillis(opens),
    sales_close_at: Timestamp.fromMillis(closes),
    draw_at: Timestamp.fromMillis(draw),
    ticket_cost_points: ticketCostPoints,
    max_tickets_per_user: maxTicketsPerUser,
    min_tickets_threshold: minTicketsThreshold,
    sold_count: 0,
    commit_hash: null,
    revealed_seed: null,
    winning_numbers: null,
    rules_url: rulesUrl,
    store_id: STORE_ID,
    created_at: FieldValue.serverTimestamp(),
    updated_at: FieldValue.serverTimestamp(),
  });
  prizes.forEach((p: { description: string; valueEur: number }, i: number) => {
    batch.set(roundRef.collection("prizes").doc(String(i + 1)), {
      rank: i + 1,
      description: p.description,
      value_eur: p.valueEur,
      winner_uid: null,
      winning_number: null,
      claimed_at: null,
      claim_deadline: null,
      store_id: STORE_ID,
    });
  });
  batch.set(roundRef.collection("grid").doc("grid"), { taken: {} });
  await batch.commit();
  return { roundId: roundRef.id };
});

export async function openLotteryRoundCore(roundId: string, actorUid: string) {
  const db = getFirestore();
  const ref = db.doc(`lottery_rounds/${roundId}`);
  await db.runTransaction(async (t) => {
    const snap = await t.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "Round non trovato.");
    const r = snap.data()!;
    if (r.status !== "draft") {
      throw new HttpsError("failed-precondition", `Round in stato ${r.status}.`);
    }
    // GATE LEGALE (invariante n.6): senza regolamento approvato non si apre.
    if (typeof r.rules_url !== "string" || r.rules_url.length === 0) {
      throw new HttpsError(
        "failed-precondition",
        "Round senza regolamento (rules_url): il rilascio richiede la validazione legale."
      );
    }
    t.update(ref, { status: "open", updated_at: FieldValue.serverTimestamp() });
  });
  await db.collection("admin_logs").add({
    actor_uid: actorUid,
    action: "open_round",
    target_ref: `lottery_rounds/${roundId}`,
    payload: {},
    at: FieldValue.serverTimestamp(),
    store_id: STORE_ID,
  });
}

export const openLotteryRound = onCall(async (request) => {
  requireAdmin(request);
  const { roundId } = request.data ?? {};
  if (typeof roundId !== "string") throw new HttpsError("invalid-argument", "roundId mancante.");
  await openLotteryRoundCore(roundId, request.auth!.uid);
  return { ok: true };
});

/** Chiusura vendite: genera il seed segreto e ne PUBBLICA l'hash (commit). */
export async function closeLotterySalesCore(roundId: string): Promise<{ commitHash: string }> {
  const db = getFirestore();
  const ref = db.doc(`lottery_rounds/${roundId}`);
  const seed = randomBytes(32).toString("hex");
  const commitHash = sha256(seed);

  await db.runTransaction(async (t) => {
    const snap = await t.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "Round non trovato.");
    if (snap.data()!.status !== "open") {
      throw new HttpsError("failed-precondition", `Round in stato ${snap.data()!.status}.`);
    }
    t.update(ref, {
      status: "closed",
      commit_hash: commitHash,
      updated_at: FieldValue.serverTimestamp(),
    });
    // Il seed resta segreto fino all'estrazione (subcollection non leggibile dal client)
    t.set(ref.collection("private").doc("seed"), { seed });
  });
  return { commitHash };
}

export const closeLotterySales = onCall(async (request) => {
  requireAdmin(request);
  const { roundId } = request.data ?? {};
  if (typeof roundId !== "string") throw new HttpsError("invalid-argument", "roundId mancante.");
  return closeLotterySalesCore(roundId);
});

/** Scheduler: chiude automaticamente i round oltre sales_close_at. */
export const autoCloseLotterySales = onSchedule(
  { schedule: "every 60 minutes", region: REGION },
  async () => {
    const db = getFirestore();
    const due = await db
      .collection("lottery_rounds")
      .where("status", "==", "open")
      .where("sales_close_at", "<", Timestamp.now())
      .get();
    for (const doc of due.docs) {
      await closeLotterySalesCore(doc.id);
    }
  }
);

// ---------- Acquisto numeri ----------

export async function buyLotteryTicketCore(
  uid: string,
  roundId: string,
  num: number
): Promise<{ balance: number }> {
  if (!Number.isInteger(num) || num < 1 || num > TOTAL_NUMBERS) {
    throw new HttpsError("invalid-argument", `Numeri validi: da 1 a ${TOTAL_NUMBERS}.`);
  }
  const db = getFirestore();
  const roundRef = db.doc(`lottery_rounds/${roundId}`);
  const ticketRef = db.doc(`lottery_tickets/${roundId}_${num}`);

  let cost = 0;
  const result = await applyLoyaltyTransaction({
    uid,
    amount: -1, // sostituito in preCheck: il costo è letto dal round
    source: "lottery_ticket",
    refId: `${roundId}_${num}`,
    preCheck: async (t) => {
      const [roundSnap, ticketSnap, mine] = await Promise.all([
        t.get(roundRef),
        t.get(ticketRef),
        t.get(
          db
            .collection("lottery_tickets")
            .where("round_id", "==", roundId)
            .where("uid", "==", uid)
        ),
      ]);
      if (!roundSnap.exists) throw new HttpsError("not-found", "Round non trovato.");
      const r = roundSnap.data()!;
      if (r.status !== "open" || (r.sales_close_at as Timestamp).toMillis() < Date.now()) {
        throw new HttpsError("failed-precondition", "Le vendite di questo round sono chiuse.");
      }
      if (ticketSnap.exists) {
        throw new HttpsError(
          "already-exists",
          `Il ${num} è appena stato preso! Scegli un altro numero.`
        );
      }
      if (mine.size >= (r.max_tickets_per_user as number)) {
        throw new HttpsError(
          "resource-exhausted",
          `Massimo ${r.max_tickets_per_user} numeri per partecipante in questo round.`
        );
      }
      cost = r.ticket_cost_points as number;
    },
    extraWrites: (t) => {
      t.set(ticketRef, {
        round_id: roundId,
        number: num,
        uid,
        points_paid: cost,
        is_winner: null,
        purchased_at: FieldValue.serverTimestamp(),
        store_id: STORE_ID,
      });
      t.update(roundRef, {
        sold_count: FieldValue.increment(1),
        updated_at: FieldValue.serverTimestamp(),
      });
      t.set(
        roundRef.collection("grid").doc("grid"),
        { taken: { [String(num)]: true } },
        { merge: true }
      );
    },
    amountOverride: () => -cost,
  });

  return { balance: result.balanceAfter };
}

export const buyLotteryTicket = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Accesso richiesto.");
  const role = request.auth?.token?.role as string | undefined;
  if (role !== "parent") {
    throw new HttpsError(
      "permission-denied",
      "Per trasparenza, staff e amministratori non partecipano all'estrazione."
    );
  }

  // Verifica 18+: data di nascita nel profilo
  const userSnap = await getFirestore().doc(`users/${uid}`).get();
  const birth = userSnap.data()?.birth_date as Timestamp | null | undefined;
  if (!birth) {
    throw new HttpsError(
      "failed-precondition",
      "Aggiungi la tua data di nascita nel profilo: l'estrazione è riservata ai maggiorenni."
    );
  }
  const age =
    (Date.now() - birth.toMillis()) / (365.25 * 24 * 3600 * 1000);
  if (age < ADULT_AGE) {
    throw new HttpsError("permission-denied", "L'estrazione è riservata ai maggiorenni.");
  }

  const { roundId, number } = request.data ?? {};
  if (typeof roundId !== "string" || typeof number !== "number") {
    throw new HttpsError("invalid-argument", "roundId e number richiesti.");
  }
  return buyLotteryTicketCore(uid, roundId, number);
});

// ---------- Estrazione (commit-reveal, riproducibile) ----------

/** Estrazione deterministica: chiunque può ricalcolarla dal seed rivelato. */
export function drawNumbersFromSeed(seed: string, soldNumbers: number[]): number[] {
  const pool = [...soldNumbers].sort((a, b) => a - b);
  const target = Math.min(PRIZE_COUNT, pool.length); // fissato PRIMA degli splice
  const winners: number[] = [];
  for (let i = 0; winners.length < target; i++) {
    const h = sha256(`${seed}:${i}`);
    const idx = Number(BigInt(`0x${h.slice(0, 12)}`) % BigInt(pool.length));
    winners.push(pool.splice(idx, 1)[0]);
  }
  return winners;
}

export async function drawLotteryCore(
  roundId: string,
  actorUid: string
): Promise<
  | { outcome: "drawn"; winningNumbers: number[]; revealedSeed: string }
  | { outcome: "refunded"; refundedTickets: number }
> {
  const db = getFirestore();
  const roundRef = db.doc(`lottery_rounds/${roundId}`);
  const [roundSnap, seedSnap, ticketsSnap] = await Promise.all([
    roundRef.get(),
    roundRef.collection("private").doc("seed").get(),
    db.collection("lottery_tickets").where("round_id", "==", roundId).get(),
  ]);
  if (!roundSnap.exists) throw new HttpsError("not-found", "Round non trovato.");
  const round = roundSnap.data()!;
  if (round.status !== "closed") {
    throw new HttpsError("failed-precondition", `Round in stato ${round.status}.`);
  }

  const tickets = ticketsSnap.docs.map((d) => d.data());

  // Fallback sotto soglia: rimborso integrale, tracciato
  if (tickets.length < (round.min_tickets_threshold as number)) {
    for (const tk of tickets) {
      await applyLoyaltyTransaction({
        uid: tk.uid as string,
        amount: tk.points_paid as number,
        source: "lottery_refund",
        refId: `${roundId}_${tk.number}`,
      });
      await db.collection(`users/${tk.uid}/notifications`).add({
        type: "lottery",
        title: "Punti rimborsati",
        body: `L'estrazione "${round.name}" non ha raggiunto il numero minimo di partecipanti: i tuoi ${tk.points_paid} punti sono tornati nel wallet.`,
        deep_link: "/lotteria",
        read: false,
        sent_at: FieldValue.serverTimestamp(),
        store_id: STORE_ID,
      });
    }
    await roundRef.update({ status: "refunded", updated_at: FieldValue.serverTimestamp() });
    await db.collection("admin_logs").add({
      actor_uid: actorUid,
      action: "refund_round",
      target_ref: `lottery_rounds/${roundId}`,
      payload: { refunded_tickets: tickets.length, threshold: round.min_tickets_threshold },
      at: FieldValue.serverTimestamp(),
      store_id: STORE_ID,
    });
    return { outcome: "refunded", refundedTickets: tickets.length };
  }

  // Reveal: il seed deve corrispondere all'hash pubblicato alla chiusura
  const seed = seedSnap.data()?.seed as string | undefined;
  if (!seed || sha256(seed) !== round.commit_hash) {
    throw new HttpsError("internal", "Seed mancante o non corrispondente al commit: estrazione annullata.");
  }

  const winning = drawNumbersFromSeed(
    seed,
    tickets.map((t) => t.number as number)
  );

  const batch = db.batch();
  const claimDeadline = Timestamp.fromMillis(Date.now() + 30 * 24 * 3600 * 1000);
  winning.forEach((num, i) => {
    const winner = tickets.find((t) => t.number === num)!;
    batch.update(db.doc(`lottery_tickets/${roundId}_${num}`), { is_winner: i + 1 });
    batch.update(roundRef.collection("prizes").doc(String(i + 1)), {
      winner_uid: winner.uid,
      winning_number: num,
      claim_deadline: claimDeadline,
    });
  });
  batch.update(roundRef, {
    status: "drawn",
    revealed_seed: seed,
    winning_numbers: winning,
    updated_at: FieldValue.serverTimestamp(),
  });
  await batch.commit();

  // Verbale pubblico in audit log: chiunque può verificare hash e ricalcolo
  await db.collection("admin_logs").add({
    actor_uid: actorUid,
    action: "draw_lottery",
    target_ref: `lottery_rounds/${roundId}`,
    payload: {
      commit_hash: round.commit_hash,
      revealed_seed: seed,
      winning_numbers: winning,
      sold_count: tickets.length,
    },
    at: FieldValue.serverTimestamp(),
    store_id: STORE_ID,
  });

  // Notifica a tutti i partecipanti
  for (const tk of tickets) {
    const rank = winning.indexOf(tk.number as number) + 1;
    await db.collection(`users/${tk.uid}/notifications`).add({
      type: "lottery",
      title: rank > 0 ? `Hai vinto il ${rank}° premio! 🎉` : "Estrazione completata",
      body:
        rank > 0
          ? `Il tuo numero ${tk.number} è stato estratto! Passa in libreria entro 30 giorni per ritirare il premio.`
          : `Numeri estratti: ${winning.join(", ")}. Grazie per aver partecipato: la prossima estrazione ti aspetta!`,
      deep_link: "/lotteria",
      read: false,
      sent_at: FieldValue.serverTimestamp(),
      store_id: STORE_ID,
    });
  }

  return { outcome: "drawn", winningNumbers: winning, revealedSeed: seed };
}

export const drawLottery = onCall(async (request) => {
  requireAdmin(request);
  const { roundId } = request.data ?? {};
  if (typeof roundId !== "string") throw new HttpsError("invalid-argument", "roundId mancante.");
  return drawLotteryCore(roundId, request.auth!.uid);
});
