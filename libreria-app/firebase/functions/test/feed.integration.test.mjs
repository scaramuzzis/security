/**
 * Test Sprint 5: classificazione commenti (unit), moderazione con
 * 3-strikes (integrazione), rate limit segnalazioni, filtro push.
 */
import { describe, it, expect } from "vitest";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error("Questi test girano solo contro l'emulatore (FIRESTORE_EMULATOR_HOST).");
}
if (getApps().length === 0) initializeApp({ projectId: "demo-libreribra" });
const db = getFirestore();

const { classifyComment, moderateCommentCore, submitReportCore } = await import(
  "../lib/feed.js"
);
const { shouldSendPush } = await import("../lib/push.js");

const cleanSignals = { recentCount: 0, restricted: false, bannedWords: ["cazzo"] };

describe("classifyComment (unit)", () => {
  it("commento pulito → visible", () => {
    expect(classifyComment("Che bel laboratorio, torneremo!", cleanSignals).status).toBe(
      "visible"
    );
  });

  it("parola vietata → pending, anche in mezzo alla frase", () => {
    const v = classifyComment("ma che cazzo dite", cleanSignals);
    expect(v.status).toBe("pending");
    expect(v.reason).toBe("linguaggio non adatto");
  });

  it("la parola vietata dentro un'altra parola NON scatta (es. 'cazzottiere')", () => {
    expect(classifyComment("un libro sul cazzottiere gentile", cleanSignals).status).toBe(
      "visible"
    );
  });

  it("URL → pending", () => {
    expect(
      classifyComment("guardate https://spam.example", cleanSignals).status
    ).toBe("pending");
    expect(classifyComment("visita www.spam.example ora", cleanSignals).status).toBe(
      "pending"
    );
  });

  it("troppi commenti ravvicinati → pending", () => {
    expect(
      classifyComment("bello!", { ...cleanSignals, recentCount: 5 }).status
    ).toBe("pending");
  });

  it("autore in restrizione → sempre pending", () => {
    expect(
      classifyComment("bello!", { ...cleanSignals, restricted: true }).status
    ).toBe("pending");
  });
});

describe("shouldSendPush (unit)", () => {
  const day = new Date("2026-07-05T15:00:00+02:00");
  const night = new Date("2026-07-05T22:30:00+02:00");

  it("transazionale: sempre, anche di notte e con prefs spente", () => {
    expect(shouldSendPush("order_ready", { novita: false }, night)).toBe(true);
    expect(shouldSendPush("event_reminder", undefined, night)).toBe(true);
  });

  it("promozionale: rispetta la preferenza di categoria", () => {
    expect(shouldSendPush("restock", { novita: false }, day)).toBe(false);
    expect(shouldSendPush("restock", { novita: true }, day)).toBe(true);
  });

  it("promozionale in quiet hours (21-9): mai", () => {
    expect(shouldSendPush("restock", { novita: true }, night)).toBe(false);
  });
});

describe("Moderazione (integrazione)", () => {
  async function makeComment(postId, commentId, authorUid, status = "visible") {
    await db.doc(`posts/${postId}`).set(
      { status: "published", comment_count: status === "visible" ? 1 : 0 },
      { merge: true }
    );
    await db.doc(`posts/${postId}/comments/${commentId}`).set({
      author_uid: authorUid,
      text: "testo",
      status,
      created_at: Timestamp.now(),
    });
  }

  it("hide decrementa il contatore e logga in admin_logs; restore lo ripristina", async () => {
    await makeComment("p1", "c1", "author_x");
    await moderateCommentCore("p1", "c1", "hide", "off topic", "staff_1");

    let post = await db.doc("posts/p1").get();
    expect(post.data().comment_count).toBe(0);
    const comment = await db.doc("posts/p1/comments/c1").get();
    expect(comment.data().status).toBe("hidden");

    const logs = await db
      .collection("admin_logs")
      .where("action", "==", "hide_comment")
      .get();
    expect(logs.size).toBeGreaterThan(0);

    await moderateCommentCore("p1", "c1", "restore", "", "staff_1");
    post = await db.doc("posts/p1").get();
    expect(post.data().comment_count).toBe(1);
  });

  it("hide idempotente: nascondere due volte non decrementa due volte", async () => {
    await makeComment("p2", "c1", "author_y");
    await moderateCommentCore("p2", "c1", "hide", "spam", "staff_1");
    await moderateCommentCore("p2", "c1", "hide", "spam", "staff_1");
    const post = await db.doc("posts/p2").get();
    expect(post.data().comment_count).toBe(0);
  });

  it("al terzo commento nascosto l'autore va in restrizione 30 giorni", async () => {
    await db.doc("users/spammer").set({ role: "parent", status: "active" });
    await makeComment("p3", "s1", "spammer");
    await makeComment("p3", "s2", "spammer");
    await makeComment("p3", "s3", "spammer");

    await moderateCommentCore("p3", "s1", "hide", "spam", "staff_1");
    await moderateCommentCore("p3", "s2", "hide", "spam", "staff_1");
    const third = await moderateCommentCore("p3", "s3", "hide", "spam", "staff_1");
    expect(third.authorRestricted).toBe(true);

    const user = await db.doc("users/spammer").get();
    expect(user.data().status).toBe("restricted");
    expect(user.data().restricted_until.toMillis()).toBeGreaterThan(Date.now());
  });
});

describe("Segnalazioni (integrazione)", () => {
  it("massimo 5 al giorno, la sesta è respinta con messaggio gentile", async () => {
    for (let i = 0; i < 5; i++) {
      await submitReportCore("reporter_1", "comment", `posts/p1/comments/c${i}`, "spam");
    }
    await expect(
      submitReportCore("reporter_1", "comment", "posts/p1/comments/c9", "spam")
    ).rejects.toThrow(/limite di segnalazioni/);

    const reports = await db
      .collection("reports")
      .where("reporter_uid", "==", "reporter_1")
      .get();
    expect(reports.size).toBe(5);
  });
});
