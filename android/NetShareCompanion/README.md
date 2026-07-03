# 🗂️ NetShare Companion

App **companion** di [PhoneGuard](../PhoneGuard/) per accedere alle **cartelle
condivise in rete** (SMB/Samba: PC Windows, Linux, NAS) dal telefono.

## Perché è un'app separata

PhoneGuard è un'app anti-spyware e la sua credibilità si basa su una proprietà
precisa: **non ha il permesso INTERNET**, quindi non può inviare i tuoi dati da
nessuna parte. L'accesso alle cartelle di rete richiede invece il permesso
INTERNET.

Per non compromettere quella garanzia, la funzione di rete vive qui, in un'app
distinta e dichiaratamente "connessa": così la parte di sicurezza resta
sigillata e questa app fa una cosa sola, in modo trasparente.

## Cosa fa

- Si connette a una condivisione SMB2/SMB3 indicando **server + nome condivisione**.
- Autenticazione con **utente/password**, **dominio** o **accesso ospite**.
- **Naviga** tra cartelle e file, con dimensioni e risalita alla cartella superiore.
- **Scarica** i file nella cartella privata dell'app
  (`Android/data/com.cybersentinel.netshare/files/Download`).

## Tecnologia

- Client SMB: [smbj](https://github.com/hierynomus/smbj) (SMB 2/3, no NetBIOS legacy).
- Tutte le operazioni di rete su coroutine in background (mai sul thread UI).
- `usesCleartextTraffic` è attivo perché SMB sulla LAN non usa TLS: da usare
  solo su reti fidate.

## Compilazione

```bash
cd android/NetShareCompanion
gradle assembleRelease
# APK firmato in app/build/outputs/apk/release/app-release.apk
```

Requisiti: Android 8.0+ (API 26), target Android 14 (API 34).

## Uso

1. Apri l'app e inserisci l'indirizzo del server (es. `192.168.1.10`) e il nome
   della condivisione (es. `Documenti`).
2. Metti la spunta su "Accesso come ospite" oppure inserisci utente e password.
3. Tocca **Connetti**: naviga toccando le cartelle, tocca un file per scaricarlo.

## Limiti noti

- L'elenco automatico delle condivisioni disponibili non è implementato: va
  indicato il nome della condivisione.
- SMB1 (obsoleto e insicuro) non è supportato di proposito.
