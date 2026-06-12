(function () {
    'use strict';

    function config() {
        return window.krtBlueprintOverview || {};
    }

    function i18n() {
        return config().i18n || {};
    }

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

    function ownersUrl(productKey) {
        const base = config().ownersUrl || '';
        const sep = base.indexOf('?') === -1 ? '?' : '&';
        const url = base + sep + 'productKey=' + encodeURIComponent(productKey);
        return window.safeSameOriginUrl ? window.safeSameOriginUrl(url, url) : url;
    }

    function renderOwners(panel, owners) {
        clear(panel);
        if (!owners || owners.length === 0) {
            panel.appendChild(el('div', 'bp-owners-empty', i18n().empty || 'No owners.'));
            return;
        }
        const list = el('ul', 'bp-owners-list');
        owners.forEach(function (owner) {
            list.appendChild(el('li', 'bp-owner', owner.ownerName));
        });
        panel.appendChild(list);
    }

    function showError(panel) {
        // Reset the loaded flag so the next expand retries the fetch.
        panel.setAttribute('data-loaded', 'false');
        clear(panel);
        panel.appendChild(el('div', 'bp-owners-error', i18n().error || 'Could not load owners.'));
    }

    function loadOwners(productKey, panel) {
        panel.setAttribute('data-loaded', 'true');
        clear(panel);
        panel.appendChild(el('div', 'bp-owners-loading', i18n().loading || 'Loading...'));
        fetch(ownersUrl(productKey), {
            credentials: 'same-origin',
            headers: { Accept: 'application/json' },
        })
            .then(function (resp) {
                return resp.ok ? resp.json() : null;
            })
            .then(function (owners) {
                if (owners === null) {
                    showError(panel);
                    return;
                }
                renderOwners(panel, owners);
            })
            .catch(function () {
                showError(panel);
            });
    }

    function wireDetails(details) {
        details.addEventListener('toggle', function () {
            const row = details.closest('tr');
            const detailsRow = row ? row.nextElementSibling : null;
            if (!detailsRow || !detailsRow.classList.contains('details-row')) {
                return;
            }
            // Show/hide the companion row via a class on it directly — a CSS
            // tr:has(details[open]) sibling rule re-evaluates style across the
            // whole table per toggle and janks the UI (REQ-INV-012).
            detailsRow.classList.toggle('bp-expanded', details.open);
            if (!details.open) {
                return;
            }
            const productKey = details.getAttribute('data-product-key');
            const panel = detailsRow.querySelector('.bp-owners-panel');
            if (!productKey || !panel) {
                return;
            }
            if (panel.getAttribute('data-loaded') !== 'true') {
                loadOwners(productKey, panel);
            }
        });
    }

    // The product search is server-side (REQ-INV-013): the table is paginated, so a
    // client-side row filter would only ever see the current page. The search input is
    // a plain GET form submitted on Enter - no JS wiring needed.
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('details[data-product-key]').forEach(wireDetails);
    });
})();
