/**
 * LibreriBrà Backoffice — logica applicativa.
 * Accesso: solo utenti con custom claim role staff|admin.
 * Le operazioni economiche passano SEMPRE dalle callable (mai scritture dirette).
 */
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth, connectAuthEmulator, signInWithEmailAndPassword, signOut, onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  getFirestore, connectFirestoreEmulator, collection, collectionGroup, doc, addDoc,
  query, where, orderBy, limit, getDocs, getCountFromServer, Timestamp, serverTimestamp,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";
import {
  getFunctions, connectFunctionsEmulator, httpsCallable,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-functions.js";
import { firebaseConfig, USE_EMULATORS } from "./firebase-config.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
const fns = getFunctions(app, "europe-west1");
if (USE_EMULATORS) {
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  connectFirestoreEmulator(db, "127.0.0.1", 8080);
  connectFunctionsEmulator(fns, "127.0.0.1", 5001);
}
const call = (name) => httpsCallable(fns, name);

// ---------- UI helpers ----------
const $ = (id) => document.getElementById(id);
const esc = (s) =>
  String(s ?? "").replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
function toast(msg, isError = false) {
  const t = $("toast");
  t.textContent = msg;
  t.className = isError ? "error" : "";
  t.style.display = "block";
  setTimeout(() => (t.style.display = "none"), 4200);
}
async function guard(fn, btn) {
  if (btn) btn.disabled = true;
  try {
    await fn();
  } catch (e) {
    toast(e.message ?? String(e), true);
  } finally {
    if (btn) btn.disabled = false;
  }
}
const fmtDate = (ts) =>
  ts?.toDate ? ts.toDate().toLocaleString("it-IT", { dateStyle: "short", timeStyle: "short" }) : "—";

// ---------- Auth ----------
let role = null;

$("login-btn").onclick = () =>
  guard(async () => {
    $("login-error").textContent = "";
    await signInWithEmailAndPassword(auth, $("login-email").value.trim(), $("login-password").value);
  }, $("login-btn")).catch(() => {});

$("logout-btn").onclick = () => signOut(auth);

onAuthStateChanged(auth, async (user) => {
  if (!user) {
    $("login").style.display = "flex";
    return;
  }
  const token = await user.getIdTokenResult();
  role = token.claims.role;
  if (!["staff", "admin"].includes(role)) {
    $("login-error").textContent = "Questo account non fa parte dello staff.";
    await signOut(auth);
    return;
  }
  $("login").style.display = "none";
  $("who").textContent = `${user.email} · ${role}`;
  $("lottery-admin-card").style.display = role === "admin" ? "block" : "none";
  loadAll();
});

// ---------- Nav ----------
$("nav").addEventListener("click", (e) => {
  const btn = e.target.closest("button[data-panel]");
  if (!btn) return;
  document.querySelectorAll("nav button").forEach((b) => b.classList.remove("active"));
  document.querySelectorAll("section.panel").forEach((p) => p.classList.remove("active"));
  btn.classList.add("active");
  $(`panel-${btn.dataset.panel}`).classList.add("active");
});

function loadAll() {
  loadModeration();
  loadPosts();
  loadBooks();
  loadCategories();
  loadEvents();
  loadOrders();
  loadRounds();
  loadKpis();
}

// ---------- FEED ----------
$("post-publish").onclick = (e) =>
  guard(async () => {
    const caption = $("post-caption").value.trim();
    const photos = $("post-photos").value.split("\n").map((s) => s.trim()).filter(Boolean);
    if (!caption) throw new Error("La caption è vuota.");
    await addDoc(collection(db, "posts"), {
      author_uid: auth.currentUser.uid,
      caption,
      photos,
      status: "published",
      published_at: serverTimestamp(),
      like_count: 0,
      comment_count: 0,
      save_count: 0,
      store_id: "libreribra-sangiovanni",
      created_at: serverTimestamp(),
      updated_at: serverTimestamp(),
    });
    $("post-caption").value = "";
    $("post-photos").value = "";
    toast("Post pubblicato 🎉");
    loadPosts();
  }, e.target);

async function loadModeration() {
  const el = $("moderation-list");
  const [pending, reports] = await Promise.all([
    getDocs(query(collectionGroup(db, "comments"), where("status", "==", "pending"), limit(20))),
    getDocs(query(collection(db, "reports"), where("status", "==", "open"), limit(20))),
  ]);
  let html = "";
  pending.forEach((d) => {
    const postId = d.ref.parent.parent.id;
    html += `<div style="margin-bottom:10px">
      <span class="pill warn">in revisione</span> <b>${esc(d.data().text)}</b>
      <div class="muted">${esc(d.data().pending_reason ?? "")} · post ${esc(postId)}</div>
      <button class="secondary" data-mod="restore" data-post="${esc(postId)}" data-comment="${esc(d.id)}">Approva</button>
      <button class="secondary danger" data-mod="hide" data-post="${esc(postId)}" data-comment="${esc(d.id)}">Nascondi</button>
    </div>`;
  });
  reports.forEach((d) => {
    html += `<div style="margin-bottom:10px"><span class="pill bad">segnalazione</span>
      ${esc(d.data().reason)} <span class="muted">→ ${esc(d.data().target_ref)}</span></div>`;
  });
  el.innerHTML = html || "Nessun commento in revisione, nessuna segnalazione. 👌";
  el.querySelectorAll("button[data-mod]").forEach((b) => {
    b.onclick = () =>
      guard(async () => {
        const reason = b.dataset.mod === "hide" ? prompt("Motivo (obbligatorio):") : "";
        if (b.dataset.mod === "hide" && !reason) return;
        await call("moderateComment")({
          postId: b.dataset.post,
          commentId: b.dataset.comment,
          action: b.dataset.mod,
          reason,
        });
        toast("Fatto.");
        loadModeration();
      }, b);
  });
}

async function loadPosts() {
  const snap = await getDocs(
    query(collection(db, "posts"), where("status", "==", "published"), orderBy("published_at", "desc"), limit(10))
  );
  $("posts-list").innerHTML =
    `<table><tr><th>Caption</th><th>❤</th><th>💬</th><th>🔖</th><th>Data</th></tr>` +
    [...snap.docs]
      .map(
        (d) =>
          `<tr><td>${esc(d.data().caption).slice(0, 80)}</td><td>${d.data().like_count}</td>
           <td>${d.data().comment_count}</td><td>${d.data().save_count}</td><td>${fmtDate(d.data().published_at)}</td></tr>`
      )
      .join("") +
    `</table>` || "Nessun post.";
}

// ---------- LIBRI ----------
$("csv-import").onclick = (e) =>
  guard(async () => {
    const file = $("csv-file").files[0];
    if (!file) throw new Error("Scegli un file CSV.");
    const csv = await file.text();
    const { data } = await call("importBooksCsv")({ csv });
    $("csv-result").innerHTML =
      `<p><b>${data.imported}</b> libri importati, <b>${data.rejected}</b> righe respinte.</p>` +
      (data.rowErrors ?? [])
        .map((r) => `<div class="pill bad">riga ${r.row}</div> ${esc(r.errors.join("; "))}<br>`)
        .join("");
    toast("Import completato.");
    loadBooks();
  }, e.target);

let booksCache = [];
async function loadBooks() {
  const snap = await getDocs(query(collection(db, "books"), orderBy("updated_at", "desc"), limit(50)));
  booksCache = snap.docs;
  renderBooks();
}
$("book-search").oninput = renderBooks;
function renderBooks() {
  const q = $("book-search").value.toLowerCase();
  const rows = booksCache
    .filter((d) => !q || (d.data().title ?? "").toLowerCase().includes(q))
    .map((d) => {
      const b = d.data();
      const pill =
        b.availability === "in_stock"
          ? '<span class="pill ok">disponibile</span>'
          : b.availability === "low"
            ? '<span class="pill warn">poche copie</span>'
            : '<span class="pill bad">esaurito</span>';
      return `<tr><td><b>${esc(b.title)}</b><br><span class="muted">${esc(b.author)}</span></td>
        <td>€${b.on_sale && b.sale_price ? `${b.sale_price} <s class="muted">${b.price}</s>` : b.price}</td>
        <td>${pill}</td><td>${(b.age_bands ?? []).join(", ")}</td></tr>`;
    })
    .join("");
  $("books-list").innerHTML = rows
    ? `<table><tr><th>Libro</th><th>Prezzo</th><th>Stato</th><th>Età</th></tr>${rows}</table>`
    : "Nessun libro. Importa il CSV per iniziare.";
}

// ---------- EVENTI ----------
async function loadCategories() {
  const snap = await getDocs(query(collection(db, "event_categories"), orderBy("sort")));
  $("ev-category").innerHTML = snap.docs
    .map((d) => `<option value="${esc(d.id)}">${esc(d.data().name)}</option>`)
    .join("");
}

$("ev-create").onclick = (e) =>
  guard(async () => {
    const title = $("ev-title").value.trim();
    const start = $("ev-start").value;
    if (!title || !start) throw new Error("Titolo e data sono obbligatori.");
    await addDoc(collection(db, "events"), {
      title,
      description: $("ev-desc").value.trim(),
      category_id: $("ev-category").value,
      age_band: $("ev-age").value || null,
      starts_at: Timestamp.fromDate(new Date(start)),
      duration_min: Number($("ev-duration").value) || 60,
      capacity: Number($("ev-capacity").value) || 10,
      booked_count: 0,
      price: Number($("ev-price").value) || 0,
      waitlist_limit: Number($("ev-waitlist").value) || 0,
      status: "published",
      store_id: "libreribra-sangiovanni",
      created_at: serverTimestamp(),
      updated_at: serverTimestamp(),
    });
    toast("Evento pubblicato 📅");
    $("ev-title").value = "";
    loadEvents();
  }, e.target);

async function loadEvents() {
  const snap = await getDocs(
    query(
      collection(db, "events"),
      where("status", "in", ["published", "sold_out"]),
      where("starts_at", ">", Timestamp.now()),
      orderBy("starts_at"),
      limit(15)
    )
  );
  $("events-list").innerHTML = snap.empty
    ? "Nessun evento in programma."
    : `<table><tr><th>Evento</th><th>Quando</th><th>Posti</th><th>ID</th></tr>` +
      snap.docs
        .map((d) => {
          const ev = d.data();
          const full = ev.booked_count >= ev.capacity;
          return `<tr><td><b>${esc(ev.title)}</b></td><td>${fmtDate(ev.starts_at)}</td>
            <td><span class="pill ${full ? "bad" : "ok"}">${ev.booked_count}/${ev.capacity}</span></td>
            <td class="muted">${esc(d.id)}</td></tr>`;
        })
        .join("") +
      `</table>`;
}

$("ci-do").onclick = (e) =>
  guard(async () => {
    const [bookingUid, qrToken] = $("ci-data").value.split("|").map((s) => s.trim());
    if (!bookingUid || !qrToken) throw new Error("Formato: uid|token (dal QR della prenotazione).");
    const { data } = await call("checkIn")({ eventId: $("ci-event").value.trim(), bookingUid, qrToken });
    toast(
      data.alreadyCheckedIn
        ? "Già registrato: nessun doppio accredito."
        : `Check-in fatto! Nuovo saldo cliente: ${data.pointsBalance} punti.`
    );
  }, e.target);

async function loadOrders() {
  const snap = await getDocs(
    query(collection(db, "orders"), where("status", "in", ["requested", "ready_for_pickup"]), limit(30))
  );
  const el = $("orders-list");
  el.innerHTML = snap.empty
    ? "Nessun ordine in corso."
    : `<table><tr><th>Libro</th><th>Stato</th><th>Scadenza</th><th></th></tr>` +
      snap.docs
        .map((d) => {
          const o = d.data();
          const btn =
            o.status === "requested"
              ? `<button class="secondary" data-ord="ready" data-id="${esc(d.id)}">Pronto per il ritiro</button>`
              : `<button class="secondary" data-ord="picked" data-id="${esc(d.id)}">Ritirato ✔</button>`;
          return `<tr><td>${esc(o.book_title)}</td>
            <td><span class="pill ${o.status === "requested" ? "warn" : "ok"}">${o.status === "requested" ? "da preparare" : "in attesa di ritiro"}</span></td>
            <td>${fmtDate(o.pickup_deadline)}</td><td>${btn}</td></tr>`;
        })
        .join("") +
      `</table>`;
  el.querySelectorAll("button[data-ord]").forEach((b) => {
    b.onclick = () =>
      guard(async () => {
        await call(b.dataset.ord === "ready" ? "markOrderReady" : "markOrderPickedUp")({ orderId: b.dataset.id });
        toast("Aggiornato.");
        loadOrders();
      }, b);
  });
}

// ---------- UTENTI ----------
$("user-search").onclick = (e) =>
  guard(async () => {
    const email = $("user-email").value.trim().toLowerCase();
    const snap = await getDocs(query(collection(db, "users"), where("email", "==", email), limit(1)));
    if (snap.empty) {
      $("user-result").innerHTML = '<p class="muted">Nessun utente con questa email.</p>';
      return;
    }
    const d = snap.docs[0];
    const u = d.data();
    let wallet = null;
    try {
      const w = await getDocs(query(collection(db, "loyalty_wallet"), where("__name__", "==", d.id)));
      wallet = w.empty ? null : w.docs[0].data();
    } catch { /* wallet non leggibile: mostra solo l'anagrafica */ }
    $("user-result").innerHTML = `
      <p><b>UID:</b> <code>${esc(d.id)}</code> — usalo per l'accredito punti</p>
      <p><b>Ruolo:</b> ${esc(u.role)} · <b>Stato:</b> ${esc(u.status)}</p>
      <p><b>Punti:</b> ${wallet ? `${wallet.balance} (${esc(wallet.tier)})` : "—"}</p>`;
  }, e.target);

// ---------- LOYALTY & LOTTERIA ----------
$("cp-do").onclick = (e) =>
  guard(async () => {
    const { data } = await call("creditPurchasePoints")({
      customerUid: $("cp-uid").value.trim(),
      amountEur: Number($("cp-amount").value),
      receiptId: $("cp-receipt").value.trim(),
      pin: $("cp-pin").value,
    });
    toast(
      data.duplicate
        ? "Scontrino già registrato: nessun doppio accredito."
        : `+${data.pointsCredited} punti. Nuovo saldo: ${data.balance}.`
    );
  }, e.target);

$("mc-do").onclick = (e) =>
  guard(async () => {
    const { data } = await call("markCouponRedeemed")({
      code: $("mc-code").value.trim().toUpperCase(),
      pin: $("mc-pin").value,
    });
    toast(`Coupon da €${data.valueEur} segnato come usato. Buona vendita!`);
  }, e.target);

$("lr-create").onclick = (e) =>
  guard(async () => {
    const prizes = $("lr-prizes").value
      .split("\n")
      .map((l) => l.split("|").map((s) => s.trim()))
      .filter((p) => p[0])
      .map(([description, v]) => ({ description, valueEur: Number(v) || 0 }));
    const { data } = await call("createLotteryRound")({
      name: $("lr-name").value.trim(),
      opensAt: new Date($("lr-open").value).toISOString(),
      salesCloseAt: new Date($("lr-close").value).toISOString(),
      drawAt: new Date($("lr-draw").value).toISOString(),
      ticketCostPoints: Number($("lr-cost").value) || 150,
      rulesUrl: $("lr-rules").value.trim(),
      prizes,
    });
    toast(`Round creato (bozza): ${data.roundId}`);
    loadRounds();
  }, e.target);

async function loadRounds() {
  if (role !== "admin") return;
  const snap = await getDocs(query(collection(db, "lottery_rounds"), orderBy("created_at", "desc"), limit(8)));
  const el = $("rounds-list");
  el.innerHTML = snap.empty
    ? "Nessun round."
    : snap.docs
        .map((d) => {
          const r = d.data();
          const action =
            r.status === "draft"
              ? `<button class="secondary" data-round="open" data-id="${esc(d.id)}">Apri vendite</button>`
              : r.status === "open"
                ? `<button class="secondary" data-round="close" data-id="${esc(d.id)}">Chiudi vendite</button>`
                : r.status === "closed"
                  ? `<button class="secondary" data-round="draw" data-id="${esc(d.id)}">Estrai 🎲</button>`
                  : "";
          const extra =
            r.status === "drawn"
              ? `<div class="muted">Numeri estratti: <b>${(r.winning_numbers ?? []).join(", ")}</b> · seed verificabile pubblicato</div>`
              : r.commit_hash
                ? `<div class="muted">commit: <code>${esc(r.commit_hash.slice(0, 16))}…</code></div>`
                : "";
          return `<div style="margin-bottom:12px"><b>${esc(r.name)}</b>
            <span class="pill ${r.status === "open" ? "ok" : r.status === "drawn" ? "warn" : "bad"}">${esc(r.status)}</span>
            <span class="muted">venduti: ${r.sold_count}</span> ${action}${extra}</div>`;
        })
        .join("");
  el.querySelectorAll("button[data-round]").forEach((b) => {
    b.onclick = () =>
      guard(async () => {
        const map = { open: "openLotteryRound", close: "closeLotterySales", draw: "drawLottery" };
        const { data } = await call(map[b.dataset.round])({ roundId: b.dataset.id });
        toast(
          b.dataset.round === "draw"
            ? data.outcome === "drawn"
              ? `Estratti: ${data.winningNumbers.join(", ")} 🎉`
              : `Sotto soglia: ${data.refundedTickets} biglietti rimborsati.`
            : "Fatto."
        );
        loadRounds();
      }, b);
  });
}

// ---------- REPORT ----------
async function loadKpis() {
  const count = async (q) => (await getCountFromServer(q)).data().count;
  const [checkins, coupons, pickups, users, events] = await Promise.all([
    count(query(collection(db, "bookings"), where("status", "==", "checked_in"))),
    count(query(collection(db, "discounts"), where("status", "==", "redeemed"))),
    count(query(collection(db, "orders"), where("status", "==", "picked_up"))),
    count(collection(db, "users")),
    count(query(collection(db, "events"), where("starts_at", ">", Timestamp.now()))),
  ]);
  $("kpis").innerHTML = `
    <div class="kpi"><b>${checkins + coupons + pickups}</b><span>visite portate dall'app</span></div>
    <div class="kpi"><b>${checkins}</b><span>check-in eventi</span></div>
    <div class="kpi"><b>${coupons}</b><span>coupon usati</span></div>
    <div class="kpi"><b>${pickups}</b><span>libri ritirati</span></div>
    <div class="kpi"><b>${users}</b><span>utenti registrati</span></div>
    <div class="kpi"><b>${events}</b><span>eventi in programma</span></div>`;
}
