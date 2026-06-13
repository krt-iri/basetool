/*
 * Bank area client behaviors (epic #556).
 *
 * Covers every interactive surface of the bank pages:
 *  - modal field priming: `data-field-*` attributes on an open-modal trigger are
 *    copied into the modal form before it opens (row-scoped id/version/name),
 *  - AJAX forms (`form.bank-ajax-form`): JSON POST/PATCH/DELETE against
 *    /api/proxy/bank/**, CSRF from the page's meta tags, success = full reload
 *    (keeps every @Version attribute in sync, see CLAUDE.md concurrency rules),
 *    errors = localized inline message next to the field the conflict names,
 *  - grants matrix toggles: one PATCH per flag click with all three flags + version,
 *  - grants filter selects: navigate to the grouped view on change,
 *  - account-create type switch: org-unit vs. area name dependent fields,
 *  - searchable user selects (holder registration / grant creation).
 */
(function () {
    'use strict';

    /** Form field names serialized as JSON numbers instead of strings. */
    const NUMBER_FIELDS = ['amount', 'version'];

    /** Form field names serialized as JSON booleans ("true"/"false" hidden inputs). */
    const BOOLEAN_FIELDS = ['active'];

    /**
     * Maps backend bank conflict codes onto the form field whose inline error slot
     * should show the localized message; unmapped codes land in the form's
     * `_global` slot.
     */
    const CODE_FIELD = {
        BANK_OVERDRAFT: 'amount',
        BANK_HOLDER_OVERDRAFT: 'amount',
        BANK_SELF_TRANSFER: 'destinationAccountId',
        BANK_GRANTEE_MISSING_ROLE: 'userId',
        BANK_HOLDER_INACTIVE: 'holderId',
    };

    /**
     * Builds the JSON + CSRF request headers from the page's meta tags (same
     * convention as mission-subresource.js).
     *
     * @returns {Object<string,string>} headers for a same-origin JSON fetch
     */
    function csrfHeaders() {
        const headers = {
            Accept: 'application/json',
            'Content-Type': 'application/json',
        };
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (token && header && header !== 'undefined' && token !== 'undefined') {
            headers[header] = token;
        }
        return headers;
    }

    /**
     * The localized fallback error text, sourced from the page (the templates set
     * `data-bank-generic-error` on <main>) so this file stays i18n-free.
     *
     * @returns {string} the generic error message
     */
    function genericError() {
        const main = document.querySelector('main[data-bank-generic-error]');
        return main ? main.getAttribute('data-bank-generic-error') : 'Error';
    }

    /**
     * Hides and empties every inline error slot of a form.
     *
     * @param {HTMLFormElement} form the bank AJAX form
     */
    function clearErrors(form) {
        form.querySelectorAll('.bank-field-error').forEach(function (el) {
            el.textContent = '';
            el.classList.remove('visible');
        });
    }

    /**
     * Shows a localized message in the error slot of the named field, falling back
     * to the form's `_global` slot and finally to an error toast.
     *
     * @param {HTMLFormElement} form the bank AJAX form
     * @param {string} field the field name the message belongs to
     * @param {string} message the localized message
     */
    function showError(form, field, message) {
        let slot = form.querySelector('.bank-field-error[data-error-for="' + field + '"]');
        if (!slot) {
            slot = form.querySelector('.bank-field-error[data-error-for="_global"]');
        }
        if (slot) {
            slot.textContent = message;
            slot.classList.add('visible');
        } else if (typeof window.showFrontendErrorToast === 'function') {
            window.showFrontendErrorToast(message);
        }
    }

    /**
     * Copies the trigger's `data-field-*` attributes into the modal: form controls
     * whose name matches the suffix (case-insensitively — the browser lowercases
     * attribute names) receive the value, `[data-bank-label]` elements receive it
     * as text. Also resets the modal's previous inline errors.
     *
     * @param {HTMLElement} trigger the clicked open-modal button
     * @param {HTMLElement} modal the overlay element being opened
     */
    function primeModal(trigger, modal) {
        const form = modal.querySelector('form.bank-ajax-form');
        if (form) {
            clearErrors(form);
        }
        for (const attr of Array.from(trigger.attributes)) {
            if (!attr.name.startsWith('data-field-')) {
                continue;
            }
            const key = attr.name.slice('data-field-'.length);
            if (form) {
                for (const el of Array.from(form.elements)) {
                    if (el.name && el.name.toLowerCase() === key) {
                        el.value = attr.value;
                    }
                }
            }
            modal.querySelectorAll('[data-bank-label]').forEach(function (el) {
                if (el.getAttribute('data-bank-label').toLowerCase() === key) {
                    el.textContent = attr.value;
                }
            });
        }
    }

    document.addEventListener('click', function (event) {
        const trigger = event.target.closest('[data-trigger="open-modal-display"][data-modal-id]');
        if (!trigger) {
            return;
        }
        const modal = document.getElementById(trigger.getAttribute('data-modal-id'));
        if (modal && modal.classList.contains('krt-modal-overlay')) {
            primeModal(trigger, modal);
        }
    });

    /**
     * Converts one form control into its JSON value: checkboxes to booleans,
     * whitelisted names to numbers/booleans, everything else stays a string.
     *
     * @param {HTMLElement} el the form control
     * @returns {*} the JSON-ready value; '' and null mark "omit this field"
     */
    function fieldValue(el) {
        if (el.type === 'checkbox') {
            return el.checked;
        }
        const value = el.value;
        if (value === '') {
            return '';
        }
        if (NUMBER_FIELDS.includes(el.name)) {
            return Number(value);
        }
        if (BOOLEAN_FIELDS.includes(el.name)) {
            return value === 'true';
        }
        return value;
    }

    /**
     * Serializes and sends one bank AJAX form: `_`-prefixed fields fill the
     * endpoint's `{placeholder}` slots instead of the body, `data-account-id`
     * is injected under `data-account-id-field` (default `accountId`), success
     * reloads the page, errors render inline.
     *
     * @param {HTMLFormElement} form the submitted form
     */
    async function submitBankForm(form) {
        clearErrors(form);
        const method = (form.getAttribute('data-method') || 'POST').toUpperCase();
        const body = {};
        const placeholders = {};
        for (const el of Array.from(form.elements)) {
            if (!el.name || el.disabled) {
                continue;
            }
            if (el.name.startsWith('_')) {
                placeholders[el.name.slice(1).toLowerCase()] = el.value;
                continue;
            }
            const value = fieldValue(el);
            if (value === '') {
                continue;
            }
            body[el.name] = value;
        }
        const accountId = form.getAttribute('data-account-id');
        if (accountId) {
            body[form.getAttribute('data-account-id-field') || 'accountId'] = accountId;
        }
        const endpoint = form
            .getAttribute('data-endpoint')
            .replace(/\{([^}]+)\}/g, function (match, name) {
                const filled = placeholders[name.toLowerCase()];
                return filled !== undefined ? encodeURIComponent(filled) : match;
            });

        const init = { method: method, headers: csrfHeaders() };
        if (method !== 'GET' && method !== 'DELETE') {
            init.body = JSON.stringify(body);
        }
        const submitButton = form.querySelector('button[type="submit"]');
        if (submitButton) {
            submitButton.disabled = true;
        }
        let response;
        try {
            response = await fetch(endpoint, init);
        } catch {
            response = null;
        }
        if (response && response.ok) {
            window.location.reload();
            return;
        }
        if (submitButton) {
            submitButton.disabled = false;
        }
        if (!response) {
            showError(form, '_global', genericError());
            return;
        }
        let payload;
        try {
            payload = await response.json();
        } catch {
            payload = null;
        }
        if (payload && payload.unauthenticated) {
            window.location.reload();
            return;
        }
        if (payload && Array.isArray(payload.fieldErrors) && payload.fieldErrors.length > 0) {
            payload.fieldErrors.forEach(function (fe) {
                showError(form, fe.field, fe.message);
            });
            return;
        }
        const message = payload && payload.message ? payload.message : genericError();
        const field =
            payload && payload.code && CODE_FIELD[payload.code]
                ? CODE_FIELD[payload.code]
                : '_global';
        showError(form, field, message);
    }

    document.addEventListener('submit', function (event) {
        const form = event.target.closest('form.bank-ajax-form');
        if (!form) {
            return;
        }
        event.preventDefault();
        submitBankForm(form);
    });

    /**
     * Grants matrix: clicking a flag cell PATCHes the grant with the toggled flag
     * plus the row's other two flags and its optimistic-lock version, then reloads
     * so every row shows the fresh version (CLAUDE.md DOM-version-sync rule).
     */
    document.addEventListener('click', async function (event) {
        const flagButton = event.target.closest('button.matrix-flag[data-flag]');
        if (!flagButton) {
            return;
        }
        const row = flagButton.closest('tr[data-user-id][data-account-id]');
        if (!row) {
            return;
        }
        const flags = {
            canDeposit: row.getAttribute('data-can-deposit') === 'true',
            canWithdraw: row.getAttribute('data-can-withdraw') === 'true',
            canTransfer: row.getAttribute('data-can-transfer') === 'true',
        };
        const flag = flagButton.getAttribute('data-flag');
        flags[flag] = !flags[flag];
        flags.version = Number(row.getAttribute('data-version'));
        flagButton.disabled = true;
        const endpoint =
            '/api/proxy/bank/grants/' +
            encodeURIComponent(row.getAttribute('data-user-id')) +
            '/' +
            encodeURIComponent(row.getAttribute('data-account-id'));
        let response;
        try {
            response = await fetch(endpoint, {
                method: 'PATCH',
                headers: csrfHeaders(),
                body: JSON.stringify(flags),
            });
        } catch {
            response = null;
        }
        if (response && response.ok) {
            window.location.reload();
            return;
        }
        flagButton.disabled = false;
        let message = genericError();
        if (response) {
            try {
                const payload = await response.json();
                if (payload && payload.message) {
                    message = payload.message;
                }
            } catch {
                // keep the generic message
            }
        }
        if (typeof window.showFrontendErrorToast === 'function') {
            window.showFrontendErrorToast(message);
        }
    });

    /** Grants filter selects: navigate to the chosen grouping/entity on change. */
    document.addEventListener('change', function (event) {
        const select = event.target.closest('select[data-role="bank-grants-filter"]');
        if (!select) {
            return;
        }
        const params = new URLSearchParams();
        params.set('view', select.getAttribute('data-view'));
        if (select.value) {
            params.set(select.getAttribute('data-param'), select.value);
        }
        window.location.assign('/bank/grants?' + params.toString());
    });

    /**
     * Account-create modal: the org-unit select is only relevant (and required)
     * for ORG_UNIT accounts, the area name only for AREA accounts; hidden rows
     * are cleared so stale values never reach the backend.
     *
     * @param {HTMLSelectElement} select the account-type select
     */
    function syncAccountTypeRows(select) {
        const form = select.closest('form');
        if (!form) {
            return;
        }
        const type = select.value;
        const orgRow = form.querySelector('.bank-row-orgunit');
        if (orgRow) {
            orgRow.style.display = type === 'ORG_UNIT' ? '' : 'none';
            const control = orgRow.querySelector('select, input');
            if (control) {
                control.required = type === 'ORG_UNIT';
                if (type !== 'ORG_UNIT') {
                    control.value = '';
                }
            }
        }
        const areaRow = form.querySelector('.bank-row-area');
        if (areaRow) {
            areaRow.style.display = type === 'AREA' ? '' : 'none';
            const control = areaRow.querySelector('input');
            if (control) {
                control.required = type === 'AREA';
                if (type !== 'AREA') {
                    control.value = '';
                }
            }
        }
    }

    document.addEventListener('change', function (event) {
        const select = event.target.closest('select[data-role="bank-account-type"]');
        if (select) {
            syncAccountTypeRows(select);
        }
    });

    document.querySelectorAll('select[data-role="bank-account-type"]').forEach(syncAccountTypeRows);

    /* Enhance the user pickers into searchable comboboxes when the helper is loaded. */
    if (typeof window.krtSearchableSelect === 'function') {
        document
            .querySelectorAll('select[data-role="bank-holder-user"]')
            .forEach(function (select) {
                window.krtSearchableSelect(select);
            });
    }

    /**
     * Fetches one bank PDF with CSRF + the browser's IANA zone and saves it as a file.
     * Reports failures through the given callback (toast or inline error).
     *
     * @param {string} url the proxy URL
     * @param {string} filename the download filename
     * @param {function(string):void} onError receives the localized error message
     */
    async function downloadBankPdf(url, filename, onError) {
        const headers = csrfHeaders();
        delete headers['Content-Type'];
        const userTimeZone =
            window.Intl && Intl.DateTimeFormat
                ? Intl.DateTimeFormat().resolvedOptions().timeZone
                : '';
        if (userTimeZone) {
            headers['X-User-Time-Zone'] = userTimeZone;
        }
        let response;
        try {
            response = await fetch(url, { method: 'GET', headers: headers });
        } catch {
            response = null;
        }
        if (!response || !response.ok) {
            onError(genericError());
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
    }

    /* Direct download buttons (dashboard three-month report). */
    document.addEventListener('click', function (event) {
        const button = event.target.closest('.bank-download-btn[data-download-url]');
        if (!button) {
            return;
        }
        button.disabled = true;
        downloadBankPdf(
            button.getAttribute('data-download-url'),
            button.getAttribute('data-filename') || 'report.pdf',
            function (message) {
                if (typeof window.showFrontendErrorToast === 'function') {
                    window.showFrontendErrorToast(message);
                }
            },
        ).finally(function () {
            button.disabled = false;
        });
    });

    /**
     * Type-to-confirm hurdle (admin wipe-reset): the named submit button stays disabled until
     * the input value exactly matches the token (case-sensitive), guarding the danger action.
     */
    document.addEventListener('input', function (event) {
        const input = event.target.closest('input[data-confirm-token][data-confirm-submit]');
        if (!input) {
            return;
        }
        const submit = document.getElementById(input.getAttribute('data-confirm-submit'));
        if (submit) {
            submit.disabled = input.value !== input.getAttribute('data-confirm-token');
        }
    });

    /**
     * Statement export form: the datetime-splitter keeps the hidden from/to fields as UTC
     * ISO instants; both are required, then the PDF is fetched as a blob download.
     */
    document.addEventListener('submit', function (event) {
        const form = event.target.closest('form.bank-download-form');
        if (!form) {
            return;
        }
        event.preventDefault();
        clearErrors(form);
        const from = form.querySelector('input[name="from"]');
        const to = form.querySelector('input[name="to"]');
        const required = form.getAttribute('data-period-required') || genericError();
        let valid = true;
        if (!from || !from.value) {
            showError(form, 'from', required);
            valid = false;
        }
        if (!to || !to.value) {
            showError(form, 'to', required);
            valid = false;
        }
        if (!valid) {
            return;
        }
        const url =
            form.getAttribute('data-endpoint') +
            '?from=' +
            encodeURIComponent(from.value) +
            '&to=' +
            encodeURIComponent(to.value);
        downloadBankPdf(
            url,
            form.getAttribute('data-filename') || 'kontoauszug.pdf',
            function (message) {
                showError(form, '_global', message);
            },
        );
    });
})();
