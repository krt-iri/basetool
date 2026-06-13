/*
 * Personal Inventory — Blueprints sub-page: master-detail view (V3).
 *
 * The owned collection renders as a master list (left) with a permanent detail
 * pane (right). Selecting a row — by click, ↑/↓ keys on the listbox, or the
 * `?bp={id}` deeplink — fills the pane head from the row's data attributes and
 * lazily fetches the product's SC Wiki recipe from
 * /personal-inventory/blueprints/{id}/recipe (cached per id). Each requirement
 * group renders as a quality block: source slot, ingredient line(s), ONE quality
 * slider spanning the group's effective band, and the group's affected stats as
 * chips whose multiplier recomputes live — using exactly the interpolation logic
 * of the previous expandable-row view (linear band, or segment-by-segment for
 * stepped curves; see computeModifierValue, kept verbatim).
 *
 * The list filter input doubles up: typing filters the rendered rows instantly
 * client-side, Enter submits the existing ?q= server filter. On ≤900px the
 * layout collapses to list → detail navigation (back button in the pane).
 *
 * Wiring: CSP-nonce-safe (no inline handlers); strings from
 * window.krtBlueprintsRecipeI18n; URLs (ID_PLACEHOLDER template) from
 * window.krtBlueprintsEndpoints. DOM is built with createElement + textContent
 * only (no innerHTML), so no value is ever an HTML sink.
 */
(function () {
    'use strict';

    let mdEl = null;
    let rowsEl = null;
    let filterInput = null;
    let detailEmpty = null;
    let detailContent = null;
    let nameEl = null;
    let acquiredEl = null;
    let recipeEl = null;
    let noteSection = null;
    let noteEl = null;
    let editBtn = null;
    let deleteBtn = null;
    let backBtn = null;
    let activeRow = null;

    // id -> recipe JSON, so re-selecting a row never refetches.
    const recipeCache = new Map();

    function i18n() {
        return window.krtBlueprintsRecipeI18n || {};
    }

    function endpoints() {
        return window.krtBlueprintsEndpoints || {};
    }

    /* ------------------------------------------------------------ DOM helpers */

    function el(tag, cls, text) {
        const node = document.createElement(tag);
        if (cls) {
            node.className = cls;
        }
        if (text != null) {
            node.textContent = text;
        }
        return node;
    }

    function clear(node) {
        while (node.firstChild) {
            node.removeChild(node.firstChild);
        }
    }

    function resolveUrl(template, id) {
        const raw = (template || '').replace('ID_PLACEHOLDER', encodeURIComponent(id));
        return window.safeSameOriginUrl ? window.safeSameOriginUrl(raw, raw) : raw;
    }

    // Thymeleaf renders a null Instant attribute as the literal string "null".
    function attr(row, name) {
        const v = row.getAttribute(name);
        return v == null || v === 'null' ? '' : v;
    }

    // Same UTC → local conversion the .utc-time elements get globally (sidebar.html).
    function formatAcquired(iso) {
        if (!iso) {
            return '';
        }
        let s = iso;
        if (!s.endsWith('Z') && s.includes('T')) {
            s += 'Z';
        }
        const date = new Date(s);
        if (isNaN(date)) {
            return iso;
        }
        return new Intl.DateTimeFormat(undefined, {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            timeZone: 'Europe/Berlin',
        }).format(date);
    }

    /* ------------------------------------------------------- selection state */

    function rows() {
        return rowsEl ? Array.from(rowsEl.querySelectorAll('.master-row')) : [];
    }

    function visibleRows() {
        return rows().filter(function (r) {
            return !r.hidden;
        });
    }

    function select(row, opts) {
        if (!row) {
            return;
        }
        const options = opts || {};
        if (activeRow) {
            activeRow.classList.remove('is-active');
            activeRow.setAttribute('aria-selected', 'false');
            activeRow.tabIndex = -1;
        }
        activeRow = row;
        row.classList.add('is-active');
        row.setAttribute('aria-selected', 'true');
        row.tabIndex = 0;
        if (options.focus) {
            row.focus();
        }

        // Deeplink: keep the existing ?q= server-filter param intact.
        try {
            const url = new URL(window.location);
            url.searchParams.set('bp', attr(row, 'data-id'));
            history.replaceState(null, '', url);
        } catch (_e) {
            /* URL API unavailable — deeplink update is best-effort */
        }

        renderDetailHead(row);
        loadRecipe(attr(row, 'data-id'));

        if (options.showDetail && mdEl && window.matchMedia('(max-width: 900px)').matches) {
            mdEl.classList.add('is-detail');
        }
    }

    function renderDetailHead(row) {
        if (!detailContent) {
            return;
        }
        detailEmpty.hidden = true;
        detailContent.hidden = false;

        const id = attr(row, 'data-id');
        const name = attr(row, 'data-name');
        const note = attr(row, 'data-note');
        const version = attr(row, 'data-version');
        const acquired = attr(row, 'data-acquired-at');

        nameEl.textContent = name;
        const formatted = formatAcquired(acquired);
        acquiredEl.textContent = formatted
            ? (i18n().acquiredLabel || 'Erhalten am') + ' ' + formatted
            : '';

        // The pane's edit/remove buttons reuse the existing bp-open-edit/bp-open-delete
        // delegation — only their data attributes change per selection.
        [editBtn, deleteBtn].forEach(function (btn) {
            if (!btn) {
                return;
            }
            btn.setAttribute('data-id', id);
            btn.setAttribute('data-name', name);
        });
        if (editBtn) {
            editBtn.setAttribute('data-note', note);
            editBtn.setAttribute('data-version', version);
            editBtn.setAttribute('data-acquired-at', acquired);
        }

        if (note) {
            noteEl.textContent = note;
            noteSection.hidden = false;
        } else {
            noteSection.hidden = true;
        }
    }

    /* --------------------------------------------------------- recipe loading */

    function loadRecipe(id) {
        if (!recipeEl || !id) {
            return;
        }
        if (recipeCache.has(id)) {
            renderRecipe(recipeCache.get(id));
            return;
        }
        clear(recipeEl);
        recipeEl.appendChild(el('div', 'krt-bp-recipe-loading', i18n().loading || 'Loading...'));
        fetch(resolveUrl(endpoints().recipe, id), {
            credentials: 'same-origin',
            headers: { Accept: 'application/json' },
        })
            .then(function (resp) {
                return resp.ok ? resp.json() : null;
            })
            .then(function (recipe) {
                if (!recipe) {
                    showError();
                    return;
                }
                recipeCache.set(id, recipe);
                // Only render if this id is still the active selection.
                if (activeRow && attr(activeRow, 'data-id') === id) {
                    renderRecipe(recipe);
                }
            })
            .catch(function () {
                showError();
            });
    }

    function showError() {
        clear(recipeEl);
        recipeEl.appendChild(el('div', 'krt-bp-recipe-error', i18n().error || 'Error.'));
    }

    /* -------------------------------------------------------------- rendering */

    function renderRecipe(recipe) {
        clear(recipeEl);
        const groups = recipe.requirementGroups || [];
        const flat = recipe.ingredients || [];
        if (groups.length === 0 && flat.length === 0) {
            recipeEl.appendChild(
                el('div', 'krt-bp-recipe-empty', i18n().empty || 'No recipe data.'),
            );
            return;
        }

        if (recipe.variantCount > 1) {
            const hint =
                recipe.variantCount +
                ' ' +
                (i18n().variants || 'variants') +
                ' · ' +
                (i18n().exampleRecipe || 'example recipe');
            recipeEl.appendChild(el('p', 'krt-bp-recipe-variants', hint));
        }

        if (groups.length > 0) {
            groups.forEach(function (g) {
                recipeEl.appendChild(renderQualityBlock(g));
            });
        } else {
            // Legacy fallback for a blueprint synced without requirement groups: the flat
            // ingredient list with no stat band (a dash).
            const block = el('div', 'quality-block');
            flat.forEach(function (ing) {
                block.appendChild(renderIngredientLine(ing));
            });
            const affects = el('div', 'quality-affects');
            affects.appendChild(el('span', 'krt-bp-recipe-dash', '–'));
            block.appendChild(affects);
            recipeEl.appendChild(block);
        }
    }

    function renderQualityBlock(group) {
        const block = el('div', 'quality-block');
        const slot = group.name || group.groupKey;
        if (slot) {
            block.appendChild(el('span', 'quality-source', slot));
        }

        const ings = group.ingredients || [];
        let firstIngredientName = '';
        if (ings.length > 0) {
            ings.forEach(function (ing, idx) {
                if (idx === 0) {
                    firstIngredientName = ing.name || '';
                }
                block.appendChild(renderIngredientLine(ing));
            });
        } else {
            block.appendChild(el('div', 'quality-name', '–'));
        }

        const mods = group.modifiers || [];
        const banded = mods.filter(function (m) {
            return (
                m.effectiveQualityMin != null &&
                m.effectiveQualityMax != null &&
                m.effectiveQualityMax > m.effectiveQualityMin
            );
        });

        const affects = el('div', 'quality-affects');
        affects.appendChild(el('span', null, '→'));

        if (mods.length === 0) {
            affects.appendChild(el('span', 'krt-bp-recipe-dash', '–'));
            block.appendChild(affects);
            return block;
        }

        // One slider per ingredient block spanning the union of its stats' bands; every
        // affected-stat chip recomputes its own multiplier from the shared quality value.
        const chips = [];
        mods.forEach(function (m) {
            const chip = el('span', 'chip');
            chip.appendChild(el('span', null, (m.label || m.propertyKey || '') + ' '));
            const valueOut = el('output', null, '×?');
            chip.appendChild(valueOut);
            const bw = betterWhenText(m.betterWhen);
            if (bw) {
                chip.title = bw;
            }
            chips.push({ modifier: m, out: valueOut });
            affects.appendChild(chip);
        });

        if (banded.length > 0) {
            const qmin = Math.min.apply(
                null,
                banded.map(function (m) {
                    return m.effectiveQualityMin;
                }),
            );
            const qmax = Math.max.apply(
                null,
                banded.map(function (m) {
                    return m.effectiveQualityMax;
                }),
            );

            const qrow = el('div', 'quality-row');
            const range = document.createElement('input');
            range.type = 'range';
            range.step = '1';
            range.min = String(qmin);
            range.max = String(qmax);
            range.value = String(qmax);
            range.setAttribute(
                'aria-label',
                (i18n().qualityAria || 'Quality') +
                    (firstIngredientName ? ' ' + firstIngredientName : ''),
            );
            qrow.appendChild(range);
            const qval = el('span', 'quality-value');
            const qOut = el('output', null, String(Math.round(qmax)));
            qval.appendChild(qOut);
            const qMaxSmall = document.createElement('small');
            qMaxSmall.textContent = ' / ' + Math.round(qmax);
            qval.appendChild(qMaxSmall);
            qrow.appendChild(qval);
            block.appendChild(qrow);

            // Hint after the chips, mirroring the mock: "(höher ist besser · 1000 → ×1.15)".
            const hintParts = [];
            const firstBw = betterWhenText(mods[0] && mods[0].betterWhen);
            if (firstBw) {
                hintParts.push(firstBw);
            }
            if (banded[0] && banded[0].modifierAtMaxQuality != null) {
                hintParts.push(
                    Math.round(qmax) + ' → ×' + Number(banded[0].modifierAtMaxQuality).toFixed(2),
                );
            }
            if (hintParts.length > 0) {
                const hint = document.createElement('small');
                hint.textContent = '(' + hintParts.join(' · ') + ')';
                affects.appendChild(hint);
            }

            function compute() {
                const q = parseFloat(range.value);
                qOut.textContent = String(Math.round(q));
                range.setAttribute('aria-valuetext', Math.round(q) + ' / ' + Math.round(qmax));
                chips.forEach(function (c) {
                    const value = computeModifierValue(c.modifier, q);
                    c.out.textContent = '×' + (value == null ? '?' : value.toFixed(2));
                });
            }
            range.addEventListener('input', compute);
            compute();
        } else {
            // No usable quality band to slide over: show each stat's max-quality multiplier.
            chips.forEach(function (c) {
                const v = c.modifier.modifierAtMaxQuality;
                c.out.textContent = v == null ? '–' : '×' + Number(v).toFixed(2);
            });
        }

        block.appendChild(affects);
        return block;
    }

    function renderIngredientLine(ing) {
        const line = el('div', 'quality-name');
        const strong = document.createElement('strong');
        strong.textContent = ing.name || '?';
        line.appendChild(strong);
        const metaParts = [];
        if (ing.quantityScu != null) {
            metaParts.push(Number(ing.quantityScu).toFixed(2) + ' SCU');
        }
        if (ing.quantityUnits != null) {
            metaParts.push(ing.quantityUnits + 'x');
        }
        if (ing.minQuality != null) {
            metaParts.push((i18n().minQuality || 'min. quality') + ' ' + ing.minQuality);
        }
        if (metaParts.length > 0) {
            const small = document.createElement('small');
            small.textContent = ' · ' + metaParts.join(' · ');
            line.appendChild(small);
        }
        return line;
    }

    function betterWhenText(bw) {
        if (bw === 'higher') {
            return i18n().betterHigher || 'higher is better';
        }
        if (bw === 'lower') {
            return i18n().betterLower || 'lower is better';
        }
        if (bw === 'neutral') {
            return i18n().betterNeutral || 'neutral';
        }
        return null;
    }

    /* ----------------------------------------------------------- computation */

    function clamp01(t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    function lerp(a, b, t) {
        return a + (b - a) * t;
    }

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
                if (a == null || b == null) {
                    continue;
                }
                if (q <= b || i === segs.length - 1) {
                    if (stepped) {
                        return vs == null ? ve : vs;
                    }
                    const t = b === a ? 0 : clamp01((q - a) / (b - a));
                    return vs == null || ve == null ? vs : lerp(vs, ve, t);
                }
            }
            return null;
        }
        const qmin = m.effectiveQualityMin;
        const qmax = m.effectiveQualityMax;
        const vmin = m.modifierAtMinQuality;
        const vmax = m.modifierAtMaxQuality;
        if (qmin != null && qmax != null && vmin != null && vmax != null) {
            const tt = qmax === qmin ? 0 : clamp01((q - qmin) / (qmax - qmin));
            return lerp(vmin, vmax, tt);
        }
        return null;
    }

    /* ----------------------------------------------------- filter + keyboard */

    function applyClientFilter() {
        if (!filterInput) {
            return;
        }
        const q = (filterInput.value || '').trim().toLowerCase();
        rows().forEach(function (r) {
            const name = attr(r, 'data-name').toLowerCase();
            r.hidden = q !== '' && name.indexOf(q) === -1;
        });
    }

    function onListKeydown(e) {
        if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp' && e.key !== 'Home' && e.key !== 'End') {
            return;
        }
        const vis = visibleRows();
        if (vis.length === 0) {
            return;
        }
        e.preventDefault();
        let idx = vis.indexOf(activeRow);
        if (e.key === 'ArrowDown') {
            idx = Math.min(vis.length - 1, idx + 1);
        } else if (e.key === 'ArrowUp') {
            idx = Math.max(0, idx - 1);
        } else if (e.key === 'Home') {
            idx = 0;
        } else {
            idx = vis.length - 1;
        }
        select(vis[idx], { focus: true, showDetail: false });
    }

    /* ------------------------------------------------------------------ init */

    function init() {
        mdEl = document.getElementById('krt-bp-md');
        if (!mdEl) {
            return; // empty collection — nothing to wire
        }
        rowsEl = document.getElementById('krt-bp-master-rows');
        filterInput = document.getElementById('krt-bp-q');
        detailEmpty = document.getElementById('krt-bp-detail-empty');
        detailContent = document.getElementById('krt-bp-detail-content');
        nameEl = document.getElementById('krt-bp-detail-name');
        acquiredEl = document.getElementById('krt-bp-detail-acquired');
        recipeEl = document.getElementById('krt-bp-detail-recipe');
        noteSection = document.getElementById('krt-bp-detail-note-section');
        noteEl = document.getElementById('krt-bp-detail-note');
        editBtn = document.getElementById('krt-bp-detail-edit');
        deleteBtn = document.getElementById('krt-bp-detail-delete');
        backBtn = document.getElementById('krt-bp-detail-back');

        rows().forEach(function (r) {
            r.tabIndex = -1;
            r.addEventListener('click', function () {
                select(r, { showDetail: true });
            });
        });
        if (rowsEl) {
            rowsEl.addEventListener('keydown', onListKeydown);
        }
        if (filterInput) {
            filterInput.addEventListener('input', applyClientFilter);
            // #573 (REQ-FE-002): every blueprint is already rendered and filtered client-side on
            // each keystroke, so the master filter must never trigger a full-page reload. Intercept
            // Enter and the wrapping <form>'s submit to re-filter in place instead of submitting the
            // ?q= server form (which reloaded the whole master-detail page and dropped the selection).
            filterInput.addEventListener('keydown', function (e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    applyClientFilter();
                }
            });
            const filterForm = filterInput.closest('form');
            if (filterForm) {
                filterForm.addEventListener('submit', function (e) {
                    e.preventDefault();
                    applyClientFilter();
                });
            }
        }
        if (backBtn) {
            backBtn.addEventListener('click', function () {
                mdEl.classList.remove('is-detail');
                if (activeRow) {
                    activeRow.focus();
                }
            });
        }

        // Initial selection: ?bp= deeplink wins; otherwise the first row. On desktop the
        // detail pane is permanent, so auto-select; on mobile only a deeplink opens it.
        let initial = null;
        let fromDeeplink = false;
        try {
            const wanted = new URLSearchParams(window.location.search).get('bp');
            if (wanted && rowsEl) {
                initial = rows().find(function (r) {
                    return attr(r, 'data-id') === wanted;
                });
                fromDeeplink = initial != null;
            }
        } catch (_e) {
            /* URLSearchParams unavailable */
        }
        if (!initial) {
            initial = rows()[0] || null;
        }
        if (initial) {
            const mobile = window.matchMedia('(max-width: 900px)').matches;
            select(initial, { showDetail: fromDeeplink || !mobile });
            if (!fromDeeplink && mobile) {
                mdEl.classList.remove('is-detail');
            }
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
