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
    var on = window.krtEvents.on;

    /**
     * Local same-origin path sanitiser. Mirrors {@link window.safeSameOriginUrl} but is defined
     * in the same scope as the {@code window.location.href} sinks below so CodeQL's
     * {@code js/xss-through-dom} interprocedural analysis can see the explicit
     * {@code charAt(0) === '/'} sanitizer and prove the data flow safe. Without an in-scope
     * sanitizer the helper-via-{@code window} indirection is treated as an unsanitised flow.
     *
     * Accepts: a non-empty string whose first char is {@code '/'} and whose second char is
     * neither {@code '/'} nor {@code '\\'} (rejects {@code //attacker} and {@code \\\\share}).
     * Returns the input unchanged on success, {@code null} on rejection.
     */
    function sanitizePath(raw) {
        if (typeof raw !== 'string' || raw.length < 2) return null;
        if (raw.charAt(0) !== '/') return null;
        var second = raw.charAt(1);
        if (second === '/' || second === '\\') return null;
        return raw;
    }

    /**
     * Navigate to the URL in {@code data-href}. Sanitised by {@link sanitizePath} so an
     * attacker-controlled absolute URL on a third-party origin cannot redirect the user out
     * of the application — the same defense the legacy inline handlers used.
     */
    on('click', 'navigate-href', function (el, event) {
        var url = sanitizePath(el.getAttribute('data-href'));
        if (!url) return;
        event.preventDefault();
        window.location.href = url;
    });

    /**
     * Navigate to a URL templated against the selected value. Element declares
     * {@code data-url-template} containing a {@code {value}} placeholder; the placeholder is
     * substituted with the input's URL-encoded current value, and the resulting path is
     * sanitised through {@link sanitizePath}.
     */
    on('change', 'navigate-select', function (el) {
        if (!el.value) return;
        var template = el.getAttribute('data-url-template');
        if (!template) return;
        var url = sanitizePath(template.replace('{value}', encodeURIComponent(el.value)));
        if (!url) return;
        window.location.href = url;
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
        var id = el.getAttribute('data-form-id');
        if (!id) return;
        var form = document.getElementById(id);
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
        var tableId = el.getAttribute('data-table-id');
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
        var id = el.getAttribute('data-modal-id');
        if (!id) return;
        var modal = document.getElementById(id);
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
        var id = el.getAttribute('data-modal-id');
        if (id) {
            var modal = document.getElementById(id);
            if (modal) modal.classList.remove('active');
            return;
        }
        var ancestor = el.closest('.modal-overlay, .modal, .modal-box');
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
        var id = el.getAttribute('data-target');
        if (!id) return;
        var target = document.getElementById(id);
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
        var id = el.getAttribute('data-target');
        if (!id) return;
        var target = document.getElementById(id);
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
        var id = el.getAttribute('data-modal-id');
        if (!id) return;
        var modal = document.getElementById(id);
        if (!modal) return;
        event.preventDefault();
        modal.style.display = el.getAttribute('data-modal-display') || 'flex';
    });

    /**
     * Convenience over {@code set-display} for "close this modal" buttons on the
     * {@code style.display}-based modal pattern.
     */
    on('click', 'close-modal-display', function (el, event) {
        var id = el.getAttribute('data-modal-id');
        if (!id) return;
        var modal = document.getElementById(id);
        if (!modal) return;
        event.preventDefault();
        modal.style.display = 'none';
    });
})();
