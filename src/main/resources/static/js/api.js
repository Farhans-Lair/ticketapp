const API_BASE_URL = window.location.hostname === "localhost"
  ? ""     // local: same origin (Express on port 3000)
  : "";    // AWS: same origin (Express behind ALB)

// Access-token refresh Access tokens now expire after 15 minutes (down from 24 hours), backed by a
let refreshInFlight = null;

async function tryRefreshToken() {
  if (!refreshInFlight) {
    const tabRefreshToken = sessionStorage.getItem("refreshToken");

    refreshInFlight = fetch(`${API_BASE_URL}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",     // cookie sent too, as a fallback for non-JS callers
      body: JSON.stringify({ refreshToken: tabRefreshToken }),
    })
      .then(async (res) => {
        if (!res.ok) return null;
        const data = await res.json();
        if (!data.token) return null;

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

// Logout NOTE: each page's own logout() function (admin-revenue.js, events.js, my-bookings.js) posts to /auth/logout directly with this

async function apiRequest(path, method = "GET", body = null, auth = false, _isRetry = false) {
  const headers = {
    "Content-Type": "application/json",
  };

  // Per-tab token via Authorization header The browser shares ONE cookie per origin across all tabs.
  const tabToken = sessionStorage.getItem("token");
  if (tabToken) {
    headers["Authorization"] = `Bearer ${tabToken}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    credentials: "include",     // still sends cookie as fallback
    body: body ? JSON.stringify(body) : null,
  });

  if (!response.ok) {
    // Access token expired (or missing) — try a silent refresh once before giving up.
    const isAuthEndpoint = path.startsWith("/auth/");
    if (response.status === 401 && !_isRetry && !isAuthEndpoint) {
      const newToken = await tryRefreshToken();
      if (newToken) {
        sessionStorage.setItem("token", newToken);
        return apiRequest(path, method, body, auth, true);     // retry once with the fresh token
      }
    }

    if (response.status === 401) {
      sessionStorage.clear();
      window.location.replace("/");
      return;
    }

    // Spring Boot GlobalExceptionHandler returns { "error": "message" } Extract it so alert() shows a clean message
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
