document.addEventListener("DOMContentLoaded", async () => {

  // ── Verify session with server via cookie ─────────────────────────────────
  let cookieConflict = false;

  try {
    const session = await apiRequest("/auth/me", "GET");

    // Always store userId from the authoritative server response.
    // This is required by auth-channel.js to match logout broadcasts.
    // Without it, tabs opened without the login form have userId = null
    // and never receive the logout signal from other tabs.
    sessionStorage.setItem("userId", String(session.userId));

    const myUserId = sessionStorage.getItem("userId");

    if (myUserId && String(session.userId) !== myUserId) {
      // Cookie has been overwritten by a different user logging in on another
      // tab. Do NOT redirect — instead serve from the per-tab sessionStorage
      // cache so the user's bookings stay visible.
      cookieConflict = true;
    } else {
      // Cookie still belongs to this tab's user — store role and proceed normally.
      sessionStorage.setItem("role", session.role);
    }
  } catch (err) {
    return; // api.js 401 handler redirects to "/"
  }

  document
    .getElementById("logoutBtn")
    .addEventListener("click", logout);

  loadBookings(cookieConflict);
});

// Cache key is scoped to this tab's userId so different users on the same
// browser never see each other's cached bookings.
function _bookingsCacheKey() {
  return 'bookingsCache_' + (sessionStorage.getItem('userId') || 'unknown');
}

function _showConflictBanner() {
  // Only inject once
  if (document.getElementById('session-conflict-banner')) return;
  const banner = document.createElement('div');
  banner.id = 'session-conflict-banner';
  banner.style.cssText = [
    'background:rgba(245,200,66,0.12)',
    'border:1px solid rgba(245,200,66,0.35)',
    'border-radius:10px',
    'padding:12px 18px',
    'margin-bottom:18px',
    'font-size:0.85rem',
    'color:#f5c842',
    'display:flex',
    'align-items:center',
    'gap:10px',
  ].join(';');
  banner.innerHTML = `
    <span style="font-size:1.1rem">⚠️</span>
    <span>Another user is active in a different tab. Showing your saved bookings —
    they are <strong>your bookings</strong> and have not changed.</span>`;
  const list = document.getElementById('bookings-list');
  list.parentNode.insertBefore(banner, list);
}

async function loadBookings(fromCache = false) {
  const container = document.getElementById('bookings-list');
  const cacheKey  = _bookingsCacheKey();

  if (fromCache) {
    // ── Conflict path: cookie belongs to another user ─────────────────────
    // Serve from the sessionStorage cache that was written on the last clean
    // load. The cache is scoped by userId so it cannot contain another user's
    // data. Show a subtle banner so the user knows what is happening.
    _showConflictBanner();

    const cached = sessionStorage.getItem(cacheKey);
    if (cached) {
      renderBookings(JSON.parse(cached), container);
    } else {
      // No cache yet (user never visited my-bookings before the conflict).
      container.innerHTML = `
        <div class="empty-state">
          <div class="emoji">⚠️</div>
          <h3>Session conflict</h3>
          <p>Another user logged in on a different tab before your bookings
             could be loaded. Please log out and log back in to view your
             bookings.</p>
        </div>`;
    }
    return;
  }

  // ── Normal path: cookie belongs to this tab's user ─────────────────────
  try {
    const bookings = await apiRequest('/bookings/my-bookings', 'GET', null, true);

    // Cache the fresh result for this tab. Stored as JSON; cleared on logout
    // via sessionStorage.clear() so a new login always fetches fresh data.
    sessionStorage.setItem(cacheKey, JSON.stringify(bookings));

    renderBookings(bookings, container);
  } catch (err) {
    alert(err.message || 'Error loading bookings');
  }
}

function renderBookings(bookings, container) {
  container.innerHTML = '';

  if (!bookings.length) {
    container.innerHTML = '<p>No bookings yet</p>';
    return;
  }

  bookings.forEach(b => {
    const statusColor = b.payment_status === 'paid'   ? 'green'
                      : b.payment_status === 'failed' ? 'red'
                      : 'orange';

    let seatsDisplay = 'N/A';
    if (b.selected_seats) {
      try {
        const seats = JSON.parse(b.selected_seats);
        seatsDisplay = seats.length > 0 ? seats.join(', ') : 'N/A';
      } catch (e) {
        seatsDisplay = b.selected_seats;
      }
    }

    const div = document.createElement('div');
    div.innerHTML = `
      <h3>${b.Event.title}</h3>
      <p>Event Date: ${new Date(b.Event.event_date).toLocaleDateString()}</p>
      <p>Tickets Booked: ${b.tickets_booked}</p>
      <p>Seats: ${seatsDisplay}</p>
      <p>Price per Ticket: ₹${b.Event.price}</p>
      <p>Convenience Fee: ₹${b.convenience_fee.toFixed(2)}</p>
      <p>GST (18%): ₹${b.gst_amount.toFixed(2)}</p>
      <p><strong>Total Paid: ₹${b.total_paid.toFixed(2)}</strong></p>
      <p>Payment ID: <code>${b.razorpay_payment_id || 'N/A'}</code></p>
      <p>Payment Status: <span style="color:${statusColor}; font-weight:bold; text-transform:uppercase;">${b.payment_status}</span></p>
      <p>Booked On: ${new Date(b.booking_date).toLocaleString()}</p>
      ${b.payment_status === 'paid'
        ? `<a href="/bookings/${b.id}/download-ticket"
               onclick="downloadTicket(event, ${b.id})"
               style="display:inline-block; margin-bottom:12px; padding:8px 16px;
                      background:#4CAF50; color:white; border-radius:4px;
                      text-decoration:none; font-weight:bold;">
               ⬇ Download Ticket PDF
             </a>`
        : ''}
      <hr>`;
    container.appendChild(div);
  });
}

async function downloadTicket(e, bookingId) {
  e.preventDefault();
  try {
    // This is a direct fetch (not apiRequest) so we must manually attach the
    // Authorization header. Without it, the backend middleware falls back to
    // the shared cookie which may belong to a different user (e.g. admin logged
    // in on another tab), causing the ownership check to fail.
    const tabToken = sessionStorage.getItem("token");
    const response = await fetch(`/bookings/${bookingId}/download-ticket`, {
      credentials: "include",
      headers: tabToken ? { "Authorization": `Bearer ${tabToken}` } : {}
    });

    if (!response.ok) throw new Error("Failed to download ticket");

    const blob = await response.blob();
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement("a");
    a.href     = url;
    a.download = `ticket-${bookingId}.pdf`;
    a.click();
    URL.revokeObjectURL(url);
  } catch (err) {
    alert("Could not download ticket: " + err.message);
  }
}

function goBack() {
  window.location.replace("/events-page");
}

function logout() {
  // Read userId from sessionStorage (per-tab) — NOT localStorage.
  const userId = sessionStorage.getItem('userId');
  if (window._authChannel && userId) {
    window._authChannel.postMessage({ type: 'LOGOUT', userId });
  }
  fetch('/auth/logout', { method: 'POST', credentials: 'include' })
    .finally(() => {
      sessionStorage.clear();
      window.location.replace('/');
    });
}



