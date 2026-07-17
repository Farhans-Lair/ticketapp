const API_BASE_URL = window.location.hostname === "localhost"
  ? ""   // local: same origin (Express on port 3000)
  : "";  // AWS:   same origin (Express behind ALB)

// ── Access-token refresh ────────────────────────────────────────────────────
// Access tokens now expire after 15 minutes (down from 24 hours), backed by
// a 7-day refresh token rotated on every use. Without this, every tab would
// get silently logged out every 15 minutes. On any 401, we try ONE refresh
// before giving up and redirecting to login. `refreshInFlight` collapses
// concurrent 401s (e.g. several API calls firing around the same time) into
// a single network request instead of racing multiple refresh attempts.
//
// IMPORTANT: this tab's OWN refresh token (from sessionStorage) is sent
// explicitly in the request body — NOT left to the httpOnly cookie alone.
// Cookies are shared per browser origin, not per tab: if a different user
// logged in on another tab more recently, the cookie would hold THEIR
// refresh token, and refreshing off the cookie would silently continue
// this tab's session as the wrong user. Same reasoning as the Authorization
// header pattern below.
let refreshInFlight = null;

async function tryRefreshToken() {
  if (!refreshInFlight) {
    const tabRefreshToken = sessionStorage.getItem("refreshToken");

    refreshInFlight = fetch(`${API_BASE_URL}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",   // cookie sent too, as a fallback for non-JS callers
      body: JSON.stringify({ refreshToken: tabRefreshToken }),
    })
      .then(async (res) => {
        if (!res.ok) return null;
        const data = await res.json();
        if (!data.token) return null;

        // CRITICAL: the refresh token just sent is now revoked (rotation) —
        // this tab's stored copy MUST be replaced with the new one, or the
        // next refresh attempt will present a revoked token and trigger
        // reuse detection, which kills the whole session.
        if (data.refreshToken) {
          sessionStorage.setItem("refreshToken", data.refreshToken);
        }
        return data.token;
      })
      .catch(() => null)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

// ── Logout ───────────────────────────────────────────────────────────────
// NOTE: each page's own logout() function (admin-revenue.js, events.js,
// my-bookings.js) posts to /auth/logout directly with this tab's own
// sessionId from sessionStorage, rather than calling a shared helper here —
// they each also broadcast a LOGOUT message to other tabs of the SAME user
// via window._authChannel, which varies slightly per page's redirect target.

async function apiRequest(path, method = "GET", body = null, auth = false, _isRetry = false) {
  const headers = {
    "Content-Type": "application/json",
  };

  // ── Per-tab token via Authorization header ────────────────────────────────
  // The browser shares ONE cookie per origin across all tabs. If two different
  // users (admin + regular user) are logged in on different tabs simultaneously,
  // the second login overwrites the shared cookie — breaking the first user's
  // API calls.
  //
  // Fix: on login, auth.js stores the JWT in sessionStorage (per-tab). Here we
  // always send it as Authorization: Bearer so the backend middleware uses THIS
  // tab's own token, not the shared cookie. The cookie is kept only as fallback.
  const tabToken = sessionStorage.getItem("token");
  if (tabToken) {
    headers["Authorization"] = `Bearer ${tabToken}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    credentials: "include",   // still sends cookie as fallback
    body: body ? JSON.stringify(body) : null,
  });

  if (!response.ok) {
    // Access token expired (or missing) — try a silent refresh once before
    // giving up. Skip this on the auth endpoints themselves to avoid loops,
    // and only ever retry once per original call (_isRetry guards that).
    const isAuthEndpoint = path.startsWith("/auth/");
    if (response.status === 401 && !_isRetry && !isAuthEndpoint) {
      const newToken = await tryRefreshToken();
      if (newToken) {
        sessionStorage.setItem("token", newToken);
        return apiRequest(path, method, body, auth, true);   // retry once with the fresh token
      }
    }

    if (response.status === 401) {
      sessionStorage.clear();
      window.location.replace("/");
      return;
    }

    // Spring Boot GlobalExceptionHandler returns { "error": "message" }
    // Extract it so alert() shows a clean message instead of raw JSON.
    try {
      const json = await response.json();
      throw new Error(json.error || json.message || "Request failed");
    } catch (parseErr) {
      if (parseErr instanceof SyntaxError) {
        throw new Error("Request failed");
      }
      throw parseErr;
    }
  }

  // 204 No Content — nothing to parse
  if (response.status === 204) return null;

  return response.json();
}
