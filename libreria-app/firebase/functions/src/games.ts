/**
 * Area giochi 6-12 (docs/05 §F): registrazione sessioni con anti-cheat
 * e cap famiglia 10 punti/giorno (sommati su tutti i giochi e figli).
 * Nessuna scrittura client su game_sessions: solo questa callable.
 */

import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { applyLoyaltyTransaction } from "./loyalty";

const STORE_ID = "libreribra-sangiovanni";
const FAMILY_DAILY_CAP = 10;
const MIN_SESSION_SECONDS = 30; // sotto: sessione registrata, 0 punti
const MAX_SESSION_SECONDS = 30 * 60;

function startOfTodayRome(): Timestamp {
  const now = new Date();
  const rome = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Rome",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now); // YYYY-MM-DD
  return Timestamp.fromDate(new Date(`${rome}T00:00:00+02:00`));
}

export async function completeGameSessionCore(
  uid: string,
  childId: string,
  gameId: string,
  durationS: number,
  score: Record<string, unknown>
): Promise<{ pointsAwarded: number; dailyRemaining: number }> {
  const db = getFirestore();

  const [gameSnap, childSnap] = await Promise.all([
    db.doc(`games/${gameId}`).get(),
    db.doc(`users/${uid}/children/${childId}`).get(),
  ]);
  if (!gameSnap.exists) throw new HttpsError("not-found", "Gioco non trovato.");
  if (!childSnap.exists) {
    throw new HttpsError("not-found", "Profilo bambino non trovato.");
  }
  const rewardPoints = (gameSnap.data()!.reward_points as number) ?? 1;

  // Anti-cheat: durata implausibile → sessione valida ma senza punti
  const cheated =
    !Number.isFinite(durationS) ||
    durationS < MIN_SESSION_SECONDS ||
    durationS > MAX_SESSION_SECONDS;

  // Cap famiglia: somma dei punti di OGGI su tutti i figli e giochi
  const todaySessions = await db
    .collection(`users/${uid}/game_sessions`)
    .where("completed_at", ">=", startOfTodayRome())
    .get();
  const todayPoints = todaySessions.docs.reduce(
    (sum, d) => sum + ((d.data().points_awarded as number) ?? 0),
    0
  );
  const remaining = Math.max(0, FAMILY_DAILY_CAP - todayPoints);
  const pointsAwarded = cheated ? 0 : Math.min(rewardPoints, remaining);

  const sessionRef = db.collection(`users/${uid}/game_sessions`).doc();
  const sessionData = {
    child_id: childId,
    game_id: gameId,
    started_at: Timestamp.fromMillis(Date.now() - durationS * 1000),
    completed_at: FieldValue.serverTimestamp(),
    duration_s: Math.round(durationS),
    score,
    points_awarded: pointsAwarded,
    store_id: STORE_ID,
    created_at: FieldValue.serverTimestamp(),
  };

  if (pointsAwarded > 0) {
    await applyLoyaltyTransaction({
      uid,
      amount: pointsAwarded,
      source: "game",
      refId: sessionRef.id,
      extraWrites: (t) => t.set(sessionRef, sessionData),
    });
  } else {
    await sessionRef.set(sessionData);
  }

  return { pointsAwarded, dailyRemaining: remaining - pointsAwarded };
}

/**
 * Token per la webview giochi: l'app FluxBuilder lo richiede autenticata
 * e lo passa nell'URL della webview, che fa signInWithCustomToken.
 * Scadenza gestita da Firebase (1h), stesso uid del genitore.
 */
export const mintGamesToken = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Accesso richiesto.");
  const token = await getAuth().createCustomToken(uid, { webview: "games" });
  return { token };
});

export const completeGameSession = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Accesso richiesto.");
  const { childId, gameId, durationS, score } = request.data ?? {};
  if (
    typeof childId !== "string" ||
    typeof gameId !== "string" ||
    typeof durationS !== "number"
  ) {
    throw new HttpsError("invalid-argument", "childId, gameId e durationS richiesti.");
  }
  return completeGameSessionCore(
    uid,
    childId,
    gameId,
    durationS,
    typeof score === "object" && score !== null ? score : {}
  );
});
