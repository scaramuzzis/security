/**
 * Configurazione Firebase del backoffice.
 * Sostituire i PLACEHOLDER con i valori reali:
 * Console Firebase → Impostazioni progetto → Le tue app → Web app.
 *
 * Con USE_EMULATORS=true il backoffice si collega agli emulatori locali
 * (firebase emulators:start) invece che al progetto cloud.
 */
export const firebaseConfig = {
  apiKey: "PLACEHOLDER",
  authDomain: "libreribra-dev.firebaseapp.com",
  projectId: "libreribra-dev",
  storageBucket: "libreribra-dev.appspot.com",
  messagingSenderId: "PLACEHOLDER",
  appId: "PLACEHOLDER",
};

export const USE_EMULATORS =
  location.hostname === "localhost" || location.hostname === "127.0.0.1";
