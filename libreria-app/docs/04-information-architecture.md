# TASK 4 — Information Architecture

## Tab bar (5 tab)

| Tab | Icona | Contenuto | Ruoli |
|---|---|---|---|
| **Home** | casetta | Feed della libreria + blocchi curati (prossimo evento, saldi, "per il tuo bambino") | Tutti |
| **Libri** | libro | Catalogo, ricerca, filtri età/tema, Saldi, Novità, wishlist | Tutti |
| **Eventi** | calendario | Calendario, prossimi eventi, le mie prenotazioni | Tutti |
| **Giochi** | puzzle | Selettore profilo figlio → modalità bambino | Genitori con ≥1 profilo figlio (altrimenti empty state invito) |
| **Profilo** | persona | Account, figli, wallet punti, coupon, lotteria, ordini, negozio, impostazioni | Tutti |

La **scheda negozio** vive dentro Profilo → "La libreria" + card fissa in Home; non merita un tab.

## Mappa schermate

```
Onboarding
├── O1 Splash / value proposition (3 slide)
├── O2 Registrazione (email OTP · Google · Apple) / Login
├── O3 Profilo genitore (nome, foto opz., data nascita, quartiere opz.)
├── O4 Aggiungi figlio (opzionale, skippabile) + consenso genitore
└── O5 Interessi di lettura → Home personalizzata

Home
├── H1 Feed (post libreria: foto, caption, like, commenti, salva)
├── H2 Dettaglio post + commenti
└── H3 Card negozio → S1

Libri
├── L1 Catalogo (filtri: età, tema, saldo, novità; ricerca)
├── L2 Scheda libro (copertina, descrizione, età, consiglio del libraio,
│      disponibilità + "verificato il", prezzo, ❤ wishlist, "Prenota ritiro")
├── L3 Wishlist
├── L4 Prenotazione ritiro (conferma → ordine "da ritirare")
└── L5 Saldi / Novità (liste filtrate, stessa L1)

Eventi
├── E1 Calendario/lista eventi
├── E2 Scheda evento (data, posti rimasti, prezzo se a pagamento, relatori)
├── E3 Prenotazione (n. partecipanti, per quale figlio) → conferma + QR
├── E4 Le mie prenotazioni (futuro/passato, QR check-in, cancella)
└── E5 Lista d'attesa (se pieno)

Giochi (modalità bambino dopo selezione profilo figlio)
├── G0 Selettore figlio + gate genitore ("tieni premuto per uscire")
├── G1 Hub giochi (5 card grandi, progressi, punti di oggi /cap)
├── G2 Parole in fuga (gioco di parole)
├── G3 Il paroliere (italiano)
├── G4 Ricomponi la figura (puzzle)
├── G5 Quiz del piccolo esploratore (quiz culturale)
├── G6 Trova le differenze (visivo-culturale)
└── G7 Fine sessione → "Ti è piaciuto? In libreria c'è {libro}" (visibile al genitore)

Profilo
├── P1 Panoramica (foto, nome, punti, livello, coupon attivi)
├── P2 I miei figli (lista, aggiungi/modifica/elimina)
├── P3 Wallet (saldo punti, storico transazioni, QR personale per la cassa)
├── P4 Coupon (attivi/usati/scaduti)
├── P5 Lotteria (round attivo, griglia 90 numeri, i miei numeri, storico, regolamento)
├── P6 Ordini e ritiri (stato: da ritirare/ritirato/annullato)
├── P7 Storico eventi
├── S1 La libreria (foto, indirizzo, orari, giorni apertura, mappa, Naviga,
│      contatti, come arrivare)
└── P8 Impostazioni (notifiche per categoria, privacy, consensi, elimina account)
```

## Flussi chiave (già validati nel piano strategico)

1. **Primo accesso**: O1→O2→O3→(O4)→O5→Home personalizzata. Obiettivo <2 minuti, skip ovunque tranne O2.
2. **Evento**: H1/E1 → E2 → E3 → push reminder T-24h → QR check-in in libreria → +20 punti.
3. **Libro**: L1 → L2 → wishlist o L4 prenota → push "pronto per il ritiro" → ritiro in cassa → accredito punti via QR wallet.
4. **Loyalty**: P3 → coupon disponibile → visita → redemption in cassa (staff PIN).
5. **Lotteria**: P5 → griglia → conferma acquisto numero con punti → push a chiusura e estrazione.
6. **Giochi**: G0 → G1 → sessione 3–5 min → reward (entro cap) → suggerimento libro al genitore.

## Stati vuoti (obbligatori, con microcopy reale)

| Schermata | Empty state |
|---|---|
| H1 Feed (nuovo utente offline-first) | "Le storie della libreria arrivano qui. Passa a trovarci intanto! 📚" + card negozio |
| L3 Wishlist | "Nessun libro salvato. Tocca il cuore su un libro che ti incuriosisce." |
| E4 Prenotazioni | "Non hai eventi in programma. Guarda cosa succede questa settimana →" |
| Giochi senza figlio | "L'area giochi è per i piccoli lettori dai 6 ai 12 anni. Aggiungi il profilo di tuo figlio per iniziare." |
| P4 Coupon | "I coupon compaiono qui quando accumuli punti. Ti mancano {n} punti al prossimo premio." |
| P5 Nessun round attivo | "La prossima lotteria apre il {data}. Intanto accumula punti!" |

## Stati errore (pattern unico)

- Rete assente: banner non bloccante "Sei offline — ti mostro le ultime cose viste", retry automatico.
- Azione fallita (like, prenotazione): toast con motivo umano + azione ("Il posto è appena andato a qualcun altro. Vuoi la lista d'attesa?").
- Conflitto lotteria: "Il {n} è appena stato preso! Il {alt} è libero 😉" + refresh griglia.
- Errore server generico: "Qualcosa non ha funzionato da parte nostra. Riprova tra poco." — mai codici tecnici all'utente.

## Permessi per ruolo (matrice sintetica)

| Azione | Anonimo* | Parent | Parent 18+ | Staff | Admin |
|---|---|---|---|---|---|
| Vedere feed/catalogo/eventi/negozio | ✅ | ✅ | ✅ | ✅ | ✅ |
| Like, commento, salva, wishlist | ❌ | ✅ | ✅ | ✅ | ✅ |
| Prenotare evento/libro | ❌ | ✅ | ✅ | ✅ | ✅ |
| Area giochi | ❌ | ✅ (con figlio) | ✅ | ✅ | ✅ |
| Comprare numeri lotteria | ❌ | ❌ | ✅ | ❌** | ❌** |
| Creare post/eventi, gestire catalogo | ❌ | ❌ | ❌ | ✅ | ✅ |
| Accreditare punti, check-in | ❌ | ❌ | ❌ | ✅ | ✅ |
| Configurare lotteria, estrarre | ❌ | ❌ | ❌ | ❌ | ✅ |
| Vedere admin_logs | ❌ | ❌ | ❌ | ❌ | ✅ |

\* Browse senza account consentito (riduce attrito); ogni azione chiede login.
\** Staff e admin esclusi dalla lotteria per trasparenza (regola nel regolamento).

## Deep link

Schema: `libreribra://` + universal links `https://app.libreribra.it/...`

| Link | Destinazione | Uso |
|---|---|---|
| `/evento/{id}` | E2 | Push reminder, QR locandine, Instagram bio |
| `/libro/{id}` | L2 | Push novità/saldi, condivisione |
| `/post/{id}` | H2 | Push nuovo post |
| `/wallet` | P3 | Push punti accreditati |
| `/lotteria` | P5 | Push apertura/chiusura/estrazione |
| `/negozio` | S1 | Google Business Profile, QR vetrina |

---
**Riepilogo TASK 4**: 5 tab (Home, Libri, Eventi, Giochi, Profilo), scheda negozio dentro Profilo + card in Home; ~30 schermate mappate; empty/error states con microcopy definito; matrice permessi con staff escluso dalla lotteria; 6 deep link canonici per push e QR fisici.
