/*
 * Personal Inventory frontend logic.
 *
 * Responsibilities:
 *  - Open/close create/edit modal, prefill fields from row dataset.
 *  - Open/close KRT-styled delete confirmation modal (no native confirm()).
 *  - Provide a debounced typeahead against /personal-inventory/uex-search,
 *    rendering combined CITY + SPACE_STATION results and writing the chosen
 *    UEX id and type into the hidden form fields.
 *
 * Wiring: this module uses the global `krtEvents.on` event-delegation helper
 * (see static/js/event-delegation.js) to bind logical actions declared in the
 * template as `data-trigger="pi-…"` attributes — no inline `onclick="…"` is
 * required, which is what lets the CSP forbid `script-src-attr 'unsafe-inline'`.
 *
 * The module purposely avoids any inline alert()/confirm()/prompt() calls
 * (KRT corporate design rule) and reads its translatable strings from
 * window.krtPersonalInventoryI18n which the Thymeleaf template populates.
 */
(function () {
    'use strict';

    let modal = null;
    let deleteModal = null;
    let form = null;
    let deleteForm = null;
    let titleEl = null;
    let searchInput = null;
    let resultsEl = null;
    let hiddenUexId = null;
    let hiddenLocationType = null;
    let debounceTimer = null;

    function $(id) {
        return document.getElementById(id);
    }

    function init() {
        modal = $('krt-pi-modal');
        deleteModal = $('krt-pi-delete-modal');
        form = $('krt-pi-form');
        deleteForm = $('krt-pi-delete-form');
        titleEl = $('krt-pi-modal-title');
        searchInput = $('krt-pi-location-search');
        resultsEl = $('krt-pi-location-results');
        hiddenUexId = $('krt-pi-location-uex-id');
        hiddenLocationType = $('krt-pi-location-type');

        if (searchInput) {
            searchInput.addEventListener('input', onSearchInput);
            searchInput.addEventListener('focus', onSearchInput);
            document.addEventListener('click', function (e) {
                if (!resultsEl) return;
                if (e.target !== searchInput && !resultsEl.contains(e.target)) {
                    resultsEl.hidden = true;
                }
            });
        }
        // ESC closes any open modal.
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                closeModal();
                closeDelete();
            }
        });
        wireFilterSwap();
    }

    // ----------------------------------------------------------- filter swap

    // The search filter re-renders only #pi-results in place (REQ-FE-002) instead of reloading
    // the page. The form lives OUTSIDE the swap container, so a single binding here survives every
    // swap; the row edit/delete buttons inside the container are krtEvents-delegated and need no
    // re-init. After each swap the header subtitle + active tab-count are resynced from the hidden
    // total the fragment carries. Without krtFetch (or JS disabled) the GET form reloads as before.
    function wireFilterSwap() {
        const filterForm = document.querySelector('form.krt-pi-filter');
        // This module is shared by the user page and the admin variant. Only wire the in-place
        // swap where the results container is actually present; otherwise leave the GET form to
        // reload as before (guards against preventDefault()-ing a submit we cannot swap).
        if (!filterForm || !window.krtFetch || !document.getElementById('pi-results')) {
            return;
        }
        let timer = null;
        function doSwap() {
            const params = new URLSearchParams(new FormData(filterForm)).toString();
            const url = filterForm.getAttribute('action') + (params ? '?' + params : '');
            window.krtFetch.swap({ url: url, container: '#pi-results', history: true });
        }
        filterForm.addEventListener('submit', function (e) {
            e.preventDefault();
            if (timer) {
                clearTimeout(timer);
                timer = null;
            }
            doSwap();
        });
        const searchField = filterForm.querySelector('input[type="search"]');
        if (searchField) {
            searchField.addEventListener('input', function () {
                if (timer) {
                    clearTimeout(timer);
                }
                timer = setTimeout(function () {
                    timer = null;
                    doSwap();
                }, 300);
            });
        }
        document.addEventListener('krt:swapped', function (e) {
            const c = e.detail && e.detail.container;
            if (c && c.id === 'pi-results') {
                syncCounts(c);
            }
        });
    }

    // Mirrors the freshly filtered total (carried by the #pi-total-meta marker inside the swapped
    // fragment) into the header subtitle ("<n> Eintraege") and the active tab's count badge, so the
    // counts never drift from the visible list after a filter swap.
    function syncCounts(root) {
        const meta = (root || document).querySelector('#pi-total-meta');
        if (!meta) {
            return;
        }
        const total = meta.getAttribute('data-total') || '0';
        const tabCount = document.querySelector('.tab-nav .tab.active .tab-count');
        if (tabCount) {
            tabCount.textContent = total;
        }
        const subtitle = document.querySelector('.krt-personal-inventory-header .krt-subtitle');
        const i18n = window.krtPersonalInventoryI18n || {};
        if (subtitle && i18n.factsCount) {
            subtitle.textContent = total + ' ' + i18n.factsCount;
        }
    }

    function openCreate(btn) {
        if (!modal || !form) return;
        let i18n = window.krtPersonalInventoryI18n || {};
        if (titleEl && i18n.createTitle) titleEl.textContent = i18n.createTitle;
        form.action = window.safeSameOriginUrl(btn.getAttribute('data-action'), form.action);
        clearForm();
        modal.style.display = 'flex';
    }

    function openEdit(btn) {
        if (!modal || !form) return;
        let i18n = window.krtPersonalInventoryI18n || {};
        if (titleEl && i18n.editTitle) titleEl.textContent = i18n.editTitle;
        form.action = window.safeSameOriginUrl(btn.getAttribute('data-action'), form.action);
        setField('id', btn.getAttribute('data-id'));
        setField('version', btn.getAttribute('data-version'));
        setField('name', btn.getAttribute('data-name'));
        setField('note', btn.getAttribute('data-note'));
        setField('quantity', btn.getAttribute('data-quantity'));
        if (hiddenUexId) hiddenUexId.value = btn.getAttribute('data-location-uex-id') || '';
        if (hiddenLocationType)
            hiddenLocationType.value = btn.getAttribute('data-location-type') || '';
        if (searchInput) searchInput.value = btn.getAttribute('data-location-name') || '';
        modal.style.display = 'flex';
    }

    function closeModal() {
        if (modal) modal.style.display = 'none';
    }

    function openDelete(btn) {
        if (!deleteModal || !deleteForm) return;
        deleteForm.action = window.safeSameOriginUrl(
            btn.getAttribute('data-action'),
            deleteForm.action,
        );
        let msgEl = $('krt-pi-delete-message');
        let name = btn.getAttribute('data-name');
        if (msgEl && name) {
            msgEl.textContent =
                window.krtPersonalInventoryI18n && window.krtPersonalInventoryI18n.confirmBody
                    ? window.krtPersonalInventoryI18n.confirmBody + ' (' + name + ')'
                    : msgEl.textContent;
        }
        deleteModal.style.display = 'flex';
    }

    function closeDelete() {
        if (deleteModal) deleteModal.style.display = 'none';
    }

    function setField(name, value) {
        if (!form) return;
        let el = form.querySelector('[name="' + name + '"]');
        if (el) el.value = value == null ? '' : value;
    }

    function clearForm() {
        if (!form) return;
        ['id', 'version', 'name', 'note', 'quantity'].forEach(function (n) {
            setField(n, '');
        });
        if (hiddenUexId) hiddenUexId.value = '';
        if (hiddenLocationType) hiddenLocationType.value = '';
        if (searchInput) searchInput.value = '';
    }

    function onSearchInput() {
        if (debounceTimer) clearTimeout(debounceTimer);
        debounceTimer = setTimeout(runSearch, 250);
    }

    function runSearch() {
        if (!searchInput || !resultsEl) return;
        let q = searchInput.value || '';
        let endpoints = window.krtPersonalInventoryEndpoints || {};
        let url =
            (endpoints.uexSearch || '/personal-inventory/uex-search') +
            '?q=' +
            encodeURIComponent(q) +
            '&limit=2000';
        resultsEl.hidden = false;
        resultsEl.innerHTML =
            '<div class="krt-pi-typeahead-loading">' +
            escapeHtml((window.krtPersonalInventoryI18n || {}).searching || 'Suche...') +
            '</div>';
        fetch(url, { credentials: 'same-origin', headers: { Accept: 'application/json' } })
            .then(function (resp) {
                return resp.ok ? resp.json() : [];
            })
            .then(renderResults)
            .catch(function () {
                renderResults([]);
            });
    }

    function renderResults(items) {
        if (!resultsEl) return;
        if (!items || items.length === 0) {
            resultsEl.innerHTML =
                '<div class="krt-pi-typeahead-empty">' +
                escapeHtml((window.krtPersonalInventoryI18n || {}).noResults || 'Keine Treffer') +
                '</div>';
            return;
        }
        let html = '';
        items.forEach(function (it) {
            let typeClass = it.type === 'CITY' ? 'krt-pi-loc-city' : 'krt-pi-loc-station';
            html +=
                '<button type="button" class="krt-pi-typeahead-item" ' +
                'data-uex-id="' +
                escapeAttr(it.uexId) +
                '" ' +
                'data-type="' +
                escapeAttr(it.type) +
                '" ' +
                'data-name="' +
                escapeAttr(it.name) +
                '">' +
                '<span class="krt-pi-location-marker ' +
                typeClass +
                '"></span>' +
                '<span class="krt-pi-typeahead-name">' +
                escapeHtml(it.name || '') +
                '</span>' +
                '<span class="krt-pi-typeahead-meta">' +
                escapeHtml(it.parentName || '') +
                (it.starSystemName ? ' / ' + escapeHtml(it.starSystemName) : '') +
                '</span>' +
                '</button>';
        });
        resultsEl.innerHTML = html;
        resultsEl.querySelectorAll('.krt-pi-typeahead-item').forEach(function (btn) {
            btn.addEventListener('click', function () {
                if (hiddenUexId) hiddenUexId.value = btn.getAttribute('data-uex-id') || '';
                if (hiddenLocationType)
                    hiddenLocationType.value = btn.getAttribute('data-type') || '';
                if (searchInput) searchInput.value = btn.getAttribute('data-name') || '';
                resultsEl.hidden = true;
            });
        });
    }

    /**
     * Sanitizes the quantity input to ensure only positive integers (>= 1).
     * Strips any non-digit characters (e.g. '-', '.', 'e') that some browsers
     * still allow in <input type="number"> and clamps the lower bound to 1.
     * The upper bound is intentionally not enforced here; backend @Min(1)
     * remains the authoritative validator.
     */
    function sanitizeQuantity(input) {
        if (!input) return;
        let raw = input.value || '';
        let digitsOnly = raw.replace(/[^0-9]/g, '');
        if (digitsOnly === '') {
            if (raw !== '') input.value = '';
            return;
        }
        // Strip leading zeros (but keep a single zero if user is mid-typing).
        digitsOnly = digitsOnly.replace(/^0+/, '');
        let n = parseInt(digitsOnly, 10);
        if (isNaN(n) || n < 1) n = 1;
        input.value = String(n);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Delegated event bindings via the global event-delegation helper. The
    // matching `data-trigger="pi-…"` attributes are set on the buttons / inputs
    // in personal-inventory.html. Using delegated listeners (rather than
    // querySelectorAll + addEventListener per node) keeps the wiring working for
    // table rows that are re-rendered by Thymeleaf on form errors without
    // requiring a re-bind. The legacy `window.krtPersonalInventory.x(this)`
    // global is intentionally removed — every template entry point now goes
    // through `data-trigger` so the CSP can later drop
    // `script-src-attr 'unsafe-inline'`.
    if (window.krtEvents && typeof window.krtEvents.on === 'function') {
        window.krtEvents.on('click', 'pi-open-create', openCreate);
        window.krtEvents.on('click', 'pi-open-edit', openEdit);
        window.krtEvents.on('click', 'pi-open-delete', openDelete);
        window.krtEvents.on('click', 'pi-close-modal', closeModal);
        window.krtEvents.on('click', 'pi-close-delete', closeDelete);
        window.krtEvents.on('input', 'pi-sanitize-quantity', sanitizeQuantity);
    }
})();
