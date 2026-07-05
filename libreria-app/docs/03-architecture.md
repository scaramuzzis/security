# TASK 3 — App Architecture

Stack deciso il 2026-07-05: **Flux/FluxBuilder (layer UI mobile) + Firebase (backend)**.

## Perché Firebase e non Supabase

| Criterio | Firebase | Supabase | Peso per questo progetto |
|---|---|---|---|
| Integrazione con FluxBuilder | Nativa/documentata (il builder nasce nell'ecosistema Firebase) | Via REST API, più colla da scrivere | **Decisivo**: meno codice ponte = meno failure surface |
| Push notification | FCM incluso, gratis | Servizio esterno da integrare | Decisivo: le push sono un pilastro del piano growth |
| Auth (email/OTP, Google, Apple) | Pronta, con SDK mobile maturi | Pronta, buona | Pari |
| Vincoli transazionali (lotteria, punti) | Transazioni Firestore + Cloud Functions | Postgres con UNIQUE e transazioni SQL native | **Vantaggio Supabase**, mitigabile: vincoli critici implementati in Cloud Functions con transazioni Firestore (vedi sotto) |
| Analytics | Firebase Analytics incluso | Da integrare (PostHog) | Vantaggio Firebase |
| Costi a questa scala (migliaia di utenti locali) | Piano Spark/Blaze: quasi zero | Piano free generoso | Pari |
| Competenze richieste allo staff | Console Firebase matura | Dashboard SQL | Leggero vantaggio Firebase |

**Verdetto**: Firebase vince su integrazione col builder, push e analytics — i tre punti dove il progetto non può permettersi attrito. Il punto debole (niente vincoli UNIQUE dichiarativi) si gestisce così: **ogni scrittura che tocca punti o biglietti lotteria passa da una Cloud Function con transazione Firestore**, mai da scritture client dirette. Le security rules negano al client la scrittura su `loyalty_transactions`, `lottery_tickets`, `orders`.

## Vincoli FluxBuilder (in assenza di `docs/flux-constraints.md`)

Assunzioni prudenti, da verificare nello Sprint 0 con un walking skeleton:

1. FluxBuilder gestisce bene: schermate CRUD su dati remoti, liste, form, auth, push, mappe, webview.
2. Logica di gioco interattiva (puzzle drag & drop, timer) potrebbe eccedere i componenti standard → piano B: i giochi girano in **webview HTML5 self-hosted** autenticata via token Firebase, i risultati tornano via callable function.
3. Nessuna logica di business critica nel client: il builder fa UI e orchestrazione, le regole vivono in Cloud Functions.

**Stop-rule**: se lo spike Sprint 0 dimostra che FluxBuilder non regge feed+eventi+catalogo con Firebase, fermarsi e riferire prima di proseguire (mismatch stack reale vs proposta).

## Componenti

```
┌─────────────────────────────────────────────┐
│ App mobile (FluxBuilder, iOS + Android)     │
│ UI, navigazione, cache locale, deep link    │
│ + webview giochi (HTML5 self-hosted)        │
└──────────────┬──────────────────────────────┘
               │ Firebase SDK / REST
┌──────────────▼──────────────────────────────┐
│ Firebase                                    │
│ · Auth (email/OTP, Google, Apple)           │
│ · Firestore (dati, security rules)          │
│ · Storage (immagini, regole per path)       │
│ · Cloud Functions (logica di dominio)       │
│ · FCM (push segmentate per topic)           │
│ · Analytics (eventi §08)                    │
│ · Scheduled Functions (chiusure lotteria,   │
│   scadenza punti, reminder eventi)          │
└──────────────┬──────────────────────────────┘
               │
┌──────────────▼──────────────────────────────┐
│ Backoffice web (hosting Firebase)           │
│ 6 sezioni: Feed · Libri · Eventi · Utenti   │
│ · Loyalty/Lotteria · Report                 │
└─────────────────────────────────────────────┘
Fase 2: + Stripe (checkout in-app) via Functions
```

## Autenticazione e ruoli

| Ruolo | Come si ottiene | Capacità |
|---|---|---|
| `admin` | Assegnato a mano (custom claim) | Tutto: contenuti, utenti, lotteria, log |
| `staff` | Assegnato da admin | Post, eventi, catalogo, check-in, accredito punti (con PIN), moderazione commenti |
| `parent` | Registrazione standard (email/OTP, Google, Apple) | Feed (like/commenti/salva), prenotazioni, wishlist, wallet, profili figli, giochi, lotteria se 18+ |
| `child_profile` | **Non è un account**: sotto-profilo del genitore | Area giochi in modalità bambino, nessuna azione sociale, nessun dato di contatto |

- Ruoli via **custom claims** Firebase Auth; le security rules e le Functions li verificano server-side.
- Verifica 18+ per la lotteria: data di nascita obbligatoria nel profilo + autodichiarazione; il regolamento legale definirà se serve di più.

## Gestione minori e consenso (GDPR art. 8, D.Lgs. 101/2018)

1. Nessun account per minori di 14 anni: solo profili figlio dentro l'account genitore.
2. Dati del figlio minimizzati: nome (o soprannome), anno di nascita, interessi. **Niente foto, niente cognome obbligatorio, niente geolocalizzazione.**
3. Consenso esplicito del genitore alla creazione del profilo figlio, con testo chiaro su quali dati e perché.
4. Modalità bambino: UI separata senza commenti, senza link esterni, senza acquisti, senza push.
5. Cancellazione account → cancellazione a cascata dei profili figlio e delle sessioni gioco (Function `onUserDelete`).
6. **DPIA (valutazione d'impatto) prima del rilascio dell'area giochi** — insieme al parere legale sulla lotteria.

## Content moderation

- Feed: pubblica solo staff/admin → moderazione a monte.
- Commenti: filtro sincrono lato Function (lista parole vietate italiana + heuristics spam: link, ripetizione, frequenza >5 commenti/10 min) → `pending` se sospetto, visibile se pulito; pulsante "Segnala" su ogni commento → coda `reports` nel backoffice; staff può nascondere (soft-delete con motivo, tracciato in `admin_logs`).

## Sicurezza dati

- Security rules: default deny; ogni collezione ha regole esplicite (dettaglio per entità in `06-data-model.md`).
- Il client **non può mai scrivere**: `loyalty_transactions`, `loyalty_wallet`, `lottery_*`, `orders`, `admin_logs`, `inventory`.
- Storage: `/public/**` lettura aperta (copertine, foto negozio); `/users/{uid}/**` lettura/scrittura solo proprietario; upload max 5 MB, solo image/*.
- Backup: export Firestore schedulato settimanale su bucket dedicato.
- Nessun dato sensibile in Analytics (no nomi, no età figli: solo fasce).

## Backoffice

Web app minimale su Firebase Hosting, login riservato staff/admin. Sei sezioni, nessuna di più:

1. **Feed**: crea/modifica post, coda commenti segnalati.
2. **Libri**: CRUD catalogo, import CSV, toggle disponibilità/saldo, coupon.
3. **Eventi**: CRUD eventi, lista prenotati, check-in QR, esporta presenze.
4. **Utenti**: ricerca, dettaglio (senza dati figli oltre il conteggio), gestione segnalazioni.
5. **Loyalty & Lotteria**: accredito manuale con PIN, configurazione round, avvio estrazione, verbale.
6. **Report**: KPI del §08 in forma leggibile dal libraio.

## Scalabilità futura

- Multi-libreria (visione SaaS del piano): ogni entità ha `store_id` fin dal giorno 1 — costo quasi zero ora, migrazione evitata dopo.
- Pagamenti Fase 2: Stripe via Functions (PaymentIntent), nessun dato carta nell'app.
- Se i giochi crescono: la webview HTML5 è già indipendente dal builder e riusabile.

---
**Riepilogo TASK 3**: Firebase scelto per integrazione nativa con FluxBuilder, FCM e Analytics; il gap sui vincoli transazionali si chiude spostando ogni scrittura critica in Cloud Functions con transazioni. Ruoli via custom claims, minori senza account propri, moderazione a monte + filtro commenti, backoffice a 6 sezioni, `store_id` ovunque per il futuro multi-negozio. Stop-rule: spike FluxBuilder nello Sprint 0.
