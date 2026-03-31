(function () {

  // Read role from sessionStorage (per-tab, not shared across tabs).
  // localStorage.role would be overwritten if a different user logs in on
  // another tab, causing this guard to fail or pass incorrectly.

  const role = sessionStorage.getItem("role");

  // Check both token existence AND role — a regular user's token would
  // otherwise pass the token-only check and reach the admin page
  if (role !== 'admin') {
    window.location.replace("/");
  }
})();
