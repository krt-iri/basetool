(function () {
    'use strict';

    const form = document.getElementById('missions-filter-form');
    const resultsContainer = document.getElementById('missions-results');
    const loadingIndicator = document.getElementById('missions-loading-indicator');
    const resetBtn = document.getElementById('missions-filter-reset');

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
            url: '/missions' + (query ? '?' + query : ''),
            container: resultsContainer,
            indicator: loadingIndicator,
            history: true,
        });
    }

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
