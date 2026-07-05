# TASK 6 — Data Model (Firestore)

Convenzioni globali:
- Ogni documento ha `store_id` (string), `created_at`, `updated_at` (timestamp) — omessi sotto per brevità.
- **Scrittura client vietata ovunque tranne dove indicato**; le collezioni marcate 🔒 sono scrivibili solo da Cloud Functions (Admin SDK) — le security rules le negano in blocco al client.
- Ruoli verificati via custom claims: `request.auth.token.role in ['staff','admin']`.
- I contatori denormalizzati (`like_count`, `booked_count`, …) sono aggiornati esclusivamente da Functions in transazione.
- Legenda eventi: chi/cosa aggiorna l'entità.

---

## Identità e profili

### `users` 🔒 (doc id = uid Firebase Auth)
| Campo | Tipo | Note |
|---|---|---|
| email | string | da Auth |
| role | string | `parent` \| `staff` \| `admin` (specchio del custom claim) |
| birth_date | timestamp | gate 18+ lotteria |
| status | string | `active` \| `restricted` \| `deleted_soft` |
| referral_code | string | univoco, generato alla creazione |
| referred_by | string? | uid |
| onboarding_completed | bool | |
| notification_prefs | map | per categoria: eventi, novità, promozioni, lotteria |
| device_fingerprints | array | anti-abuso referral |

Sicurezza: lettura solo proprietario (`uid == doc.id`) o staff/admin; scrittura solo Functions.
Aggiornata da: `onUserCreate` (Auth trigger), onboarding, impostazioni (via callable), `onUserDelete` (cascata).
Indici: `referral_code` (lookup), `status + store_id`.

### `profiles` (doc id = uid)
Dati pubblici minimi mostrati accanto ai commenti.
| Campo | Tipo |
|---|---|
| display_name | string |
| photo_url | string? |
| neighborhood | string? |

Sicurezza: lettura autenticati; scrittura solo proprietario, con validazione campi (nessun campo extra).
Aggiornata da: utente (P1).

### `child_profiles` (subcollection `users/{uid}/children/{childId}`)
| Campo | Tipo | Note |
|---|---|---|
| nickname | string | mai cognome obbligatorio |
| birth_year | int | solo anno: minimizzazione |
| interests | array\<string\> | da vocabolario chiuso |
| avatar_id | string | set illustrato, niente foto |
| consent_at | timestamp | consenso genitore, obbligatorio |
| age_band | string | `6-8` \| `9-12` calcolato, override genitore |

Sicurezza: lettura/scrittura solo genitore proprietario; **staff/admin vedono solo conteggio** (via aggregato), non i documenti.
Vincoli: max 6 per utente (rule su query + check in Function); `consent_at` obbligatorio alla creazione.
Aggiornata da: genitore (P2); cascata delete da `onUserDelete`.

---

## Feed

### `posts`
| Campo | Tipo | Note |
|---|---|---|
| author_uid | string | solo staff/admin |
| photos | array\<string\> | 1–5 URL Storage |
| caption | string ≤2200 | |
| status | string | `draft` \| `published` \| `hidden` \| `archived` |
| published_at | timestamp? | |
| linked_book_id / linked_event_id | string? | card inline |
| age_band_tag | string? | segmentazione |
| like_count / comment_count / save_count | int | denormalizzati 🔒 |

Sicurezza: lettura pubblica se `published`; scrittura solo staff/admin (create/update campi contenuto, mai i contatori).
Indici: `status + published_at desc`, `linked_event_id`.
Aggiornata da: staff (backoffice/app), Functions (contatori), scheduler (post programmati).

### `comments` (subcollection `posts/{postId}/comments/{commentId}`)
| Campo | Tipo | Note |
|---|---|---|
| author_uid | string | = `request.auth.uid` (rule) |
| text | string ≤500 | niente URL (validato in Function) |
| status | string | `visible` \| `pending` \| `hidden` |
| hidden_reason | string? | obbligatorio se `hidden` |

Sicurezza: create autenticati (solo `text`; lo stato lo decide `onCommentCreate`); lettura: `visible` per tutti, `pending` solo autore, `hidden` solo staff; update/delete solo staff (soft).
Aggiornata da: utente (create), `onCommentCreate` (filtro), staff (moderazione → riga in `admin_logs`).

### `likes` (subcollection `posts/{postId}/likes/{postId}_{uid}`)
Doc id composito = **unicità strutturale** (un like per utente).
Campi: `uid`. Sicurezza: create/delete solo se `uid` nel doc id == auth; contatore aggiornato da trigger.

### `saves` (subcollection `users/{uid}/saves/{postId}`)
Campi: `post_id`, `saved_at`. Sicurezza: solo proprietario. Alimenta la raccolta "Salvati".

---

## Catalogo e ordini

### `books`
| Campo | Tipo | Note |
|---|---|---|
| title, author, publisher | string | |
| isbn | string? | indicizzato |
| cover_url | string | |
| description | string | |
| staff_pick_note | string? | "Il consiglio di LibreriBrà" |
| age_bands | array\<string\> | `0-2`…`adulti` |
| themes | array\<string\> | vocabolario chiuso |
| price | number | € |
| on_sale | bool + sale_price | number? | |
| is_new | bool | Novità |
| availability | string | `in_stock` \| `low` \| `out` |
| availability_checked_at | timestamp | badge "verificato il" |

Sicurezza: lettura pubblica; scrittura staff/admin.
Indici compositi: `age_bands (array) + availability`, `on_sale + updated_at desc`, `is_new + updated_at desc`, `themes (array) + age_bands (array)`.
Aggiornata da: staff (CRUD, import CSV), Functions (`reserved_count` in `inventory`).

### `inventory` 🔒 (doc id = bookId)
Campi: `stock_hint` (int, indicativo), `reserved_count` (int).
Sicurezza: lettura staff; scrittura solo Functions (transazioni prenotazione) e staff via callable.
Aggiornata da: `reserveBook`, `onOrderStatusChange`, staff.

### `discounts`
| Campo | Tipo | Note |
|---|---|---|
| code | string | univoco (doc id) |
| type | string | `percent` \| `amount` \| `gift` |
| value | number | |
| owner_uid | string? | null = campagna aperta |
| status | string | `active` \| `redeemed` \| `expired` |
| expires_at | timestamp | |
| source | string | `loyalty` \| `campaign` \| `lottery_prize` |
| redeemed_by_operator | string? | staff uid + PIN check |

Sicurezza: lettura proprietario (o pubblica se campagna); scrittura solo Functions/staff.
Aggiornata da: `redeemPoints`, redemption in cassa, scheduler scadenze.

### `orders` 🔒
| Campo | Tipo | Note |
|---|---|---|
| uid, book_id | string | |
| status | string | `requested` \| `paid`(F2) \| `ready_for_pickup` \| `picked_up` \| `expired` \| `cancelled` |
| pickup_deadline | timestamp | +7 giorni da ready |
| payment | map? | Fase 2: Stripe intent id, amount — mai dati carta |

Sicurezza: lettura proprietario + staff; scrittura solo Functions (`reserveBook`, `markReady`, `markPickedUp`).
Indici: `uid + status`, `status + pickup_deadline` (scheduler expiry).
Aggiornata da: Functions, scheduler (`expireOrders` giornaliero).

---

## Eventi

### `event_categories`
Campi: `name`, `icon`, `sort`. Lettura pubblica, scrittura admin.

### `events`
| Campo | Tipo | Note |
|---|---|---|
| title, description, photo_url | string | |
| category_id | string | → event_categories |
| age_band | string? | |
| starts_at, duration_min | timestamp, int | |
| capacity | int | |
| booked_count | int 🔒 | solo Function |
| price | number | 0 = gratuito |
| linked_book_id / bundle_price | string?, number? | bundle §C |
| status | string | `draft` \| `published` \| `sold_out` \| `completed` \| `cancelled` |
| waitlist_limit | int | |

Sicurezza: lettura pubblica se `published+`; scrittura staff/admin (mai `booked_count`).
Indici: `status + starts_at`, `category_id + starts_at`, `age_band + starts_at`.
Aggiornata da: staff, `bookEvent`/`cancelBooking` (contatore), scheduler (`completed` post-evento, reminder T-24h/T-2h).

### `bookings` 🔒 (doc id = `{eventId}_{uid}` → un booking per utente/evento)
| Campo | Tipo | Note |
|---|---|---|
| event_id, uid | string | |
| seats | int 1–4 | |
| child_ids | array? | quali figli |
| status | string | `confirmed` \| `waitlisted` \| `checked_in` \| `cancelled_by_user` \| `cancelled_by_store` \| `no_show` |
| qr_token | string | firmato, per check-in |
| waitlist_position | int? | FIFO |

Sicurezza: lettura proprietario + staff; scrittura solo Functions (`bookEvent`, `cancelBooking`, `checkIn`, `promoteWaitlist`).
Indici: `uid + status`, `event_id + status + waitlist_position`.
Aggiornata da: Functions; check-in → trigger accredito 20 punti.

---

## Negozio

### `store_locations` (doc id = store_id)
Campi: `name`, `photos[]`, `address`, `lat`, `lng`, `phone`, `whatsapp`, `email`, `instagram`, `opening_hours` (map giorno→fasce), `closures[]` (date eccezionali), `directions_note`, `parking_note`.
Sicurezza: lettura pubblica; scrittura admin.

---

## Giochi

### `games` (doc id = slug: `parole-in-fuga`, …)
Campi: `title`, `description`, `age_bands[]`, `reward_points` (int), `status` (`active`|`hidden`), `engine` (`native`|`webview`), `sort`.
Sicurezza: lettura pubblica; scrittura admin.

### `quiz_questions` (usata da F2 "Il paroliere" e F4 quiz)
| Campo | Tipo | Note |
|---|---|---|
| game_id | string | |
| age_band | string | `6-8` \| `9-12` |
| level | int | |
| theme | string? | F4 |
| question, options[4], correct_index | string/array/int | |
| explanation | string | anti-frustrazione |
| linked_book_id | string? | ponte libreria |
| status | string | `draft` \| `approved` (validazione staff) |

Sicurezza: lettura autenticati (solo `approved`); scrittura staff/admin.
Indici: `game_id + age_band + level + status`.

### `puzzle_assets` (F1 parole, F3 puzzle, F5 differenze)
Campi: `game_id`, `age_band`, `type` (`word`|`grid`|`puzzle_image`|`spot_diff`|`memory_deck`), `payload` (map: parola/griglia/URL immagine+coordinate differenze/coppie), `license_note` (obbligatorio per immagini: diritti/licenza), `linked_book_id?`, `status`.
Sicurezza: come `quiz_questions`.

### `game_sessions` 🔒 (subcollection `users/{uid}/game_sessions/{id}`)
| Campo | Tipo | Note |
|---|---|---|
| child_id, game_id | string | |
| started_at, completed_at | timestamp | |
| score | map | per tipo di gioco |
| points_awarded | int | 0 se cap raggiunto |

Sicurezza: lettura genitore; **scrittura solo callable `completeGameSession`** che valida durata minima plausibile (anti-cheat: sessione <30s → 0 punti) e applica il cap famiglia 10/giorno.
Indici: `game_id + completed_at` (analytics contenuti).
Aggiornata da: callable; alimenta `loyalty_transactions`.

---

## Loyalty

### `loyalty_wallet` 🔒 (doc id = uid)
Campi: `balance` (int), `earned_this_year` (int), `tier` (`lettore`|`gran_lettore`|`topo_di_biblioteca`), `expiring_next_30d` (int, ricalcolato).
Sicurezza: lettura proprietario + staff; scrittura solo Functions. **Il saldo è derivato: si ricalcola sempre dalle transazioni, mai editato a mano.**
Aggiornata da: `applyLoyaltyTransaction` (unica via di scrittura), scheduler scadenze/tier.

### `loyalty_transactions` 🔒 (subcollection `users/{uid}/loyalty_transactions/{id}`)
| Campo | Tipo | Note |
|---|---|---|
| amount | int | + earning, − redemption/scadenza (immutabile: mai update, solo append) |
| source | string | `purchase` \| `event_checkin` \| `game` \| `referral` \| `welcome` \| `coupon_redeem` \| `lottery_ticket` \| `lottery_refund` \| `expiry` \| `admin_adjust` |
| ref_id | string? | ordine/booking/sessione/ticket collegato |
| operator_uid | string? | se accreditato da staff |
| earned_expires_at | timestamp? | per FIFO scadenza |
| balance_after | int | snapshot per audit |

Sicurezza: lettura proprietario + staff; **append-only da Functions**; nessun update/delete mai (le rules negano anche all'Admin? no: disciplina di Function + audit).
Indici: `source + created_at`, `earned_expires_at` (scheduler).
Aggiornata da: tutte le Functions economiche; `admin_adjust` richiede motivo + riga in `admin_logs`.

---

## Lotteria

### `lottery_rounds` 🔒
| Campo | Tipo | Note |
|---|---|---|
| name | string | "Estrazione dei Lettori — Marzo" |
| status | string | `draft` \| `open` \| `closed` \| `drawn` \| `refunded` \| `archived` |
| opens_at, sales_close_at, draw_at | timestamp | |
| ticket_cost_points | int | default 150 |
| max_tickets_per_user | int | default 5 |
| min_tickets_threshold | int | default 30 |
| sold_count | int | solo Function |
| commit_hash | string? | SHA-256(seed) pubblicato alla chiusura |
| revealed_seed | string? | pubblicato all'estrazione |
| winning_numbers | array\<int\>? | [1°, 2°, 3°] |
| rules_url | string | regolamento legale, obbligatorio per `open` |

Sicurezza: lettura pubblica (`open+`); scrittura solo admin via callable + scheduler.
Aggiornata da: admin (config), scheduler (`open`→`closed` + commit), `drawLottery` (reveal + estrazione), `refundRound`.
**Vincolo di rilascio**: la callable `openRound` rifiuta se `rules_url` è vuoto → gate legale codificato.

### `lottery_tickets` 🔒 (doc id = `{roundId}_{number}` → **unicità strutturale del numero**)
Campi: `round_id`, `number` (1–90), `uid`, `points_paid`, `purchased_at`, `is_winner` (int? 1/2/3).
Sicurezza: lettura: proprietario vede i suoi; griglia pubblica via aggregato `rounds/{id}/grid` (array 90 bool, niente uid esposti); scrittura solo `buyLotteryTicket` in transazione (verifica: round `open`, saldo, count utente < max, 18+, ruolo ≠ staff/admin).
Indici: `round_id + uid`, `uid + purchased_at`.

### `lottery_prizes` (subcollection `lottery_rounds/{id}/prizes/{1|2|3}`)
Campi: `rank`, `description`, `value_eur`, `winner_uid?`, `claimed_at?`, `claim_deadline` (+30gg), `unclaimed_destination` (string, da regolamento).
Sicurezza: lettura pubblica (senza uid finché non ritirato → mostra solo numero vincente); scrittura Functions/admin.
Aggiornata da: `drawLottery`, ritiro in negozio (staff), scheduler unclaimed.

---

## Sistema

### `notifications` 🔒 (subcollection `users/{uid}/notifications/{id}`)
Campi: `type`, `title`, `body`, `deep_link`, `read` (bool), `sent_at`.
Sicurezza: lettura/flag-read proprietario; create solo Functions. Specchio in-app delle push (centro notifiche).
Aggiornata da: tutte le Functions notificanti; scheduler digest.

### `reports` 🔒
Campi: `reporter_uid`, `target_type` (`comment`|`user`), `target_ref`, `reason` (enum + testo), `status` (`open`|`resolved`|`dismissed`), `resolved_by?`, `resolution_note?`.
Sicurezza: create autenticati (rate-limit 5/giorno in Function); lettura/update solo staff.
Aggiornata da: utenti (create via callable), staff (backoffice).

### `admin_logs` 🔒 (append-only)
Campi: `actor_uid`, `action` (enum: `hide_comment`, `adjust_points`, `open_round`, `draw_lottery`, `refund_round`, `csv_import`, `role_change`, …), `target_ref`, `payload` (map, es. seed+hash del verbale estrazione), `at`.
Sicurezza: lettura solo admin; scrittura solo Functions. Nessun update/delete: è il registro di audit (verbali lotteria inclusi).

---

## Diagramma relazioni (essenziale)

```
users 1─N child_profiles          users 1─1 loyalty_wallet 1─N loyalty_transactions
users 1─N bookings N─1 events N─1 event_categories
users 1─N orders   N─1 books  1─1 inventory
posts 1─N comments · posts 1─N likes · users 1─N saves N─1 posts
games 1─N quiz_questions / puzzle_assets · users 1─N game_sessions N─1 games
lottery_rounds 1─N lottery_tickets N─1 users · lottery_rounds 1─3 lottery_prizes
tutte le entità N─1 store_locations (store_id)
```

## Invarianti di dominio (fatti rispettare da Functions + rules, mai dal client)

1. Un numero lotteria ha un solo proprietario per round (doc id `{roundId}_{number}`).
2. `wallet.balance == Σ loyalty_transactions.amount` — ricalcolabile sempre; job notturno di riconciliazione segnala drift in `admin_logs`.
3. `events.booked_count ≤ capacity` (transazione `bookEvent`).
4. Nessun punto accreditato senza `source` e riferimento; nessuna transazione modificata dopo la scrittura.
5. Un profilo figlio esiste solo con `consent_at` valorizzato.
6. Un round lotteria non può aprirsi senza `rules_url` (gate legale nel codice).

---
**Riepilogo TASK 6**: 26 entità modellate su Firestore con sicurezza default-deny, collezioni economiche scrivibili solo da Cloud Functions, unicità critiche ottenute con doc id compositi (like, booking, ticket), wallet event-sourced con riconciliazione notturna, e gate legale della lotteria codificato nell'invariante n. 6.
