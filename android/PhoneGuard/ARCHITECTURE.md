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
│   ├── ▶ Analisi Globale                 (minacce + sistema + file + energia, un tap)
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
│   ├── Controllo file sospetti           [Scansiona file]
│   └── Traffico di rete per app          (↑ inviati / ↓ ricevuti, 24h)
│
├── ⚡ ENERGIA                             [EnergyFragment]
│   ├── Lista app energivore              (ordinata per impatto %, ultime 24h)
│   │   ├── badge ⚡ SERVIZIO IN BACKGROUND (Foreground Service rilevato)
│   │   └── [Disattiva / Limita] → scheda di sistema (Arresto forzato,
│   │       Disattiva per le app di sistema, restrizione batteria)
│   └── Richiesta permesso "Dati di utilizzo" se mancante
│
└── ⚙️ IMPOSTAZIONI                        [SettingsFragment]
    ├── Regole di automazione             (gli stessi 4 toggle della Dashboard)
    │   └── Nota soglie: 50 MB upload · 45 °C · 20%/h · ciclo 15 min
    └── Autorizzazioni di sistema         (stato ✅/❌ + scorciatoia alla schermata giusta)
        ├── Accesso ai dati di utilizzo
        ├── Accesso a tutti i file
        └── Notifiche
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
| Analisi Globale | orchestrazione coroutine dei 4 motori (`ThreatScanner`, `SystemAnalyzer`, `FileScanner`, `EnergyMonitor`) |

### 3.2 Energia & Consumi
| Funzione | API | Note di piattaforma |
|---|---|---|
| Tempo in primo piano per app | `UsageStatsManager.queryUsageStats(INTERVAL_DAILY, …)` | richiede `PACKAGE_USAGE_STATS` (concessione manuale) |
| Rilevamento Foreground Service | `UsageStatsManager.queryEvents` + `UsageEvents.Event.FOREGROUND_SERVICE_START` | disponibile da Android 10 |
| Avviso "app avviata da sola" | loop 60s su `queryEvents`: `FOREGROUND_SERVICE_START` senza `ACTIVITY_RESUMED` dell'utente nei 10 min precedenti → notifica heads-up (canale IMPORTANCE_HIGH) | de-duplica 1h per app; ⚠️ un vero popup overlay richiederebbe `SYSTEM_ALERT_WINDOW`, invasivo: l'heads-up è il pattern raccomandato |
| **Disabilitare/abilitare app** | deep-link scheda app (`ACTION_APPLICATION_DETAILS_SETTINGS`) | ⚠️ `setApplicationEnabledSetting` su altre app richiede `CHANGE_COMPONENT_ENABLED_STATE` (signature\|system): il pulsante "Disattiva" della scheda è l'unico canale consentito |
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
| Traffico per app | `NetworkStatsManager.querySummary` (Wi-Fi + mobile) |
| Disinstallazione | `Intent.ACTION_DELETE` + `REQUEST_DELETE_PACKAGES` (conferma di sistema) |
| Scansione file | `Environment.getExternalStorageDirectory` + `MANAGE_EXTERNAL_STORAGE` (Android 11+) |

### 3.4 Impostazioni
| Funzione | API |
|---|---|
| Persistenza regole di automazione | `SharedPreferences` (wrapper `Prefs`) |
| Deep-link autorizzazioni | `ACTION_USAGE_ACCESS_SETTINGS`, `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`, `ACTION_APP_NOTIFICATION_SETTINGS` |
| Servizio di monitoraggio | Foreground Service `dataSync` + `POST_NOTIFICATIONS` (Android 13+) |

---

## 4. Roadmap (funzioni del brief non implementabili senza componenti aggiuntivi)

| Funzione richiesta | Stato | Architettura prevista |
|---|---|---|
| Firewall di rete locale | Roadmap | `VpnService` locale che filtra i pacchetti per UID (come NetGuard): richiede il consenso VPN dell'utente e un engine di routing dedicato. |
| Protezione Web & SMS / Scam Guard | Roadmap | `NotificationListenerService` per estrarre i link dai messaggi + verifica reputazione. Nota di design: PhoneGuard oggi **non ha il permesso INTERNET** (garanzia privacy); un URL-checker online romperebbe questa proprietà, quindi andrebbe fatto con blocklist locale aggiornata al momento dell'installazione. |
| Profili termici | Roadmap | `PowerManager.addThermalStatusListener` (Android 10+) per reagire agli stati termici `THERMAL_STATUS_*`. |
| Grafico mAh in tempo reale | Roadmap | campionamento periodico `BATTERY_PROPERTY_CURRENT_NOW` persistito in Room + sparkline custom. |
