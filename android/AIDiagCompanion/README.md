# 🩺 Diagnostica IA (companion)

App **companion** di [PhoneGuard](../PhoneGuard/): incrocia nel tempo
batteria, traffico dati e permessi privilegiati, e ne chiede una sintesi in
linguaggio semplice a un modello IA (facoltativo).

## Perché è un'app separata

PhoneGuard non ha e non avrà mai il permesso INTERNET. Chiamare un modello
IA nel cloud richiede INTERNET. Per non compromettere la garanzia di
PhoneGuard, questa funzione vive qui, in un'app dedicata e dichiaratamente
"connessa".

## Cosa fa davvero (e cosa no)

**Non introduce nuove misurazioni**: legge le stesse API di sistema di
PhoneGuard (BatteryManager, NetworkStatsManager, DevicePolicyManager,
Settings.Secure), in modo indipendente perché le due app sono sandboxate
separatamente e non condividono codice o dati.

**Il valore aggiunto è la correlazione nel tempo**, non il singolo dato:

- Un servizio di campionamento in background (ogni 15 minuti) registra
  batteria/temperatura/schermo, traffico dati per app e permessi
  privilegiati concessi (amministratore dispositivo, accessibilità, lettura
  notifiche), in un archivio SQLite locale.
- "Esegui controllo iniziale" incrocia questa cronologia per individuare
  pattern che un controllo puntuale non vedrebbe: surriscaldamento
  *ripetuto* a schermo spento (non un picco isolato), app che inviano dati
  *con regolarità* a schermo spento (non un burst singolo), permessi
  privilegiati concessi *di recente*, e le combinazioni fra questi segnali
  (es. un'app che ha ottenuto un permesso privilegiato di recente E invia
  dati di notte è un segnale più forte dei due presi separatamente).
- Il **report grezzo** (regole esplicite, verificabili) è sempre mostrato,
  con o senza IA configurata: l'attendibilità dell'app non dipende dal
  servizio esterno.
- Se è configurata una API key, la stessa cronologia viene anche riassunta
  in linguaggio naturale da un modello IA (default: Claude, `claude-sonnet-5`),
  con istruzioni esplicite a non essere allarmista e a citare spiegazioni
  innocue quando plausibili.

## Limite onesto e dichiarato

**Nessun controllo qui — IA compresa — può rilevare un impianto a livello
di kernel/root che manipoli questi stessi dati prima che vengano letti.**
Se il sistema operativo è compromesso a un livello sufficientemente
profondo, può falsificare in modo coerente sia i dati grezzi sia (di
conseguenza) qualunque sintesi, IA o regola che sia, costruita sopra di
essi. Questo vale per qualunque analisi fatta in user-space, senza
eccezioni: l'IA aiuta a **interpretare meglio segnali genuini**, non a
vedere oltre ciò che Android espone onestamente a un'app senza privilegi
di sistema.

Per un sospetto fondato di sorveglianza sofisticata, lo strumento corretto
resta [MVT (Mobile Verification Toolkit)](https://github.com/mvt-project/mvt)
di Amnesty International, che analizza un backup offline — non un'app che
gira sul dispositivo stesso.

## Sicurezza della API key

Salvata cifrata tramite `EncryptedSharedPreferences` (Android Keystore),
mai in chiaro su disco né trasmessa se non al servizio IA configurato.

## Compilazione

```bash
cd android/AIDiagCompanion
gradle assembleRelease
# APK firmato in app/build/outputs/apk/release/app-release.apk
```

Requisiti: Android 8.0+ (API 26), target Android 14 (API 34).

## Uso

1. Concedi l'accesso ai dati di utilizzo (per l'analisi del traffico dati).
2. Attiva "Monitoraggio in background": più a lungo resta attivo, più
   affidabile diventa l'analisi (i pattern richiedono cronologia).
3. (Facoltativo) Inserisci una API key per la sintesi in linguaggio naturale.
4. Tocca "Esegui controllo iniziale": il report grezzo compare subito, la
   sintesi IA (se configurata) sopra di esso.
