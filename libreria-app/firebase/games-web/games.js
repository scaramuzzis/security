/**
 * LibreriBrà — webview giochi (prototipo spike S0-6, giochi F3 + F4).
 * Modalità: con ?token=<customToken>&childId=... si collega a Firebase e
 * registra la sessione (punti veri, cap famiglia lato server);
 * senza parametri gira in DEMO (contenuti locali, nessun punto).
 * Regole modalità bambino: touch ≥64pt, niente timer 6-8, niente game over,
 * gate di uscita "tieni premuto 3 secondi", nessun link esterno.
 */
"use strict";

const params = new URLSearchParams(location.search);
const CTX = {
  token: params.get("token"),
  childId: params.get("childId"),
  ageBand: params.get("ageBand") === "9-12" ? "9-12" : "6-8",
  name: params.get("name") || "",
};
const DEMO = !CTX.token;
let firebaseApi = null; // { completeSession(gameId, durationS, score) }
let sessionStart = 0;
let currentGame = null;

// ---------- Contenuti di esempio (in produzione: quiz_questions/puzzle_assets) ----------
const QUESTIONS = {
  "6-8": [
    { q: "Quante zampe ha un ragno?", opts: ["Sei", "Otto", "Quattro", "Dieci"], ok: 1,
      why: "Otto zampe! Per questo i ragni non sono insetti: gli insetti ne hanno sei. 🕷️" },
    { q: "Di che colore diventa la foglia in autunno?", opts: ["Blu", "Rosa", "Arancione", "Bianca"], ok: 2,
      why: "In autunno le foglie si tingono di arancione, rosso e giallo. 🍂" },
    { q: "Dove vive il polpo?", opts: ["Nel bosco", "Nel mare", "Nel deserto", "Sulle nuvole"], ok: 1,
      why: "Nel mare! E lo sapevi? Il polpo ha tre cuori. 🐙" },
    { q: "Cosa usa l'ape per fare il miele?", opts: ["Il nettare dei fiori", "Le foglie", "La pioggia", "La sabbia"], ok: 0,
      why: "Le api raccolgono il nettare dai fiori e lo trasformano in miele. 🐝" },
    { q: "Qual è il pianeta dove viviamo?", opts: ["Marte", "La Luna", "La Terra", "Giove"], ok: 2,
      why: "La Terra, il nostro pianeta blu! 🌍" },
    { q: "Che verso fa il gufo?", opts: ["Muuu", "Uuuh uuuh", "Coccodè", "Bau"], ok: 1,
      why: "Uuuh uuuh! Il gufo canta di notte, quando tutti dormono. 🦉" },
  ],
  "9-12": [
    { q: "Chi scrisse 'Pinocchio'?", opts: ["Gianni Rodari", "Carlo Collodi", "Italo Calvino", "Roald Dahl"], ok: 1,
      why: "Carlo Collodi lo pubblicò nel 1883: il burattino più famoso del mondo è nato in Toscana!" },
    { q: "Qual è il fiume più lungo d'Italia?", opts: ["Il Tevere", "L'Arno", "Il Po", "L'Adige"], ok: 2,
      why: "Il Po: 652 chilometri dal Monviso fino all'Adriatico." },
    { q: "Quanti continenti ci sono sulla Terra?", opts: ["Cinque", "Sei", "Sette", "Quattro"], ok: 2,
      why: "Sette: Africa, America del Nord e del Sud, Antartide, Asia, Europa e Oceania." },
    { q: "Cosa studia un paleontologo?", opts: ["Le stelle", "I fossili e i dinosauri", "I vulcani", "Il meteo"], ok: 1,
      why: "I fossili! Grazie ai paleontologi conosciamo i dinosauri. 🦕" },
    { q: "In quale città c'è il Colosseo?", opts: ["Milano", "Napoli", "Venezia", "Roma"], ok: 3,
      why: "A Roma: quasi 2000 anni fa ci entravano 50.000 spettatori." },
    { q: "Qual è l'animale più grande mai esistito?", opts: ["Il T-Rex", "L'elefante", "La balenottera azzurra", "Il mammut"], ok: 2,
      why: "La balenottera azzurra: più grande di qualsiasi dinosauro, e nuota ancora nei nostri oceani! 🐋" },
  ],
};

const BOOK_TIPS = {
  "quiz-esploratore": { emoji: "🔬", text: "Ti piace scoprire come funziona il mondo? In libreria c'è uno scaffale pieno di libri di scienze per te. Chiedi di vederlo al tuo prossimo giro!" },
  "ricomponi-la-figura": { emoji: "🎨", text: "Questa figura è in stile albo illustrato: in libreria trovi tanti albi con illustrazioni bellissime da guardare e riguardare." },
};

// Illustrazione del puzzle: SVG inline (nessuna licenza esterna necessaria)
const PUZZLE_SVG = encodeURIComponent(`
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 300">
  <rect width="300" height="300" fill="#BFE3F0"/>
  <circle cx="245" cy="55" r="32" fill="#F0B93E"/>
  <path d="M0 210 Q75 170 150 210 T300 210 V300 H0 Z" fill="#6E9B68"/>
  <path d="M0 250 Q75 220 150 250 T300 250 V300 H0 Z" fill="#5A8455"/>
  <g transform="translate(90,140)">
    <ellipse cx="60" cy="80" rx="46" ry="38" fill="#D45A2B"/>
    <circle cx="60" cy="34" r="30" fill="#D45A2B"/>
    <polygon points="38,14 46,-16 58,12" fill="#D45A2B"/>
    <polygon points="82,14 74,-16 62,12" fill="#D45A2B"/>
    <polygon points="40,16 46,-8 54,14" fill="#FAF5EC"/>
    <polygon points="80,16 74,-8 66,14" fill="#FAF5EC"/>
    <ellipse cx="60" cy="44" rx="16" ry="12" fill="#FAF5EC"/>
    <circle cx="50" cy="30" r="4" fill="#2B2118"/>
    <circle cx="70" cy="30" r="4" fill="#2B2118"/>
    <circle cx="60" cy="40" r="5" fill="#2B2118"/>
    <path d="M100 90 Q150 70 140 110 Q120 130 96 108 Z" fill="#F0B93E"/>
    <rect x="30" y="95" width="60" height="44" rx="6" fill="#33506D"/>
    <rect x="36" y="101" width="48" height="32" rx="3" fill="#FAF5EC"/>
    <line x1="60" y1="101" x2="60" y2="133" stroke="#33506D" stroke-width="3"/>
  </g>
  <text x="150" y="290" text-anchor="middle" font-family="sans-serif" font-size="14" fill="#2B2118">La volpe che leggeva</text>
</svg>`);
const PUZZLE_IMG = `url("data:image/svg+xml,${PUZZLE_SVG}")`;

// ---------- Utilità ----------
const $ = (id) => document.getElementById(id);
function show(view) {
  document.querySelectorAll(".view").forEach((v) => v.classList.remove("active"));
  $(view).classList.add("active");
}
function shuffle(a) {
  const arr = [...a];
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

// ---------- Firebase bridge (solo con token) ----------
async function initFirebase() {
  if (DEMO) {
    $("demo-badge").textContent = "modalità demo — i punti veri arrivano dall'app";
    return;
  }
  try {
    const [{ initializeApp }, authMod, fnsMod] = await Promise.all([
      import("https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js"),
      import("https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js"),
      import("https://www.gstatic.com/firebasejs/10.12.2/firebase-functions.js"),
    ]);
    const { firebaseConfig } = await import("./firebase-config.js");
    const app = initializeApp(firebaseConfig);
    const auth = authMod.getAuth(app);
    await authMod.signInWithCustomToken(auth, CTX.token);
    const fns = fnsMod.getFunctions(app, "europe-west1");
    firebaseApi = {
      completeSession: (gameId, durationS, score) =>
        fnsMod.httpsCallable(fns, "completeGameSession")({
          childId: CTX.childId,
          gameId,
          durationS,
          score,
        }),
    };
  } catch (e) {
    // La sessione di gioco continua comunque: i punti si perdono, il divertimento no.
    firebaseApi = null;
    $("demo-badge").textContent = "connessione assente — i progressi non verranno salvati";
  }
}

async function endSession(gameId, score) {
  const durationS = Math.round((Date.now() - sessionStart) / 1000);
  let pointsAwarded = null;
  if (firebaseApi) {
    try {
      const res = await firebaseApi.completeSession(gameId, durationS, score);
      pointsAwarded = res.data.pointsAwarded;
    } catch { /* offline: nessun punto, nessun errore in faccia al bambino */ }
  }
  const tip = BOOK_TIPS[gameId];
  $("tip-emoji").textContent = tip.emoji;
  $("tip-text").textContent = tip.text;
  $("end-points").textContent =
    pointsAwarded === null
      ? "Bravissimo!"
      : pointsAwarded > 0
        ? `Hai fatto guadagnare ${pointsAwarded} ${pointsAwarded === 1 ? "punto" : "punti"} alla tua famiglia! ⭐`
        : "Le stelline di oggi sono al completo: domani se ne guadagnano altre!";
  show("view-end");
}

// ---------- Gate di uscita (tieni premuto 3s) ----------
(() => {
  let t0 = null, raf = null;
  const btn = $("exit-btn"), ring = $("exit-progress");
  const tick = () => {
    const p = Math.min(100, ((Date.now() - t0) / 3000) * 100);
    ring.style.setProperty("--p", p);
    if (p >= 100) {
      window.parent?.postMessage({ type: "games-exit" }, "*");
      location.href = "about:blank#exit"; // l'app nativa intercetta e chiude la webview
      return;
    }
    raf = requestAnimationFrame(tick);
  };
  const start = (e) => { e.preventDefault(); t0 = Date.now(); tick(); };
  const stop = () => { cancelAnimationFrame(raf); ring.style.setProperty("--p", 0); };
  btn.addEventListener("pointerdown", start);
  btn.addEventListener("pointerup", stop);
  btn.addEventListener("pointerleave", stop);
})();

// ---------- QUIZ (F4) ----------
const quiz = { deck: [], index: 0, correct: 0 };

function startQuiz() {
  currentGame = "quiz-esploratore";
  sessionStart = Date.now();
  quiz.deck = shuffle(QUESTIONS[CTX.ageBand]).slice(0, 6);
  quiz.index = 0;
  quiz.correct = 0;
  $("quiz-progress").innerHTML = quiz.deck.map(() => "<i></i>").join("");
  show("view-quiz");
  renderQuestion();
}

function renderQuestion() {
  const item = quiz.deck[quiz.index];
  $("quiz-question").textContent = item.q;
  $("quiz-explain").innerHTML = "";
  $("quiz-options").innerHTML = item.opts
    .map((o, i) => `<button class="opt" data-i="${i}">${o}</button>`)
    .join("");
  $("quiz-options").querySelectorAll(".opt").forEach((b) => {
    b.onclick = () => answer(Number(b.dataset.i), b);
  });
}

function answer(i, btn) {
  const item = quiz.deck[quiz.index];
  const right = i === item.ok;
  document.querySelectorAll(".opt").forEach((b) => (b.onclick = null));
  btn.classList.add(right ? "right" : "wrong");
  if (!right) {
    document.querySelector(`.opt[data-i="${item.ok}"]`).classList.add("right");
  } else {
    quiz.correct++;
  }
  const dot = $("quiz-progress").children[quiz.index];
  dot.classList.add(right ? "done" : "miss");
  $("quiz-explain").innerHTML =
    `<div class="explain">${right ? "Giusto! " : "Niente paura: si impara sbagliando. "}${item.why}</div>
     <button class="big-btn" id="quiz-next" style="margin-top:12px">${quiz.index + 1 < quiz.deck.length ? "Avanti →" : "Vedi come è andata"}</button>`;
  $("quiz-next").onclick = () => {
    quiz.index++;
    if (quiz.index < quiz.deck.length) renderQuestion();
    else {
      $("end-stars").textContent = "⭐".repeat(Math.max(1, Math.round((quiz.correct / quiz.deck.length) * 5)));
      $("end-title").textContent =
        quiz.correct === 1
          ? `1 risposta giusta su ${quiz.deck.length}!`
          : `${quiz.correct} risposte giuste su ${quiz.deck.length}!`;
      endSession("quiz-esploratore", { correct: quiz.correct, total: quiz.deck.length });
    }
  };
}

// ---------- PUZZLE (F3) ----------
const GRID = 3; // 3×3 per la fascia 6-8; 9-12 in produzione: 5×5
let placedCount = 0;

function startPuzzle() {
  currentGame = "ricomponi-la-figura";
  sessionStart = Date.now();
  placedCount = 0;
  show("view-puzzle");
  const board = $("puzzle-board");
  board.innerHTML = "";
  requestAnimationFrame(() => buildPuzzle(board));
}

function buildPuzzle(board) {
  const size = board.clientWidth;
  const cell = size / GRID;
  const cells = [];
  for (let r = 0; r < GRID; r++) {
    for (let c = 0; c < GRID; c++) cells.push({ r, c });
  }
  // Slot di destinazione
  for (const { r, c } of cells) {
    const slot = document.createElement("div");
    slot.className = "slot";
    Object.assign(slot.style, {
      left: `${c * cell + 2}px`, top: `${r * cell + 2}px`,
      width: `${cell - 4}px`, height: `${cell - 4}px`,
    });
    board.appendChild(slot);
  }
  // Pezzi sparpagliati
  for (const { r, c } of shuffle(cells)) {
    const p = document.createElement("div");
    p.className = "piece";
    p.dataset.r = r;
    p.dataset.c = c;
    Object.assign(p.style, {
      width: `${cell - 4}px`, height: `${cell - 4}px`,
      backgroundImage: PUZZLE_IMG,
      backgroundPosition: `${(c / (GRID - 1)) * 100}% ${(r / (GRID - 1)) * 100}%`,
      left: `${Math.random() * (size - cell)}px`,
      top: `${Math.random() * (size - cell)}px`,
    });
    board.appendChild(p);
    makeDraggable(p, board, cell);
  }
  $("hint-btn").onclick = () => {
    // Anti-frustrazione: mostra la figura completa per 2 secondi
    const hint = document.createElement("div");
    Object.assign(hint.style, {
      position: "absolute", inset: "0", backgroundImage: PUZZLE_IMG,
      backgroundSize: "cover", borderRadius: "16px", zIndex: 20, opacity: ".95",
    });
    board.appendChild(hint);
    setTimeout(() => hint.remove(), 2000);
  };
}

function makeDraggable(piece, board, cell) {
  let sx, sy, ox, oy;
  const onMove = (e) => {
    piece.style.left = `${ox + e.clientX - sx}px`;
    piece.style.top = `${oy + e.clientY - sy}px`;
  };
  const onUp = () => {
    document.removeEventListener("pointermove", onMove);
    document.removeEventListener("pointerup", onUp);
    // Snap generoso: entro il 40% della cella
    const targetX = piece.dataset.c * cell + 2;
    const targetY = piece.dataset.r * cell + 2;
    const dx = Math.abs(parseFloat(piece.style.left) - targetX);
    const dy = Math.abs(parseFloat(piece.style.top) - targetY);
    if (dx < cell * 0.4 && dy < cell * 0.4) {
      piece.style.left = `${targetX}px`;
      piece.style.top = `${targetY}px`;
      piece.classList.add("placed");
      piece.style.pointerEvents = "none";
      placedCount++;
      if (placedCount === GRID * GRID) {
        setTimeout(() => {
          $("end-stars").textContent = "⭐⭐⭐⭐⭐";
          $("end-title").textContent = "Figura completata!";
          endSession("ricomponi-la-figura", { pieces: GRID * GRID });
        }, 500);
      }
    }
  };
  piece.addEventListener("pointerdown", (e) => {
    e.preventDefault();
    sx = e.clientX; sy = e.clientY;
    ox = parseFloat(piece.style.left); oy = parseFloat(piece.style.top);
    piece.style.zIndex = 10;
    document.addEventListener("pointermove", onMove);
    document.addEventListener("pointerup", onUp);
  });
}

// ---------- Avvio ----------
if (CTX.name) $("hub-sub").textContent = `Scegli un gioco, ${CTX.name}!`;
document.querySelectorAll(".game-card").forEach((card) => {
  card.onclick = () =>
    card.dataset.game === "quiz-esploratore" ? startQuiz() : startPuzzle();
});
$("end-again").onclick = () => show("view-hub");
initFirebase();
