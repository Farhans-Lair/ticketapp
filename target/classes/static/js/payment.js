document.addEventListener("DOMContentLoaded", async() => {
  // ── Verify session with server via cookie ─────────────────────────────────
  try {
    const session = await apiRequest("/auth/me", "GET");
    // Store userId so auth-channel.js can match logout broadcasts from other
    // tabs of the same user. Without this, this tab stays open after logout.
    sessionStorage.setItem("userId", String(session.userId));

  } catch (err) {
    return; // api.js 401 handler redirects to "/"
  }

  const raw = sessionStorage.getItem("razorpay_order");
  if (!raw) {
    alert("No payment session found. Please select tickets again.");
    window.location.replace("/events-page");
    return;
  }
  const orderData = JSON.parse(raw);
  renderSummary(orderData.breakdown);
  renderPayButton(orderData);
});

/*
====================================================
 RENDER ORDER SUMMARY
====================================================
*/
function renderSummary(breakdown) {

  const seatsHtml = breakdown.selected_seats && breakdown.selected_seats.length > 0
    ? `<tr><td>Seats</td><td>${breakdown.selected_seats.join(", ")}</td></tr>`
    : "";

  document.getElementById("summary").innerHTML = `
    <h3>${breakdown.event_title}</h3>
    <table>
      <tr><td>Tickets</td><td>${breakdown.tickets_booked}</td></tr>${seatsHtml}
      <tr><td>Ticket Amount</td><td>₹${breakdown.ticket_amount.toFixed(2)}</td></tr>
      <tr><td>Convenience Fee</td><td>₹${breakdown.convenience_fee.toFixed(2)}</td></tr>
      <tr><td>GST (18%)</td><td>₹${breakdown.gst_amount.toFixed(2)}</td></tr>
      <tr class="total-row"><td><strong>Total Payable</strong></td><td><strong>₹${breakdown.total_paid.toFixed(2)}</strong></td></tr>
    </table>
  `;
}

/*
====================================================
 OPEN RAZORPAY CHECKOUT
====================================================
*/
function renderPayButton(orderData) {
  document.getElementById("pay-btn").addEventListener("click", () => {
    const options = {
      key:          orderData.key_id,
      amount:       orderData.amount,       // paise
      currency:     orderData.currency,
      name:         "Ticket Booking App",
      description:  orderData.breakdown.event_title,
      order_id:     orderData.order_id,

      handler: async function (response) {
        // Called by Razorpay on successful payment
        await verifyAndConfirm(response, orderData.meta);
      },

      prefill: {
        // Optional: pre-fill user email/phone if available
        name:  "",
        email: "",
      },

      theme: {
        color: "#4f46e5",
      },

      modal: {
        ondismiss: function () {
          document.getElementById("status-msg").textContent =
            "Payment cancelled. You can try again.";
          document.getElementById("pay-btn").disabled = false;
        },
      },
    };

    document.getElementById("pay-btn").disabled = true;
    const rzp = new Razorpay(options);
    rzp.open();
  });
}

/*
====================================================
 VERIFY SIGNATURE + CONFIRM BOOKING
====================================================
*/
async function verifyAndConfirm(response, meta) {
  const statusMsg = document.getElementById("status-msg");
  statusMsg.textContent = "Verifying payment...";

  try {
    const result = await apiRequest("/payments/verify", "POST", {
      razorpay_order_id:   response.razorpay_order_id,
      razorpay_payment_id: response.razorpay_payment_id,
      razorpay_signature:  response.razorpay_signature,
      event_id:            meta.event_id,
      tickets_booked:      meta.tickets_booked,
      selected_seats:      meta.selected_seats || [],
    }, true);

    // Clean up session
    sessionStorage.removeItem("razorpay_order");

    statusMsg.innerHTML = `
      <span style="color:green; font-size:1.2rem;">
        ✅ ${result.message}
      </span>
      <br/>Redirecting to your bookings...
    `;

    document.getElementById("pay-btn").style.display = "none";

    setTimeout(() => {
      window.location.replace("/my-bookings");
    }, 2000);

  } catch (err) {
    statusMsg.innerHTML = `
      <span style="color:red;">
        ❌ Verification failed: ${err.message}
      </span>
    `;
    document.getElementById("pay-btn").disabled = false;
  }
}
