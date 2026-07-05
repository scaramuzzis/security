# Sprint 1 — Guida schermate FluxBuilder

Come cablare in FluxBuilder le schermate dello Sprint 1 sul backend già pronto nel repo. Prerequisito: spike S0 concluso con GO (vedi `sprint-0-spike.md`). Stile: applicare i token di `07-ux-design-system.md` (sfondo `#FAF5EC`, testo `#2B2118`, CTA `#C4572E`).

Backend disponibile (deployato da `firebase/`):
- Trigger: `onUserCreate` (crea `users/{uid}` + wallet), `onUserDelete` (cascata GDPR).
- Callable (region `europe-west1`): `healthCheck`, `completeOnboarding`, `updateNotificationPrefs`, `deleteAccount`.
- Dati seed: `store_locations/libreribra-sangiovanni`, 5 `event_categories`, 5 `games` (hidden).

## O1 — Splash / value proposition
- 3 slide illustrate, testi: "Il libro giusto in un minuto" / "Prenota laboratori ed eventi" / "Ogni visita vale punti".
- Bottoni: "Inizia" (→O2), "Entra come ospite" (→Home in sola lettura; ogni azione riproporrà il login).
- Analytics: `app_open` al lancio.

## O2 — Registrazione / Login
- Provider (in quest'ordine): Google, Apple, Email+link/OTP. Nessuna password custom.
- Alla prima registrazione il backend crea da solo utente+wallet: **non** creare documenti dal client (le rules lo negano).
- Errori: "Non riusciamo a contattare il servizio. Controlla la connessione e riprova." — mai codici.

## O3 — Profilo genitore
- Form → scrive su `profiles/{uid}` (unica scrittura client ammessa): `display_name` (obbligatorio, ≤60), `photo_url` (upload su Storage `/users/{uid}/avatar.jpg`, max 5 MB), `neighborhood` (opzionale, picker).
- ⚠️ Campi extra = scrittura respinta dalle rules: bind esattamente questi tre.
- La data di nascita del genitore (per il gate 18+ lotteria) si raccoglie qui ma si salva **via callable** nello Sprint 2 quando aggiungeremo `updateUserProfile`; per ora può restare non richiesta.

## O4 — Aggiungi figlio (skippabile)
- Form → `users/{uid}/children/{auto-id}`: `nickname`, `birth_year` (picker anni, range corrente−14…corrente), `interests` (chips da vocabolario chiuso), `avatar_id` (griglia avatar illustrati), `consent_at` = timestamp corrente valorizzato **solo** se la checkbox di consenso è spuntata.
- Testo consenso: "Confermo di essere il genitore o tutore e acconsento al trattamento di questi dati per personalizzare l'esperienza. [Informativa privacy]" — checkbox non preselezionata.
- Le rules rifiutano la creazione senza `consent_at`: se l'utente non spunta, disabilitare il salvataggio.
- Analytics: `child_profile_created` con param `age_band` (calcolata: 6-8/9-12/altra).

## O5 — Interessi e chiusura
- Chips interessi genitore → per ora solo analytics (la personalizzazione Home arriva nello Sprint 2).
- Al tap "Inizia a esplorare": invocare callable **`completeOnboarding`** → risposta `{ balance, welcomeBonusGranted }`.
- Se `welcomeBonusGranted: true`: toast "🎉 50 punti di benvenuto nel tuo wallet!" — la callable è idempotente, richiamarla non duplica i punti.
- Analytics: `onboarding_completed` con `children_count`, `interests_count`.

## P1 — Panoramica profilo
- Bind: `profiles/{uid}` (nome, foto) + `loyalty_wallet/{uid}` (`balance`, `tier`) in lettura.
- Pillola punti `#E9B44C` con saldo; righe menu → P2, P3 (placeholder), P8, S1.

## P2 — I miei figli
- Lista da `users/{uid}/children` (solo i propri: le rules isolano). Aggiungi (riusa il form O4), modifica (stesso form; `consent_at` non è modificabile — le rules lo bloccano), elimina con conferma.

## P8 — Impostazioni
- Toggle notifiche per categoria (`eventi`, `novita`, `promozioni`, `lotteria`): stato iniziale da `users/{uid}.notification_prefs`; al cambio → callable **`updateNotificationPrefs`** con `{ prefs: { <categoria>: bool } }`.
- "Elimina il mio account": doppia conferma ("Questa azione cancella profilo, figli, punti e cronologia. Non si può annullare.") → callable **`deleteAccount`** → logout locale. La cascata è server-side.
- Link: informativa privacy, regolamento loyalty (placeholder finché il legale non consegna).

## S1 — La libreria
- Bind: `store_locations/libreribra-sangiovanni` (lettura pubblica, funziona anche da ospite).
- Galleria `photos[]`; orari da `opening_hours` con banner "Oggi: {fasce}" calcolato dal giorno corrente; `closures[]` in evidenza se presenti.
- **Naviga**: bottone → `https://www.google.com/maps/dir/?api=1&destination={lat},{lng}` (si apre nell'app mappe di sistema su entrambe le piattaforme; su iOS in alternativa `maps://?daddr={lat},{lng}`).
- Contatti: tap-to-call su `phone`, link WhatsApp `https://wa.me/{numero}`, Instagram.
- Analytics: `store_navigate_tapped` sul bottone Naviga.
- ⚠️ I dati seed sono placeholder: **inserire indirizzo/orari/contatti reali dalla console prima di qualsiasi demo.**

## Criteri di accettazione Sprint 1 (da build plan)
- [ ] Onboarding completo in <2 minuti cronometrati su device reale.
- [ ] `completeOnboarding` chiamata due volte → i 50 punti compaiono una sola volta.
- [ ] Utente B non vede i figli di utente A (già garantito dalle rules: 21 test verdi in `firebase/tests/`).
- [ ] `deleteAccount` → dopo il logout, in console non restano `users/{uid}`, `profiles/{uid}`, wallet, figli.
- [ ] VoiceOver/TalkBack: flusso O2→O5 percorribile.
