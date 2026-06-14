(function () {
    'use strict';

    const form = document.getElementById('operations-filter-form');
    const resultsContainer = document.getElementById('operations-results');
    const loadingIndicator = document.getElementById('operations-loading-indicator');
    const resetBtn = document.getElementById('operations-filter-reset');

    // krtFetch (fragments/head.html) owns the fragment swap + the in-results pagination
    // interception, so the whole list — filter, sort and paginate — stays in place.
    if (!form || !resultsContainer || !window.krtFetch) return;

    let debounceTimer = null;

    function buildQueryString() {
        const data = new FormData(form);
        const params = new URLSearchParams();
        for (const [key, value] of data.entries()) {
            if (value !== '') params.append(key, value);
        }
        return params.toString();
    }

    function loadResults() {
        const query = buildQueryString();
        window.krtFetch.swap({
            url: '/operations' + (query ? '?' + query : ''),
            container: resultsContainer,
            indicator: loadingIndicator,
            history: true,
        });
    }

    // Exposed so the page-local create/delete handlers (operations-index.html) can refresh the list
    // in place after an in-place write, reusing the active filter query — no full-page reload (#576).
    window.krtOperationsReload = loadResults;

    function onFilterChange() {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(loadResults, 300);
    }

    form.querySelectorAll('input, select').forEach(function (el) {
        el.addEventListener('input', onFilterChange);
        el.addEventListener('change', onFilterChange);
    });

    if (resetBtn) {
        resetBtn.addEventListener('click', function () {
            form.querySelectorAll(
                'input[type="text"], input[type="hidden"], input[type="date"], input[type="time"]',
            ).forEach(function (el) {
                el.value = '';
            });
            form.querySelectorAll('input[type="checkbox"]').forEach(function (el) {
                el.checked = false;
            });
            loadResults();
        });
    }
})();
