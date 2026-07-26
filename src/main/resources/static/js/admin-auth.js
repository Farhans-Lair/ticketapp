(function () {

  // Read role from sessionStorage (per-tab, not shared across tabs).

  const role = sessionStorage.getItem("role");

  // Check both token existence AND role — a regular user's token would otherwise pass the token-only
  if (role !== 'admin') {
    window.location.replace("/");
  }
})();
