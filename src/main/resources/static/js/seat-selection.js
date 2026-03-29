let selectedSeats   = [];
let requiredCount   = 0;
let currentEventId  = null;

document.addEventListener("DOMContentLoaded", async () => {
  // ── Verify session with server via cookie ─────────────────────────────────
  try {
     const session = await apiRequest("/auth/me", "GET");
    // Store userId so auth-channel.js can match logout broadcasts from other
    // tabs of the same user. Without this, this tab stays open after logout.
    sessionStorage.setItem("userId", String(session.userId));
  } catch (err) {
    return; // api.js 401 handler redirects to "/"
  }

  const raw = sessionStorage.getItem("seat_selection_meta");
  if (!raw) {
    alert("No booking session found. Please select an event first.");
    window.location.replace("/events-page");
    return;
  }
  const meta = JSON.parse(raw);
  currentEventId = meta.event_id;
  requiredCount  = meta.tickets_booked;

  document.getElementById("required-count").textContent = requiredCount;
  document.getElementById("seat-info").textContent =
    `Please select ${requiredCount} seat(s)`;

  document.getElementById("proceed-btn").addEventListener("click", proceedToPayment);

  await loadSeats(currentEventId);
});

/*
====================================================
 LOAD SEATS FROM API AND RENDER GRID
====================================================
*/
async function loadSeats(eventId) {
  try {
    const seats = await apiRequest(`/seats/${eventId}`, "GET", null, true);

    if (!seats || seats.length === 0) {
      document.getElementById("seat-grid").innerHTML = "<p>No seats found for this event.</p>";
      return;
    }

    // Group seats by row letter
    const rows = {};
    seats.forEach(seat => {
      const row = seat.seat_number[0];       // e.g. "A" from "A1"
      if (!rows[row]) rows[row] = [];
      rows[row].push(seat);
    });

    const grid = document.getElementById("seat-grid");
    grid.innerHTML = "";

    Object.keys(rows).sort().forEach(rowLabel => {
      const rowDiv = document.createElement("div");
      rowDiv.className = "seat-row";

      const label = document.createElement("span");
      label.className   = "row-label";
      label.textContent = rowLabel;
      rowDiv.appendChild(label);

      rows[rowLabel].forEach(seat => {
        const btn = document.createElement("button");
        btn.className   = `seat ${seat.status === 'booked' ? 'booked' : 'available'}`;
        btn.textContent = seat.seat_number;
        btn.dataset.seat = seat.seat_number;
        btn.disabled    = seat.status === 'booked';

        if (seat.status !== 'booked') {
          btn.addEventListener("click", () => toggleSeat(btn, seat.seat_number));
        }

        rowDiv.appendChild(btn);
      });

      grid.appendChild(rowDiv);
    });

  } catch (err) {
    document.getElementById("seat-grid").innerHTML =
      `<p style="color:red;">Error loading seats: ${err.message}</p>`;
  }
}

/*
====================================================
 TOGGLE SEAT SELECTION
====================================================
*/
function toggleSeat(btn, seatNumber) {
  const isSelected = selectedSeats.includes(seatNumber);

  if (isSelected) {
    // Deselect
    selectedSeats = selectedSeats.filter(s => s !== seatNumber);
    btn.classList.remove("selected");
    btn.classList.add("available");
  } else {
    if (selectedSeats.length >= requiredCount) {
      alert(`You can only select ${requiredCount} seat(s). Deselect one first.`);
      return;
    }
    // Select
    selectedSeats.push(seatNumber);
    btn.classList.remove("available");
    btn.classList.add("selected");
  }

  document.getElementById("selected-count").textContent = selectedSeats.length;
  document.getElementById("proceed-btn").disabled = selectedSeats.length !== requiredCount;
}

/*
====================================================
 PROCEED TO PAYMENT
 Calls /payments/create-order with selected seats,
 stores result in sessionStorage, redirects to /payment
====================================================
*/
async function proceedToPayment() {
  if (selectedSeats.length !== requiredCount) {
    alert(`Please select exactly ${requiredCount} seat(s)`);
    return;
  }

  document.getElementById("proceed-btn").disabled   = true;
  document.getElementById("proceed-btn").textContent = "Processing...";

  try {
    const meta = JSON.parse(sessionStorage.getItem("seat_selection_meta"));

    const data = await apiRequest("/payments/create-order", "POST", {
      event_id:       meta.event_id,
      tickets_booked: meta.tickets_booked,
      selected_seats: selectedSeats,
    }, true);

    // Store for payment page
    sessionStorage.setItem("razorpay_order", JSON.stringify(data));
    sessionStorage.removeItem("seat_selection_meta");

    window.location.href = "/payment";

  } catch (err) {
    alert("Could not initiate payment: " + err.message);
    document.getElementById("proceed-btn").disabled   = false;
    document.getElementById("proceed-btn").textContent = "Proceed to Payment →";
  }
}
