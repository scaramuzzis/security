# TASK 5 — Functional Specification (Moduli A–H)

Regole applicate a ogni modulo (dal Success Brief): stati espliciti, flusso numerato, vincoli nel database, errori con microcopy reale, rischi legali inline.

---

## A. Feed social (editoriale, non UGC)

**Decisione**: pubblica solo la libreria (staff/admin). Gli utenti interagiscono: like, commenti, salvataggi.

**Stati post**: `draft → published → archived` (+ `hidden` per rimozione rapida senza cancellare).

**Flusso pubblicazione (staff, da backoffice o app)**
1. Nuovo post → 1–5 foto (compressione client, max 5 MB/foto), caption max 2.200 caratteri, tag opzionali (evento collegato, libro collegato, fascia età).
2. Anteprima → Pubblica ora o programma data/ora.
3. Post pubblicato → opzionale push al topic `nuovi_post` (mai automatica: checkbox esplicita, per rispettare il cap 2 push/settimana).

**Flusso interazione (utente)**
1. Feed in ordine cronologico inverso (niente algoritmo: è una libreria, non un'ad platform).
2. Doppio tap o cuore = like (toggle). Contatore visibile.
3. Commento: max 500 caratteri, testo semplice, niente immagini né link cliccabili.
4. Salva → raccolta privata "Salvati" nel profilo.
5. Se il post ha un libro/evento collegato: card inline "📖 Ne parliamo qui" → L2/E2.

**Moderazione commenti**
- Filtro sincrono (Cloud Function `onCommentCreate`): lista parole vietate IT + heuristics (URL → blocco, >5 commenti/10 min per utente → blocco temporaneo con messaggio "Ci stai scrivendo tantissimo! Riprova tra qualche minuto 🙂").
- Commento sospetto → stato `pending`, visibile solo all'autore con etichetta "In revisione".
- "Segnala" su ogni commento → documento in `reports` → coda backoffice.
- Staff: nasconde (soft-delete, motivo obbligatorio, riga in `admin_logs`); 3 commenti nascosti → utente in `restricted` (commenti sempre `pending` per 30 giorni).

**Profilo libreria vs profili utente**: il profilo libreria è marcato "Libreria ✔", è l'unico che pubblica post e crea eventi; i profili utente sono visibili solo come autori di commenti (foto + nome), non hanno pagina pubblica navigabile — riduce superficie social e rischi privacy.

**Vincoli DB**: `likes` con ID composito `{postId}_{uid}` (un like per utente per post, idempotente). Contatori denormalizzati aggiornati solo da Function.

---

## B. Eventi

**Stati evento**: `draft → published → sold_out → completed → cancelled`.
**Stati prenotazione**: `confirmed → checked_in | cancelled_by_user | cancelled_by_store | no_show` (+ `waitlisted → confirmed` se si libera un posto).

**Flusso creazione (staff)**
1. Titolo, descrizione, foto, categoria (lettura animata, laboratorio, incontro autore…), fascia età consigliata, data/ora, durata, capienza, prezzo (0 = gratuito), soglia lista d'attesa.
2. Pubblica → appare in E1 e come card in Home; opzionale push al segmento per fascia età interessata.

**Flusso prenotazione (genitore)**
1. E2 → "Prenota" → seleziona n. posti (max 4) e per quale figlio (opzionale, migliora i dati).
2. Se evento a pagamento: **MVP** = si paga in cassa ("Prenoti ora, paghi in libreria"); **Fase 2** = Stripe in-app.
3. Conferma atomica (Function `bookEvent` in transazione: `booked_count + n ≤ capacity`, altrimenti errore).
4. Conferma → QR prenotazione + voce in E4 + evento in calendario di sistema (opt-in).
5. Push reminder T-24h e T-2h ("Ci vediamo domani alle 17:00 per {titolo}! 🎨").
6. In libreria: staff scansiona QR → `checked_in` → +20 punti automatici al wallet.

**Capienza piena**: "I posti sono finiti 😔 Vuoi metterti in lista d'attesa? Ti avvisiamo subito se si libera un posto." → `waitlisted` in ordine FIFO; se una prenotazione si cancella, la Function promuove il primo in lista → push con conferma entro 6h, altrimenti passa al successivo.

**Cancellazione utente**: consentita fino a 6h prima; oltre, invito a chiamare il negozio. `no_show` tracciato (2 no-show in 60 giorni → warning gentile, 3 → prenotazioni con conferma manuale staff).

**Vincoli DB**: `bookings` unico per `{eventId}_{uid}`; `booked_count` aggiornato solo dalla Function in transazione.

---

## C. Vendita libri

**Stati ordine (prenotazione ritiro)**: `requested → ready_for_pickup → picked_up | expired | cancelled`.

**Catalogo**
- Filtri combinabili: fascia età (0–2, 3–5, 6–8, 9–12, 13+, adulti), tema (avventura, emozioni, scienza…), 🏷 Saldo, ✨ Novità, disponibilità.
- Scheda libro (L2): copertina, titolo, autore, editore, prezzo (+ prezzo barrato se in saldo), fascia età, **"Il consiglio di LibreriBrà"** (campo curato, la vera differenziazione), disponibilità con "verificato il {data}", cuore wishlist, "Prenota il ritiro".

**Flusso prenotazione ritiro (MVP)**
1. L2 → "Prenota il ritiro" → conferma ("Te lo teniamo da parte per 7 giorni").
2. Function crea ordine `requested`, decrementa `reserved_count` in transazione (se disponibile, altrimenti: "È appena finito! Vuoi che ti avvisiamo quando torna?" → notifica restock da wishlist).
3. Staff conferma preparazione → `ready_for_pickup` → push "📚 {titolo} ti aspetta in libreria fino al {data}".
4. Ritiro e pagamento in cassa → staff segna `picked_up` → accredito punti (1 punto/€) via QR wallet.
5. Non ritirato entro 7 giorni → `expired` automatico (scheduled function) + push gentile.

**Fase 2 — pagamento in-app**: checkout Stripe (PaymentIntent via Function), stesso ciclo di vita con `paid` prima di `ready_for_pickup`; niente spedizioni nell'orizzonte attuale (drive-to-store).

**Coupon**: codici generati dal loyalty (§G) o da campagne; validazione in cassa da staff (marca `redeemed` con PIN). Tipi: sconto %, sconto €, omaggio.

**Bundle libro+evento**: scheda evento può referenziare un libro con prezzo bundle ("Laboratorio + albo illustrato a €18"); prenotando l'evento l'utente opziona anche il libro (un solo flusso).

**Saldi**: flag `on_sale` + `sale_price` sul libro; sezione L5 filtrata; push saldi max 1/settimana al topic `promozioni` (opt-in).

---

## D. Scheda negozio

Dati statici in `store_locations` (già multi-store ready):
- Galleria foto (interni, vetrina, angolo bambini).
- Indirizzo completo + coordinate.
- Mappa statica embedded; **"Naviga"** = deep link `comgooglemaps://` / `maps://` (Apple) con fallback web — nessuna API key mappe necessaria nell'MVP.
- Orari per giorno della settimana + eccezioni (festivi, chiusure straordinarie) con banner "Oggi siamo aperti fino alle 19:30" calcolato client-side.
- Contatti: telefono (tap-to-call), email, WhatsApp, Instagram.
- "Come arrivare": metro/bus/parcheggio (testo curato).

---

## E. Profili utenti

**Genitore**: foto profilo (opzionale), nome, email, data di nascita (serve per gate 18+ lotteria), quartiere (opzionale, per segmentazione locale), preferenze notifiche per categoria, cronologia acquisti/eventi, saldo punti e QR wallet personale, coupon.

**Profili figlio** (max 6 per account):
- Campi: nome o soprannome, **anno** di nascita (non data completa: minimizzazione), interessi (checkbox).
- **Niente foto del minore.** Avatar da set illustrato (animali lettori — coerente col brand).
- Creazione richiede consenso esplicito: "Confermo di essere il genitore/tutore e acconsento al trattamento di questi dati per personalizzare l'esperienza. [Informativa]".
- Il profilo figlio abilita: filtro catalogo per età, prenotazioni eventi nominali, area giochi.

**Privacy e account**
- P8: consensi granulari (push per categoria, email marketing), esporta i miei dati (Function → email con JSON), elimina account (soft-delete immediato + hard-delete a 30 giorni con cascata su figli, commenti anonimizzati "Utente non più attivo", punti azzerati, biglietti lotteria attivi rimborsati).

---

## F. Area giochi 6–12 anni

Regole trasversali: modalità bambino a UI separata (gate di uscita "tieni premuto 3 secondi"); nessuna chat, nessun link esterno, nessun acquisto, nessuna push; reward moderati con **cap 10 punti/giorno per famiglia** (sommati su tutti i giochi e figli, anti-abuso e anti-compulsione); ogni sessione termina con suggerimento libro correlato mostrato al genitore; niente "game over" punitivi: si sbaglia → si riprova con un aiuto in più; progressi per profilo figlio; contenuti offline-cacheable.

Fasce di difficoltà: **6–8** e **9–12** (selezione automatica dall'anno di nascita, regolabile dal genitore).

### F1. "Parole in fuga" — gioco di parole
- **Obiettivo educativo**: vocabolario, riconoscimento lettere/parole.
- **Meccanica**: anagrammi e parole nascoste tratte da titoli e personaggi dei libri in catalogo. 6–8: ricomponi 4–6 lettere con tessere trascinabili; 9–12: parole nascoste in griglia 8×8 a tema (un libro = un tema).
- **Durata**: 3–4 min (5 parole). **Difficoltà**: lunghezza parola e assenza suggerimenti crescono col livello.
- **Reward**: 1 punto ogni 5 parole, entro cap. Stelline cosmetiche per il progresso personale.
- **Anti-frustrazione**: dopo 2 errori la prima lettera si posiziona da sola; mai timer nella fascia 6–8.
- **Contenuti iniziali**: 60 parole (30 per fascia), curate dallo staff da titoli reali.
- **Ponte libreria**: "La parola era VOLPE 🦊 — c'è una volpe furbissima in {libro}, ce l'abbiamo in libreria!"

### F2. "Il paroliere" — conoscenza dell'italiano
- **Obiettivo**: grammatica e ortografia (plurali, doppie, accenti, articoli; 9–12: verbi, sinonimi).
- **Meccanica**: scelta multipla illustrata a round di 8 domande ("Si scrive… 🍒 CILIEGIA o CILIEGGIA?").
- **Durata**: 3–5 min. **Difficoltà**: 3 livelli per fascia, sblocco progressivo.
- **Reward**: 1 punto ogni round ≥6/8, entro cap.
- **Anti-frustrazione**: risposta sbagliata → spiegazione breve e amichevole, la domanda torna a fine round per il riscatto.
- **Contenuti iniziali**: 90 domande (45 per fascia) validate dallo staff.
- **Ponte libreria**: round a tema collegati a collane di grammatica divertente presenti a scaffale.

### F3. "Ricomponi la figura" — puzzle
- **Obiettivo**: percezione visiva, memoria, pazienza.
- **Meccanica**: puzzle drag & drop da illustrazioni e copertine (con permesso editori o tavole con licenza). 6–8: 9–12 pezzi; 9–12: 24–35 pezzi.
- **Durata**: 3–6 min. **Difficoltà**: numero pezzi + rotazione (solo 9–12, ultimo livello).
- **Reward**: 2 punti a puzzle completato, entro cap. Galleria personale dei puzzle completati.
- **Anti-frustrazione**: pulsante "aiutino" mostra 2 sec l'immagine completa; i pezzi si agganciano con tolleranza generosa.
- **Contenuti iniziali**: 30 immagini (15 per fascia).
- **Ponte libreria**: completato il puzzle → "Questa copertina è di {libro}: sfoglialo in libreria!"

### F4. "Quiz del piccolo esploratore" — quiz culturale
- **Obiettivo**: cultura generale (storie e miti, geografia, scienza, arte) per fascia.
- **Meccanica**: quiz illustrato 6 domande, 4 opzioni, con "amico libro" (un indizio per round preso da un libro reale).
- **Durata**: 3–4 min. **Reward**: 1 punto per quiz ≥4/6, entro cap; badge tematici (Esploratore dello Spazio…).
- **Anti-frustrazione**: nessun timer 6–8; spiegazione curiosa dopo ogni risposta ("Lo sapevi? Il polpo ha tre cuori! 🐙").
- **Contenuti iniziali**: 120 domande (60 per fascia, 4 temi × 15).
- **Ponte libreria**: ogni tema chiude con lo scaffale correlato ("Ti piace lo spazio? Guarda cosa c'è nello scaffale Scienze!").

### F5. "Trova le differenze" — visivo-culturale
- **Obiettivo**: attenzione ai dettagli, osservazione — la stessa che serve per leggere le figure degli albi.
- **Meccanica**: due tavole illustrate affiancate, 5 differenze (6–8) o 8 (9–12), tap sulla differenza. Variante memory con personaggi letterari per varietà.
- **Durata**: 2–4 min. **Reward**: 1 punto a tavola completata, entro cap.
- **Anti-frustrazione**: dopo 60 sec di stallo una zona si illumina leggermente.
- **Contenuti iniziali**: 24 tavole + 3 mazzi memory da 12 coppie.
- **Ponte libreria**: tavole tratte da illustratori presenti in libreria → "Le illustrazioni sono di {illustratore}: trovi i suoi libri da noi!"

**Nota tecnica**: se i componenti FluxBuilder non coprono drag & drop (F1/F3), i giochi girano in webview HTML5 self-hosted autenticata (piano B del §03). Decisione allo spike Sprint 0.

---

## G. Punti fedeltà

**Principio**: il wallet è **event-sourced** — il saldo è la somma delle `loyalty_transactions`, immutabili, scritte solo da Cloud Functions. Nessuna scrittura client, mai.

**Earning**

| Azione | Punti | Validazione |
|---|---|---|
| Acquisto in negozio | 1 punto/€ | Staff scansiona QR wallet + inserisce importo + PIN |
| Ritiro prenotazione | come sopra | idem, al momento del pagamento |
| Check-in evento | 20 | Scansione QR prenotazione da staff |
| Gioco completato | 1–2 (cap 10/giorno/famiglia) | Function `onGameSessionComplete` |
| Referral andato a buon fine | 50 a entrambi | Nuovo utente completa onboarding + primo check-in/acquisto |
| Bonus benvenuto | 50 | Onboarding completato (una volta) |

**Redemption**: coupon a soglie (200 punti → sconto €5; 500 → €15; 900 → €30) + biglietti lotteria (150 punti/numero, vedi §H).

**Livelli** (solo status, benefici semplici): Lettore (0+) · Gran Lettore (500 punti/anno: anteprima eventi 24h) · Topo di Biblioteca (1.200/anno: evento riservato annuale). I livelli si calcolano sui punti **guadagnati** nell'anno, non sul saldo — spendere non fa retrocedere.

**Scadenza**: 12 mesi rolling dal guadagno; push di preavviso a 30 giorni ("Hai 180 punti che scadono il {data}: c'è un coupon che ti aspetta"). Consumo FIFO (i punti più vecchi si spendono per primi).

**Anti-abuso**
- Accredito acquisti solo da staff con PIN; importo max €300/transazione, oltre → conferma admin.
- Cap giochi per **famiglia**, non per figlio (evita farming multi-profilo).
- Referral: max 5/mese; stesso device fingerprint → non conta.
- Ogni transazione ha `source`, `operator_id` (se staff), timestamp: audit completo in P3 per l'utente e nel backoffice.

---

## H. Lotteria adulti a 90 numeri

> ⚠️ **Compliance (inline, non in fondo)**: in Italia le lotterie con vendita di biglietti sono riservate a enti no-profit (DPR 430/2001). Per un esercizio commerciale la configurazione lecita è la **manifestazione a premio collegata al programma fedeltà**: i numeri si ottengono **esclusivamente con punti** già maturati (mai denaro, mai punti acquistabili), con regolamento depositato secondo la normativa su operazioni/concorsi a premio, eventuale cauzione e comunicazioni ministeriali ove richieste. **Decisione committente 2026-07-05**: variante solo-punti confermata; sviluppo consentito, **rilascio bloccato finché un consulente legale non valida regolamento e inquadramento**. La UI non usa mai il lessico dell'azzardo: si chiama "Estrazione dei Lettori".

**Stati round**: `draft → open → closed → drawn → archived` (+ `refunded` per fallback).

**Configurazione round (solo admin)**: nome, data apertura (default giorno 1), chiusura vendite (default giorno 25, ore 23:59), data/luogo estrazione (default ultimo sabato, in libreria), costo per numero (default 150 punti), soglia minima numeri venduti (default 30), 3 premi con descrizione e valore (default: 1° buono €50, 2° buono €25, 3° libro ≤€15 — placeholder da confermare col regolamento).

**Flusso utente (solo parent 18+ verificato; staff/admin esclusi)**
1. P5 → round attivo con countdown chiusura e regolamento sempre linkato.
2. Griglia 90 numeri: verde = libero, grigio = preso, blu = mio. Max 5 numeri per utente per round (limite di equità, configurabile).
3. Tap su libero → sheet: "Numero 42 — 150 punti. Il tuo saldo: 380. Confermi?"
4. Function `buyLotteryTicket` in **transazione atomica**: verifica saldo + numero libero + limite utente + round `open` → debito punti + creazione ticket con ID `{roundId}_{number}` (unicità strutturale), o rollback totale. Conflitto → "Il 42 è appena stato preso! Il 43 è libero 😉" + refresh.
5. Ricevuta nel wallet; push a chiusura vendite e a estrazione avvenuta.

**Estrazione (trasparente e riproducibile)**
1. Alla chiusura la Function pubblica `commit_hash = SHA-256(seed segreto)` — impegno pubblico prima dell'estrazione.
2. Il giorno dell'estrazione (in libreria, momento pubblico): admin avvia `drawLottery` → il seed viene rivelato, chiunque può verificare l'hash; PRNG deterministico dal seed estrae 3 numeri distinti tra i **venduti** → 1°, 2°, 3° premio.
3. Verbale automatico in `admin_logs` (seed, hash, numeri, timestamp, operatore) + pubblicazione risultati in P5 + push ai partecipanti ("È uscito il 42! Controlla i tuoi numeri 🎉").
4. Vincitori: notifica dedicata + coupon-premio nel wallet, ritiro in negozio entro 30 giorni (poi il premio va in beneficenza secondo regolamento — prassi delle operazioni a premio).

**Fallback soglia minima**: se alla chiusura i numeri venduti sono < soglia → round `refunded`: rimborso automatico integrale dei punti (transazioni di storno tracciate) + push "Non abbiamo raggiunto il numero minimo di partecipanti: i tuoi punti sono tornati nel wallet. La prossima estrazione apre il {data}."

**Storico**: P5 mostra round passati con i propri numeri, esiti e verbali pubblici.

---
**Riepilogo TASK 5**: 8 moduli specificati con stati, flussi numerati, microcopy e vincoli DB-side. Scelte chiave: feed editoriale senza UGC; prenota-e-ritira come motore drive-to-store con Stripe rinviato a Fase 2; wallet event-sourced scritto solo da Functions; 5 giochi con cap famiglia e ponte esplicito ai libri; lotteria "Estrazione dei Lettori" solo-punti con commit-reveal, verbale e rilascio gated dal legale.
