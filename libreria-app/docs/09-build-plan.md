# TASK 9 — Build Plan

Sprint da 2 settimane, allineati alla roadmap del piano strategico (MVP validazione → growth engine → ecosistema). Lotteria e giochi avanzati **dopo** la validazione dell'MVP commerciale, come da piano. Team di riferimento: quello del piano strategico (product lead pt, mobile engineer ft, backend pt, designer pt, store ops interno).

## Fase 1 — MVP (Sprint 0–5, ~12 settimane)

### Sprint 0 — Discovery + spike tecnico (2 settimane)
- **Obiettivo**: de-rischiare FluxBuilder e allineare lo staff.
- **Scope**: spike walking-skeleton (FluxBuilder + Firebase Auth + una lista Firestore + una push FCM + una mappa); test componenti interattivi per capire se i giochi F1/F3 richiedono webview; co-design backoffice con il libraio; setup progetto Firebase (dev/prod), repo, CI.
- **Deliverable**: app scheletro funzionante su un device reale; decisione documentata `native vs webview` per i giochi; prototipo low-fi flussi chiave; progetto Firebase configurato con security rules default-deny.
- **Dipendenze**: account Firebase, licenza FluxBuilder, accesso store Apple/Google avviato (i tempi di review/enrollment partono ora).
- **Rischi**: FluxBuilder inadeguato → **stop-rule**: si riferisce al committente prima di proseguire.
- **Accettazione**: spike dimostrato end-to-end; go/no-go esplicito sullo stack.
- **Test**: smoke manuale su iOS + Android.

### Sprint 1 — Fondamenta: auth, profili, negozio (2 settimane)
- **Scope**: O1–O5 onboarding completo (con consenso figli), P1/P2/P8, S1 scheda negozio con mappa e Naviga, design system implementato (palette, type, componenti base), analytics core (`app_open`, `onboarding_completed`, `child_profile_created`).
- **Deliverable**: un utente si registra, crea profilo figlio con consenso, trova il negozio e naviga.
- **Dipendenze**: Sprint 0; testi informativa privacy (bozza legale).
- **Rischi**: consenso minori mal formulato → revisione testo con legale già in questo sprint.
- **Accettazione**: onboarding <2 min misurato; eliminazione account con cascata verificata; rules testate (un client non può leggere figli altrui).
- **Test**: unit rules (emulator), E2E onboarding, VoiceOver sul flusso registrazione.

### Sprint 2 — Catalogo e wishlist (2 settimane)
- **Scope**: L1–L3, L5 (saldi/novità), backoffice §Libri con import CSV, eventi analytics libro.
- **Deliverable**: catalogo reale caricato dallo staff (≥200 titoli), filtri età/tema funzionanti, wishlist.
- **Dipendenze**: CSV catalogo preparato dallo staff (inizia in Sprint 1).
- **Rischi**: dati catalogo sporchi → validatore import con report errori riga per riga.
- **Accettazione**: staff carica e corregge il catalogo **senza aiuto dello sviluppatore**.
- **Test**: import CSV con dataset volutamente sporco; query filtri su indici compositi.

### Sprint 3 — Eventi con prenotazione (2 settimane)
- **Scope**: E1–E5 completi (prenotazione transazionale, lista d'attesa, QR, cancellazione), backoffice §Eventi con check-in, reminder T-24h/T-2h, deep link `/evento/{id}`.
- **Deliverable**: ciclo evento completo: creazione → prenotazione → reminder → check-in.
- **Rischi**: overbooking → test di concorrenza sulla transazione `bookEvent`.
- **Accettazione**: 2 prenotazioni simultanee sull'ultimo posto: una sola passa, l'altra riceve la lista d'attesa; promozione waitlist verificata.
- **Test**: test concorrenza emulator; E2E prenotazione+check-in con QR reale.

### Sprint 4 — Loyalty + prenota-e-ritira (2 settimane)
- **Scope**: wallet event-sourced (`applyLoyaltyTransaction`, riconciliazione notturna), QR wallet + accredito staff con PIN, coupon a soglie, L4 + ciclo ordini completo con push, bonus benvenuto, check-in → +20 punti.
- **Deliverable**: il loop economico gira: acquisto/evento → punti → coupon → redemption.
- **Dipendenze**: Sprint 3 (check-in), processo di cassa concordato con staff.
- **Rischi**: doppio accredito → idempotenza per `ref_id`; riconciliazione segnala drift.
- **Accettazione**: saldo sempre = Σ transazioni sotto test di concorrenza; scadenze FIFO corrette su dataset simulato di 12 mesi.
- **Test**: property test sul wallet (sequenze casuali earning/redemption), E2E ordine completo.

### Sprint 5 — Feed + backoffice completo + hardening (2 settimane)
- **Scope**: H1/H2 feed con like/commenti/salvataggi, moderazione (filtro, report, coda staff), backoffice §Feed/§Utenti/§Report, centro notifiche in-app, hardening security rules (audit completo), preparazione store listing.
- **Deliverable**: MVP feature-complete, build in review Apple/Google.
- **Rischi**: review store lenta → account e metadati preparati dallo Sprint 0.
- **Accettazione**: penetration-check delle rules (script che tenta ogni scrittura vietata da client); checklist WCAG sui flussi core.
- **Test**: E2E completo dei 4 flussi chiave; test moderazione con corpus di commenti tossici.

### Sprint 6 — Pilot (2–4 settimane, come da piano strategico)
- **Scope**: soft launch con 30–50 clienti reali reclutati in negozio; QR in cassa; analisi funnel settimanale; fix UX; prime automazioni CRM (welcome, reminder, inattività).
- **Accettazione (gate per Fase 2)**: attivazione >50% (verso il target 60%), ≥30% dei pilot con almeno 1 prenotazione o redemption, crash-free >99%.

## Fase 2 — Growth engine (Sprint 7–10, ~8 settimane)

### Sprint 7 — Area giochi, parte 1
- **Scope**: modalità bambino (gate, selettore figlio), G1 hub, **F4 Quiz + F3 Puzzle** (i 2 giochi MVP decisi nel requirement check), cap punti famiglia, suggerimento libro post-sessione, produzione contenuti (120 domande + 30 immagini con `license_note`).
- **Accettazione**: sessione media 3–5 min; cap non aggirabile (test multi-profilo); zero eventi analytics che identifichino il minore. **DPIA completata prima del rilascio pubblico.**

### Sprint 8 — Area giochi, parte 2 + referral
- **Scope**: F1 Parole in fuga, F2 Il paroliere, F5 Trova le differenze; progressi e badge; referral loop completo con anti-abuso.
- **Accettazione**: funnel `game_completed → book_viewed` misurabile; referral pagato solo su conversione reale.

### Sprint 9 — Lotteria "Estrazione dei Lettori" ⚠️ gate legale
- **Pre-condizione bloccante**: parere legale ricevuto e regolamento approvato (`rules_url` disponibile). Il lavoro legale parte in Fase 1 per non bloccare qui. Senza parere → lo sprint slitta e si anticipa lo Sprint 10.
- **Scope**: P5 completa (griglia, acquisto transazionale, storico), round scheduler, commit-reveal, `drawLottery` con verbale, fallback rimborso, backoffice §Lotteria, push topic.
- **Accettazione**: test di concorrenza su stesso numero (uno solo vince); estrazione riproducibile da seed pubblicato; rimborso integrale verificato su round sotto soglia; staff/admin bloccati dall'acquisto.

### Sprint 10 — Pagamenti in-app + bundle
- **Scope**: Stripe (PaymentIntent via Functions) su eventi a pagamento e ordini; bundle libro+evento; ricevute.
- **Accettazione**: nessun dato carta nel client; rimborso su evento cancellato dallo store funzionante.

## Fase 3 — Ecosistema locale (Sprint 11+, dal piano strategico)

Prenotazioni gruppi/scuole, contenuti editoriali e guide di lettura, sezione "weekend in famiglia", integrazione Google Business Profile e campagne Meta, membership famiglia. Da ripianificare sui dati del pilot: non si specifica ora ciò che i dati possono smentire.

## Vista d'insieme dipendenze

```
S0 spike ──► S1 auth/profili ──► S2 catalogo ──► S4 loyalty+ordini ──► S5 feed+hardening ──► S6 PILOT
                              └─► S3 eventi ──┘                                              │ gate
Legale (parte in Fase 1) ────────────────────────────────► S9 lotteria                       ▼
DPIA minori ────────────────► S7 giochi 1 ──► S8 giochi 2        S7–S10 Fase 2 ──► Fase 3
```

---
**Riepilogo TASK 9**: 6 sprint di MVP (12 settimane) chiusi da un pilot con gate quantitativo, poi 4 sprint di growth con i giochi divisi in due tranche, la lotteria vincolata al parere legale (avviato in Fase 1 per non fare collo di bottiglia) e Stripe per ultimo. Ogni sprint ha criteri di accettazione verificabili, inclusi i test di concorrenza sui tre punti economici critici (prenotazioni, wallet, biglietti).
