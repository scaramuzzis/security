# LibreriBrà — App mobile (Flux/FluxBuilder + Firebase)

App per la libreria per bambini e ragazzi LibreriBrà (San Giovanni, Roma): feed editoriale, eventi con prenotazione, catalogo e prenota-e-ritira, area giochi 6–12 anni, punti fedeltà e "Estrazione dei Lettori" (lotteria a punti, rilascio soggetto a parere legale).

> Questa cartella è indipendente dal resto del repository (AI Security Scanner).

## Struttura

```
libreria-app/
├── PROMPT-CLAUDE-CODE.md      # prompt operativo del progetto
├── docs/
│   ├── piano-strategico.md    # fonte di verità business
│   ├── 01-requirements.md     # …fino a…
│   ├── 09-build-plan.md       # design completo (TASK 1–9)
│   └── sprint-0-spike.md      # checklist go/no-go in corso
└── firebase/
    ├── firebase.json          # config + emulatori + hosting
    ├── .firebaserc            # alias dev/prod
    ├── firestore.rules        # default-deny (vedi docs/06)
    ├── firestore.indexes.json
    ├── storage.rules
    ├── backoffice/            # web app staff (Firebase Hosting)
    │   ├── index.html         # 6 sezioni: Feed·Libri·Eventi·Utenti·Loyalty·Report
    │   ├── app.js
    │   └── firebase-config.js # PLACEHOLDER: incollare la config reale
    ├── seed/seed.mjs          # dati di base per dev/emulatore
    ├── tests/                 # test security rules (24)
    └── functions/             # TypeScript, Node 20 — tutta la logica di dominio
        ├── src/               # loyalty, events, orders, catalog, feed, push,
        │                      # games, lottery
        └── test/              # test integrazione su emulatore (40)
```

## Quick start (sviluppo)

```bash
cd libreria-app/firebase/functions && npm ci && npm run build
cd .. && firebase emulators:start   # richiede firebase-tools + Java 11+
```

Setup completo dei progetti Firebase e spike FluxBuilder: `docs/sprint-0-spike.md`.

## Stato

- ✅ Design completo (docs 01–09)
- ✅ Backend completo (Sprint 1–5 + Fase 2): onboarding/GDPR, catalogo+CSV,
  wishlist, eventi con lista d'attesa, loyalty/coupon/scadenze, ordini,
  feed con moderazione, push FCM, giochi con cap famiglia, lotteria
  commit-reveal con gate legale — 24 test rules + 40 test integrazione
- ✅ Backoffice web per lo staff (`firebase/backoffice/`)
- ⏸ Da fare con gli account del committente: progetti Firebase + deploy
  (`docs/sprint-0-spike.md`), schermate FluxBuilder (`docs/sprint-1-flux-screens.md`),
  enrollment store, parere legale lotteria (senza il quale i round non si aprono)
