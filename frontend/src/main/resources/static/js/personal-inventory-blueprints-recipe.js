/*
 * Personal Inventory — Blueprints sub-page: expandable recipe detail (#327).
 *
 * Each owned-blueprint row carries a chevron toggle (data-trigger="bp-toggle-recipe").
 * Expanding it reveals a hidden detail <tr> and, on first open, lazily fetches the
 * product's SC Wiki recipe from /personal-inventory/blueprints/{id}/recipe and renders
 * a compact two-column view — "Zutaten" (ingredients) next to "Stat-Beitrag nach
 * Zutat-Qualität" (per-quality stat sliders) — mirroring the admin /admin/blueprints
 * page. Each stat slider recomputes its multiplier live, client-side, exactly like the
 * admin page (linear band, or segment-by-segment for stepped curves).
 *
 * Wiring: uses the global `krtEvents.on` delegation helper (CSP-nonce-safe, no inline
 * onclick). Strings come from window.krtBlueprintsRecipeI18n; the per-row URL (with an
 * `ID_PLACEHOLDER` placeholder) from window.krtBlueprintsEndpoints. The DOM is built with
 * createElement + textContent only (no innerHTML), so no value is ever an HTML sink.
 */
(function () {
    'use strict';

    function i18n() { return window.krtBlueprintsRecipeI18n || {}; }

    function endpoints() { return window.krtBlueprintsEndpoints || {}; }

    /* ------------------------------------------------------------ DOM helpers */

    function el(tag, cls, text) {
        const node = document.createElement(tag);
        if (cls) { node.className = cls; }
        if (text != null) { node.textContent = text; }
        return node;
    }

    function clear(node) {
        while (node.firstChild) { node.removeChild(node.firstChild); }
    }

    function resolveUrl(template, id) {
        const raw = (template || '').replace('ID_PLACEHOLDER', encodeURIComponent(id));
        return window.safeSameOriginUrl ? window.safeSameOriginUrl(raw, raw) : raw;
    }

    /* --------------------------------------------------------- expand / fetch */

    function toggleRecipe(btn) {
        const id = btn.getAttribute('data-id');
        if (!id) { return; }
        const row = document.getElementById('bp-recipe-' + id);
        if (!row) { return; }
        const expanded = btn.getAttribute('aria-expanded') === 'true';
        if (expanded) {
            row.hidden = true;
            btn.setAttribute('aria-expanded', 'false');
            return;
        }
        btn.setAttribute('aria-expanded', 'true');
        row.hidden = false;
        const panel = row.querySelector('.krt-bp-recipe-panel');
        if (panel && panel.getAttribute('data-loaded') !== 'true') {
            loadRecipe(id, panel);
        }
    }

    function loadRecipe(id, panel) {
        panel.setAttribute('data-loaded', 'true');
        clear(panel);
        panel.appendChild(el('div', 'krt-bp-recipe-loading', i18n().loading || 'Loading...'));
        fetch(resolveUrl(endpoints().recipe, id), {
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        })
            .then(function (resp) { return resp.ok ? resp.json() : null; })
            .then(function (recipe) {
                if (!recipe) { showError(panel); return; }
                renderRecipe(panel, recipe);
            })
            .catch(function () { showError(panel); });
    }

    function showError(panel) {
        // Allow a retry on the next expand by clearing the loaded flag.
        panel.setAttribute('data-loaded', 'false');
        clear(panel);
        panel.appendChild(el('div', 'krt-bp-recipe-error', i18n().error || 'Error.'));
    }

    /* -------------------------------------------------------------- rendering */

    // renderRecipe is only ever called from loadRecipe after `recipe` is confirmed truthy, so the
    // object itself needs no further guarding here — only its optional fields default to empty.
    function renderRecipe(panel, recipe) {
        clear(panel);
        const groups = recipe.requirementGroups || [];
        const flat = recipe.ingredients || [];
        if (groups.length === 0 && flat.length === 0) {
            panel.appendChild(el('div', 'krt-bp-recipe-empty', i18n().empty || 'No recipe data.'));
            return;
        }

        const wrap = el('div', 'krt-bp-recipe');
        if (recipe.variantCount > 1) {
            const hint = recipe.variantCount + ' ' + (i18n().variants || 'variants')
                    + ' · ' + (i18n().exampleRecipe || 'example recipe');
            wrap.appendChild(el('p', 'krt-bp-recipe-variants', hint));
        }

        const table = el('table', 'krt-bp-recipe-table');
        const thead = document.createElement('thead');
        const htr = document.createElement('tr');
        htr.appendChild(el('th', null, i18n().ingredients || 'Ingredients'));
        htr.appendChild(el('th', null, i18n().statContribution || 'Stat contribution'));
        thead.appendChild(htr);
        table.appendChild(thead);

        const tbody = document.createElement('tbody');
        if (groups.length > 0) {
            groups.forEach(function (g) { tbody.appendChild(renderGroupRow(g)); });
        } else {
            tbody.appendChild(renderFlatRow(flat));
        }
        table.appendChild(tbody);
        wrap.appendChild(table);
        panel.appendChild(wrap);
    }

    // Legacy fallback row for a blueprint synced without requirement groups: the flat
    // ingredient list with no stat band (a dash), mirroring the admin page's fallback.
    function renderFlatRow(ingredients) {
        const tr = document.createElement('tr');
        const ingCell = ingredientCell();
        ingredients.forEach(function (ing) { ingCell.appendChild(renderIngredient(ing)); });
        tr.appendChild(ingCell);
        const statCell = statContributionCell();
        statCell.appendChild(el('span', 'krt-bp-recipe-dash', '–'));
        tr.appendChild(statCell);
        return tr;
    }

    function renderGroupRow(group) {
        const tr = document.createElement('tr');

        const ingCell = ingredientCell();
        const slot = group.name || group.groupKey;
        if (slot) { ingCell.appendChild(el('span', 'krt-bp-recipe-slot', slot)); }
        const ings = group.ingredients || [];
        if (ings.length > 0) {
            ings.forEach(function (ing) { ingCell.appendChild(renderIngredient(ing)); });
        } else {
            ingCell.appendChild(el('span', 'krt-bp-recipe-dash', '–'));
        }
        tr.appendChild(ingCell);

        const statCell = statContributionCell();
        const mods = group.modifiers || [];
        if (mods.length > 0) {
            mods.forEach(function (m) { statCell.appendChild(renderModifier(m)); });
        } else {
            statCell.appendChild(el('span', 'krt-bp-recipe-dash', '–'));
        }
        tr.appendChild(statCell);
        return tr;
    }

    // The data-label is surfaced as a pseudo-element heading on narrow screens, where
    // the table collapses to stacked blocks and the column <thead> is hidden.
    function ingredientCell() {
        const td = el('td', 'krt-bp-recipe-ing-cell');
        td.setAttribute('data-label', i18n().ingredients || 'Ingredients');
        return td;
    }

    function statContributionCell() {
        const td = el('td', 'krt-bp-recipe-stat-cell');
        td.setAttribute('data-label', i18n().statContribution || 'Stat contribution');
        return td;
    }

    function renderIngredient(ing) {
        const span = el('span', 'krt-bp-recipe-ing');
        span.appendChild(el('span', null, ing.name || '?'));
        if (ing.quantityScu != null) {
            span.appendChild(el('span', 'krt-bp-recipe-ing-meta',
                    ' · ' + Number(ing.quantityScu).toFixed(2) + ' SCU'));
        }
        if (ing.quantityUnits != null) {
            span.appendChild(el('span', 'krt-bp-recipe-ing-meta', ' · ' + ing.quantityUnits + 'x'));
        }
        if (ing.minQuality != null) {
            span.appendChild(el('span', 'krt-bp-recipe-ing-meta',
                    ' · ' + (i18n().minQuality || 'min. quality') + ' ' + ing.minQuality));
        }
        return span;
    }

    function renderModifier(m) {
        const stat = el('div', 'krt-bp-recipe-stat');
        stat.appendChild(el('span', 'krt-bp-recipe-stat-label', m.label || m.propertyKey || ''));
        const bw = betterWhenText(m.betterWhen);
        if (bw) { stat.appendChild(el('span', 'krt-bp-recipe-stat-bw', '(' + bw + ')')); }

        const hasBand = m.effectiveQualityMin != null && m.effectiveQualityMax != null
                && m.effectiveQualityMax > m.effectiveQualityMin;
        stat.appendChild(hasBand ? buildSlider(m) : buildFallback(m));
        return stat;
    }

    function betterWhenText(bw) {
        if (bw === 'higher') { return i18n().betterHigher || 'higher is better'; }
        if (bw === 'lower') { return i18n().betterLower || 'lower is better'; }
        if (bw === 'neutral') { return i18n().betterNeutral || 'neutral'; }
        return null;
    }

    // Live slider spanning the ingredient's effective quality band; the output recomputes
    // the stat multiplier on every input event, identically to the admin blueprint page.
    function buildSlider(m) {
        const qmin = m.effectiveQualityMin;
        const qmax = m.effectiveQualityMax;
        const slider = el('div', 'krt-bp-recipe-slider');

        const row = el('div', 'krt-bp-recipe-slider-row');
        row.appendChild(el('span', 'krt-bp-recipe-end', String(Math.round(qmin))));
        const range = document.createElement('input');
        range.type = 'range';
        range.className = 'krt-bp-recipe-range';
        range.step = '1';
        range.min = String(qmin);
        range.max = String(qmax);
        range.value = String(qmax);
        row.appendChild(range);
        row.appendChild(el('span', 'krt-bp-recipe-end krt-bp-recipe-end-max', String(Math.round(qmax))));
        slider.appendChild(row);

        const out = el('div', 'krt-bp-recipe-slider-out');
        const qOut = el('output', 'krt-bp-recipe-q', '–');
        out.appendChild(qOut);
        out.appendChild(el('span', 'krt-bp-recipe-arrow', '→'));
        const vOut = el('output', 'krt-bp-recipe-val', '×?');
        out.appendChild(vOut);
        slider.appendChild(out);

        function compute() {
            const q = parseFloat(range.value);
            const value = computeModifierValue(m, q);
            qOut.textContent = String(Math.round(q));
            vOut.textContent = '×' + (value == null ? '?' : value.toFixed(2));
        }
        range.addEventListener('input', compute);
        compute();
        return slider;
    }

    // No usable quality band to slide over: show the max-quality multiplier, else a dash.
    function buildFallback(m) {
        if (m.modifierAtMaxQuality != null) {
            return el('span', 'krt-bp-recipe-depval', '×' + Number(m.modifierAtMaxQuality).toFixed(2));
        }
        return el('span', 'krt-bp-recipe-dash', '–');
    }

    /* ----------------------------------------------------------- computation */

    function clamp01(t) { return t < 0 ? 0 : (t > 1 ? 1 : t); }

    function lerp(a, b, t) { return a + (b - a) * t; }

    // Mirror of the admin blueprint slider math. A segmented modifier follows its ordered
    // segments — interpolated within a 'linear' segment, held constant for a stepped form
    // (e.g. 'linear_integer_additive'); a non-segmented modifier interpolates linearly
    // between its endpoint multipliers across the effective band.
    function computeModifierValue(m, q) {
        const segs = m.segments || [];
        if (segs.length > 0) {
            const stepped = (m.valueRangeType || 'linear').toLowerCase() !== 'linear';
            for (let i = 0; i < segs.length; i++) {
                const a = segs[i].qualityMin;
                const b = segs[i].qualityMax;
                const vs = segs[i].modifierAtStart;
                const ve = segs[i].modifierAtEnd;
                if (a == null || b == null) { continue; }
                if (q <= b || i === segs.length - 1) {
                    if (stepped) { return (vs == null) ? ve : vs; }
                    const t = (b === a) ? 0 : clamp01((q - a) / (b - a));
                    return (vs == null || ve == null) ? vs : lerp(vs, ve, t);
                }
            }
            return null;
        }
        const qmin = m.effectiveQualityMin;
        const qmax = m.effectiveQualityMax;
        const vmin = m.modifierAtMinQuality;
        const vmax = m.modifierAtMaxQuality;
        if (qmin != null && qmax != null && vmin != null && vmax != null) {
            const tt = (qmax === qmin) ? 0 : clamp01((q - qmin) / (qmax - qmin));
            return lerp(vmin, vmax, tt);
        }
        return null;
    }

    /* --------------------------------------------------------------- wiring */

    if (window.krtEvents && typeof window.krtEvents.on === 'function') {
        window.krtEvents.on('click', 'bp-toggle-recipe', toggleRecipe);
    }
})();
