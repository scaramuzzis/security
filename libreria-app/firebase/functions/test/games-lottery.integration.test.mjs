/**
 * Test Fase 2: cap famiglia giochi, gate legale lotteria, acquisto
 * transazionale dei numeri, estrazione commit-reveal riproducibile,
 * rimborso sotto soglia.
 */
import { describe, it, expect } from "vitest";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { createHash } from "node:crypto";

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error("Questi test girano solo contro l'emulatore (FIRESTORE_EMULATOR_HOST).");
}
if (getApps().length === 0) initializeApp({ projectId: "demo-libreribra" });
const db = getFirestore();

const { completeGameSessionCore } = await import("../lib/games.js");
const {
  openLotteryRoundCore,
  closeLotterySalesCore,
  buyLotteryTicketCore,
  drawLotteryCore,
  drawNumbersFromSeed,
} = await import("../lib/lottery.js");

const sha256 = (s) => createHash("sha256").update(s).digest("hex");

async function makeWallet(uid, balance) {
  await db.doc(`loyalty_wallet/${uid}`).set({ balance, earned_this_year: balance, tier: "lettore" });
  await db.collection(`users/${uid}/loyalty_transactions`).add({
    amount: balance,
    source: "admin_adjust",
    ref_id: "fixture",
    balance_after: balance,
    created_at: Timestamp.now(),
  });
}

async function makeRound(id, overrides = {}) {
  await db.doc(`lottery_rounds/${id}`).set({
    name: `Round ${id}`,
    status: "draft",
    opens_at: Timestamp.fromMillis(Date.now() - 3600e3),
    sales_close_at: Timestamp.fromMillis(Date.now() + 24 * 3600e3),
    draw_at: Timestamp.fromMillis(Date.now() + 48 * 3600e3),
    ticket_cost_points: 150,
    max_tickets_per_user: 2,
    min_tickets_threshold: 3,
    sold_count: 0,
    commit_hash: null,
    revealed_seed: null,
    winning_numbers: null,
    rules_url: "https://example.org/regolamento.pdf",
    ...overrides,
  });
  for (let i = 1; i <= 3; i++) {
    await db.doc(`lottery_rounds/${id}/prizes/${i}`).set({
      rank: i,
      description: `Premio ${i}`,
      value_eur: [50, 25, 15][i - 1],
      winner_uid: null,
      winning_number: null,
    });
  }
  await db.doc(`lottery_rounds/${id}/grid/grid`).set({ taken: {} });
}

describe("Giochi: anti-cheat e cap famiglia", () => {
  async function makeFamily(uid) {
    await makeWallet(uid, 0);
    await db.doc(`users/${uid}`).set({ role: "parent" }, { merge: true });
    await db.doc(`users/${uid}/children/k1`).set({ nickname: "Leo", birth_year: 2018, consent_at: Timestamp.now() });
    await db.doc(`users/${uid}/children/k2`).set({ nickname: "Mia", birth_year: 2016, consent_at: Timestamp.now() });
    await db.doc("games/quiz-esploratore").set({ title: "Quiz", reward_points: 2, status: "active" });
  }

  it("sessione sotto i 30 secondi: registrata ma 0 punti", async () => {
    await makeFamily("fam_cheat");
    const r = await completeGameSessionCore("fam_cheat", "k1", "quiz-esploratore", 10, {});
    expect(r.pointsAwarded).toBe(0);
    const sessions = await db.collection("users/fam_cheat/game_sessions").get();
    expect(sessions.size).toBe(1);
  });

  it("il cap 10 punti/giorno vale per la FAMIGLIA, non per figlio", { timeout: 30000 }, async () => {
    await makeFamily("fam_cap");
    let total = 0;
    // 6 sessioni da 2 punti alternando i figli = 12 potenziali, cap a 10
    for (let i = 0; i < 6; i++) {
      const r = await completeGameSessionCore(
        "fam_cap",
        i % 2 === 0 ? "k1" : "k2",
        "quiz-esploratore",
        120,
        { correct: 5 }
      );
      total += r.pointsAwarded;
    }
    expect(total).toBe(10);
    const wallet = await db.doc("loyalty_wallet/fam_cap").get();
    expect(wallet.data().balance).toBe(10);

    const extra = await completeGameSessionCore("fam_cap", "k1", "quiz-esploratore", 120, {});
    expect(extra.pointsAwarded).toBe(0);
  });
});

describe("Lotteria: gate legale e acquisto", () => {
  it("un round SENZA regolamento non si apre (gate legale)", async () => {
    await makeRound("r_nogate", { rules_url: "" });
    await expect(openLotteryRoundCore("r_nogate", "admin_1")).rejects.toThrow(
      /validazione legale/
    );
  });

  it("acquisto: debito punti, biglietto, contatore e griglia in un'unica transazione", async () => {
    await makeRound("r_buy");
    await openLotteryRoundCore("r_buy", "admin_1");
    await makeWallet("player_1", 400);

    const r = await buyLotteryTicketCore("player_1", "r_buy", 42);
    expect(r.balance).toBe(250);

    const ticket = await db.doc("lottery_tickets/r_buy_42").get();
    expect(ticket.data().uid).toBe("player_1");
    const round = await db.doc("lottery_rounds/r_buy").get();
    expect(round.data().sold_count).toBe(1);
    const grid = await db.doc("lottery_rounds/r_buy/grid/grid").get();
    expect(grid.data().taken["42"]).toBe(true);
  });

  it("stesso numero, due utenti in parallelo: uno solo lo ottiene", { timeout: 30000 }, async () => {
    await makeRound("r_race");
    await openLotteryRoundCore("r_race", "admin_1");
    await makeWallet("racer_a", 200);
    await makeWallet("racer_b", 200);

    const results = await Promise.allSettled([
      buyLotteryTicketCore("racer_a", "r_race", 7),
      buyLotteryTicketCore("racer_b", "r_race", 7),
    ]);
    expect(results.filter((r) => r.status === "fulfilled").length).toBe(1);
    expect(results.filter((r) => r.status === "rejected").length).toBe(1);

    const ticket = await db.doc("lottery_tickets/r_race_7").get();
    const owner = ticket.data().uid;
    const loser = owner === "racer_a" ? "racer_b" : "racer_a";
    const loserWallet = await db.doc(`loyalty_wallet/${loser}`).get();
    expect(loserWallet.data().balance).toBe(200); // nessun addebito al perdente
  });

  it("limite numeri per utente e punti insufficienti", async () => {
    await makeRound("r_lim");
    await openLotteryRoundCore("r_lim", "admin_1");
    await makeWallet("limited", 500); // bastano per 3, il limite è 2
    await buyLotteryTicketCore("limited", "r_lim", 1);
    await buyLotteryTicketCore("limited", "r_lim", 2);
    await expect(buyLotteryTicketCore("limited", "r_lim", 3)).rejects.toThrow(/Massimo 2/);

    await makeWallet("broke", 100);
    await expect(buyLotteryTicketCore("broke", "r_lim", 4)).rejects.toThrow(/insufficienti/);
    const t4 = await db.doc("lottery_tickets/r_lim_4").get();
    expect(t4.exists).toBe(false);
  });
});

describe("Lotteria: estrazione commit-reveal", () => {
  it("drawNumbersFromSeed è deterministica e senza ripetizioni", () => {
    const pool = [3, 15, 27, 42, 88];
    const a = drawNumbersFromSeed("seme-fisso", pool);
    const b = drawNumbersFromSeed("seme-fisso", pool);
    expect(a).toEqual(b);
    expect(new Set(a).size).toBe(3);
    a.forEach((n) => expect(pool).toContain(n));
    expect(drawNumbersFromSeed("altro-seme", pool)).not.toEqual(a);
  });

  it("estrae 3 premi anche con pool minimo (3 o 4 venduti)", () => {
    expect(drawNumbersFromSeed("s", [1, 2, 3])).toHaveLength(3);
    expect(drawNumbersFromSeed("s", [1, 2, 3, 4])).toHaveLength(3);
    expect(new Set(drawNumbersFromSeed("s", [1, 2, 3, 4])).size).toBe(3);
  });

  it("ciclo completo: chiusura pubblica l'hash, l'estrazione è verificabile da chiunque", { timeout: 40000 }, async () => {
    await makeRound("r_draw");
    await openLotteryRoundCore("r_draw", "admin_1");
    const players = ["dw_1", "dw_2", "dw_3", "dw_4"];
    for (const [i, p] of players.entries()) {
      await makeWallet(p, 200);
      await buyLotteryTicketCore(p, "r_draw", (i + 1) * 10);
    }

    const { commitHash } = await closeLotterySalesCore("r_draw");
    let round = (await db.doc("lottery_rounds/r_draw").get()).data();
    expect(round.status).toBe("closed");
    expect(round.commit_hash).toBe(commitHash);

    const result = await drawLotteryCore("r_draw", "admin_1");
    expect(result.outcome).toBe("drawn");
    expect(result.winningNumbers).toHaveLength(3);

    // Verifica pubblica: hash(seed rivelato) == commit, ricalcolo identico
    round = (await db.doc("lottery_rounds/r_draw").get()).data();
    expect(sha256(round.revealed_seed)).toBe(commitHash);
    expect(drawNumbersFromSeed(round.revealed_seed, [10, 20, 30, 40])).toEqual(
      result.winningNumbers
    );

    // Premi assegnati e verbale in audit log
    const prize1 = (await db.doc("lottery_rounds/r_draw/prizes/1").get()).data();
    expect(prize1.winner_uid).toBeTruthy();
    const logs = await db.collection("admin_logs").where("action", "==", "draw_lottery").get();
    expect(logs.docs.some((d) => d.data().target_ref === "lottery_rounds/r_draw")).toBe(true);
  });

  it("sotto soglia minima: rimborso integrale e round refunded", { timeout: 30000 }, async () => {
    await makeRound("r_refund", { min_tickets_threshold: 30 });
    await openLotteryRoundCore("r_refund", "admin_1");
    await makeWallet("rf_1", 200);
    await makeWallet("rf_2", 200);
    await buyLotteryTicketCore("rf_1", "r_refund", 5);
    await buyLotteryTicketCore("rf_2", "r_refund", 6);
    await closeLotterySalesCore("r_refund");

    const result = await drawLotteryCore("r_refund", "admin_1");
    expect(result.outcome).toBe("refunded");
    expect(result.refundedTickets).toBe(2);

    for (const uid of ["rf_1", "rf_2"]) {
      const wallet = await db.doc(`loyalty_wallet/${uid}`).get();
      expect(wallet.data().balance).toBe(200); // integralmente rimborsato
    }
    const round = (await db.doc("lottery_rounds/r_refund").get()).data();
    expect(round.status).toBe("refunded");
  });
});
