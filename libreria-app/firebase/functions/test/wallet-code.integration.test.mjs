/**
 * Test QR wallet a rotazione: monouso, scadenza, replay in parallelo,
 * fallback UID.
 */
import { describe, it, expect } from "vitest";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error("Questi test girano solo contro l'emulatore (FIRESTORE_EMULATOR_HOST).");
}
if (getApps().length === 0) initializeApp({ projectId: "demo-libreribra" });
const db = getFirestore();

const { mintWalletCodeCore, resolveWalletCustomer } = await import("../lib/wallet-code.js");

describe("QR wallet a rotazione", () => {
  it("mint → resolve restituisce l'uid e brucia il codice", async () => {
    const { code, expiresInS } = await mintWalletCodeCore("cliente_1");
    expect(code).toMatch(/^W[A-Z2-9]{8}$/);
    expect(expiresInS).toBe(90);

    const uid = await resolveWalletCustomer(code);
    expect(uid).toBe("cliente_1");

    // Secondo uso (screenshot inoltrato): respinto
    await expect(resolveWalletCustomer(code)).rejects.toThrow(/già utilizzato/);
  });

  it("replay in parallelo: una sola risoluzione passa", { timeout: 20000 }, async () => {
    const { code } = await mintWalletCodeCore("cliente_2");
    const results = await Promise.allSettled([
      resolveWalletCustomer(code),
      resolveWalletCustomer(code),
    ]);
    expect(results.filter((r) => r.status === "fulfilled").length).toBe(1);
    expect(results.filter((r) => r.status === "rejected").length).toBe(1);
  });

  it("codice scaduto: respinto con invito a rigenerare", async () => {
    const { code } = await mintWalletCodeCore("cliente_3");
    await db.doc(`wallet_codes/${code}`).update({
      expires_at: Timestamp.fromMillis(Date.now() - 1000),
    });
    await expect(resolveWalletCustomer(code)).rejects.toThrow(/scaduto/);
  });

  it("codice inesistente: respinto", async () => {
    await expect(resolveWalletCustomer("WAAAAAAAA")).rejects.toThrow(/non riconosciuto/);
  });

  it("fallback UID diretto: passa se l'utente esiste, altrimenti errore", async () => {
    await db.doc("users/uid_diretto").set({ role: "parent", status: "active" });
    expect(await resolveWalletCustomer("uid_diretto")).toBe("uid_diretto");
    await expect(resolveWalletCustomer("uid_fantasma")).rejects.toThrow(/non trovato/);
  });
});
