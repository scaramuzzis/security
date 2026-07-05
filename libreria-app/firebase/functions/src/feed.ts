/**
 * Feed (docs/05 §A): moderazione commenti, contatori denormalizzati,
 * segnalazioni. Il client crea i commenti in 'pending'; qui si decide.
 */

import * as functionsV1 from "firebase-functions/v1";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";

const REGION = "europe-west1";
const STORE_ID = "libreribra-sangiovanni";
const RATE_WINDOW_MS = 10 * 60 * 1000;
const RATE_MAX_COMMENTS = 5;
const REPORTS_PER_DAY = 5;
const STRIKES_FOR_RESTRICTION = 3;
const RESTRICTION_DAYS = 30;

/**
 * Lista di partenza; lo staff la estende dal backoffice nel documento
 * moderation/banned_words (campo words: array).
 */
const BANNED_WORDS_SEED = [
  "cazzo", "merda", "stronzo", "stronza", "vaffanculo", "coglione",
  "puttana", "troia", "bastardo", "porca", "porco",
];

const URL_PATTERN = /(https?:\/\/|www\.)\S+/i;

export interface CommentSignals {
  recentCount: number; // commenti dell'autore negli ultimi 10 minuti
  restricted: boolean;
  bannedWords: string[];
}

/** Pura e testabile: decide lo stato del commento. */
export function classifyComment(
  text: string,
  signals: CommentSignals
): { status: "visible" | "pending"; reason: string | null } {
  if (signals.restricted) {
    return { status: "pending", reason: "autore in restrizione" };
  }
  if (URL_PATTERN.test(text)) {
    return { status: "pending", reason: "link non consentiti" };
  }
  if (signals.recentCount >= RATE_MAX_COMMENTS) {
    return { status: "pending", reason: "troppi commenti ravvicinati" };
  }
  const lower = ` ${text.toLowerCase()} `;
  const hit = signals.bannedWords.find((w) =>
    new RegExp(`[^a-zà-ú]${w}[^a-zà-ú]`, "i").test(lower)
  );
  if (hit) {
    return { status: "pending", reason: "linguaggio non adatto" };
  }
  return { status: "visible", reason: null };
}

async function loadBannedWords(): Promise<string[]> {
  const snap = await getFirestore().doc("moderation/banned_words").get();
  const custom = (snap.data()?.words as string[]) ?? [];
  return [...new Set([...BANNED_WORDS_SEED, ...custom])];
}

export const onCommentCreate = functionsV1
  .region(REGION)
  .firestore.document("posts/{postId}/comments/{commentId}")
  .onCreate(async (snap, context) => {
    const db = getFirestore();
    const comment = snap.data();
    const authorUid = comment.author_uid as string;

    const [userSnap, recent, bannedWords] = await Promise.all([
      db.doc(`users/${authorUid}`).get(),
      db
        .collectionGroup("comments")
        .where("author_uid", "==", authorUid)
        .where("created_at", ">", Timestamp.fromMillis(Date.now() - RATE_WINDOW_MS))
        .get(),
      loadBannedWords(),
    ]);

    const user = userSnap.data() ?? {};
    const restricted =
      user.status === "restricted" &&
      user.restricted_until &&
      (user.restricted_until as Timestamp).toMillis() > Date.now();

    const verdict = classifyComment(comment.text as string, {
      recentCount: recent.size - 1, // esclude il commento appena creato
      restricted: Boolean(restricted),
      bannedWords,
    });

    const updates: Record<string, unknown> = {
      status: verdict.status,
      created_at: comment.created_at ?? FieldValue.serverTimestamp(),
    };
    if (verdict.status === "pending") {
      updates.pending_reason = verdict.reason;
    }
    await snap.ref.update(updates);

    if (verdict.status === "visible") {
      await db
        .doc(`posts/${context.params.postId}`)
        .update({ comment_count: FieldValue.increment(1) });
    }
  });

// ---------- Contatori like e salvataggi ----------

export const onLikeCreate = functionsV1
  .region(REGION)
  .firestore.document("posts/{postId}/likes/{likeId}")
  .onCreate((_snap, context) =>
    getFirestore()
      .doc(`posts/${context.params.postId}`)
      .update({ like_count: FieldValue.increment(1) })
  );

export const onLikeDelete = functionsV1
  .region(REGION)
  .firestore.document("posts/{postId}/likes/{likeId}")
  .onDelete((_snap, context) =>
    getFirestore()
      .doc(`posts/${context.params.postId}`)
      .update({ like_count: FieldValue.increment(-1) })
  );

export const onSaveCreate = functionsV1
  .region(REGION)
  .firestore.document("users/{uid}/saves/{postId}")
  .onCreate((_snap, context) =>
    getFirestore()
      .doc(`posts/${context.params.postId}`)
      .update({ save_count: FieldValue.increment(1) })
  );

export const onSaveDelete = functionsV1
  .region(REGION)
  .firestore.document("users/{uid}/saves/{postId}")
  .onDelete((_snap, context) =>
    getFirestore()
      .doc(`posts/${context.params.postId}`)
      .update({ save_count: FieldValue.increment(-1) })
  );

// ---------- Moderazione staff ----------

export async function moderateCommentCore(
  postId: string,
  commentId: string,
  action: "hide" | "restore",
  reason: string,
  operatorUid: string
): Promise<{ authorRestricted: boolean }> {
  const db = getFirestore();
  const commentRef = db.doc(`posts/${postId}/comments/${commentId}`);

  const authorUid = await db.runTransaction(async (t) => {
    const snap = await t.get(commentRef);
    if (!snap.exists) throw new HttpsError("not-found", "Commento non trovato.");
    const c = snap.data()!;

    if (action === "hide") {
      if (c.status === "hidden") return null;
      t.update(commentRef, {
        status: "hidden",
        hidden_reason: reason,
      });
      if (c.status === "visible") {
        t.update(db.doc(`posts/${postId}`), {
          comment_count: FieldValue.increment(-1),
        });
      }
    } else {
      if (c.status !== "hidden") return null;
      t.update(commentRef, { status: "visible", hidden_reason: null });
      t.update(db.doc(`posts/${postId}`), {
        comment_count: FieldValue.increment(1),
      });
    }
    return c.author_uid as string;
  });

  await db.collection("admin_logs").add({
    actor_uid: operatorUid,
    action: action === "hide" ? "hide_comment" : "restore_comment",
    target_ref: `posts/${postId}/comments/${commentId}`,
    payload: { reason },
    at: FieldValue.serverTimestamp(),
    store_id: STORE_ID,
  });

  // 3 commenti nascosti → restrizione 30 giorni (commenti sempre in revisione)
  let authorRestricted = false;
  if (action === "hide" && authorUid) {
    const hidden = await db
      .collectionGroup("comments")
      .where("author_uid", "==", authorUid)
      .where("status", "==", "hidden")
      .get();
    if (hidden.size >= STRIKES_FOR_RESTRICTION) {
      await db.doc(`users/${authorUid}`).update({
        status: "restricted",
        restricted_until: Timestamp.fromMillis(
          Date.now() + RESTRICTION_DAYS * 24 * 3600 * 1000
        ),
        updated_at: FieldValue.serverTimestamp(),
      });
      authorRestricted = true;
    }
  }
  return { authorRestricted };
}

export const moderateComment = onCall(async (request) => {
  const role = request.auth?.token?.role as string | undefined;
  if (!role || !["staff", "admin"].includes(role)) {
    throw new HttpsError("permission-denied", "Riservato allo staff.");
  }
  const { postId, commentId, action, reason } = request.data ?? {};
  if (
    typeof postId !== "string" ||
    typeof commentId !== "string" ||
    !["hide", "restore"].includes(action) ||
    (action === "hide" && (typeof reason !== "string" || reason.length === 0))
  ) {
    throw new HttpsError(
      "invalid-argument",
      "postId, commentId, action (hide|restore) e reason (per hide) richiesti."
    );
  }
  return moderateCommentCore(postId, commentId, action, reason ?? "", request.auth!.uid);
});

// ---------- Segnalazioni ----------

export async function submitReportCore(
  reporterUid: string,
  targetType: "comment" | "user",
  targetRef: string,
  reason: string
): Promise<{ reportId: string }> {
  const db = getFirestore();
  const since = Timestamp.fromMillis(Date.now() - 24 * 3600 * 1000);
  const todays = await db
    .collection("reports")
    .where("reporter_uid", "==", reporterUid)
    .where("created_at", ">", since)
    .get();
  if (todays.size >= REPORTS_PER_DAY) {
    throw new HttpsError(
      "resource-exhausted",
      "Hai raggiunto il limite di segnalazioni per oggi. Grazie per l'aiuto!"
    );
  }
  const ref = await db.collection("reports").add({
    reporter_uid: reporterUid,
    target_type: targetType,
    target_ref: targetRef,
    reason,
    status: "open",
    resolved_by: null,
    resolution_note: null,
    store_id: STORE_ID,
    created_at: FieldValue.serverTimestamp(),
    updated_at: FieldValue.serverTimestamp(),
  });
  return { reportId: ref.id };
}

export const submitReport = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Accesso richiesto.");
  const { targetType, targetRef, reason } = request.data ?? {};
  if (
    !["comment", "user"].includes(targetType) ||
    typeof targetRef !== "string" ||
    typeof reason !== "string" ||
    reason.length === 0 ||
    reason.length > 500
  ) {
    throw new HttpsError("invalid-argument", "targetType, targetRef e reason (≤500) richiesti.");
  }
  return submitReportCore(uid, targetType, targetRef, reason);
});
