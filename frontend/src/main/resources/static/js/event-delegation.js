/*
 * Lightweight event-delegation helper.
 *
 * Replaces the historical pattern of inline HTML event handlers
 * (`onclick="myFn(this)"`, `onchange="…"`, …) with a CSP-safe alternative:
 * elements declare a logical action via `data-trigger="<actionName>"`, and
 * modules register a delegated handler via `krtEvents.on(eventType, actionName, handler)`.
 *
 * Why a single delegated listener per (eventType, actionName) rather than
 * `el.addEventListener` per node? Two reasons:
 *   1. Works out of the box for DOM elements that are added dynamically *after*
 *      DOMContentLoaded (modals, search results, paginated tables) — no need to
 *      re-bind on every render.
 *   2. Keeps the binding code in one place per module, so the CSP migration
 *      (removing `script-src-attr 'unsafe-inline'`) does not multiply churn
 *      across every template.
 *
 * Naming: actions use a short kebab-case prefix per feature to avoid collisions
 * (e.g. `pi-open-create` for personal-inventory, `members-toggle-logistician`
 * for the members admin page). The handler signature is
 * `(matchedElement, event) => void`. The matched element is the closest
 * `[data-trigger="<actionName>"]` ancestor of the event target, which mirrors
 * the `this`/`event.currentTarget` semantics that the old inline handlers had.
 *
 * The handler is not called if the matched element is disabled (form fields)
 * — that prevents the typical "click-on-disabled-button still fires" footgun
 * for buttons that get disabled while a request is in flight.
 */
(function () {
    'use strict';

    /**
     * Register a delegated handler.
     *
     * @param {string} eventType  - DOM event name (`click`, `change`, `input`, `submit`, …).
     * @param {string} actionName - value of the `data-trigger` attribute to match.
     * @param {(element: HTMLElement, event: Event) => void} handler
     */
    function on(eventType, actionName, handler) {
        if (typeof handler !== 'function') return;
        var selector = '[data-trigger="' + actionName + '"]';
        document.addEventListener(eventType, function (event) {
            var target = event.target;
            if (!target || typeof target.closest !== 'function') return;
            var matched = target.closest(selector);
            if (!matched) return;
            if (matched.disabled) return;
            handler(matched, event);
        });
    }

    window.krtEvents = { on: on };
})();
