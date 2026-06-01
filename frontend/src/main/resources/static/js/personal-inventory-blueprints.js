/*
 * Personal Inventory — Blueprints sub-page logic (#327, Phase 5).
 *
 * Responsibilities:
 *  - Debounced type-ahead against /personal-inventory/blueprints/search, rendering
 *    products with an "already owned" marker (owned hits are not stageable).
 *  - Multi-select staging: tick several hits into a chip area, keep searching,
 *    then "Add selected" -> POST the staged keys to the batch proxy, toast the
 *    added/skipped counts, and reload to show the new owned rows.
 *  - Open/close KRT-styled edit-note and remove modals (no native confirm()/alert()).
 *
 * Wiring: uses the global `krtEvents.on` delegation helper and `data-trigger="bp-…"`
 * attributes (CSP-nonce-safe, no inline onclick). Translatable strings come from
 * window.krtBlueprintsI18n; endpoints from window.krtBlueprintsEndpoints (the
 * per-row URLs carry an `__ID__` placeholder the module substitutes).
 */
(function () {
    'use strict';

    let searchInput = null;
    let resultsEl = null;
    let stagingListEl = null;
    let addSelectedBtn = null;
    let editModal = null;
    let editForm = null;
    let deleteModal = null;
    let deleteForm = null;
    let debounceTimer = null;

    // productKey -> display name of staged (not-yet-added) blueprints.
    const staged = new Map();

    function $(id) { return document.getElementById(id); }

    function i18n() { return window.krtBlueprintsI18n || {}; }

    function endpoints() { return window.krtBlueprintsEndpoints || {}; }

    // Escape HTML meta-characters before any value is written via innerHTML. Implemented as a
    // self-contained replace chain (not a delegate to window.escapeHtml) so it is an unconditional,
    // statically-recognizable HTML-escape barrier on every path (CodeQL js/xss-through-dom).
    function esc(v) {
        return String(v == null ? '' : v)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function init() {
        searchInput = $('krt-bp-search-input');
        resultsEl = $('krt-bp-search-results');
        stagingListEl = $('krt-bp-staging-list');
        addSelectedBtn = $('krt-bp-add-selected');
        editModal = $('krt-bp-edit-modal');
        editForm = $('krt-bp-edit-form');
        deleteModal = $('krt-bp-delete-modal');
        deleteForm = $('krt-bp-delete-form');

        if (searchInput) {
            searchInput.addEventListener('input', onSearchInput);
            searchInput.addEventListener('focus', onSearchInput);
            document.addEventListener('click', function (e) {
                if (!resultsEl) return;
                if (e.target !== searchInput && !resultsEl.contains(e.target)) {
                    resultsEl.hidden = true;
                }
            });
        }
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') { closeEdit(); closeDelete(); }
        });
        if (addSelectedBtn) addSelectedBtn.addEventListener('click', addSelected);
        renderStaging();
    }

    /* ----------------------------------------------------------------- search */

    function onSearchInput() {
        if (debounceTimer) clearTimeout(debounceTimer);
        debounceTimer = setTimeout(runSearch, 250);
    }

    function runSearch() {
        if (!searchInput || !resultsEl) return;
        const q = searchInput.value || '';
        const url = (endpoints().search || '/personal-inventory/blueprints/search')
                + '?q=' + encodeURIComponent(q) + '&limit=25';
        resultsEl.hidden = false;
        resultsEl.innerHTML = '<div class="krt-pi-typeahead-loading">'
                + esc(i18n().searching || 'Suche...') + '</div>';
        fetch(url, { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function (resp) { return resp.ok ? resp.json() : []; })
            .then(renderResults)
            .catch(function () { renderResults([]); });
    }

    function renderResults(items) {
        if (!resultsEl) return;
        if (!items || items.length === 0) {
            resultsEl.innerHTML = '<div class="krt-pi-typeahead-empty">'
                    + esc(i18n().noResults || 'Keine Treffer') + '</div>';
            return;
        }
        let html = '';
        items.forEach(function (it) {
            const isStaged = staged.has(it.productKey);
            const blocked = it.ownedByCurrentUser;
            const cls = 'krt-pi-typeahead-item krt-bp-result'
                    + (blocked ? ' krt-bp-result-owned' : '')
                    + (isStaged ? ' krt-bp-result-staged' : '');
            const variants = (it.variantCount && it.variantCount > 1)
                    ? (it.variantCount + ' ' + esc(i18n().variants || 'Varianten'))
                    : esc(it.manufacturerName || '');
            html += '<button type="button" class="' + cls + '"'
                    + ' data-key="' + esc(it.productKey) + '"'
                    + ' data-name="' + esc(it.name) + '"'
                    + (blocked ? ' disabled' : '') + '>'
                    + '<span class="krt-pi-typeahead-name">' + esc(it.name || '') + '</span>'
                    + '<span class="krt-pi-typeahead-meta">'
                    + (blocked ? esc(i18n().owned || 'Bereits vorhanden') : variants)
                    + '</span>'
                    + '</button>';
        });
        resultsEl.innerHTML = html;
        resultsEl.querySelectorAll('.krt-bp-result').forEach(function (btn) {
            if (btn.disabled) return;
            btn.addEventListener('click', function () {
                toggleStaged(btn.getAttribute('data-key'), btn.getAttribute('data-name'));
                btn.classList.toggle('krt-bp-result-staged', staged.has(btn.getAttribute('data-key')));
            });
        });
    }

    /* ---------------------------------------------------------------- staging */

    function toggleStaged(key, name) {
        if (!key) return;
        if (staged.has(key)) {
            staged.delete(key);
        } else {
            staged.set(key, name || key);
        }
        renderStaging();
    }

    function renderStaging() {
        if (!stagingListEl) return;
        if (staged.size === 0) {
            stagingListEl.innerHTML = '<span class="krt-bp-staging-empty">'
                    + esc(stagingListEl.getAttribute('data-empty-text') || i18n().emptyStaging || '') + '</span>';
        } else {
            let html = '';
            staged.forEach(function (name, key) {
                html += '<span class="krt-bp-chip" data-key="' + esc(key) + '">'
                        + '<span class="krt-bp-chip-name">' + esc(name) + '</span>'
                        + '<button type="button" class="krt-bp-chip-remove" data-key="' + esc(key) + '"'
                        + ' aria-label="remove">&times;</button>'
                        + '</span>';
            });
            stagingListEl.innerHTML = html;
            stagingListEl.querySelectorAll('.krt-bp-chip-remove').forEach(function (btn) {
                btn.addEventListener('click', function () {
                    toggleStaged(btn.getAttribute('data-key'), null);
                });
            });
        }
        if (addSelectedBtn) addSelectedBtn.disabled = staged.size === 0;
    }

    function csrfHeaders() {
        const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
        const token = document.querySelector('meta[name="_csrf"]');
        const header = document.querySelector('meta[name="_csrf_header"]');
        if (token && header && token.content && header.content) {
            headers[header.content] = token.content;
        }
        return headers;
    }

    function addSelected() {
        if (staged.size === 0) return;
        const keys = Array.from(staged.keys());
        if (addSelectedBtn) addSelectedBtn.disabled = true;
        fetch(endpoints().addSelected || '/personal-inventory/blueprints/add-selected', {
            method: 'POST',
            credentials: 'same-origin',
            headers: csrfHeaders(),
            body: JSON.stringify(keys)
        })
            .then(function (resp) { return resp.ok ? resp.json() : null; })
            .then(function (result) {
                if (!result) { showError(); return; }
                const msg = (i18n().addedLabel || 'added') + ': ' + (result.added || 0)
                        + ', ' + (i18n().skippedLabel || 'skipped') + ': '
                        + ((result.skippedAlreadyOwned || 0) + (result.skippedUnresolved || 0));
                if (window.showFrontendSuccessToast) window.showFrontendSuccessToast(msg);
                // Reload so the new owned rows render and owned-flags refresh.
                setTimeout(function () { window.location.reload(); }, 600);
            })
            .catch(function () { showError(); });
    }

    function showError() {
        if (addSelectedBtn) addSelectedBtn.disabled = staged.size === 0;
        if (window.showFrontendErrorToast) {
            window.showFrontendErrorToast(i18n().errorToast || 'Error');
        }
    }

    /* ----------------------------------------------------------------- modals */

    function resolveUrl(template, id) {
        const raw = (template || '').replace('__ID__', encodeURIComponent(id));
        return window.safeSameOriginUrl ? window.safeSameOriginUrl(raw, raw) : raw;
    }

    function openEdit(btn) {
        if (!editModal || !editForm) return;
        editForm.action = resolveUrl(endpoints().updateNote, btn.getAttribute('data-id'));
        setValue('krt-bp-edit-version', btn.getAttribute('data-version'));
        setValue('krt-bp-edit-acquired', normalizeAcquired(btn.getAttribute('data-acquired-at')));
        setValue('krt-bp-edit-note', btn.getAttribute('data-note'));
        const productEl = $('krt-bp-edit-product');
        if (productEl) productEl.textContent = btn.getAttribute('data-name') || '';
        editModal.style.display = 'flex';
    }

    function closeEdit() { if (editModal) editModal.style.display = 'none'; }

    function openDelete(btn) {
        if (!deleteModal || !deleteForm) return;
        deleteForm.action = resolveUrl(endpoints().remove, btn.getAttribute('data-id'));
        const msgEl = $('krt-bp-delete-message');
        const name = btn.getAttribute('data-name');
        if (msgEl && name) {
            msgEl.textContent = (i18n().removeBody || msgEl.textContent) + ' (' + name + ')';
        }
        deleteModal.style.display = 'flex';
    }

    function closeDelete() { if (deleteModal) deleteModal.style.display = 'none'; }

    function setValue(id, value) {
        const el = $(id);
        if (el) el.value = (value == null || value === 'null') ? '' : value;
    }

    // Thymeleaf renders a null Instant attribute as the literal string "null";
    // treat that (and blank) as "no timestamp" so the hidden field stays empty.
    function normalizeAcquired(value) {
        return (!value || value === 'null') ? '' : value;
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    if (window.krtEvents && typeof window.krtEvents.on === 'function') {
        window.krtEvents.on('click', 'bp-open-edit', openEdit);
        window.krtEvents.on('click', 'bp-close-edit', closeEdit);
        window.krtEvents.on('click', 'bp-open-delete', openDelete);
        window.krtEvents.on('click', 'bp-close-delete', closeDelete);
    }
})();
