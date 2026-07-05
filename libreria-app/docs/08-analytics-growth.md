# TASK 8 — Analytics & Growth

Strumento: Firebase Analytics (incluso nello stack) + export BigQuery attivato dal giorno 1 (gratis, evita di perdere lo storico). PostHog opzionale in Fase 2 se servono funnel visuali self-service per il team.

**Regola privacy**: nessun nome, nessuna età esatta di minori negli eventi — solo fasce (`age_band`). Nessun evento analytics dalla modalità bambino oltre a quelli di sessione gioco anonimi rispetto al figlio (si traccia `child_index`, mai il nickname).

## Event tracking schema

Eventi del piano strategico (invariati) + estensioni. Parametri comuni: `store_id`, `role`.

| Evento | Parametri | Trigger |
|---|---|---|
| `app_open` | source (push/deep_link/organic) | apertura |
| `onboarding_completed` | children_count, interests_count | fine O5 |
| `child_profile_created` | age_band | O4/P2 |
| `post_viewed` / `post_liked` / `post_saved` | post_id, linked_type | feed |
| `comment_created` | post_id | feed |
| `book_viewed` | book_id, age_band, on_sale | L2 |
| `book_saved` | book_id | wishlist |
| `book_reserved` | book_id | L4 |
| `order_ready_notified` / `order_picked_up` | order_id, days_to_pickup | Functions |
| `event_viewed` | event_id, category | E2 |
| `event_booked` | event_id, seats, price>0 | `bookEvent` |
| `event_waitlisted` / `event_checked_in` / `event_cancelled` | event_id | Functions |
| `game_started` / `game_completed` | game_id, age_band, duration_s, points_awarded | callable |
| `game_book_suggestion_tapped` | game_id, book_id | G7 → L2 (il ponte giochi→libri, KPI chiave) |
| `points_earned` / `points_redeemed` | amount, source | `applyLoyaltyTransaction` |
| `coupon_redeemed` | coupon_id, type | cassa |
| `lottery_viewed` | round_id | P5 |
| `lottery_ticket_purchased` | round_id, number, points | `buyLotteryTicket` |
| `lottery_draw_viewed` | round_id, is_winner | post-estrazione |
| `push_opened` | campaign, deep_link | notifica |
| `referral_sent` / `referral_converted` | — | share/Function |
| `store_navigate_tapped` | — | S1 (proxy intenzione di visita) |
| `purchase_registered` | amount_eur | accredito punti in cassa (proxy revenue) |

## North star e KPI

**North star: visite in negozio attribuite all'app / mese** = `event_checked_in` + `coupon_redeemed` + `order_picked_up` + `purchase_registered` (dedup per utente/giorno).

KPI di supporto: quelli della tabella del piano strategico (attivazione >60%, engagement >40%, retention 30gg 20–25%, ≥2 redemption), riportati nel backoffice §Report in linguaggio da libraio ("Questa settimana l'app ha portato in negozio ~34 visite").

## Funnel (definizioni misurabili)

1. **Onboarding**: `first_open → onboarding_completed` (target >60%) con step intermedi O2/O3/O4 per trovare il drop.
2. **Eventi**: `event_viewed → event_booked → event_checked_in`. Benchmark interno da costruire; allarme se checked_in/booked <60% (troppi no-show → rivedere reminder).
3. **Acquisto**: `book_viewed → book_saved|book_reserved → order_picked_up`.
4. **Giochi**: `game_started → game_completed → game_book_suggestion_tapped → book_viewed`. L'ultimo passo è la prova che i giochi vendono libri.
5. **Loyalty**: `points_earned (1ª) → points_earned (2ª sorgente diversa) → points_redeemed`. Utente "attivato loyalty" = ha guadagnato da ≥2 sorgenti.
6. **Lotteria**: `lottery_viewed → lottery_ticket_purchased → lottery_draw_viewed`. Metrica di salute: % punti spesi in lotteria vs coupon (se la lotteria cannibalizza i coupon oltre il 60%, ribilanciare i costi).

## Cohort e retention

- Coorti settimanali per data di installazione; retention D7/D30 per: con/senza profilo figlio, con/senza prima prenotazione entro 7 giorni.
- Ipotesi da verificare al pilot: *chi prenota un evento nei primi 7 giorni ha retention 30gg doppia* → se vera, l'onboarding spinge un evento imminente come primo step.

## Retention loops (dal piano, resi operativi)

1. **Evento → visita → acquisto → punti → prossimo evento** (loop primario, misurato dalla north star).
2. **Wishlist → push disponibilità/saldo → visita** (push `book_saved` + `on_sale` = segmento caldissimo).
3. **Gioco → suggerimento libro → wishlist genitore → visita** (loop famiglia, unico nel suo genere: nessuna catena ce l'ha).
4. **Punti in scadenza → push 30gg → redemption** (loss aversion gentile).
5. **Lotteria mensile → ritmo ricorrente di apertura/estrazione** = due momenti di riattivazione al mese gratuiti.

## Push strategy

- **Cap globale: 2 push promozionali/settimana per utente.** Le transazionali (ordine pronto, reminder evento prenotato, esiti lotteria) non contano nel cap.
- Opt-in granulare per categoria (eventi, novità/saldi, lotteria) chiesto **in contesto** (dopo la prima prenotazione, non all'install).
- Segmenti FCM (topic): fascia età figli, interessi, quartiere, tier loyalty.
- Quiet hours: mai push 21:00–9:00 (target = genitori).

## CRM triggers (scheduled functions, MVP semplice)

| Trigger | Condizione | Azione |
|---|---|---|
| Welcome flow | onboarding completato | push J+1 "Ecco 3 libri per {fascia}" + J+3 evento imminente |
| Reminder evento | T-24h, T-2h | push transazionale |
| Ordine pronto / in scadenza | `ready_for_pickup`, deadline-2gg | push |
| Inattività | 14 giorni senza `app_open` | push con contenuto per fascia figlio |
| Compleanno bambino | mese del birth_year+interessi | "Un'idea regalo per i suoi {n} anni" |
| Cambio fascia età | birth_year → nuova fascia | aggiorna segmenti + push raccolta nuova fascia |
| Punti in scadenza | 30 giorni | push wallet |
| Lotteria | apertura, chiusura-48h, estrazione | push topic `lotteria` |

## Referral loop

- Codice personale in P1 + share sheet ("Regala 50 punti a un amico genitore: 50 anche a te").
- Conversione = nuovo utente completa onboarding **e** fa il primo check-in/acquisto (non basta installare: paga solo comportamento reale).
- Anti-abuso: max 5/mese, device fingerprint, punti referral marcati `source=referral` e monitorati.

---
**Riepilogo TASK 8**: schema eventi che estende i nomi del piano senza rinominarli; north star = visite in negozio attribuite; 6 funnel con soglie d'allarme; 5 retention loop di cui uno esclusivo (giochi→libri); push cappate a 2/settimana con quiet hours; 8 trigger CRM automatizzabili con scheduled functions; referral pagato solo su comportamento verificato.
