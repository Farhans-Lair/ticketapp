/* auth-channel.js — Cross-tab session synchronisation Included in every protected page (events, my-bookings, seat-selection, payment, admin-dashboard). */
(function () {
  'use strict';

  const CHANNEL_NAME = 'ticketverse_auth';

  // BroadcastChannel is supported in all modern browsers (Chrome 54+, Firefox 38+, Safari 15.4+).
  if (!window.BroadcastChannel) {
    window._authChannel = null;
    return;
  }

  const channel = new BroadcastChannel(CHANNEL_NAME);

  /* React to a LOGOUT broadcast from another tab. */
  channel.onmessage = function (event) {
    const msg = event.data;

    if (!msg || msg.type !== 'LOGOUT') return;

    // Read userId from sessionStorage (per-tab) — NOT localStorage.
    const myUserId = sessionStorage.getItem('userId');

    if (myUserId && myUserId === String(msg.userId)) {
      sessionStorage.clear();
      window.location.replace('/');
    }
  };

  // Expose channel to page scripts so logout functions can broadcast.
  window._authChannel = channel;
})();
