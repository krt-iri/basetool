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

    function esc(value) {
        const div = document.createElement('div');
        div.textContent = value == null ? '' : String(value);
        return div.innerHTML;
    }

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

    /* ------------------------------------------------------------- type-ahead */

    function hideResults() {
        if (resultsEl) {
            resultsEl.hidden = true;
            resultsEl.innerHTML = '';
        }
    }

    function renderMessage(text) {
        if (!resultsEl) {
            return;
        }
        resultsEl.innerHTML = '<div class="krt-pi-typeahead-empty">' + esc(text) + '</div>';
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
        const html = items
            .map(function (item) {
                const isDefault = defaultKeys.has(item.productKey);
                const isStaged = staged.has(item.productKey);
                const disabled = isDefault || isStaged;
                const tag = isDefault ? ' · ' + esc(i18n.alreadyDefault || 'Bereits Standard') : '';
                const variants =
                    item.variantCount > 1
                        ? ' <span class="krt-pi-typeahead-variants">(' +
                          item.variantCount +
                          ' ' +
                          esc(i18n.variants || 'Varianten') +
                          ')</span>'
                        : '';
                return (
                    '<button type="button" class="krt-pi-typeahead-item"' +
                    (disabled ? ' disabled' : '') +
                    ' data-key="' +
                    esc(item.productKey) +
                    '" data-name="' +
                    esc(item.name) +
                    '"><span>' +
                    esc(item.name) +
                    variants +
                    '</span><span class="krt-pi-typeahead-tag">' +
                    tag +
                    '</span></button>'
                );
            })
            .join('');
        resultsEl.innerHTML = html;
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
        chip.innerHTML =
            '<span class="krt-bp-chip-label">' +
            esc(name || key) +
            '</span>' +
            '<button type="button" class="krt-bp-chip-remove" aria-label="x">&times;</button>' +
            '<input type="hidden" name="productKeys" value="' +
            esc(key) +
            '">';
        chip.querySelector('.krt-bp-chip-remove').addEventListener('click', function () {
            staged.delete(key);
            chip.remove();
            refreshStagingEmptyState();
        });
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
