# Sprint 0 — Spike tecnico e checklist go/no-go

Obiettivo: **de-rischiare FluxBuilder prima di investire negli sprint 1–5.**
Cosa è già pronto nel repo: config Firebase (`libreria-app/firebase/`), rules default-deny, indici, Functions compilanti (`onUserCreate`, `healthCheck`), CI.
Cosa richiede il tuo account (da fare tu o con me in sessione): creazione progetti Firebase, licenza FluxBuilder, test su device reale.

## 1. Setup progetti Firebase (≈30 min, una volta sola)

```bash
# Prerequisiti: Node 20+, poi:
npm install -g firebase-tools
firebase login

# Crea DUE progetti dalla console https://console.firebase.google.com:
#   libreribra-dev   (sviluppo/test)
#   libreribra-prod  (produzione — non toccarlo fino al pilot)
# Se i nomi sono occupati, aggiorna .firebaserc con gli ID reali.

cd libreria-app/firebase
firebase use dev

# Nella console, per il progetto dev abilita:
#   Authentication → Sign-in: Email/Password (+ Email link), Google, Apple
#   Firestore (location: europe-west1 o eur3)
#   Storage
#   Piano Blaze (necessario per Cloud Functions; il free tier resta ampio)

# Deploy di rules, indici e functions:
firebase deploy --only firestore:rules,firestore:indexes,storage
cd functions && npm ci && cd ..
firebase deploy --only functions
```

Verifica locale senza toccare il cloud (emulatori, serve Java 11+):

```bash
cd libreria-app/firebase
firebase emulators:start
# UI su http://localhost:4000 — crea un utente in Auth e verifica che
# onUserCreate generi users/{uid} e loyalty_wallet/{uid}
```

## 2. Spike FluxBuilder — criteri go/no-go

Costruire nel builder una mini-app di 4 schermate collegata a `libreribra-dev`. Ogni criterio si valuta su **device reale** (un iPhone e un Android), non solo in preview.

| # | Criterio | Come si verifica | Esito |
|---|---|---|---|
| S0-1 | Auth + Functions | Login Google/email nel builder → chiamata alla callable `healthCheck` → risposta con `authenticated: true`, `role: "parent"`, `userDocExists: true` | ☐ |
| S0-2 | Lista Firestore | Schermata che legge una collezione `books` (inserire 5 doc a mano nella console) con filtro per campo | ☐ |
| S0-3 | Push FCM | Invio push di test dalla console → ricezione con app in background + tap che apre una schermata specifica (deep link) | ☐ |
| S0-4 | Mappa + naviga | Schermata con mappa statica del negozio e bottone che apre Google/Apple Maps | ☐ |
| S0-5 | Upload immagine | Foto profilo caricata su Storage in `/users/{uid}/` (rispettando le rules: riprovare su path altrui deve FALLIRE) | ☐ |
| S0-6 | Componenti interattivi | Drag & drop o equivalente per i giochi F1/F3: il builder ha componenti adeguati? | ☐ |

**Regole di decisione**
- S0-1…S0-4 tutti ✅ → **GO**: FluxBuilder confermato come layer UI, si parte con lo Sprint 1.
- S0-5 ❌ → workaround accettabile (upload via callable), non blocca.
- S0-6 ❌ → **non blocca**: si attiva il piano B già previsto (giochi in webview HTML5 self-hosted, sotto).
- S0-1 o S0-2 ❌ dopo 3 giorni di tentativi → **NO-GO: fermarsi e riferire al committente** (stop-rule del piano). Alternativa da valutare insieme: FlutterFlow o Flutter nativo, stessa architettura Firebase (i documenti 01–09 e tutto `firebase/` restano validi al 100%).

## 3. Decisione native vs webview per i giochi (S0-6)

| Fattore | Nativo nel builder | Webview HTML5 |
|---|---|---|
| Drag & drop fluido (F1 tessere, F3 puzzle) | Dipende dai componenti del builder | Garantito (canvas/JS) |
| Quiz e trova-differenze (F2, F4, F5) | Quasi certamente fattibile | Fattibile |
| Offline | Migliore | Richiede cache manifest |
| Manutenzione | Nel builder | Codice separato, riusabile fuori dal builder |
| Auth | Nativa | Token Firebase passato alla webview, risultati via callable `completeGameSession` |

**Default se S0-6 fallisce**: F2/F4/F5 nativi nel builder, F1/F3 in webview. La decisione va scritta qui sotto a spike concluso:

> **Decisione (da compilare):** ______ · Data: ______ · Motivazione: ______

## 4. Altre attività Sprint 0 (fuori repo, da calendario)

- [ ] Avviare **oggi** l'iscrizione Apple Developer Program e Google Play Console (i tempi di enrollment sono i più lunghi di tutto lo sprint).
- [ ] Contattare il **consulente legale** per lotteria (DPR 430/2001) e informativa privacy/consenso minori — serve pronto per lo Sprint 9 e per i testi dello Sprint 1.
- [ ] Sessione di co-design del backoffice con lo staff della libreria (1 ora: mostrare le 6 sezioni del §03 e farsi dire cosa manca dal processo di cassa reale).
- [ ] Lo staff inizia il CSV catalogo (colonne: isbn, titolo, autore, editore, prezzo, fasce_età, temi, disponibilità, consiglio_libraio) — serve completo per lo Sprint 2.

## 5. Definition of done dello Sprint 0

1. Progetti `libreribra-dev` e `libreribra-prod` esistenti; rules/indici/functions deployate su dev.
2. Tabella go/no-go compilata con esito su device reali.
3. Decisione native/webview scritta nel §3.
4. Enrollment store avviato; legale contattato; CSV catalogo in lavorazione.
5. In caso di NO-GO: sessione di decisione col committente prima di qualsiasi Sprint 1.
