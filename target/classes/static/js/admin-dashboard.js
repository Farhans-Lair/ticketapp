// admin-dashboard.js
// Event management has moved to the organizer dashboard.
// This file now only provides the pageshow reload guard and logout —
// everything else is handled inline in admin-dashboard.html.

window.addEventListener("pageshow", function (event) {
  if (event.persisted) {
    window.location.reload();
  }
});
