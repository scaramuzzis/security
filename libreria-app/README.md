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
    ├── firebase.json          # config + emulatori
    ├── .firebaserc            # alias dev/prod
    ├── firestore.rules        # default-deny (vedi docs/06)
    ├── firestore.indexes.json
    ├── storage.rules
    └── functions/             # TypeScript, Node 20
        └── src/index.ts       # onUserCreate, healthCheck
```

## Quick start (sviluppo)

```bash
cd libreria-app/firebase/functions && npm ci && npm run build
cd .. && firebase emulators:start   # richiede firebase-tools + Java 11+
```

Setup completo dei progetti Firebase e spike FluxBuilder: `docs/sprint-0-spike.md`.

## Stato

- ✅ Design completo (docs 01–09)
- 🔄 Sprint 0: config Firebase e functions pronte nel repo; spike FluxBuilder e creazione progetti da eseguire sull'account del committente
- ⏸ Sprint 1+ : dopo il go/no-go dello spike
