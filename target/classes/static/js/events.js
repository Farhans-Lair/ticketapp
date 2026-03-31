console.log("events.js loaded");

const CATEGORIES = ['All','Music','Sports','Comedy','Theatre','Conference','Festival','Workshop','Other'];

window.addEventListener("pageshow", function (event) {
  if (event.persisted) window.location.reload();
});

document.addEventListener("DOMContentLoaded", async () => {
 // ── Verify session with server via cookie — do NOT rely on localStorage ───
  // localStorage.role is only a UI hint; the real auth check is the HttpOnly
  // cookie verified by GET /auth/me. This handles cases where the cookie was
  // silently dropped (e.g. Secure flag on HTTP) or localStorage was cleared.
  try {
    const session = await apiRequest("/auth/me", "GET");
    // Sync localStorage with the server's authoritative role value
    sessionStorage.setItem("role", session.role);
    sessionStorage.setItem("userId", String(session.userId));
  } catch (err) {
    // 401 — cookie missing or expired → api.js already redirects to "/"
    return;
  }


  renderCategoryFilters();
  loadEvents();

  document.getElementById("logoutBtn").addEventListener("click", logout);
});

/*
====================================================
 RENDER CATEGORY FILTER BUTTONS
====================================================
*/
function renderCategoryFilters() {
  const container = document.getElementById("category-filters");
  if (!container) return;

  CATEGORIES.forEach(cat => {
    const btn = document.createElement("button");
    btn.textContent = cat;
    btn.className   = "cat-btn" + (cat === "All" ? " active" : "");
    btn.onclick     = () => {
      document.querySelectorAll(".cat-btn").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      loadEvents(cat === "All" ? null : cat);
    };
    container.appendChild(btn);
  });
}

/*
====================================================
 LOAD EVENTS (with optional category filter)
====================================================
*/
async function loadEvents(category = null) {
  try {
    const url    = category ? `/events?category=${category}` : "/events";
    const events = await apiRequest(url, "GET", null, true);

    const list = document.getElementById("events-list");
    list.innerHTML = "";

    if (!events || events.length === 0) {
      list.innerHTML = "<p>No events available</p>";
      return;
    }

    events.forEach(event => {
      const div = document.createElement("div");
      div.className = "event-card";

      const bookingSection = event.available_tickets === 0
        ? `<p style="color:red; font-weight:bold;">Sold Out</p>`
        : `
          <input type="number" min="1" max="${event.available_tickets}"
            id="qty-${event.id}" placeholder="Number of tickets" />
          <button onclick="goToSeatSelection(${event.id})">Select Seats & Book</button>
        `;

      div.innerHTML = `
        <span class="category-badge">${event.category || 'Other'}</span>
        <strong>${event.title}</strong><br/>
        ${event.description || ""}<br/>
        <p>${event.location || ""}</p>
        Date: ${new Date(event.event_date).toLocaleDateString()}<br/>
        Available: ${event.available_tickets} / ${event.total_tickets} &nbsp;|&nbsp;
        Price: ₹${event.price}<br/>
        ${bookingSection}
        <hr/>
      `;
      list.appendChild(div);
    });

  } catch (err) {
    console.error("Error loading events:", err);
  }
}

/*
====================================================
 GO TO SEAT SELECTION
 Stores event id + qty in sessionStorage, then redirects
====================================================
*/
function goToSeatSelection(eventId) {
  const tickets_booked = parseInt(
    document.getElementById(`qty-${eventId}`).value, 10
  );

  if (!tickets_booked || tickets_booked <= 0) {
    alert("Enter a valid number of tickets");
    return;
  }

  sessionStorage.setItem("seat_selection_meta", JSON.stringify({
    event_id:       eventId,
    tickets_booked,
  }));

  window.location.href = "/seat-selection";
}

// Backward compatibility alias
function bookTicket(eventId)    { goToSeatSelection(eventId); }
function initiatePayment(eventId) { goToSeatSelection(eventId); }

function goMyBookings() { window.location.replace("/my-bookings"); }

function logout() {

  const userId = sessionStorage.getItem('userId');
  // Broadcast to all other tabs of this same user so they redirect immediately.
  // Tabs belonging to a different user (different userId) will ignore this.
  // _authChannel is set by auth-channel.js which must be loaded on the page.
  if (window._authChannel && userId) {
    window._authChannel.postMessage({ type: 'LOGOUT', userId });
  }
  // Clear the server-side HttpOnly cookie, then wipe localStorage and redirect.
  fetch('/auth/logout', { method: 'POST', credentials: 'include' })
    .finally(() => {
  sessionStorage.clear();
  window.location.replace("/");
});
}