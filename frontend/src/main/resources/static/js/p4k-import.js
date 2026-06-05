/*
 * Admin — P4K catalog import flow (#326).
 *
 * Responsibilities:
 *  - "Datei wählen" -> file pick -> enable the Vorschau/Anwenden buttons.
 *  - "Vorschau" -> POST the picked file (multipart `file`) to the frontend preview proxy
 *    (dry run; nothing is written) and render the returned per-type count table.
 *  - "Anwenden" -> POST the same file plus `?seedNew=<checkbox>` to the apply proxy and
 *    render the result, then toast the summary.
 *
 * Wiring: CSP-nonce-safe via window.krtEvents delegation + data-trigger="p4k-…".
 * Strings from window.krtP4kImportI18n; endpoints from window.krtP4kImportEndpoints.
 */
(function () {
    'use strict';

    let fileInput = null;
    let filenameEl = null;
    let seedEl = null;
    let previewBtn = null;
    let applyBtn = null;
    let resultsEl = null;

    function $(id) { return document.getElementById(id); }

    function i18n() { return window.krtP4kImportI18n || {}; }

    function endpoints() { return window.krtP4kImportEndpoints || {}; }

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
        fileInput = $('krt-p4k-file');
        filenameEl = $('krt-p4k-filename');
        seedEl = $('krt-p4k-seed');
        previewBtn = $('krt-p4k-preview-btn');
        applyBtn = $('krt-p4k-apply-btn');
        resultsEl = $('krt-p4k-results');
        if (fileInput) fileInput.addEventListener('change', onFileChosen);
    }

    function pickFile() { if (fileInput) fileInput.click(); }

    function selectedFile() {
        if (!fileInput || !fileInput.files || fileInput.files.length === 0) return null;
        return fileInput.files[0];
    }

    function onFileChosen() {
        const file = selectedFile();
        if (filenameEl) filenameEl.textContent = file ? file.name : '';
        const hasFile = !!file;
        if (previewBtn) previewBtn.disabled = !hasFile;
        if (applyBtn) applyBtn.disabled = !hasFile;
    }

    /* --------------------------------------------------------------- transfer */

    function preview() { upload(endpoints().preview, false); }

    function apply() {
        const seed = !!(seedEl && seedEl.checked);
        const base = endpoints().apply || '/admin/p4k-import/apply';
        const url = base + (base.indexOf('?') === -1 ? '?' : '&') + 'seedNew=' + (seed ? 'true' : 'false');
        upload(url, true);
    }

    function upload(url, isApply) {
        const file = selectedFile();
        if (!file) {
            if (window.showFrontendErrorToast) window.showFrontendErrorToast(i18n().pickFirst || 'Please choose a file first.');
            return;
        }
        const fd = new FormData();
        fd.append('file', file);
        if (previewBtn) previewBtn.disabled = true;
        if (applyBtn) applyBtn.disabled = true;
        fetch(url || '/admin/p4k-import/preview', {
            method: 'POST',
            credentials: 'same-origin',
            headers: csrfHeaders({ 'Accept': 'application/json' }),
            body: fd
        })
            .then(function (resp) { return resp.ok ? resp.json() : null; })
            .then(function (result) {
                reenable();
                if (!result) { toastError(); return; }
                renderResult(result);
                const msg = isApply ? (i18n().appliedToast || 'Import applied.') : (i18n().previewToast || 'Preview ready.');
                if (window.showFrontendSuccessToast) window.showFrontendSuccessToast(msg);
            })
            .catch(function () { reenable(); toastError(); });
    }

    function reenable() {
        const hasFile = !!selectedFile();
        if (previewBtn) previewBtn.disabled = !hasFile;
        if (applyBtn) applyBtn.disabled = !hasFile;
    }

    function toastError() {
        if (window.showFrontendErrorToast) window.showFrontendErrorToast(i18n().error || 'Import failed.');
    }

    /* ----------------------------------------------------------------- render */

    function renderResult(result) {
        if (!resultsEl) return;
        const modeEl = $('krt-p4k-mode');
        if (modeEl) modeEl.textContent = result.dryRun ? (i18n().modeDryRun || 'Preview') : (i18n().modeApplied || 'Applied');
        const seedingEl = $('krt-p4k-seeding');
        if (seedingEl) seedingEl.textContent = result.seedingEnabled ? (i18n().seedingOn || 'on') : (i18n().seedingOff || 'off');

        const rows = [
            [i18n().rowManufacturers || 'Manufacturers', result.manufacturers],
            [i18n().rowItems || 'Items', result.items],
            [i18n().rowShips || 'Ships', result.ships],
            [i18n().rowCommodities || 'Commodities', result.commodities],
            [i18n().rowBlueprints || 'Blueprints', result.blueprints]
        ];
        const body = $('krt-p4k-rows');
        if (body) {
            let html = '';
            rows.forEach(function (pair) { html += renderRow(pair[0], pair[1]); });
            body.innerHTML = html;
        }

        const ingredientsEl = $('krt-p4k-ingredients');
        if (ingredientsEl) ingredientsEl.textContent = String(result.ingredientsResolved || 0);

        const runIdLine = $('krt-p4k-runid-line');
        const runIdEl = $('krt-p4k-runid');
        if (runIdLine && runIdEl) {
            if (result.runId) {
                runIdEl.textContent = result.runId;
                runIdLine.hidden = false;
            } else {
                runIdEl.textContent = '';
                runIdLine.hidden = true;
            }
        }

        resultsEl.hidden = false;
    }

    function renderRow(label, counts) {
        const c = counts || {};
        return '<tr>'
            + '<th scope="row">' + esc(label) + '</th>'
            + '<td>' + num(c.matched) + '</td>'
            + '<td>' + num(c.uuidBackfilled) + '</td>'
            + '<td>' + num(c.uuidConflicts) + '</td>'
            + '<td>' + num(c.enriched) + '</td>'
            + '<td>' + num(c.created) + '</td>'
            + '<td>' + num(c.unmatched) + '</td>'
            + '</tr>';
    }

    function num(v) { return esc(v == null ? 0 : v); }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    if (window.krtEvents && typeof window.krtEvents.on === 'function') {
        window.krtEvents.on('click', 'p4k-pick', pickFile);
        window.krtEvents.on('click', 'p4k-preview', preview);
        window.krtEvents.on('click', 'p4k-apply', apply);
    }
})();
