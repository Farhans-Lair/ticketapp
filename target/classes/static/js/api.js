const API_BASE_URL = window.location.hostname === "localhost"
  ? ""   // local: same origin (Express on port 3000)
  : "";  // AWS:   same origin (Express behind ALB)

async function apiRequest(path, method = "GET", body = null, auth = false) {
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
    // Redirect to login on unauthenticated
    if (response.status === 401) {
      sessionStorage.removeItem("token");
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
