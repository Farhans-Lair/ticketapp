// ════════════════════════════════════════════════════════════════════════════
//  HELPER
// ════════════════════════════════════════════════════════════════════════════

function showStep(stepId) {
  document.getElementById(stepId).classList.add("visible");
}

function hideStep(stepId) {
  document.getElementById(stepId).classList.remove("visible");
}

// ════════════════════════════════════════════════════════════════════════════
//  SIGNUP FLOW
// ════════════════════════════════════════════════════════════════════════════

/**
 * Step 1 – Send OTP to the entered email.
 */
async function signupRequest() {
  const name     = document.getElementById("registerName").value.trim();
  const email    = document.getElementById("registerEmail").value.trim();
  const password = document.getElementById("registerPassword").value;

  if (!name || !email || !password) {
    alert("Please fill in all fields.");
    return;
  }

  try {
    await apiRequest("/auth/signup-request", "POST", { name, email, password });

    // Transition to OTP step
    document.getElementById("registerStep1").style.display = "none";
    document.getElementById("registerEmailDisplay").textContent = email;
    showStep("registerStep2");
  } catch (err) {
    alert("Could not send verification code: " + err.message);
  }
}

/**
 * Step 2 – Verify OTP and create the account.
 */
async function signupVerify() {
  const email = document.getElementById("registerEmail").value.trim();
  const otp   = document.getElementById("registerOtp").value.trim();

  if (!otp || otp.length !== 6) {
    alert("Please enter the 6-digit code sent to your email.");
    return;
  }

  try {
    await apiRequest("/auth/signup-verify", "POST", { email, otp });

    alert("Registration successful! Please log in.");

    // Reset signup section
    document.getElementById("registerStep1").style.display = "";
    hideStep("registerStep2");
    document.getElementById("registerName").value     = "";
    document.getElementById("registerEmail").value    = "";
    document.getElementById("registerPassword").value = "";
    document.getElementById("registerOtp").value      = "";
  } catch (err) {
    alert("Verification failed: " + err.message);
  }
}

/**
 * Resend signup OTP (re-uses the same step-1 values still in the fields).
 */
async function signupResend() {
  const btn      = document.getElementById("signupResendBtn");
  const name     = document.getElementById("registerName").value.trim();
  const email    = document.getElementById("registerEmail").value.trim();
  const password = document.getElementById("registerPassword").value;

  btn.disabled = true;
  btn.textContent = "Sending…";

  try {
    await apiRequest("/auth/signup-request", "POST", { name, email, password });
    alert("A new code has been sent to " + email);
    document.getElementById("registerOtp").value = "";
  } catch (err) {
    alert("Could not resend code: " + err.message);
  } finally {
    // Re-enable after 30 s to prevent spam
    setTimeout(() => {
      btn.disabled = false;
      btn.textContent = "Resend code";
    }, 30000);
  }
}

// ════════════════════════════════════════════════════════════════════════════
//  LOGIN FLOW  (works for both user and admin — role is returned after OTP)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Step 1 – Validate credentials and send OTP.
 */
async function loginRequest() {
  const email    = document.getElementById("loginEmail").value.trim();
  const password = document.getElementById("loginPassword").value;

  if (!email || !password) {
    alert("Please enter your email and password.");
    return;
  }

  try {
    await apiRequest("/auth/login-request", "POST", { email, password });

    // Transition to OTP step
    document.getElementById("loginStep1").style.display = "none";
    document.getElementById("loginEmailDisplay").textContent = email;
    showStep("loginStep2");
  } catch (err) {
    alert("Login failed: " + err.message);
  }
}

/**
 * Step 2 – Verify OTP and receive JWT.
 */
async function loginVerify() {
  const email = document.getElementById("loginEmail").value.trim();
  const otp   = document.getElementById("loginOtp").value.trim();

  if (!otp || otp.length !== 6) {
    alert("Please enter the 6-digit code sent to your email.");
    return;
  }

  try {
    const res = await fetch(`${API_BASE_URL}/auth/login-verify`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials : "include",
      body: JSON.stringify({ email, otp }),
    });

    const data = await res.json();

    if (!res.ok) {
      alert(data.error || "Verification failed. Please try again.");
      return;
    }

    // Store token, role, and userId in sessionStorage (per-tab).
    // sessionStorage is isolated per tab — each tab holds its own token so
    // admin on Tab A and user on Tab B never interfere with each other's
    // API calls, even though they share one origin-wide cookie.
    sessionStorage.setItem("token",  data.token);
    sessionStorage.setItem("role",   data.role);
    sessionStorage.setItem("userId", String(data.userId));


    // Role-based redirect
    if (data.role === "admin") {
      window.location.href = "/admin";
    } else if (data.role === "organizer") {
      window.location.href = "/organizer-dashboard";
    } else {
      window.location.href = "/events-page";
    }
  } catch (err) {
    console.error(err);
    alert("Login verification failed. Please try again.");
  }
}

/**
 * Resend login OTP.
 */
async function loginResend() {
  const btn      = document.getElementById("loginResendBtn");
  const email    = document.getElementById("loginEmail").value.trim();
  const password = document.getElementById("loginPassword").value;

  btn.disabled = true;
  btn.textContent = "Sending…";

  try {
    await apiRequest("/auth/login-request", "POST", { email, password });
    alert("A new code has been sent to " + email);
    document.getElementById("loginOtp").value = "";
  } catch (err) {
    alert("Could not resend code: " + err.message);
  } finally {
    setTimeout(() => {
      btn.disabled = false;
      btn.textContent = "Resend code";
    }, 30000);
  }
}
