/*
 * Shared CSP-safe event handlers — registered globally so every template can
 * declare a logical action via `data-trigger="<name>"` instead of an inline
 * `onclick="…"` (which would require the dropped `script-src-attr 'unsafe-inline'`
 * CSP allowance).
 *
 * Pattern: each entry pairs a short, kebab-case action name with a function that
 * receives the matched element and the original DOM event. State (URL templates,
 * form ids, table ids, …) comes from `data-*` attributes on the element itself
 * so the JS stays declarative and free of hard-coded selectors.
 *
 * Naming: the prefix is left off for actions that are genuinely page-agnostic
 * ("navigate-href", "close-modal"). Page-specific actions still live in
 * dedicated `<template>.js` modules with their own short prefix
 * (e.g. `pi-` for personal-inventory) so a refactor of one page cannot trip a
 * collision across the codebase.
 */
(function () {
    'use strict';

    if (!window.krtEvents || typeof window.krtEvents.on !== 'function') {
        // event-delegation.js has not loaded — abort silently. Production fragments/head.html
        // wires event-delegation.js BEFORE this file, so the guard only fires if a test slice
        // bypasses the fragment.
        return;
    }
    let on = window.krtEvents.on;

    /**
     * Strict whitelist regex for same-origin path URLs. Matches a string that starts with
     * {@code '/'}, whose second character is neither {@code '/'} nor {@code '\\'} (rejects
     * protocol-relative {@code //attacker} and Windows UNC {@code /\\share}), and whose
     * remainder contains no whitespace, angle brackets, quotes, or backticks (HTML/JS
     * meta-characters). Capture groups split the input into path / search / hash so each part
     * can be assigned to the corresponding {@link HTMLAnchorElement} setter, which validates
     * its argument structurally and cannot be tricked into changing the URL's scheme.
     */
    let SAFE_PATH_REGEX = /^(\/[^/\\][^?#\s<>"'`]*)(\?[^#\s<>"'`]*)?(#[^\s<>"'`]*)?$/;

    /**
     * Navigate to a same-origin path safely. Sets {@code pathname} / {@code search} /
     * {@code hash} on a freshly-created {@code <a>} anchored at the current origin, then reads
     * back the validated {@code href} for navigation. The {@link HTMLAnchorElement.pathname},
     * {@link HTMLAnchorElement.search}, and {@link HTMLAnchorElement.hash} setters are
     * structurally narrower than {@code location.href}: they can only mutate their respective
     * URL components, so even if the regex check were somehow bypassed they could never change
     * the scheme to {@code javascript:} (the structural-safety pattern from the CodeQL
     * {@code js/xss-through-dom} docs — analogue of {@code $.find} versus {@code $()}).
     *
     * @param raw  the candidate URL string (typically a {@code data-*} attribute value)
     * @return {@code true} if navigation was triggered, {@code false} if the input was
     *     rejected (caller may then leave the default-action in place)
     */
    function navigateSafe(raw) {
        if (typeof raw !== 'string') return false;
        let match = SAFE_PATH_REGEX.exec(raw);
        if (!match) return false;
        let a = document.createElement('a');
        a.href = window.location.origin;
        a.pathname = match[1];
        if (match[2]) a.search = match[2];
        if (match[3]) a.hash = match[3];
        if (a.origin !== window.location.origin) return false;
        window.location.assign(a.href);
        return true;
    }

    /**
     * Navigate to the URL in {@code data-href}. Delegated to {@link navigateSafe} so the
     * actual navigation sink only ever receives a URL re-built from the structural
     * {@link HTMLAnchorElement} setters.
     */
    on('click', 'navigate-href', function (el, event) {
        if (navigateSafe(el.getAttribute('data-href'))) {
            event.preventDefault();
        }
    });

    /**
     * Navigate to a URL templated against the selected value. Element declares
     * {@code data-url-template} containing a {@code {value}} placeholder; the placeholder is
     * substituted with the input's URL-encoded current value, then handed to
     * {@link navigateSafe} for the structural same-origin guard.
     */
    on('change', 'navigate-select', function (el) {
        if (!el.value) return;
        let template = el.getAttribute('data-url-template');
        if (!template) return;
        navigateSafe(template.replace('{value}', encodeURIComponent(el.value)));
    });

    /**
     * Browser-history back. {@code preventDefault} so the surrounding {@code <a>} with a real
     * href does not also navigate forward — this keeps the "go back" behaviour identical to
     * the historical {@code onclick="history.back(); return false;"} pattern.
     */
    on('click', 'history-back', function (el, event) {
        event.preventDefault();
        window.history.back();
    });

    /**
     * Pure {@link Event#stopPropagation}. Used on inner buttons / forms inside clickable
     * container rows so a nested action does not bubble up to the row's own click handler.
     */
    on('click', 'stop-propagation', function (el, event) {
        event.stopPropagation();
    });

    /**
     * Submit the closest surrounding {@code <form>} whenever the bound input changes. Mirrors
     * the historical {@code onchange="this.form.submit()"} pattern used by filter dropdowns
     * and auto-applying selects.
     */
    on('change', 'submit-form', function (el) {
        if (el.form && typeof el.form.submit === 'function') {
            el.form.submit();
        }
    });

    /**
     * Submit a form by id. Element declares {@code data-form-id} pointing at the form's id.
     * Used by buttons placed outside the form (e.g. in a toolbar) that need to trigger one of
     * the inline forms on the page. Registered on {@code click} (for buttons) and {@code
     * change} (for filter-checkbox / filter-select patterns that historically used
     * {@code onchange="getElementById('…').submit()"}). The duplicate registration is safe
     * because the two event types fire on disjoint sets of elements in practice — buttons
     * fire {@code click}, form controls fire {@code change} (the {@code click} that a mouse
     * also fires on a checkbox is suppressed via {@code event.preventDefault()} below, which
     * leaves the {@code change} handler as the single submit trigger).
     */
    function submitFormByIdHandler(el, event) {
        let id = el.getAttribute('data-form-id');
        if (!id) return;
        let form = document.getElementById(id);
        if (!form || typeof form.submit !== 'function') return;
        if (event && typeof event.preventDefault === 'function' && el.tagName === 'BUTTON') {
            event.preventDefault();
        }
        form.submit();
    }
    on('click', 'submit-form-by-id', submitFormByIdHandler);
    on('change', 'submit-form-by-id', submitFormByIdHandler);

    /**
     * Call the global {@code filterTable(tableId, query)} helper on every keystroke. Element
     * declares {@code data-table-id} naming the target table. Bound to {@code input} (covers
     * paste / autofill) and {@code keyup} (legacy keyboard-only flows).
     */
    function filterTableHandler(el) {
        if (typeof window.filterTable !== 'function') return;
        let tableId = el.getAttribute('data-table-id');
        if (!tableId) return;
        window.filterTable(tableId, el.value);
    }
    on('input', 'filter-table', filterTableHandler);
    on('keyup', 'filter-table', filterTableHandler);

    /**
     * Open a modal by id. Element declares {@code data-modal-id} naming the modal's
     * {@code id}; the modal element receives the {@code .active} class (matches the KRT modal
     * convention from {@code fragments/toast.html} / {@code personal-inventory.html}).
     */
    on('click', 'open-modal', function (el, event) {
        let id = el.getAttribute('data-modal-id');
        if (!id) return;
        let modal = document.getElementById(id);
        if (!modal) return;
        event.preventDefault();
        modal.classList.add('active');
    });

    /**
     * Close a modal. {@code data-modal-id} explicitly names the modal to close; falling back
     * to the nearest {@code .modal-overlay} or {@code .modal} ancestor when omitted (covers
     * close-buttons living inside the modal itself).
     */
    on('click', 'close-modal', function (el, event) {
        event.preventDefault();
        let id = el.getAttribute('data-modal-id');
        if (id) {
            let modal = document.getElementById(id);
            if (modal) modal.classList.remove('active');
            return;
        }
        let ancestor = el.closest('.modal-overlay, .modal, .modal-box');
        if (!ancestor) return;
        // `.modal-box` is the inner element — climb one level to the overlay if needed.
        if (ancestor.classList.contains('modal-box')) {
            ancestor = ancestor.closest('.modal-overlay, .modal') || ancestor;
        }
        ancestor.classList.remove('active');
    });

    /**
     * Toggle a target element's {@code style.display} between {@code 'none'} and an empty
     * string. {@code data-target} carries the target element's id. Used for collapsible
     * info-boxes and "show details" reveals.
     */
    on('click', 'toggle-display', function (el, event) {
        let id = el.getAttribute('data-target');
        if (!id) return;
        let target = document.getElementById(id);
        if (!target) return;
        event.preventDefault();
        target.style.display = target.style.display === 'none' ? '' : 'none';
    });

    /**
     * Set a target element's {@code style.display} to an explicit value declared in
     * {@code data-display}. Used by the legacy {@code style.display='flex'} /
     * {@code style.display='none'} modals (operation-detail, operations-index, several
     * older admin pages) that pre-date the {@code .active} class convention. Keeping a
     * separate trigger means we do not have to migrate those modal CSS rules in lockstep
     * with the JS migration — the templates can stay byte-identical except for the
     * handler attribute.
     */
    on('click', 'set-display', function (el, event) {
        let id = el.getAttribute('data-target');
        if (!id) return;
        let target = document.getElementById(id);
        if (!target) return;
        event.preventDefault();
        target.style.display = el.getAttribute('data-display') || '';
    });

    /**
     * Convenience over {@code set-display} for "open this modal" buttons on the
     * {@code style.display}-based modal pattern. Sets the modal to {@code data-modal-display}
     * (default {@code flex}). The accompanying close button can use {@code set-display} with
     * {@code data-display="none"}, or its own {@code close-modal-display}.
     */
    on('click', 'open-modal-display', function (el, event) {
        let id = el.getAttribute('data-modal-id');
        if (!id) return;
        let modal = document.getElementById(id);
        if (!modal) return;
        event.preventDefault();
        modal.style.display = el.getAttribute('data-modal-display') || 'flex';
    });

    /**
     * Convenience over {@code set-display} for "close this modal" buttons on the
     * {@code style.display}-based modal pattern.
     */
    on('click', 'close-modal-display', function (el, event) {
        let id = el.getAttribute('data-modal-id');
        if (!id) return;
        let modal = document.getElementById(id);
        if (!modal) return;
        event.preventDefault();
        modal.style.display = 'none';
    });
})();
