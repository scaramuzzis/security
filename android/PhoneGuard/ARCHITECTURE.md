# PhoneGuard 3.0 — Specifiche architetturali "Cyber-Dashboard"

Documento di architettura UI/UX e tecnica. Tutto ciò che è descritto qui è
**implementato nel codice** di questa cartella, salvo dove indicato come
*Roadmap* (con la motivazione tecnica).

---

## 1. Struttura ad albero dei menu

```
PhoneGuard
│
├── 📊 DASHBOARD                          [DashboardFragment]
│   ├── Header di stato (semaforo)        🛡️ verde / ⚠️ ambra / 🚨 rosso
│   ├── Anelli di stato (RingGaugeView)   batteria, RAM, archiviazione — Canvas nativo
│   ├── Donut sicurezza (DonutChartView)  ripartizione OK/da verificare/critico + legenda
│   ├── ▶ Analisi Globale                 (minacce + sistema + file + energia, un tap)
│   │   └── Barra di avanzamento a 5 fasi con etichetta della fase corrente
│   ├── Card consumo PhoneGuard           (CPU% e RAM dell'app stessa, refresh 2s)
│   ├── Contatori: App sospette · Controlli falliti · File sospetti · Servizi attivi
│   ├── Card batteria in tempo reale      (livello, W, temperatura, autonomia stimata)
│   └── Controlli attivi (toggle)
│       ├── Monitoraggio continuo         [ON/OFF → avvia/ferma MonitorService]
│       ├── Avvisi batteria               [ON/OFF]
│       ├── Scansione automatica file     [ON/OFF]
│       └── Avviso app avviate da sole    [ON/OFF → popup heads-up]
│
├── 🛡️ SICUREZZA                          [SecurityFragment]
│   ├── App sospette e nascoste           (anti-spyware, punteggio 0–100)
│   │   └── per ogni app: [Disinstalla] [Info app]
│   ├── Audit permessi critici            (Accessibilità / Admin / Notifiche / Posizione background)
│   ├── Analisi di sistema                (root, MDM, VPN, ADB, blocco schermo, ...)
│   ├── Affidabilità Wi-Fi                (cifratura, proxy, captive portal, DNS, VPN)
│   ├── Controllo file sospetti           [Scansiona file: interna + microSD]
│   ├── App da aggiornare                 (ordinate per ultimo agg., [Aggiorna]→Play Store)
│   ├── Inventario app e sistema          (tutte le app per spazio: app/dati/cache)
│   └── Traffico di rete per app          (↑ inviati / ↓ ricevuti, 24h)
│
├── ⚡ ENERGIA                             [EnergyFragment]
│   ├── App attive in background          (servizi attivi + uso recente)
│   │   └── [Ferma] → killBackgroundProcesses; riga → Arresto forzato
│   ├── Lista app energivore              (ordinata per impatto %, ultime 24h)
│   │   ├── badge ⚡ SERVIZIO IN BACKGROUND (Foreground Service rilevato)
│   │   └── [Gestisci] → scheda di sistema (Arresto forzato, restrizione batteria)
│   └── Pulizia spazio                    (selezione con checkbox di cosa eliminare)
│       ├── cartelle vuote, file vuoti/temporanei, cache miniature, log/backup
│       ├── cache dell'app PhoneGuard (eliminabile direttamente)
│       └── [Seleziona tutto] + [Pulisci N selezionati · dimensione]
│
└── ⚙️ IMPOSTAZIONI                        [SettingsFragment]
    ├── Pannello di controllo completo    (ogni funzione attivabile singolarmente):
    │   ├── Monitoraggio continuo (servizio)
    │   ├── Scansione anti-spyware in background
    │   ├── Avvisi traffico dati sospetto
    │   ├── Avvisi batteria
    │   ├── Scansione automatica file
    │   └── Avviso app avviate da sole
    │   └── Nota soglie: 50 MB upload · 45 °C · 20%/h · ciclo 15 min
    ├── Autorizzazioni di sistema         (stato ✅/❌ + scorciatoia alla schermata giusta)
    │   ├── Accesso ai dati di utilizzo
    │   ├── Accesso a tutti i file
    │   └── Notifiche
    ├── 📱 Informazioni dispositivo        (Activity dedicata: Android, patch,
    │   hardware, memoria, batteria, cifratura, root, conteggio app)
    ├── 📶 Schede SIM                      (Activity dedicata: operatore, numero,
    │   rete, SIM predefinita dati/voce/SMS, roaming — sola lettura + deep-link
    │   alle impostazioni di sistema per modificarle)
    └── Registro attività (log)           [ON/OFF] + visualizzazione, Aggiorna, Svuota
        └── database SQLite normalizzato (tabelle `category` + `event`),
            scritture su thread dedicato, letture con LIMIT (memoria
            costante), rotazione a 1000 righe; categorie: SERVIZIO,
            AVVISO, SCANSIONE, PULIZIA, SISTEMA
```

---

## 2. Mockup di layout

### 2.1 Dashboard (implementata in `res/layout/fragment_dashboard.xml`)

```
┌─────────────────────────────────────────┐
│  NestedScrollView (sfondo #0E141A)      │
│  ┌───────────────────────────────────┐  │
│  │            🛡️  (52sp)             │  │  ← Header Stato
│  │      Protetto e ottimizzato       │  │    colore dinamico:
│  │   Nessuna minaccia rilevata       │  │    #00E676 / #FFB300 / #FF5252
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │       🔍 ANALISI GLOBALE          │  │  ← Quick Action (56dp, primary)
│  └───────────────────────────────────┘  │
│  ┌───────────────┐  ┌────────────────┐  │
│  │      0        │  │       2        │  │  ← Griglia contatori
│  │ App sospette  │  │ Controlli fall.│  │    (view_counter.xml × 4)
│  └───────────────┘  └────────────────┘  │
│  ┌───────────────┐  ┌────────────────┐  │
│  │      1        │  │       3        │  │
│  │ File sospetti │  │ Servizi attivi │  │
│  └───────────────┘  └────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │ 🔋 35% (in scarica)  🌡️ 31.2 °C   │  │  ← Card consumo tempo reale
│  │ ⚡ 1.24 W   ⏳ ~4h 20m rimanenti   │  │    (BatteryManager)
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │ Controlli attivi                  │  │  ← Toggle automazioni
│  │ Monitoraggio continuo      [●━━]  │  │    (MaterialSwitch → Prefs)
│  │ Avvisi batteria            [●━━]  │  │
│  │ Scansione automatica file  [●━━]  │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
│ [📊 Dashboard][🛡️ Sicurezza][⚡][⚙️]     │  ← BottomNavigationView
└─────────────────────────────────────────┘
```

### 2.2 Energia / Gestione Consumi (implementata in `fragment_energy.xml` + `item_energy.xml`)

```
┌─────────────────────────────────────────┐
│ App energivore (ultime 24 ore)          │
│ Impatto stimato dal tempo di utilizzo…  │  ← header esplicativo
│ ┌─────────────────────────────────────┐ │
│ │ [icona] WhatsApp                    │ │
│ │         2h 15m in primo piano · 18% │ │  ← impatto % sul totale
│ │         ⚡ SERVIZIO IN BACKGROUND    │ │  ← badge FGS (ambra)
│ │                          [Gestisci] │ │  ← deep-link scheda app
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │ [icona] Instagram                   │ │
│ │         1h 40m in primo piano · 13% │ │
│ │                          [Gestisci] │ │
│ └─────────────────────────────────────┘ │
│                  …                      │
└─────────────────────────────────────────┘
```

**Perché "Gestisci" e non "Forza Arresto" diretto:** vedi §3.2.

---

## 3. API Android native per modulo

### 3.1 Dashboard
| Funzione | API |
|---|---|
| Livello, stato carica, temperatura, salute | Broadcast sticky `ACTION_BATTERY_CHANGED` |
| Corrente istantanea (µA) e carica residua (µAh) | `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW`, `BATTERY_PROPERTY_CHARGE_COUNTER` |
| Potenza stimata (W) | corrente × tensione (calcolo in `BatterySnapshot.estimatedWatts`) |
| Autonomia stimata | carica residua ÷ corrente di scarica |
| Analisi Globale | orchestrazione coroutine a 5 fasi (`ThreatScanner`, `SystemAnalyzer`, `FileScanner`, `EnergyMonitor`+`JunkScanner`, `SecurityAnalyst`) |
| Referto AI on-device | `SecurityAnalyst` (motore di regole locale) aggrega tutti i risultati e produce verdetto + raccomandazioni. Nessuna chiamata di rete: l'app non ha `INTERNET` |
| Inventario app + spazio | `StorageStatsManager.queryStatsForPackage` (appBytes/dataBytes/cacheBytes) — usa il permesso "Dati di utilizzo" già concesso |
| App da aggiornare | `PackageInfo.lastUpdateTime` + origine `getInstallSourceInfo`; ⚠️ Android non espone la disponibilità di aggiornamenti alle app di terze parti, quindi si ordina per anzianità e si apre `market://details?id=` per l'update reale |
| Pulizia cache app propria | `context.cacheDir`/`externalCacheDir` svuotabili direttamente; ⚠️ la cache di *altre* app non è cancellabile via API (Android 8+): resta il deep-link alla scheda di sistema |
| Pulizia file inutili | scansione multi-volume + `File.delete` limitato ai volumi noti; ⚠️ la cache di *altre* app non è cancellabile via API (Android 8+): il canale è la scheda di sistema |

### 3.2 Energia & Consumi
| Funzione | API | Note di piattaforma |
|---|---|---|
| Tempo in primo piano per app | `UsageStatsManager.queryUsageStats(INTERVAL_DAILY, …)` | richiede `PACKAGE_USAGE_STATS` (concessione manuale) |
| Rilevamento Foreground Service | `UsageStatsManager.queryEvents` + `UsageEvents.Event.FOREGROUND_SERVICE_START` | disponibile da Android 10 |
| Avviso "app avviata da sola" | loop 60s su `queryEvents`: `FOREGROUND_SERVICE_START` senza `ACTIVITY_RESUMED` dell'utente nei 10 min precedenti → notifica heads-up (canale IMPORTANCE_HIGH) | de-duplica 1h per app; ⚠️ un vero popup overlay richiederebbe `SYSTEM_ALERT_WINDOW`, invasivo: l'heads-up è il pattern raccomandato |
| **Disabilitare/abilitare app** | deep-link scheda app (`ACTION_APPLICATION_DETAILS_SETTINGS`) | ⚠️ `setApplicationEnabledSetting` su altre app richiede `CHANGE_COMPONENT_ENABLED_STATE` (signature\|system): il pulsante "Disattiva" della scheda è l'unico canale consentito |
| **App in background + Ferma** | rilevamento via `UsageEvents` (FGS start/stop) + uso recente; stop con `ActivityManager.killBackgroundProcesses` (permesso `KILL_BACKGROUND_PROCESSES`) | ⚠️ best-effort: il sistema può riavviare i processi; per la chiusura definitiva resta l'Arresto forzato nella scheda app |
| **Informazioni dispositivo** | `Build.*`, `Build.VERSION.SECURITY_PATCH`, `ActivityManager.MemoryInfo`, `StatFs`, `DevicePolicyManager.storageEncryptionStatus`, `DisplayMetrics` | tutto locale, nessun dato inviato |
| **Schede SIM** | `SubscriptionManager.getActiveSubscriptionInfoList`, `getDefaultData/Voice/SmsSubscriptionId`, `TelephonyManager.createForSubscriptionId().dataNetworkType` | ⚠️ `MODIFY_PHONE_STATE` (cambiare SIM predefinita, roaming) è riservato alle app di sistema/operatore: la pagina è di sola lettura con deep-link a `ACTION_NETWORK_OPERATOR_SETTINGS`/`ACTION_WIRELESS_SETTINGS` per modificare |
| Grafici Dashboard | `RingGaugeView`/`DonutChartView`: View custom disegnate su `Canvas`, nessuna libreria di charting esterna | coerente con la filosofia "poche dipendenze" dell'app |
| **Forza Arresto** | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` (deep-link) | ⚠️ `FORCE_STOP_PACKAGES` è un permesso *signature\|system*: nessuna app di terze parti può arrestare un altro processo direttamente. Il pulsante di sistema è nella scheda app. |
| **Restrizione background** | stesso deep-link | ⚠️ `setAppStandbyBucket`/restrizione batteria sono API di sistema; la scelta "Con restrizioni / Ottimizzata / Senza restrizioni" è riservata all'utente nella scheda app. |
| Consumo reale in mAh per app | — | ⚠️ `BatteryStatsManager` richiede il permesso di sistema `BATTERY_STATS`: si usa il tempo di primo piano come proxy documentato. |
| WakeLock per app | — | ⚠️ non esposto alle app non-system; il rilevamento FGS è il segnale osservabile equivalente. |

### 3.3 Sicurezza
| Funzione | API |
|---|---|
| Enumerazione app + permessi concessi | `PackageManager.getInstalledApplications`, `GET_PERMISSIONS`, `REQUESTED_PERMISSION_GRANTED` (visibilità completa via `QUERY_ALL_PACKAGES`) |
| App nascoste dal launcher | `queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)` per differenza |
| Origine installazione (sideload) | `PackageManager.getInstallSourceInfo` (API 30+) / `getInstallerPackageName` |
| Admin del dispositivo / MDM | `DevicePolicyManager.activeAdmins`, `isDeviceOwnerApp`, `isProfileOwnerApp` |
| Servizi accessibilità / listener notifiche | `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, `enabled_notification_listeners` |
| VPN attiva | `ConnectivityManager.getNetworkCapabilities` + `TRANSPORT_VPN` |
| Affidabilità Wi-Fi | `WifiInfo.getCurrentSecurityType` (API 31+), `NET_CAPABILITY_VALIDATED`/`CAPTIVE_PORTAL`, proxy da `Settings.Global.HTTP_PROXY`, DNS privato da `private_dns_mode`; SSID richiede `ACCESS_FINE_LOCATION` |
| Traffico per app | `NetworkStatsManager.querySummary` (Wi-Fi + mobile) |
| Disinstallazione | `Intent.ACTION_DELETE` + `REQUEST_DELETE_PACKAGES` (conferma di sistema) |
| Scansione file (interna + microSD) | `Environment.getExternalStorageDirectory` per la memoria interna; radici dei volumi rimovibili ricavate da `Context.getExternalFilesDirs` (segmento prima di `/Android/`); lettura con `MANAGE_EXTERNAL_STORAGE` (Android 11+) |
| Consumo dell'app stessa | `Process.getElapsedCpuTime` (delta CPU/tempo reale) + `ActivityManager.getProcessMemoryInfo` (PSS) — consentiti senza permessi sul proprio processo |

### 3.4 Impostazioni
| Funzione | API |
|---|---|
| Persistenza regole di automazione | `SharedPreferences` (wrapper `Prefs`) |
| Deep-link autorizzazioni | `ACTION_USAGE_ACCESS_SETTINGS`, `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`, `ACTION_APP_NOTIFICATION_SETTINGS` |
| Servizio di monitoraggio | Foreground Service `dataSync` + `POST_NOTIFICATIONS` (Android 13+) |

---

## 4. Falsi positivi corretti (revisione senior)

Durante lo sviluppo sono stati identificati e corretti tre difetti che
avrebbero generato allarmi ingiustificati, colpendo in particolare il
pubblico naturale di un'app di sicurezza (utenti privacy-conscious):

| Difetto | Impatto reale | Correzione |
|---|---|---|
| `installer == null` trattato come "sideload" (ThreatScanner + SystemAnalyzer) | Ogni app installata via ADB o ripristinata da backup al cambio telefono (scenario comune) veniva segnalata come sospetta | `installer == null` ora è neutro; si segnala solo un installer *presente ma non riconosciuto* |
| F-Droid/Aurora Store assenti dagli installer fidati | Utenti che scelgono store open-source (il profilo tipico di chi installa un anti-spyware) vedevano le proprie app segnalate | Aggiunti `org.fdroid.fdroid` e `com.aurora.store` a `TRUSTED_INSTALLERS`, con test di regressione |
| VPN attiva sempre segnalata come "attenzione" | Chi usa una VPN di proposito (privacy, lavoro) vedeva un falso allarme costante | Il controllo VPN è ora informativo (`ok=true`), non conta più come controllo fallito nel verdetto |
| Servizi di accessibilità/notifiche di sistema segnalati come "verifica se ti fidi" | TalkBack e altri servizi di accessibilità di sistema (usati da utenti con disabilità) comparivano come app da controllare | Filtrati i pacchetti con `FLAG_SYSTEM` prima di generare l'avviso |
| "App attive in background" includeva app usate negli ultimi 30 minuti | Un'app appena chiusa dall'utente compariva con un pulsante "Ferma", inducendo a chiuderla senza motivo | Il segnale "uso recente" è stato rimosso: resta solo il Foreground Service genuinamente attivo |
| Pulizia file: preselezione automatica di tutto, inclusi `.log`/`.bak` | Rischio di cancellare backup intenzionali dell'utente con un solo tocco | I file di log/backup non sono più preselezionati di default (restano comunque selezionabili) |

## 5. Roadmap (funzioni del brief non implementabili senza componenti aggiuntivi)

| Funzione richiesta | Stato | Architettura prevista |
|---|---|---|
| Firewall di rete locale | Roadmap | `VpnService` locale che filtra i pacchetti per UID (come NetGuard): richiede il consenso VPN dell'utente e un engine di routing dedicato. |
| Protezione Web & SMS / Scam Guard | Roadmap | `NotificationListenerService` per estrarre i link dai messaggi + verifica reputazione. Nota di design: PhoneGuard oggi **non ha il permesso INTERNET** (garanzia privacy); un URL-checker online romperebbe questa proprietà, quindi andrebbe fatto con blocklist locale aggiornata al momento dell'installazione. |
| Profili termici | Roadmap | `PowerManager.addThermalStatusListener` (Android 10+) per reagire agli stati termici `THERMAL_STATUS_*`. |
| Grafico mAh in tempo reale | Roadmap | campionamento periodico `BATTERY_PROPERTY_CURRENT_NOW` persistito in Room + sparkline custom. |
