# 📦 Documento di consegna — App LibreriBrà

**Branch**: `claude/libreria-flux-app-design-ptqbn5` · **Data**: 2026-07-05
**Stack**: Flux/FluxBuilder (UI mobile) + Firebase (Auth, Firestore, Functions, Storage, FCM, Hosting)

App mobile per LibreriBrà, libreria per bambini e ragazzi (San Giovanni, Roma): feed editoriale, eventi con prenotazione, catalogo con prenota-e-ritira, punti fedeltà, area giochi 6–12 anni ed "Estrazione dei Lettori" (lotteria a 90 numeri solo a punti).

---

## 1. Cosa è stato consegnato

### Design e documentazione (`docs/`)

| Documento | Contenuto |
|---|---|
| `piano-strategico.md` | Piano di business: target, KPI, MVP, roadmap, rischi |
| `01-requirements.md` | Requirement check, decisioni prese, registro rischi |
| `02-product-framing.md` | Target, proposta di valore, north star KPI |
| `03-architecture.md` | Architettura Flux+Firebase, ruoli, GDPR minori, scelta backend motivata |
| `04-information-architecture.md` | 5 tab, ~30 schermate, stati vuoti/errore, permessi, deep link |
| `05-functional-spec.md` | Specifica dei moduli A–H con stati, flussi e microcopy |
| `06-data-model.md` | 26+ entità Firestore con security rules e invarianti di dominio |
| `07-ux-design-system.md` | Palette "carta e bottega", typography, pattern bambini/adulti, WCAG AA |
| `08-analytics-growth.md` | Event tracking, funnel, retention loop, push strategy, CRM |
| `09-build-plan.md` | Sprint plan a 3 fasi con criteri di accettazione |
| `sprint-0-spike.md` | **Checklist go/no-go FluxBuilder + setup Firebase passo-passo** |
| `sprint-1-flux-screens.md` | Guida schermata-per-schermata per il builder |
| `catalogo-template.csv` | Template CSV per il censimento del catalogo |

### Backend (`firebase/functions/` — TypeScript, Node 20)

Tutta la logica di dominio vive nelle Cloud Functions: il client non scrive mai su wallet, ordini, prenotazioni, biglietti o log.

| Modulo | Funzioni principali |
|---|---|
| `loyalty.ts` | Core wallet event-sourced: transazioni atomiche, idempotenti per refId, con preCheck/extraWrites |
| `loyalty-ops.ts` | Accredito acquisti (staff+PIN, cap €300), coupon a soglie 200/500/900, scadenza punti FIFO 12 mesi, riconciliazione notturna |
| `wallet-code.ts` | QR wallet a rotazione: codici monouso 90s, anti-replay, pulizia notturna |
| `events.ts` | Prenotazioni con capienza atomica, lista d'attesa FIFO con promozione, check-in QR (+20 punti idempotenti), reminder T-24h/T-2h |
| `orders.ts` | Prenota-e-ritira: requested→ready→picked_up/expired, scadenza automatica 7 giorni |
| `catalog.ts` | Import CSV con validazione riga-per-riga, notifica riassortimento da wishlist |
| `feed.ts` | Moderazione commenti (filtro parole, anti-spam, 3-strikes→restrizione 30gg), contatori, segnalazioni (max 5/giorno) |
| `push.ts` | FCM su ogni notifica: transazionali sempre, promozionali con prefs + quiet hours 21–9 |
| `games.ts` | Sessioni gioco con anti-cheat (<30s = 0 punti) e cap famiglia 10 punti/giorno; token webview |
| `lottery.ts` | Round draft→open→closed→drawn/refunded, **gate legale** (non si apre senza regolamento), acquisto atomico, estrazione commit-reveal verificabile, rimborso sotto soglia |
| `admin-users.ts` | Promozione/revoca staff (solo admin, con audit) |
| `index.ts` | Bootstrap utenti (onUserCreate), onboarding con bonus benvenuto, preferenze, cancellazione account con cascata GDPR |

### Sicurezza (`firebase/firestore.rules`, `storage.rules`)

Default-deny totale. Punti chiave: profili figlio leggibili **solo dal genitore** (nemmeno dallo staff), creazione figlio impossibile senza consenso, collezioni economiche scrivibili solo dalle Functions, contatori protetti anche dallo staff, like con unicità strutturale, commenti che nascono solo `pending`.

### Backoffice web (`firebase/backoffice/` — Firebase Hosting)

Login staff con recupero password. Sei sezioni: **Feed** (post + coda moderazione), **Libri** (import CSV con report errori), **Eventi** (creazione, check-in, ordini), **Utenti** (ricerca, promozione staff), **Loyalty & Lotteria** (accredito con PIN, coupon, ciclo round con estrazione), **Report** (KPI in linguaggio da libraio).

### Webview giochi (`firebase/games-web/`)

Quiz del piccolo esploratore + puzzle drag-and-drop, in modalità bambino (touch ≥64pt, gate uscita 3s, niente timer 6–8, suggerimento libro finale). Funziona in demo aprendo `index.html` in un browser; nell'app si autentica via `mintGamesToken`.

### Strumenti

- `firebase/seed/seed.mjs` — dati di base (negozio, categorie, 5 giochi) ⚠️ indirizzo/orari placeholder
- `firebase/scripts/make-admin.mjs` — bootstrap del primo amministratore
- `.github/workflows/libreria-app-ci.yml` — CI: build + 68 test su ogni push

---

## 2. Qualità: come è stato verificato

| Suite | Test | Cosa dimostrano |
|---|---|---|
| Security rules (`firebase/tests/`) | 24 | Isolamento dati minori, collezioni economiche blindate, unicità like, commenti pending |
| Integrazione (`functions/test/`) | 44 | Concorrenza reale sull'emulatore: doppia prenotazione ultimo posto, doppia spesa oltre saldo, gara stesso numero lotteria, replay QR wallet — in ogni caso passa una sola operazione |
| End-to-end giochi | Chromium headless | Quiz completato, puzzle risolto con drag simulato dei 9 pezzi |

I test hanno trovato e fatto correggere **3 bug reali** prima della consegna: ordine letture/scritture nelle transazioni della lista d'attesa, estrazione lotteria che si fermava a 2 premi con pool piccoli, plurale sbagliato nel copy dei giochi.

Esecuzione locale (richiede Node 20 + Java 11+):

```bash
cd libreria-app/firebase
npm --prefix functions ci && npm --prefix functions run build
npm --prefix tests install
npx firebase-tools@13 emulators:exec --only firestore --project demo-libreribra \
  "npm --prefix tests test && npm --prefix functions test"
```

---

## 3. Cosa resta da fare (richiede i tuoi account)

In ordine:

1. **Progetti Firebase** (~30 min) — segui `docs/sprint-0-spike.md` §1: crea `libreribra-dev` e `libreribra-prod`, abilita Auth/Firestore/Storage/Blaze, `firebase deploy`, poi `node scripts/make-admin.mjs tua@email.it`.
2. **Spike FluxBuilder** — i 6 criteri go/no-go di `docs/sprint-0-spike.md` §2 su device reale. È la decisione tecnica più importante rimasta: se S0-6 fallisce, la webview giochi è già pronta come piano B.
3. **Enrollment store** — Apple Developer Program e Google Play Console: avviali subito, sono la coda più lunga.
4. **Consulente legale** ⚠️ — regolamento lotteria (DPR 430/2001) e informativa privacy/consenso minori (GDPR art. 8 + DPIA per l'area giochi). *Senza regolamento i round non si aprono: il blocco è nel codice.*
5. **Schermate FluxBuilder** — con `docs/sprint-1-flux-screens.md` alla mano, contro il backend già deployato.
6. **Dati reali** — sostituisci i placeholder di `seed/seed.mjs` (indirizzo, orari, contatti) e di `firebase-config.js` (backoffice e games-web); lo staff compila il CSV catalogo.
7. **Pilot** — 30–50 clienti reali; gate per la Fase 2: attivazione >50%, ≥30% con una prenotazione o redemption, crash-free >99%.

## 4. Decisioni chiave da ricordare

- **Feed**: pubblica solo la libreria; gli utenti interagiscono. Niente UGC.
- **Vendite**: prenota-e-ritira nell'MVP; Stripe in Fase 2 (Sprint 10).
- **Minori**: nessun account sotto i 14 anni, profili figlio con dati minimi e consenso, niente foto dei bambini.
- **Lotteria**: solo punti fedeltà, mai denaro; lessico non da azzardo ("Estrazione dei Lettori"); rilascio subordinato al parere legale — vincolo codificato, non solo dichiarato.
- **Punti**: 1/€, 20/evento, giochi cap 10/giorno/famiglia, scadenza 12 mesi FIFO, saldo sempre ricalcolabile dalle transazioni.
