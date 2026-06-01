/*
 * Personal Inventory — Blueprints SCMDB import flow (#327, Phase 6).
 *
 * Responsibilities:
 *  - "Import JSON" -> file pick -> POST the upload to the frontend preview proxy.
 *  - Render a KRT-styled preview modal (no native dialogs) grouping entries into
 *    Auto-matched / Suggestions / Unmatched / Already owned. Suggested + unmatched
 *    rows carry an inline type-ahead (reusing the Phase 5 product search) to pick a
 *    product; every includable row carries an optional note and an include checkbox.
 *  - Multi-row resolution: select-all / select-none across includable rows, then
 *    "Apply" posts the full resolution list and toasts the summary.
 *
 * Wiring: CSP-nonce-safe via window.krtEvents delegation + data-trigger="bp-import-…".
 * Strings from window.krtBlueprintsImportI18n; endpoints from window.krtBlueprintsEndpoints.
 */
(function () {
    'use strict';

    let fileInput = null;
    let modal = null;
    let bodyEl = null;
    let summaryEl = null;
    let applyBtn = null;
    const rowDebounce = new WeakMap();

    function $(id) { return document.getElementById(id); }

    function i18n() { return window.krtBlueprintsImportI18n || {}; }

    function endpoints() { return window.krtBlueprintsEndpoints || {}; }

    // Escape HTML meta-characters on EVERY path (the fallback must sanitize too, so the
    // value is never reinterpreted as HTML when written via innerHTML — CodeQL js/xss-through-dom).
    function esc(v) {
        if (window.escapeHtml) return window.escapeHtml(v);
        return String(v == null ? '' : v)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function csrfHeaders(base) {
        const headers = base || {};
        const token = document.querySelector('meta[name="_csrf"]');
        const header = document.querySelector('meta[name="_csrf_header"]');
        if (token && header && token.content && header.content) {
            headers[header.content] = token.content;
        }
        return headers;
    }

    function init() {
        fileInput = $('krt-bp-import-file');
        modal = $('krt-bp-import-modal');
        bodyEl = $('krt-bp-import-body');
        summaryEl = $('krt-bp-import-summary');
        applyBtn = $('krt-bp-import-apply');
        if (fileInput) fileInput.addEventListener('change', onFileChosen);
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') closeModal();
        });
    }

    function pickFile() { if (fileInput) fileInput.click(); }

    function onFileChosen() {
        if (!fileInput || !fileInput.files || fileInput.files.length === 0) return;
        const file = fileInput.files[0];
        uploadPreview(file);
        fileInput.value = '';
    }

    /* ----------------------------------------------------------------- preview */

    function uploadPreview(file) {
        const fd = new FormData();
        fd.append('file', file);
        fetch(endpoints().importPreview || '/personal-inventory/blueprints/import/preview', {
            method: 'POST',
            credentials: 'same-origin',
            headers: csrfHeaders({ 'Accept': 'application/json' }),
            body: fd
        })
            .then(function (resp) { return resp.ok ? resp.json() : null; })
            .then(function (preview) {
                if (!preview) { toastError(); return; }
                renderPreview(preview);
                openModal();
            })
            .catch(function () { toastError(); });
    }

    function renderPreview(preview) {
        if (!bodyEl) return;
        if (summaryEl) {
            summaryEl.textContent = (i18n().summary || 'Entries') + ': ' + (preview.total || 0)
                    + ' · ' + (i18n().groupMatched || 'Matched') + ': '
                    + ((preview.matched || 0) + (preview.matchedByAlias || 0))
                    + ' · ' + (i18n().groupSuggested || 'Suggested') + ': ' + (preview.suggested || 0)
                    + ' · ' + (i18n().groupUnmatched || 'Unmatched') + ': ' + (preview.unmatched || 0)
                    + ' · ' + (i18n().groupOwned || 'Owned') + ': ' + (preview.alreadyOwned || 0);
        }
        const entries = preview.entries || [];
        const matched = entries.filter(function (e) {
            return e.status === 'MATCHED' || e.status === 'MATCHED_BY_ALIAS';
        });
        const suggested = entries.filter(function (e) { return e.status === 'SUGGESTED'; });
        const unmatched = entries.filter(function (e) { return e.status === 'UNMATCHED'; });
        const owned = entries.filter(function (e) { return e.status === 'ALREADY_OWNED'; });

        let html = '';
        html += renderGroup(i18n().groupMatched, matched, 'matched');
        html += renderGroup(i18n().groupSuggested, suggested, 'suggested');
        html += renderGroup(i18n().groupUnmatched, unmatched, 'unmatched');
        html += renderGroup(i18n().groupOwned, owned, 'owned');
        if (!html) html = '<p class="krt-bp-staging-empty">' + esc(i18n().nothing || 'Nothing to import.') + '</p>';
        bodyEl.innerHTML = html;
        bindRowSearch();
    }

    function renderGroup(title, entries, kind) {
        if (!entries || entries.length === 0) return '';
        let rows = '';
        entries.forEach(function (e) { rows += renderRow(e, kind); });
        return '<section class="krt-bp-imp-group">'
                + '<h3 class="krt-bp-imp-group-title">' + esc(title || '') + ' (' + entries.length + ')</h3>'
                + '<div class="krt-bp-imp-rows">' + rows + '</div>'
                + '</section>';
    }

    function renderRow(entry, kind) {
        const owned = kind === 'owned';
        const resolved = entry.productKey || '';
        const acquired = entry.suggestedAcquiredAt || '';
        const checked = (kind === 'matched' || kind === 'suggested') && !owned ? ' checked' : '';
        let resolution;
        if (kind === 'matched') {
            resolution = '<span class="krt-bp-imp-product">' + esc(entry.productName || '') + '</span>';
        } else if (owned) {
            resolution = '<span class="krt-bp-imp-owned">' + esc(i18n().ownedLabel || 'Already owned') + '</span>';
        } else {
            resolution = renderSearchControl(entry);
        }
        const acquiredHint = acquired
                ? '<span class="krt-bp-imp-date">' + esc(String(acquired).substring(0, 10)) + '</span>'
                : '';
        return '<div class="krt-bp-imp-row" data-external="' + esc(entry.externalName) + '"'
                + ' data-key="' + esc(resolved) + '" data-acquired="' + esc(acquired) + '">'
                + '<label class="krt-bp-imp-include-cell">'
                + '<input type="checkbox" class="krt-bp-imp-include"' + (owned ? ' disabled' : checked) + '>'
                + '</label>'
                + '<span class="krt-bp-imp-external">' + esc(entry.externalName || '') + '</span>'
                + '<span class="krt-bp-imp-resolution">' + resolution + acquiredHint + '</span>'
                + (owned ? '' : '<input type="text" class="krt-bp-imp-note" maxlength="2000"'
                        + ' placeholder="' + esc(i18n().notePlaceholder || '') + '">')
                + '</div>';
    }

    function renderSearchControl(entry) {
        const suggestions = entry.suggestions || [];
        let chips = '';
        suggestions.forEach(function (s) {
            chips += '<button type="button" class="krt-bp-imp-suggestion" data-key="' + esc(s.productKey) + '"'
                    + ' data-name="' + esc(s.productName) + '">' + esc(s.productName) + '</button>';
        });
        const seed = suggestions.length > 0 ? esc(suggestions[0].productName) : '';
        return '<span class="krt-bp-imp-search-wrap">'
                + '<input type="text" class="krt-bp-imp-search" autocomplete="off"'
                + ' value="' + seed + '" placeholder="' + esc(i18n().searchPlaceholder || 'Search...') + '">'
                + '<div class="krt-bp-imp-results krt-pi-typeahead-results" hidden></div>'
                + (chips ? '<span class="krt-bp-imp-suggestions">' + chips + '</span>' : '')
                + '</span>';
    }

    /* --------------------------------------------------------- per-row search */

    function bindRowSearch() {
        if (!bodyEl) return;
        bodyEl.querySelectorAll('.krt-bp-imp-suggestion').forEach(function (chip) {
            chip.addEventListener('click', function () {
                const row = chip.closest('.krt-bp-imp-row');
                setRowProduct(row, chip.getAttribute('data-key'), chip.getAttribute('data-name'));
            });
        });
        bodyEl.querySelectorAll('.krt-bp-imp-search').forEach(function (input) {
            const row = input.closest('.krt-bp-imp-row');
            const results = row ? row.querySelector('.krt-bp-imp-results') : null;
            input.addEventListener('input', function () {
                if (rowDebounce.has(input)) clearTimeout(rowDebounce.get(input));
                rowDebounce.set(input, setTimeout(function () { rowSearch(input, results, row); }, 250));
            });
            input.addEventListener('blur', function () {
                setTimeout(function () { if (results) results.hidden = true; }, 200);
            });
        });
    }

    function rowSearch(input, results, row) {
        if (!results) return;
        const q = input.value || '';
        const url = (endpoints().search || '/personal-inventory/blueprints/search')
                + '?q=' + encodeURIComponent(q) + '&limit=10';
        fetch(url, { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function (resp) { return resp.ok ? resp.json() : []; })
            .then(function (items) { renderRowResults(results, row, items); })
            .catch(function () { renderRowResults(results, row, []); });
    }

    function renderRowResults(results, row, items) {
        if (!results) return;
        if (!items || items.length === 0) {
            results.innerHTML = '<div class="krt-pi-typeahead-empty">' + esc(i18n().noResults || 'No matches') + '</div>';
            results.hidden = false;
            return;
        }
        let html = '';
        items.forEach(function (it) {
            html += '<button type="button" class="krt-pi-typeahead-item krt-bp-imp-hit"'
                    + ' data-key="' + esc(it.productKey) + '" data-name="' + esc(it.name) + '">'
                    + '<span class="krt-pi-typeahead-name">' + esc(it.name || '') + '</span>'
                    + '</button>';
        });
        results.innerHTML = html;
        results.hidden = false;
        results.querySelectorAll('.krt-bp-imp-hit').forEach(function (btn) {
            btn.addEventListener('click', function () {
                setRowProduct(row, btn.getAttribute('data-key'), btn.getAttribute('data-name'));
                results.hidden = true;
            });
        });
    }

    function setRowProduct(row, key, name) {
        if (!row) return;
        row.setAttribute('data-key', key || '');
        const input = row.querySelector('.krt-bp-imp-search');
        if (input) input.value = name || '';
        const include = row.querySelector('.krt-bp-imp-include');
        if (include && key) include.checked = true;
    }

    /* ------------------------------------------------------------------ apply */

    function selectAll(value) {
        if (!bodyEl) return;
        bodyEl.querySelectorAll('.krt-bp-imp-include').forEach(function (cb) {
            if (!cb.disabled) {
                const row = cb.closest('.krt-bp-imp-row');
                // Only tick rows that actually have a resolved product.
                if (!value || (row && row.getAttribute('data-key'))) cb.checked = value;
            }
        });
    }

    function apply() {
        if (!bodyEl) return;
        const resolutions = [];
        bodyEl.querySelectorAll('.krt-bp-imp-row').forEach(function (row) {
            const include = row.querySelector('.krt-bp-imp-include');
            const key = row.getAttribute('data-key');
            if (!include || !include.checked || !key) return;
            const noteEl = row.querySelector('.krt-bp-imp-note');
            const acquired = row.getAttribute('data-acquired');
            resolutions.push({
                externalName: row.getAttribute('data-external'),
                productKey: key,
                acquiredAt: acquired ? acquired : null,
                note: noteEl && noteEl.value ? noteEl.value : null
            });
        });
        if (resolutions.length === 0) { closeModal(); return; }
        if (applyBtn) applyBtn.disabled = true;
        fetch(endpoints().importApply || '/personal-inventory/blueprints/import/apply', {
            method: 'POST',
            credentials: 'same-origin',
            headers: csrfHeaders({ 'Content-Type': 'application/json', 'Accept': 'application/json' }),
            body: JSON.stringify(resolutions)
        })
            .then(function (resp) { return resp.ok ? resp.json() : null; })
            .then(function (result) {
                if (!result) { applyError(); return; }
                const msg = (i18n().applied || 'Import complete.') + ' '
                        + (i18n().addedLabel || 'added') + ': ' + (result.added || 0) + ', '
                        + (i18n().aliasesLabel || 'aliases') + ': ' + (result.aliasesLearned || 0) + ', '
                        + (i18n().skippedLabel || 'skipped') + ': ' + (result.skipped || 0);
                if (window.showFrontendSuccessToast) window.showFrontendSuccessToast(msg);
                setTimeout(function () { window.location.reload(); }, 700);
            })
            .catch(function () { applyError(); });
    }

    function applyError() {
        if (applyBtn) applyBtn.disabled = false;
        toastError();
    }

    function toastError() {
        if (window.showFrontendErrorToast) window.showFrontendErrorToast(i18n().error || 'Import failed.');
    }

    /* ----------------------------------------------------------------- modal */

    function openModal() { if (modal) modal.style.display = 'flex'; }

    function closeModal() { if (modal) modal.style.display = 'none'; }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    if (window.krtEvents && typeof window.krtEvents.on === 'function') {
        window.krtEvents.on('click', 'bp-import-pick', pickFile);
        window.krtEvents.on('click', 'bp-import-close', closeModal);
        window.krtEvents.on('click', 'bp-import-apply', apply);
        window.krtEvents.on('click', 'bp-import-selectall', function () { selectAll(true); });
        window.krtEvents.on('click', 'bp-import-selectnone', function () { selectAll(false); });
    }
})();
