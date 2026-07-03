# 📱 PhoneGuard — Monitoraggio sicurezza del cellulare

App Android (Kotlin) del team **Cyber Sentinel** che controlla il telefono per rilevare
comportamenti sospetti:

- **📡 Traffico dati per app** — quali app inviano e ricevono dati (Wi-Fi + rete mobile),
  con evidenza delle app che *inviano* troppi dati verso l'esterno (possibile esfiltrazione).
- **🔋 Consumo batteria** — livello, temperatura, salute, corrente e potenza stimata,
  con avvisi in caso di surriscaldamento o scarica anomala (possibile app malevola attiva
  in background).
- **🗂️ Controllo file sospetti** — scansione della memoria condivisa alla ricerca di
  APK fuori store, file con doppia estensione (es. `fattura.pdf.apk`), eseguibili e
  script nascosti; il servizio in background segnala i nuovi file sospetti appena compaiono.
- **🔎 Analisi di sistema** — rilevamento di segni di root (binari `su`, Magisk,
  build test-keys), debug USB attivo, opzioni sviluppatore, assenza di blocco schermo
  e app installate fuori dagli store ufficiali (sideload).

## Come funziona

| Componente | Ruolo |
|---|---|
| `NetworkMonitor` | Legge le statistiche di rete per-app tramite `NetworkStatsManager` (byte inviati ↑ e ricevuti ↓ per ogni UID/app) |
| `BatteryMonitor` | Legge lo stato della batteria tramite `BatteryManager` e il broadcast `ACTION_BATTERY_CHANGED` |
| `FileScanner` | Scansiona la memoria condivisa (profondità e numero di file limitati) segnalando APK, doppie estensioni ed eseguibili nascosti |
| `SystemAnalyzer` | Controlla root, debug USB, opzioni sviluppatore, blocco schermo e app sideload |
| `MonitorService` | Servizio in foreground che ogni **15 minuti** ricontrolla rete, batteria e nuovi file sospetti e invia una **notifica di avviso** quando rileva un'anomalia |
| `MainActivity` | Dashboard: stato batteria, analisi di sistema, scansione file e classifica delle app per dati inviati nelle ultime 24 ore |

### Regole di allerta (personalizzabili in `MonitorService.kt`)

| Controllo | Soglia predefinita |
|---|---|
| Upload di un'app in un intervallo | > 50 MB **e** upload > download → notifica "traffico sospetto" |
| Temperatura batteria | ≥ 45 °C → notifica surriscaldamento |
| Velocità di scarica (senza caricabatterie) | ≥ 20 %/ora → notifica consumo anomalo |

## Requisiti

- Android Studio (Hedgehog o successivo)
- Android 8.0+ (API 26), target Android 14 (API 34)

## Compilazione e installazione

1. Apri Android Studio → **Open** → seleziona la cartella `android/PhoneGuard`
   (al primo avvio Android Studio genera automaticamente il Gradle wrapper).
2. Collega il telefono con il **debug USB** attivo (oppure usa un emulatore).
3. Premi **Run ▶** per installare l'app.

In alternativa da terminale (con Android SDK configurato):

```bash
cd android/PhoneGuard
gradle assembleDebug
# APK generato in app/build/outputs/apk/debug/app-debug.apk
```

## Permessi da concedere al primo avvio

1. **Notifiche** — richiesto automaticamente all'avvio (Android 13+).
2. **Accesso ai dati di utilizzo** — necessario per leggere il traffico per-app:
   tocca il pulsante *"Concedi accesso ai dati di utilizzo"* nell'app, poi attiva
   **PhoneGuard** nell'elenco (Impostazioni → App con accesso ai dati di utilizzo).
3. **Accesso a tutti i file** — necessario per la scansione dei file sospetti
   (Android 11+): tocca *"Concedi accesso ai file"* nella sezione dedicata e
   attiva l'interruttore per PhoneGuard.

Senza questi permessi l'app mostra comunque batteria e analisi di sistema, ma non
può leggere il traffico di rete delle altre app né scansionare la memoria.

## Privacy

Tutti i dati restano **sul dispositivo**: l'app non ha il permesso `INTERNET`,
quindi non può inviare nulla all'esterno — è solo un osservatore locale.

## Limiti noti

- Le statistiche di `NetworkStatsManager` sono aggregate dal sistema con una
  granularità di circa 2 ore per i dati storici; i controlli periodici del
  servizio usano finestre più ampie per compensare.
- Su alcuni dispositivi (Xiaomi, Huawei, ecc.) il risparmio energetico aggressivo
  può terminare il servizio in background: aggiungi PhoneGuard alle app protette.
