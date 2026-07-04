# PhoneGuard 6.0 — Specifiche architetturali "Cyber-Dashboard"

Documento di architettura UI/UX e tecnica. Tutto ciò che è descritto qui è
**implementato nel codice** di questa cartella, salvo dove indicato come
*Roadmap* (con la motivazione tecnica).

---

## 0. Architettura di navigazione: menu ad hamburger, una pagina per funzione

Dalla v6.0 la navigazione non è più a Bottom Navigation/fragment condivisi:
è un **menu ad hamburger** (`DrawerLayout` + `NavigationView`) che raccoglie
ogni funzione per categoria, e **ogni funzione vive nella propria pagina**
(un'Activity dedicata), non più raggruppata in mega-schermate.

- **`MainActivity`** — shell col drawer; mostra sempre `DashboardFragment`
  come home. Ogni voce del drawer diversa da "Dashboard" apre una pagina.
- **`SectionHostActivity`** — host generico riusato da tutte le pagine
  "semplici": riceve una `Section` (enum con titolo + fabbrica del
  Fragment) e la mostra con Toolbar e freccia indietro. Evita di scrivere
  15 Activity quasi identiche.
- **`DeviceInfoActivity`, `SimInfoActivity`, `FileManagerActivity`** —
  Activity proprie perché hanno bisogno di più controlli in Toolbar
  (selettore volumi, vista lista/griglia, incolla) di quanti l'host
  generico preveda.

```
PhoneGuard (menu ad hamburger, ordinato per frequenza d'uso)
│
├── ⭐ Più usati                           (dinamico: le 5 funzioni aperte più
│      spesso, ricostruito a ogni apertura del drawer — sempre aggiornato)
│
├── 📊 Dashboard                           [DashboardFragment — sempre la home]
│   └── 🚀 Ottimizza il cellulare          ferma le app non di sistema/nascoste
│          attive in background (riusa BackgroundAppsMonitor)
│
├── 🛡️ Sicurezza (ordine: uso frequente → occasionale)
│   ├── 🔎 Verifica intercettazione        [InterceptionFragment] — verdetto aggregato +
│   │      codici USSD ufficiali di deviazione chiamata (*#21#, *#67#, *#62#, ##002#, *#06#)
│   ├── App sospette e nascoste            [ThreatsFragment] — anti-spyware, punteggio 0–100
│   ├── Affidabilità Wi-Fi                 [WifiFragment] — controllata a ogni nuova rete
│   ├── Traffico di rete per app           [NetworkUsageFragment] — TUTTE le app
│   │      (comprese quelle senza icona nel launcher), ordinate per byte
│   │      inviati, con badge "nascosta" e [Ferma] per app; + registro rete
│   │      in linguaggio semplice
│   ├── Audit permessi critici             [PermissionAuditFragment]
│   ├── Analisi di sistema                 [SystemAnalysisFragment] — root, MDM, VPN, ADB...
│   ├── Controllo file sospetti            [SuspiciousFilesFragment] — interna + microSD
│   └── App da aggiornare                  [UpdatesFragment] — [Aggiorna]→Play Store
│
├── ⚡ Energia e spazio
│   ├── App in background                 [RunningAppsFragment] — solo Foreground Service attivi, [Ferma]
│   ├── App energivore                     [EnergyUsageFragment]
│   ├── Pulizia spazio                     [CleanSpaceFragment] — selezione con checkbox
│   └── Inventario app                     [AppInventoryFragment] — spazio app/dati/cache
│
├── 🗂️ File
│   └── Gestione file                      [FileManagerActivity] — Esplora Windows-like,
│          vista lista/griglia, copia/taglia/incolla tra memoria interna e microSD
│
├── 📱 Dispositivo
│   ├── Informazioni dispositivo           [DeviceInfoActivity]
│   └── Schede SIM                         [SimInfoActivity]
│
└── ⚙️ App
    ├── Impostazioni                       [SettingsFragment] — ogni funzione attivabile singolarmente
    ├── Registro attività                  [LogFragment] — SQLite, Aggiorna/Svuota
    └── Informazioni app                   [AboutFragment] — versione, mese/anno
           di rilascio (calcolati da Gradle a ogni build) e cosa fa l'app
```

### Eliminazione della duplicazione di codice (revisione senior)

Con 15+ pagine che aprono le stesse schermate di sistema (scheda app,
Play Store, permesso storage...), la stessa logica `Intent` + `runCatching`
era ripetuta in 8+ file. Consolidata in **`util/SystemIntents`**: un unico
punto per `openAppDetails`, `openUsageAccessSettings`,
`requestAllFilesAccess`, `openNotificationSettings`, `uninstallApp`,
`openPlayStoreListing`, `dialUssd`. Ogni pagina ora chiama l'helper invece
di ridefinire l'`Intent`.

`FileRepository` (copia/taglia/incolla) è stata scomposta in **`FileOps`**,
un `object` puro senza alcuna dipendenza da Android (solo `java.io.File`):
`FileRepository` resta la facciata che aggiunge ciò che richiede un
`Context` (permesso di archiviazione, elenco dei volumi), ma la logica di
copia/spostamento/conflitti-di-nome è ora testabile con veri unit test JVM
su cartelle temporanee — 9 test in `FileOpsTest`, nessun dispositivo o
Robolectric necessario.

Rimossi anche: un parametro morto in `SecurityAnalyst.buildSummary`
(il conteggio delle app ad alto rischio veniva calcolato ma mai mostrato
nel messaggio — ora il messaggio lo riporta), un parametro `ConnectivityManager`
inutilizzato in `WifiAnalyzer`, e l'`onBackPressed()` deprecato in
`MainActivity`/`FileManagerActivity` sostituito con `OnBackPressedCallback`.

Le categorie del registro attività erano stringhe libere ("RETE", "SISTEMA"...)
ripetute in 7 file: un refuso in una sarebbe passato silenzioso, rompendo
i filtri per categoria senza errore. Consolidate in `LogCategory`
(costanti in `util/AppLog.kt`).

**Versione e data di rilascio**: `AboutFragment` legge `versionName`/
`versionCode` a runtime da `PackageInfo` (unica fonte di verità:
`build.gradle.kts`) invece di una stringa duplicata. Il mese/anno di
build è un `buildConfigField` calcolato da Gradle al momento della
compilazione (`SimpleDateFormat` in `build.gradle.kts`), non una data
scritta a mano che rischia di restare disallineata.

### Hardening di sicurezza (v6.5)

**Firma di rilascio**: `phoneguard.keystore` era tracciato in git e
`build.gradle.kts` conteneva le password del keystore in chiaro
(`"phoneguard2026"`) come valore di default — chiunque legga il repository
poteva firmare un APK spacciandolo per un aggiornamento legittimo di
PhoneGuard, minando l'unica garanzia di autenticità che l'utente ha per
un'app distribuita fuori dal Play Store. Il keystore è stato rimosso dal
tracking (`git rm --cached`, resta solo sul disco locale e in
`.gitignore`); le password ora si leggono da **`keystore.properties`**,
file locale mai versionato (vedi `keystore.properties.example` per il
formato), senza alcun default in chiaro nello script di build.
Nota: il keystore era presente in commit precedenti della cronologia; la
cronologia non è stata riscritta, quindi chi clona il repo lo trova ancora
nei commit passati.

**Gestione dei crash**: aggiunto `PhoneGuardApplication` (Application
custom) che installa `util/CrashHandler` in `onCreate()`. Cattura le
eccezioni non gestite, le scrive nel registro locale con categoria
`LogCategory.CRASH` (scrittura sincrona con `AppLog.logSync`, per non
perdere l'evento se il processo termina prima che una scrittura asincrona
sia completata) e poi passa la mano al gestore di sistema predefinito, così
il comportamento standard del crash (dialogo, riavvio) resta invariato.
Nessun dato lascia il dispositivo: la diagnosi resta consultabile solo
nella pagina Registro attività.

**Esenzione dalle ottimizzazioni batteria**: su molti produttori (Xiaomi,
Huawei, Samsung...) il sistema può terminare il servizio di monitoraggio
anche se è in foreground, se l'app non è nella lista delle eccezioni.
Aggiunto un pulsante di stato in Impostazioni (stesso pattern degli altri
permessi: ✅/❌ con azione per risolvere), che richiama
`SystemIntents.requestIgnoreBatteryOptimizations` — richiesta sempre con
conferma esplicita dell'utente, mai automatica.

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
| **Gestione file (copia/taglia/incolla)** | `FileRepository`: `File.copyTo`/cancellazione ricorsiva sui volumi da `FileScanner.storageRoots()`; conflitti risolti con suffisso " (2)" invece di sovrascrivere | Nessuna dipendenza esterna; vista lista/griglia tramite `LinearLayoutManager`/`GridLayoutManager` sullo stesso `RecyclerView` |
| **Registro rete in linguaggio semplice** | `MonitorService.logNetworkSummary()` ogni 15 min scrive in `AppLog` (categoria "RETE") i maggiori mittenti in linguaggio naturale | ⚠️ Android non espone contenuto/destinazione dei pacchetti a un'app senza VPN/root: il registro mostra solo byte inviati/ricevuti per app, dichiarato esplicitamente nel testo dell'app |
| **Verifica intercettazione** | Aggrega `ThreatScanner`, `SystemAnalyzer` (admin/accessibilità/notifiche/MDM), `CellNetworkMonitor` (downgrade a 2G via `TelephonyManager.dataNetworkType`), `WifiAnalyzer`; più `Intent.ACTION_DIAL` con i codici USSD ufficiali (*#21#/*#67#/*#62#/##002#) per la deviazione chiamate | ⚠️ Nessuna app senza privilegi di operatore/sistema può rilevare con certezza un IMSI-catcher o un'intercettazione di rete: sono indizi euristici, dichiarati come tali in pagina. I codici USSD interrogano la rete dell'operatore direttamente — è lui a rispondere, non una stima dell'app |
| **App nascoste che inviano dati + Ferma** | `AppVisibility.launcherPackages` (condiviso con `ThreatScanner`) marca le app senza icona nel launcher nella lista di `NetworkUsageFragment`; `BackgroundAppsMonitor.stop()` (già usato in "App in background") ferma qualunque app non di sistema dalla stessa lista | Ordinamento per byte inviati già garantito da `NetworkMonitor.queryUsage()` |
| **Menu "Più usati" in tempo reale** | `UsageTracker` (SharedPreferences: contatore per id di menu) incrementato a ogni navigazione in `MainActivity`; `NavigationView.menu.addSubMenu()`/`removeGroup()` ricostruiscono la sezione dinamica a ogni apertura del drawer (`DrawerLayout.DrawerListener.onDrawerOpened`) | Riusa titolo/icona della voce statica originale via `menu.findItem(id)`: nessuna tabella duplicata da mantenere |
| **Ottimizza il cellulare** | Riusa integralmente `BackgroundAppsMonitor.runningApps()` (già filtra le app di sistema) + `.stop()` su ciascuna | Stessa logica esatta della pagina "App in background": nessuna duplicazione |

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
