# TASK 7 — UX & Design System

Direzione: **editoriale, caldo, educativo, contemporaneo**. Riferimento mentale: un albo illustrato ben stampato, non un'app tech. Non è un clone di Instagram: il feed è una vetrina curata, la gerarchia visiva privilegia carta, illustrazione e tipografia.

## Palette

Base calda "carta e bottega", con accenti da albo illustrato:

| Token | Hex | Uso |
|---|---|---|
| `paper` | `#FAF5EC` | Sfondo principale (mai bianco puro: è carta) |
| `ink` | `#2B2118` | Testo primario (marrone-inchiostro, non nero) |
| `terracotta` | `#C4572E` | Primario: CTA, tab attiva, prezzi in saldo |
| `verde-salvia` | `#6E8B6B` | Successo, disponibilità, conferme |
| `blu-notte` | `#33506D` | Link, eventi, elementi informativi |
| `giallo-miele` | `#E9B44C` | Punti fedeltà, stelle, highlight |
| `rosa-gesso` | `#E8C4B8` | Sfondi card secondarie, area bambini |
| `errore` | `#A63D2F` | Errori (in famiglia con terracotta, mai rosso allarme) |

Contrasto: tutte le coppie testo/sfondo verificate ≥4.5:1 (AA); `terracotta` su `paper` usata solo ≥18pt o come sfondo con testo `paper`.

**Modalità bambino**: stessi colori ma saturazione +10%, sfondi a blocchi pieni (`rosa-gesso`, `giallo-miele`), niente grigi.

## Typography

| Ruolo | Font | Note |
|---|---|---|
| Display/titoli | **Fraunces** (o serif calda equivalente disponibile nel builder) | Voce editoriale, da libreria |
| Testo UI | **Nunito Sans** | Rotonda, amichevole, ottima leggibilità |
| Modalità bambino | **Nunito** (arrotondata) 18pt+ | Lettere ben distinguibili (a/g a un piano), interlinea 1.5 |

Scala: 28/22/18/16/14. Minimo assoluto 14pt; niente testo grigio chiaro su carta.

## Componenti core

- **Card libro**: copertina dominante (60% dell'area), titolo serif, fascia età come pillola colorata, prezzo; badge angolari 🏷 Saldo / ✨ Novità. Ombra morbida, raggio 12.
- **Card evento**: data a blocco calendario (giorno grande + mese), foto, titolo, pillola "Posti: 5 rimasti" (verde→giallo→"Lista d'attesa").
- **Card post**: foto edge-to-edge, caption sotto, azioni (cuore, commento, segnalibro) in `ink`, contatori discreti. Niente stories, niente reels: post e basta.
- **Pillola punti**: `giallo-miele`, icona stella + saldo, sempre in header di Profilo e wallet.
- **Griglia lotteria**: 9×10, celle 40pt min; libero = bordo verde, preso = pieno grigio caldo, mio = pieno `blu-notte` con stella. Countdown testuale sopra ("Le vendite chiudono tra 3 giorni").
- **Bottoni**: primario pieno `terracotta` raggio 24, alto 52pt; secondario outline `ink`. Mai più di un primario per schermata.

## Tab bar

5 icone line-style con riempimento allo stato attivo + label sempre visibile (utenti non tech). Il tab Giochi ha l'icona puzzle in `giallo-miele` anche da inattivo: è il tab "dei bambini", riconoscibile al volo dal genitore.

## Onboarding (O1–O5)

- 3 slide massimo, illustrate, una promessa ciascuna: "Il libro giusto in un minuto" / "Prenota laboratori ed eventi" / "Ogni visita vale punti".
- Registrazione dopo il valore, non prima; login social in evidenza, OTP email come fallback.
- Aggiunta figlio incorniciata come beneficio ("Dicci per chi leggi: ti mostriamo solo il meglio per la sua età"), skippabile, consenso in linguaggio umano + link informativa.
- Bonus benvenuto (50 punti) mostrato subito nel wallet: il loop loyalty parte al minuto zero.

## Pattern adulti vs bambini

| Aspetto | Adulti | Bambini (modalità gioco) |
|---|---|---|
| Densità | Liste e card compatte | Una cosa alla volta, schermo pieno |
| Touch target | ≥44pt | ≥64pt |
| Testo | Normale | Minimo indispensabile, sempre accompagnato da icona/illustrazione |
| Audio | No | Feedback sonori brevi opzionali (toggle genitore) |
| Navigazione | Tab bar | Lineare: avanti/esci, niente tab |
| Uscita | Libera | Gate "tieni premuto 3 secondi" |
| Ricompense | Punti, coupon | Stelline e badge cosmetici (i punti li vede il genitore) |

## Microcopy (voce del brand)

Tono: la voce del libraio di fiducia — calda, concreta, mai infantile con gli adulti, mai aziendale. Regole:
1. Prima persona plurale ("Te lo teniamo da parte"), mai passivi burocratici.
2. Numeri e date esplicite, mai "a breve".
3. Errori senza colpa e con via d'uscita ("Il posto è appena andato a qualcun altro. Vuoi la lista d'attesa?").
4. Emoji: max una per messaggio, solo in contesti leggeri (mai in errori di pagamento o privacy).
5. Lessico vietato: "gioca e vinci", "fortuna", "jackpot" (lotteria = "Estrazione dei Lettori"); "utente" (si dice "tu"); anglicismi evitabili ("wishlist" → "Lista dei desideri" in UI).

## Accessibilità (WCAG 2.1 AA)

- Contrasto ≥4.5:1 testo, ≥3:1 componenti; verificato sulla palette sopra.
- Dynamic type: layout regge fino a +30% dimensione testo.
- Tutte le immagini funzionali con alt; le foto dei post con alt dalla caption.
- Nessuna informazione affidata al solo colore (griglia lotteria: anche icone — bordo/pieno/stella).
- Target minimi 44pt; focus visibile; VoiceOver/TalkBack sui flussi critici (prenotazione, wallet) testato prima del rilascio.
- Animazioni disattivabili (rispetta `prefers-reduced-motion`).

## Empty ed error states

Già specificati schermata per schermata in `04-information-architecture.md`; regola di design: ogni stato vuoto ha un'illustrazione leggera, una frase nel tono del brand e **una sola azione** che porta avanti (mai un vicolo cieco).

---
**Riepilogo TASK 7**: palette "carta e bottega" a 8 token verificata AA, Fraunces+Nunito Sans, componenti card-first con feed dichiaratamente non-Instagram (cronologico, senza stories), doppio pattern adulti/bambini con gate di uscita, e regole di microcopy che vietano il lessico dell'azzardo e il tono da e-commerce generico.
