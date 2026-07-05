# PROMPT PER CLAUDE CODE — App LibreriBrà (Flux/FluxBuilder + Database)

> Copia e incolla questo prompt in Claude Code dalla root del progetto.
> Versione 1.0 — 2026-07-05

---

## 🎯 OBIETTIVO (dichiarazione immediata)

Agisci come **senior product engineer + senior mobile architect + senior growth strategist**.
Progetta e sviluppa una **app mobile in Flux / FluxBuilder con database integrato** per **LibreriBrà**, libreria indipendente per bambini e ragazzi (San Giovanni, Roma).

L'app deve unire in un unico ecosistema:

1. **Feed social tipo Instagram** (post con foto, like, commenti, salvataggi) — reinterpretato, non copiato.
2. **Eventi** con creazione, calendario, prenotazione, capienza, reminder e check-in.
3. **Vendita libri**: catalogo, saldo libri, novità, wishlist, prenotazione ritiro in negozio.
4. **Scheda negozio**: foto, indirizzo, orari, giorni di apertura, **mappa con pulsante "Naviga"**.
5. **Profili utenti con foto**, figli associati, cronologia acquisti/eventi, punti.
6. **Area giochi bambini 6–12 anni**: 5 giochi (parole, italiano, puzzle di figure, quiz culturale, gioco visivo-culturale).
7. **Punti fedeltà** con earning, redemption, livelli e anti-abuso.
8. **Lotteria adulti a 90 numeri** acquistabili con punti fedeltà: 3 premi, data apertura, chiusura vendite ed estrazione.

Non è un catalogo passivo: è il **motore commerciale e relazionale** della libreria (più visite, più prenotazioni, più riacquisti, dati first-party).

---

## 📂 CONTEXT FILES (da leggere PRIMA di rispondere)

Prima di produrre qualsiasi output, **elenca esplicitamente i file che leggerai** con una riga di motivazione ciascuno, poi **leggili davvero uno per uno**:

| File | Perché serve |
|---|---|
| `libreria-app/docs/piano-strategico.md` | Piano di business completo: target, KPI, MVP, roadmap, rischi. È la fonte di verità strategica. |
| `README.md` | Panoramica dello stato attuale del repository. |
| `docs/product-vision.md` | Visione di prodotto e obiettivi (se esiste). |
| `docs/brand.md` | Tono, identità visiva, voce del brand (se esiste). |
| `docs/requirements.md` | Requisiti funzionali e non funzionali (se esiste). |
| `docs/business-rules.md` | Regole su punti, lotteria, eventi, ruoli (se esiste). |
| `docs/content-model.md` | Entità dati e relazioni previste (se esiste). |
| `docs/games-design.md` | Logica giochi educativi 6–12 anni (se esiste). |
| `docs/legal-and-policy.md` | Privacy, minori, lotteria, consenso genitori (se esiste). |
| `docs/flux-constraints.md` | Vincoli tecnici di Flux/FluxBuilder (se esiste). |
| `src/` o `app/` (albero completo) | Stato reale dell'implementazione: non assumere nulla. |
| `package.json` / config equivalenti | Dipendenze e toolchain reali. |
| `database/schema.*`, `supabase/`, `firebase/` | Schema dati e regole di sicurezza esistenti. |

**Se un file manca**: dichiaralo, produci la lista dei mancanti, proponi il contenuto minimo necessario e **non inventare dettagli nascosti**.

---

## 💬 CONVERSATION & PLAN

1. **TASK 0 — Domande prima di tutto.** Se i requisiti sono ambigui, **fermati prima di generare architettura definitiva** e fammi **massimo 10 domande ad alta priorità**, ordinate per impatto (business → legale → UX → fattibilità tecnica). Parti da queste, adattandole a ciò che trovi nei file:
   1. Gli utenti possono pubblicare post nel feed, o solo la libreria pubblica e gli utenti interagiscono (like/commenti/salvataggi)?
   2. La vendita libri avviene con pagamento in-app (Stripe/PayPal) o solo prenotazione con ritiro e pagamento in negozio?
   3. Esiste già un gestionale/inventario di cassa da cui leggere disponibilità e prezzi, o il catalogo si gestisce a mano dal backoffice?
   4. Come si accreditano i punti per gli acquisti in negozio fisico: QR sullo scontrino, codice cassa, inserimento manuale dello staff?
   5. Lotteria: la normativa italiana sulle manifestazioni a premio è stringente. Confermi che sarà validata con un consulente legale, e nel frattempo procediamo con un "concorso a punti" senza valore monetario dei biglietti?
   6. I bambini 6–12 usano l'app dal telefono del genitore (profilo figlio dentro l'account genitore) o hanno un accesso proprio?
   7. Quali sono i 3 premi della lotteria e le date tipo di apertura/chiusura/estrazione (es. round mensile)?
   8. Regole punti: quanti punti per € speso, per evento frequentato, per gioco completato? Scadenza punti?
   9. Backend: hai già un account Firebase o Supabase, o scelgo io e lo configuro da zero?
   10. Lingua solo italiana o serve predisposizione multilingua?
2. **Poi entra in plan mode**: presenta il piano completo (architettura, data model, sprint) e **attendi la mia approvazione prima di scrivere codice**.
3. Procedi per **task nell'ordine indicato sotto**, un task alla volta, chiudendo ogni task con un riepilogo di 5 righe massimo e la lista dei file toccati.

---

## 🛠 TASK (ordine rigoroso, non saltare passaggi)

### TASK 1 — Requirement check
Analizza i requisiti contro i context files. Segnala gap, contraddizioni e rischi. Output: tabella `requisito → stato → rischio → decisione necessaria`.

### TASK 2 — Product framing
Target primario/secondario, obiettivi di business e utente, proposta di valore, metriche di successo, rischi e mitigazioni. Riusa i KPI del piano strategico (attivazione >60%, retention 30gg 20–25%, ecc.), non inventarne di nuovi.

### TASK 3 — App architecture
Architettura completa e pragmatica: Flux/FluxBuilder come layer UI + backend reale. **Scegli tra Firebase e Supabase e giustifica la scelta** per questo caso specifico. Includi: autenticazione, ruoli (admin/staff/genitore/figlio), database, storage immagini, push, analytics, content moderation, sicurezza dati, **gestione minori e consenso genitori (GDPR art. 8)**, backoffice, scalabilità.

### TASK 4 — Information architecture
App map completa: tab principali (proposta: Home/Feed, Libri, Eventi, Giochi, Profilo), tutte le schermate, flussi, stati vuoti, stati errore, permessi per ruolo, deep link.

### TASK 5 — Functional specification
Specifica dettagliata dei moduli A–H:

- **A. Feed social**: post foto+caption, like, commenti, salvataggi; contenuti libreria vs contenuti utente (se ammessi); moderazione pre-pubblicazione per UGC; regole anti-spam; differenza profilo libreria (verificato, può creare eventi) vs profili utente.
- **B. Eventi**: creazione (solo admin/staff), calendario, scheda evento, prenotazione con capienza, lista d'attesa, cancellazione, reminder push, check-in QR, storico partecipazioni, eventi gratuiti e a pagamento.
- **C. Vendita libri**: catalogo con tag età/tema, scheda libro con "consiglio del libraio", disponibilità, **sezione Saldi** e Novità, wishlist, prenotazione ritiro in negozio, ordini, coupon, bundle libro+evento.
- **D. Scheda negozio**: galleria foto, indirizzo, orari e giorni di apertura, mappa integrata con pulsante naviga (deep link Google/Apple Maps), contatti, info trasporti/parcheggio.
- **E. Profili utenti**: foto profilo, dati base, **profili figli associati** (nome/età/interessi, niente foto del minore pubblica di default), preferenze, cronologia acquisti/eventi, saldo punti, coupon, privacy e cancellazione account.
- **F. Area giochi 6–12 anni** — 5 giochi obbligatori:
  1. **Gioco di parole** (es. anagrammi/parole nascoste da titoli di libri).
  2. **Conoscenza dell'italiano** (grammatica/vocabolario a livelli).
  3. **Figure da mettere insieme** (puzzle con copertine e illustrazioni).
  4. **Quiz culturale** (storie, geografia, scienza per fasce 6–8 / 9–12).
  5. **Gioco visivo-culturale** (es. "trova le differenze" su tavole illustrate o memory di personaggi letterari).
  Per ogni gioco: obiettivo educativo, età, meccanica, durata media (target 3–5 min), difficoltà progressiva, reward in punti **con cap giornaliero anti-abuso**, UI mobile, contenuti iniziali (min. 30 item/gioco), anti-frustrazione (niente "game over" punitivi), parental safety (no chat, no link esterni), e **collegamento ai libri della libreria** (ogni gioco suggerisce un libro correlato).
- **G. Punti fedeltà**: earning (acquisti, eventi, giochi con cap, referral), redemption (coupon, biglietti lotteria), livelli, scadenza, anti-abuso (rate limit, validazione staff per acquisti fisici), storico transazioni immutabile.
- **H. Lotteria adulti 90 numeri**: round con `apertura → chiusura vendite → estrazione`; acquisto numeri 1–90 con punti (solo utenti 18+ verificati); un numero = un solo proprietario per round (transazione atomica anti-duplicazione); 3 premi (1°, 2°, 3° estratto); estrazione trasparente e riproducibile (seed pubblicato + audit log); storico partecipazioni; fallback se non si raggiunge la soglia minima (rimborso punti o proroga). **⚠️ Segnala esplicitamente il rischio normativo italiano (DPR 430/2001 manifestazioni a premio) e proponi la variante compliant** (premi come operazione a premio legata alla fedeltà, nessun acquisto in denaro dei biglietti) da validare con un legale prima del rilascio.

### TASK 6 — Data model
Schema completo per: `users`, `profiles`, `child_profiles`, `posts`, `comments`, `likes`, `saves`, `books`, `inventory`, `discounts`, `orders`, `event_categories`, `events`, `bookings`, `store_locations`, `games`, `game_sessions`, `quiz_questions`, `puzzle_assets`, `loyalty_wallet`, `loyalty_transactions`, `lottery_rounds`, `lottery_tickets`, `lottery_prizes`, `notifications`, `reports`, `admin_logs`.
Per ogni entità: campi con tipo, relazioni, indici, vincoli, regole di sicurezza (RLS/security rules), ed eventi che la aggiornano.

### TASK 7 — UX & design system
Stile **editoriale, caldo, educativo, contemporaneo** — non tech-freddo, non template generico, non clone di Instagram. Palette, typography, componenti, card system, tab bar, onboarding, empty/error states, microcopy in italiano caldo ma non infantile per gli adulti, **pattern separati bambini (touch grandi, feedback sonoro opzionale, zero testo denso) vs adulti**, accessibilità WCAG AA.

### TASK 8 — Analytics & growth
Event tracking schema (riusa i nomi del piano: `app_open`, `onboarding_completed`, `event_booked`, `coupon_redeemed`… ed estendi con `game_completed`, `lottery_ticket_purchased`, ecc.), north star KPI, funnel (onboarding, eventi, acquisto, giochi, loyalty, lotteria), cohort, retention loops, push strategy, CRM triggers, referral loop.

### TASK 9 — Build plan
Sprint da 2 settimane, allineati alla roadmap del piano strategico (MVP → growth → ecosistema). Per ogni sprint: obiettivo, scope, deliverable, dipendenze, rischi, criteri di accettazione, test richiesti. La lotteria e i giochi avanzati vanno **dopo** la validazione dell'MVP commerciale.

### TASK 10 — Code generation discipline
Solo dopo approvazione del piano. Vedi RULES.

---

## 📚 REFERENCE

- **FluxBuilder**: builder per app mobili con integrazione dati/API, configurazione schermate, autenticazione, push e layout personalizzabili. L'app è il layer UI; la logica di business vive nel backend.
- **Piano strategico** (`libreria-app/docs/piano-strategico.md`): MVP stretto (scoprire → prenotare → tornare), pilot con 30–50 clienti reali, analytics prima delle feature, backoffice semplice per lo staff.
- **Canale proprietario**: l'app riduce la dipendenza dall'algoritmo di Instagram e costruisce retention e dati first-party.
- **Giochi educativi**: chiarezza, reward non tossici (niente loop compulsivi, niente acquisti in-app per bambini), progressione semplice, valore didattico, sicurezza minori.
- **Posizionamento reale**: bambini, ragazzi, laboratori, eventi — prodotto e tono devono restare coerenti con questo.

---

## ✅ SUCCESS BRIEF (esempio di risultato atteso + regole reverse-engineered)

### Esempio: come deve apparire una specifica di modulo ben fatta

```markdown
## Modulo H — Lotteria a 90 numeri

**Stato round**: draft → open → closed → drawn → archived

**Flusso utente (adulto 18+)**
1. Wallet → tab "Lotteria" → round attivo con countdown chiusura.
2. Griglia 90 numeri: verdi = liberi, grigi = venduti, blu = miei.
3. Tap su numero libero → sheet conferma: "Numero 42 — 150 punti. Confermi?"
4. Transazione atomica: se il numero è stato preso nel frattempo → errore
   gentile "Il 42 è appena stato preso! Il 43 è libero 😉" + refresh griglia.
5. Ricevuta nel wallet + push a chiusura vendite e a estrazione avvenuta.

**Regole di dominio**
- `lottery_tickets`: UNIQUE (round_id, number) — vincolo a livello DB, non solo client.
- Acquisto = una transazione: debito punti + insert ticket, o rollback totale.
- Estrazione: seed = hash pubblicato prima della chiusura + verbale in admin_logs.
- Fallback soglia minima non raggiunta: rimborso automatico punti, push di scuse.

**⚠️ Compliance**: configurazione da validare con consulente legale
(DPR 430/2001). Variante sicura: nessun acquisto in denaro, solo punti
fedeltà già maturati → operazione a premio legata al programma loyalty.
```

### Regole estratte da questo esempio (applicale a TUTTO l'output)

1. **Ogni modulo ha stati espliciti** e un flusso utente numerato passo-passo.
2. **I vincoli critici vivono nel database**, mai solo nel client.
3. **Ogni errore ha una UX definita**, con microcopy reale in italiano, non "mostra errore".
4. **I rischi legali sono segnalati inline** con la variante compliant, non nascosti in fondo.
5. **Concretezza sopra astrazione**: numeri, soglie, esempi di copy — mai "il sistema gestirà opportunamente".

### Output, lunghezza, effetto desiderato

- **Formato**: file markdown in `libreria-app/docs/` (uno per task: `01-requirements.md`, `02-product-framing.md`, … `09-build-plan.md`), più codice solo nel TASK 10.
- **Lunghezza**: ogni documento 300–800 righe, denso e azionabile. Niente riempitivi, niente ripetizione del prompt.
- **Effetto**: un developer o un PM deve poter prendere ogni documento e **lavorare senza farti altre domande**. Il cliente (libraio non tecnico) deve capire le sezioni di business leggendole una volta sola.

---

## 🚫 RULES (vincoli assoluti)

### Cosa EVITARE
- ❌ **Non deve sembrare IA generica**: niente frasi vuote ("app moderna e intuitiva"), niente elenchi di ovvietà, niente lorem ipsum concettuale.
- ❌ Non un clone di Instagram: il feed è una reinterpretazione editoriale per famiglie.
- ❌ Niente dark pattern, loop compulsivi o meccaniche da casinò nei giochi per bambini.
- ❌ Niente foto di minori pubbliche di default; nessun dato del bambino oltre il necessario.
- ❌ Non banalizzare la lotteria: mai presentarla come "gioco d'azzardo light".
- ❌ Niente feature inventate non richieste (chat realtime, marketplace multi-vendor, AI custom).

### Blocco esecuzione (stop obbligatori)
**FERMATI e chiedi conferma** prima di procedere se stai per:
- generare l'architettura definitiva con requisiti ancora ambigui;
- implementare la lotteria senza che il rischio normativo sia stato accettato esplicitamente;
- gestire dati di minori senza il flusso di consenso genitori definito;
- scegliere lo stack backend se nei file esiste già una scelta diversa (mismatch stack reale vs proposta);
- sovrascrivere file esistenti che non hai letto.

### Disciplina di generazione codice (salva-token)
- **Blocchi piccoli**: mai più di 1–2 file per risposta; attendi conferma prima del blocco successivo.
- **Leggi prima di scrivere**: verifica sempre i file esistenti prima di crearne o modificarne.
- **Non assumere librerie non installate**: controlla `package.json`/config prima di ogni import.
- **Non generare decine di file speculativi**: solo ciò che serve al task corrente.
- Se il codice non compila o c'è un mismatch con lo stack reale: **fermati, diagnostica, non rigenerare a raffica**.
- Chiudi ogni blocco con: cosa hai fatto (max 3 righe), file toccati, prossimo passo proposto.
