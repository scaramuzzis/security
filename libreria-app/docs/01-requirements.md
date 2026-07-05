# TASK 1 — Requirement Check

Analisi dei requisiti contro i context files disponibili. Data: 2026-07-05.

## Stato dei context files

| File previsto | Stato | Azione |
|---|---|---|
| `libreria-app/docs/piano-strategico.md` | ✅ Esiste, letto | Fonte di verità per target, KPI, MVP, roadmap |
| `README.md` (root repo) | ✅ Esiste, letto | ⚠️ Descrive un progetto diverso (AI Security Scanner). L'app libreria vive isolata in `libreria-app/` |
| `docs/product-vision.md` | ❌ Mancante | Coperto da `02-product-framing.md` |
| `docs/brand.md` | ❌ Mancante | Coperto dalla sezione brand di `07-ux-design-system.md` |
| `docs/requirements.md` | ❌ Mancante | Questo documento |
| `docs/business-rules.md` | ❌ Mancante | Regole punti/lotteria/eventi in `05-functional-spec.md` §G-H |
| `docs/content-model.md` | ❌ Mancante | Coperto da `06-data-model.md` |
| `docs/games-design.md` | ❌ Mancante | Coperto da `05-functional-spec.md` §F |
| `docs/legal-and-policy.md` | ❌ Mancante | Requisiti legali inline nei moduli + registro rischi sotto |
| `docs/flux-constraints.md` | ❌ Mancante | Vincoli FluxBuilder documentati in `03-architecture.md` |
| `docs/analytics-plan.md` | ❌ Mancante | Coperto da `08-analytics-growth.md` |
| `docs/firebase-or-supabase-plan.md` | ❌ Mancante | Scelta Firebase motivata in `03-architecture.md` |
| `src/`, `package.json`, schema DB | ❌ Nessun codice app esistente | Progetto greenfield: nessun vincolo di stack ereditato |

## Decisioni prese con il committente (2026-07-05)

| # | Domanda | Decisione |
|---|---|---|
| 1 | Chi pubblica nel feed? | **Solo la libreria.** Gli utenti mettono like, commentano, salvano. Niente UGC nell'MVP. |
| 2 | Pagamenti libri | **Entrambi**: prenota-e-ritira (MVP, default) + pagamento in-app Stripe (Fase 2). |
| 3 | Lotteria e normativa | **Variante compliant confermata**: numeri acquistabili solo con punti fedeltà, mai denaro. Rilascio condizionato a parere legale (DPR 430/2001). |
| 4 | Backend | **Firebase** (Auth, Firestore, Storage, FCM, Functions, Analytics). |

## Default adottati (modificabili, nessuna risposta esplicita)

| # | Tema | Default |
|---|---|---|
| 5 | Accesso bambini | Profili figlio dentro l'account genitore; uso dal telefono del genitore; nessun account autonomo <14 anni (soglia consenso digitale italiana, D.Lgs. 101/2018). |
| 6 | Regole punti | 1 punto/€ speso · 20 punti/evento con check-in · giochi max 10 punti/giorno per famiglia · scadenza 12 mesi rolling. |
| 7 | Lotteria round tipo | Mensile: apertura giorno 1, chiusura vendite giorno 25, estrazione ultimo sabato del mese in libreria. Premi placeholder: 1° buono €50, 2° buono €25, 3° libro ≤€15. |
| 8 | Catalogo | Gestione manuale da backoffice + import CSV. Integrazione gestionale cassa in Fase 2. |
| 9 | Accredito punti in cassa | QR personale nel wallet mostrato in cassa, validazione staff con PIN. |
| 10 | Lingua | Solo italiano; stringhe centralizzate per futura i18n. |

## Matrice requisiti

Legenda stato: ✅ chiaro · 🟡 chiaro con vincoli · 🔴 bloccato

| Requisito | Stato | Rischio | Decisione necessaria |
|---|---|---|---|
| Feed social tipo Instagram | ✅ Solo libreria pubblica | Basso: engagement dipende dalla costanza editoriale dello staff | Piano editoriale minimo 3 post/settimana (operativo, non tecnico) |
| Commenti utenti sui post | 🟡 | Medio: commenti = UGC, serve moderazione e segnalazione | Filtro parole vietate + report + soft-delete staff (definito in §A spec) |
| Eventi con prenotazione | ✅ | Basso | — |
| Eventi a pagamento | 🟡 | Medio: pagamento evento richiede Stripe → Fase 2; MVP = prenotazione con pagamento in cassa | Nessuna: phasing deciso |
| Catalogo + saldi + novità | ✅ | Medio: dati disponibilità aggiornati a mano rischiano di invecchiare | Processo staff: aggiornamento disponibilità = parte del flusso di cassa |
| Pagamento in-app | 🟡 Fase 2 | Alto se anticipato: PCI, commissioni, fiscalità e-commerce | Confermato rinvio a Fase 2 |
| Scheda negozio + mappa | ✅ | Basso | — |
| Profili con foto | ✅ | Basso (foto profilo = solo adulti) | — |
| Profili figli | 🟡 | Alto se maldisegnato: dati minori | Solo nome/anno di nascita/interessi; niente foto minore; consenso genitore esplicito in onboarding |
| 5 giochi 6–12 anni | ✅ | Medio: produzione contenuti (min 30 item/gioco) è lavoro editoriale reale | Lo staff valida i contenuti prima del rilascio |
| Punti fedeltà | ✅ | Medio: anti-abuso (self-scan, doppio accredito) | Regole anti-abuso in §G; accredito acquisti solo via staff |
| Lotteria 90 numeri | 🔴→🟡 | **Alto: normativo.** DPR 430/2001: le lotterie con vendita biglietti sono riservate a enti no-profit; per un esercizio commerciale la via lecita è l'operazione/concorso a premio legato alla fedeltà | Sbloccato con variante solo-punti; **il rilascio resta bloccato finché un consulente legale non valida il regolamento** |
| Backoffice | ✅ | Medio: se troppo complesso lo staff non lo usa | UI admin ridotta a 6 sezioni (vedi §03) |
| Notifiche push | ✅ | Medio: spam percepito → disinstallazioni | Max 2 push/settimana per utente non transazionali (regola in §08) |

## Contraddizioni rilevate e risolte

1. **Piano strategico vs richiesta feed**: il piano sconsiglia "social feed interno stile community completa" nell'MVP; la richiesta lo vuole. **Risoluzione**: feed solo-libreria = vetrina editoriale con interazioni, non community UGC. Rispetta entrambi.
2. **Piano strategico vs giochi**: il piano sconsiglia "gamification troppo articolata" nell'MVP. **Risoluzione**: nell'MVP entrano 2 giochi su 5 (quiz culturale + puzzle); gli altri 3 in Fase 2 (vedi build plan).
3. **Lotteria vs MVP stretto**: la lotteria è Fase 2/3, dopo validazione loyalty e parere legale. Il data model però la prevede da subito per evitare migrazioni.

## Registro rischi (top 5)

| Rischio | Impatto | Probabilità | Mitigazione |
|---|---|---|---|
| Lotteria non conforme DPR 430/2001 | Sanzioni | Media | Solo punti, regolamento scritto, parere legale bloccante prima del rilascio |
| Dati minori trattati male (GDPR art. 8) | Sanzioni + reputazione | Media | Dati minimi, consenso genitore, niente foto minori, DPIA prima del rilascio giochi |
| Catalogo/disponibilità non aggiornati | Sfiducia utenti | Alta | Processo staff obbligatorio + badge "verificato il {data}" sulla scheda libro |
| Push percepite come spam | Disinstallazioni | Media | Cap frequenza, segmentazione, opt-in granulare per categoria |
| Backoffice inutilizzato dallo staff | App vetrina morta | Media | Co-design con staff nello Sprint 0, max 6 sezioni, test con il libraio |

---
**Riepilogo TASK 1**: context files quasi tutti mancanti → prodotti come deliverable dei task successivi. 4 decisioni chiave prese dal committente, 6 default documentati. Un solo blocco reale (lotteria) risolto con variante compliant + gate legale sul rilascio. Nessun vincolo tecnico ereditato: greenfield in `libreria-app/`.
