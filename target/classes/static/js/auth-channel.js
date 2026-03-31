/**
 * auth-channel.js — Cross-tab session synchronisation
 *
 * Included in every protected page (events, my-bookings, seat-selection,
 * payment, admin-dashboard). Uses the BroadcastChannel API so that when a
 * user logs out in any one tab, ALL other tabs belonging to that same user
 * are immediately redirected to the login page.
 *
 * Key design decisions:
 *  • Messages are matched by userId (stored in sessionStorage on login), NOT by
 *    role. This means an admin logging out does NOT affect a simultaneously
 *    open user session and vice-versa.
 *  • The channel is same-origin only — messages never cross domains.
 *  • window._authChannel is exposed so each page's logout function can call
 *    _authChannel.postMessage({ type: 'LOGOUT', userId }) before clearing
 *    sessionStorage and redirecting.
 */
(function () {
  'use strict';

  const CHANNEL_NAME = 'ticketverse_auth';

  // BroadcastChannel is supported in all modern browsers (Chrome 54+,
  // Firefox 38+, Safari 15.4+). If somehow unavailable, we degrade
  // gracefully — logout still works in the current tab, it just won't
  // propagate to others.
  if (!window.BroadcastChannel) {
    window._authChannel = null;
    return;
  }

  const channel = new BroadcastChannel(CHANNEL_NAME);

  /**
   * React to a LOGOUT broadcast from another tab.
   * Only redirects if the userId in the message matches THIS tab's userId.
   */
  channel.onmessage = function (event) {
    const msg = event.data;

    if (!msg || msg.type !== 'LOGOUT') return;


    // Read userId from sessionStorage (per-tab) — NOT localStorage.
    // With localStorage, admin logging in on Tab B would overwrite the user's
    // userId, causing this tab to match the wrong broadcast and log out.
    const myUserId = sessionStorage.getItem('userId');

    // Guard: only act when our userId matches the broadcaster's userId.
    // This ensures:
    //   - User Tab B logs out when User Tab A broadcasts (same userId).
    //   - Admin Tab is unaffected when a user broadcasts (different userId).
    //   - User Tab is unaffected when admin broadcasts (different userId).
    if (myUserId && myUserId === String(msg.userId)) {
      sessionStorage.clear();
      window.location.replace('/');
    }
  };

  // Expose channel to page scripts so logout functions can broadcast.
  window._authChannel = channel;
})();
