/* payment.js — Feature 7: coupon apply + discount in summary */
let appliedCoupon    = null;   // { code, discountAmount, finalAmount }
let currentBreakdown = null;

document.addEventListener("DOMContentLoaded", async () => {
  try {
    const session = await apiRequest("/auth/me", "GET");
    sessionStorage.setItem("userId", String(session.userId));
  } catch { return; }

  const raw = sessionStorage.getItem("razorpay_order");
  if (!raw) { alert("No payment session found."); window.location.replace("/events-page"); return; }
  const orderData = JSON.parse(raw);
  currentBreakdown = orderData.breakdown;
  renderSummary(currentBreakdown, 0);
  renderPayButton(orderData);
});

/* ── RENDER SUMMARY ─────────────────────────────────────────────────────── */
function renderSummary(b, discountAmt) {
  const seatsRow = b.selected_seats && b.selected_seats.length > 0
    ? `<tr><td>Seats</td><td>${b.selected_seats.join(", ")}</td></tr>` : "";
  const discountRow = discountAmt > 0
    ? `<tr class="discount-row"><td>🎉 Coupon Discount</td><td>−₹${discountAmt.toFixed(2)}</td></tr>` : "";
  const finalTotal = (b.total_paid - discountAmt).toFixed(2);

  document.getElementById("summary").innerHTML = `
    <h3>${b.event_title}</h3>
    <table class="summary-table">
      <tr><td>Tickets</td><td>${b.tickets_booked}</td></tr>
      ${seatsRow}
      <tr><td>Ticket Amount</td><td>₹${b.ticket_amount.toFixed(2)}</td></tr>
      <tr><td>Convenience Fee</td><td>₹${b.convenience_fee.toFixed(2)}</td></tr>
      <tr><td>GST</td><td>₹${b.gst_amount.toFixed(2)}</td></tr>
      ${discountRow}
      <tr class="total-row"><td><strong>Total Payable</strong></td><td><strong>₹${finalTotal}</strong></td></tr>
    </table>`;
}

/* ── APPLY COUPON ────────────────────────────────────────────────────────── */
async function applyCoupon() {
  const code = document.getElementById('coupon-input').value.trim().toUpperCase();
  const fb   = document.getElementById('coupon-feedback');
  if (!code) { fb.textContent = 'Please enter a coupon code.'; fb.className = 'coupon-feedback err'; return; }

  fb.textContent = 'Checking…'; fb.className = 'coupon-feedback';
  try {
    const orderAmt = currentBreakdown ? currentBreakdown.total_paid : 0;
    const result   = await apiRequest('/coupons/validate', 'POST', { code, orderAmount: orderAmt }, true);
    if (result.valid) {
      appliedCoupon = result;
      fb.textContent = `✅ Coupon applied — saving ₹${result.discountAmount.toFixed(2)}!`;
      fb.className   = 'coupon-feedback ok';
      renderSummary(currentBreakdown, result.discountAmount);
    } else {
      appliedCoupon = null;
      fb.textContent = `❌ ${result.reason}`;
      fb.className   = 'coupon-feedback err';
      renderSummary(currentBreakdown, 0);
    }
  } catch (err) {
    fb.textContent = '❌ Could not validate coupon.';
    fb.className   = 'coupon-feedback err';
  }
}

/* ── RAZORPAY PAY BUTTON ─────────────────────────────────────────────────── */
function renderPayButton(orderData) {
  document.getElementById("pay-btn").addEventListener("click", () => {
    const options = {
      key:         orderData.key_id,
      amount:      orderData.amount,
      currency:    orderData.currency,
      name:        "TicketVerse",
      description: orderData.breakdown.event_title,
      order_id:    orderData.order_id,
      handler: async (response) => {
        await verifyAndConfirm(response, orderData.meta);
      },
      theme: { color: "#f5c842" },
      modal: {
        ondismiss: () => {
          document.getElementById("status-msg").textContent = "Payment cancelled. You can try again.";
          document.getElementById("pay-btn").disabled = false;
        }
      }
    };
    document.getElementById("pay-btn").disabled = true;
    new Razorpay(options).open();
  });
}

/* ── VERIFY & CONFIRM ────────────────────────────────────────────────────── */
async function verifyAndConfirm(response, meta) {
  const statusMsg = document.getElementById("status-msg");
  statusMsg.textContent = "Verifying payment…";
  try {
    const payload = {
      razorpay_order_id:   response.razorpay_order_id,
      razorpay_payment_id: response.razorpay_payment_id,
      razorpay_signature:  response.razorpay_signature,
      event_id:            meta.event_id,
      tickets_booked:      meta.tickets_booked,
      selected_seats:      meta.selected_seats || [],
    };
    // Feature 7: pass coupon code to backend so it can be atomically redeemed
    if (appliedCoupon && appliedCoupon.code) payload.coupon_code = appliedCoupon.code;

    const result = await apiRequest("/payments/verify", "POST", payload, true);
    sessionStorage.removeItem("razorpay_order");
    statusMsg.innerHTML = `<span style="color:var(--green);font-size:1.1rem;">✅ ${result.message}</span><br/>Redirecting to your bookings…`;
    document.getElementById("pay-btn").style.display = "none";
    setTimeout(() => window.location.replace("/my-bookings"), 2000);
  } catch (err) {
    statusMsg.innerHTML = `<span style="color:var(--red);">❌ Verification failed: ${err.message}</span>`;
    document.getElementById("pay-btn").disabled = false;
  }
}
