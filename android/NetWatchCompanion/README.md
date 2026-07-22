# 🌐 NetWatch Companion

App **companion** di [PhoneGuard](../PhoneGuard/) per vedere **quali domini
contattano le altre app installate** — la parte di "traffico di rete" che
PhoneGuard non può mostrare da sé (vede solo quanti byte, non verso dove).

## Perché è un'app separata

PhoneGuard è un'app anti-spyware e la sua credibilità si basa su una proprietà
precisa: **non ha il permesso INTERNET**, quindi non può inviare i tuoi dati da
nessuna parte. Vedere le destinazioni di rete richiede invece un servizio VPN
locale che, per inoltrare le interrogazioni DNS a un resolver reale, ha
bisogno del permesso INTERNET.

Per non compromettere quella garanzia, questa funzione vive qui, in un'app
distinta e dichiaratamente "connessa": così la parte di sicurezza resta
sigillata e questa app fa una cosa sola, in modo trasparente.

## Cosa fa

- Attiva una `VpnService` **locale** (non tunnellizza il traffico verso un
  server remoto): instrada solo le interrogazioni DNS verso questa app,
  registra quale dominio ha chiesto di risolvere ciascuna app tramite
  `ConnectivityManager.getConnectionOwnerUid`, poi le inoltra a un resolver
  reale (Cloudflare 1.1.1.1) e restituisce la risposta — il resto del
  traffico dell'app continua a funzionare come sempre, senza passare da qui.
- Mostra un elenco in tempo reale (app, dominio, orario), consultabile e
  svuotabile dall'app.

## Perché solo DNS, non tutto il traffico

Instradare **tutti** i pacchetti (0.0.0.0/0) richiederebbe reimplementare un
proxy TCP/IP completo (stato delle connessioni, riassemblaggio TCP...) per
non rompere la connessione a internet dell'utente — settimane di lavoro per
un'app di questa scala. Instradando solo l'indirizzo DNS fittizio configurato
dalla VPN, un bug in questo codice nel caso peggiore impedisce la
risoluzione di un nome — mai la connettività reale del telefono.

## Limiti onesti

- Le app che usano **DNS-over-HTTPS/TLS proprio** (bypassando il resolver di
  sistema — alcuni browser, alcune app aggiornate) non compaiono nel log:
  le loro interrogazioni non passano da questa VPN.
- Solo **IPv4**: le interrogazioni DNS su IPv6 non sono intercettate.
- Mentre la protezione è attiva, le query DNS passano da **Cloudflare
  (1.1.1.1)** invece che dal DNS del tuo operatore/router: se la tua rete
  locale risolve nomi interni (NAS, stampanti), potrebbero non funzionare
  finché disattivi la protezione.
- Il registro è **solo in memoria**: si azzera se l'app o il servizio
  vengono terminati (nessuna cronologia persistita su disco).
- Vedere un dominio non è prova di nulla di per sé: molte app legittime
  contattano decine di domini (analytics, CDN, pubblicità). Serve giudizio,
  non un verdetto automatico.

## Compilazione

```bash
cd android/NetWatchCompanion
gradle assembleRelease
# APK firmato in app/build/outputs/apk/release/app-release.apk
```

Requisiti: Android 10+ (API 29, richiesto da `getConnectionOwnerUid`), target
Android 14 (API 34).

## Uso

1. Apri l'app e tocca **Avvia protezione**: Android chiederà conferma per la
   VPN locale (comparirà l'icona a forma di chiave nella barra di stato).
2. Usa il telefono normalmente: l'elenco si popola con i domini richiesti da
   ogni app, aggiornato ogni 2 secondi.
3. Tocca **Ferma protezione** per disattivare, **Svuota** per azzerare l'elenco.
