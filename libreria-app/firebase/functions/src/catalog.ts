/**
 * Catalogo libri — import CSV dal backoffice e notifica riassortimento.
 *
 * Formato CSV (separatore , o ; — intestazione obbligatoria):
 *   isbn,titolo,autore,editore,prezzo,fasce_eta,temi,disponibilita,
 *   consiglio_libraio,descrizione,novita,saldo,prezzo_saldo
 * - fasce_eta e temi: valori multipli separati da |
 * - prezzo: "12,90" o "12.90"
 * - disponibilita: disponibile | poche_copie | esaurito (o in_stock|low|out)
 */

import * as functionsV1 from "firebase-functions/v1";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

const AGE_BANDS = ["0-2", "3-5", "6-8", "9-12", "13+", "adulti"];
const AVAILABILITY_MAP: Record<string, string> = {
  disponibile: "in_stock",
  poche_copie: "low",
  esaurito: "out",
  in_stock: "in_stock",
  low: "low",
  out: "out",
};
const MAX_ROWS = 1000;
const REGION = "europe-west1";
const STORE_ID = "libreribra-sangiovanni";

export interface RowError {
  row: number; // 1-based, esclusa intestazione
  errors: string[];
}

export interface ParsedBook {
  docId: string;
  data: Record<string, unknown>;
}

/** Split di una riga CSV rispettando i campi tra virgolette. */
function splitCsvLine(line: string, sep: string): string[] {
  const out: string[] = [];
  let cur = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (inQuotes) {
      if (ch === '"' && line[i + 1] === '"') {
        cur += '"';
        i++;
      } else if (ch === '"') {
        inQuotes = false;
      } else {
        cur += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === sep) {
      out.push(cur);
      cur = "";
    } else {
      cur += ch;
    }
  }
  out.push(cur);
  return out.map((s) => s.trim());
}

function slugify(s: string): string {
  return s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
}

/**
 * Valida e trasforma il CSV. Pura: nessun accesso a Firestore,
 * testabile in isolamento. Non scarta l'intero file per una riga
 * sbagliata: restituisce libri validi + report errori riga per riga.
 */
export function parseBooksCsv(csvText: string): {
  books: ParsedBook[];
  rowErrors: RowError[];
} {
  const lines = csvText
    .replace(/^﻿/, "") // BOM di Excel
    .split(/\r?\n/)
    .filter((l) => l.trim().length > 0);
  if (lines.length < 2) {
    throw new HttpsError(
      "invalid-argument",
      "Il file deve contenere l'intestazione e almeno una riga."
    );
  }

  const sep = lines[0].includes(";") ? ";" : ",";
  const header = splitCsvLine(lines[0], sep).map((h) =>
    h.toLowerCase().replace(/\s+/g, "_")
  );
  for (const required of ["titolo", "autore", "prezzo"]) {
    if (!header.includes(required)) {
      throw new HttpsError(
        "invalid-argument",
        `Colonna obbligatoria mancante nell'intestazione: ${required}`
      );
    }
  }
  if (lines.length - 1 > MAX_ROWS) {
    throw new HttpsError(
      "invalid-argument",
      `Massimo ${MAX_ROWS} righe per import: dividere il file.`
    );
  }

  const books: ParsedBook[] = [];
  const rowErrors: RowError[] = [];
  const seenIds = new Set<string>();

  for (let i = 1; i < lines.length; i++) {
    const cells = splitCsvLine(lines[i], sep);
    const row: Record<string, string> = {};
    header.forEach((h, idx) => (row[h] = cells[idx] ?? ""));
    const errors: string[] = [];

    if (!row.titolo) errors.push("titolo mancante");
    if (!row.autore) errors.push("autore mancante");

    const price = Number(row.prezzo.replace(",", "."));
    if (!Number.isFinite(price) || price <= 0) {
      errors.push(`prezzo non valido: "${row.prezzo}"`);
    }

    const isbn = (row.isbn ?? "").replace(/[-\s]/g, "");
    if (isbn && !/^\d{10}(\d{3})?$/.test(isbn)) {
      errors.push(`isbn non valido: "${row.isbn}" (10 o 13 cifre)`);
    }

    const ageBands = (row.fasce_eta ?? "")
      .split("|")
      .map((s) => s.trim())
      .filter(Boolean);
    const badBands = ageBands.filter((b) => !AGE_BANDS.includes(b));
    if (badBands.length > 0) {
      errors.push(
        `fasce_eta non valide: ${badBands.join(", ")} (ammesse: ${AGE_BANDS.join(", ")})`
      );
    }

    const availRaw = (row.disponibilita ?? "disponibile").toLowerCase();
    const availability = AVAILABILITY_MAP[availRaw];
    if (!availability) {
      errors.push(`disponibilita non valida: "${row.disponibilita}"`);
    }

    const onSale = ["si", "sì", "true", "1", "x"].includes(
      (row.saldo ?? "").toLowerCase()
    );
    let salePrice: number | null = null;
    if (onSale) {
      salePrice = Number((row.prezzo_saldo ?? "").replace(",", "."));
      if (!Number.isFinite(salePrice) || salePrice <= 0 || salePrice >= price) {
        errors.push(
          `prezzo_saldo non valido: "${row.prezzo_saldo}" (deve essere > 0 e < prezzo)`
        );
      }
    }

    const docId = isbn || slugify(`${row.titolo}-${row.autore}`);
    if (seenIds.has(docId)) {
      errors.push(`riga duplicata nel file (stesso isbn/titolo+autore)`);
    }

    if (errors.length > 0) {
      rowErrors.push({ row: i, errors });
      continue;
    }
    seenIds.add(docId);

    books.push({
      docId,
      data: {
        title: row.titolo,
        author: row.autore,
        publisher: row.editore || null,
        isbn: isbn || null,
        description: row.descrizione || null,
        staff_pick_note: row.consiglio_libraio || null,
        age_bands: ageBands,
        themes: (row.temi ?? "")
          .split("|")
          .map((s) => s.trim())
          .filter(Boolean),
        price,
        on_sale: onSale,
        sale_price: salePrice,
        is_new: ["si", "sì", "true", "1", "x"].includes(
          (row.novita ?? "").toLowerCase()
        ),
        availability,
      },
    });
  }

  return { books, rowErrors };
}

/**
 * Import catalogo dal backoffice (solo staff/admin).
 * Upsert per docId (ISBN o slug): rilanciare lo stesso file è idempotente.
 */
export const importBooksCsv = onCall(async (request) => {
  const role = request.auth?.token?.role as string | undefined;
  if (!role || !["staff", "admin"].includes(role)) {
    throw new HttpsError("permission-denied", "Riservato allo staff.");
  }
  const csvText = request.data?.csv;
  if (typeof csvText !== "string" || csvText.length === 0) {
    throw new HttpsError("invalid-argument", "Campo 'csv' mancante.");
  }

  const { books, rowErrors } = parseBooksCsv(csvText);
  const db = getFirestore();

  let written = 0;
  for (let i = 0; i < books.length; i += 400) {
    const batch = db.batch();
    for (const b of books.slice(i, i + 400)) {
      batch.set(
        db.doc(`books/${b.docId}`),
        {
          ...b.data,
          store_id: STORE_ID,
          availability_checked_at: FieldValue.serverTimestamp(),
          updated_at: FieldValue.serverTimestamp(),
          created_at: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
      written++;
    }
    await batch.commit();
  }

  await db.collection("admin_logs").add({
    actor_uid: request.auth!.uid,
    action: "csv_import",
    target_ref: "books",
    payload: { imported: written, rejected: rowErrors.length },
    at: FieldValue.serverTimestamp(),
    store_id: STORE_ID,
  });

  return { imported: written, rejected: rowErrors.length, rowErrors };
});

/**
 * Riassortimento: quando un libro torna disponibile, notifica in-app
 * chi lo ha in wishlist. (Push FCM: cablata nello Sprint 5.)
 */
export const onBookRestocked = functionsV1
  .region(REGION)
  .firestore.document("books/{bookId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();
    if (before.availability !== "out" || after.availability === "out") {
      return;
    }

    const db = getFirestore();
    const bookId = context.params.bookId as string;
    const watchers = await db
      .collectionGroup("wishlist")
      .where("book_id", "==", bookId)
      .limit(500)
      .get();
    if (watchers.empty) return;

    const batch = db.batch();
    for (const w of watchers.docs) {
      const uid = w.ref.parent.parent?.id;
      if (!uid) continue;
      batch.set(db.collection(`users/${uid}/notifications`).doc(), {
        type: "restock",
        title: "È tornato! 📚",
        body: `"${after.title}" è di nuovo disponibile in libreria.`,
        deep_link: `/libro/${bookId}`,
        read: false,
        sent_at: FieldValue.serverTimestamp(),
        store_id: STORE_ID,
      });
    }
    await batch.commit();
  });
