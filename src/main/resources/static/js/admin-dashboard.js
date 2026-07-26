// admin-dashboard.js Event management has moved to the organizer dashboard.

window.addEventListener("pageshow", function (event) {
  if (event.persisted) {
    window.location.reload();
  }
});
