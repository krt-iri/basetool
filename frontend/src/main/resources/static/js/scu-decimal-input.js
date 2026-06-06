/*
 * SCU decimal-input normalisation.
 *
 * Star-Citizen cargo amounts are entered in SCU and may be fractional
 * (cSCU = 0.01 SCU, microSCU = 0.001 SCU). A native <input type="number">
 * only accepts the decimal separator of the *browser* locale: German Chrome
 * takes "0,01" but rejects "0.01", English Chrome does the reverse - so a value
 * typed with the "wrong" separator is silently dropped. To let operators type
 * EITHER "." or "," regardless of browser locale, every SCU amount field is a
 * plain <input type="text" inputmode="decimal" data-scu-decimal ...> and this
 * module keeps those fields numeric and canonical:
 *
 *   - on input it strips any character that is not a digit or a decimal
 *     separator (and strips separators entirely while the field is in integer
 *     "Stueck"/PIECE mode, detected from its live step attribute),
 *   - right before the surrounding form is submitted it rewrites every such
 *     field to a canonical dot value, so the backend - which binds Double via
 *     the locale-independent Double.valueOf - always receives "0.01", and
 *   - it exposes window.krtScuInput.{normalize,parse} for the few inline page
 *     scripts that read these fields live (the book-out target<->amount sync,
 *     the AJAX material claim and the handover amount check) so they parse a
 *     comma-typed value correctly too.
 *
 * "Normalise internally" therefore means: the operator keeps seeing the
 * separator they typed while editing; only the value that leaves the field (on
 * submit or via parse()) is canonicalised to a dot.
 *
 * Loaded globally from fragments/head.html, which also defines a minimal inline
 * stub so normalize()/parse() stay callable even if this file is slow to load.
 */
(function (root) {
    "use strict";

    const SELECTOR = "input[data-scu-decimal]";

    /**
     * Canonicalises a user-entered amount to a dot-decimal string: trims, turns
     * every comma into a dot and keeps only the first dot (so "1,2.3" -> "1.23").
     * Returns "" when the input holds no digit (null, blank or a lone separator),
     * which lets the backend's @NotNull report a clean "required" error rather
     * than a number-format failure.
     *
     * @param {*} raw the raw field value (or anything String()-able)
     * @returns {string} the canonical dot value, or "" when there is no number
     */
    function normalize(raw) {
        if (raw === null || raw === undefined) {
            return "";
        }
        let s = String(raw).trim().replace(/,/g, ".");
        if (!/[0-9]/.test(s)) {
            return "";
        }
        const dot = s.indexOf(".");
        if (dot !== -1) {
            s = s.slice(0, dot + 1) + s.slice(dot + 1).replace(/\./g, "");
        }
        return s;
    }

    /**
     * Parses a user-entered amount to a finite Number via {@link normalize},
     * accepting both "." and "," as the decimal separator. Returns NaN when the
     * field holds no finite number; callers already guard with `> 0` style checks.
     *
     * @param {*} raw the raw field value
     * @returns {number} the parsed amount, or NaN
     */
    function parse(raw) {
        const n = parseFloat(normalize(raw));
        return Number.isFinite(n) ? n : NaN;
    }

    /**
     * Reports whether a field is currently in integer ("Stueck"/PIECE) mode and
     * must reject decimal separators. Inferred from the live `step` attribute,
     * which the quantity-type-aware page scripts already toggle between "1"
     * (PIECE) and "0.001" (SCU); a missing or "any" step counts as decimal.
     *
     * @param {HTMLInputElement} el the amount field
     * @returns {boolean} true when only whole digits are allowed
     */
    function isIntegerMode(el) {
        const step = el.getAttribute("step");
        if (!step || step === "any") {
            return false;
        }
        const n = parseFloat(step.replace(",", "."));
        return Number.isInteger(n);
    }

    /**
     * Strips every character not allowed in the field's current mode, preserving
     * the caret. Runs on every input event (covering typing, paste and IME) so a
     * stray letter, sign or extra digit-group never survives in the field.
     *
     * @param {Event} e the input event
     */
    function sanitize(e) {
        const el = e.target;
        if (!el || typeof el.matches !== "function" || !el.matches(SELECTOR)) {
            return;
        }
        const disallowed = isIntegerMode(el) ? /[^0-9]/g : /[^0-9.,]/g;
        const before = el.value;
        const cleaned = before.replace(disallowed, "");
        if (cleaned === before) {
            return;
        }
        const caret = el.selectionStart;
        el.value = cleaned;
        if (caret !== null && caret !== undefined) {
            const pos = Math.max(0, caret - (before.length - cleaned.length));
            try {
                el.setSelectionRange(pos, pos);
            } catch (_e) {
                // setSelectionRange throws on some input types; the caret is cosmetic.
            }
        }
    }

    /**
     * Rewrites every managed field in the submitting form to its canonical dot
     * value just before serialisation. Constraint validation (e.g. `required`)
     * has already run by the time the submit event fires, so an empty field is
     * still blocked natively; this only canonicalises the non-empty ones.
     *
     * @param {Event} e the submit event
     */
    function canonicaliseForm(e) {
        const form = e.target;
        if (!form || typeof form.querySelectorAll !== "function") {
            return;
        }
        form.querySelectorAll(SELECTOR).forEach((el) => {
            const norm = normalize(el.value);
            if (norm !== el.value) {
                el.value = norm;
            }
        });
    }

    // Capture phase on both listeners so they run before any page-level handler
    // (the book-out sync reads the value, the handover validator reads it on
    // submit) - those consumers then see an already-sanitised / canonical value.
    document.addEventListener("input", sanitize, true);
    document.addEventListener("submit", canonicaliseForm, true);

    root.krtScuInput = { normalize: normalize, parse: parse };
})(window);
