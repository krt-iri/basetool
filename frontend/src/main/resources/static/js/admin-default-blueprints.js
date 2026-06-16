/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Admin default-blueprints page (REQ-INV-017): blueprint-product type-ahead, staging of the
 * picked products into the add form, and the remove-confirm modal. Mutations are classic
 * POST -> redirect forms (the set is small and admin-only), so this script only handles the
 * search/stage interaction and the modal wiring — no AJAX writes.
 */
(function () {
    'use strict';

    const cfg = window.krtDefaultBlueprints || {};
    const i18n = cfg.i18n || {};
    const defaultKeys = new Set(cfg.defaultKeys || []);
    const staged = new Set();

    const searchInput = document.getElementById('krt-dbp-search-input');
    const resultsEl = document.getElementById('krt-dbp-search-results');
    const stagingList = document.getElementById('krt-dbp-staging-list');
    const addSubmit = document.getElementById('krt-dbp-add-submit');
    const deleteModal = document.getElementById('krt-dbp-delete-modal');
    const deleteForm = document.getElementById('krt-dbp-delete-form');
    const deleteMessage = document.getElementById('krt-dbp-delete-message');

    function debounce(fn, wait) {
        let timer = null;
        return function () {
            const args = arguments;
            window.clearTimeout(timer);
            timer = window.setTimeout(function () {
                fn.apply(null, args);
            }, wait);
        };
    }

    function str(value) {
        return value == null ? '' : String(value);
    }

    /* ------------------------------------------------------------- type-ahead */

    function hideResults() {
        if (resultsEl) {
            resultsEl.hidden = true;
            resultsEl.replaceChildren();
        }
    }

    // Builds nodes with the DOM API (textContent) rather than innerHTML, so untrusted product
    // names from the search response can never be reinterpreted as HTML (CodeQL js/xss-through-dom).
    function renderMessage(text) {
        if (!resultsEl) {
            return;
        }
        const empty = document.createElement('div');
        empty.className = 'krt-pi-typeahead-empty';
        empty.textContent = str(text);
        resultsEl.replaceChildren(empty);
        resultsEl.hidden = false;
    }

    function renderResults(items) {
        if (!resultsEl) {
            return;
        }
        if (!items || items.length === 0) {
            renderMessage(i18n.noResults || 'Keine Treffer');
            return;
        }
        resultsEl.replaceChildren();
        items.forEach(function (item) {
            const isDefault = defaultKeys.has(item.productKey);
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'krt-pi-typeahead-item';
            button.disabled = isDefault || staged.has(item.productKey);
            button.setAttribute('data-key', str(item.productKey));
            button.setAttribute('data-name', str(item.name));

            const label = document.createElement('span');
            label.textContent = str(item.name);
            if (item.variantCount > 1) {
                const variants = document.createElement('span');
                variants.className = 'krt-pi-typeahead-variants';
                variants.textContent =
                    ' (' + item.variantCount + ' ' + (i18n.variants || 'Varianten') + ')';
                label.appendChild(variants);
            }
            button.appendChild(label);

            const tag = document.createElement('span');
            tag.className = 'krt-pi-typeahead-tag';
            if (isDefault) {
                tag.textContent = ' · ' + (i18n.alreadyDefault || 'Bereits Standard');
            }
            button.appendChild(tag);

            resultsEl.appendChild(button);
        });
        resultsEl.hidden = false;
    }

    function runSearch(q) {
        if (!cfg.searchUrl) {
            return;
        }
        if (!q || q.trim().length === 0) {
            hideResults();
            return;
        }
        renderMessage(i18n.searching || 'Suche...');
        const url = cfg.searchUrl + '?q=' + encodeURIComponent(q.trim()) + '&limit=25';
        window
            .fetch(url, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
            .then(function (resp) {
                return resp.ok ? resp.json() : [];
            })
            .then(renderResults)
            .catch(function () {
                renderMessage(i18n.noResults || 'Keine Treffer');
            });
    }

    /* ------------------------------------------------------------- staging */

    function refreshStagingEmptyState() {
        const empty = stagingList ? stagingList.querySelector('.krt-bp-staging-empty') : null;
        if (empty) {
            empty.style.display = staged.size === 0 ? '' : 'none';
        }
        if (addSubmit) {
            addSubmit.disabled = staged.size === 0;
        }
    }

    function stageProduct(key, name) {
        if (!key || staged.has(key) || defaultKeys.has(key) || !stagingList) {
            return;
        }
        staged.add(key);
        const chip = document.createElement('span');
        chip.className = 'krt-bp-chip';
        chip.setAttribute('data-key', key);

        const label = document.createElement('span');
        label.className = 'krt-bp-chip-label';
        label.textContent = str(name || key);
        chip.appendChild(label);

        const removeBtn = document.createElement('button');
        removeBtn.type = 'button';
        removeBtn.className = 'krt-bp-chip-remove';
        removeBtn.setAttribute('aria-label', 'x');
        removeBtn.textContent = '×';
        removeBtn.addEventListener('click', function () {
            staged.delete(key);
            chip.remove();
            refreshStagingEmptyState();
        });
        chip.appendChild(removeBtn);

        const hidden = document.createElement('input');
        hidden.type = 'hidden';
        hidden.name = 'productKeys';
        hidden.value = key;
        chip.appendChild(hidden);

        stagingList.appendChild(chip);
        refreshStagingEmptyState();
    }

    /* ------------------------------------------------------------- remove modal */

    function openDeleteModal(action, name) {
        if (!deleteModal || !deleteForm) {
            return;
        }
        deleteForm.setAttribute('action', action);
        if (deleteMessage) {
            const base = i18n.removeBody || 'Wirklich entfernen?';
            deleteMessage.textContent = name ? base + ' (' + name + ')' : base;
        }
        deleteModal.style.display = 'flex';
    }

    function closeDeleteModal() {
        if (deleteModal) {
            deleteModal.style.display = 'none';
        }
    }

    /* ------------------------------------------------------------- wiring */

    if (searchInput) {
        searchInput.addEventListener(
            'input',
            debounce(function () {
                runSearch(searchInput.value);
            }, 200),
        );
    }

    if (resultsEl) {
        resultsEl.addEventListener('click', function (e) {
            const item = e.target.closest('.krt-pi-typeahead-item');
            if (!item || item.disabled) {
                return;
            }
            stageProduct(item.getAttribute('data-key'), item.getAttribute('data-name'));
            hideResults();
            if (searchInput) {
                searchInput.value = '';
                searchInput.focus();
            }
        });
    }

    document.addEventListener('click', function (e) {
        const removeBtn = e.target.closest('[data-trigger="dbp-open-delete"]');
        if (removeBtn) {
            openDeleteModal(
                removeBtn.getAttribute('data-action'),
                removeBtn.getAttribute('data-name'),
            );
            return;
        }
        if (e.target.closest('[data-trigger="dbp-close-delete"]')) {
            closeDeleteModal();
            return;
        }
        if (e.target === deleteModal) {
            closeDeleteModal();
        }
    });

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            closeDeleteModal();
            hideResults();
        }
    });

    document.addEventListener('click', function (e) {
        if (resultsEl && !resultsEl.hidden && !e.target.closest('.krt-bp-search')) {
            hideResults();
        }
    });

    refreshStagingEmptyState();
})();
