/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * Notification bell + always-on unread badge (epic #622, REQ-NOTIF-005/006).
 *
 * - Lazily loads the recent notifications into the bell dropdown on open.
 * - Marks read / deletes individual notifications and the bulk actions with no page reload via
 *   window.krtFetch (CSRF + 403-retry handled centrally); destructive actions confirm through the
 *   non-native window.showKrtConfirm dialog.
 * - Keeps the unread badge fresh from the server (the single source of truth) after every mutation
 *   and on a 60s poll, paused while the tab is hidden. Per-item DOM is patched in place so the same
 *   handlers drive both the dropdown and the full /notifications page.
 *
 * i18n strings are read from data-* attributes injected by Thymeleaf so this static file carries no
 * hardcoded user-facing text.
 */
(function () {
    const POLL_INTERVAL_MS = 60000;
    const bell = document.getElementById('notification-bell');
    const i18n = readMessages();
    let pollTimer = null;

    // Read the localized strings the templates expose so this static JS stays text-free.
    function readMessages() {
        const holder = document.getElementById('notification-i18n');
        const data = holder ? holder.dataset : {};
        return {
            loading: data.loading || 'Loading...',
            empty: data.empty || 'No notifications',
            markRead: data.markRead || 'Mark read',
            deleteLabel: data.delete || 'Delete',
            deleted: data.deleted || 'Notification deleted',
            allRead: data.allRead || 'All marked read',
            cleared: data.cleared || 'Read notifications cleared',
            confirmDeleteTitle: data.confirmDeleteTitle || 'Delete notification',
            confirmDeleteBody: data.confirmDeleteBody || 'Delete this notification?',
            confirmClearTitle: data.confirmClearTitle || 'Clear read notifications',
            confirmClearBody: data.confirmClearBody || 'Delete all read notifications?',
            confirmOk: data.confirmOk || 'Delete',
            confirmCancel: data.confirmCancel || 'Cancel',
            error: data.error || 'Action failed',
        };
    }

    function csrfRequestInit() {
        return {
            headers: { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
        };
    }

    // ---- unread badge -------------------------------------------------------

    function setBadge(count) {
        const badge = document.getElementById('notification-badge');
        if (!badge) {
            return;
        }
        const n = typeof count === 'number' ? count : 0;
        badge.textContent = n > 99 ? '99+' : String(n);
        if (n > 0) {
            badge.classList.remove('notification-badge-hidden');
        } else {
            badge.classList.add('notification-badge-hidden');
        }
    }

    function refreshUnreadCount() {
        return fetch('/notifications/unread-count', csrfRequestInit())
            .then(function (res) {
                return res.ok ? res.json() : null;
            })
            .then(function (data) {
                if (data && data.count != null) {
                    setBadge(Number(data.count));
                }
            })
            .catch(function () {
                /* a transient count refresh failure must never break the page */
            });
    }

    // ---- dropdown -----------------------------------------------------------

    function buildItem(item) {
        const li = document.createElement('li');
        li.className = 'notification-item' + (item.read ? ' is-read' : '');
        li.setAttribute('data-notif-id', item.id);
        li.setAttribute('data-notif-read', item.read ? 'true' : 'false');

        const body = document.createElement('div');
        body.className = 'notification-item-body';
        const text = document.createElement('p');
        text.className = 'notification-item-text';
        text.textContent = item.text != null ? item.text : '';
        const time = document.createElement('span');
        time.className = 'notification-item-time';
        time.textContent = item.createdAtDisplay != null ? item.createdAtDisplay : '';
        body.appendChild(text);
        body.appendChild(time);

        const actions = document.createElement('div');
        actions.className = 'notification-item-actions';
        if (!item.read) {
            actions.appendChild(
                actionButton('notif-mark-read', i18n.markRead, 'krt-icon-check', 'btn btn-icon'),
            );
        }
        actions.appendChild(
            actionButton(
                'notif-delete',
                i18n.deleteLabel,
                'krt-icon-trash',
                'btn btn-quiet-danger btn-icon',
            ),
        );

        li.appendChild(body);
        li.appendChild(actions);
        return li;
    }

    function actionButton(attr, label, icon, className) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = className;
        btn.setAttribute('data-' + attr, '');
        btn.setAttribute('aria-label', label);
        btn.title = label;
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('class', 'krt-icon');
        svg.setAttribute('aria-hidden', 'true');
        const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
        use.setAttribute('href', '#' + icon);
        svg.appendChild(use);
        btn.appendChild(svg);
        return btn;
    }

    function loadDropdown() {
        const list = document.getElementById('notification-dropdown-list');
        const empty = document.getElementById('notification-dropdown-empty');
        if (!list) {
            return;
        }
        list.innerHTML = '';
        if (empty) {
            empty.classList.add('notification-badge-hidden');
        }
        fetch('/notifications/recent', csrfRequestInit())
            .then(function (res) {
                return res.ok ? res.json() : [];
            })
            .then(function (items) {
                if (!Array.isArray(items) || items.length === 0) {
                    if (empty) {
                        empty.classList.remove('notification-badge-hidden');
                    }
                    return;
                }
                items.forEach(function (item) {
                    list.appendChild(buildItem(item));
                });
            })
            .catch(function () {
                if (empty) {
                    empty.classList.remove('notification-badge-hidden');
                }
            });
    }

    function openDropdown() {
        const dropdown = document.getElementById('notification-dropdown');
        const toggle = document.getElementById('notification-toggle');
        if (!dropdown) {
            return;
        }
        dropdown.classList.remove('notification-dropdown-hidden');
        if (toggle) {
            toggle.setAttribute('aria-expanded', 'true');
        }
        loadDropdown();
    }

    function closeDropdown() {
        const dropdown = document.getElementById('notification-dropdown');
        const toggle = document.getElementById('notification-toggle');
        if (!dropdown) {
            return;
        }
        dropdown.classList.add('notification-dropdown-hidden');
        if (toggle) {
            toggle.setAttribute('aria-expanded', 'false');
        }
    }

    function toggleDropdown() {
        const dropdown = document.getElementById('notification-dropdown');
        if (!dropdown) {
            return;
        }
        if (dropdown.classList.contains('notification-dropdown-hidden')) {
            openDropdown();
        } else {
            closeDropdown();
        }
    }

    // ---- mutations (no reload) ---------------------------------------------

    function eachItem(id, fn) {
        const nodes = document.querySelectorAll('[data-notif-id="' + cssEscape(id) + '"]');
        Array.prototype.forEach.call(nodes, fn);
    }

    function cssEscape(value) {
        if (window.CSS && typeof window.CSS.escape === 'function') {
            return window.CSS.escape(value);
        }
        return String(value).replace(/["\\]/g, '\\$&');
    }

    function markReadInPlace(li) {
        li.classList.add('is-read');
        li.setAttribute('data-notif-read', 'true');
        const btn = li.querySelector('[data-notif-mark-read]');
        if (btn) {
            btn.remove();
        }
    }

    function removeItem(li) {
        const list = li.parentElement;
        li.remove();
        refreshEmptyState(list);
    }

    function refreshEmptyState(list) {
        if (!list) {
            return;
        }
        const hasItems = list.querySelector('.notification-item');
        const empty = list.parentElement
            ? list.parentElement.querySelector('[data-notif-empty]')
            : null;
        if (empty) {
            if (hasItems) {
                empty.classList.add('notification-badge-hidden');
            } else {
                empty.classList.remove('notification-badge-hidden');
            }
        }
    }

    function doMarkRead(id, submitter) {
        if (!window.krtFetch) {
            return;
        }
        window.krtFetch
            .write({
                method: 'POST',
                url: '/notifications/' + encodeURIComponent(id) + '/read',
                toast: false,
                errorMessage: i18n.error,
                submitter: submitter,
                onSuccess: function () {
                    eachItem(id, markReadInPlace);
                },
            })
            .finally(refreshUnreadCount);
    }

    function doDelete(id, submitter) {
        if (!window.krtFetch) {
            return;
        }
        confirmThen(i18n.confirmDeleteTitle, i18n.confirmDeleteBody, function () {
            window.krtFetch
                .write({
                    method: 'DELETE',
                    url: '/notifications/' + encodeURIComponent(id),
                    successMessage: i18n.deleted,
                    errorMessage: i18n.error,
                    submitter: submitter,
                    onSuccess: function () {
                        eachItem(id, removeItem);
                    },
                })
                .finally(refreshUnreadCount);
        });
    }

    function doMarkAll(submitter) {
        if (!window.krtFetch) {
            return;
        }
        window.krtFetch
            .write({
                method: 'POST',
                url: '/notifications/read-all',
                successMessage: i18n.allRead,
                errorMessage: i18n.error,
                submitter: submitter,
                onSuccess: function () {
                    const nodes = document.querySelectorAll('.notification-item');
                    Array.prototype.forEach.call(nodes, markReadInPlace);
                },
            })
            .finally(refreshUnreadCount);
    }

    function doClearRead(submitter) {
        if (!window.krtFetch) {
            return;
        }
        confirmThen(i18n.confirmClearTitle, i18n.confirmClearBody, function () {
            window.krtFetch
                .write({
                    method: 'DELETE',
                    url: '/notifications/read',
                    successMessage: i18n.cleared,
                    errorMessage: i18n.error,
                    submitter: submitter,
                    onSuccess: function () {
                        const nodes = document.querySelectorAll('.notification-item.is-read');
                        Array.prototype.forEach.call(nodes, removeItem);
                    },
                })
                .finally(refreshUnreadCount);
        });
    }

    function confirmThen(title, body, action) {
        if (typeof window.showKrtConfirm === 'function') {
            window
                .showKrtConfirm(title, body, i18n.confirmOk, i18n.confirmCancel)
                .then(function (ok) {
                    if (ok) {
                        action();
                    }
                });
        } else {
            action();
        }
    }

    // ---- wiring -------------------------------------------------------------

    function onDocumentClick(event) {
        const markReadBtn = event.target.closest('[data-notif-mark-read]');
        if (markReadBtn) {
            const readLi = markReadBtn.closest('[data-notif-id]');
            if (readLi) {
                doMarkRead(readLi.getAttribute('data-notif-id'), markReadBtn);
            }
            return;
        }
        const deleteBtn = event.target.closest('[data-notif-delete]');
        if (deleteBtn) {
            const delLi = deleteBtn.closest('[data-notif-id]');
            if (delLi) {
                doDelete(delLi.getAttribute('data-notif-id'), deleteBtn);
            }
            return;
        }
        const markAllBtn = event.target.closest('[data-notif-mark-all]');
        if (markAllBtn) {
            doMarkAll(markAllBtn);
            return;
        }
        const clearReadBtn = event.target.closest('[data-notif-clear-read]');
        if (clearReadBtn) {
            doClearRead(clearReadBtn);
            return;
        }
        if (event.target.closest('#notification-toggle')) {
            toggleDropdown();
            return;
        }
        // Click outside the bell closes the dropdown.
        if (bell && !event.target.closest('#notification-bell')) {
            closeDropdown();
        }
    }

    function startPolling() {
        if (pollTimer || !document.getElementById('notification-badge')) {
            return;
        }
        pollTimer = window.setInterval(refreshUnreadCount, POLL_INTERVAL_MS);
    }

    function stopPolling() {
        if (pollTimer) {
            window.clearInterval(pollTimer);
            pollTimer = null;
        }
    }

    function onVisibilityChange() {
        if (document.hidden) {
            stopPolling();
        } else {
            startPolling();
            refreshUnreadCount();
        }
    }

    document.addEventListener('click', onDocumentClick);
    document.addEventListener('visibilitychange', onVisibilityChange);

    // Per-item buttons and the page-level mark-all / clear-read controls exist on the
    // /notifications page even when the bell is absent on a given render, so wiring is
    // unconditional; the badge poll only starts when a badge is present.
    startPolling();
})();
