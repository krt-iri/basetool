(function () {
    'use strict';

    // ---------------------------------------------------------------------------
    // Filtering + paging in place. krtFetch (fragments/head.html) owns the fragment
    // swap + in-results pagination interception + URL sync, exactly like bank-audit.js.
    // The hidden `domain` field keeps the active tab across swaps.
    // ---------------------------------------------------------------------------
    const form = document.getElementById('audit-filter-form');
    const resultsContainer = document.getElementById('audit-results');
    const resetLink = document.getElementById('audit-filter-reset');

    if (form && resultsContainer && window.krtFetch) {
        let debounceTimer = null;

        const buildUrl = function () {
            // The split datetime widget keeps the hidden from/to inputs in sync, so a plain
            // FormData serialisation already carries the canonical filter values.
            const data = new FormData(form);
            const params = new URLSearchParams();
            for (const [key, value] of data.entries()) {
                if (value !== '') params.append(key, value);
            }
            const query = params.toString();
            return '/admin/audit-log' + (query ? '?' + query : '');
        };

        const loadResults = function (url) {
            window.krtFetch.swap({
                url: url || buildUrl(),
                container: resultsContainer,
                history: true,
            });
        };

        const onFilterChange = function () {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(function () {
                loadResults();
            }, 300);
        };

        form.addEventListener('submit', function (event) {
            event.preventDefault();
            clearTimeout(debounceTimer);
            loadResults();
        });

        form.querySelectorAll('input, select').forEach(function (el) {
            el.addEventListener('input', onFilterChange);
            el.addEventListener('change', onFilterChange);
        });

        if (resetLink) {
            resetLink.addEventListener('click', function (event) {
                event.preventDefault();
                // Clear every filter but keep the active tab (the hidden domain field).
                form.querySelectorAll('input').forEach(function (el) {
                    if (el.name !== 'domain') {
                        el.value = '';
                    }
                });
                form.querySelectorAll('select').forEach(function (el) {
                    el.selectedIndex = 0;
                });
                loadResults(resetLink.getAttribute('href'));
            });
        }
    }

    // ---------------------------------------------------------------------------
    // Period export (PDF or JSON) — fetch -> blob -> hidden <a download>, the documented
    // bank.js download pattern (a real fetch is needed to attach the X-User-Time-Zone
    // header). The two modal buttons pick the format. No native dialogs: validation +
    // failures render into the modal's inline error slots.
    // ---------------------------------------------------------------------------
    const csrfHeaders = function () {
        const base = { Accept: 'application/json' };
        try {
            if (window.krtCsrf && typeof window.krtCsrf.headers === 'function') {
                return window.krtCsrf.headers(base);
            }
        } catch {
            /* fall through to the bare Accept header */
        }
        return base;
    };

    const downloadBlob = async function (url, filename, onError) {
        const headers = csrfHeaders();
        delete headers['Content-Type'];
        const tz =
            window.Intl && Intl.DateTimeFormat
                ? Intl.DateTimeFormat().resolvedOptions().timeZone
                : '';
        if (tz) {
            headers['X-User-Time-Zone'] = tz;
        }
        let response;
        try {
            response = await fetch(url, { method: 'GET', headers: headers });
        } catch {
            response = null;
        }
        if (!response || !response.ok) {
            onError();
            return;
        }
        const blob = await response.blob();
        const objectUrl = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = objectUrl;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(objectUrl);
    };

    const clearErrors = function (frm) {
        frm.querySelectorAll('.bank-field-error').forEach(function (el) {
            el.textContent = '';
        });
    };

    const showError = function (frm, field, message) {
        const slot =
            frm.querySelector('.bank-field-error[data-error-for="' + field + '"]') ||
            frm.querySelector('.bank-field-error[data-error-for="_global"]');
        if (slot) {
            slot.textContent = message;
        }
    };

    document.addEventListener('submit', function (event) {
        const frm = event.target.closest('form.audit-download-form');
        if (!frm) {
            return;
        }
        event.preventDefault();
        clearErrors(frm);
        const from = frm.querySelector('input[name="from"]');
        const to = frm.querySelector('input[name="to"]');
        const required = frm.getAttribute('data-period-required') || '';
        const downloadError = frm.getAttribute('data-download-error') || required;
        let valid = true;
        if (!from || !from.value) {
            showError(frm, 'from', required);
            valid = false;
        }
        if (!to || !to.value) {
            showError(frm, 'to', required);
            valid = false;
        }
        if (!valid) {
            return;
        }
        // The two submit buttons carry data-format; the JSON endpoint is the PDF endpoint + '.json'.
        const json = event.submitter && event.submitter.getAttribute('data-format') === 'json';
        const endpoint = frm.getAttribute('data-endpoint');
        const url =
            (json ? endpoint + '.json' : endpoint) +
            '?from=' +
            encodeURIComponent(from.value) +
            '&to=' +
            encodeURIComponent(to.value);
        const baseName = frm.getAttribute('data-filename') || 'audit.pdf';
        const filename = json ? baseName.replace(/\.pdf$/, '.json') : baseName;
        downloadBlob(url, filename, function () {
            showError(frm, '_global', downloadError);
        });
    });
})();
